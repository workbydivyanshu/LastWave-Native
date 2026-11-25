package com.lastwave.app.data.repository

import com.lastwave.app.data.local.db.PendingScrobbleDao
import com.lastwave.app.data.local.db.PendingScrobbleEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PendingScrobbleQueue @Inject constructor(
    private val dao: PendingScrobbleDao,
) {
    suspend fun enqueue(title: String, artist: String, album: String?, timestampSec: Long, durationMs: Long = 0L) {
        dao.insert(PendingScrobbleEntity(title = title, artist = artist, album = album, timestampSec = timestampSec, durationMs = durationMs))
        val all = dao.getAll()
        if (all.size > MAX) {
            all.take(all.size - MAX).forEach { dao.delete(it) }
        }
    }

    suspend fun pending(): List<PendingScrobbleEntity> = dao.getAll()
    suspend fun remove(scrobble: PendingScrobbleEntity) = dao.delete(scrobble)
    suspend fun clear() = dao.clearAll()

    /** Flush oldest-first while [send] returns true. Returns count flushed. */
    suspend fun flush(send: suspend (PendingScrobbleEntity) -> Boolean): Int {
        var flushed = 0
        for (entry in dao.getAll()) {
            if (send(entry)) {
                dao.delete(entry)
                flushed++
            } else {
                break
            }
        }
        return flushed
    }

    private companion object { const val MAX = 200 }
}
