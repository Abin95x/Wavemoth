package com.wavemoth.app.ui.home

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import com.wavemoth.app.data.VisualizerStyle
import com.wavemoth.app.data.formatDuration
import com.wavemoth.app.playback.AudioSpectrum
import com.wavemoth.app.playback.PlaybackProgress
import com.wavemoth.app.ui.theme.WavemothColors
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min

// All as a share of the visualizer's side.
private const val RING_RADIUS = 0.443f
private const val RING_TOUCH_FROM = 0.385f
private const val RING_TOUCH_TO = 0.5f

private val TimeBackground = Color(0xD90F1320)
private val Faint = Color(0xFFB8BED0)

/** What the visualizer can do while a song is loaded. */
class VisualizerActions(
    val onTogglePlay: () -> Unit,
    val onSeek: (Long) -> Unit,
)

/** Which part of the visualizer a touch [distance] from the middle lands on. */
internal enum class VisualizerZone { Inside, Ring, Outside }

internal fun visualizerZone(distance: Float, side: Float): VisualizerZone {
    val share = distance / side
    return when {
        share < RING_TOUCH_FROM -> VisualizerZone.Inside
        share <= RING_TOUCH_TO -> VisualizerZone.Ring
        else -> VisualizerZone.Outside
    }
}

/** How far round the ring a point is, from 0 at the top going clockwise to just under 1. */
internal fun ringFraction(dx: Float, dy: Float): Float {
    val turn = (atan2(dx, -dy) / (2 * PI)).toFloat()
    return if (turn < 0) turn + 1 else turn
}

/** The next drag position. Stops at the start or end instead of jumping across the top. */
internal fun continueRingDrag(previous: Float, next: Float): Float = when {
    next - previous > 0.5f -> 0f
    previous - next > 0.5f -> 1f
    else -> next
}

/** True if a finger at [position] (from the middle) moving by [move] follows the ring. */
internal fun movesAlongRing(position: Offset, move: Offset): Boolean {
    val length = position.getDistance()
    if (length == 0f) return false
    val outX = position.x / length
    val outY = position.y / length
    val outward = abs(move.x * outX + move.y * outY)
    val around = abs(move.x * outY - move.y * outX)
    return around >= outward
}

/** Band levels, peak dots and the clock the styles animate with, moved along every frame. */
internal class VisualizerState {
    val target = FloatArray(AudioSpectrum.BANDS)
    val levels = FloatArray(AudioSpectrum.BANDS)
    val peaks = FloatArray(AudioSpectrum.BANDS)
    val path = Path()
    var time = 0f

    /** Thickens lines for small previews. */
    var stroke = 1f

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

    /** Level at a point between bands, so neighbours blend. */
    fun level(of: FloatArray, band: Float): Float {
        val i = band.toInt().coerceIn(0, of.size - 1)
        val j = (i + 1).coerceAtMost(of.size - 1)
        val f = band - i
        return of[i] * (1 - f) + of[j] * f
    }

    fun average(from: Int, to: Int): Float = (from until to).sumOf { levels[it].toDouble() }.toFloat() / (to - from)
}

/**
 * The music, drawn in the chosen [style]. With a song loaded ([actions] set), the ring round the
 * edge shows the position and can be tapped or dragged to seek, and a tap inside it plays or pauses.
 */
