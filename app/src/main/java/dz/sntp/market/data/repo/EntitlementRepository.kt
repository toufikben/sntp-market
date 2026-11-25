package dz.sntp.market.data.repo

import dz.sntp.market.data.local.Prefs
import dz.sntp.market.data.model.Entitlement
import dz.sntp.market.data.model.Tier
import dz.sntp.market.util.IsoTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * One place that answers "what did this device pay for". Three sources feed it:
 * Play (renewing), the first-500 promo (lifetime, server-granted), and admin.
 *
 * The promo claim is deliberately server-side: if the limit were checked here,
 * every install would think it was number one.
 */
class EntitlementRepository(
    private val prefs: Prefs,
    private val market: MarketRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun current(): Entitlement = withContext(Dispatchers.IO) {
        prefs.entitlement().effective()
    }

    /**
     * Asks the server, then tries the promo once. Returns null when there is no
     * server configured or nothing changed, so callers keep the cached value.
     * A free answer never downgrades a locally stored Play purchase.
     */
    suspend fun syncFromServer(): Entitlement? = withContext(Dispatchers.IO) {
        if (!market.isConfigured) return@withContext null

        val device = prefs.deviceId

        ask("my_entitlement", device)?.let { save(it); return@withContext it }

        if (!prefs.promoAttempted()) {
            val claimed = ask("claim_promo", device)
            prefs.setPromoAttempted()
            if (claimed != null) {
                save(claimed)
                return@withContext claimed
            }
        }
        null
    }

    /** A Play purchase is acknowledged by the client and stored immediately. */
    suspend fun grantFromPlay(sku: String): Entitlement = withContext(Dispatchers.IO) {
        val e = Entitlement.fromSku(sku)
        prefs.setEntitlement(e)
        e
    }

    private suspend fun ask(fn: String, device: String): Entitlement? {
        val body = """{"p_device_id":"${device.filter { it.isLetterOrDigit() }}"}"""
        val raw = market.rpc(fn, body).getOrNull() ?: return null
        return runCatching {
            val row = json.parseToJsonElement(raw).jsonArray.firstOrNull()?.jsonObject
                ?: return@runCatching null
            Entitlement.fromServer(
                tier = row["tier"]?.jsonPrimitive?.text(),
                source = row["source"]?.jsonPrimitive?.text(),
                expiresAtMs = parseTime(row["expires_at"]?.jsonPrimitive?.text()),
            )
        }.getOrNull()
    }

    private fun parseTime(iso: String?): Long = IsoTime.toEpochMs(iso)

    private suspend fun save(e: Entitlement) {
        if (e.tier != Tier.FREE) prefs.setEntitlement(e)
    }
}

private fun JsonPrimitive.text(): String? = if (this is JsonNull) null else content
