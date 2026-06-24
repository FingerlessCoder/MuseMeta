package com.mymusicplayer.data.scanner

import android.net.Uri
import android.util.Log
import java.io.File

data class DirectoryAudioInfo(
    val name: String,
    val path: String,
    val audioCount: Int
)

class FileSystemScanner {

    companion object {
        private const val TAG = "FileSystemScanner"

        private val AUDIO_EXTENSIONS = setOf(
            "mp3", "flac", "ogg", "wav", "wave", "m4a", "aac", "wma", "opus", "aiff", "alac"
        )

        private val EXCLUDED_DIR_NAMES = setOf(
            "android", "obb", "data", "cache", "tmp", "lost+found",
            "dalvik-cache", "app", "system", "proc", "etc"
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

    fun findDirectoriesWithAudio(rootPath: String): List<DirectoryAudioInfo> {
        val root = File(rootPath)
        if (!root.exists() || !root.isDirectory) return emptyList()

        val results = mutableListOf<DirectoryAudioInfo>()
        walkForDirs(root, results)
        results.sortBy { it.name.lowercase() }
        Log.d(TAG, "Found ${results.size} directories with audio files")
        return results
    }

    private fun walkForDirs(dir: File, results: MutableList<DirectoryAudioInfo>) {
        val files = dir.listFiles() ?: return
        var audioCount = 0
        val subDirs = mutableListOf<File>()

        for (file in files) {
            val name = file.name
            if (file.isDirectory) {
                if (!name.startsWith(".") && name.lowercase() !in EXCLUDED_DIR_NAMES) {
                    subDirs.add(file)
                }
            } else if (file.isFile && file.length() >= MIN_FILE_SIZE_BYTES) {
                val ext = file.extension.lowercase()
                if (ext in AUDIO_EXTENSIONS) {
                    audioCount++
                }
            }
        }

        if (audioCount > 0) {
            results.add(
                DirectoryAudioInfo(
                    name = dir.name,
                    path = dir.absolutePath,
                    audioCount = audioCount
                )
            )
        }

        for (subDir in subDirs) {
            walkForDirs(subDir, results)
        }
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
