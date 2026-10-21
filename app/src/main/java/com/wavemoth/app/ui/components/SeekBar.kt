package com.wavemoth.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import com.wavemoth.app.data.formatDuration
import com.wavemoth.app.playback.PlaybackProgress
import com.wavemoth.app.ui.theme.WavemothColors
import kotlinx.coroutines.delay

/**
 * Thin seek bar. The line moves every frame without recomposing anything; the knob follows the
 * finger exactly, grows while held, and stays where you let go until the player catches up.
 * Tap anywhere on it to jump there.
 */
@Composable
fun SeekBar(progress: PlaybackProgress, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    val position = rememberSmoothPosition(progress)
    val latest by rememberUpdatedState(progress)
    var dragging by remember { mutableStateOf<Float?>(null) }
    var pending by remember { mutableStateOf<Float?>(null) }  // where we seeked to, until the player reports it
    val knob by animateFloatAsState(if (dragging != null) 1f else 0f, Motion.softSpring, label = "knob")

    // A new sample means the player has the seek; also give up waiting after a moment.
    LaunchedEffect(progress) { pending = null }
    LaunchedEffect(pending) { if (pending != null) { delay(1_500); pending = null } }

    val fraction by remember {
        derivedStateOf {
            dragging ?: pending ?: run {
                val d = latest.durationMs
                if (d > 0) (position.value.toFloat() / d).coerceIn(0f, 1f) else 0f
            }
        }
    }

    fun commit(f: Float) {
        val d = latest.durationMs
        if (d > 0) {
            pending = f
            onSeek((f * d).toLong())
        }
        dragging = null
    }

    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .semantics {
                    contentDescription = "Seek"
                    setProgress { target -> commit(target.coerceIn(0f, 1f)); true }
                }
                .pointerInput(Unit) {
                    detectTapGestures { commit((it.x / size.width).coerceIn(0f, 1f)) }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = (it.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = { dragging?.let(::commit) },
                        onDragCancel = { dragging = null },
                    ) { change, amount ->
                        change.consume()
                        dragging = ((dragging ?: 0f) + amount / size.width).coerceIn(0f, 1f)
                    }
                }
                .drawBehind {
                    val y = size.height / 2
                    val line = 3.dp.toPx()
                    val end = size.width * fraction
                    drawLine(Color.White.copy(alpha = 0.16f), Offset(0f, y), Offset(size.width, y), line, StrokeCap.Round)
                    drawLine(WavemothColors.Red, Offset(0f, y), Offset(end, y), line, StrokeCap.Round)
                    val r = (5f + 3f * knob).dp.toPx()
                    if (knob > 0f) drawCircle(WavemothColors.Red.copy(alpha = 0.18f * knob), r * 2.4f, Offset(end, y))
                    drawCircle(Color.White, r, Offset(end, y))
                },
        )
        Row(Modifier.fillMaxWidth()) {
            val d = progress.durationMs
            val shownSeconds by remember { derivedStateOf { ((fraction * latest.durationMs).toLong()) / 1000 } }
            TimeLabel(shownSeconds)
            Spacer(Modifier.weight(1f))
            TimeLabel(d / 1000)
        }
    }
}

@Composable
private fun TimeLabel(seconds: Long) {
    Text(
        seconds.formatDuration().ifEmpty { "0:00" },
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = MaterialTheme.typography.bodySmall.letterSpacing),
        color = WavemothColors.TextSecondary,
        modifier = Modifier.padding(top = 2.dp),
    )
}
