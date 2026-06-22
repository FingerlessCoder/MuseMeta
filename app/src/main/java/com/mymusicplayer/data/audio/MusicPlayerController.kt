package com.mymusicplayer.data.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.mymusicplayer.data.db.dao.TrackDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentTrackId: Long? = null,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val queueSize: Int = 0,
    val queueIndex: Int = -1,
    val shuffleMode: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF
)

@Singleton
class MusicPlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackDao: TrackDao
) {

    companion object {
        private const val TAG = "MusicPlayerController"
    }

    private var exoPlayer: ExoPlayer? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var currentTrackPaths: List<String> = emptyList()
    private var currentTrackIds: List<Long> = emptyList()

    fun initialize() {
        if (exoPlayer != null) return

        exoPlayer = ExoPlayer.Builder(context).build().also { player ->
            player.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateState()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    updateState()
                    if (playbackState == Player.STATE_ENDED) {
                        onTrackCompleted()
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    updateState()
                }
            })
        }
    }

    fun play(trackPath: String, trackId: Long? = null) {
        playFromQueue(listOf(trackPath), 0, trackId?.let { listOf(it) })
    }

    fun playFromQueue(trackPaths: List<String>, startIndex: Int, trackIds: List<Long>? = null) {
        val player = exoPlayer ?: return

        currentTrackPaths = trackPaths
        currentTrackIds = trackIds ?: trackPaths.indices.map { it.toLong() }

        val mediaItems = trackPaths.map { path ->
            MediaItem.fromUri(Uri.parse("file://$path"))
        }

        player.setMediaItems(mediaItems, startIndex, 0L)
        player.prepare()
        player.play()

        updateState()
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
        updateState()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        updateState()
    }

    fun skipToNext() {
        exoPlayer?.seekToNextMediaItem()
        updateState()
    }

    fun skipToPrevious() {
        exoPlayer?.seekToPreviousMediaItem()
        updateState()
    }

    fun setShuffleMode(enabled: Boolean) {
        exoPlayer?.shuffleModeEnabled = enabled
        _playbackState.value = _playbackState.value.copy(shuffleMode = enabled)
    }

    fun setRepeatMode(mode: Int) {
        exoPlayer?.repeatMode = mode
        _playbackState.value = _playbackState.value.copy(repeatMode = mode)
    }

    fun getCurrentPosition(): Long {
        return exoPlayer?.currentPosition ?: 0L
    }

    fun getDuration(): Long {
        return exoPlayer?.duration ?: 0L
    }

    fun isPlaying(): Boolean {
        return exoPlayer?.isPlaying ?: false
    }

    fun getCurrentMediaIndex(): Int {
        return exoPlayer?.currentMediaItemIndex ?: -1
    }

    fun getQueueSize(): Int {
        return exoPlayer?.mediaItemCount ?: 0
    }

    fun getPlayer(): ExoPlayer? = exoPlayer

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }

    private fun updateState() {
        val player = exoPlayer ?: return
        val currentIndex = player.currentMediaItemIndex

        _playbackState.value = PlaybackState(
            isPlaying = player.isPlaying,
            currentTrackId = currentTrackIds.getOrNull(currentIndex),
            currentPosition = player.currentPosition,
            duration = player.duration,
            queueSize = player.mediaItemCount,
            queueIndex = currentIndex,
            shuffleMode = player.shuffleModeEnabled,
            repeatMode = player.repeatMode
        )
    }

    private fun onTrackCompleted() {
        val index = exoPlayer?.currentMediaItemIndex ?: return
        val trackId = currentTrackIds.getOrNull(index) ?: return
        Log.d(TAG, "Track completed: id=$trackId")
    }
}
