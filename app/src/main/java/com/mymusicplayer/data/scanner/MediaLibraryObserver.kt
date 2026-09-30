package com.mymusicplayer.data.scanner

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import com.mymusicplayer.domain.repository.MusicRepository
import com.mymusicplayer.data.preferences.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaLibraryObserver(
    private val context: Context,
    private val musicRepository: MusicRepository,
    private val settingsDataStore: SettingsDataStore,
    private val scanRepository: ScanRepository
) {
    companion object {
        private const val TAG = "MediaLibraryObserver"
        private const val DEBOUNCE_MS = 3000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val thread = HandlerThread("media-library-observer").apply { start() }
    private val handler = Handler(thread.looper)
    private val pendingUris = mutableSetOf<Uri>()
    private var unknownChange = false
    private val lock = Any()

    private val flushRunnable = Runnable { flush() }

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            onMediaChanged(null)
        }

        override fun onChange(selfChange: Boolean, uri: Uri?, flags: Int) {
            onMediaChanged(uri)
        }
    }

    fun register() {
        try {
            context.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
            Log.d(TAG, "Registered MediaStore audio observer")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register observer", e)
        }
    }

    fun unregister() {
        try {
            context.contentResolver.unregisterContentObserver(observer)
        } catch (_: Exception) {
        }
        handler.removeCallbacks(flushRunnable)
        thread.quitSafely()
        synchronized(lock) {
            pendingUris.clear()
        }
    }

    private fun onMediaChanged(uri: Uri?) {
        synchronized(lock) {
            if (uri == null) unknownChange = true else pendingUris.add(uri)
        }
        handler.removeCallbacks(flushRunnable)
        handler.postDelayed(flushRunnable, DEBOUNCE_MS)
    }

    private fun flush() {
        val uris: List<Uri>
        val unknown: Boolean
        synchronized(lock) {
            uris = pendingUris.toList()
            pendingUris.clear()
            unknown = unknownChange
            unknownChange = false
        }
        if (uris.isEmpty() && !unknown) return
        scope.launch {
            val paths = resolvePaths(uris).toMutableList()
            if (unknown) {
                try {
                    val last = settingsDataStore.lastScanTimestamp.first()
                    if (last > 0L) {
                        val found = scanRepository.scanIncrementalSince(last - 120L)
                        if (found > 0) {
                            settingsDataStore.setLastScanTimestamp(System.currentTimeMillis() / 1000L)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Unknown-change incremental scan failed", e)
                }
            }
            if (paths.isNotEmpty()) {
                try {
                    musicRepository.triggerIncrementalScan(paths)
                } catch (e: Exception) {
                    Log.e(TAG, "Incremental scan trigger failed", e)
                }
            }
        }
    }

    private suspend fun resolvePaths(uris: List<Uri>): List<String> = withContext(Dispatchers.IO) {
        val out = mutableListOf<String>()
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        for (uri in uris) {
            try {
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    val col = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    while (cursor.moveToNext()) {
                        if (col >= 0) {
                            cursor.getString(col)?.takeIf { it.isNotBlank() }?.let { out.add(it) }
                        }
                    }
                }
            } catch (_: SecurityException) {
            } catch (e: Exception) {
                Log.w(TAG, "Failed to resolve $uri", e)
            }
        }
        out.distinct()
    }
}
