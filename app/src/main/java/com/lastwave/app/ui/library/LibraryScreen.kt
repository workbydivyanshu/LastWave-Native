@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.lastwave.app.ui.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lastwave.app.R
import com.lastwave.app.data.local.db.DownloadedTrackEntity
import com.lastwave.app.playback.PlaybackChromeState
import com.lastwave.app.ui.common.ArtworkImage
import com.lastwave.app.ui.common.ExpressiveHeader
import com.lastwave.app.ui.common.GroupPosition
import com.lastwave.app.ui.common.PredictiveBackScreen
import com.lastwave.app.ui.common.adaptiveContentWidth
import com.lastwave.app.ui.common.groupPositionFor
import com.lastwave.app.ui.common.groupShape
import com.lastwave.app.ui.common.safeDrawingBottomPadding
import com.lastwave.app.ui.player.LocalMusicPlayer
import com.lastwave.app.ui.player.PlayingWaveBars
import com.lastwave.app.ui.settings.DownloadedAlbum
import com.lastwave.app.ui.settings.DownloadedArtist
import com.lastwave.app.ui.shell.FloatingNavDefaults
import com.lastwave.app.ui.theme.ArtworkShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb >= 1000) "%.1f GB".format(mb / 1024) else "%.1f MB".format(mb)
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun formatDate(millis: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(millis))

enum class LibraryTab(val label: String) {
    SONGS("Songs"),
    ARTISTS("Artists"),
    ALBUMS("Albums"),
    FOLDERS("Folders"),
}

enum class SongSortOption(val label: String) {
    RECENT("Recently Added"),
    OLDEST("Oldest First"),
    TITLE_AZ("Title (A-Z)"),
    ARTIST_AZ("Artist (A-Z)"),
    SIZE_DESC("File Size"),
    DURATION("Duration"),
}

enum class ArtistSortOption(val label: String) {
    NAME_AZ("Name (A-Z)"),
    MOST_SONGS("Most Songs"),
    STORAGE_SIZE("Storage Size"),
}

enum class AlbumSortOption(val label: String) {
    TITLE_AZ("Title (A-Z)"),
    ARTIST_AZ("Artist (A-Z)"),
    MOST_TRACKS("Most Tracks"),
    RECENT("Recently Added"),
}

sealed interface LibrarySubView {
    data object Root : LibrarySubView
    data class ArtistDetail(val artistName: String) : LibrarySubView
    data class AlbumDetail(val albumTitle: String, val artistName: String) : LibrarySubView
}

