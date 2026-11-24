package com.lastwave.app.playback

import com.lastwave.app.playback.MusicPlayer.ResolvedStream

/** Outcome of local-only stream resolution. */
sealed interface LocalResolveResult {
    data class Found(val stream: ResolvedStream) : LocalResolveResult
    data object NotFound : LocalResolveResult
}

/**
 * Local-only resolution policy: the [local] source is the ONLY one consulted.
 * [network] exists purely so callers/tests can prove it is never invoked; the
 * default throws if anything ever calls it.
 */
object LocalPlaybackPolicy {
    const val MISSING_FILE_ERROR = "Audio file not found on device"

    suspend fun resolve(
        local: suspend () -> ResolvedStream?,
        network: suspend () -> ResolvedStream? = { throw IllegalStateException("network fallback disabled") },
    ): LocalResolveResult =
        local()?.let { LocalResolveResult.Found(it) } ?: LocalResolveResult.NotFound
}
