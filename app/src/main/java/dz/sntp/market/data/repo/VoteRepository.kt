package dz.sntp.market.data.repo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dz.sntp.market.data.local.Prefs
import dz.sntp.market.data.model.Proximity
import dz.sntp.market.data.model.ReportDto
import dz.sntp.market.data.model.VoteMessage
import dz.sntp.market.data.model.VoteOutcome
import dz.sntp.market.data.remote.ApiException
import dz.sntp.market.data.seed.SntpSeed
import dz.sntp.market.util.Geo
import dz.sntp.market.util.MarketTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.coroutines.resume

/**
 * Community voting. Three defences keep a single trader from inventing a state:
 * a real GPS fix inside the market radius, one vote per device every 20 minutes
 * (the market can open, fill and shut inside one day, so a daily limit froze the
 * screen at its morning value), and a server-side check we cannot bypass here.
 */
class VoteRepository(
    private val context: Context,
    private val marketRepository: MarketRepository,
    private val prefs: Prefs,
) {
    private val json = Json { encodeDefaults = true; explicitNulls = false }
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(context) }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): Location? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) return@withContext null
        suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
            runCatching {
                fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
            }.onFailure { cont.resume(null) }
        }
    }

    fun proximityTo(location: Location?): Proximity {
        val loc = location ?: return Proximity.UNKNOWN
        val d = Geo.distanceMeters(loc.latitude, loc.longitude, SntpSeed.LAT, SntpSeed.LNG)
        return when {
            d <= SntpSeed.VOTE_RADIUS_M -> Proximity.INSIDE
            d <= SntpSeed.FAR_RADIUS_M -> Proximity.NEAR
            else -> Proximity.FAR
        }
    }

    suspend fun vote(
        status: String,
        opensAt: String? = null,
        closesAt: String? = null,
        reason: String? = null,
    ): VoteOutcome = withContext(Dispatchers.IO) {
        val nowMs = System.currentTimeMillis()
        val sinceLast = nowMs - prefs.lastVoteAt()
        if (sinceLast < VOTE_COOLDOWN_MS) {
            return@withContext VoteOutcome(false, false, VoteMessage.RATE_LIMITED)
        }

        val loc = currentLocation()
        val proximity = proximityTo(loc)
        if (proximity == Proximity.FAR) {
            return@withContext VoteOutcome(false, false, VoteMessage.TOO_FAR)
        }
        if (proximity == Proximity.UNKNOWN) {
            return@withContext VoteOutcome(false, false, VoteMessage.NEED_LOCATION)
        }

        val payload = buildJsonObject {
            put("market_slug", SntpSeed.MARKET_ID)
            put("status", status)
            opensAt?.let { put("opens_at", it) }
            closesAt?.let { put("closes_at", it) }
            reason?.let { put("reason", it) }
            put("device_id", prefs.deviceId)
            loc?.let {
                put("lat", it.latitude)
                put("lng", it.longitude)
                put("accuracy_m", it.accuracy.toDouble())
            }
            put("client_time", MarketTime.now().toString())
        }.toString()

        val result = marketRepository.postReport(payload)
        if (result.isSuccess) {
            prefs.setLastVoteAt(nowMs)
            prefs.setLastVoteDay(MarketTime.isoDay())
            VoteOutcome(true, false, VoteMessage.SENT)
        } else {
            val error = result.exceptionOrNull() as? ApiException
            val permanent = error?.isPermanent == true

            if (permanent) {
                // The server refused this vote for a reason retrying cannot fix
                // (rate limited, device blocked, market unknown). Queuing it would
                // promise the trader something that never happens.
                prefs.setLastVoteAt(nowMs)
                val message = when {
                    error?.mentions("rate limited") == true -> VoteMessage.RATE_LIMITED
                    else -> VoteMessage.FAILED
                }
                VoteOutcome(false, false, message)
            } else {
                // Never lose the trader's observation: it goes out on reconnect.
                PendingVotes.enqueue(context, payload)
                // Count the attempt locally too, so the button cannot be farmed offline.
                prefs.setLastVoteAt(nowMs)
                VoteOutcome(true, true, VoteMessage.QUEUED)
            }
        }
    }

    /**
     * Sends everything recorded while the network was down. Items the server
     * rejects for good (stale, duplicate, blocked) are dropped rather than
     * requeued; only transport failures are retried.
     */
    suspend fun flushPending(): Int = withContext(Dispatchers.IO) {
        val items = PendingVotes.drain(context)
        var sent = 0
        for (item in items) {
            val res = marketRepository.postReport(item)
            if (res.isSuccess) {
                sent++
            } else {
                val permanent = (res.exceptionOrNull() as? ApiException)?.isPermanent == true
                if (!permanent) PendingVotes.enqueue(context, item)
            }
        }
        sent
    }

    /**
     * The server's view of this device's score. Settings used to display a local
     * constant of 100 that never moved, while the screen claimed the number grows
     * when your votes match the consensus. It now grows there and is read here.
     * Returns null when the server cannot be asked; the cached value stays.
     */
    suspend fun syncReputation(): Int? = withContext(Dispatchers.IO) {
        if (!marketRepository.isConfigured) return@withContext null

        val payload = buildJsonObject {
            put("p_device_id", prefs.deviceId)
        }.toString()
        val raw = marketRepository.rpc("my_reputation", payload).getOrNull()
            ?: return@withContext null

        runCatching {
            val row = json.parseToJsonElement(raw).jsonArray.firstOrNull()?.jsonObject
                ?: return@runCatching null
            val rep = row["reputation"]?.jsonPrimitive?.text()?.toIntOrNull()
                ?: return@runCatching null
            prefs.setReputation(rep)
            rep
        }.getOrNull()
    }

    @Suppress("unused")
    private fun encodeReport(r: ReportDto) = json.encodeToString(ReportDto.serializer(), r)

    private companion object {
        /** Same gap the server enforces; a client that disagrees only gets errors. */
        const val VOTE_COOLDOWN_MS = 20 * 60 * 1000L
    }
}
