package com.lastwave.app.data.local.scanner

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import com.lastwave.app.data.download.SafTreeFiles
import com.lastwave.app.data.local.DEFAULT_DOWNLOAD_FOLDER
import com.lastwave.app.data.local.SettingsPreferences
import com.lastwave.app.data.local.db.DownloadedTrackDao
import com.lastwave.app.data.local.db.DownloadedTrackEntity
import com.lastwave.app.data.local.sanitizeDownloadFolderName
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain model produced by the scanner (decoupled from Room).
 *
 * Kept free of any Android/Room types so unit tests can construct and assert
 * on it directly; [toEntity] is the single mapping point into persistence.
 */
data class ScannedTrack(
    val trackKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val filePath: String,
    val mediaStoreUri: String? = null,
    val fileSizeBytes: Long = 0L,
    val formatBadge: String = "AUDIO",
    val durationMs: Long = 0L,
    val bitrateKbps: Int? = null,
    val year: Int? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val isLossless: Boolean = false,
    val hasLyrics: Boolean = false,
    val syncedLyrics: String? = null,
    val plainLyrics: String? = null,
    val lrcFilePath: String? = null,
    val dateAddedMillis: Long = System.currentTimeMillis(),
) {
    /** Maps this domain model onto the Room row. [DownloadedTrackEntity] is
     *  never referenced by callers, keeping the scanner testable in isolation. */
    fun toEntity(): DownloadedTrackEntity = DownloadedTrackEntity(
        trackKey = trackKey,
        title = title,
        artist = artist,
        album = album,
        filePath = filePath,
        mediaStoreUri = mediaStoreUri,
        fileSizeBytes = fileSizeBytes,
        formatBadge = formatBadge,
        durationMs = durationMs,
        bitrateKbps = bitrateKbps,
        isLossless = isLossless ||
            formatBadge.equals("FLAC", ignoreCase = true) ||
            filePath.substringAfterLast('.', "").equals("flac", ignoreCase = true),
        hasLyrics = hasLyrics,
        syncedLyrics = syncedLyrics,
        plainLyrics = plainLyrics,
        lrcFilePath = lrcFilePath,
        downloadedAtMillis = dateAddedMillis,
    )
}

/**
 * Injectable so unit tests can simulate corrupt/unreadable files.
 *
 * [extract] receives a filesystem path OR a `content://` uri string.
 */
fun interface MetadataExtractor {
    /** @throws Exception when the source is corrupt/unreadable. */
    fun extract(source: String): TrackMetadata
}

/** Raw tag values pulled from a single source; every field is optional. */
data class TrackMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
    val bitrateKbps: Int? = null,
    val year: Int? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
)

/**
 * Universal, fully-offline local media scanner.
 *
 * Indexes three sources into a common [ScannedTrack] list:
 *  1. the platform MediaStore audio collection,
 *  2. user-granted SAF document trees (from settings, or explicit), and
 *  3. the default public `Music/<downloadFolder>` directory.
 *
 * No network access is ever performed; failures are logged via [Log] and the
 * scan continues so a single corrupt file can never abort the whole run.
 */
