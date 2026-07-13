package com.mymusicplayer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.mymusicplayer.R
import com.mymusicplayer.data.audio.MusicPlayerController
import com.mymusicplayer.data.audio.PlaybackMode
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MusicService : MediaSessionService(), KoinComponent {

    private val playerController: MusicPlayerController by inject()

    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        playerController.initialize()
        val player = playerController.getPlayer() ?: return

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(MySessionCallback())
            .build()

        // Must start foreground immediately. Use track info from player if available
        // so the notification shows meaningful content from the start — this helps
        // Honor/Huawei Dynamic Island recognize it as a media notification.
        val startNotification = buildTrackNotification()
        startForeground(NOTIFICATION_ID, startNotification)

        addSession(mediaSession!!)

        // Apply custom command buttons so notification shows mode + favorite
        updateCustomLayout()
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

    // ── Notification channel ──

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

    // ── Initial notification (uses track info if available) ──

    private fun buildTrackNotification(): Notification {
        val player = playerController.getPlayer()
        val mediaItem = player?.currentMediaItem
        val title = mediaItem?.mediaMetadata?.title?.toString()
            ?: getString(R.string.app_name)
        val artist = mediaItem?.mediaMetadata?.artist?.toString()
            ?: "Tap to play music"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_music)
            .setContentTitle(title)
            .setContentText(artist)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .build()
    }

    // ── Notification custom layout (mode + favorite buttons) ──

    private fun updateCustomLayout() {
        val player = playerController.getPlayer() ?: return
        val mode = playerController.getCurrentPlaybackMode()

        val modeIcon = when (mode) {
            PlaybackMode.SHUFFLE -> androidx.media3.session.R.drawable.media3_icon_shuffle_on
            PlaybackMode.LIST -> androidx.media3.session.R.drawable.media3_icon_repeat_all
            PlaybackMode.SINGLE -> androidx.media3.session.R.drawable.media3_icon_repeat_one
        }
        val modeLabel = when (mode) {
            PlaybackMode.SHUFFLE -> "Shuffle"
            PlaybackMode.LIST -> "Repeat list"
            PlaybackMode.SINGLE -> "Repeat one"
        }

        val customLayout = listOf(
            CommandButton.Builder()
                .setCustomIconResId(modeIcon)
                .setDisplayName(modeLabel)
                .setSessionCommand(SessionCommand(CUSTOM_ACTION_CYCLE_MODE, Bundle.EMPTY))
                .build(),
            CommandButton.Builder()
                .setCustomIconResId(androidx.media3.session.R.drawable.media3_icon_heart_unfilled)
                .setDisplayName("Favorite")
                .setSessionCommand(SessionCommand(CUSTOM_ACTION_TOGGLE_FAVORITE, Bundle.EMPTY))
                .build()
        )

        mediaSession?.setCustomLayout(customLayout)
    }

    // ── Session callback for custom actions ──

    private inner class MySessionCallback : MediaSession.Callback {

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            command: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (command.customAction) {
                CUSTOM_ACTION_CYCLE_MODE -> playerController.cyclePlaybackMode()
                CUSTOM_ACTION_TOGGLE_FAVORITE -> playerController.toggleCurrentTrackFavorite()
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    companion object {
        private const val CHANNEL_ID = "media_playback"
        private const val NOTIFICATION_ID = 1
        const val ACTION_PLAY_PAUSE = "com.mymusicplayer.action.PLAY_PAUSE"
        const val ACTION_SKIP_NEXT = "com.mymusicplayer.action.SKIP_NEXT"
        const val ACTION_SKIP_PREVIOUS = "com.mymusicplayer.action.SKIP_PREVIOUS"

        // Custom session commands for notification buttons
        const val CUSTOM_ACTION_CYCLE_MODE = "CUSTOM_CYCLE_MODE"
        const val CUSTOM_ACTION_TOGGLE_FAVORITE = "CUSTOM_TOGGLE_FAVORITE"
    }
}
