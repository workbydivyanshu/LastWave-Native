package com.lastwave.app.data.lyrics

import com.lastwave.app.data.local.db.DownloadedTrackDao
import java.io.File

/** Minimal track identity for saving a companion .lrc (avoids a data->playback dep). */
data class LrcTrack(val title: String, val artist: String, val filePath: String?)

object LrcFileSaver {
    fun fileNameFor(title: String, artist: String): String {
        val base = "$artist - $title".ifBlank { title.ifBlank { "lyrics" } }
        val safe = base.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().trim('.')
        return (safe.ifBlank { "lyrics" }) + ".lrc"
    }

    fun toLrcContent(lines: List<LyricLine>, plainLyrics: String?): String {
        if (lines.isNotEmpty()) {
            return lines.joinToString("\n") { line ->
                val minutes = line.timeMs / 60_000
                val seconds = (line.timeMs % 60_000) / 1_000
                val centis = (line.timeMs % 1_000) / 10
                "[%02d:%02d.%02d] %s".format(minutes, seconds, centis, line.text)
            }
        }
        return plainLyrics.orEmpty()
    }

    /**
     * Writes a companion .lrc next to [track]'s audio file and updates the
     * matching Room row (lrcFilePath + hasLyrics=true). Returns the file, or
     * null when the track is not a local file / content is blank.
     */
    suspend fun save(track: LrcTrack, lines: List<LyricLine>, plainLyrics: String?, dao: DownloadedTrackDao): File? {
        val path = track.filePath ?: return null
        if (path.startsWith("content://")) return null
        val audio = File(path.removePrefix("file://"))
        val dir = audio.parentFile ?: return null
        val content = toLrcContent(lines, plainLyrics)
        if (content.isBlank()) return null
        val out = File(dir, fileNameFor(track.title, track.artist))
        out.writeText(content)
        val row = dao.findByTitleAndArtist(track.title, track.artist)
            ?: dao.findByTrackKey("${track.artist.trim().lowercase()}_${track.title.trim().lowercase()}")
        if (row != null) {
            dao.insert(row.copy(lrcFilePath = out.absolutePath, hasLyrics = true))
        }
        return out
    }
}
