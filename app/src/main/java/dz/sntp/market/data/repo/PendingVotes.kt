package dz.sntp.market.data.repo

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
            writeAtomic(context, (existing + payload).takeLast(MAX_ITEMS))
        }
    }

    @Synchronized
    fun drain(context: Context): List<String> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return runCatching { f.readLines().filter { it.isNotBlank() } }.getOrDefault(emptyList())
    }

    /**
     * Removes the payloads that are finished with, keeping everything else.
     *
     * Read-modify-write inside one lock, on purpose. The flush used to drain the
     * file, post, then drain it again and write back the difference — and a vote
     * appended between that second read and the write was silently erased. The
     * votes in this queue were typed inside the market, where there is no signal
     * to record them again, so losing one is the one failure the trader cannot
     * recover from. `enqueue` takes the same lock, so it either lands before this
     * (and is kept) or after (and is appended to the new file).
     */
    @Synchronized
    fun remove(context: Context, done: List<String>) {
        if (done.isEmpty()) return
        runCatching {
            val remaining = drain(context).toMutableList()
            for (item in done) remaining.remove(item)
            val f = file(context)
            if (remaining.isEmpty()) f.delete() else writeAtomic(context, remaining)
        }
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
            writeAtomic(context, items)
        }
    }

    /**
     * Write-then-rename. `enqueue` used to truncate the real file and rewrite it,
     * so a process death between those two steps lost every vote in the queue —
     * the ones captured inside the market, where there is no signal to record
     * them again. Only `replace` was atomic; both paths are now.
     */
    private fun writeAtomic(context: Context, items: List<String>) {
        val tmp = java.io.File(context.filesDir, "$FILE.tmp")
        tmp.writeText(items.takeLast(MAX_ITEMS).joinToString("\n"))
        val f = file(context)
        if (!tmp.renameTo(f)) {
            f.writeText(tmp.readText())
            tmp.delete()
        }
    }

    /**
     * Reads the queue off the main thread. Every caller is inside a coroutine, and
     * the refresh path used to run this file read on the Main dispatcher — three
     * times per refresh, on the screen the user is staring at.
     */
    suspend fun count(context: Context): Int = withContext(Dispatchers.IO) {
        runCatching {
            val f = file(context)
            if (!f.exists()) 0 else f.readLines().count { it.isNotBlank() }
        }.getOrDefault(0)
    }
}
