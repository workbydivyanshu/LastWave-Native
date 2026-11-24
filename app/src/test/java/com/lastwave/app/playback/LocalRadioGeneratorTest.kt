package com.lastwave.app.playback

import com.google.common.truth.Truth.assertThat
import com.lastwave.app.data.local.db.DownloadedTrackEntity
import org.junit.Test

class LocalRadioGeneratorTest {

    private val seed = RadioSeed("Seed Song", "Queen", "AlbumA")

    private val sameAlbum = DownloadedTrackEntity(
        title = "Other Song",
        artist = "Queen",
        album = "AlbumA",
        filePath = "/m/queen/other.flac",
    )

    private val sameArtist = DownloadedTrackEntity(
        title = "Another Song",
        artist = "Queen",
        album = "AlbumB",
        filePath = "/m/queen/another.flac",
    )

    private val rest = DownloadedTrackEntity(
        title = "Hotel California",
        artist = "Eagles",
        album = "Hotel California",
        filePath = "/m/eagles/hotel.m4a",
    )

    private val played = DownloadedTrackEntity(
        title = "Played Song",
        artist = "Queen",
        album = "AlbumA",
        filePath = "/m/queen/played.flac",
    )

    private fun candidates() = listOf(rest, sameArtist, sameAlbum, played)

    @Test
    fun sameAlbumFirstThenArtistThenRest() {
        val result = LocalRadioGenerator.generate(
            seed = seed,
            candidates = candidates(),
            playedKeys = setOf("queen_played song"),
        )
        assertThat(result).hasSize(3)
        assertThat(result.first()).isEqualTo(sameAlbum)
        assertThat(result[1]).isEqualTo(sameArtist)
        assertThat(result.last()).isEqualTo(rest)
    }

    @Test
    fun seedExcluded() {
        val result = LocalRadioGenerator.generate(
            seed = seed,
            candidates = candidates(),
            playedKeys = setOf("queen_played song"),
        )
        assertThat(result.map { it.title }).doesNotContain("Seed Song")
    }

    @Test
    fun playedExcluded() {
        val result = LocalRadioGenerator.generate(
            seed = seed,
            candidates = candidates(),
            playedKeys = setOf("queen_played song"),
        )
        assertThat(result.map { it.title }).doesNotContain("Played Song")
    }

    @Test
    fun batchSizeRespected() {
        val result = LocalRadioGenerator.generate(
            seed = seed,
            candidates = candidates(),
            playedKeys = setOf("queen_played song"),
            batchSize = 1,
        )
        assertThat(result).hasSize(1)
        assertThat(result.first()).isEqualTo(sameAlbum)
    }

    @Test
    fun emptyCandidates() {
        val result = LocalRadioGenerator.generate(seed = seed, candidates = emptyList())
        assertThat(result).isEmpty()
    }
}
