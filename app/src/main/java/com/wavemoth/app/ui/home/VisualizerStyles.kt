package com.wavemoth.app.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.wavemoth.app.data.VisualizerStyle
import com.wavemoth.app.playback.AudioSpectrum
import com.wavemoth.app.ui.theme.WavemothColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
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

// LED dots closer together than this many pixels just blur, so small previews stay solid.
private const val MIN_DOT_PITCH = 2.5f

/** Colours up a column, from the bottom row to the top. */
internal class Ramp(val bottom: Color, val middle: Color, val top: Color) {
    fun at(t: Float): Color = if (t < 0.5f) lerp(bottom, middle, t * 2) else lerp(middle, top, (t - 0.5f) * 2)
}

/** How a spectrum style is built: its grid, colours and the way its peaks move. */
internal class SpectrumLook private constructor(
    val bars: Int,
    val rows: Int,
    val barGap: Float,
    val rowGap: Float,
    val lit: Ramp,
    val unlit: Ramp? = null,
    val unlitAlpha: Float = 0f,
    val peak: Color? = null,
    val launch: Boolean = false,
    val mirrored: Boolean = false,
    val led: Float = 0f,  // how much of each cell is drawn as LED dots
    val vignette: Float = 0f,
    val hueStart: Float = 0f,
    val hueSpeed: Float = 0f,
) {
    companion object {
        private val Meter = Ramp(Color(0xFF2ECC1E), Color(0xFFF2D21C), Color(0xFFE8341C))

        val RoseGold = SpectrumLook(
            bars = 36, rows = 32, barGap = 0.25f, rowGap = 0.3f,
            lit = Ramp(Color(0xFFE3BCB6), Color(0xFFF2DAD4), Color(0xFFBA7480)),
            unlit = Ramp(Color(0xFFAE6872), Color(0xFFC18089), Color(0xFF763C46)),
            unlitAlpha = 1f,
            peak = Color(0xFFF6E2DD),
            led = 0.47f,
            vignette = 0.45f,
        )
        val LedMeter = SpectrumLook(
            bars = 77, rows = 32, barGap = 0.25f, rowGap = 0.3f,
            lit = Meter,
            unlitAlpha = 0.08f,
            peak = Color(0xFFD8D8D8),
            launch = true,
        )
        val Prism = SpectrumLook(
            bars = 32, rows = 64, barGap = 0.25f, rowGap = 0f,
            lit = Ramp(Color(0xFF1DC111), Color(0xFFEFC810), Color(0xFFE22310)),
            mirrored = true,
            led = 1f,
            hueStart = 1.23f,
            hueSpeed = 1.25f,
        )
    }
}

/** One column of a look, painted once, then cut to each bar's height every frame. */
internal class SpectrumSprites(val look: SpectrumLook, val width: Int, val height: Int, val dotPitch: Float) {
    val lit = paintColumn(look.lit)
    val unlit = look.unlit?.let(::paintColumn) ?: lit

    private fun paintColumn(ramp: Ramp): ImageBitmap {
        val image = ImageBitmap(width, height)
        val w = width.toFloat()
        val h = height.toFloat()
        val rowHeight = h / look.rows
        val dots = look.led > 0f && dotPitch >= MIN_DOT_PITCH
        val cellAlpha = if (dots) 1f - look.led else 1f
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(w, h)) {
            if (cellAlpha > 0f) {
                for (row in 0 until look.rows) {
                    val top = h - (row + 1 - look.rowGap / 2) * rowHeight
                    drawRect(
                        ramp.at((row + 0.5f) / look.rows).copy(alpha = cellAlpha),
                        Offset(0f, top), Size(w, rowHeight * (1 - look.rowGap)),
                    )
                }
            }
            if (!dots) return@draw
            val across = max(1, floor(w / dotPitch).toInt())
            val startX = (w - (across - 1) * dotPitch) / 2
            var y = dotPitch / 2
            while (y < h) {
                val rowPosition = (h - y) / rowHeight
                val inRow = rowPosition - floor(rowPosition)
                if (inRow >= look.rowGap / 2 && inRow <= 1 - look.rowGap / 2) {
                    val colour = ramp.at((h - y) / h)
                    for (i in 0 until across) {
                        val dot = Offset(startX + i * dotPitch, y)
                        drawCircle(colour.copy(alpha = 0.22f), dotPitch * 0.62f, dot)
                        drawCircle(colour, dotPitch * 0.36f, dot)
                    }
                }
                y += dotPitch
            }
        }
        return image
    }
}

/** Turns colours round the hue wheel by [angle] radians, keeping their brightness. */
internal fun hueRotation(angle: Float): FloatArray {
    val ca = cos(angle)
    val sa = sin(angle)
    val luma = floatArrayOf(0.299f, 0.587f, 0.114f)
    val i = floatArrayOf(0.596f, -0.274f, -0.322f)
    val q = floatArrayOf(0.211f, -0.523f, 0.312f)
    val out = FloatArray(20)
    // How much each output channel takes from the two colour axes.
    val toI = floatArrayOf(0.956f, -0.272f, -1.106f)
    val toQ = floatArrayOf(0.621f, -0.647f, 1.703f)
    for (channel in 0 until 3) {
        val a = toI[channel] * ca + toQ[channel] * sa
        val b = toQ[channel] * ca - toI[channel] * sa
        for (c in 0 until 3) out[channel * 5 + c] = luma[c] + a * i[c] + b * q[c]
    }
    out[18] = 1f
    return out
}

