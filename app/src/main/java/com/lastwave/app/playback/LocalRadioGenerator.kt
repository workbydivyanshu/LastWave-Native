package com.lastwave.app.playback

import com.lastwave.app.data.local.db.DownloadedTrackEntity

data class RadioSeed(val title: String, val artist: String, val album: String)

/**
 * Offline "smart radio": builds an endless queue from LOCAL tracks only.
 * Priority: (1) other tracks from the same album, (2) other tracks from the
 * same artist, (3) a shuffle of the remaining local tracks. The seed and any
 * already-played keys are excluded. No network access.
 */
object LocalRadioGenerator {
    fun generate(
        seed: RadioSeed,
        candidates: List<DownloadedTrackEntity>,
        playedKeys: Set<String> = emptySet(),
        batchSize: Int = 25,
    ): List<DownloadedTrackEntity> {
        val seedKey = key(seed.artist, seed.title)
        val pool = candidates.filter {
            val k = key(it.artist, it.title)
            k != seedKey && k !in playedKeys
        }
        val sameAlbum = pool.filter {
            it.album.isNotBlank() && it.album.equals(seed.album, ignoreCase = true)
        }
        val sameAlbumKeys = sameAlbum.mapTo(mutableSetOf()) { key(it.artist, it.title) }
        val sameArtist = pool.filter {
            it.artist.equals(seed.artist, ignoreCase = true) &&
                key(it.artist, it.title) !in sameAlbumKeys
        }
        val sameArtistKeys = sameArtist.mapTo(mutableSetOf()) { key(it.artist, it.title) }
        val rest = pool.filter { key(it.artist, it.title) !in sameAlbumKeys && key(it.artist, it.title) !in sameArtistKeys }
            .shuffled()
        return (sameAlbum + sameArtist + rest).take(batchSize.coerceAtLeast(0))
    }

    private fun key(artist: String, title: String): String =
        "${artist.trim().lowercase()}_${title.trim().lowercase()}"
}
