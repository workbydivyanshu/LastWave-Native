package com.lastwave.app.ui.search

import com.lastwave.app.data.local.db.DownloadedTrackEntity
import com.lastwave.app.data.search.SearchResultItem
import com.lastwave.app.util.ArtistHelper

/**
 * Result of grouping a local library into matching tracks / artists / albums.
 *
 * Pure data (no Android dependencies) so [LocalSearch] can be unit tested on
 * the JVM without an emulator.
 */
data class LocalSearchResults(
    val tracks: List<SearchResultItem> = emptyList(),
    val artists: List<SearchResultItem> = emptyList(),
    val albums: List<SearchResultItem> = emptyList(),
) {
    val isEmpty: Boolean get() = tracks.isEmpty() && artists.isEmpty() && albums.isEmpty()
}

/**
 * Pure, Android-free grouping/matching for LOCAL-ONLY search.
 *
 * The screen no longer performs any network search: it feeds locally stored
 * [DownloadedTrackEntity] rows (typically from a Room query) into [group] and
 * renders the resulting [LocalSearchResults].
 */
object LocalSearch {

    /**
     * Group DB rows into matching tracks / artists / albums for [query]
     * (case-insensitive substring match on title, artist, album).
     *
     * - Blank query -> empty [LocalSearchResults].
     * - tracks: rows where title OR artist OR album contains q.
     * - artists: distinct artist names (skipping blank / stat artists) where the
     *   artist name contains q OR the artist has at least one matching track.
     * - albums: distinct (album, artist) pairs (skipping blank album and
     *   "Singles") where album contains q OR artist contains q OR the album has
     *   a matching track.
     *
     * Ordering is deterministic: tracks by title then artist, artists by name,
     * albums by title then artist.
     */
    fun group(tracks: List<DownloadedTrackEntity>, query: String): LocalSearchResults {
        val q = query.trim()
        if (q.isEmpty()) return LocalSearchResults()
        val needle = q.lowercase()

        val matchingRows = tracks.filter { row ->
            row.title.contains(needle, ignoreCase = true) ||
                row.artist.contains(needle, ignoreCase = true) ||
                row.album.contains(needle, ignoreCase = true)
        }

        val trackItems = matchingRows
            .sortedWith(compareBy({ it.title.lowercase() }, { it.artist.lowercase() }))
            .map { row ->
                SearchResultItem(
                    name = row.title,
                    artist = row.artist,
                    subtitle = row.album,
                    artworkUrl = row.artworkUrl,
                    entityId = row.filePath,
                )
            }

        val artistNames = LinkedHashSet<String>()
        tracks.forEach { row ->
            val name = row.artist
            if (name.isNotBlank() &&
                !ArtistHelper.isPlayCountOrStat(name) &&
                name.contains(needle, ignoreCase = true)
            ) {
                artistNames.add(name)
            }
        }
        matchingRows.forEach { row ->
            val name = row.artist
            if (name.isNotBlank() && !ArtistHelper.isPlayCountOrStat(name)) {
                artistNames.add(name)
            }
        }

        val artistItems = artistNames
            .sortedWith(compareBy { it.lowercase() })
            .map { name ->
                val count = tracks.count { it.artist.equals(name, ignoreCase = true) }
                SearchResultItem(
                    name = name,
                    subtitle = "$count songs",
                    entityId = "artist:$name",
                )
            }

        val albumKeys = LinkedHashSet<Pair<String, String>>()
        tracks.forEach { row ->
            val title = row.album
            val artist = row.artist
            if (title.isNotBlank() &&
                !title.equals("Singles", ignoreCase = true) &&
                (title.contains(needle, ignoreCase = true) || artist.contains(needle, ignoreCase = true))
            ) {
                albumKeys.add(title to artist)
            }
        }
        matchingRows.forEach { row ->
            val title = row.album
            val artist = row.artist
            if (title.isNotBlank() && !title.equals("Singles", ignoreCase = true)) {
                albumKeys.add(title to artist)
            }
        }

        val albumItems = albumKeys
            .sortedWith(compareBy({ it.first.lowercase() }, { it.second.lowercase() }))
            .map { (title, artist) ->
                SearchResultItem(
                    name = title,
                    artist = artist,
                    subtitle = artist,
                    entityId = "album:$title|$artist",
                )
            }

        return LocalSearchResults(
            tracks = trackItems,
            artists = artistItems,
            albums = albumItems,
        )
    }
}
