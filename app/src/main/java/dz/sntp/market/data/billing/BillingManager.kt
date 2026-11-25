package dz.sntp.market.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Play Billing, as thin as it can be. It deliberately does no server call: a
 * token is granted only after Play says PURCHASED, but real fraud protection
 * needs a server-side check against the Play Developer API before this app
 * charges anyone. See SETUP.md → "قبل تحصيل أي مبلغ".
 *
 * With no console products configured — which is the current state — the client
 * stays quiet, [ready] is false, and the upgrade sheet says so instead of
 * failing mid-flow.
 */
class BillingManager(private val context: Context) {

    private var client: BillingClient? = null

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _products = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())

    /** Called on the Play callback thread (main) with the purchased SKU. */
    var onPurchased: ((String) -> Unit)? = null

    private val listener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            purchases?.forEach(::handle)
        }
    }

    fun connect() {
        if (client != null) return
        val c = BillingClient.newBuilder(context)
            .setListener(listener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .build()
        client = c
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _ready.value = true
                    queryProducts()
                    restore()
                }
            }

            override fun onBillingServiceDisconnected() {
                _ready.value = false
            }
        })
    }

    fun buy(activity: Activity, sku: String): String? {
        val c = client ?: return "billing_unavailable"
        val product = _products.value[sku] ?: return "product_unavailable"
        val offerToken = product.subscriptionOfferDetails
            ?.firstOrNull()?.offerToken ?: return "offer_unavailable"

        val params = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product)
            .setOfferToken(offerToken)
            .build()
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(params))
            .build()
        val result = c.launchBillingFlow(activity, flow)
        return if (result.responseCode == BillingClient.BillingResponseCode.OK) null
        else "flow_${result.responseCode}"
    }

    /** Replays an active subscription after a reinstall or a new device. */
    fun restore() {
        val c = client ?: return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        c.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            purchases
                .firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                ?.products?.firstOrNull()
                ?.let { onPurchased?.invoke(it) }
        }
    }

    fun close() {
        client?.endConnection()
        client = null
        _ready.value = false
    }

    private fun queryProducts() {
        val c = client ?: return
        val products = SKUS.map { sku ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(sku)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()
        c.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _products.value = details.associateBy { it.productId }
            }
        }
    }

    private fun handle(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        if (!purchase.isAcknowledged) {
            val ack = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            client?.acknowledgePurchase(ack) { /* ignored: Play retries the callback */ }
        }
        purchase.products.firstOrNull()?.let { onPurchased?.invoke(it) }
    }

    companion object {
        const val SKU_PRO_MONTHLY = "pro_monthly"
        const val SKU_PRO_YEARLY = "pro_yearly"
        const val SKU_BOUTIQUE_MONTHLY = "boutique_monthly"

        val SKUS = listOf(SKU_PRO_MONTHLY, SKU_PRO_YEARLY, SKU_BOUTIQUE_MONTHLY)
    }
}
