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

    /** Main thread: Play callbacks and `startConnection` must run there. */
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private val _ready = MutableStateFlow(false)

    /**
     * True only when Play is connected *and* at least one product came back.
     * Connected-but-no-products is the normal state before the console has
     * prices, and enabling the buy button then means every tap fails with
     * `product_unavailable`. The sheet says "not activated yet" instead.
     */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _products = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())

    /**
     * Real, localized prices from Play, keyed by SKU. Play requires the price the
     * user is charged to be the price shown, and it varies by country and by
     * whatever tax rules apply, so a hardcoded "1000 دج" is both wrong abroad and
     * a policy problem.
     */
    private val _prices = MutableStateFlow<Map<String, String>>(emptyMap())
    val prices: StateFlow<Map<String, String>> = _prices.asStateFlow()

    /**
     * Called on the Play callback thread (main) with the purchased SKU and its
     * purchase token. The token is the only thing that tells a renewal (a new
     * period, a new expiry) from a replay of a purchase we already have (the same
     * period again, which must not extend anything).
     */
    var onPurchased: ((String, String) -> Unit)? = null

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
        startConnection(c, attempt = 1)
    }

    /**
     * Play asks for `startConnection` to be retried when the service drops — the
     * BillingClient docs are explicit about it. Nothing did, and `connect()` returns
     * early once a client exists, so a single disconnect (a Play Store update, a
     * service crash, a brief network change) left billing dead for the rest of the
     * process: the upgrade sheet said "not activated yet" and no purchase could be
     * started until the user force-stopped the app.
     */
    private fun startConnection(c: BillingClient, attempt: Int) {
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProducts()
                    restore()
                } else if (attempt < MAX_CONNECT_ATTEMPTS) {
                    retryLater(c, attempt)
                } else {
                    _ready.value = false
                }
            }

            override fun onBillingServiceDisconnected() {
                _ready.value = false
                // Fresh budget: this is a new drop, not a continuation of the last
                // one, and it is exactly the case this retry exists for.
                retryLater(c, 1)
            }
        })
    }

    private fun retryLater(c: BillingClient, attempt: Int) {
        mainHandler.postDelayed({
            // A new client was built meanwhile (close + connect): this one is stale.
            if (client !== c) return@postDelayed
            if (!c.isReady) startConnection(c, attempt + 1)
        }, CONNECT_RETRY_MS * attempt)
    }

    fun close() {
        mainHandler.removeCallbacksAndMessages(null)
        client?.endConnection()
        client = null
        _ready.value = false
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
            // Through handle(), not straight to onPurchased: a purchase restored
            // after a reinstall is very often still unacknowledged, and Play
            // refunds an unacknowledged purchase after three days. Acknowledging
            // only in the buy callback meant the user who reinstalled was charged
            // and then silently refunded.
            purchases
                .firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                ?.let(::handle)
        }
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
                val bySku = details.associateBy { it.productId }
                _products.value = bySku
                _prices.value = bySku.mapValues { (_, d) ->
                    d.subscriptionOfferDetails?.firstOrNull()
                        ?.pricingPhases?.pricingPhaseList?.firstOrNull()
                        ?.formattedPrice
                        ?: ""
                }.filterValues { it.isNotBlank() }
                // Only now is a purchase actually possible.
                _ready.value = bySku.isNotEmpty()
            } else {
                _ready.value = false
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
        purchase.products.firstOrNull()?.let { sku ->
            onPurchased?.invoke(sku, purchase.purchaseToken)
        }
    }

    companion object {
        const val SKU_PRO_MONTHLY = "pro_monthly"
        const val SKU_PRO_YEARLY = "pro_yearly"
        const val SKU_BOUTIQUE_MONTHLY = "boutique_monthly"

        val SKUS = listOf(SKU_PRO_MONTHLY, SKU_PRO_YEARLY, SKU_BOUTIQUE_MONTHLY)

        /** Play's own guidance is a few retries with backoff, not an infinite loop. */
        private const val MAX_CONNECT_ATTEMPTS = 4
        private const val CONNECT_RETRY_MS = 2_000L
    }
}
