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
     * Asks the server, then tries the promo once. Returns null when nothing
     * changed, so callers keep the cached value. A free answer never downgrades
     * a locally stored Play purchase.
     */
    suspend fun syncFromServer(): Entitlement? = withContext(Dispatchers.IO) {
        if (!market.isConfigured) return@withContext null

        val device = prefs.deviceId
        val local = prefs.entitlement().effective()

        when (val existing = ask("my_entitlement", device)) {
            is Answer.Found -> {
                save(existing.e)
                // What is actually stored now — save() may have refused the write.
                return@withContext prefs.entitlement().effective()
            }
            // No server session or a transport failure. Trying the promo now would
            // burn the device's single attempt on a connection problem, so wait.
            Answer.Unreachable -> return@withContext null
            Answer.Empty -> Unit
        }

        // A paying subscriber must not take a free lifetime seat: the server would
        // hand back the paid row unchanged, but the seat counter still moves.
        if (local.isPro) return@withContext null
        if (prefs.promoAttempted()) return@withContext null

        when (val claimed = ask("claim_promo", device)) {
            is Answer.Found -> {
                // Only now is the attempt really spent: the server answered.
                prefs.setPromoAttempted()
                save(claimed.e)
                prefs.entitlement().effective()
            }
            Answer.Empty -> {
                // Answered, and there is no row: the 500 seats are gone.
                prefs.setPromoAttempted()
                null
            }
            Answer.Unreachable -> null
        }
    }

    /** A Play purchase is acknowledged by the client and stored immediately. */
    suspend fun grantFromPlay(sku: String, token: String? = null): Entitlement =
        withContext(Dispatchers.IO) {
            val previous = prefs.entitlement()
            val e = Entitlement.fromSku(
                sku = sku,
                previous = previous,
                previousToken = prefs.playToken(),
                token = token,
            )
            // One edit: a new period must never be recorded under the old token.
            prefs.setPlayEntitlement(e, token)
            e
        }

    private sealed interface Answer {
        data class Found(val e: Entitlement) : Answer
        /** The server replied with an empty set. */
        data object Empty : Answer
        /** Network, timeout, or auth problem — nothing was decided. */
        data object Unreachable : Answer
    }

    private suspend fun ask(fn: String, device: String): Answer {
        val body = """{"p_device_id":"${device.filter { it.isLetterOrDigit() }}"}"""
        val result = market.rpc(fn, body)

        val raw = result.getOrElse { return Answer.Unreachable }
        return runCatching {
            val row = json.parseToJsonElement(raw).jsonArray.firstOrNull()?.jsonObject
                ?: return@runCatching Answer.Empty
            val e = Entitlement.fromServer(
                tier = row["tier"]?.jsonPrimitive?.text(),
                source = row["source"]?.jsonPrimitive?.text(),
                expiresAtMs = parseTime(row["expires_at"]?.jsonPrimitive?.text()),
            )
            if (e.tier == Tier.FREE) Answer.Empty else Answer.Found(e)
        }.getOrDefault(Answer.Empty)
    }

    private fun parseTime(iso: String?): Long = IsoTime.toEpochMs(iso)

    private suspend fun save(e: Entitlement) {
        if (e.tier == Tier.FREE) return
        // A live Play subscription outranks anything the server can say. The
        // server has no verified Play row at all (verification is not wired up),
        // so what it answers for a paying device is either an older promo grant or
        // an admin one. Letting that overwrite the local Play row dropped the
        // paying relationship from the device while the charge kept coming, and
        // the next restore() then re-dated the period from scratch.
        val local = prefs.entitlement()
        if (local.source == "play" && !local.isExpired()) return
        prefs.setEntitlement(e)
    }
}

private fun JsonPrimitive.text(): String? = if (this is JsonNull) null else content
