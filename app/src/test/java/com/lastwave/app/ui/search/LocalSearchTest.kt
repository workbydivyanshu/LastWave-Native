package com.lastwave.app.ui.search

import com.google.common.truth.Truth.assertThat
import com.lastwave.app.data.local.db.DownloadedTrackEntity
import com.lastwave.app.data.search.SearchResultItem
import org.junit.Test

class LocalSearchTest {

    private val tracks = listOf(
        DownloadedTrackEntity(
            title = "Bohemian Rhapsody",
            artist = "Queen",
            album = "A Night at the Opera",
            filePath = "/music/queen/bohemian.flac",
        ),
        DownloadedTrackEntity(
            title = "Hotel California",
            artist = "Eagles",
            album = "Hotel California",
            filePath = "/music/eagles/hotel.m4a",
        ),
        DownloadedTrackEntity(
            title = "Song A",
            artist = "15 ml listens",
            album = "Singles",
            filePath = "/music/unknown/a.mp3",
        ),
    )

    private val titleThenArtist = compareBy<SearchResultItem>(
        { it.name.lowercase() },
        { it.artist?.lowercase() ?: "" },
    )

    @Test
    fun blankQueryReturnsEmpty() {
        assertThat(LocalSearch.group(tracks, "  ").isEmpty).isTrue()
    }

    @Test
    fun matchesTrackTitle() {
        val result = LocalSearch.group(tracks, "bohemian")
        assertThat(result.tracks).hasSize(1)
        assertThat(result.tracks.first().name).isEqualTo("Bohemian Rhapsody")
    }

    @Test
    fun matchesArtistName() {
        assertThat(LocalSearch.group(tracks, "queen").artists.map { it.name }).contains("Queen")
        assertThat(LocalSearch.group(tracks, "eagles").artists.map { it.name }).contains("Eagles")
    }

    @Test
    fun matchesAlbumName() {
        assertThat(LocalSearch.group(tracks, "opera").albums.map { it.name })
            .contains("A Night at the Opera")
        assertThat(LocalSearch.group(tracks, "hotel california").albums.map { it.name })
            .contains("Hotel California")
    }

    @Test
    fun caseInsensitive() {
        val result = LocalSearch.group(tracks, "QUEEN")
        assertThat(result.tracks).isNotEmpty()
        assertThat(result.artists).isNotEmpty()
    }

    @Test
    fun statArtistExcludedFromArtists() {
        val artists = LocalSearch.group(tracks, "song a").artists
        assertThat(artists.none { it.name.contains("15 ml listens", ignoreCase = true) }).isTrue()
    }

    @Test
    fun singlesAlbumExcluded() {
        val albums = LocalSearch.group(tracks, "song a").albums
        assertThat(albums.none { it.name.equals("Singles", ignoreCase = true) }).isTrue()
    }

    @Test
    fun deterministicOrdering() {
        val result = LocalSearch.group(tracks, "a")
        assertThat(result.tracks).isEqualTo(result.tracks.sortedWith(titleThenArtist))
    }
}
