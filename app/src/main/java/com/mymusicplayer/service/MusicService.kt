package com.mymusicplayer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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

@Suppress("Lint")
class MusicService : MediaSessionService(), KoinComponent {

    private val playerController: MusicPlayerController by inject()

    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        playerController.initialize()
        val player = playerController.getPlayer() ?: return

        val targetIntent = Intent(this, Class.forName("com.mymusicplayer.MainActivity")).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val intentFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val sessionActivityPendingIntent = PendingIntent.getActivity(this, 0, targetIntent, intentFlags)

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(MySessionCallback())
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        val startNotification = buildTrackNotification(sessionActivityPendingIntent)
        startForeground(NOTIFICATION_ID, startNotification)

        addSession(mediaSession!!)

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

    @OptIn(UnstableApi::class)
    private fun buildTrackNotification(contentIntent: PendingIntent? = null): Notification {
        val player = playerController.getPlayer()
        val mediaItem = player?.currentMediaItem
        val title = mediaItem?.mediaMetadata?.title?.toString()
            ?: getString(R.string.app_name)
        val artist = mediaItem?.mediaMetadata?.artist?.toString()
            ?: "Tap to play music"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_music)
            .setContentTitle(title)
            .setContentText(artist)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)

        val dynamicArtwork = mediaItem?.mediaMetadata?.artworkData
        if (dynamicArtwork != null) {
            val bitmapArtwork = android.graphics.BitmapFactory.decodeByteArray(dynamicArtwork, 0, dynamicArtwork.size)
            if (bitmapArtwork != null && !bitmapArtwork.isRecycled) {
                builder.setLargeIcon(bitmapArtwork)
            }
        }

        if (contentIntent != null) {
            builder.setContentIntent(contentIntent)
        }

        return builder.build()
    }

    // ── Safe layout generation tracking custom metadata parameters completely ──

    @OptIn(UnstableApi::class)
    private fun buildCustomLayoutSpecification(): List<CommandButton> {
        val mode = playerController.getCurrentPlaybackMode()
        val isFavouriteTrack = playerController.isCurrentTrackFavourite()

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

        val favouriteIcon = if (isFavouriteTrack) {
            androidx.media3.session.R.drawable.media3_icon_heart_filled
        } else {
            androidx.media3.session.R.drawable.media3_icon_heart_unfilled
        }

        return listOf(
            CommandButton.Builder()
                .setCustomIconResId(modeIcon)
                .setDisplayName(modeLabel)
                .setSessionCommand(SessionCommand(CUSTOM_ACTION_CYCLE_MODE, Bundle.EMPTY))
                .build(),
            CommandButton.Builder()
                .setCustomIconResId(favouriteIcon)
                .setDisplayName("Favourite")
                .setSessionCommand(SessionCommand(CUSTOM_ACTION_TOGGLE_FAVORITE, Bundle.EMPTY))
                .build()
        )
    }

    @OptIn(UnstableApi::class)
    private fun updateCustomLayout() {
        mediaSession?.setCustomLayout(buildCustomLayoutSpecification())
    }

    // ── Session callback handling secure channel handshake connections ──

    private inner class MySessionCallback : MediaSession.Callback {

        @OptIn(UnstableApi::class)
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val availablePlayerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                .buildUpon()

            val availableSessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                .buildUpon()

            availableSessionCommands.add(SessionCommand(CUSTOM_ACTION_CYCLE_MODE, Bundle.EMPTY))
            availableSessionCommands.add(SessionCommand(CUSTOM_ACTION_TOGGLE_FAVORITE, Bundle.EMPTY))

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(availablePlayerCommands.build())
                .setAvailableSessionCommands(availableSessionCommands.build())
                .setCustomLayout(buildCustomLayoutSpecification())
                .build()
        }

        @OptIn(UnstableApi::class)
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            command: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (command.customAction) {
                CUSTOM_ACTION_CYCLE_MODE -> {
                    playerController.cyclePlaybackMode()
                    updateCustomLayout()
                }
                CUSTOM_ACTION_TOGGLE_FAVORITE -> {
                    playerController.toggleCurrentTrackFavorite()
                    updateCustomLayout()
                }
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

        const val CUSTOM_ACTION_CYCLE_MODE = "CUSTOM_CYCLE_MODE"
        const val CUSTOM_ACTION_TOGGLE_FAVORITE = "CUSTOM_TOGGLE_FAVORITE"
    }
}