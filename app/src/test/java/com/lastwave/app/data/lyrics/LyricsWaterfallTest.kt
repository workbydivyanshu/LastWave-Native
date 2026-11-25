package com.lastwave.app.data.lyrics

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LyricsWaterfallTest {

    private fun online(source: String = "Online") = LyricsResult.Success(
        lines = emptyList(),
        isSynced = false,
        source = source,
    )

    @Test
    fun sidecarBeatsEmbeddedAndOnline() {
        val result = LyricsWaterfall.resolve(
            sidecarLrc = "[00:01.00] sidecar line",
            embeddedSynced = "[00:02.00] embedded line",
            embeddedPlain = null,
            online = online(),
        )

        assertThat(result).isInstanceOf(LyricsResult.Success::class.java)
        val success = result as LyricsResult.Success
        assertThat(success.source).isEqualTo("Local Sidecar (LRC)")
        assertThat(success.isSynced).isTrue()
    }

    @Test
    fun embeddedBeatsOnline() {
        val result = LyricsWaterfall.resolve(
            sidecarLrc = null,
            embeddedSynced = "[00:02.00] embedded line",
            embeddedPlain = null,
            online = online(),
        )

        assertThat(result).isInstanceOf(LyricsResult.Success::class.java)
        assertThat((result as LyricsResult.Success).source).isEqualTo("Embedded Lyrics (LRC)")
    }

    @Test
    fun embeddedPlainUsedWhenNoSynced() {
        val result = LyricsWaterfall.resolve(
            sidecarLrc = null,
            embeddedSynced = null,
            embeddedPlain = "plain words",
            online = online(),
        )

        assertThat(result).isInstanceOf(LyricsResult.Success::class.java)
        val success = result as LyricsResult.Success
        assertThat(success.source).isEqualTo("Embedded Lyrics (Plain)")
        assertThat(success.isSynced).isFalse()
        assertThat(success.plainLyrics).isEqualTo("plain words")
    }

    @Test
    fun onlineFallbackWhenOfflineEmpty() {
        val result = LyricsWaterfall.resolve(
            sidecarLrc = null,
            embeddedSynced = null,
            embeddedPlain = null,
            online = online(),
        )

        assertThat(result).isInstanceOf(LyricsResult.Success::class.java)
        assertThat((result as LyricsResult.Success).source).isEqualTo("Online")
    }

    @Test
    fun allEmptyReturnsNull() {
        val result = LyricsWaterfall.resolve(
            sidecarLrc = null,
            embeddedSynced = null,
            embeddedPlain = null,
            online = null,
        )

        assertThat(result).isNull()
    }

    @Test
    fun blankSidecarFallsThrough() {
        val result = LyricsWaterfall.resolve(
            sidecarLrc = "   ",
            embeddedSynced = "[00:02.00] e",
            embeddedPlain = null,
            online = online(),
        )

        assertThat(result).isInstanceOf(LyricsResult.Success::class.java)
        assertThat((result as LyricsResult.Success).source).isEqualTo("Embedded Lyrics (LRC)")
    }
}
