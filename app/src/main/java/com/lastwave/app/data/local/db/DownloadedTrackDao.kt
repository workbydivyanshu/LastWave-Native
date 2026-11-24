package com.lastwave.app.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedTrackDao {

    @Query("SELECT * FROM downloaded_tracks ORDER BY downloadedAtMillis DESC")
    fun getAll(): Flow<List<DownloadedTrackEntity>>

    @Query("SELECT * FROM downloaded_tracks ORDER BY downloadedAtMillis DESC")
    suspend fun getAllList(): List<DownloadedTrackEntity>

    @Query("SELECT * FROM downloaded_tracks WHERE LOWER(title) = LOWER(:title) AND LOWER(artist) = LOWER(:artist) LIMIT 1")
    suspend fun findByTitleAndArtist(title: String, artist: String): DownloadedTrackEntity?

    @Query("SELECT * FROM downloaded_tracks WHERE trackKey = :trackKey LIMIT 1")
    suspend fun findByTrackKey(trackKey: String): DownloadedTrackEntity?

    @Query("SELECT * FROM downloaded_tracks WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): DownloadedTrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(track: DownloadedTrackEntity): Long

    @Query("DELETE FROM downloaded_tracks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Delete
    suspend fun delete(track: DownloadedTrackEntity)

    @Query("DELETE FROM downloaded_tracks")
    suspend fun clearAll()

    @Query("SELECT trackKey FROM downloaded_tracks")
    suspend fun getAllTrackKeys(): List<String>

    @Query("SELECT COUNT(*) FROM downloaded_tracks")
    fun count(): Flow<Int>

    @Query("SELECT SUM(fileSizeBytes) FROM downloaded_tracks")
    fun totalBytes(): Flow<Long?>

    @Query("SELECT * FROM downloaded_tracks ORDER BY title COLLATE NOCASE ASC")
    fun getAllSortedByTitle(): Flow<List<DownloadedTrackEntity>>

    @Query("SELECT * FROM downloaded_tracks ORDER BY artist COLLATE NOCASE ASC, title COLLATE NOCASE ASC")
    fun getAllSortedByArtist(): Flow<List<DownloadedTrackEntity>>

    @Query("SELECT * FROM downloaded_tracks ORDER BY downloadedAtMillis DESC")
    fun getAllSortedByDateAdded(): Flow<List<DownloadedTrackEntity>>

    @Query("SELECT * FROM downloaded_tracks ORDER BY fileSizeBytes DESC")
    fun getAllSortedByFileSize(): Flow<List<DownloadedTrackEntity>>

    @Query("SELECT * FROM downloaded_tracks ORDER BY durationMs DESC")
    fun getAllSortedByDuration(): Flow<List<DownloadedTrackEntity>>

    @Query(
        """
        SELECT * FROM downloaded_tracks
        WHERE title LIKE :prefix || '%' COLLATE NOCASE
           OR artist LIKE :prefix || '%' COLLATE NOCASE
           OR album LIKE :prefix || '%' COLLATE NOCASE
        ORDER BY title COLLATE NOCASE ASC
        """
    )
    fun searchByPrefix(prefix: String): Flow<List<DownloadedTrackEntity>>

    @Query(
        """
        SELECT * FROM downloaded_tracks
        WHERE title LIKE '%' || :query || '%' COLLATE NOCASE
           OR artist LIKE '%' || :query || '%' COLLATE NOCASE
           OR album LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY title COLLATE NOCASE ASC
        """
    )
    fun searchAll(query: String): Flow<List<DownloadedTrackEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM downloaded_tracks
        WHERE trackKey IS NULL OR TRIM(trackKey) = ''
           OR title IS NULL OR TRIM(title) = ''
           OR artist IS NULL OR TRIM(artist) = ''
           OR filePath IS NULL OR TRIM(filePath) = ''
        """
    )
    suspend fun countRowsMissingIndexedFields(): Int
}
