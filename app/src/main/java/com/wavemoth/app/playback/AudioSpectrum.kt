package com.wavemoth.app.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * How loud each frequency band of the playing music is, for the Home visualizer.
 *
 * The audio is analysed as it is handed to the speaker ([SpectrumAudioSink]), which is a moment
 * before it is heard. Each result is kept with its timestamp and [read] returns the one that is
 * audible right now, so the bars move in time with the music. No microphone permission needed.
 */
object AudioSpectrum {
    const val BANDS = 32
    const val WAVE_POINTS = 128
    private const val KEEP = 96  // ~2 s of results; more than the speaker buffer ever holds

    private val lock = Any()
    private val times = LongArray(KEEP)
    private val frames = Array(KEEP) { FloatArray(BANDS) }
    private val waves = Array(KEEP) { FloatArray(WAVE_POINTS) }
    private var head = 0
    private var count = 0

    /** Where the speaker is now, in the same time base as the results. */
    @Volatile internal var playedUs: Long = Long.MIN_VALUE

    /**
     * Copies the levels (0 to 1, low to high frequencies) of what's audible now into [out], and
     * its shape (-1 to 1, [WAVE_POINTS] long) into [wave].
     */
    fun read(out: FloatArray, wave: FloatArray): Boolean = synchronized(lock) {
        val now = playedUs
        for (k in 0 until count) {
            val i = (head - 1 - k + KEEP) % KEEP
            if (times[i] <= now) {
                frames[i].copyInto(out)
                waves[i].copyInto(wave)
                return true
            }
        }
        false
    }

    internal fun push(timeUs: Long, levels: FloatArray, samples: FloatArray) = synchronized(lock) {
        times[head] = timeUs
        levels.copyInto(frames[head])
        val wave = waves[head]
        val perPoint = samples.size / WAVE_POINTS
        for (p in 0 until WAVE_POINTS) {
            var sum = 0f
            for (i in 0 until perPoint) sum += samples[p * perPoint + i]
            wave[p] = sum / perPoint
        }
        head = (head + 1) % KEEP
        if (count < KEEP) count++
    }

    internal fun clear() = synchronized(lock) { count = 0 }
}

/** Passes audio through to the real sink and feeds a copy to [AudioSpectrum]. */
@UnstableApi
class SpectrumAudioSink(sink: AudioSink) : ForwardingAudioSink(sink) {

    private val analyzer = SpectrumAnalyzer()
    private var lastBufferUs = C.TIME_UNSET

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        super.configure(inputFormat, specifiedBufferSize, outputChannels)
        val pcm = inputFormat.sampleMimeType == MimeTypes.AUDIO_RAW
        analyzer.setFormat(
            encoding = if (pcm) inputFormat.pcmEncoding else C.ENCODING_INVALID,
            channels = inputFormat.channelCount,
            sampleRate = inputFormat.sampleRate,
        )
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        // The same buffer comes back until the sink has taken all of it; analyse it once.
        if (presentationTimeUs != lastBufferUs) {
            lastBufferUs = presentationTimeUs
            analyzer.add(buffer, presentationTimeUs)
        }
        return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long =
        super.getCurrentPositionUs(sourceEnded).also {
            if (it != AudioSink.CURRENT_POSITION_NOT_SET) AudioSpectrum.playedUs = it
        }

    override fun flush() {
        super.flush()
        restart()
    }

    override fun reset() {
        super.reset()
        restart()
    }

    private fun restart() {
        lastBufferUs = C.TIME_UNSET
        analyzer.restart()
        AudioSpectrum.clear()
    }
}

/** Collects mono samples into windows and turns each window into band levels with an FFT. */
internal class SpectrumAnalyzer {
    private val window = FloatArray(SIZE)
    private val re = FloatArray(SIZE)
    private val im = FloatArray(SIZE)
    private val levels = FloatArray(AudioSpectrum.BANDS)
    private var filled = 0
    private var windowStartUs = 0L

    private var encoding = C.ENCODING_INVALID
    private var channels = 0
    private var sampleRate = 0
    private var bandBins = emptyArray<IntRange>()
    private var bandTilt = FloatArray(0)

    fun setFormat(encoding: Int, channels: Int, sampleRate: Int) {
        this.encoding = encoding
        this.channels = channels
        this.sampleRate = sampleRate
        if (sampleRate > 0) layoutBands()
        restart()
    }

    fun restart() {
        filled = 0
    }

