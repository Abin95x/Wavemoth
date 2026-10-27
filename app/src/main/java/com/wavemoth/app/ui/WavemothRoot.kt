@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.wavemoth.app.ui

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import com.wavemoth.app.ui.collection.DownloadsScreen
import com.wavemoth.app.ui.components.LocalDownloadStatus
import com.wavemoth.app.ui.components.Motion
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wavemoth.app.WavemothApp
import com.wavemoth.app.data.Song
import com.wavemoth.app.playback.PlayerControls
import com.wavemoth.app.ui.collection.CategoryScreen
import com.wavemoth.app.ui.collection.LikedScreen
import com.wavemoth.app.ui.collection.PlaylistScreen
import com.wavemoth.app.ui.collection.RecentScreen
import com.wavemoth.app.ui.components.AddToPlaylistDialog
import com.wavemoth.app.ui.components.LocalSongActions
import com.wavemoth.app.ui.components.MiniPlayer
import com.wavemoth.app.ui.components.LocalOverlayHost
import com.wavemoth.app.ui.components.OverlayHost
import com.wavemoth.app.ui.components.NameDialog
import com.wavemoth.app.ui.components.SongActions
import com.wavemoth.app.ui.components.SongOptionsSheet
import com.wavemoth.app.ui.home.HomeScreen
import com.wavemoth.app.ui.library.LibraryScreen
import com.wavemoth.app.ui.player.NowPlayingScreen
import com.wavemoth.app.ui.search.SearchScreen
import com.wavemoth.app.ui.browse.ArtistScreen
import com.wavemoth.app.ui.browse.RemotePlaylistScreen
import com.wavemoth.app.ui.settings.SettingsScreen
import com.wavemoth.app.ui.theme.WavemothColors

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabRoutes = setOf("home", "search", "library")

private val tabs = listOf(
    Tab("home", "Home", Icons.Outlined.Home, Icons.Filled.Home),
    Tab("search", "Search", Icons.Outlined.Search, Icons.Filled.Search),
    Tab("library", "Library", Icons.Outlined.LibraryMusic, Icons.Filled.LibraryMusic),
)

private fun NavHostController.openCategory(title: String, query: String) =
    navigate("category?title=${Uri.encode(title)}&query=${Uri.encode(query)}")

/** Opens a tab at its main page, closing any pages opened on top of the current one. */
private fun NavHostController.navigateToTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id)
    launchSingleTop = true
}

private fun NavHostController.openArtist(name: String, image: String) =
    navigate("artist?name=${Uri.encode(name)}&image=${Uri.encode(image)}")

private fun NavHostController.openRemotePlaylist(url: String, title: String) =
    navigate("remote?url=${Uri.encode(url)}&title=${Uri.encode(title)}")

/** What the song-options sheet is showing, and for which playlist (if any). */
private data class OptionsTarget(val song: Song, val playlistId: String?)

