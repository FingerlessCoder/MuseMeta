package com.mymusicplayer.data.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.pow

/**
 * Software 5-band graphic equalizer + volume normalization, implemented as a Media3
 * [AudioProcessor]. Runs inside the ExoPlayer audio pipeline, so it works on devices
 * whose hardware [android.media.audiofx.Equalizer] HAL is unavailable (e.g. some Honor
 * firmware returns ERROR_INVALID_OPERATION when constructing the effect).
 *
 * Each band is a peaking (bell) IIR biquad filter; the five central frequencies match the
 * UI labels (60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz). [normalizationGain] applies a flat
 * multiplier used for volume leveling.
 */
@UnstableApi
class GraphicEQProcessor : BaseAudioProcessor() {

    // UI band center frequencies (Hz) — must match EQ_FREQUENCIES in EqualizerPanel.
    private val BAND_FREQS = doubleArrayOf(60.0, 230.0, 910.0, 3600.0, 14000.0)

    // One filter state per channel (L, R, ...). Always sized to the configured channels.
    @Volatile
    private var filtersPerChannel: List<List<Biquad>> = emptyList()

    @Volatile
    private var normalizationGain: Float = 1f

    private var channelCount = 2
    private var sampleRate = 44100

    // Last-set band gains (dB) so onConfigure can rebuild filters for the new channel count.
    @Volatile
    private var lastBandDb: List<Double> = List(BAND_FREQS.size) { 0.0 }

    private fun buildCoeffs(): List<Biquad> =
        BAND_FREQS.mapIndexed { i, freq -> Biquad.peaking(sampleRate.toDouble(), freq, lastBandDb[i]) }

    fun updateBands(bandsMillibel: List<Int>) {
        lastBandDb = BAND_FREQS.indices.map { i -> mbToDb(bandsMillibel.getOrElse(i) { 0 }.toDouble()) }
        filtersPerChannel = List(channelCount) { buildCoeffs() }
    }

    /** Volume normalization target gain as a linear multiplier (1f = off). */
    fun setNormalizationGain(gain: Float) {
        normalizationGain = gain
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        channelCount = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate
        // Rebuild filters for the new format, reusing the last-set band gains.
        filtersPerChannel = List(channelCount) { buildCoeffs() }
        // Always active: gating (flat vs EQ) is handled per-sample in queueInput, not by
        // returning NOT_SET here — otherwise the sink bypasses the processor entirely and
        // band changes would never reach the audio.
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (inputBuffer.hasRemaining().not()) return

        val out = replaceOutputBuffer(inputBuffer.remaining())
        val channels = filtersPerChannel
        val norm = normalizationGain
        val hasFilters = channels.isNotEmpty() && channels.first().any { it.gainDb != 0.0 }

        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_FLOAT -> {
                while (inputBuffer.hasRemaining()) {
                    for (c in 0 until channelCount) {
                        var s = inputBuffer.getFloat()
                        if (hasFilters) {
                            for (f in channels[c]) s = f.process(s)
                        }
                        s *= norm
                        out.putFloat(s.coerceIn(-1f, 1f))
                    }
                }
            }
            C.ENCODING_PCM_16BIT -> {
                while (inputBuffer.hasRemaining()) {
                    for (c in 0 until channelCount) {
                        var s = inputBuffer.getShort() / 32768f
                        if (hasFilters) {
                            for (f in channels[c]) s = f.process(s)
                        }
                        s *= norm
                        val clamped = (s.coerceIn(-1f, 1f) * 32767f).toInt()
                        out.putShort(clamped.toShort())
                    }
                }
            }
            else -> out.put(inputBuffer)
        }
        out.flip()
    }

    override fun isActive(): Boolean = true

    private fun mbToDb(mb: Double): Double = mb / 100.0

    /**
     * RBJ audio EQ biquad (Direct Form I). [gainDb] is the peaking gain in dB; [freq] the
     * center frequency; Q fixed at 1.0 for a musical, non-resonant bell.
     */
    private class Biquad(
        val b0: Double, val b1: Double, val b2: Double,
        val a1: Double, val a2: Double,
        val gainDb: Double
    ) {
        private var x1 = 0.0
        private var x2 = 0.0
        private var y1 = 0.0
        private var y2 = 0.0

        fun process(x0: Float): Float {
            val y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x0.toDouble()
            y2 = y1; y1 = y0
            return y0.toFloat()
        }

        companion object {
            fun peaking(sampleRate: Double, freq: Double, gainDb: Double): Biquad {
                val w0 = 2.0 * PI * freq / sampleRate
                val cosw0 = kotlin.math.cos(w0)
                val sinw0 = kotlin.math.sin(w0)
                val alpha = sinw0 / (2.0 * 1.0) // Q = 1.0
                val a = 10.0.pow(gainDb / 40.0)
                val b0 = 1.0 + alpha * a
                val b1 = -2.0 * cosw0
                val b2 = 1.0 - alpha * a
                val a0 = 1.0 + alpha / a
                val a1 = -2.0 * cosw0
                val a2 = 1.0 - alpha / a
                return Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0, gainDb)
            }
        }
    }
}
