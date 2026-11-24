package com.lastwave.app.playback

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LoudnessNormalizerLocalGainTest {

    @Test
    fun replayGainTrackTagApplied() {
        val tags = LoudnessNormalizer.parseFromMap(mapOf("REPLAYGAIN_TRACK_GAIN" to "-7.5 dB"))
        assertThat(LoudnessNormalizer.gainForTags(tags, LoudnessMode.TRACK))
            .isWithin(0.001f)
            .of(-7.5f)
    }

    @Test
    fun r128TrackTagApplied() {
        val tags = LoudnessNormalizer.parseFromMap(mapOf("R128_TRACK_GAIN" to "-3.0 dB"))
        assertThat(LoudnessNormalizer.gainForTags(tags, LoudnessMode.TRACK))
            .isWithin(0.001f)
            .of(-3.0f)
    }

    @Test
    fun offModeIsZero() {
        val tags = LoudnessNormalizer.parseFromMap(mapOf("REPLAYGAIN_TRACK_GAIN" to "-7.5 dB"))
        assertThat(LoudnessNormalizer.gainForTags(tags, LoudnessMode.OFF)).isEqualTo(0f)
    }

    @Test
    fun albumFallsBackToTrack() {
        val tags = LoudnessNormalizer.parseFromMap(mapOf("REPLAYGAIN_TRACK_GAIN" to "-4.0 dB"))
        assertThat(LoudnessNormalizer.gainForTags(tags, LoudnessMode.ALBUM))
            .isWithin(0.001f)
            .of(-4.0f)
    }
}
