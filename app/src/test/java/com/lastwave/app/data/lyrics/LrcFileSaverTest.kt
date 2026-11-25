package com.lastwave.app.data.lyrics

import com.google.common.truth.Truth.assertThat
import com.lastwave.app.data.local.db.DownloadedTrackDao
import com.lastwave.app.data.local.db.DownloadedTrackEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LrcFileSaverTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun fileNameFor_sanitizes() {
        assertThat(LrcFileSaver.fileNameFor("Bohemian Rhapsody", "Queen"))
            .isEqualTo("Queen - Bohemian Rhapsody.lrc")

        val sanitized = LrcFileSaver.fileNameFor("A/B", "X:Y")
        assertThat(sanitized).doesNotContain("/")
        assertThat(sanitized).doesNotContain(":")
    }

    @Test
    fun toLrcContent_synced() {
        val lines = listOf(
            LyricLine(timeMs = 0, text = "Hello"),
            LyricLine(timeMs = 1500, text = "World"),
        )

        assertThat(LrcFileSaver.toLrcContent(lines, null))
            .isEqualTo("[00:00.00] Hello\n[00:01.50] World")
    }

    @Test
    fun toLrcContent_plainFallback() {
        assertThat(LrcFileSaver.toLrcContent(emptyList(), "plain text"))
            .isEqualTo("plain text")
    }

    @Test
    fun save_writesFileAndUpdatesRow() = runTest {
        val audio = folder.newFile("song.flac")
        val row = DownloadedTrackEntity(
            title = "Song",
            artist = "Artist",
            filePath = audio.absolutePath,
        )
        val dao = mockk<DownloadedTrackDao>(relaxed = true)
        coEvery { dao.findByTitleAndArtist("Song", "Artist") } returns row
        coEvery { dao.insert(any()) } returns 1L

        val out = LrcFileSaver.save(
            LrcTrack("Song", "Artist", audio.absolutePath),
            listOf(LyricLine(timeMs = 0, text = "Hi")),
            null,
            dao,
        )

        assertThat(out).isNotNull()
        assertThat(out!!.readText()).isEqualTo("[00:00.00] Hi")
        coVerify {
            dao.insert(match { it.lrcFilePath == out!!.absolutePath && it.hasLyrics })
        }
    }
}
