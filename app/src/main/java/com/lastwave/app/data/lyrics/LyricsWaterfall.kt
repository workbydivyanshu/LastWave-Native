package com.lastwave.app.data.lyrics

/**
 * Strict lyrics resolution waterfall:
 *  Tier 1 local sidecar .lrc  >  Tier 2 embedded tags  >  Tier 3 online.
 * Returns the highest-priority non-empty tier, or null when all are empty.
 */
object LyricsWaterfall {
    fun resolve(
        sidecarLrc: String?,
        embeddedSynced: String?,
        embeddedPlain: String?,
        online: LyricsResult? = null,
    ): LyricsResult? {
        if (!sidecarLrc.isNullOrBlank()) {
            val lines = LyricsRepository.parseLrc(sidecarLrc)
            if (lines.isNotEmpty()) {
                return LyricsResult.Success(
                    lines = lines,
                    isSynced = true,
                    isWordSynced = lines.any { it.hasSyllables },
                    plainLyrics = null,
                    isInstrumental = false,
                    source = "Local Sidecar (LRC)",
                )
            }
        }
        if (!embeddedSynced.isNullOrBlank()) {
            val lines = LyricsRepository.parseLrc(embeddedSynced)
            if (lines.isNotEmpty()) {
                return LyricsResult.Success(
                    lines = lines,
                    isSynced = true,
                    isWordSynced = lines.any { it.hasSyllables },
                    plainLyrics = embeddedPlain,
                    isInstrumental = false,
                    source = "Embedded Lyrics (LRC)",
                )
            }
        }
        if (!embeddedPlain.isNullOrBlank()) {
            return LyricsResult.Success(
                lines = emptyList(),
                isSynced = false,
                isWordSynced = false,
                plainLyrics = embeddedPlain.trim(),
                isInstrumental = false,
                source = "Embedded Lyrics (Plain)",
            )
        }
        return online
    }
}
