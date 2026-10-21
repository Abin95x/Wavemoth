package com.wavemoth.app.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wavemoth.app.data.Catalog
import com.wavemoth.app.data.LrcParser
import com.wavemoth.app.data.LyricLine
import com.wavemoth.app.data.Lyrics
import com.wavemoth.app.data.Song
import com.wavemoth.app.playback.PlaybackProgress
import com.wavemoth.app.ui.components.MarqueeText
import com.wavemoth.app.ui.components.rememberSmoothPosition
import com.wavemoth.app.ui.theme.WavemothColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

sealed interface LyricsState {
    data object Loading : LyricsState
    data class Found(val lyrics: Lyrics) : LyricsState
    data object NotFound : LyricsState
    data object Failed : LyricsState
}

/** Loads lyrics for [song]; changing [attempt] retries. */
@Composable
fun rememberLyrics(song: Song, attempt: Int = 0): LyricsState {
    val state by produceState<LyricsState>(LyricsState.Loading, song.id, attempt) {
        value = LyricsState.Loading
        value = withContext(Dispatchers.IO) {
            runCatching { Catalog.lyrics(song) }.fold(
                { it?.let(LyricsState::Found) ?: LyricsState.NotFound },
                { LyricsState.Failed },
            )
        }
    }
    return state
}

/** Full-screen lyrics. Synced lyrics follow the song; tap a line to jump there. */
@Composable
fun LyricsPanel(
    song: Song,
    progress: PlaybackProgress,
    background: Color,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
) {
    var attempt by remember { mutableIntStateOf(0) }
    val state = rememberLyrics(song, attempt)
    val panelColor = lerp(background, Color.Black, 0.15f)

    Column(Modifier.fillMaxSize().background(panelColor).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.KeyboardArrowDown, "Close lyrics", modifier = Modifier.size(32.dp)) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                MarqueeText(song.title, MaterialTheme.typography.titleSmall, Color.White)
                MarqueeText(song.artist, MaterialTheme.typography.bodySmall, Color.White.copy(alpha = 0.7f))
            }
            Box(Modifier.size(48.dp))
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                LyricsState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                LyricsState.NotFound -> Message("No lyrics found for this song")
                LyricsState.Failed -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Message("Couldn't load lyrics. Check your connection.")
                    TextButton(onClick = { attempt++ }) { Text("Try again", color = Color.White) }
                }
                is LyricsState.Found -> when {
                    state.lyrics.synced.isNotEmpty() -> SyncedLyrics(state.lyrics.synced, progress, onSeek)
                    state.lyrics.plain.isNotBlank() -> PlainLyrics(state.lyrics.plain)
                    else -> Message("Instrumental")
                }
            }
        }

        Text(
            "Lyrics from LRCLIB",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SyncedLyrics(lines: List<LyricLine>, progress: PlaybackProgress, onSeek: (Long) -> Unit) {
    val position by rememberSmoothPosition(progress)
    // Recomposes only when the highlighted line changes, not every frame.
    val current by remember(lines) { derivedStateOf { LrcParser.currentIndex(lines, position + 250) } }
    val listState = rememberLazyListState()

    // Keep the current line near the top third, but pause while the user is scrolling by hand.
    LaunchedEffect(lines) {
        snapshotFlow { current }.collectLatest { index ->
            while (listState.isScrollInProgress) delay(300)
            listState.animateScrollToItem((index - 2).coerceAtLeast(0))
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(lines) { i, line ->
            val color by animateColorAsState(
                when {
                    i == current -> Color.White
                    i < current -> Color.White.copy(alpha = 0.55f)
                    else -> Color.Black.copy(alpha = 0.55f)
                },
                tween(250),
                label = "lyricColor",
            )
            Text(
                line.text.ifBlank { "♪" },
                color = color,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().clickable { onSeek(line.timeMs) },
            )
        }
    }
}

@Composable
private fun PlainLyrics(text: String) {
    Text(
        text,
        color = Color.White,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
    )
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = WavemothColors.TextPrimary, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}
