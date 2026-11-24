package com.lastwave.app.data.local.scanner

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.lastwave.app.data.local.SettingsPreferences
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for [LocalMediaScanner]'s pure, Android-free logic:
 * duration/IS_MUSIC filtering, codec badge resolution, sidecar .lrc matching,
 * track-key canonicalization, and per-file resilience against corrupt sources.
 *
 * No Robolectric: [scanFileList] never touches the Context/SettingsPreferences,
 * so the scanner is built with relaxed mocks.
 */
class LocalMediaScannerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val scanner = LocalMediaScanner(
        mockk<Context>(relaxed = true),
        mockk<SettingsPreferences>(relaxed = true),
    )

    private fun audio(name: String): File =
        temp.newFile(name).apply { writeBytes(ByteArray(8) { 1 }) }

    private fun validExtractor(durationMs: Long = 40_000L): MetadataExtractor =
        MetadataExtractor { TrackMetadata(durationMs = durationMs) }

    // --- 1. Duration threshold -------------------------------------------------

    @Test
    fun isMusicTrack_respectsDurationThresholdAndIsMusicFlag() {
        assertThat(LocalMediaScanner.isMusicTrack(29_999L, 1)).isFalse()
        assertThat(LocalMediaScanner.isMusicTrack(30_000L, 1)).isTrue()

        // IS_MUSIC == 0 marks a system sound -> never music.
        assertThat(LocalMediaScanner.isMusicTrack(30_000L, 0)).isFalse()
        // null IS_MUSIC is treated as music.
        assertThat(LocalMediaScanner.isMusicTrack(30_000L, null)).isTrue()
    }

    @Test
    fun scanFileList_skipsShortTracksAndKeepsLongOnes() {
        val shortFile = audio("short.flac")
        val longFile = audio("long.flac")
        val extractor = MetadataExtractor { source ->
            val duration = if (source.endsWith("short.flac")) 10_000L else 40_000L
            TrackMetadata(durationMs = duration)
        }

        val result = scanner.scanFileList(listOf(shortFile, longFile), extractor)

        assertThat(result).hasSize(1)
        assertThat(result[0].filePath).isEqualTo(longFile.absolutePath)
        assertThat(result[0].durationMs).isEqualTo(40_000L)
    }

    // --- 2. Codec badge resolution --------------------------------------------

    @Test
    fun codecBadgeForExtension_mapsKnownFormats() {
        assertThat(LocalMediaScanner.codecBadgeForExtension("flac")).isEqualTo("FLAC")
        assertThat(LocalMediaScanner.codecBadgeForExtension("wav")).isEqualTo("WAV")
        assertThat(LocalMediaScanner.codecBadgeForExtension("alac")).isEqualTo("ALAC")
        assertThat(LocalMediaScanner.codecBadgeForExtension("m4a")).isEqualTo("M4A AAC")
        assertThat(LocalMediaScanner.codecBadgeForExtension("mp4")).isEqualTo("M4A AAC")
        assertThat(LocalMediaScanner.codecBadgeForExtension("aac")).isEqualTo("M4A AAC")
        assertThat(LocalMediaScanner.codecBadgeForExtension("opus")).isEqualTo("OPUS")
        assertThat(LocalMediaScanner.codecBadgeForExtension("ogg")).isEqualTo("OPUS")
    }

    @Test
    fun codecBadgeForExtension_mp3UsesBitrateForHighBitrateBadge() {
        assertThat(LocalMediaScanner.codecBadgeForExtension("mp3", 320)).isEqualTo("320k MP3")
        assertThat(LocalMediaScanner.codecBadgeForExtension("mp3", 128)).isEqualTo("MP3")
        assertThat(LocalMediaScanner.codecBadgeForExtension("mp3", null)).isEqualTo("MP3")
    }

    @Test
    fun codecBadgeForExtension_unknownExtensionFallsBackToAudio() {
        assertThat(LocalMediaScanner.codecBadgeForExtension("xyz")).isEqualTo("AUDIO")
    }

    // --- 3. Sidecar .lrc name matching ----------------------------------------

    @Test
    fun resolveSidecarLrc_matchesByFileName() {
        val match = LocalMediaScanner.resolveSidecarLrc(
            fileNameWithoutExtension = "song",
            artist = "Artist",
            title = "Title",
            dirEntries = listOf("cover.jpg", "song.lrc"),
        )
        assertThat(match).isEqualTo("song.lrc")
    }

    @Test
    fun resolveSidecarLrc_matchesByArtistAndTitle() {
        val match = LocalMediaScanner.resolveSidecarLrc(
            fileNameWithoutExtension = "track1",
            artist = "Queen",
            title = "Bohemian Rhapsody",
            dirEntries = listOf("Queen - Bohemian Rhapsody.lrc"),
        )
        assertThat(match).isEqualTo("Queen - Bohemian Rhapsody.lrc")
    }

    @Test
    fun resolveSidecarLrc_isCaseInsensitive() {
        val match = LocalMediaScanner.resolveSidecarLrc(
            fileNameWithoutExtension = "song",
            artist = "Artist",
            title = "Title",
            dirEntries = listOf("SONG.LRC"),
        )
        assertThat(match).isEqualTo("SONG.LRC")
    }

    @Test
    fun resolveSidecarLrc_returnsNullWhenNoCandidate() {
        val match = LocalMediaScanner.resolveSidecarLrc(
            fileNameWithoutExtension = "song",
            artist = "Artist",
            title = "Title",
            dirEntries = listOf("cover.jpg", "other.lrc"),
        )
        assertThat(match).isNull()
    }

    @Test
    fun scanFileList_attachesSyncedLrcSidecar() {
        val song = audio("song.flac")
        val lrc = temp.newFile("song.lrc").apply { writeText("[00:01.00]line") }

        val result = scanner.scanFileList(listOf(song), validExtractor())

        assertThat(result).hasSize(1)
        val track = result[0]
        assertThat(track.hasLyrics).isTrue()
        assertThat(track.syncedLyrics).contains("[")
        assertThat(track.plainLyrics).isNull()
        assertThat(track.lrcFilePath).endsWith("song.lrc")
        assertThat(track.lrcFilePath).isEqualTo(lrc.absolutePath)
    }

    @Test
    fun scanFileList_attachesPlainLrcSidecar() {
        val song = audio("plain.flac")
        temp.newFile("plain.lrc").apply { writeText("just plain words") }

        val result = scanner.scanFileList(listOf(song), validExtractor())

        assertThat(result).hasSize(1)
        val track = result[0]
        assertThat(track.hasLyrics).isTrue()
        assertThat(track.plainLyrics).isEqualTo("just plain words")
        assertThat(track.syncedLyrics).isNull()
    }

    // --- 4. Graceful error handling -------------------------------------------

    @Test
    fun scanFileList_continuesPastCorruptFileAndIgnoresNonAudio() {
        val good = audio("good.flac")
        val bad = audio("bad.flac")
        val notes = temp.newFile("notes.txt").apply { writeText("ignore me") }
        val extractor = MetadataExtractor { source ->
            if (source.endsWith("bad.flac")) throw RuntimeException("corrupt file")
            TrackMetadata(durationMs = 40_000L)
        }

        // Must not throw: the corrupt file is logged and skipped, the loop continues.
        val result = scanner.scanFileList(listOf(good, bad, notes), extractor)

        assertThat(result).hasSize(1)
        assertThat(result[0].filePath).isEqualTo(good.absolutePath)
    }

    // --- 5. Track-key canonicalization ----------------------------------------

    @Test
    fun makeTrackKey_canonicalizesCaseAndWhitespace() {
        assertThat(LocalMediaScanner.makeTrackKey("  Queen ", "Bohemian Rhapsody"))
            .isEqualTo("queen_bohemian rhapsody")
    }
}
