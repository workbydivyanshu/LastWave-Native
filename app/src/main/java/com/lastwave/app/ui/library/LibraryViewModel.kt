package com.lastwave.app.ui.library

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastwave.app.data.local.SettingsPreferences
import com.lastwave.app.data.local.db.DownloadedTrackDao
import com.lastwave.app.data.local.db.DownloadedTrackEntity
import com.lastwave.app.data.local.scanner.LocalMediaScanner
import com.lastwave.app.playback.MusicPlayer
import com.lastwave.app.ui.settings.DownloadedAlbum
import com.lastwave.app.ui.settings.DownloadedArtist
import com.lastwave.app.ui.settings.toPlayableTrack
import com.lastwave.app.util.ArtistHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

private fun <T> Flow<T>.withLibraryFallback(fallback: T): Flow<T> =
    catch { error ->
        Log.e("LibraryViewModel", "Library storage unavailable", error)
        emit(fallback)
    }

/** One folder in the user's local library, derived from each track's parent path. */
data class LibraryFolder(
    val name: String,
    val path: String,
    val trackCount: Int,
    val totalSizeBytes: Long,
)

/**
 * Primary Library (local songs) state holder.
 *
 * Reads the shared [DownloadedTrackDao] populated by [LocalMediaScanner] and
 * exposes reactive lists plus playback actions. File deletion is best-effort
 * and intentionally decoupled from TrackDownloadManager.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext @Suppress("unused") private val context: Context,
    private val downloadedTrackDao: DownloadedTrackDao,
    private val localMediaScanner: LocalMediaScanner,
    private val musicPlayer: MusicPlayer,
    @Suppress("unused") private val settingsPreferences: SettingsPreferences,
) : ViewModel() {

    val tracks: StateFlow<List<DownloadedTrackEntity>> =
        downloadedTrackDao.getAll().withLibraryFallback(emptyList()).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    // Counter strings ("15 ml listens", "Track 16", ...) must never surface as
    // artists/albums. They collapse to "Unknown Artist" so one corrupted album
    // can't fan out into N artists / N single-track albums in the UI.
    private fun displayArtist(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.isBlank() || ArtistHelper.isPlayCountOrStat(trimmed)) {
            "Unknown Artist"
        } else {
            trimmed
        }
    }

    private fun isUsableAlbum(raw: String): Boolean {
        val trimmed = raw.trim()
        return trimmed.isNotBlank() &&
            !trimmed.equals("Singles", ignoreCase = true) &&
            !ArtistHelper.isPlayCountOrStat(trimmed)
    }

    val artists: StateFlow<List<DownloadedArtist>> = tracks.map { trackList ->
        trackList.groupBy { displayArtist(it.artist) }
            .map { (artistName, artistTracks) ->
                val uniqueAlbums = artistTracks.map { it.album.trim() }
                    .filter { isUsableAlbum(it) }
                    .map { it.lowercase() }
                    .distinct()
                val latestArtwork = artistTracks.firstOrNull { !it.artworkUrl.isNullOrBlank() }?.artworkUrl
                val totalSize = artistTracks.sumOf { it.fileSizeBytes }
                DownloadedArtist(
                    name = artistName,
                    tracks = artistTracks,
                    albumCount = uniqueAlbums.size,
                    artworkUrl = latestArtwork,
                    totalSizeBytes = totalSize,
                )
            }
            .sortedBy { it.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val albums: StateFlow<List<DownloadedAlbum>> = tracks.map { trackList ->
        val albumsList = mutableListOf<DownloadedAlbum>()
        val tracksWithAlbum = trackList.filter { isUsableAlbum(it.album) }
        val byTitle = tracksWithAlbum.groupBy { it.album.trim().lowercase() }
        for ((_, sameTitleTracks) in byTitle) {
            val artistClusters = mutableListOf<MutableList<DownloadedTrackEntity>>()
            for (track in sameTitleTracks) {
                // Stat artists all normalize to the same bucket so they never
                // split one album into N single-track albums.
                val normArtist = displayArtist(track.artist).lowercase()
                val cluster = artistClusters.firstOrNull { cluster ->
                    cluster.any { existing ->
                        val existingNorm = displayArtist(existing.artist).lowercase()
                        if (normArtist == "unknown artist" || existingNorm == "unknown artist") {
                            true
                        } else {
                            existingNorm == normArtist ||
                                existingNorm.contains(normArtist) ||
                                normArtist.contains(existingNorm)
                        }
                    }
                }
                if (cluster != null) {
                    cluster.add(track)
                } else {
                    artistClusters.add(mutableListOf(track))
                }
            }
            for (clusterTracks in artistClusters) {
                val albumTitle = clusterTracks.first().album.trim()
                val dominantArtist = clusterTracks.groupBy { displayArtist(it.artist) }
                    .maxByOrNull { it.value.size }?.key ?: displayArtist(clusterTracks.first().artist)
                val latestArtwork = clusterTracks.firstOrNull { !it.artworkUrl.isNullOrBlank() }?.artworkUrl
                val totalSize = clusterTracks.sumOf { it.fileSizeBytes }
                val totalDuration = clusterTracks.sumOf { it.durationMs }
                albumsList.add(
                    DownloadedAlbum(
                        title = albumTitle,
                        artist = dominantArtist,
                        tracks = clusterTracks,
                        artworkUrl = latestArtwork,
                        totalSizeBytes = totalSize,
                        totalDurationMs = totalDuration,
                    ),
                )
            }
        }
        albumsList.sortedBy { it.title.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Groups tracks by their parent folder path, sorted by display name. */
    val folders: StateFlow<List<LibraryFolder>> = tracks.map { trackList ->
        trackList.groupBy { parentPathOf(it) }
            .map { (path, folderTracks) ->
                val name = path.substringAfterLast('/').ifBlank { "Unknown Folder" }
                LibraryFolder(
                    name = name,
                    path = path,
                    trackCount = folderTracks.size,
                    totalSizeBytes = folderTracks.sumOf { it.fileSizeBytes },
                )
            }
            .sortedBy { it.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalBytes: StateFlow<Long?> =
        downloadedTrackDao.totalBytes().withLibraryFallback(null).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            null,
        )

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun scanLibrary() {
        if (_isScanning.value) return
        _isScanning.value = true
        viewModelScope.launch {
            try {
                val result = runCatching { localMediaScanner.scanAndStore(downloadedTrackDao) }
                result.exceptionOrNull()?.let { error ->
                    if (error is CancellationException) throw error
                    Log.e("LibraryViewModel", "Library scan failed", error)
                }
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun playTrack(track: DownloadedTrackEntity, queue: List<DownloadedTrackEntity>? = null) {
        val currentTracks = queue ?: tracks.value
        val playables = currentTracks.map { it.toPlayableTrack() }
        val startIndex = currentTracks.indexOfFirst { it.id == track.id }
            .takeIf { it >= 0 }
            ?: currentTracks.indexOfFirst {
                it.title.equals(track.title, ignoreCase = true) &&
                    it.artist.equals(track.artist, ignoreCase = true)
            }.coerceAtLeast(0)

        try {
            if (playables.isNotEmpty()) {
                musicPlayer.playQueue(
                    tracks = playables,
                    startIndex = startIndex,
                    sourceLabel = "Library",
                )
            } else {
                musicPlayer.play(track.toPlayableTrack(), sourceLabel = "Library")
            }
        } catch (error: Exception) {
            Log.e("LibraryViewModel", "Could not play library track", error)
        } catch (error: LinkageError) {
            Log.e("LibraryViewModel", "Playback unsupported on this device", error)
        }
    }

    fun playTracks(tracks: List<DownloadedTrackEntity>, startIndex: Int = 0, startShuffled: Boolean = false) {
        if (tracks.isEmpty()) return
        val playables = tracks.map { it.toPlayableTrack() }
        val actualStartIndex = if (startShuffled) playables.indices.random() else startIndex.coerceIn(0, playables.size - 1)
        try {
            musicPlayer.playQueue(
                tracks = playables,
                startIndex = actualStartIndex,
                sourceLabel = "Library",
                startShuffled = startShuffled,
            )
        } catch (error: Exception) {
            Log.e("LibraryViewModel", "Could not play library queue", error)
        } catch (error: LinkageError) {
            Log.e("LibraryViewModel", "Playback unsupported on this device", error)
        }
    }

    fun playAll(startShuffled: Boolean = false) {
        val currentTracks = tracks.value
        if (currentTracks.isEmpty()) return
        playTracks(currentTracks, startIndex = 0, startShuffled = startShuffled)
    }

    fun playNext(track: DownloadedTrackEntity) {
        try {
            musicPlayer.playNext(track.toPlayableTrack())
        } catch (error: Exception) {
            Log.e("LibraryViewModel", "Could not add to play next", error)
        } catch (error: LinkageError) {
            Log.e("LibraryViewModel", "Playback unsupported on this device", error)
        }
    }

    fun addToQueue(track: DownloadedTrackEntity) {
        try {
            musicPlayer.addToQueue(track.toPlayableTrack())
        } catch (error: Exception) {
            Log.e("LibraryViewModel", "Could not add to queue", error)
        } catch (error: LinkageError) {
            Log.e("LibraryViewModel", "Playback unsupported on this device", error)
        }
    }

    fun deleteTrack(track: DownloadedTrackEntity) {
        launchLibraryAction("delete library track") {
            downloadedTrackDao.delete(track)
            deleteLocalFile(track)
        }
    }

    fun deleteHistoryRecordOnly(track: DownloadedTrackEntity) {
        launchLibraryAction("delete library history") {
            downloadedTrackDao.delete(track)
        }
    }

    fun clearAll() {
        launchLibraryAction("clear library") {
            val snapshot = tracks.value
            downloadedTrackDao.clearAll()
            snapshot.forEach { deleteLocalFile(it) }
        }
    }

    private fun deleteLocalFile(track: DownloadedTrackEntity) {
        runCatching {
            val file = File(track.filePath)
            if (file.exists()) file.delete()
        }
    }

    private fun launchLibraryAction(action: String, block: suspend () -> Unit) =
        viewModelScope.launch {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.e("LibraryViewModel", "Failed to $action", error)
            } catch (error: LinkageError) {
                Log.e("LibraryViewModel", "Unsupported platform action: $action", error)
            }
        }

    private fun parentPathOf(track: DownloadedTrackEntity): String {
        val raw = track.filePath.trim()
        if (raw.isBlank()) return "Unknown Folder"
        if (raw.startsWith("content://")) {
            val segments = raw.substringAfter("://").split('/').filter { it.isNotBlank() }
            return if (segments.size > 1) segments.dropLast(1).joinToString("/") else "Unknown Folder"
        }
        val parent = raw.substringBeforeLast('/', "")
        return parent.ifBlank { "Unknown Folder" }
    }

}
