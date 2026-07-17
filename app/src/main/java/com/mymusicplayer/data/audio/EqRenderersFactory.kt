package com.mymusicplayer.data.audio

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * ExoPlayer has no public API to inject a custom [androidx.media3.common.audio.AudioProcessor]
 * via the Builder in Media3 1.5.1 — the processor chain is owned by [DefaultAudioSink]. This
 * factory subclasses [DefaultRenderersFactory] and overrides [buildAudioSink] so the audio
 * renderer uses a [DefaultAudioSink] wired with [eqProcessor] (software EQ).
 */
@UnstableApi
class EqRenderersFactory(
    context: Context,
    private val eqProcessor: GraphicEQProcessor
) : DefaultRenderersFactory(context) {

    @UnstableApi
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): DefaultAudioSink {
        return DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf(eqProcessor))
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()
    }
}