internal fun DrawScope.drawVisualizerArt(style: VisualizerStyle, s: VisualizerState) = when (style) {
    VisualizerStyle.Classic -> drawClassic(s)
    VisualizerStyle.SoundWave -> drawSoundWave(s)
    VisualizerStyle.RoseGold -> drawSpectrum(s, SpectrumLook.RoseGold)
    VisualizerStyle.LedMeter -> drawSpectrum(s, SpectrumLook.LedMeter)
    VisualizerStyle.Waveform -> drawWaveform(s)
    VisualizerStyle.Prism -> drawSpectrum(s, SpectrumLook.Prism)
}

/** Columns of lit cells filling the area. Mirrored looks grow out of the middle, bass first. */
private fun DrawScope.drawSpectrum(s: VisualizerState, look: SpectrumLook) {
    val bars = s.barsFor(look.bars, look.launch)
    val areaWidth = if (look.mirrored) size.width / 2 else size.width
    val height = (if (look.mirrored) size.height / 2 else size.height).toInt()
    val slot = areaWidth / look.bars
    val barWidth = max(1, (slot * (1 - look.barGap)).roundToInt())
    if (height <= 0) return

    val pitch = 3.5f * side / 360f
    val sprites = s.sprites?.takeIf {
        it.look === look && it.width == barWidth && it.height == height && it.dotPitch == pitch
    } ?: SpectrumSprites(look, barWidth, height, pitch).also { s.sprites = it }
    val filter = if (look.hueSpeed > 0f) {
        ColorFilter.colorMatrix(ColorMatrix(hueRotation(look.hueStart + s.time * look.hueSpeed)))
    } else null

    fun drawColumns() {
        for (i in 0 until look.bars) {
            val x = (i * slot + (slot - barWidth) / 2).roundToInt()
            val across = (i + 0.5f) / look.bars * 2 - 1
            val alpha = 1f - look.vignette * across * across
            val litRows = (bars.levels[i] * look.rows + 0.5f).toInt().coerceIn(0, look.rows)
            val cut = (height * litRows.toFloat() / look.rows).roundToInt()

            if (look.unlitAlpha > 0f && cut < height) {
                drawImage(
                    sprites.unlit, IntOffset.Zero, IntSize(barWidth, height - cut),
                    IntOffset(x, 0), IntSize(barWidth, height - cut),
                    alpha = alpha * look.unlitAlpha, filterQuality = FilterQuality.None,
                )
            }
            if (cut > 0) {
                drawImage(
                    sprites.lit, IntOffset(0, height - cut), IntSize(barWidth, cut),
                    IntOffset(x, height - cut), IntSize(barWidth, cut),
                    alpha = alpha, colorFilter = filter, filterQuality = FilterQuality.None,
                )
            }
            look.peak?.let { colour ->
                val row = min(floor(bars.peaks[i] * look.rows).toInt(), look.rows - 1)
                val rowHeight = height.toFloat() / look.rows
                drawRect(
                    colour.copy(alpha = alpha),
                    Offset(x.toFloat(), height - (row + 1 - look.rowGap / 2) * rowHeight),
                    Size(barWidth.toFloat(), rowHeight * (1 - look.rowGap)),
                )
            }
        }
    }

    if (!look.mirrored) return drawColumns()
    val middle = center
    fun quadrant() = translate(left = size.width / 2) { drawColumns() }
    quadrant()
    scale(-1f, 1f, middle) { quadrant() }
    scale(1f, -1f, middle) { quadrant() }
    scale(-1f, -1f, middle) { quadrant() }
}

/** The sound's shape as one bright line, coloured by how hard it is moving at each point. */
private fun DrawScope.drawWaveform(s: VisualizerState) {
    val middle = size.height / 2
    val reach = size.height * 0.36f
    val px = side / 360f * s.stroke
    val last = s.wave.size - 1

    fun x(i: Int) = size.width * i / last
    fun drive(i: Int) = s.level(s.levels, abs(2f * i / last - 1) * TOP_BAND)
    fun lift(i: Int): Float {
        val fromMiddle = abs(2f * i / last - 1)
        return s.wave[i] * (1 + drive(i)) * (1 + (1 - fromMiddle).pow(2)) * 1.5f
    }
    fun colour(i: Int): Color {
        val moved = abs(lift(i))
        val energy = 1.4f * drive(i)
        val hue = 1f - min((moved + energy) * 1.2f, 1f)
        return Color.hsl(
            hue * 360f,
            min(0.85f + 0.15f * energy, 1f),
            min(0.52f + 0.4f * max(moved, energy), 0.8f),
        )
    }
    fun drawTrace(centre: Float, scale: Float, width: Float, alpha: Float) {
        var from = Offset(0f, centre - (lift(0) * reach * scale).coerceIn(-reach, reach))
        for (i in 1..last) {
            val to = Offset(x(i), centre - (lift(i) * reach * scale).coerceIn(-reach, reach))
            drawLine(colour(i).copy(alpha = alpha), from, to, width, StrokeCap.Round)
            from = to
        }
    }

    // Faint echoes above and below, then the glow and the line itself.
    drawTrace(middle - size.height * 0.3f, 0.5f, 1.2f * px, 0.12f)
    drawTrace(middle + size.height * 0.3f, 0.5f, 1.2f * px, 0.12f)
    drawTrace(middle, 1f, 7f * px, 0.16f)
    drawTrace(middle, 1f, 2f * px, 1f)
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
