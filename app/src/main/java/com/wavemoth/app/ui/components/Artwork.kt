package com.wavemoth.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.wavemoth.app.ui.theme.WavemothColors

@Composable
fun Artwork(
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
) {
    Box(modifier.clip(shape).background(WavemothColors.Elevated), contentAlignment = Alignment.Center) {
        if (url.isNullOrBlank()) {
            Icon(Icons.Filled.MusicNote, null, tint = WavemothColors.TextTertiary, modifier = Modifier.fillMaxSize(0.4f))
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun LikedSongsCover(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(6.dp)) =
    IconCover(Icons.Filled.Favorite, modifier, shape)

@Composable
fun RecentCover(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(6.dp)) =
    IconCover(Icons.Filled.History, modifier, shape)

@Composable
fun DownloadsCover(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(6.dp)) =
    IconCover(Icons.Filled.Download, modifier, shape)

/** Black tile with a hairline edge and a red icon: covers for the built-in collections. */
@Composable
fun IconCover(icon: ImageVector, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(6.dp)) {
    Box(
        modifier.clip(shape).background(WavemothColors.Surface).border(1.dp, WavemothColors.Hairline, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = WavemothColors.Red, modifier = Modifier.fillMaxSize(0.38f))
    }
}

/** Cover for a playlist: its first song's artwork, or a music note. */
@Composable
fun PlaylistCover(firstSongArt: String?, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(6.dp)) =
    Artwork(firstSongArt, modifier, shape)
