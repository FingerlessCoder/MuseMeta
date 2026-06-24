package com.mymusicplayer.data.scanner

import android.net.Uri
import android.util.Log
import java.io.File

class FileSystemScanner {

    companion object {
        private const val TAG = "FileSystemScanner"

        private val AUDIO_EXTENSIONS = setOf(
            "mp3", "flac", "ogg", "wav", "wave", "m4a", "aac", "wma", "opus", "aiff", "alac"
        )

        private const val MIN_FILE_SIZE_BYTES = 10_000L
    }

    fun scanDirectory(directoryPath: String): List<MediaStoreAudioFile> {
        val dir = File(directoryPath)
        if (!dir.exists() || !dir.isDirectory) {
            Log.w(TAG, "Directory does not exist or is not a directory: $directoryPath")
            return emptyList()
        }

        val results = mutableListOf<MediaStoreAudioFile>()
        walkDirectory(dir, results)
        Log.d(TAG, "Found ${results.size} audio files in $directoryPath")
        return results
    }

    private fun walkDirectory(dir: File, results: MutableList<MediaStoreAudioFile>) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                if (!file.name.startsWith(".")) {
                    walkDirectory(file, results)
                }
            } else if (file.isFile && file.length() >= MIN_FILE_SIZE_BYTES) {
                val ext = file.extension.lowercase()
                if (ext in AUDIO_EXTENSIONS) {
                    results.add(
                        MediaStoreAudioFile(
                            uri = Uri.fromFile(file),
                            path = file.absolutePath,
                            size = file.length(),
                            dateModified = file.lastModified() / 1000
                        )
                    )
                }
            }
        }
    }
}