@Composable
fun WavemothRoot(player: PlayerControls) {
    val nav = rememberNavController()
    val library = WavemothApp.instance.library
    val state by player.state.collectAsState()
    val liked by library.liked.collectAsState()
    val playlists by library.playlists.collectAsState()
    val currentId by rememberUpdatedState(state.current?.id)
    val likedIds = remember(liked) { liked.mapTo(HashSet()) { it.id } }
    val isPlaying by rememberUpdatedState(state.isPlaying)
    val downloads = WavemothApp.instance.downloads
    val downloadStatus = downloads.status.collectAsState()

    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var options by remember { mutableStateOf<OptionsTarget?>(null) }
    var pickPlaylistFor by remember { mutableStateOf<Song?>(null) }
    var newPlaylistFor by remember { mutableStateOf<Song?>(null) }
    val snackbar = remember { SnackbarHostState() }

    val actions = remember {
        SongActions(
            play = { songs, index, source -> player.play(songs, index, source) },
            showOptions = { song, playlistId -> options = OptionsTarget(song, playlistId) },
            currentSongId = { currentId },
            isPlaying = { isPlaying },
        )
    }

    // Show each playback error once: clear it right away, then show the message.
    LaunchedEffect(player) {
        player.state.map { it.error }.filterNotNull().collect { message ->
            player.clearError()
            launch { snackbar.showSnackbar(message) }
        }
    }

    val overlays = remember { OverlayHost() }

    CompositionLocalProvider(LocalSongActions provides actions, LocalOverlayHost provides overlays, LocalDownloadStatus provides downloadStatus) {
        Box(Modifier.fillMaxSize().background(WavemothColors.Background)) {
            Scaffold(
                containerColor = WavemothColors.Background,
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    Column(Modifier.background(WavemothColors.Background)) {
                        AnimatedVisibility(
                            visible = state.current != null,
                            enter = expandVertically(tween(Motion.SLOW, easing = Motion.Emphasized)) + fadeIn(tween(Motion.MEDIUM)),
                            exit = shrinkVertically(tween(Motion.MEDIUM, easing = Motion.Emphasized)) + fadeOut(tween(Motion.FAST)),
                        ) {
                            // Position is collected here so its updates only touch the mini player.
                            val progress by player.progress.collectAsState()
                            MiniPlayer(
                                state = state,
                                progress = progress,
                                liked = state.current?.id in likedIds,
                                onToggleLike = { state.current?.let(library::toggleLike) },
                                onTogglePlay = player::togglePlay,
                                onNext = player::next,
                                onPrevious = player::skipToPrevious,
                                onOpen = { showPlayer = true },
                            )
                        }
                        BottomBar(nav)
                    }
                },
            ) { padding ->
                NavHost(
                    nav,
                    startDestination = "home",
                    modifier = Modifier.padding(padding),
                    // Tabs cross-fade; a new page rises in slightly and settles, and sinks back when closed.
                    enterTransition = {
                        if (targetState.destination.route in tabRoutes) fadeIn(tween(Motion.MEDIUM))
                        else fadeIn(tween(Motion.MEDIUM)) + slideInVertically(tween(Motion.SLOW, easing = Motion.Emphasized)) { it / 14 }
                    },
                    exitTransition = { fadeOut(tween(Motion.FAST)) },
                    popEnterTransition = { fadeIn(tween(Motion.MEDIUM)) },
                    popExitTransition = {
                        if (initialState.destination.route in tabRoutes) fadeOut(tween(Motion.FAST))
                        else fadeOut(tween(Motion.MEDIUM)) + slideOutVertically(tween(Motion.MEDIUM, easing = Motion.Emphasized)) { it / 14 }
                    },
                ) {
                    composable("home") {
                        HomeScreen(
                            onOpenLiked = { nav.navigate("liked") },
                            onOpenDownloads = { nav.navigate("downloads") },
                            onOpenPlaylist = { nav.navigate("playlist/$it") },
                            onOpenSearch = { nav.navigateToTab("search") },
                            onOpenSettings = { nav.navigate("settings") },
                        )
                    }
                    composable(
                        "artist?name={name}&image={image}",
                        arguments = listOf(navArgument("name") { defaultValue = "" }, navArgument("image") { defaultValue = "" }),
                    ) { entry ->
                        ArtistScreen(
                            name = entry.arguments?.getString("name").orEmpty(),
                            image = entry.arguments?.getString("image").orEmpty(),
                            onOpenAlbum = { nav.openRemotePlaylist(it.url, it.name) },
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable(
                        "remote?url={url}&title={title}",
                        arguments = listOf(navArgument("url") { defaultValue = "" }, navArgument("title") { defaultValue = "" }),
                    ) { entry ->
                        RemotePlaylistScreen(
                            url = entry.arguments?.getString("url").orEmpty(),
                            title = entry.arguments?.getString("title").orEmpty(),
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
                    composable("search") {
                        SearchScreen(
                            onOpenCategory = { nav.openCategory(it.title, it.query) },
                            onOpenArtist = { nav.openArtist(it.name, it.thumbnail) },
                            onOpenPlaylist = { nav.openRemotePlaylist(it.url, it.name) },
                        )
                    }
                    composable("library") {
                        LibraryScreen(
                            onOpenLiked = { nav.navigate("liked") },
                            onOpenRecent = { nav.navigate("recent") },
                            onOpenDownloads = { nav.navigate("downloads") },
                            onOpenPlaylist = { nav.navigate("playlist/$it") },
                        )
                    }
                    composable("liked") { LikedScreen(onBack = { nav.popBackStack() }, onOpenPlaylist = { nav.navigate("playlist/$it") }) }
                    composable("recent") { RecentScreen(onBack = { nav.popBackStack() }) }
                    composable("downloads") { DownloadsScreen(onBack = { nav.popBackStack() }) }
                    composable("playlist/{id}") { entry ->
                        PlaylistScreen(entry.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
                    }
                    composable(
                        "category?title={title}&query={query}",
                        arguments = listOf(
                            navArgument("title") { defaultValue = "" },
                            navArgument("query") { defaultValue = "" },
                        ),
                    ) { entry ->
                        val args = entry.arguments
                        CategoryScreen(
                            title = args?.getString("title").orEmpty(),
                            query = args?.getString("query").orEmpty(),
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showPlayer && state.current != null,
                enter = slideInVertically(tween(Motion.SLOW, easing = Motion.Emphasized)) { it } + fadeIn(tween(Motion.MEDIUM)),
                exit = slideOutVertically(tween(Motion.MEDIUM, easing = Motion.Emphasized)) { it } + fadeOut(tween(Motion.MEDIUM)),
            ) {
                BackHandler { showPlayer = false }
                val progress by player.progress.collectAsState()
                NowPlayingScreen(
                    state = state,
                    progress = progress,
                    player = player,
                    liked = state.current?.id in likedIds,
                    onToggleLike = { state.current?.let(library::toggleLike) },
                    onDownload = { state.current?.let { downloads.download(listOf(it)) } },
                    onMore = { state.current?.let { options = OptionsTarget(it, null) } },
                    onClose = { showPlayer = false },
                )
            }

            // Sheets and dialogs, above everything else.
            overlays.Render()
        }

        val target = options
        SongOptionsSheet(
            song = target?.song,
            liked = target != null && target.song.id in likedIds,
            inPlaylist = target?.playlistId != null,
            onDismiss = { options = null },
            onToggleLike = { target?.let { library.toggleLike(it.song) } },
            onAddToPlaylist = { pickPlaylistFor = target?.song },
            onPlayNext = { target?.let { player.playNext(it.song) } },
            onAddToQueue = { target?.let { player.addToQueue(it.song) } },
            onGoToArtist = {
                target?.let {
                    showPlayer = false
                    nav.openArtist(it.song.artist.split(',').first().trim(), "")
                }
            },
            onRemoveFromPlaylist = { target?.let { t -> t.playlistId?.let { library.removeFromPlaylist(it, t.song.id) } } },
            onDownload = { target?.let { downloads.download(listOf(it.song)) } },
            onRemoveDownload = { target?.let { downloads.remove(it.song.id) } },
        )

        AddToPlaylistDialog(
            visible = pickPlaylistFor != null,
            playlists = playlists,
            onDismiss = { pickPlaylistFor = null },
            onPick = { p ->
                pickPlaylistFor?.let { library.addToPlaylist(p.id, it) }
                pickPlaylistFor = null
            },
            onNew = {
                newPlaylistFor = pickPlaylistFor
                pickPlaylistFor = null
            },
        )

        NameDialog(
            visible = newPlaylistFor != null,
            title = "Give your playlist a name",
            onDismiss = { newPlaylistFor = null },
            onConfirm = { name ->
                newPlaylistFor?.let { library.createPlaylist(name, it) }
                newPlaylistFor = null
            },
        )
    }
}

/** Three tabs on black. A short red line slides to the selected one. */
@Composable
private fun BottomBar(nav: NavHostController) {
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val selected = tabs.indexOfFirst { it.route == route }
    // Keep the line under the last tab while a page is open on top of it.
    var lastSelected by remember { mutableIntStateOf(0) }
    if (selected >= 0) lastSelected = selected
    val position by animateFloatAsState(lastSelected.toFloat(), tween(Motion.SLOW, easing = Motion.Emphasized), label = "tabLine")
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(WavemothColors.Hairline, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
                val cell = size.width / tabs.size
                val line = 20.dp.toPx()
                val x = cell * position + (cell - line) / 2
                drawLine(WavemothColors.Red, Offset(x, 1.dp.toPx()), Offset(x + line, 1.dp.toPx()), 2.dp.toPx())
            }
            .navigationBarsPadding()
            .height(60.dp),
    ) {
        tabs.forEachIndexed { i, tab ->
            val active = i == lastSelected
            val tint by animateColorAsState(if (active) WavemothColors.TextPrimary else WavemothColors.TextTertiary, tween(Motion.MEDIUM), label = "tabTint")
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(
                        selected = active,
                        role = Role.Tab,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { nav.navigateToTab(tab.route) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(if (active) tab.selectedIcon else tab.icon, null, tint = tint, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(3.dp))
                Text(tab.label, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.4.sp), color = tint)
            }
        }
    }
}
