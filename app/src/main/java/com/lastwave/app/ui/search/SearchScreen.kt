package com.lastwave.app.ui.search

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lastwave.app.data.search.SearchResultItem
import com.lastwave.app.ui.common.ArtworkImage
import com.lastwave.app.ui.common.ExpressiveMotion
import com.lastwave.app.ui.common.HeaderActionIcon
import com.lastwave.app.ui.common.adaptiveContentWidth
import com.lastwave.app.ui.common.safeDrawingBottomPadding
import com.lastwave.app.ui.common.safeHorizontalContentPadding
import com.lastwave.app.ui.player.LocalMiniPlayerScrollClearance

private val SearchHeaderShape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)

/**
 * LOCAL-ONLY library search: header with back + input + clear, recent-search
 * history on an empty query, and grouped track / artist / album results.
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit = {},
    onOpenArtist: (name: String, browseId: String?) -> Unit = { _, _ -> },
    onOpenAlbum: (title: String, artist: String, browseId: String?) -> Unit = { _, _, _ -> },
    onOpenPlaylist: (playlistId: String) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    Column(
        Modifier
            .fillMaxSize()
            .adaptiveContentWidth(maxWidth = 860.dp),
    ) {
        Surface(
            shape = SearchHeaderShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    )
                    .padding(horizontal = 16.dp)
                    .padding(top = 6.dp, bottom = 10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeaderActionIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                    Spacer(Modifier.width(10.dp))

                    val pillBg = MaterialTheme.colorScheme.surfaceContainerHighest

                    BasicTextField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                focusManager.clearFocus()
                                viewModel.searchNow()
                            },
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        decorationBox = { innerTextField ->
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(pillBg)
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = if (state.query.isNotEmpty()) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f)
                                    },
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    if (state.query.isEmpty()) {
                                        Text(
                                            text = "Search your library\u2026",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    innerTextField()
                                }
                                if (state.query.isNotEmpty()) {
                                    IconButton(
                                        onClick = viewModel::clearQuery,
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(
                                            Icons.Filled.Clear,
                                            contentDescription = "Clear",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }

        Box(Modifier.fillMaxSize().safeHorizontalContentPadding()) {
            when {
                state.query.isBlank() -> HistoryContent(
                    recentSearches = state.recentSearches,
                    viewModel = viewModel,
                    focusManager = focusManager,
                )

                else -> Crossfade(
                    targetState = state.status,
                    animationSpec = tween(ExpressiveMotion.Standard, easing = FastOutSlowInEasing),
                    label = "searchState",
                ) { status ->
                    when (status) {
                        SearchStatus.IDLE -> Box(Modifier.fillMaxSize())
                        SearchStatus.LOADING -> Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            com.lastwave.app.ui.common.ExpressiveLoadingIndicator()
                        }
                        SearchStatus.EMPTY -> Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(44.dp),
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "No results found for \"${state.query}\"",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        SearchStatus.RESULTS -> ResultsContent(
                            state = state,
                            viewModel = viewModel,
                            onOpenArtist = onOpenArtist,
                            onOpenAlbum = onOpenAlbum,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryContent(
    recentSearches: List<String>,
    viewModel: SearchViewModel,
    focusManager: FocusManager,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            top = 12.dp,
            bottom = 24.dp + LocalMiniPlayerScrollClearance.current + safeDrawingBottomPadding(),
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (recentSearches.isNotEmpty()) {
            item(key = "history_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Recent searches",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = viewModel::clearRecentSearches) {
                        Text("Clear all", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            items(recentSearches, key = { "hist_$it" }) { recentQuery ->
                RecentSearchRow(
                    text = recentQuery,
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.executeSearch(recentQuery)
                    },
                    onDelete = { viewModel.removeRecentSearch(recentQuery) },
                )
            }
        }
    }
}

@Composable
private fun ResultsContent(
    state: SearchUiState,
    viewModel: SearchViewModel,
    onOpenArtist: (name: String, browseId: String?) -> Unit,
    onOpenAlbum: (title: String, artist: String, browseId: String?) -> Unit,
) {
    val results = state.results
    LazyColumn(
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = 24.dp + LocalMiniPlayerScrollClearance.current + safeDrawingBottomPadding(),
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (results.tracks.isNotEmpty()) {
            item(key = "tracks_header") { SectionHeader("Matching Tracks") }
            items(results.tracks, key = { "track_${it.entityId}" }) { item ->
                ResultRow(
                    item = item,
                    fallbackIcon = Icons.Filled.MusicNote,
                    onClick = { viewModel.playResult(item) },
                )
            }
        }
        if (results.artists.isNotEmpty()) {
            item(key = "artists_header") { SectionHeader("Matching Artists") }
            items(results.artists, key = { "artist_${it.entityId}" }) { item ->
                ResultRow(
                    item = item,
                    fallbackIcon = Icons.Filled.Person,
                    circular = true,
                    onClick = { onOpenArtist(item.name, item.entityId) },
                )
            }
        }
        if (results.albums.isNotEmpty()) {
            item(key = "albums_header") { SectionHeader("Matching Albums") }
            items(results.albums, key = { "album_${it.entityId}" }) { item ->
                ResultRow(
                    item = item,
                    fallbackIcon = Icons.Filled.Album,
                    onClick = { onOpenAlbum(item.name, item.artist.orEmpty(), item.entityId) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ResultRow(
    item: SearchResultItem,
    fallbackIcon: ImageVector,
    circular: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(if (circular) CircleShape else RoundedCornerShape(10.dp)),
        ) {
            ArtworkImage(
                name = item.name,
                artist = item.artist.orEmpty(),
                embeddedUrl = item.artworkUrl,
                fallbackIcon = fallbackIcon,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = item.subtitle.orEmpty()
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RecentSearchRow(
    text: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
        )
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Filled.Clear,
                contentDescription = "Remove from history",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
