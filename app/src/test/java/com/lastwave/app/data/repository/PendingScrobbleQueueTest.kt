package com.lastwave.app.data.repository

import com.google.common.truth.Truth.assertThat
import com.lastwave.app.data.local.db.PendingScrobbleDao
import com.lastwave.app.data.local.db.PendingScrobbleEntity
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PendingScrobbleQueueTest {

    private class FakeDao : PendingScrobbleDao {
        val rows = mutableListOf<PendingScrobbleEntity>()
        private var nextId = 1L
        override suspend fun insert(scrobble: PendingScrobbleEntity): Long {
            val e = scrobble.copy(id = nextId++)
            rows += e
            return e.id
        }
        override suspend fun getAll(): List<PendingScrobbleEntity> =
            rows.sortedWith(compareBy({ it.timestampSec }, { it.id }))
        override suspend fun delete(scrobble: PendingScrobbleEntity) {
            rows.removeAll { it.id == scrobble.id }
        }
        override suspend fun clearAll() {
            rows.clear()
        }
    }

    @Test
    fun enqueueOfflinePersists() = runTest {
        val dao = FakeDao()
        val queue = PendingScrobbleQueue(dao)

        queue.enqueue("T", "A", null, 1000L)

        assertThat(queue.pending().size).isEqualTo(1)
    }

    @Test
    fun survivesSimulatedRestart() = runTest {
        val dao = FakeDao()
        val queue = PendingScrobbleQueue(dao)
        queue.enqueue("T", "A", null, 1000L)

        val restarted = PendingScrobbleQueue(dao)

        val pending = restarted.pending()
        assertThat(pending.size).isEqualTo(1)
        assertThat(pending.first().title).isEqualTo("T")
    }

    @Test
    fun flushOnOnlineEmptiesQueue() = runTest {
        val dao = FakeDao()
        val queue = PendingScrobbleQueue(dao)
        queue.enqueue("T1", "A", null, 1000L)
        queue.enqueue("T2", "A", null, 2000L)

        val n = queue.flush { true }

        assertThat(n).isEqualTo(2)
        assertThat(queue.pending()).isEmpty()
    }

    @Test
    fun flushStopsOnFailure() = runTest {
        val dao = FakeDao()
        val queue = PendingScrobbleQueue(dao)
        queue.enqueue("T1", "A", null, 1000L)
        queue.enqueue("T2", "A", null, 2000L)

        val n = queue.flush { false }

        assertThat(n).isEqualTo(0)
        assertThat(queue.pending().size).isEqualTo(2)
    }

    @Test
    fun clear() = runTest {
        val dao = FakeDao()
        val queue = PendingScrobbleQueue(dao)
        queue.enqueue("T", "A", null, 1000L)

        queue.clear()

        assertThat(queue.pending()).isEmpty()
    }
}
