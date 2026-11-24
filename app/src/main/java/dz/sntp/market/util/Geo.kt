package dz.sntp.market.util

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Distance and vote-weight maths for the crowd engine. Kept pure so it is unit-testable. */
object Geo {

    private const val EARTH_R = 6_371_000.0

    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_R * asin(sqrt(a))
    }

    /**
     * How much a single report should count.
     *
     * Inside the market: full weight. Just outside: reduced. Beyond the radius:
     * zero, because a trader 10 km away has no idea whether the gate is open.
     */
    fun proximityWeight(distanceM: Double, voteRadius: Double, farRadius: Double): Double = when {
        distanceM <= voteRadius -> 1.0
        distanceM <= farRadius -> 0.35
        else -> 0.0
    }

    /** Reputation multiplier: new device 1.0, proven 1.8, discredited 0.4. */
    fun reputationWeight(reputationX100: Int): Double = when {
        reputationX100 >= 180 -> 1.8
        reputationX100 >= 140 -> 1.4
        reputationX100 >= 80 -> 1.0
        reputationX100 >= 40 -> 0.6
        else -> 0.4
    }

    /** A vote older than 90 minutes describes a market that has moved on. */
    fun recencyWeight(ageMinutes: Long): Double = when {
        ageMinutes <= 45 -> 1.0
        ageMinutes <= 90 -> 0.7
        ageMinutes <= 180 -> 0.4
        else -> 0.0
    }

    fun totalWeight(distanceM: Double, reputationX100: Int, ageMinutes: Long, voteRadius: Double, farRadius: Double): Double =
        proximityWeight(distanceM, voteRadius, farRadius) *
            reputationWeight(reputationX100) *
            recencyWeight(ageMinutes)
}
