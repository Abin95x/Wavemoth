package com.wavemoth.app.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.wavemoth.app.data.VisualizerStyle
import com.wavemoth.app.playback.AudioSpectrum
import com.wavemoth.app.ui.theme.WavemothColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private const val TAU = 2 * PI.toFloat()
private const val TOP_BAND = AudioSpectrum.BANDS - 1f

// Sound Wave stays inside this share of the side, within the progress ring.
private const val ART_RADIUS = 0.425f

private val Violet = Color(0xFF7C6CF2)
private val Cyan = Color(0xFF38C6E8)
private val Core = Color(0xFF0F1320)
private val Faint = Color(0xFFB8BED0)
private val Lilac = Color(0xFFC9A6F7)
private val Sky = Color(0xFF58B7FF)
private val Magenta = Color(0xFFE23BE8)

internal fun DrawScope.drawVisualizerArt(style: VisualizerStyle, s: VisualizerState) = when (style) {
    VisualizerStyle.Classic -> drawClassic(s)
    VisualizerStyle.SoundWave -> drawSoundWave(s)
}

private val DrawScope.side get() = min(size.width, size.height)
private val DrawScope.artRadius get() = side * ART_RADIUS
private fun DrawScope.unit(s: VisualizerState) = side / 360f * s.stroke  // ~1dp on a phone-width visualizer

/** Bars round a dark core: bass at the bottom, treble at the top, mirrored left and right. */
private fun DrawScope.drawClassic(s: VisualizerState) {
    val bars = 120
    val size = side
    val c = center
    val rBase = size * 0.25f
    val maxBar = size * 0.165f
    val minBar = size * 0.012f
    val px = unit(s)

    // Outer dial: faint dots with clock-position ticks.
    val rDial = size * 0.47f
    for (i in 0 until 72) {
        drawCircle(Faint.copy(alpha = 0.18f), radius = 0.9f * px, center = polar(c, rDial, angle(i, 72)))
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

    // Bars and peak dots.
    val half = bars / 2
    for (i in 0 until bars) {
        val a = angle(i, bars)
        // 0 at the top (treble) to 1 at the bottom (bass), the same on both sides.
        val fromTop = (if (i <= half) i else bars - i).toFloat() / half
        val band = (1 - fromTop) * TOP_BAND
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
    val blob = s.path
    blob.reset()
    val points = 72
    for (p in 0..points) {
        val a = TAU * p / points
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

/** Soft overlapping swells across the middle, biggest at the bass in the centre. */
private fun DrawScope.drawSoundWave(s: VisualizerState) {
    val layers = 5
    val width = artRadius * 0.96f
    val height = artRadius * 0.62f
    val brush = Brush.horizontalGradient(
        listOf(Cyan, Sky, Lilac, Magenta),
        startX = center.x - width,
        endX = center.x + width,
    )

    fun drawLayer(layer: Int, scale: Float, alpha: Float) {
        fun lift(u: Float): Float {
            val taper = (1f - u * u).pow(2)
            val ripple = 0.5f + 0.5f * sin(u * PI.toFloat() * (1.6f + 0.45f * layer) - s.time * (1.1f + 0.25f * layer) + layer * 1.3f)
            // Several neighbouring bands together, so the swells stay round.
            val band = abs(u) * TOP_BAND
            val level = (s.level(s.levels, (band - 3f).coerceAtLeast(0f)) + s.level(s.levels, band) + s.level(s.levels, band + 3f)) / 3f
            return taper * height * scale * (0.12f + 0.1f * ripple + 0.88f * level * (0.55f + 0.45f * ripple))
        }
        val points = 64
        val path: Path = s.path
        path.reset()
        for (p in 0..points) {
            val u = -1f + 2f * p / points
            val x = center.x + u * width
            val y = center.y - lift(u)
            if (p == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        for (p in points downTo 0) {
            val u = -1f + 2f * p / points
            path.lineTo(center.x + u * width, center.y + lift(u))
        }
        path.close()
        drawPath(path, brush, alpha = alpha)
    }

    for (layer in 0 until layers) {
        val depth = layer / (layers - 1f)
        drawLayer(layer, 1f - 0.14f * layer, 0.2f + 0.14f * depth)
    }
    drawLayer(1, 0.2f, 0.95f)  // the bright line through the middle
}

/** Angle of step [i] of [n] round the circle, starting at the top. */
private fun angle(i: Int, n: Int): Float = (2 * PI * i / n - PI / 2).toFloat()

/** Angle of [fraction] of the way round the circle, starting at the top. */
internal fun angle(fraction: Float): Float = TAU * fraction - PI.toFloat() / 2

internal fun polar(c: Offset, r: Float, a: Float) = Offset(c.x + r * cos(a), c.y + r * sin(a))

private fun smooth(x: Float): Float = x * x * (3 - 2 * x)