@Singleton
class LocalMediaScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsPreferences: SettingsPreferences,
) {
    companion object {
        const val MIN_DURATION_MS = 30_000L
        val AUDIO_EXTENSIONS: Set<String> =
            setOf("flac", "alac", "wav", "mp3", "m4a", "mp4", "aac", "opus", "ogg")

        private const val TAG = "LocalMediaScanner"

        /** Legacy MediaStore data column, unavailable as a constant in recent SDKs. */
        private const val MEDIA_DATA_COLUMN = "_data"

        /** Noise/system-sound filter: IS_MUSIC != 0 (null treated as music) AND duration >= 30s. */
        fun isMusicTrack(durationMs: Long, isMusic: Int?): Boolean =
            (isMusic == null || isMusic != 0) && durationMs >= MIN_DURATION_MS

        /** Codec badge. mp3 -> "320k MP3" when bitrateKbps != null && >= 320, else "MP3". */
        fun codecBadgeForExtension(ext: String, bitrateKbps: Int? = null): String = when (ext.lowercase()) {
            "flac" -> "FLAC"
            "alac" -> "ALAC"
            "wav" -> "WAV"
            "mp3" -> if (bitrateKbps != null && bitrateKbps >= 320) "320k MP3" else "MP3"
            "m4a", "mp4", "aac" -> "M4A AAC"
            "opus", "ogg" -> "OPUS"
            else -> "AUDIO"
        }

        /** Canonical key: "${artist.lowercase().trim()}_${title.lowercase().trim()}". */
        fun makeTrackKey(artist: String, title: String): String =
            "${artist.lowercase().trim()}_${title.lowercase().trim()}"

        /** Returns the matching .lrc file NAME from [dirEntries] (case-insensitive),
         *  trying "${fileNameWithoutExtension}.lrc" first, then "${artist} - ${title}.lrc". */
        fun resolveSidecarLrc(
            fileNameWithoutExtension: String,
            artist: String,
            title: String,
            dirEntries: List<String>,
        ): String? {
            val byLower = dirEntries.associateBy { it.lowercase() }
            byLower["$fileNameWithoutExtension.lrc".lowercase()]?.let { return it }
            return byLower["$artist - $title.lrc".lowercase()]
        }
    }

    /** Full universal scan: MediaStore Audio + SAF trees (from settings, or explicit) + default download dir. */
    suspend fun scan(treeUris: List<String> = emptyList()): List<ScannedTrack> =
        withContext(Dispatchers.IO) {
            val extractor = defaultExtractor()
            val results = mutableListOf<ScannedTrack>()

            results += scanMediaStore()

            val trees = if (treeUris.isNotEmpty()) {
                treeUris
            } else {
                runCatching { settingsPreferences.settings.first().downloadTreeUri }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { listOf(it) }
                    .orEmpty()
            }
            for (tree in trees) {
                results += scanTree(tree, extractor)
            }

            defaultDownloadDir()?.let { dir ->
                results += scanFileList(collectAudioFiles(dir), extractor)
            }

            results
        }

    /** Scan + persist via the DAO, de-duplicating by trackKey. Returns (inserted, skipped). */
    suspend fun scanAndStore(
        dao: DownloadedTrackDao,
        treeUris: List<String> = emptyList(),
    ): Pair<Int, Int> {
        val scanned = scan(treeUris)
        val existing = runCatching { dao.getAllTrackKeys() }
            .getOrDefault(emptyList())
            .toHashSet()
        var inserted = 0
        var skipped = 0
        for (track in scanned) {
            if (track.trackKey in existing) {
                skipped++
                continue
            }
            runCatching { dao.insert(track.toEntity()) }
                .onSuccess {
                    inserted++
                    existing += track.trackKey
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed inserting ${track.trackKey}", error)
                    skipped++
                }
        }
        return inserted to skipped
    }

    // Visible-for-test internal entry point (no Android framework needed):
    internal fun scanFileList(
        files: List<File>,
        extractor: MetadataExtractor,
        lrcDirEntries: (File) -> List<String> = { dir -> dir?.list()?.toList().orEmpty() },
    ): List<ScannedTrack> {
        val results = mutableListOf<ScannedTrack>()
        for (file in files) {
            try {
                if (!file.isFile) continue
                val ext = file.extension.lowercase()
                if (ext !in AUDIO_EXTENSIONS) continue

                val metadata = extractor.extract(file.absolutePath)
                val durationMs = metadata.durationMs
                if (durationMs != null && durationMs < MIN_DURATION_MS) continue

                val nameWithoutExt = file.nameWithoutExtension
                val title = metadata.title?.trim()?.takeIf { it.isNotBlank() } ?: nameWithoutExt
                val artist = metadata.artist?.trim()?.takeIf { it.isNotBlank() }
                    ?: file.parentFile?.name?.takeIf { it.isNotBlank() }
                    ?: "Unknown Artist"
                val album = metadata.album?.trim().orEmpty()
                val bitrateKbps = metadata.bitrateKbps
                val badge = codecBadgeForExtension(ext, bitrateKbps)

                val dir = file.parentFile
                val entries = dir?.let { lrcDirEntries(it) }.orEmpty()
                val lrcName = resolveSidecarLrc(nameWithoutExt, artist, title, entries)

                var syncedLyrics: String? = null
                var plainLyrics: String? = null
                var hasLyrics = false
                var lrcFilePath: String? = null
                if (lrcName != null && dir != null) {
                    val lrcFile = File(dir, lrcName)
                    val text = runCatching { if (lrcFile.isFile) lrcFile.readText() else null }.getOrNull()
                    if (!text.isNullOrEmpty()) {
                        hasLyrics = true
                        lrcFilePath = lrcFile.absolutePath
                        if (text.contains("[")) syncedLyrics = text else plainLyrics = text
                    }
                }

                results += ScannedTrack(
                    trackKey = makeTrackKey(artist, title),
                    title = title,
                    artist = artist,
                    album = album,
                    filePath = file.absolutePath,
                    mediaStoreUri = null,
                    fileSizeBytes = runCatching { file.length() }.getOrDefault(0L),
                    formatBadge = badge,
                    durationMs = durationMs ?: 0L,
                    bitrateKbps = bitrateKbps,
                    year = metadata.year,
                    trackNumber = metadata.trackNumber,
                    discNumber = metadata.discNumber,
                    isLossless = ext == "flac",
                    hasLyrics = hasLyrics,
                    syncedLyrics = syncedLyrics,
                    plainLyrics = plainLyrics,
                    lrcFilePath = lrcFilePath,
                    dateAddedMillis = runCatching { file.lastModified() }.getOrDefault(System.currentTimeMillis()),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (oom: OutOfMemoryError) {
                Log.e(TAG, "Out of memory scanning ${file.name}", oom)
            } catch (error: Exception) {
                Log.e(TAG, "Failed scanning ${file.name}", error)
            }
        }
        return results
    }

    /** Wraps [MediaMetadataRetriever]; release() always runs in `finally`. */
    private fun defaultExtractor(): MetadataExtractor = MetadataExtractor { source ->
        val retriever = MediaMetadataRetriever()
        try {
            if (source.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(source))
            } else {
                retriever.setDataSource(source)
            }
            fun read(key: Int): String? = runCatching { retriever.extractMetadata(key) }.getOrNull()
            TrackMetadata(
                title = read(MediaMetadataRetriever.METADATA_KEY_TITLE)?.ifBlank { null },
                artist = read(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.ifBlank { null },
                album = read(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.ifBlank { null },
                durationMs = read(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                bitrateKbps = read(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.let { it / 1000 },
                year = read(MediaMetadataRetriever.METADATA_KEY_YEAR)?.toIntOrNull(),
                discNumber = read(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)?.toIntOrNull(),
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    @Suppress("DEPRECATION")
    private fun scanMediaStore(): List<ScannedTrack> {
        val results = mutableListOf<ScannedTrack>()
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.IS_MUSIC)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.YEAR)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
            } else {
                add(MEDIA_DATA_COLUMN)
            }
        }.toTypedArray()

        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                null,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val durCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val isMusicCol = cursor.getColumnIndex(MediaStore.Audio.Media.IS_MUSIC)
                val dateCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                val yearCol = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
                val relCol = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                val dataCol = cursor.getColumnIndex(MEDIA_DATA_COLUMN)

                while (cursor.moveToNext()) {
                    if (idCol < 0) continue
                    val id = cursor.getLong(idCol)
                    val durationMs = if (durCol >= 0) cursor.getLong(durCol) else 0L
                    val isMusic = if (isMusicCol >= 0 && !cursor.isNull(isMusicCol)) cursor.getInt(isMusicCol) else null
                    if (!isMusicTrack(durationMs, isMusic)) continue

                    val mediaStoreUri = Uri.withAppendedPath(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id.toString(),
                    ).toString()
                    val rawTitle = if (titleCol >= 0) cursor.getString(titleCol) else null
                    val rawArtist = if (artistCol >= 0) cursor.getString(artistCol) else null
                    val rawAlbum = if (albumCol >= 0) cursor.getString(albumCol) else null
                    val title = rawTitle?.trim()?.takeIf { it.isNotBlank() } ?: "Unknown Track"
                    val artist = rawArtist?.trim()?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
                    val album = rawAlbum?.trim().orEmpty()
                    val mime = (if (mimeCol >= 0) cursor.getString(mimeCol) else null).orEmpty().lowercase()
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val dateAddedSec = if (dateCol >= 0) cursor.getLong(dateCol) else 0L
                    val year = if (yearCol >= 0) cursor.getInt(yearCol).takeIf { it > 0 } else null

                    val data = if (dataCol >= 0) cursor.getString(dataCol) else null
                    val relative = if (relCol >= 0) cursor.getString(relCol) else null
                    val filePath = data?.takeIf { it.isNotBlank() } ?: relative.orEmpty()

                    val ext = filePath.substringAfterLast('.', "").lowercase()
                        .ifBlank { mime.substringAfterLast('.', "").lowercase() }
                        .ifBlank {
                            when {
                                mime.contains("flac") -> "flac"
                                mime.contains("mp4") || mime.contains("m4a") || mime.contains("aac") -> "m4a"
                                mime.contains("mpeg") || mime.contains("mp3") -> "mp3"
                                mime.contains("opus") || mime.contains("ogg") -> "opus"
                                else -> ""
                            }
                        }

                    results += ScannedTrack(
                        trackKey = makeTrackKey(artist, title),
                        title = title,
                        artist = artist,
                        album = album,
                        filePath = filePath,
                        mediaStoreUri = mediaStoreUri,
                        fileSizeBytes = size,
                        formatBadge = codecBadgeForExtension(ext),
                        durationMs = durationMs,
                        bitrateKbps = null,
                        year = year,
                        isLossless = ext == "flac" || mime.contains("flac"),
                        dateAddedMillis = if (dateAddedSec > 0) dateAddedSec * 1000L else System.currentTimeMillis(),
                    )
                }
            }
        }.onFailure { error -> Log.e(TAG, "MediaStore scan failed", error) }
        return results
    }

    private fun scanTree(treeUriString: String, extractor: MetadataExtractor): List<ScannedTrack> {
        val results = mutableListOf<ScannedTrack>()
        val treeUri = SafTreeFiles.parseTreeUri(treeUriString) ?: return results
        if (!SafTreeFiles.hasPersistedAccess(context, treeUriString)) {
            Log.w(TAG, "No persisted SAF access for $treeUriString")
            return results
        }
        val rootDocUri = runCatching {
            val treeId = DocumentsContract.getTreeDocumentId(treeUri)
            DocumentsContract.buildDocumentUriUsingTree(treeUri, treeId)
        }.getOrNull() ?: return results

        walkTree(context.contentResolver, treeUri, rootDocUri, extractor, results)
        return results
    }

    /** Depth-first walk; lists a directory's children once so sidecar [resolveSidecarLrc]
     *  has the full name set before any audio file in that directory is processed. */
    private fun walkTree(
        resolver: ContentResolver,
        treeUri: Uri,
        dirDocUri: Uri,
        extractor: MetadataExtractor,
        out: MutableList<ScannedTrack>,
    ) {
        val childrenUri = runCatching {
            val dirId = DocumentsContract.getDocumentId(dirDocUri)
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirId)
        }.getOrNull() ?: return

        val childNames = mutableListOf<String>()
        val childDocUris = mutableListOf<Uri>()
        val childIsDir = mutableListOf<Boolean>()
        val childSizes = mutableListOf<Long>()
        val nameToUri = HashMap<String, Uri>()

        runCatching {
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                while (cursor.moveToNext()) {
                    if (idCol < 0 || nameCol < 0) continue
                    val id = cursor.getString(idCol) ?: continue
                    val name = cursor.getString(nameCol) ?: continue
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol) else null
                    val size = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                    childDocUris += docUri
                    childNames += name
                    childIsDir += (mime == DocumentsContract.Document.MIME_TYPE_DIR)
                    childSizes += size
                    nameToUri[name.lowercase()] = docUri
                }
            }
        }.onFailure { error -> Log.e(TAG, "Failed listing $dirDocUri", error) }

        for (i in childDocUris.indices) {
            val docUri = childDocUris[i]
            val name = childNames[i]
            if (childIsDir[i]) {
                walkTree(resolver, treeUri, docUri, extractor, out)
                continue
            }
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext !in AUDIO_EXTENSIONS) continue

            val scanned = runCatching {
                val metadata = extractor.extract(docUri.toString())
                val durationMs = metadata.durationMs
                if (durationMs != null && durationMs < MIN_DURATION_MS) return@runCatching null
                buildSafTrack(docUri, name, ext, childSizes[i], metadata, nameToUri, resolver)
            }.getOrNull()
            if (scanned != null) out += scanned
        }
    }

    private fun buildSafTrack(
        docUri: Uri,
        fileName: String,
        ext: String,
        fileSize: Long,
        metadata: TrackMetadata,
        nameToUri: Map<String, Uri>,
        resolver: ContentResolver,
    ): ScannedTrack {
        val nameWithoutExt = fileName.substringBeforeLast('.')
        val title = metadata.title?.trim()?.takeIf { it.isNotBlank() }
            ?: nameWithoutExt.ifBlank { fileName }
        val artist = metadata.artist?.trim()?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
        val album = metadata.album?.trim().orEmpty()
        val bitrateKbps = metadata.bitrateKbps

        val lrcName = resolveSidecarLrc(nameWithoutExt, artist, title, nameToUri.keys.toList())
        var syncedLyrics: String? = null
        var plainLyrics: String? = null
        var hasLyrics = false
        var lrcFilePath: String? = null
        if (lrcName != null) {
            val lrcUri = nameToUri[lrcName.lowercase()]
            if (lrcUri != null) {
                val text = runCatching {
                    resolver.openInputStream(lrcUri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                }.getOrNull()
                if (!text.isNullOrEmpty()) {
                    hasLyrics = true
                    lrcFilePath = lrcUri.toString()
                    if (text.contains("[")) syncedLyrics = text else plainLyrics = text
                }
            }
        }

        return ScannedTrack(
            trackKey = makeTrackKey(artist, title),
            title = title,
            artist = artist,
            album = album,
            filePath = docUri.toString(),
            mediaStoreUri = null,
            fileSizeBytes = fileSize,
            formatBadge = codecBadgeForExtension(ext, bitrateKbps),
            durationMs = metadata.durationMs ?: 0L,
            bitrateKbps = bitrateKbps,
            year = metadata.year,
            trackNumber = metadata.trackNumber,
            discNumber = metadata.discNumber,
            isLossless = ext == "flac",
            hasLyrics = hasLyrics,
            syncedLyrics = syncedLyrics,
            plainLyrics = plainLyrics,
            lrcFilePath = lrcFilePath,
        )
    }

    @Suppress("DEPRECATION")
    private suspend fun defaultDownloadDir(): File? {
        val folder = runCatching { settingsPreferences.settings.first().downloadFolder }
            .getOrNull()
            ?.let { runCatching { sanitizeDownloadFolderName(it) }.getOrNull() }
            ?: DEFAULT_DOWNLOAD_FOLDER
        val music = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        return File(music, folder).takeIf { it.isDirectory }
    }

    private fun collectAudioFiles(dir: File): List<File> {
        val out = mutableListOf<File>()
        fun walk(current: File) {
            val children = current.listFiles() ?: return
            for (child in children) {
                when {
                    child.isDirectory -> walk(child)
                    child.isFile && child.extension.lowercase() in AUDIO_EXTENSIONS -> out += child
                }
            }
        }
        walk(dir)
        return out
    }

}
