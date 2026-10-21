package com.wavemoth.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wavemoth.app.FakePlayer.Companion.song
import com.wavemoth.app.data.Catalog
import com.wavemoth.app.data.LyricLine
import com.wavemoth.app.data.Lyrics
import com.wavemoth.app.data.PlaylistFile
import com.wavemoth.app.data.YouTubeSource
import com.wavemoth.app.playback.PlayerUiState
import com.wavemoth.app.ui.WavemothRoot
import com.wavemoth.app.ui.theme.WavemothTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Presses every button on every screen, on the computer (Robolectric), with a fake player and
 * fake search results so nothing touches the network. Lyrics here are placeholder text.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp-xxhdpi")  // a typical phone screen
@GraphicsMode(GraphicsMode.Mode.NATIVE)  // real graphics, so taps on rounded sheets hit-test correctly
class AppUiTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val tmp = TemporaryFolder()

    private val player = FakePlayer()
    private val app get() = WavemothApp.instance
    private val library get() = app.library

    private val saved = listOf(Catalog.search, Catalog.searchMore, Catalog.searchArtists, Catalog.searchPlaylists, Catalog.playlist, Catalog.lyrics, Catalog.related)

    @Before
    fun fakeCatalog() {
        Catalog.search = { q -> YouTubeSource.SearchPage((1..5).map { song(it + q.hashCode() % 1000 * 10, title = "$q result $it", artist = "Artist $q") }, null) }
        Catalog.searchMore = { _, _ -> YouTubeSource.SearchPage(emptyList(), null) }
        Catalog.searchArtists = { q -> listOf(YouTubeSource.Artist("$q Official", "https://yt.test/artist", "", 1_500_000)) }
        Catalog.searchPlaylists = { q, albums ->
            listOf(YouTubeSource.RemotePlaylist(if (albums) "Album $q" else "Playlist $q", "https://yt.test/list/$albums", "", "Owner", albums))
        }
        Catalog.playlist = { url ->
            YouTubeSource.PlaylistPage(YouTubeSource.RemotePlaylist("Remote List", url, "", "Owner", false), (1..4).map { song(900 + it, title = "Remote song $it") }, null)
        }
        Catalog.lyrics = { Lyrics(listOf(LyricLine(0, "Line one"), LyricLine(5_000, "Line two"), LyricLine(10_000, "Line three")), "", false) }
        Catalog.related = { emptyList() }
    }

    @After
    fun restoreCatalog() {
        @Suppress("UNCHECKED_CAST")
        run {
            Catalog.search = saved[0] as (String) -> YouTubeSource.SearchPage
            Catalog.searchMore = saved[1] as (String, org.schabi.newpipe.extractor.Page) -> YouTubeSource.SearchPage
            Catalog.searchArtists = saved[2] as (String) -> List<YouTubeSource.Artist>
            Catalog.searchPlaylists = saved[3] as (String, Boolean) -> List<YouTubeSource.RemotePlaylist>
            Catalog.playlist = saved[4] as (String) -> YouTubeSource.PlaylistPage
            Catalog.lyrics = saved[5] as (com.wavemoth.app.data.Song) -> Lyrics?
            Catalog.related = saved[6] as (String) -> List<com.wavemoth.app.data.Song>
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun launch(state: PlayerUiState? = null) {
        state?.let { player.state.value = it }
        rule.setContent { WavemothTheme { WavemothRoot(player) } }
        // Drive time by hand: a blinking text cursor inside a dialog never lets Robolectric go idle.
        rule.mainClock.autoAdvance = false
        rule.waitUntil(5_000) { settle(100); library.playlists.value.isNotEmpty() }  // starter playlist added on first launch
        settle()
    }

    /** Lets delays (debounce, animations) and background work finish. */
    private fun settle(ms: Long = 600) {
        repeat(4) {
            rule.mainClock.advanceTimeBy(ms / 4)
            ShadowLooper.idleMainLooper(ms / 4, TimeUnit.MILLISECONDS)
            Thread.sleep(30)
            rule.waitForIdle()
        }
    }

    private fun waitForText(text: String, substring: Boolean = false) {
        rule.waitUntil(5_000) {
            settle(100)
            rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun tab(label: String) =
        rule.onNode(hasText(label) and hasClickAction() and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))

    private fun click(text: String) { rule.onNodeWithText(text).performClick(); settle() }
    private fun clickDesc(desc: String) { rule.onNodeWithContentDescription(desc).performClick(); settle() }
    private fun exists(text: String, substring: Boolean = false) = rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    /** Answers the system file screen the app just opened with [file]. Returns the request intent. */
    private fun answerFilePicker(file: File): Intent {
        val shadow = shadowOf(rule.activity)
        val started = shadow.nextStartedActivityForResult ?: error("no file picker was opened")
        shadow.receiveResult(started.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        settle()
        return started.intent
    }

    // ---------------------------------------------------------------- tests

    @Test
    fun tabsSettingsAndHome() {
        launch()
        assertTrue(exists("Good", substring = true))
        assertTrue(exists("Your playlists"))

        tab("Search").performClick(); settle()
        assertTrue(exists("Browse all"))
        tab("Your Library").performClick(); settle()
        assertTrue(exists("Import playlist file"))
        tab("Home").performClick(); settle()
        assertTrue(exists("Your playlists"))

        // Switching tabs opens the tab's main page, not the page left open there.
        tab("Your Library").performClick(); settle()
        rule.onAllNodesWithText("My Playlist").onFirst().performClick(); settle()
        assertFalse(exists("Import playlist file"))
        tab("Home").performClick(); settle()
        tab("Your Library").performClick(); settle()
        assertTrue(exists("Import playlist file"))
        // Tapping the open tab also goes back to its main page.
        rule.onAllNodesWithText("My Playlist").onFirst().performClick(); settle()
        tab("Your Library").performClick(); settle()
        assertTrue(exists("Import playlist file"))
        tab("Home").performClick(); settle()

        clickDesc("Settings")
        assertTrue(app.settings.stopOnSwipe.value)
        click("Stop music when app is closed")
        assertFalse(app.settings.stopOnSwipe.value)
        click("Stop music when app is closed")
        assertTrue(app.settings.stopOnSwipe.value)
        assertTrue(exists("Highest available", substring = true))
        click("Clear cache")
        waitForText("of saved songs", substring = true)
        clickDesc("Back")
        assertTrue(exists("Your playlists"))
    }

    @Test
    fun starterPlaylistAndPlayButtons() {
        launch()
        val mine = library.playlists.value.single()
        assertEquals("My Playlist", mine.name)
        assertEquals(60, mine.songs.size)

        rule.onAllNodesWithText("My Playlist").onFirst().performClick(); settle()
        assertTrue(exists("60 songs", substring = true))

        clickDesc("Play")
        assertEquals(0, player.lastPlay!!.second)
        assertEquals(60, player.lastPlay!!.first.size)
        assertEquals("My Playlist", player.lastPlay!!.third)

        clickDesc("Shuffle play")
        assertEquals(mine.songs.map { it.id }.toSet(), player.lastPlay!!.first.map { it.id }.toSet())

        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("YSL"))
        click("YSL")
        assertEquals(5, player.lastPlay!!.second)
    }

    @Test
    fun songOptionsSheet() {
        launch()
        rule.onAllNodesWithText("My Playlist").onFirst().performClick(); settle()
        val first = library.playlists.value.single().songs.first()
        fun openOptions() { rule.onAllNodesWithContentDescription("More options").onFirst().performClick(); settle() }

        openOptions(); click("Add to Liked Songs")
        assertTrue(library.liked.value.any { it.id == first.id })

        openOptions(); assertTrue(exists("Remove from Liked Songs")); click("Add to queue")
        assertTrue("addToQueue:${first.id}" in player.calls)

        openOptions(); click("Play next")
        assertTrue("playNext:${first.id}" in player.calls)

        openOptions(); click("Add to playlist"); click("New playlist")
        rule.onNode(hasSetTextAction()).performTextInput("Road")
        click("Create")
        assertEquals(listOf(first.id), library.playlists.value.first { it.name == "Road" }.songs.map { it.id })

        openOptions(); click("Add to playlist"); click("Road")
        assertEquals(1, library.playlists.value.first { it.name == "Road" }.songs.size)  // no duplicate

        openOptions(); click("Remove from this playlist")
        assertEquals(59, library.playlists.value.first { it.name == "My Playlist" }.songs.size)

        val next = library.playlists.value.first { it.name == "My Playlist" }.songs.first()
        openOptions(); click("Go to artist")
        val artist = next.artist.split(',').first().trim()
        waitForText("Popular")
        clickDesc("Play")
        assertEquals(artist, player.lastPlay!!.third)
    }

    @Test
    fun createRenameExportDeletePlaylists() {
        launch()
        tab("Your Library").performClick(); settle()

        clickDesc("Create playlist")
        rule.onNode(hasSetTextAction()).performTextInput("Gym")
        click("Create")
        assertTrue(exists("This playlist is empty", substring = true))

        clickDesc("More"); click("Rename")
        rule.onNode(hasSetTextAction()).performTextClearance()
        rule.onNode(hasSetTextAction()).performTextInput("Gym Mix")
        click("Save")
        assertTrue(exists("Gym Mix"))
        assertTrue(library.playlists.value.any { it.name == "Gym Mix" })

        clickDesc("More")
        rule.onNodeWithText("Export as file").assertIsNotEnabled()  // nothing to export yet
        click("Delete playlist")
        assertFalse(library.playlists.value.any { it.name.startsWith("Gym") })
        assertTrue(exists("Import playlist file"))  // back on Your Library

        // Export a real playlist through the system "Save as" screen.
        rule.onAllNodesWithText("My Playlist").onFirst().performClick(); settle()
        clickDesc("More"); click("Export as file")
        val out = tmp.newFile("export.csv")
        val request = answerFilePicker(out)
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, request.action)
        assertEquals("My Playlist.csv", request.getStringExtra(Intent.EXTRA_TITLE))
        val rows = PlaylistFile.parse(out.readText())
        assertEquals(60, rows.size)
        assertTrue(rows.all { it.youtubeId != null })
    }

    @Test
    fun importBackupAndRestore() {
        launch()
        tab("Your Library").performClick(); settle()

        val csv = tmp.newFile("party.csv").apply {
            writeText("title,artist,album,youtube_id\nFirst Song,Band A,,vid00000001\nSecond Song,Band B,,vid00000002\nThird Song,Band C,,\n")
        }
        click("Import playlist file")
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, answerFilePicker(csv).action)
        waitForText("Imported 3 of 3 songs", substring = true)
        assertEquals(3, library.playlists.value.first { it.name != "My Playlist" }.songs.size)
        click("OK")
        assertFalse(exists("Imported 3 of 3 songs", substring = true))

        clickDesc("Import and export"); click("Back up library")
        val backup = tmp.newFile("backup.json")
        val request = answerFilePicker(backup)
        assertEquals("Wavemoth backup.json", request.getStringExtra(Intent.EXTRA_TITLE))
        assertTrue(backup.readText().contains("My Playlist"))

        library.playlists.value.forEach { library.deletePlaylist(it.id) }
        settle()
        clickDesc("Import and export"); click("Restore from backup")
        answerFilePicker(backup)
        rule.waitUntil(5_000) { library.playlists.value.any { it.name == "My Playlist" && it.songs.size == 60 } }
    }

    @Test
    fun miniPlayerNowPlayingAndLyrics() {
        val songs = listOf(song(1, "Alpha Track"), song(2, "Beta Track"), song(3, "Gamma Track"))
        launch(PlayerUiState(current = songs[0], isPlaying = true, queue = songs, queueIndex = 0, source = "Test Mix"))

        clickDesc("Pause")
        assertEquals(1, player.calls.count { it == "togglePlay" })
        clickDesc("Save to Liked Songs")
        assertTrue(library.liked.value.any { it.id == songs[0].id })

        // Swipe the mini player: left for next, right for previous. Neither opens the full player.
        rule.onNodeWithText("Alpha Track").performTouchInput { swipeLeft() }; settle()
        assertEquals(1, player.calls.count { it == "next" })
        rule.onNodeWithText("Alpha Track").performTouchInput { swipeRight() }; settle()
        assertEquals(1, player.calls.count { it == "previous" })
        assertFalse(exists("PLAYING FROM"))

        click("Alpha Track")  // open the full player
        assertTrue(exists("PLAYING FROM"))
        assertTrue(exists("Test Mix"))

        clickDesc("Next"); clickDesc("Previous"); clickDesc("Shuffle"); clickDesc("Repeat")
        assertTrue(player.calls.containsAll(listOf("next", "previous", "toggleShuffle", "cycleRepeat")))
        rule.onAllNodesWithContentDescription("Play").onLast().performClick(); settle()
        assertEquals(2, player.calls.count { it == "togglePlay" })
        rule.onAllNodesWithContentDescription("Remove from Liked Songs").onLast().performClick(); settle()
        assertTrue(library.liked.value.isEmpty())

        clickDesc("Lyrics")
        waitForText("Line two")
        assertTrue(exists("Lyrics from LRCLIB"))
        click("Line two")
        assertTrue("seekTo:5000" in player.calls)
        clickDesc("Close lyrics")
        assertFalse(exists("Line two"))

        clickDesc("More options"); click("Add to queue")
        assertTrue("addToQueue:${songs[0].id}" in player.calls)

        clickDesc("Close player")
        assertFalse(exists("PLAYING FROM"))

        click("Alpha Track")
        clickDesc("Queue")
        click("Beta Track")
        assertTrue("skipTo:1" in player.calls)
    }

    @Test
    fun searchFiltersArtistAlbumAndPlaylistPages() {
        launch()
        tab("Search").performClick(); settle()

        rule.onNode(hasSetTextAction()).performTextInput("drake")
        waitForText("drake result 2")
        click("drake result 2")
        assertEquals(1, player.lastPlay!!.second)
        assertEquals("Search", player.lastPlay!!.third)

        tab("Artists").performClick()
        waitForText("1.5M followers", substring = true)
        click("drake Official")
        waitForText("Popular")
        assertTrue(exists("Albums"))
        click("Album drake Official")
        waitForText("Remote song 1")
        clickDesc("Save to Your Library")
        rule.waitUntil(5_000) { settle(100); library.playlists.value.any { it.name == "Remote List" && it.songs.size == 4 } }
        clickDesc("Play")
        assertEquals("Remote List", player.lastPlay!!.third)
        clickDesc("Back"); clickDesc("Back")

        tab("Playlists").performClick()
        waitForText("Playlist drake")
        click("Playlist drake")
        waitForText("Remote song 1")
        clickDesc("Back")

        tab("Albums").performClick()
        waitForText("Album drake")

        clickDesc("Clear")
        assertTrue(exists("Browse all"))
        click("Pop")
        waitForText("pop hits result 1")
        clickDesc("Back")
        assertTrue(exists("Browse all"))
    }

    @Test
    fun likedRecentEmptyHomeAndErrors() {
        launch()
        library.toggleLike(song(1, "Liked One"))
        library.addRecent(song(2, "Recent One"))
        settle()

        tab("Your Library").performClick(); settle()
        rule.onAllNodesWithText("Liked Songs").onFirst().performClick(); settle()
        click("Liked One")
        assertEquals("Liked Songs", player.lastPlay!!.third)
        clickDesc("Back")
        rule.onAllNodesWithText("Recently played").onFirst().performClick(); settle()
        assertTrue(exists("Recent One"))
        clickDesc("Back")

        // Empty home offers a way to start.
        library.playlists.value.forEach { library.deletePlaylist(it.id) }
        library.toggleLike(song(1, "Liked One"))
        tab("Home").performClick(); settle()
        player.state.value = player.state.value.copy(error = "Something broke")
        waitForText("Something broke")
        rule.waitUntil(5_000) { settle(200); "clearError" in player.calls }
    }
}
