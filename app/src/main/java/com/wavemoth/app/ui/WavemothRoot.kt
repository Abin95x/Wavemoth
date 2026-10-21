package com.wavemoth.app.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
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
    Tab("library", "Your Library", Icons.Outlined.LibraryMusic, Icons.Filled.LibraryMusic),
)

private fun NavHostController.openCategory(title: String, query: String, color: Color = Color(0xFF3A3A3A)) =
    navigate("category?title=${Uri.encode(title)}&query=${Uri.encode(query)}&color=${color.toArgb().toLong() and 0xFFFFFFFFL}")

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

    CompositionLocalProvider(LocalSongActions provides actions, LocalOverlayHost provides overlays) {
        Box(Modifier.fillMaxSize().background(WavemothColors.Background)) {
            Scaffold(
                containerColor = WavemothColors.Background,
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    Column(Modifier.background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))))) {
                        AnimatedVisibility(
                            visible = state.current != null,
                            enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
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
                                onPrevious = player::previous,
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
                    // Tabs cross-fade; opening a page slides it in slightly from the right.
                    enterTransition = {
                        if (targetState.destination.route in tabRoutes) fadeIn(tween(220))
                        else fadeIn(tween(260)) + slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 8 }
                    },
                    exitTransition = { fadeOut(tween(180)) },
                    popEnterTransition = { fadeIn(tween(220)) },
                    popExitTransition = {
                        if (initialState.destination.route in tabRoutes) fadeOut(tween(180))
                        else fadeOut(tween(200)) + slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 8 }
                    },
                ) {
                    composable("home") {
                        HomeScreen(
                            onOpenLiked = { nav.navigate("liked") },
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
                            onOpenCategory = { nav.openCategory(it.title, it.query, it.color) },
                            onOpenArtist = { nav.openArtist(it.name, it.thumbnail) },
                            onOpenPlaylist = { nav.openRemotePlaylist(it.url, it.name) },
                        )
                    }
                    composable("library") {
                        LibraryScreen(
                            onOpenLiked = { nav.navigate("liked") },
                            onOpenRecent = { nav.navigate("recent") },
                            onOpenPlaylist = { nav.navigate("playlist/$it") },
                        )
                    }
                    composable("liked") { LikedScreen(onBack = { nav.popBackStack() }) }
                    composable("recent") { RecentScreen(onBack = { nav.popBackStack() }) }
                    composable("playlist/{id}") { entry ->
                        PlaylistScreen(entry.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
                    }
                    composable(
                        "category?title={title}&query={query}&color={color}",
                        arguments = listOf(
                            navArgument("title") { defaultValue = "" },
                            navArgument("query") { defaultValue = "" },
                            navArgument("color") { type = NavType.LongType; defaultValue = 0xFF3A3A3AL },
                        ),
                    ) { entry ->
                        val args = entry.arguments
                        CategoryScreen(
                            title = args?.getString("title").orEmpty(),
                            query = args?.getString("query").orEmpty(),
                            colorArgb = args?.getLong("color") ?: 0xFF3A3A3AL,
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showPlayer && state.current != null,
                enter = slideInVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) { it } +
                    fadeIn(tween(200)),
                exit = slideOutVertically(tween(280, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(280)),
            ) {
                BackHandler { showPlayer = false }
                val progress by player.progress.collectAsState()
                NowPlayingScreen(
                    state = state,
                    progress = progress,
                    player = player,
                    liked = state.current?.id in likedIds,
                    onToggleLike = { state.current?.let(library::toggleLike) },
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

@Composable
private fun BottomBar(nav: NavHostController) {
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
        tabs.forEach { tab ->
            val selected = route == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { nav.navigateToTab(tab.route) },
                icon = { Icon(if (selected) tab.selectedIcon else tab.icon, tab.label) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = Color.White,
                    unselectedIconColor = WavemothColors.TextSecondary,
                    unselectedTextColor = WavemothColors.TextSecondary,
                    indicatorColor = Color.Transparent,
                ),
            )
        }
    }
}

