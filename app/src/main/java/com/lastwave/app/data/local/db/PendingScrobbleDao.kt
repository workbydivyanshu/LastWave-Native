package com.lastwave.app.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PendingScrobbleDao {
    @Insert
    suspend fun insert(scrobble: PendingScrobbleEntity): Long

    @Query("SELECT * FROM pending_scrobbles ORDER BY timestampSec ASC, id ASC")
    suspend fun getAll(): List<PendingScrobbleEntity>

    @Delete
    suspend fun delete(scrobble: PendingScrobbleEntity)

    @Query("DELETE FROM pending_scrobbles")
    suspend fun clearAll()
}
