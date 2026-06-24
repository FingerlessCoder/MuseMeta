package com.mymusicplayer.data.scanner

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import java.io.File

data class MediaStoreAudioFile(
    val uri: Uri,
    val path: String,
    val size: Long,
    val dateModified: Long
)

class MediaStoreScanner constructor(
    private val contentResolver: ContentResolver
) {

    companion object {
        private const val TAG = "MediaStoreScanner"

        private val SUPPORTED_MIME_TYPES = setOf(
            "audio/mpeg",
            "audio/flac",
            "audio/mp4",
            "audio/ogg",
            "audio/wav",
            "audio/x-wav",
            "audio/x-flac"
        )

        private const val MIN_FILE_SIZE_BYTES = 10_000L
    }

    fun scanAll(): List<MediaStoreAudioFile> {
        val results = mutableListOf<MediaStoreAudioFile>()
        val collectionUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.IS_MUSIC
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        contentResolver.query(
            collectionUri,
            projection,
            selection,
            null,
            MediaStore.Audio.Media.DATE_ADDED + " DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
            val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
            val dateCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
            val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val mime = if (mimeCol >= 0) cursor.getString(mimeCol) else null
                if (mime != null && mime !in SUPPORTED_MIME_TYPES) continue

                val path = if (dataCol >= 0) cursor.getString(dataCol) else null
                if (path == null || path.isBlank()) continue

                val file = File(path)
                if (!file.exists() || !file.canRead()) continue

                val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                if (size < MIN_FILE_SIZE_BYTES) continue

                val id = if (idCol >= 0) cursor.getLong(idCol) else 0L
                val date = if (dateCol >= 0) cursor.getLong(dateCol) else 0L
                val uri = Uri.withAppendedPath(collectionUri, id.toString())

                results.add(
                    MediaStoreAudioFile(
                        uri = uri,
                        path = path,
                        size = size,
                        dateModified = date
                    )
                )
            }
        } ?: Log.w(TAG, "MediaStore query returned null cursor")

        Log.d(TAG, "Found ${results.size} audio files")
        return results
    }

    fun scanIncremental(lastScanTime: Long): List<MediaStoreAudioFile> {
        val results = mutableListOf<MediaStoreAudioFile>()
        val collectionUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.IS_MUSIC
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
                "${MediaStore.Audio.Media.DATE_MODIFIED} > $lastScanTime"

        contentResolver.query(
            collectionUri,
            projection,
            selection,
            null,
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
            val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
            val dateCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
            val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val mime = if (mimeCol >= 0) cursor.getString(mimeCol) else null
                if (mime != null && mime !in SUPPORTED_MIME_TYPES) continue

                val path = if (dataCol >= 0) cursor.getString(dataCol) else null
                if (path == null || path.isBlank()) continue
                if (!File(path).exists()) continue

                val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                if (size < MIN_FILE_SIZE_BYTES) continue

                val id = if (idCol >= 0) cursor.getLong(idCol) else 0L
                val date = if (dateCol >= 0) cursor.getLong(dateCol) else 0L
                val uri = Uri.withAppendedPath(collectionUri, id.toString())

                results.add(
                    MediaStoreAudioFile(
                        uri = uri,
                        path = path,
                        size = size,
                        dateModified = date
                    )
                )
            }
        }

        Log.d(TAG, "Incremental scan found ${results.size} new/changed files")
        return results
    }
}
