package com.lastwave.app.playback

import com.google.common.truth.Truth.assertThat
import com.lastwave.app.playback.MusicPlayer.ResolvedStream
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LocalPlaybackPolicyTest {

    @Test
    fun localStreamFound() = runTest {
        val stream = ResolvedStream(
            url = "content://local/song.flac",
            mimeType = "audio/flac",
            bitrateKbps = null,
            audioCodec = "24/96kHz",
            cacheKey = "local:queen_song",
            isLossless = true,
            bitDepth = 24,
            samplingRateKHz = 96.0,
        )
        val r = LocalPlaybackPolicy.resolve(local = { stream })
        assertThat(r).isInstanceOf(LocalResolveResult.Found::class.java)
        assertThat((r as LocalResolveResult.Found).stream.cacheKey).isEqualTo("local:queen_song")
    }

    @Test
    fun missingFileReturnsNotFound() = runTest {
        val r = LocalPlaybackPolicy.resolve(local = { null })
        assertThat(r).isEqualTo(LocalResolveResult.NotFound)
    }

    @Test
    fun neverCallsNetworkFallback() = runTest {
        var networkCalls = 0
        val r = LocalPlaybackPolicy.resolve(
            local = { null },
            network = {
                networkCalls++
                null
            },
        )
        assertThat(r).isEqualTo(LocalResolveResult.NotFound)
        assertThat(networkCalls).isEqualTo(0)
    }

    @Test
    fun missingFileErrorMessage() {
        assertThat(LocalPlaybackPolicy.MISSING_FILE_ERROR).isEqualTo("Audio file not found on device")
    }
}