@Composable
fun LibraryScreen(
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenDownloads: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val totalBytes by viewModel.totalBytes.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()

    val haptic = LocalHapticFeedback.current
    val musicPlayer = LocalMusicPlayer.current
    val playbackState by musicPlayer.chromeState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(LibraryTab.SONGS) }
    var subViewStack by remember { mutableStateOf(listOf<LibrarySubView>(LibrarySubView.Root)) }
    val currentSubView = subViewStack.last()

    var searchQuery by remember { mutableStateOf("") }
    var songSort by remember { mutableStateOf(SongSortOption.RECENT) }
    var artistSort by remember { mutableStateOf(ArtistSortOption.NAME_AZ) }
    var albumSort by remember { mutableStateOf(AlbumSortOption.TITLE_AZ) }
    var filterLosslessOnly by remember { mutableStateOf(false) }
    var filterLyricsOnly by remember { mutableStateOf(false) }
    var folderFilter by remember { mutableStateOf<String?>(null) }

    var showClearAllConfirm by remember { mutableStateOf(false) }
    var trackToDelete by remember { mutableStateOf<DownloadedTrackEntity?>(null) }
    var showOptionsMenu by remember { mutableStateOf(false) }

    fun navigateTo(subView: LibrarySubView) {
        subViewStack = subViewStack + subView
    }

    fun popSubView(): Boolean = if (subViewStack.size > 1) {
        subViewStack = subViewStack.dropLast(1)
        true
    } else {
        false
    }

    val totalSizeText = formatBytes(totalBytes ?: 0L)

    val artistDetailTracks = remember(tracks, currentSubView) {
        if (currentSubView is LibrarySubView.ArtistDetail) {
            tracks.filter {
                it.artist.equals(currentSubView.artistName, ignoreCase = true) ||
                    it.artist.contains(currentSubView.artistName, ignoreCase = true)
            }.sortedByDescending { it.downloadedAtMillis }
        } else {
            emptyList()
        }
    }
    val artistDetailAlbums = remember(albums, currentSubView) {
        if (currentSubView is LibrarySubView.ArtistDetail) {
            albums.filter {
                it.artist.equals(currentSubView.artistName, ignoreCase = true) ||
                    it.artist.contains(currentSubView.artistName, ignoreCase = true) ||
                    currentSubView.artistName.contains(it.artist, ignoreCase = true)
            }
        } else {
            emptyList()
        }
    }
    val albumDetailTracks = remember(tracks, currentSubView) {
        if (currentSubView is LibrarySubView.AlbumDetail) {
            tracks.filter {
                it.album.trim().equals(currentSubView.albumTitle.trim(), ignoreCase = true) &&
                    (currentSubView.artistName.isBlank() ||
                        it.artist.equals(currentSubView.artistName, ignoreCase = true) ||
                        it.artist.contains(currentSubView.artistName, ignoreCase = true) ||
                        currentSubView.artistName.contains(it.artist, ignoreCase = true))
            }
        } else {
            emptyList()
        }
    }

    PredictiveBackScreen(
        enabled = subViewStack.size > 1,
        onBack = { popSubView() },
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .adaptiveContentWidth(maxWidth = 860.dp)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            ) {
                val headerTitle = when (currentSubView) {
                    is LibrarySubView.Root -> stringResource(R.string.library_title)
                    is LibrarySubView.ArtistDetail -> currentSubView.artistName
                    is LibrarySubView.AlbumDetail -> currentSubView.albumTitle
                }
                val headerSubtitle = when (currentSubView) {
                    is LibrarySubView.Root ->
                        "${tracks.size} songs \u2022 ${artists.size} artists \u2022 $totalSizeText"
                    is LibrarySubView.ArtistDetail ->
                        "${artistDetailTracks.size} songs \u2022 ${artistDetailAlbums.size} albums \u2022 " +
                            formatBytes(artistDetailTracks.sumOf { it.fileSizeBytes })
                    is LibrarySubView.AlbumDetail ->
                        "${currentSubView.artistName} \u2022 ${albumDetailTracks.size} tracks \u2022 " +
                            formatBytes(albumDetailTracks.sumOf { it.fileSizeBytes })
                }

                ExpressiveHeader(
                    title = headerTitle,
                    subtitle = headerSubtitle,
                    onBack = if (subViewStack.size > 1) {
                        { popSubView() }
                    } else {
                        null
                    },
                    actions = {
                        IconButton(
                            onClick = onOpenSearch,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                        FilledTonalButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.scanLibrary()
                            },
                            enabled = !isScanning,
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp),
                        ) {
                            if (isScanning) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .width(16.dp)
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (isScanning) {
                                    stringResource(R.string.library_scanning)
                                } else if (tracks.isEmpty()) {
                                    stringResource(R.string.library_scan)
                                } else {
                                    stringResource(R.string.library_rescan)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                        Box {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showOptionsMenu = true
                                },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Options")
                            }
                            DropdownMenu(
                                expanded = showOptionsMenu,
                                onDismissRequest = { showOptionsMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.settings)) },
                                    leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                                    onClick = {
                                        showOptionsMenu = false
                                        onOpenSettings()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.library_rescan)) },
                                    leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                                    onClick = {
                                        showOptionsMenu = false
                                        viewModel.scanLibrary()
                                    },
                                )
                                if (tracks.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Clear library",
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Filled.DeleteSweep,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        onClick = {
                                            showOptionsMenu = false
                                            showClearAllConfirm = true
                                        },
                                    )
                                }
                            }
                        }
                    },
                )

                when (currentSubView) {
                    is LibrarySubView.Root -> {
                        if (tracks.isEmpty()) {
                            EmptyLibraryState(onScan = { viewModel.scanLibrary() })
                        } else {
                            LibraryTabBar(
                                selectedTab = selectedTab,
                                onTabSelected = { selectedTab = it },
                            )

                            LibraryFilterControls(
                                searchQuery = searchQuery,
                                onSearchQueryChange = { searchQuery = it },
                                selectedTab = selectedTab,
                                songSort = songSort,
                                onSongSortChange = { songSort = it },
                                artistSort = artistSort,
                                onArtistSortChange = { artistSort = it },
                                albumSort = albumSort,
                                onAlbumSortChange = { albumSort = it },
                                filterLossless = filterLosslessOnly,
                                onToggleLossless = { filterLosslessOnly = !filterLosslessOnly },
                                filterLyrics = filterLyricsOnly,
                                onToggleLyrics = { filterLyricsOnly = !filterLyricsOnly },
                                folderLabel = folderFilter?.let { path ->
                                    folders.firstOrNull { it.path == path }?.name ?: path
                                },
                                onClearFolder = { folderFilter = null },
                            )

                            when (selectedTab) {
                                LibraryTab.SONGS -> {
                                    val filteredTracks = remember(
                                        tracks,
                                        searchQuery,
                                        songSort,
                                        filterLosslessOnly,
                                        filterLyricsOnly,
                                        folderFilter,
                                    ) {
                                        tracks
                                            .filter {
                                                if (searchQuery.isBlank()) true
                                                else it.title.contains(searchQuery, ignoreCase = true) ||
                                                    it.artist.contains(searchQuery, ignoreCase = true) ||
                                                    it.album.contains(searchQuery, ignoreCase = true)
                                            }
                                            .filter { if (filterLosslessOnly) it.isLossless else true }
                                            .filter { if (filterLyricsOnly) it.hasLyrics else true }
                                            .filter { track ->
                                                folderFilter == null || trackParentPath(track) == folderFilter
                                            }
                                            .let { list ->
                                                when (songSort) {
                                                    SongSortOption.RECENT -> list.sortedByDescending { it.downloadedAtMillis }
                                                    SongSortOption.OLDEST -> list.sortedBy { it.downloadedAtMillis }
                                                    SongSortOption.TITLE_AZ -> list.sortedBy { it.title.lowercase() }
                                                    SongSortOption.ARTIST_AZ -> list.sortedBy { it.artist.lowercase() }
                                                    SongSortOption.SIZE_DESC -> list.sortedByDescending { it.fileSizeBytes }
                                                    SongSortOption.DURATION -> list.sortedByDescending { it.durationMs }
                                                }
                                            }
                                    }

                                    LibrarySongsList(
                                        tracks = filteredTracks,
                                        playbackState = playbackState,
                                        folderLabel = folderFilter?.let { path ->
                                            folders.firstOrNull { it.path == path }?.name ?: path
                                        },
                                        onClearFolder = { folderFilter = null },
                                        onPlayTrack = { track -> viewModel.playTrack(track, filteredTracks) },
                                        onPlayNext = { viewModel.playNext(it) },
                                        onAddToQueue = { viewModel.addToQueue(it) },
                                        onPlayAll = { shuffled -> viewModel.playTracks(filteredTracks, startShuffled = shuffled) },
                                        onSelectArtist = { navigateTo(LibrarySubView.ArtistDetail(it)) },
                                        onSelectAlbum = { title, artist -> navigateTo(LibrarySubView.AlbumDetail(title, artist)) },
                                        onDeleteTrack = { trackToDelete = it },
                                    )
                                }
                                LibraryTab.ARTISTS -> {
                                    val filteredArtists = remember(artists, searchQuery, artistSort) {
                                        artists
                                            .filter {
                                                if (searchQuery.isBlank()) true
                                                else it.name.contains(searchQuery, ignoreCase = true)
                                            }
                                            .let { list ->
                                                when (artistSort) {
                                                    ArtistSortOption.NAME_AZ -> list.sortedBy { it.name.lowercase() }
                                                    ArtistSortOption.MOST_SONGS -> list.sortedByDescending { it.tracks.size }
                                                    ArtistSortOption.STORAGE_SIZE -> list.sortedByDescending { it.totalSizeBytes }
                                                }
                                            }
                                    }

                                    LibraryArtistsList(
                                        artists = filteredArtists,
                                        onSelectArtist = { navigateTo(LibrarySubView.ArtistDetail(it.name)) },
                                        onPlayArtist = { artist -> viewModel.playTracks(artist.tracks) },
                                        onShuffleAll = { viewModel.playAll(startShuffled = true) },
                                    )
                                }
                                LibraryTab.ALBUMS -> {
                                    val filteredAlbums = remember(albums, searchQuery, albumSort) {
                                        albums
                                            .filter {
                                                if (searchQuery.isBlank()) true
                                                else it.title.contains(searchQuery, ignoreCase = true) ||
                                                    it.artist.contains(searchQuery, ignoreCase = true)
                                            }
                                            .let { list ->
                                                when (albumSort) {
                                                    AlbumSortOption.TITLE_AZ -> list.sortedBy { it.title.lowercase() }
                                                    AlbumSortOption.ARTIST_AZ -> list.sortedBy { it.artist.lowercase() }
                                                    AlbumSortOption.MOST_TRACKS -> list.sortedByDescending { it.tracks.size }
                                                    AlbumSortOption.RECENT -> list.sortedByDescending { album ->
                                                        album.tracks.maxOfOrNull { it.downloadedAtMillis } ?: 0L
                                                    }
                                                }
                                            }
                                    }

                                    LibraryAlbumsList(
                                        albums = filteredAlbums,
                                        onSelectAlbum = { navigateTo(LibrarySubView.AlbumDetail(it.title, it.artist)) },
                                        onPlayAlbum = { album -> viewModel.playTracks(album.tracks) },
                                        onShuffleAll = { viewModel.playAll(startShuffled = true) },
                                    )
                                }
                                LibraryTab.FOLDERS -> {
                                    val filteredFolders = remember(folders, searchQuery) {
                                        folders.filter {
                                            if (searchQuery.isBlank()) true
                                            else it.name.contains(searchQuery, ignoreCase = true) ||
                                                it.path.contains(searchQuery, ignoreCase = true)
                                        }
                                    }

                                    LibraryFoldersList(
                                        folders = filteredFolders,
                                        onSelectFolder = { folder ->
                                            folderFilter = folder.path
                                            selectedTab = LibraryTab.SONGS
                                        },
                                    )
                                }
                            }
                        }
                    }
                    is LibrarySubView.ArtistDetail -> {
                        if (artistDetailTracks.isEmpty() && tracks.isNotEmpty()) {
                            LaunchedEffect(Unit) { popSubView() }
                        }

                        ArtistDetailView(
                            artistName = currentSubView.artistName,
                            tracks = artistDetailTracks,
                            albums = artistDetailAlbums,
                            playbackState = playbackState,
                            onPlayTrack = { track -> viewModel.playTrack(track, artistDetailTracks) },
                            onPlayNext = { viewModel.playNext(it) },
                            onAddToQueue = { viewModel.addToQueue(it) },
                            onPlayAll = { shuffled -> viewModel.playTracks(artistDetailTracks, startShuffled = shuffled) },
                            onSelectArtist = { navigateTo(LibrarySubView.ArtistDetail(it)) },
                            onSelectAlbum = { album -> navigateTo(LibrarySubView.AlbumDetail(album.title, currentSubView.artistName)) },
                            onDeleteTrack = { trackToDelete = it },
                        )
                    }
                    is LibrarySubView.AlbumDetail -> {
                        if (albumDetailTracks.isEmpty() && tracks.isNotEmpty()) {
                            LaunchedEffect(Unit) { popSubView() }
                        }

                        AlbumDetailView(
                            albumTitle = currentSubView.albumTitle,
                            artistName = currentSubView.artistName,
                            tracks = albumDetailTracks,
                            playbackState = playbackState,
                            onPlayTrack = { track -> viewModel.playTrack(track, albumDetailTracks) },
                            onPlayNext = { viewModel.playNext(it) },
                            onAddToQueue = { viewModel.addToQueue(it) },
                            onPlayAll = { shuffled -> viewModel.playTracks(albumDetailTracks, startShuffled = shuffled) },
                            onSelectArtist = { navigateTo(LibrarySubView.ArtistDetail(it)) },
                            onDeleteTrack = { trackToDelete = it },
                        )
                    }
                }
            }
        }
    }

    trackToDelete?.let { track ->
        AlertDialog(
            onDismissRequest = { trackToDelete = null },
            title = { Text("Delete \"${track.title}\"?") },
            text = {
                Text(
                    "Delete the local audio file to free storage space, or remove it from your library history only.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTrack(track)
                        trackToDelete = null
                    },
                ) {
                    Text("Delete File", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            viewModel.deleteHistoryRecordOnly(track)
                            trackToDelete = null
                        },
                    ) {
                        Text("History Only")
                    }
                    TextButton(onClick = { trackToDelete = null }) { Text("Cancel") }
                }
            },
        )
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("Clear library?") },
            text = {
                Text(
                    "This removes all ${tracks.size} local songs and deletes their audio files from this device.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAll()
                        showClearAllConfirm = false
                    },
                ) {
                    Text("Clear Library", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun LibraryTabBar(
    selectedTab: LibraryTab,
    onTabSelected: (LibraryTab) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            LibraryTab.entries.forEach { tab ->
                val isSelected = selectedTab == tab
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
                    label = "libraryTabBg",
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = "libraryTabText",
                )

                Surface(
                    onClick = { onTabSelected(tab) },
                    shape = RoundedCornerShape(16.dp),
                    color = bgColor,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = textColor,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryFilterControls(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedTab: LibraryTab,
    songSort: SongSortOption,
    onSongSortChange: (SongSortOption) -> Unit,
    artistSort: ArtistSortOption,
    onArtistSortChange: (ArtistSortOption) -> Unit,
    albumSort: AlbumSortOption,
    onAlbumSortChange: (AlbumSortOption) -> Unit,
    filterLossless: Boolean,
    onToggleLossless: () -> Unit,
    filterLyrics: Boolean,
    onToggleLyrics: () -> Unit,
    folderLabel: String?,
    onClearFolder: () -> Unit,
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        when (selectedTab) {
                            LibraryTab.SONGS -> "Search local songs, artists, albums..."
                            LibraryTab.ARTISTS -> "Search local artists..."
                            LibraryTab.ALBUMS -> "Search local albums..."
                            LibraryTab.FOLDERS -> "Search folders..."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = "Search", modifier = Modifier.size(20.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
            )

            if (selectedTab != LibraryTab.FOLDERS) {
                Box {
                    FilledTonalButton(
                        onClick = { sortMenuExpanded = true },
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.height(52.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort",
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false },
                    ) {
                        when (selectedTab) {
                            LibraryTab.SONGS -> SongSortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option.label,
                                            fontWeight = if (songSort == option) FontWeight.Bold else FontWeight.Normal,
                                            color = if (songSort == option) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                        )
                                    },
                                    onClick = {
                                        onSongSortChange(option)
                                        sortMenuExpanded = false
                                    },
                                )
                            }
                            LibraryTab.ARTISTS -> ArtistSortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option.label,
                                            fontWeight = if (artistSort == option) FontWeight.Bold else FontWeight.Normal,
                                            color = if (artistSort == option) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                        )
                                    },
                                    onClick = {
                                        onArtistSortChange(option)
                                        sortMenuExpanded = false
                                    },
                                )
                            }
                            LibraryTab.ALBUMS -> AlbumSortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option.label,
                                            fontWeight = if (albumSort == option) FontWeight.Bold else FontWeight.Normal,
                                            color = if (albumSort == option) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                        )
                                    },
                                    onClick = {
                                        onAlbumSortChange(option)
                                        sortMenuExpanded = false
                                    },
                                )
                            }
                            LibraryTab.FOLDERS -> Unit
                        }
                    }
                }
            }
        }

        if (selectedTab == LibraryTab.SONGS) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !filterLossless && !filterLyrics,
                    onClick = {
                        if (filterLossless) onToggleLossless()
                        if (filterLyrics) onToggleLyrics()
                    },
                    label = { Text("All") },
                    shape = RoundedCornerShape(12.dp),
                )
                FilterChip(
                    selected = filterLossless,
                    onClick = onToggleLossless,
                    label = { Text("Lossless only") },
                    shape = RoundedCornerShape(12.dp),
                )
                FilterChip(
                    selected = filterLyrics,
                    onClick = onToggleLyrics,
                    label = { Text("Lyrics only") },
                    shape = RoundedCornerShape(12.dp),
                )
                if (folderLabel != null) {
                    FilterChip(
                        selected = true,
                        onClick = onClearFolder,
                        label = { Text("Folder: $folderLabel") },
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }
        } else if (folderLabel != null) {
            FilterChip(
                selected = true,
                onClick = onClearFolder,
                label = { Text("Folder: $folderLabel") },
                shape = RoundedCornerShape(12.dp),
            )
        }
    }
}

