package com.wavemoth.app.ui.home

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.wavemoth.app.playback.AudioSpectrum
import com.wavemoth.app.playback.PlaybackProgress
import com.wavemoth.app.ui.theme.WavemothColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val BARS = 120
private val Violet = Color(0xFF7C6CF2)
private val Cyan = Color(0xFF38C6E8)
private val Core = Color(0xFF0F1320)
private val Faint = Color(0xFFB8BED0)

/** Bar heights, peak dots and the core's wobble, moved along every frame. */
private class VisualizerState {
    val target = FloatArray(AudioSpectrum.BANDS)
    val levels = FloatArray(AudioSpectrum.BANDS)
    val peaks = FloatArray(AudioSpectrum.BANDS)
    var time = 0f

    val moving get() = levels.any { it > 0.002f } || peaks.any { it > 0.002f }

    fun step(dt: Float, playing: Boolean) {
        if (!(playing && AudioSpectrum.read(target))) target.fill(0f)
        for (i in levels.indices) {
            val t = target[i]
            levels[i] = if (t > levels[i]) levels[i] + (t - levels[i]) * min(1f, dt * 28f)
            else (levels[i] - dt * 1.9f).coerceAtLeast(t)
            peaks[i] = if (levels[i] > peaks[i]) levels[i] else (peaks[i] - dt * 0.35f).coerceAtLeast(0f)
        }
        if (playing) time += dt
    }

    /** Level at a point between bands, so neighbouring bars blend. */
    fun level(of: FloatArray, band: Float): Float {
        val i = band.toInt().coerceIn(0, of.size - 1)
        val j = (i + 1).coerceAtMost(of.size - 1)
        val f = band - i
        return of[i] * (1 - f) + of[j] * f
    }

    fun average(from: Int, to: Int): Float = (from until to).sumOf { levels[it].toDouble() }.toFloat() / (to - from)
}

/**
 * A ring of bars that move with the music: bass at the bottom, treble at the top, mirrored left
 * and right. Loud bars turn red, dots mark recent peaks, the core wobbles with the bass and the
 * red arc shows how far into the song you are.
 */
@Composable
fun Visualizer(playing: Boolean, progress: () -> PlaybackProgress, modifier: Modifier = Modifier) {
    val state = remember { VisualizerState() }
    var frame by remember { mutableLongStateOf(0L) }
    val isPlaying by rememberUpdatedState(playing)

    LaunchedEffect(playing) {
        var last = 0L
        // Runs while playing, then until the bars have settled; idle otherwise.
        while (isPlaying || state.moving) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 1 / 60f else ((now - last) / 1e9f).coerceIn(0f, 0.1f)
                last = now
                state.step(dt, isPlaying)
                frame = now
            }
        }
    }

    Canvas(modifier) {
        frame  // redraw (only) when a new frame was computed
        drawVisualizer(state, progress())
    }
}

