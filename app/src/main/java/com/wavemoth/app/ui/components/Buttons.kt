@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.wavemoth.app.ui.components

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wavemoth.app.WavemothApp
import com.wavemoth.app.data.Song
import com.wavemoth.app.download.DownloadStatus
import com.wavemoth.app.ui.theme.WavemothColors

private val PillShape = RoundedCornerShape(50)

/** Rounded text button. Filled is the red primary action; outlined is everything else. */
@Composable
fun PillButton(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .pressScale(interaction, 0.95f)
            .alpha(if (enabled) 1f else 0.4f)
            .height(42.dp)
            .clip(PillShape)
            .then(if (filled) Modifier.background(WavemothColors.RedDeep) else Modifier.border(1.dp, WavemothColors.Hairline, PillShape))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(start = if (icon != null) 14.dp else 20.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (filled) androidx.compose.ui.graphics.Color.White else WavemothColors.TextPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.titleSmall, color = if (filled) androidx.compose.ui.graphics.Color.White else WavemothColors.TextPrimary)
    }
}

/**
 * Downloads every song in [songs]. Shows a ring while they download and a red tick once
 * they're all on the phone.
 */
@Composable
fun DownloadAllButton(songs: List<Song>) {
    val context = LocalContext.current
    val all by LocalDownloadStatus.current
    val total = songs.size
    val done = songs.count { all[it.id] == DownloadStatus.Done }
    val active = songs.count { all[it.id] == DownloadStatus.Queued || all[it.id] == DownloadStatus.Downloading }
    when {
        total > 0 && done == total -> IconButton(onClick = { Toast.makeText(context, "All $total songs are downloaded", Toast.LENGTH_SHORT).show() }) {
            Icon(Icons.Filled.DownloadDone, "All songs downloaded", tint = WavemothColors.Red)
        }
        active > 0 -> {
            val fraction by animateFloatAsState(done.toFloat() / total, tween(Motion.SLOW), label = "dlRing")
            Box(Modifier.size(48.dp).semantics { contentDescription = "Downloading $done of $total" }, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.size(22.dp),
                    color = WavemothColors.Red,
                    trackColor = WavemothColors.Hairline,
                    strokeWidth = 2.dp,
                )
            }
        }
        else -> IconButton(
            enabled = total > 0,
            onClick = {
                WavemothApp.instance.downloads.download(songs)
                Toast.makeText(context, "Downloading ${total - done} songs", Toast.LENGTH_SHORT).show()
            },
        ) {
            Icon(Icons.Outlined.FileDownload, "Download all", tint = WavemothColors.TextSecondary, modifier = Modifier.padding(1.dp))
        }
    }
}
