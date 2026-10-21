package com.wavemoth.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import coil.imageLoader
import com.wavemoth.app.BuildConfig
import com.wavemoth.app.WavemothApp
import com.wavemoth.app.ui.components.ConfirmDialog
import com.wavemoth.app.ui.components.SectionLabel
import com.wavemoth.app.ui.theme.WavemothColors
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val app = WavemothApp.instance
    val stopOnSwipe by app.settings.stopOnSwipe.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cacheBytes by remember { mutableLongStateOf(-1L) }
    var downloadBytes by remember { mutableLongStateOf(-1L) }
    val downloaded by app.downloads.songs.collectAsState()
    var confirmRemove by remember { mutableStateOf(false) }

    suspend fun measureCache() {
        cacheBytes = withContext(Dispatchers.IO) {
            app.audioCache.cacheSpace + (context.imageLoader.diskCache?.size ?: 0L)
        }
    }
    LaunchedEffect(Unit) { measureCache() }
    LaunchedEffect(downloaded.size) { downloadBytes = withContext(Dispatchers.IO) { app.downloadCache.cacheSpace } }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = WavemothColors.TextPrimary) }
        }
        Text("Settings", style = MaterialTheme.typography.headlineMedium, color = WavemothColors.TextPrimary, modifier = Modifier.padding(start = 20.dp, bottom = 4.dp))

        SectionHeader("Playback")
        SwitchRow(
            title = "Stop music when app is closed",
            subtitle = "Swiping Wavemoth away from Recents stops playback and removes the notification",
            checked = stopOnSwipe,
            onChange = app.settings::setStopOnSwipe,
        )
        InfoRow("Audio quality", "Highest available: Opus 160 kbps")

        SectionHeader("Downloads")
        InfoRow(
            title = "Downloaded songs",
            subtitle = if (downloadBytes < 0) "Calculating…" else "${downloaded.size} songs · ${formatSize(downloadBytes)}",
        )
        InfoRow(
            title = "Remove all downloads",
            subtitle = "Songs stay in your library and stream again",
            onClick = if (downloaded.isEmpty()) null else ({ confirmRemove = true }),
        )

        SectionHeader("Storage")
        InfoRow(
            title = "Clear cache",
            subtitle = when {
                cacheBytes < 0 -> "Calculating…"
                else -> "${formatSize(cacheBytes)} of saved songs and album art. Your library is kept."
            },
            onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        app.audioCache.keys.toList().forEach { runCatching { app.audioCache.removeResource(it) } }
                        context.imageLoader.diskCache?.clear()
                    }
                    context.imageLoader.memoryCache?.clear()
                    measureCache()
                }
            },
        )

        SectionHeader("About")
        InfoRow("Version", BuildConfig.VERSION_NAME)
        InfoRow("Music", "Streams from YouTube Music")
        InfoRow("Lyrics", "Provided by LRCLIB (lrclib.net)")
    }

    ConfirmDialog(
        visible = confirmRemove,
        title = "Remove all downloads?",
        message = "Songs stay in your library and will stream again.",
        confirmLabel = "Remove",
        onDismiss = { confirmRemove = false },
        onConfirm = app.downloads::removeAll,
    )
}

@Composable
private fun SectionHeader(text: String) {
    SectionLabel(text, Modifier.padding(bottom = 0.dp))
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = WavemothColors.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = WavemothColors.TextSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,  // the whole row toggles
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = WavemothColors.RedDeep,
                checkedBorderColor = WavemothColors.RedDeep,
                uncheckedThumbColor = WavemothColors.TextSecondary,
                uncheckedTrackColor = WavemothColors.Background,
                uncheckedBorderColor = WavemothColors.Hairline,
            ),
        )
    }
}

@Composable
private fun InfoRow(title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = WavemothColors.TextPrimary)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = WavemothColors.TextSecondary)
    }
    HorizontalDivider(color = WavemothColors.Hairline, modifier = Modifier.padding(horizontal = 20.dp))
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.0f MB".format(bytes / (1024.0 * 1024))
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