    fun add(buffer: ByteBuffer, timeUs: Long) {
        val bytesPerSample = when (encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_FLOAT -> 4
            else -> return
        }
        if (channels <= 0 || sampleRate <= 0) return
        val data = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val frameBytes = bytesPerSample * channels
        val start = data.position()
        val frames = (data.limit() - start) / frameBytes
        for (f in 0 until frames) {
            if (filled == 0) windowStartUs = timeUs + f * 1_000_000L / sampleRate
            var sum = 0f
            val at = start + f * frameBytes
            for (c in 0 until channels) {
                sum += if (bytesPerSample == 2) data.getShort(at + c * 2) / 32768f else data.getFloat(at + c * 4)
            }
            window[filled++] = sum / channels
            if (filled == SIZE) {
                analyse()
                filled = 0
            }
        }
    }

    private fun analyse() {
        for (i in 0 until SIZE) {
            re[i] = window[i] * HANN[i]
            im[i] = 0f
        }
        fft(re, im)
        for (b in levels.indices) {
            var peak = 0f
            for (k in bandBins[b]) {
                val mag = sqrt(re[k] * re[k] + im[k] * im[k])
                if (mag > peak) peak = mag
            }
            // Peak amplitude relative to a full-scale sine (Hann window halves it).
            val db = 20f * log10(peak * 4f / SIZE + 1e-9f) + bandTilt[b]
            levels[b] = ((db - FLOOR_DB) / (CEIL_DB - FLOOR_DB)).coerceIn(0f, 1f)
        }
        // Tag the result with the middle of the window, when it's actually being heard.
        AudioSpectrum.push(windowStartUs + SIZE / 2 * 1_000_000L / sampleRate, levels, window)
    }

    /** Bands spaced evenly in pitch from [LOW_HZ] to [HIGH_HZ], each at least one FFT bin wide. */
    private fun layoutBands() {
        val n = AudioSpectrum.BANDS
        val binHz = sampleRate.toFloat() / SIZE
        val ratio = HIGH_HZ / LOW_HZ
        bandBins = Array(n) { b ->
            val lo = LOW_HZ * ratio.pow(b.toFloat() / n)
            val hi = LOW_HZ * ratio.pow((b + 1).toFloat() / n)
            val first = (lo / binHz).toInt().coerceIn(1, SIZE / 2 - 1)
            val last = (hi / binHz).toInt().coerceIn(first, SIZE / 2 - 1)
            first..last
        }
        // Music has less energy higher up; lift the top bands so they move as much as the bass.
        bandTilt = FloatArray(n) { b ->
            val centre = LOW_HZ * ratio.pow((b + 0.5f) / n)
            (TILT_DB_PER_OCTAVE * log2(centre / 150f)).coerceAtLeast(0f)
        }
    }

    private companion object {
        const val SIZE = 1024
        const val LOW_HZ = 40f
        const val HIGH_HZ = 14_000f
        const val FLOOR_DB = -62f
        const val CEIL_DB = -8f
        const val TILT_DB_PER_OCTAVE = 3f
        val HANN = FloatArray(SIZE) { i -> (0.5 - 0.5 * cos(2 * PI * i / (SIZE - 1))).toFloat() }

        /** In-place radix-2 FFT; [re] and [im] must be a power-of-two long. */
        fun fft(re: FloatArray, im: FloatArray) {
            val n = re.size
            var j = 0
            for (i in 1 until n) {
                var bit = n shr 1
                while (j and bit != 0) {
                    j = j xor bit
                    bit = bit shr 1
                }
                j = j xor bit
                if (i < j) {
                    re[i] = re[j].also { re[j] = re[i] }
                    im[i] = im[j].also { im[j] = im[i] }
                }
            }
            var len = 2
            while (len <= n) {
                val angle = -2 * PI / len
                val wRe = cos(angle).toFloat()
                val wIm = sin(angle).toFloat()
                var i = 0
                while (i < n) {
                    var curRe = 1f
                    var curIm = 0f
                    for (k in 0 until len / 2) {
                        val a = i + k
                        val b = a + len / 2
                        val tRe = re[b] * curRe - im[b] * curIm
                        val tIm = re[b] * curIm + im[b] * curRe
                        re[b] = re[a] - tRe
                        im[b] = im[a] - tIm
                        re[a] += tRe
                        im[a] += tIm
                        val nextRe = curRe * wRe - curIm * wIm
                        curIm = curRe * wIm + curIm * wRe
                        curRe = nextRe
                    }
                    i += len
                }
                len = len shl 1
            }
        }
    }
}
