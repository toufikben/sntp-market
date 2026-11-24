package dz.sntp.market.data.repo

import android.content.Context

/**
 * Reports captured inside the market, where the network is usually dead. They are
 * written to a plain file and replayed on the next successful connection. The
 * server drops anything older than three hours, so a stale queue cannot pollute
 * tomorrow's state.
 */
object PendingVotes {

    private const val FILE = "pending_votes.txt"
    private const val MAX_ITEMS = 20

    private fun file(context: Context) = java.io.File(context.filesDir, FILE)

    @Synchronized
    fun enqueue(context: Context, payload: String) {
        runCatching {
            val f = file(context)
            val existing = if (f.exists()) f.readLines().filter { it.isNotBlank() } else emptyList()
            val trimmed = (existing + payload).takeLast(MAX_ITEMS)
            f.writeText(trimmed.joinToString("\n"))
        }
    }

    @Synchronized
    fun drain(context: Context): List<String> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        val items = runCatching { f.readLines().filter { it.isNotBlank() } }.getOrDefault(emptyList())
        runCatching { f.delete() }
        return items
    }

    @Synchronized
    fun count(context: Context): Int = runCatching {
        val f = file(context)
        if (!f.exists()) 0 else f.readLines().count { it.isNotBlank() }
    }.getOrDefault(0)
}
