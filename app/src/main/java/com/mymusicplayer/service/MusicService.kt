package com.mymusicplayer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.mymusicplayer.R
import com.mymusicplayer.data.audio.MusicPlayerController
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MusicService : MediaSessionService(), KoinComponent {

    private val playerController: MusicPlayerController by inject()

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Must call startForeground() immediately to satisfy Android's timeout
        // after startForegroundService(). Media3's default notification provider
        // will update this notification once the session is active and the player
        // has media — adding album art, media controls, and proper styling.
        val startNotification = buildStartNotification()
        startForeground(NOTIFICATION_ID, startNotification)

        playerController.initialize()
        val player = playerController.getPlayer() ?: return
        mediaSession = MediaSession.Builder(this, player).build()

        // Register the session with the service so the MediaNotificationManager
        // gets initialized. This triggers an internal MediaController to bind to
        // the service — calling onGetSession() and initializing notification
        // management. Without this, no notification ever appears because our UI
        // controls the player directly rather than through a MediaController.
        addSession(mediaSession!!)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SKIP_PREVIOUS -> playerController.skipToPrevious()
            ACTION_PLAY_PAUSE -> playerController.togglePlayPause()
            ACTION_SKIP_NEXT -> playerController.skipToNext()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = playerController.getPlayer()
        if (player?.playWhenReady != true || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Now Playing",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildStartNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_music)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Starting playback...")
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "media_playback"
        private const val NOTIFICATION_ID = 1
        const val ACTION_PLAY_PAUSE = "com.mymusicplayer.action.PLAY_PAUSE"
        const val ACTION_SKIP_NEXT = "com.mymusicplayer.action.SKIP_NEXT"
        const val ACTION_SKIP_PREVIOUS = "com.mymusicplayer.action.SKIP_PREVIOUS"
    }
}
