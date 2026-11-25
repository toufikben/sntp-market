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
        return runCatching { f.readLines().filter { it.isNotBlank() } }.getOrDefault(emptyList())
    }

    /**
     * Replaces the queue with [items]. The old queue is dropped only after the new
     * one is safely written, so a crash during a flush cannot lose the votes
     * collected inside the market — where there is no signal to record them again.
     */
    @Synchronized
    fun replace(context: Context, items: List<String>) {
        runCatching {
            val f = file(context)
            if (items.isEmpty()) {
                f.delete()
                return@runCatching
            }
            val tmp = java.io.File(context.filesDir, "$FILE.tmp")
            tmp.writeText(items.takeLast(MAX_ITEMS).joinToString("\n"))
            if (!tmp.renameTo(f)) {
                f.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    @Synchronized
    fun count(context: Context): Int = runCatching {
        val f = file(context)
        if (!f.exists()) 0 else f.readLines().count { it.isNotBlank() }
    }.getOrDefault(0)
}
