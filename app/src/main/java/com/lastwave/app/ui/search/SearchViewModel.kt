package com.lastwave.app.ui.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastwave.app.data.local.db.DownloadedTrackDao
import com.lastwave.app.data.local.db.DownloadedTrackEntity
import com.lastwave.app.data.search.SearchHistoryRepository
import com.lastwave.app.data.search.SearchResultItem
import com.lastwave.app.playback.MusicPlayer
import com.lastwave.app.ui.settings.toPlayableTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchStatus { IDLE, LOADING, EMPTY, RESULTS }

@Immutable
data class SearchUiState(
    val query: String = "",
    val status: SearchStatus = SearchStatus.IDLE,
    val results: LocalSearchResults = LocalSearchResults(),
    val recentSearches: List<String> = emptyList(),
    val tracks: List<DownloadedTrackEntity> = emptyList(),
)

/**
 * LOCAL-ONLY search over the on-device library.
 *
 * There is no network repository and no debounce: every keystroke launches a
 * fresh Room query whose emissions are grouped by [LocalSearch]. Playback is
 * served entirely from the local [DownloadedTrackEntity] snapshot.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val dao: DownloadedTrackDao,
    private val historyRepository: SearchHistoryRepository,
    private val musicPlayer: MusicPlayer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var searchGeneration = 0

    init {
        viewModelScope.launch {
            historyRepository.history.collect { history ->
                _uiState.update { it.copy(recentSearches = history) }
            }
        }
        // Keep a full-library snapshot for local playback of any result.
        viewModelScope.launch {
            dao.getAll().collect { snapshot ->
                _uiState.update { it.copy(tracks = snapshot) }
            }
        }
    }

    /** Instant local search: no network, no debounce. Cancels the prior job. */
    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        val generation = ++searchGeneration

        if (query.isBlank()) {
            _uiState.update {
                it.copy(status = SearchStatus.IDLE, results = LocalSearchResults())
            }
            return
        }

        _uiState.update { it.copy(status = SearchStatus.LOADING) }
        searchJob = viewModelScope.launch {
            dao.searchAll(query).collect { rows ->
                if (generation != searchGeneration) return@collect
                val grouped = LocalSearch.group(rows, query)
                _uiState.update {
                    it.copy(
                        results = grouped,
                        status = if (grouped.isEmpty) SearchStatus.EMPTY else SearchStatus.RESULTS,
                    )
                }
            }
        }
    }

    fun searchNow() {
        val q = _uiState.value.query
        if (q.isBlank()) return
        executeSearch(q)
    }

    fun executeSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        historyRepository.add(trimmed)
        setQuery(trimmed)
    }

    fun playResult(item: SearchResultItem) {
        val snapshot = _uiState.value.tracks
        val entityId = item.entityId

        // Track rows carry entityId = filePath.
        if (entityId != null &&
            !entityId.startsWith("artist:") &&
            !entityId.startsWith("album:")
        ) {
            val track = snapshot.firstOrNull { it.filePath == entityId }
                ?: snapshot.firstOrNull {
                    it.title.equals(item.name, ignoreCase = true) &&
                        it.artist.equals(item.artist, ignoreCase = true)
                }
            if (track != null) {
                musicPlayer.play(track.toPlayableTrack(), sourceLabel = "Search")
            }
            return
        }

        val queue = when {
            entityId?.startsWith("artist:") == true -> {
                val name = entityId.removePrefix("artist:")
                snapshot.filter { it.artist.equals(name, ignoreCase = true) }
            }
            entityId?.startsWith("album:") == true -> {
                val parts = entityId.removePrefix("album:").split("|", limit = 2)
                val albumTitle = parts.getOrElse(0) { item.name }
                val albumArtist = parts.getOrElse(1) { item.artist.orEmpty() }
                snapshot.filter {
                    it.album.equals(albumTitle, ignoreCase = true) &&
                        it.artist.equals(albumArtist, ignoreCase = true)
                }
            }
            else -> emptyList()
        }
        if (queue.isNotEmpty()) {
            musicPlayer.playQueue(queue.map { it.toPlayableTrack() }, sourceLabel = "Search")
        }
    }

    fun removeRecentSearch(query: String) {
        historyRepository.remove(query)
    }

    fun clearRecentSearches() {
        historyRepository.clear()
    }

    fun clearQuery() {
        searchJob?.cancel()
        searchGeneration++
        _uiState.update {
            it.copy(query = "", status = SearchStatus.IDLE, results = LocalSearchResults())
        }
    }
}