private fun DrawScope.drawVisualizer(s: VisualizerState, progress: PlaybackProgress) {
    val size = min(this.size.width, this.size.height)
    val c = center
    val rBase = size * 0.25f
    val maxBar = size * 0.165f
    val minBar = size * 0.012f
    val px = size / 360f  // ~1dp on a phone-width visualizer

    // Outer dial: faint dots with clock-position ticks.
    val rDial = size * 0.47f
    for (i in 0 until 72) {
        val a = angle(i, 72)
        drawCircle(Faint.copy(alpha = 0.18f), radius = 0.9f * px, center = polar(c, rDial, a))
    }
    for (i in 0 until 12) {
        val a = angle(i, 12)
        val long = i % 3 == 0
        drawLine(
            Faint.copy(alpha = if (long) 0.55f else 0.3f),
            polar(c, rDial - (if (long) 9f else 6f) * px, a), polar(c, rDial + 3f * px, a),
            strokeWidth = 1.2f * px, cap = StrokeCap.Round,
        )
    }

    // Song progress: a thin red arc just outside the tallest bars.
    val rArc = rBase + maxBar + 10f * px
    val arcSize = Size(rArc * 2, rArc * 2)
    val arcTopLeft = Offset(c.x - rArc, c.y - rArc)
    drawArc(Faint.copy(alpha = 0.08f), -90f, 360f, false, arcTopLeft, arcSize, style = Stroke(1.5f * px))
    val d = progress.durationMs
    if (d > 0) {
        val sweep = 360f * (progress.positionAt(SystemClock.elapsedRealtime()).toFloat() / d).coerceIn(0f, 1f)
        drawArc(WavemothColors.Red, -90f, sweep, false, arcTopLeft, arcSize, style = Stroke(2f * px, cap = StrokeCap.Round))
    }

    // Bars and peak dots.
    val half = BARS / 2
    val bands = AudioSpectrum.BANDS - 1
    for (i in 0 until BARS) {
        val a = angle(i, BARS)
        // 0 at the top (treble) to 1 at the bottom (bass), the same on both sides.
        val fromTop = (if (i <= half) i else BARS - i).toFloat() / half
        val band = (1 - fromTop) * bands
        val shimmer = 0.88f + 0.12f * sin(i * 1.7f + s.time * 2.3f)
        val level = (s.level(s.levels, band) * shimmer).coerceIn(0f, 1f)
        val peak = s.level(s.peaks, band)

        val drift = (sin(2 * a + s.time * 0.35f) + 1) / 2
        val colour = if (level > 0.84f) WavemothColors.Red else lerp(Violet, Cyan, smooth(drift))
        val inner = rBase + 3f * px
        drawLine(
            colour.copy(alpha = 0.55f + 0.45f * level),
            polar(c, inner, a), polar(c, inner + minBar + level * maxBar, a),
            strokeWidth = 2.4f * px, cap = StrokeCap.Round,
        )
        if (peak > 0.04f) {
            drawCircle(colour.copy(alpha = 0.8f), radius = 1.1f * px, center = polar(c, inner + minBar + peak * maxBar + 5f * px, a))
        }
    }

    // Core: a dark disc with faint rings and ticks.
    drawCircle(Core, radius = rBase - 2f * px, center = c)
    drawCircle(Faint.copy(alpha = 0.1f), radius = rBase * 0.9f, center = c, style = Stroke(1f * px))
    drawCircle(Faint.copy(alpha = 0.06f), radius = rBase * 0.55f, center = c, style = Stroke(1f * px))
    for (i in 0 until 60) {
        val a = angle(i, 60)
        drawLine(Faint.copy(alpha = 0.16f), polar(c, rBase * 0.66f, a), polar(c, rBase * 0.71f, a), strokeWidth = 1f * px)
    }

    // A soft shape in the middle that swells and wobbles with the bass.
    val bass = s.average(0, 6)
    val mid = s.average(6, 16)
    val r0 = rBase * (0.3f + 0.06f * bass)
    val blob = Path()
    val points = 72
    for (p in 0..points) {
        val a = 2 * PI.toFloat() * p / points
        val r = r0 * (1 + 0.04f * sin(6 * a + s.time * 0.8f) + 0.12f * bass * sin(3 * a + s.time * 1.3f) + 0.07f * mid * sin(5 * a - s.time * 1.7f))
        val pt = polar(c, r, a)
        if (p == 0) blob.moveTo(pt.x, pt.y) else blob.lineTo(pt.x, pt.y)
    }
    blob.close()
    drawPath(blob, Cyan.copy(alpha = 0.85f), style = Stroke(1.6f * px))

    val arm = 5f * px
    drawLine(Color.White, Offset(c.x - arm, c.y), Offset(c.x + arm, c.y), strokeWidth = 1.4f * px)
    drawLine(Color.White, Offset(c.x, c.y - arm), Offset(c.x, c.y + arm), strokeWidth = 1.4f * px)
}

/** Angle of step [i] of [n] round the circle, starting at the top. */
private fun angle(i: Int, n: Int): Float = (2 * PI * i / n - PI / 2).toFloat()

private fun polar(c: Offset, r: Float, a: Float) = Offset(c.x + r * cos(a), c.y + r * sin(a))

private fun smooth(x: Float): Float = x * x * (3 - 2 * x)
