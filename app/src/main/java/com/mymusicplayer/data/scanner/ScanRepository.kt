package com.mymusicplayer.data.scanner

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import com.mymusicplayer.data.db.dao.AlbumDao
import com.mymusicplayer.data.db.dao.ArtistDao
import com.mymusicplayer.data.db.dao.TrackDao
import com.mymusicplayer.data.db.entity.AlbumEntity
import com.mymusicplayer.data.db.entity.ArtistEntity
import com.mymusicplayer.data.db.entity.TrackArtistEntity
import com.mymusicplayer.data.db.entity.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

data class ScanProgress(
    val phase: ScanPhase,
    val progress: Float,
    val message: String
)

enum class ScanPhase {
    DISCOVERING,
    PARSING,
    DB_WRITE,
    COMPLETE,
    ERROR
}

class ScanRepository(
    private val context: Context,
    private val trackDao: TrackDao,
    private val artistDao: ArtistDao,
    private val albumDao: AlbumDao,
    private val mediaStoreScanner: MediaStoreScanner,
    private val metadataParser: MetadataParser,
    private val fileSystemScanner: FileSystemScanner
) {

    companion object {
        private const val TAG = "ScanRepository"
        private const val PARALLEL_PARSERS = 4
        private const val PROGRESS_INTERVAL_MS = 150L
        private val EXCLUDED_DIRS = setOf(
            "Notifications", "Ringtones", "Alarms", "podcasts"
        )
    }

    fun scanLibrary(
        excludedPaths: List<String> = emptyList(),
        scanDirectoryPath: String? = null,
        minFileSize: Long = 0L,
        minDuration: Long = 0L
    ): Flow<ScanProgress> = callbackFlow {
        try {
            trySend(ScanProgress(ScanPhase.DISCOVERING, 0f, "Discovering audio files..."))

            val allFiles = if (scanDirectoryPath != null) {
                val paths = scanDirectoryPath.split("|").filter { it.isNotBlank() }
                // Fast path: query MediaStore for files in these directories (instant, with metadata)
                val msFiles = mediaStoreScanner.scanByPaths(paths)
                val msPaths = msFiles.map { it.path }.toMutableSet()
                // Supplement: filesystem scan for anything MediaStore missed (rare but thorough).
                // Only works on Android 10- (API 29) where legacy storage may be available.
                // On Android 11+ scoped storage blocks direct file access without
                // MANAGE_EXTERNAL_STORAGE, which we no longer request for scanning.
                val fsFiles = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    paths.flatMap { path ->
                        fileSystemScanner.scanDirectory(path).filter { it.path !in msPaths }
                    }
                } else {
                    emptyList()
                }
                if (fsFiles.isNotEmpty()) {
                    Log.d(TAG, "MediaStore returned ${msFiles.size} files; " +
                            "filesystem supplement found ${fsFiles.size} more")
                }
                msFiles + fsFiles
            } else {
                mediaStoreScanner.scanAll()
            }

            val audioFiles = allFiles.filter { file ->
                if (excludedPaths.isNotEmpty() && excludedPaths.any { file.path.startsWith(it, ignoreCase = true) }) {
                    return@filter false
                }
                if (minFileSize > 0L && file.size < minFileSize) return@filter false
                true
            }

            if (audioFiles.isEmpty()) {
                trySend(ScanProgress(ScanPhase.COMPLETE, 1f, "No audio files found"))
                close()
                return@callbackFlow
            }

            trySend(ScanProgress(
                ScanPhase.DISCOVERING, 0.3f,
                "Found ${audioFiles.size} audio files"
            ))

            val existingPaths = trackDao.getAllFilePaths()
            val scannedPaths = audioFiles.map { it.path }.toSet()
            val totalFiles = audioFiles.size

            val parsedResults = mutableListOf<Pair<ScannedAudioFile, ParsedMetadata?>>()
            var lastEmitMs = System.currentTimeMillis()

            audioFiles.chunked(PARALLEL_PARSERS).forEachIndexed { batchIndex, batch ->
                val deferred = batch.map { file ->
                    async(Dispatchers.IO) {
                        // ALWAYS parse metadata directly from the file to ensure accuracy
                        // and avoid stale data from system MediaStore.
                        val meta = metadataParser.parse(file.path, extractAlbumArt = false)
                        file to meta
                    }
                }
                val batchResults = deferred.awaitAll()
                parsedResults.addAll(batchResults)

                val processed = min((batchIndex + 1) * PARALLEL_PARSERS, totalFiles)
                val now = System.currentTimeMillis()
                if (now - lastEmitMs >= PROGRESS_INTERVAL_MS || processed == totalFiles) {
                    val progress = 0.3f + (processed.toFloat() / totalFiles) * 0.5f
                    val fileName = batchResults.lastOrNull()?.first?.let {
                        File(it.path).name
                    } ?: ""
                    trySend(ScanProgress(
                        ScanPhase.PARSING, progress,
                        "Parsing $processed/$totalFiles — $fileName"
                    ))
                    lastEmitMs = now
                }
            }

            trySend(ScanProgress(ScanPhase.DB_WRITE, 0.9f, "Writing to database..."))

            withContext(Dispatchers.IO) {
                writeBatch(parsedResults, scannedPaths, minDuration)
            }

            trySend(ScanProgress(
                ScanPhase.COMPLETE, 1f,
                "Scan complete: ${parsedResults.count { it.second != null }} tracks"
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Scan failed", e)
            trySend(ScanProgress(ScanPhase.ERROR, 0f, "Scan failed: ${e.message}"))
        } finally {
            close()
        }

        awaitClose { }
    }

    suspend fun scanPaths(paths: List<String>) {
        if (paths.isEmpty()) return

        val msFiles = mediaStoreScanner.scanByPaths(paths)
        if (msFiles.isEmpty()) return

        val parsedResults = msFiles.map { file ->
            val metadata = metadataParser.parse(file.path, extractAlbumArt = false)
            file to metadata
        }

        writeBatch(parsedResults, msFiles.map { it.path }.toSet(), isFullScan = false)
    }

    private suspend fun writeBatch(
        parsedResults: List<Pair<ScannedAudioFile, ParsedMetadata?>>,
        scannedPaths: Set<String>,
        minDuration: Long = 0L,
        isFullScan: Boolean = true
    ) {
        if (isFullScan) {
            trackDao.deleteRemovedTracks(scannedPaths.toList())
        }

        val albumCache = mutableMapOf<String, Long>()
        // Track file path + content URI for each album (URI needed for scoped storage on Android 10+)
        val firstTrackPerAlbum = mutableMapOf<Long, Pair<String, Uri>>()
        val validTracks = parsedResults.mapNotNull { (file, meta) ->
            meta ?: return@mapNotNull null
            if (minDuration > 0L && meta.duration < minDuration) return@mapNotNull null
            Triple(file, meta, resolveAlbumId(meta, albumCache))
        }

        validTracks.chunked(50).forEach { chunk ->
            for ((file, metadata, albumId) in chunk) {
                val existingTrack = trackDao.getTrackByPathOnce(file.path)
                val trackId: Long

                if (existingTrack != null) {
                    val updated = existingTrack.copy(
                        title = metadata.title,
                        albumId = albumId,
                        duration = metadata.duration,
                        trackNumber = metadata.trackNumber,
                        discNumber = metadata.discNumber,
                        year = metadata.year,
                        genre = metadata.genre,
                        comment = metadata.comment,
                        fileSize = file.size,
                        bitrate = metadata.bitrate,
                        sampleRate = metadata.sampleRate,
                        format = metadata.format,
                        rawArtistTag = metadata.artists.joinToString(" / ")
                    )
                    trackDao.updateTrack(updated)
                    trackId = existingTrack.id
                } else {
                    val trackEntity = TrackEntity(
                        title = metadata.title,
                        albumId = albumId,
                        duration = metadata.duration,
                        trackNumber = metadata.trackNumber,
                        discNumber = metadata.discNumber,
                        year = metadata.year,
                        genre = metadata.genre,
                        comment = metadata.comment,
                        filePath = file.path,
                        fileSize = file.size,
                        bitrate = metadata.bitrate,
                        sampleRate = metadata.sampleRate,
                        format = metadata.format,
                        dateAdded = System.currentTimeMillis(),
                        rawArtistTag = metadata.artists.joinToString(" / ")
                    )
                    trackId = trackDao.insertTrack(trackEntity)
                }

                artistDao.deleteArtistsForTrack(trackId)
                val relations = metadata.artists.map { artistName ->
                    val artistId = resolveArtistId(artistName)
                    TrackArtistEntity(
                        trackId = trackId,
                        artistId = artistId,
                        role = "ARTIST"
                    )
                }
                artistDao.insertTrackArtistRelations(relations)

                if (albumId != null && albumId !in firstTrackPerAlbum) {
                    firstTrackPerAlbum[albumId] = Pair(file.path, file.uri)
                }
            }
        }

        artistDao.deleteOrphanedArtists()
        mergeDuplicateAlbums()

        extractAlbumArt(firstTrackPerAlbum)
    }

    /**
     * Remove all previously cached album art files.
     * Called at the start of each scan so stale/ orphaned art
     * from old scans doesn't show for albums that no longer exist.
     */
    private fun clearAlbumArtCache() {
        val artDir = File(context.cacheDir, "album_art")
        if (artDir.exists()) {
            artDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".jpg")) {
                    file.delete()
                }
            }
        }
    }

    private suspend fun extractAlbumArt(albumTrackMap: Map<Long, Pair<String, Uri>>) {
        val artDir = File(context.cacheDir, "album_art")
        artDir.mkdirs()

        for ((albumId, trackInfo) in albumTrackMap) {
            if (albumId == 0L) continue
            val (filePath, fileUri) = trackInfo
            try {
                val album = albumDao.getAlbumByIdOnce(albumId) ?: continue

                if (album.albumArtist == null) {
                    // Attempt to fill in albumArtist from first track's metadata.
                    // If jAudiotagger can't read the file (scoped storage on API 30+),
                    // this returns null — that's OK, we skip the artist update
                    // but still proceed to art extraction below.
                    val meta = metadataParser.parse(filePath, extractAlbumArt = false)
                    if (meta != null) {
                        val firstArtist = meta.artists.firstOrNull()
                        if (firstArtist != null) {
                            albumDao.updateAlbumArtist(albumId, firstArtist)
                        }
                    }
                }

                if (album.artPath == null || !File(album.artPath).exists()) {
                    var bytes: ByteArray? = null
                    // Try jAudiotagger first (handles most standard embedded art)
                    val meta = metadataParser.parse(filePath, extractAlbumArt = true)
                    if (meta != null) {
                        bytes = meta.albumArtBytes
                    }
                    // Fallback to Android MediaMetadataRetriever (wider format support, scoped-storage compatible)
                    if (bytes == null) {
                        bytes = extractArtWithMediaRetriever(filePath, fileUri)
                    }
                    if (bytes != null) {
                        val resized = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { bmp ->
                            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                            val scale = (512f / maxOf(opts.outWidth, opts.outHeight, 1)).coerceAtMost(1f)
                            val out = java.io.ByteArrayOutputStream()
                            val final = if (scale < 1f) {
                                android.graphics.Bitmap.createScaledBitmap(bmp, (opts.outWidth * scale).toInt(), (opts.outHeight * scale).toInt(), true)
                            } else bmp
                            final.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                            if (final != bmp) final.recycle()
                            out.toByteArray()
                        } ?: bytes
                        val artFile = File(artDir, "${albumId}.jpg")
                        artFile.writeBytes(resized)
                        albumDao.updateAlbumArt(albumId, artFile.absolutePath)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to extract for album $albumId", e)
            }
        }

        // Remove cached art for albums that no longer exist in the database.
        // This runs after extraction so existing albums' art is preserved
        // across re-scans (no needlessly re-extracting from audio files).
        cleanupOrphanedAlbumArt(artDir)
    }

    /**
     * Delete album art files whose album ID no longer exists in the database.
     * Called at the end of [extractAlbumArt] to prevent orphaned files from
     * accumulating without nuking the entire cache every scan.
     */
    private suspend fun cleanupOrphanedAlbumArt(artDir: File) {
        if (!artDir.exists()) return
        val allAlbumIds = albumDao.getAllAlbumsOnce().map { it.id }.toSet()
        artDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".jpg")) {
                val albumId = file.nameWithoutExtension.toLongOrNull()
                if (albumId != null && albumId !in allAlbumIds) {
                    file.delete()
                }
            }
        }
    }

    private suspend fun resolveAlbumId(
        metadata: ParsedMetadata,
        albumCache: MutableMap<String, Long>
    ): Long? {
        val albumTitle = metadata.albumTitle ?: return null
        val cacheKey = albumTitle.lowercase().trim()

        albumCache[cacheKey]?.let { return it }

        val existing = albumDao.getAlbumByTitleNormalized(albumTitle)
        if (existing != null) {
            albumCache[cacheKey] = existing.id
            return existing.id
        }

        val newAlbum = AlbumEntity(
            title = albumTitle.trim(),
            albumArtist = metadata.albumArtist,
            year = metadata.year
        )
        val albumId = albumDao.insertAlbum(newAlbum)
        albumCache[cacheKey] = albumId
        return albumId
    }

    private suspend fun resolveArtistId(artistName: String): Long {
        val existing = artistDao.getArtistByName(artistName)
        if (existing != null) return existing.id

        val newArtist = ArtistEntity(name = artistName)
        return artistDao.insertArtist(newArtist)
    }

    private fun extractArtWithMediaRetriever(filePath: String, fileUri: Uri): ByteArray? {
        // Each attempt gets its own retriever — sharing one across setDataSource calls
        // can cause native crashes (SIGSEGV) on corrupt files since the first partial
        // failure leaves internal native resources in an undefined state.
        try {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(context, fileUri)
                val art = r.embeddedPicture
                if (art != null) return art
            } finally {
                try { r.release() } catch (_: Exception) {}
            }
        } catch (_: Exception) {
            // URI approach failed — fall through to direct file path
        }
        try {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(filePath)
                return r.embeddedPicture
            } finally {
                try { r.release() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaMetadataRetriever failed for $filePath", e)
            return null
        }
    }

    private suspend fun mergeDuplicateAlbums() {
        val allAlbums = albumDao.getAllAlbumsOnce()
        val seen = mutableMapOf<String, MutableList<AlbumEntity>>()

        for (album in allAlbums) {
            val key = album.title.lowercase().trim()
            seen.getOrPut(key) { mutableListOf() }.add(album)
        }

        for ((_, duplicates) in seen) {
            if (duplicates.size <= 1) continue
            val sorted = duplicates.sortedByDescending { it.id }
            val keep = sorted.first()
            for (duplicate in sorted.drop(1)) {
                albumDao.reassignTracksToAlbum(keep.id, duplicate.id)
                albumDao.deleteAlbumById(duplicate.id)
            }
        }
    }

}
