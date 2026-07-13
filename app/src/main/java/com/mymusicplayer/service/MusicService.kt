package com.mymusicplayer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.collect.ImmutableList
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
        // after startForegroundService(). Media3 will update this notification
        // once the session is connected.
        val startNotification = buildStartNotification()
        startForeground(NOTIFICATION_ID, startNotification)

        playerController.initialize()
        val player = playerController.getPlayer() ?: return
        mediaSession = MediaSession.Builder(this, player).build()

        val notificationProvider = object : MediaNotification.Provider {
            override fun createNotification(
                session: MediaSession,
                customActionButtons: ImmutableList<CommandButton>,
                actionFactory: MediaNotification.ActionFactory,
                callback: MediaNotification.Provider.Callback
            ): MediaNotification {
                return MediaNotification(NOTIFICATION_ID, buildNotification(session))
            }

            override fun handleCustomCommand(
                session: MediaSession,
                action: String,
                extras: Bundle
            ): Boolean = false
        }
        setMediaNotificationProvider(notificationProvider)
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

    private fun buildNotification(session: MediaSession): Notification {
        val player = session.player
        val metadata = player.currentMediaItem?.mediaMetadata

        val launchIntent = packageManager?.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_music)
            .setContentTitle(metadata?.title?.toString() ?: "Unknown")
            .setContentText(metadata?.artist?.toString() ?: "Unknown Artist")
            .setStyle(
                MediaStyle()
                    .setMediaSession(session.sessionCompatToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(player.playWhenReady)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, launchIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )

        builder.addAction(
            R.drawable.ic_notification_music,
            "Previous",
            transportActionIntent(ACTION_SKIP_PREVIOUS)
        )
        builder.addAction(
            R.drawable.ic_notification_music,
            if (player.isPlaying) "Pause" else "Play",
            transportActionIntent(ACTION_PLAY_PAUSE)
        )
        builder.addAction(
            R.drawable.ic_notification_music,
            "Next",
            transportActionIntent(ACTION_SKIP_NEXT)
        )

        return builder.build()
    }

    private fun transportActionIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
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
