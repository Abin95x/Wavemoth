package com.wavemoth.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.wavemoth.app.data.Song
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * Shows the current song and lets you swipe to the next or previous one, like flipping through a
 * row of cards: the neighbouring song follows your finger in, a quick flick is enough, and letting
 * go early springs back. Song changes made elsewhere (buttons, notification) slide in gently.
 */
@Composable
fun TrackSwiper(
    current: Song,
    previous: Song?,
    next: Song?,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Song) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var x by remember { mutableFloatStateOf(0f) }  // drag offset in px
    var width by remember { mutableIntStateOf(1) }
    var settleJob by remember { mutableStateOf<Job?>(null) }

    // After a swipe the neighbour stays in the middle until the player confirms the change.
    var landed by remember { mutableStateOf<Song?>(null) }
    val shown = landed ?: current

    val latestNext by rememberUpdatedState(next)
    val latestPrevious by rememberUpdatedState(previous)
    val latestOnNext by rememberUpdatedState(onNext)
    val latestOnPrevious by rememberUpdatedState(onPrevious)

    // Song changed some other way: slide the new one in from the side it came from.
    val enter = remember { Animatable(1f) }
    var enterDir by remember { mutableFloatStateOf(1f) }
    var lastId by remember { mutableStateOf(current.id) }
    LaunchedEffect(current.id) {
        if (current.id == lastId) return@LaunchedEffect
        val fromSwipe = landed?.id == current.id
        val wentBack = previous?.id != lastId
        lastId = current.id
        landed = null
        if (!fromSwipe) {
            enterDir = if (wentBack) -1f else 1f
            enter.snapTo(0f)
            enter.animateTo(1f, tween(Motion.SLOW, easing = Motion.Emphasized))
        }
    }
    // The player didn't move (end of queue, say): put the real song back.
    LaunchedEffect(landed) {
        if (landed != null) { delay(1_500); landed = null }
    }

    Box(
        modifier
            .clipToBounds()
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                val flick = 350.dp.toPx()  // px per second
                fun settle(velocity: Float) {
                    val w = width.toFloat()
                    val goNext = latestNext != null && x < 0 && (x < -w * 0.3f || velocity < -flick) && velocity < flick
                    val goPrevious = latestPrevious != null && x > 0 && (x > w * 0.3f || velocity > flick) && velocity > -flick
                    val target = when {
                        goNext -> -w
                        goPrevious -> w
                        else -> 0f
                    }
                    val spec = if (target == 0f) spring(1f, Spring.StiffnessMediumLow, 0.5f) else spring(1f, 800f, 0.5f)
                    settleJob = scope.launch {
                        animate(x, target, velocity, spec) { value, _ -> x = value.coerceIn(-w, w) }
                        when {
                            goNext -> { landed = latestNext; x = 0f; latestOnNext() }
                            goPrevious -> { landed = latestPrevious; x = 0f; latestOnPrevious() }
                        }
                    }
                }
                detectHorizontalDragGestures(
                    onDragStart = { settleJob?.cancel(); tracker.resetTracking() },
                    onDragEnd = { settle(tracker.calculateVelocity().x) },
                    onDragCancel = { settle(0f) },
                ) { change, amount ->
                    change.consume()
                    tracker.addPointerInputChange(change)
                    val goingTo = if (x + amount < 0) latestNext else latestPrevious
                    // Resist when there's no song on that side.
                    x += if (goingTo != null) amount else amount * 0.25f
                }
            },
    ) {
        val side by remember { derivedStateOf { sign(x) } }
        val neighbour = when {
            landed != null -> null
            side < 0 -> next
            side > 0 -> previous
            else -> null
        }
        // Keyed by song so the neighbour's composition (and its loaded artwork) becomes the middle one.
        listOfNotNull(shown to 0f, neighbour?.let { it to -side }).forEach { (song, slot) ->
            key(song.id) {
                Box(
                    Modifier.fillMaxWidth().graphicsLayer {
                        val w = size.width
                        val f = (abs(x) / w).coerceIn(0f, 1f)
                        if (slot == 0f) {
                            translationX = x + (1f - enter.value) * enterDir * w * 0.3f
                            alpha = (1f - 0.6f * f) * (0.2f + 0.8f * enter.value)
                        } else {
                            translationX = x + slot * w
                            alpha = 0.4f + 0.6f * f
                        }
                    },
                ) { content(song) }
            }
        }
    }
}