@Composable
private fun LibrarySongsList(
    tracks: List<DownloadedTrackEntity>,
    playbackState: PlaybackChromeState,
    folderLabel: String?,
    onClearFolder: () -> Unit,
    onPlayTrack: (DownloadedTrackEntity) -> Unit,
    onPlayNext: (DownloadedTrackEntity) -> Unit,
    onAddToQueue: (DownloadedTrackEntity) -> Unit,
    onPlayAll: (Boolean) -> Unit,
    onSelectArtist: (String) -> Unit,
    onSelectAlbum: (String, String) -> Unit,
    onDeleteTrack: (DownloadedTrackEntity) -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    LazyColumn(
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = 6.dp,
            bottom = libraryBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (tracks.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (folderLabel != null) "Songs in $folderLabel (${tracks.size})" else "Local Songs (${tracks.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (folderLabel != null) {
                            Text(
                                "Filtered by folder",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { onClearFolder() },
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPlayAll(true)
                            },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp),
                        ) {
                            Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Shuffle", style = MaterialTheme.typography.labelMedium)
                        }
                        FilledTonalButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPlayAll(false)
                            },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play all", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Play", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            itemsIndexed(
                items = tracks,
                key = { _, item -> item.id },
            ) { index, track ->
                val isPlayingThis = playbackState.isPlaying &&
                    playbackState.current?.title.equals(track.title, ignoreCase = true) &&
                    playbackState.current?.artist.equals(track.artist, ignoreCase = true)

                Box(Modifier.animateItem()) {
                    LibraryTrackCard(
                        track = track,
                        isPlaying = isPlayingThis,
                        position = groupPositionFor(index, tracks.size),
                        onPlay = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPlayTrack(track)
                        },
                        onPlayNext = { onPlayNext(track) },
                        onAddToQueue = { onAddToQueue(track) },
                        onSelectArtist = { onSelectArtist(track.artist) },
                        onSelectAlbum = {
                            if (track.album.isNotBlank()) onSelectAlbum(track.album, track.artist)
                        },
                        onDelete = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDeleteTrack(track)
                        },
                    )
                }
            }
        }

        if (tracks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp),
                        )
                        Text(
                            "No matching local songs",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryArtistsList(
    artists: List<DownloadedArtist>,
    onSelectArtist: (DownloadedArtist) -> Unit,
    onPlayArtist: (DownloadedArtist) -> Unit,
    onShuffleAll: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    LazyColumn(
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = 6.dp,
            bottom = libraryBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (artists.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Artists (${artists.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onShuffleAll()
                        },
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(32.dp),
                    ) {
                        Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle all", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Shuffle All", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            itemsIndexed(
                items = artists,
                key = { _, item -> item.name },
            ) { index, artist ->
                Card(
                    shape = groupShape(groupPositionFor(index, artists.size)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectArtist(artist) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(50.dp)) {
                            ArtworkImage(
                                name = artist.name,
                                artist = artist.name,
                                embeddedUrl = artist.artworkUrl,
                                fallbackIcon = Icons.Filled.Person,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                            )
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(Modifier.weight(1f)) {
                            Text(
                                text = artist.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            val albumText = if (artist.albumCount > 0) "${artist.albumCount} album(s) \u2022 " else ""
                            Text(
                                text = "$albumText${artist.tracks.size} song(s) \u2022 ${formatBytes(artist.totalSizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPlayArtist(artist)
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = "Play artist",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (artists.isEmpty()) {
            item { LibraryEmptyHint(icon = Icons.Filled.Person, text = "No local artists found") }
        }
    }
}

@Composable
private fun LibraryAlbumsList(
    albums: List<DownloadedAlbum>,
    onSelectAlbum: (DownloadedAlbum) -> Unit,
    onPlayAlbum: (DownloadedAlbum) -> Unit,
    onShuffleAll: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    LazyColumn(
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = 6.dp,
            bottom = libraryBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (albums.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Albums (${albums.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onShuffleAll()
                        },
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(32.dp),
                    ) {
                        Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle all", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Shuffle All", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            itemsIndexed(
                items = albums,
                key = { _, item -> "${item.title}_${item.artist}" },
            ) { index, album ->
                Card(
                    shape = groupShape(groupPositionFor(index, albums.size)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectAlbum(album) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(54.dp)) {
                            ArtworkImage(
                                name = album.title,
                                artist = album.artist,
                                embeddedUrl = album.artworkUrl,
                                fallbackIcon = Icons.Filled.Album,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(Modifier.weight(1f)) {
                            Text(
                                text = album.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "${album.artist} \u2022 ${album.tracks.size} track(s) \u2022 ${formatBytes(album.totalSizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPlayAlbum(album)
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = "Play album",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (albums.isEmpty()) {
            item { LibraryEmptyHint(icon = Icons.Filled.Album, text = "No local albums found") }
        }
    }
}

@Composable
private fun LibraryFoldersList(
    folders: List<LibraryFolder>,
    onSelectFolder: (LibraryFolder) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = 6.dp,
            bottom = libraryBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (folders.isNotEmpty()) {
            item {
                Text(
                    "Folders (${folders.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp),
                )
            }

            itemsIndexed(
                items = folders,
                key = { _, item -> item.path },
            ) { index, folder ->
                Card(
                    shape = groupShape(groupPositionFor(index, folders.size)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectFolder(folder) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(Modifier.weight(1f)) {
                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "${folder.trackCount} song(s) \u2022 ${formatBytes(folder.totalSizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Open folder",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        if (folders.isEmpty()) {
            item { LibraryEmptyHint(icon = Icons.Filled.Folder, text = "No folders found") }
        }
    }
}

@Composable
private fun ArtistDetailView(
    artistName: String,
    tracks: List<DownloadedTrackEntity>,
    albums: List<DownloadedAlbum>,
    playbackState: PlaybackChromeState,
    onPlayTrack: (DownloadedTrackEntity) -> Unit,
    onPlayNext: (DownloadedTrackEntity) -> Unit,
    onAddToQueue: (DownloadedTrackEntity) -> Unit,
    onPlayAll: (Boolean) -> Unit,
    onSelectArtist: (String) -> Unit,
    onSelectAlbum: (DownloadedAlbum) -> Unit,
    onDeleteTrack: (DownloadedTrackEntity) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val totalSize = tracks.sumOf { it.fileSizeBytes }
    val latestArtwork = tracks.firstOrNull { !it.artworkUrl.isNullOrBlank() }?.artworkUrl

    LazyColumn(
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = 8.dp,
            bottom = libraryBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(100.dp)) {
                        ArtworkImage(
                            name = artistName,
                            artist = artistName,
                            embeddedUrl = latestArtwork,
                            fallbackIcon = Icons.Filled.Person,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .shadow(8.dp, CircleShape),
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = artistName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = "${tracks.size} local songs \u2022 ${albums.size} album(s) \u2022 ${formatBytes(totalSize)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPlayAll(true)
                            },
                            shape = CircleShape,
                        ) {
                            Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Shuffle")
                        }
                        FilledTonalButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPlayAll(false)
                            },
                            shape = CircleShape,
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Play All")
                        }
                    }
                }
            }
        }

        if (albums.isNotEmpty()) {
            item {
                Text(
                    text = "Albums (${albums.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }

            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(albums, key = { "${it.title}_${it.artist}" }) { album ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            modifier = Modifier
                                .width(130.dp)
                                .clickable { onSelectAlbum(album) },
                        ) {
                            Column(Modifier.padding(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(114.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                ) {
                                    ArtworkImage(
                                        name = album.title,
                                        artist = album.artist,
                                        embeddedUrl = album.artworkUrl,
                                        fallbackIcon = Icons.Filled.Album,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = album.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${album.tracks.size} tracks",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Songs (${tracks.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )
        }

        itemsIndexed(
            items = tracks,
            key = { _, item -> item.id },
        ) { index, track ->
            val isPlayingThis = playbackState.isPlaying &&
                playbackState.current?.title.equals(track.title, ignoreCase = true) &&
                playbackState.current?.artist.equals(track.artist, ignoreCase = true)

            LibraryTrackCard(
                track = track,
                isPlaying = isPlayingThis,
                position = groupPositionFor(index, tracks.size),
                onPlay = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPlayTrack(track)
                },
                onPlayNext = { onPlayNext(track) },
                onAddToQueue = { onAddToQueue(track) },
                onSelectArtist = { onSelectArtist(track.artist) },
                onSelectAlbum = { if (track.album.isNotBlank()) onSelectAlbum(albumFor(track, albums)) },
                onDelete = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDeleteTrack(track)
                },
            )
        }
    }
}

@Composable
private fun AlbumDetailView(
    albumTitle: String,
    artistName: String,
    tracks: List<DownloadedTrackEntity>,
    playbackState: PlaybackChromeState,
    onPlayTrack: (DownloadedTrackEntity) -> Unit,
    onPlayNext: (DownloadedTrackEntity) -> Unit,
    onAddToQueue: (DownloadedTrackEntity) -> Unit,
    onPlayAll: (Boolean) -> Unit,
    onSelectArtist: (String) -> Unit,
    onDeleteTrack: (DownloadedTrackEntity) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val totalSize = tracks.sumOf { it.fileSizeBytes }
    val totalDuration = tracks.sumOf { it.durationMs }
    val latestArtwork = tracks.firstOrNull { !it.artworkUrl.isNullOrBlank() }?.artworkUrl

    LazyColumn(
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = 8.dp,
            bottom = libraryBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(150.dp)) {
                        ArtworkImage(
                            name = albumTitle,
                            artist = artistName,
                            embeddedUrl = latestArtwork,
                            fallbackIcon = Icons.Filled.Album,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(18.dp))
                                .shadow(10.dp, RoundedCornerShape(18.dp)),
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = albumTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = "By $artistName",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onSelectArtist(artistName) },
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = "${tracks.size} tracks \u2022 ${formatDuration(totalDuration)} \u2022 ${formatBytes(totalSize)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPlayAll(true)
                            },
                            shape = CircleShape,
                        ) {
                            Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Shuffle")
                        }
                        FilledTonalButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPlayAll(false)
                            },
                            shape = CircleShape,
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Play Album")
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Tracks (${tracks.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
        }

        itemsIndexed(
            items = tracks,
            key = { _, item -> item.id },
        ) { index, track ->
            val isPlayingThis = playbackState.isPlaying &&
                playbackState.current?.title.equals(track.title, ignoreCase = true) &&
                playbackState.current?.artist.equals(track.artist, ignoreCase = true)

            LibraryTrackCard(
                track = track,
                trackNumber = index + 1,
                isPlaying = isPlayingThis,
                position = groupPositionFor(index, tracks.size),
                onPlay = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPlayTrack(track)
                },
                onPlayNext = { onPlayNext(track) },
                onAddToQueue = { onAddToQueue(track) },
                onSelectArtist = { onSelectArtist(track.artist) },
                onSelectAlbum = {},
                onDelete = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDeleteTrack(track)
                },
            )
        }
    }
}

@Composable
private fun LibraryTrackCard(
    track: DownloadedTrackEntity,
    isPlaying: Boolean,
    position: GroupPosition,
    trackNumber: Int? = null,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onSelectArtist: () -> Unit,
    onSelectAlbum: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        shape = groupShape(position),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onPlay, onLongClick = { menuExpanded = true })
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (trackNumber != null) {
                Text(
                    text = "$trackNumber",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(24.dp),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.width(6.dp))
            }

            Box(Modifier.size(50.dp)) {
                ArtworkImage(
                    name = track.title,
                    artist = track.artist,
                    embeddedUrl = track.artworkUrl,
                    fallbackIcon = Icons.Filled.MusicNote,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(ArtworkShape),
                )
                if (isPlaying) {
                    PlayingWaveBars(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(3.dp)
                            .size(22.dp, 16.dp),
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (track.isLossless) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                    ) {
                        Text(
                            text = track.formatBadge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (track.isLossless) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                        )
                    }
                    if (track.hasLyrics) {
                        Spacer(Modifier.width(4.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                        ) {
                            Text(
                                text = "LRC",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onSelectArtist() },
                    )
                    if (track.album.isNotBlank() && !track.album.equals(track.title, ignoreCase = true)) {
                        Text(
                            text = " \u2022 ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = track.album,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { onSelectAlbum() },
                        )
                    }
                }

                Spacer(Modifier.height(1.dp))

                val sizeText = formatBytes(track.fileSizeBytes)
                val durationText = if (track.durationMs > 0) "${formatDuration(track.durationMs)} \u2022 " else ""
                Text(
                    text = "$durationText$sizeText \u2022 ${formatDate(track.downloadedAtMillis)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Surface(
                onClick = onPlay,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Play local track",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.width(2.dp))

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Song options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Play") },
                        leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onPlay()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Play Next") },
                        leadingIcon = { Icon(Icons.Filled.PlaylistAdd, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onPlayNext()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Add to Queue") },
                        leadingIcon = { Icon(Icons.Filled.QueueMusic, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onAddToQueue()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("View Artist (${track.artist})") },
                        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onSelectArtist()
                        },
                    )
                    if (track.album.isNotBlank()) {
                        DropdownMenuItem(
                            text = { Text("View Album (${track.album})") },
                            leadingIcon = { Icon(Icons.Filled.Album, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onSelectAlbum()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryEmptyHint(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(36.dp),
            )
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyLibraryState(onScan: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(88.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
            Text(
                "Your library is empty\u2026",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.library_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            FilledTonalButton(
                onClick = onScan,
                shape = CircleShape,
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.library_scan))
            }
        }
    }
}

@Composable
private fun libraryBottomPadding(): Dp =
    maxOf(FloatingNavDefaults.contentBottomPadding(), safeDrawingBottomPadding())

private fun trackParentPath(track: DownloadedTrackEntity): String {
    val raw = track.filePath.trim()
    if (raw.isBlank()) return "Unknown Folder"
    if (raw.startsWith("content://")) {
        val segments = raw.substringAfter("://").split('/').filter { it.isNotBlank() }
        return if (segments.size > 1) segments.dropLast(1).joinToString("/") else "Unknown Folder"
    }
    val parent = raw.substringBeforeLast('/', "")
    return parent.ifBlank { "Unknown Folder" }
}

private fun albumFor(
    track: DownloadedTrackEntity,
    albums: List<DownloadedAlbum>,
): DownloadedAlbum = albums.firstOrNull {
    it.title.equals(track.album, ignoreCase = true) &&
        (it.artist.equals(track.artist, ignoreCase = true) || it.artist.contains(track.artist, ignoreCase = true))
} ?: DownloadedAlbum(
    title = track.album,
    artist = track.artist,
    tracks = listOf(track),
    artworkUrl = track.artworkUrl,
    totalSizeBytes = track.fileSizeBytes,
    totalDurationMs = track.durationMs,
)
