package dz.sntp.market.data.remote

import java.io.IOException

/**
 * The server answered and refused. Distinct from [IOException], which means we
 * never reached it. The difference decides whether a vote is retried later or
 * dropped: retrying a 400 forever fills the queue with work that can never
 * succeed, and the trader keeps seeing "will send when back online".
 */
class ApiException(
    val code: Int,
    val body: String,
) : IOException("HTTP $code: ${body.take(200)}") {

    /** PostgREST sends the trigger's `raise exception` text in `message`. */
    fun mentions(fragment: String): Boolean = body.contains(fragment, ignoreCase = true)

    val isPermanent: Boolean get() = code in 400..499 && code != 408 && code != 429
}