@Composable
fun Visualizer(
    style: VisualizerStyle,
    playing: Boolean,
    progress: () -> PlaybackProgress,
    actions: VisualizerActions?,
    modifier: Modifier = Modifier,
) {
    val state = remember { VisualizerState() }
    var frame by remember { mutableLongStateOf(0L) }
    val isPlaying by rememberUpdatedState(playing)
    val latestActions by rememberUpdatedState(actions)
    val latestProgress by rememberUpdatedState(progress)
    var seeking by remember { mutableStateOf<Float?>(null) }
    var pending by remember { mutableStateOf<Float?>(null) }  // where we seeked to, until the player catches up
    val hasSong = actions != null

    LaunchedEffect(playing) {
        var last = 0L
        // Runs while playing, then until the levels have settled; idle otherwise.
        while (isPlaying || state.moving) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 1 / 60f else ((now - last) / 1e9f).coerceIn(0f, 0.1f)
                last = now
                state.step(dt, isPlaying)
                frame = now
            }
        }
    }
    LaunchedEffect(pending) { if (pending != null) { delay(1_500); pending = null } }

    fun commit(fraction: Float) {
        val duration = latestProgress().durationMs
        if (duration > 0) {
            pending = fraction
            latestActions?.onSeek?.invoke((fraction * duration).toLong())
        }
        seeking = null
    }

    Box(
        modifier
            .semantics {
                contentDescription = "Music visualizer"
                if (hasSong) {
                    onClick(if (playing) "Pause music" else "Play music") { latestActions?.onTogglePlay?.invoke(); true }
                    setProgress("Seek") { target -> commit(target.coerceIn(0f, 1f)); true }
                }
            }
            .pointerInput(hasSong) {
                if (!hasSong) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val middle = Offset(size.width / 2f, size.height / 2f)
                    val side = min(size.width, size.height).toFloat()
                    when (visualizerZone((down.position - middle).getDistance(), side)) {
                        VisualizerZone.Ring -> trackRing(down, middle, { seeking = it }, ::commit) { seeking = null }
                        VisualizerZone.Inside -> waitForUpOrCancellation()?.let {
                            it.consume()
                            latestActions?.onTogglePlay?.invoke()
                        }
                        VisualizerZone.Outside -> Unit
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            frame  // redraw (only) when a new frame was computed
            drawVisualizerArt(style, state)
            val now = latestProgress()
            val fraction = seeking ?: pending ?: now.durationMs.takeIf { it > 0 }?.let {
                (now.positionAt(SystemClock.elapsedRealtime()).toFloat() / it).coerceIn(0f, 1f)
            }
            drawProgressRing(fraction.takeIf { hasSong }, held = seeking != null)
        }
        // While seeking, the time you would land on.
        val seekSeconds by remember {
            derivedStateOf { seeking?.let { (it * latestProgress().durationMs).toLong() / 1000 } }
        }
        seekSeconds?.let { seconds ->
            Text(
                seconds.formatDuration().ifEmpty { "0:00" },
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier
                    .background(TimeBackground, RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

/** Follows a touch that began on the ring: a tap seeks there, and so does a drag along it. */
private suspend fun AwaitPointerEventScope.trackRing(
    down: PointerInputChange,
    middle: Offset,
    onMove: (Float) -> Unit,
    onCommit: (Float) -> Unit,
    onCancel: () -> Unit,
) {
    fun fractionAt(position: Offset) = (position - middle).let { ringFraction(it.x, it.y) }

    // Until the finger has really moved this could be a tap, or the start of scrolling the page.
    var travel = Offset.Zero
    var change: PointerInputChange
    while (true) {
        change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return
        if (change.isConsumed) return
        if (!change.pressed) {
            change.consume()
            onCommit(fractionAt(down.position))
            return
        }
        travel += change.positionChange()
        if (travel.getDistance() > viewConfiguration.touchSlop) {
            if (!movesAlongRing(change.position - middle, travel)) return
            break
        }
    }

    change.consume()
    var fraction = fractionAt(change.position)
    onMove(fraction)
    while (true) {
        change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return onCancel()
        change.consume()
        if (!change.pressed) return onCommit(fraction)
        fraction = continueRingDrag(fraction, fractionAt(change.position))
        onMove(fraction)
    }
}

/** A still picture of [style] for the Settings list. */
@Composable
fun VisualizerPreview(style: VisualizerStyle, modifier: Modifier = Modifier) {
    val state = remember {
        VisualizerState().apply {
            stroke = 2.2f
            time = 2f
            for (i in levels.indices) {
                levels[i] = 0.2f + 0.6f * abs(kotlin.math.sin(i * 0.7f)) * (1f - i / 64f)
                peaks[i] = levels[i]
            }
        }
    }
    Canvas(modifier) { drawVisualizerArt(style, state) }
}

/** The song's position round the edge, with a knob to show it can be moved. */
private fun DrawScope.drawProgressRing(fraction: Float?, held: Boolean) {
    val side = min(size.width, size.height)
    val px = side / 360f
    val radius = side * RING_RADIUS
    val box = Size(radius * 2, radius * 2)
    val topLeft = Offset(center.x - radius, center.y - radius)
    drawArc(Faint.copy(alpha = 0.14f), -90f, 360f, false, topLeft, box, style = Stroke(1.5f * px))
    if (fraction == null) return

    val sweep = 360f * fraction
    drawArc(WavemothColors.Red, -90f, sweep, false, topLeft, box, style = Stroke((if (held) 3f else 2f) * px, cap = StrokeCap.Round))
    val knob = polar(center, radius, angle(fraction))
    if (held) drawCircle(WavemothColors.Red.copy(alpha = 0.22f), 15f * px, knob)
    drawCircle(Color.White, (if (held) 7f else 4f) * px, knob)
}
