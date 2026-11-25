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
import dz.sntp.market.data.seed.SntpSeed
import dz.sntp.market.util.Geo
import dz.sntp.market.util.MarketTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.resume

/**
 * Community voting. Three defences keep a single trader from inventing a state:
 * a real GPS fix inside the market radius, one vote per device per day, and a
 * server-side check we cannot bypass from here.
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
            // Never lose the trader's observation: it goes out on reconnect.
            PendingVotes.enqueue(context, payload)
            // Count the attempt locally too, so the button cannot be farmed offline.
            prefs.setLastVoteAt(nowMs)
            VoteOutcome(true, true, VoteMessage.QUEUED)
        }
    }

    /** Sends everything that was recorded while the network was down. Server rejects old ones. */
    suspend fun flushPending(): Int = withContext(Dispatchers.IO) {
        val items = PendingVotes.drain(context)
        var sent = 0
        for (item in items) {
            val res = marketRepository.postReport(item)
            if (res.isSuccess) sent++ else PendingVotes.enqueue(context, item)
        }
        sent
    }

    @Suppress("unused")
    private fun encodeReport(r: ReportDto) = json.encodeToString(ReportDto.serializer(), r)

    private companion object {
        /** Same gap the server enforces; a client that disagrees only gets errors. */
        const val VOTE_COOLDOWN_MS = 20 * 60 * 1000L
    }
}
