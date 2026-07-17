package com.mymusicplayer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mymusicplayer.MainActivity
import com.mymusicplayer.data.preferences.SettingsDataStore
import com.mymusicplayer.data.scanner.ScanPhase
import com.mymusicplayer.data.scanner.ScanRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ScanService : Service(), KoinComponent {

    private val scanRepository: ScanRepository by inject()
    private val settingsDataStore: SettingsDataStore by inject()

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var scanJob: Job? = null

    companion object {
        const val ACTION_START_SCAN = "com.mymusicplayer.action.START_SCAN"
        const val ACTION_SCAN_PATHS = "com.mymusicplayer.action.SCAN_PATHS"
        const val EXTRA_PATHS = "extra_paths"
        const val CHANNEL_ID = "scan_service"
        const val NOTIFICATION_ID = 2
        private const val TAG = "ScanService"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SCAN -> {
                // Cancel any running scan before starting a new one
                scanJob?.cancel()
                startScan()
            }
            ACTION_SCAN_PATHS -> {
                val paths = intent.getStringArrayListExtra(EXTRA_PATHS)
                if (paths != null) {
                    serviceScope.launch {
                        scanRepository.scanPaths(paths)
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startScan() {
        val notification = buildNotification("Scanning library...", 0, false)
        startForeground(NOTIFICATION_ID, notification)

        scanJob = serviceScope.launch {
            // Read user-configured scan settings
            val excludedDirs = settingsDataStore.excludedDirs.first()
            val scanDir = settingsDataStore.scanDirectoryPath.first()
            val minFileSizeKb = settingsDataStore.scanMinFileSize.first()
            val minDurationSec = settingsDataStore.scanMinDuration.first()

            scanRepository.scanLibrary(
                excludedPaths = excludedDirs,
                scanDirectoryPath = scanDir?.ifBlank { null },
                minFileSize = minFileSizeKb * 1024L,
                minDuration = minDurationSec * 1000L
            ).collectLatest { progress ->
                val message = progress.message
                val isComplete = progress.phase == ScanPhase.COMPLETE || progress.phase == ScanPhase.ERROR

                val notif = buildNotification(message, (progress.progress * 100).toInt(), isComplete)
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, notif)

                if (isComplete) {
                    if (progress.phase == ScanPhase.COMPLETE) {
                        settingsDataStore.setScanCompletedOnce()
                    }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Library Scan",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows progress when scanning music library"
        }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(message: String, progress: Int, isComplete: Boolean): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MuseMeta")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(pendingIntent)
            .setOngoing(!isComplete)
            .setProgress(100, progress, false)
            .build()
    }

    override fun onDestroy() {
        scanJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
