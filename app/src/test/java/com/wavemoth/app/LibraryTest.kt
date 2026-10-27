package com.wavemoth.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wavemoth.app.FakePlayer.Companion.song
import com.wavemoth.app.data.Catalog
import com.wavemoth.app.data.LibraryRepository
import com.wavemoth.app.data.PlaylistImporter
import com.wavemoth.app.data.YouTubeSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LibraryTest {

    @get:Rule val tmp = TemporaryFolder()
    private lateinit var file: File
    private val originalSearch = Catalog.search

    @Before fun setUp() { file = File(tmp.root, "library.json") }
    @After fun tearDown() { Catalog.search = originalSearch }

    /** Saving happens in the background; wait until the file has caught up. */
    private fun reloadWhenSaved(expect: (LibraryRepository) -> Boolean): LibraryRepository {
        repeat(100) {
            if (file.exists()) {
                val fresh = LibraryRepository(file)
                if (expect(fresh)) return fresh
            }
            Thread.sleep(20)
        }
        error("library was not saved")
    }

    @Test
    fun likesPlaylistsAndHistoryWorkAndPersist() {
        val lib = LibraryRepository(file)
        val a = song(1); val b = song(2); val c = song(3)

        lib.toggleLike(a); lib.toggleLike(b); lib.toggleLike(a)
        assertEquals(listOf(b), lib.liked.value)

        lib.addRecent(a); lib.addRecent(b); lib.addRecent(a)
        assertEquals(listOf(a, b), lib.recent.value)  // newest first, no duplicates
        repeat(60) { lib.addRecent(song(100 + it)) }
        assertEquals(50, lib.recent.value.size)       // capped

        val p = lib.createPlaylist("  Road Trip ", a)
        lib.addToPlaylist(p.id, b); lib.addToPlaylist(p.id, b); lib.addToPlaylist(p.id, c)
        assertEquals(listOf(a, b, c), lib.playlists.value.single().songs)  // no duplicate b
        lib.removeFromPlaylist(p.id, b.id)
        lib.renamePlaylist(p.id, "Drive")
        lib.renamePlaylist(p.id, "   ")  // blank name keeps the old one
        assertEquals("Drive", lib.playlists.value.single().name)
        assertEquals(listOf(a, c), lib.playlists.value.single().songs)

        val reloaded = reloadWhenSaved { it.playlists.value.firstOrNull()?.name == "Drive" && it.recent.value.size == 50 }
        assertEquals(listOf(b), reloaded.liked.value)
        assertEquals(listOf(a, c), reloaded.playlists.value.single().songs)

        lib.deletePlaylist(p.id)
        assertTrue(lib.playlists.value.isEmpty())
    }

    @Test
    fun backupRestoresIntoAnotherLibrary() {
        val lib = LibraryRepository(file)
        lib.toggleLike(song(1))
        lib.createPlaylist("Gym", song(2))
        val backup = lib.exportBackup()

        val other = LibraryRepository(File(tmp.root, "other.json"))
        other.toggleLike(song(9))
        assertEquals(1, other.restoreBackup(backup))
        assertEquals(setOf(song(9).id, song(1).id), other.liked.value.map { it.id }.toSet())  // merged
        assertEquals("Gym", other.playlists.value.single().name)
        assertEquals(1, other.restoreBackup(backup))  // restoring twice doesn't duplicate
        assertEquals(1, other.playlists.value.size)

        assertFalse(runCatching { other.restoreBackup("""{"hello":1}""") }.isSuccess)
        assertFalse(runCatching { other.restoreBackup("not json") }.isSuccess)
    }

    @Test
    fun importerUsesIdsDirectlyAndSearchesTheRest() {
        val searched = mutableListOf<String>()
        Catalog.search = { q ->
            searched += q
            YouTubeSource.SearchPage(listOf(song(77, title = "Third Song", artist = "Band C")), null)
        }
        val lib = LibraryRepository(file)
        val importer = PlaylistImporter(lib)
        val csv = """
            title,artist,album,youtube_id
            First Song,Band A,,vid00000001
            Second Song,Band B,,vid00000002
            Third Song,Band C,,
        """.trimIndent()
        importer.import("Party", csv)
        repeat(200) { if (importer.status.value?.finished == true) return@repeat; Thread.sleep(20) }

        val status = importer.status.value!!
        assertTrue(status.finished)
        assertEquals(3, status.found)
        val playlist = lib.playlists.value.single()
        assertEquals("Party", playlist.name)
        assertEquals(listOf("vid00000001", "vid00000002", song(77).id), playlist.songs.map { it.id })
        assertEquals(1, searched.size)  // only the row without an id was searched

        importer.dismiss()
        assertEquals(null, importer.status.value)
        importer.import("Empty", "nothing useful here")
        repeat(100) { if (importer.status.value?.finished == true) return@repeat; Thread.sleep(20) }
        assertTrue(importer.status.value?.error != null)
        assertEquals(1, lib.playlists.value.size)  // no empty playlist was created
    }

    @Test
    fun importerCanAddToLikedSongs() {
        Catalog.search = { YouTubeSource.SearchPage(emptyList(), null) }
        val lib = LibraryRepository(file)
        lib.toggleLike(song(9))
        val importer = PlaylistImporter(lib)
        val csv = """
            title,artist,album,youtube_id
            First Song,Band A,,vid00000001
            Again,Band A,,${song(9).id}
        """.trimIndent()
        importer.import("Favourites", csv, intoLiked = true)
        repeat(200) { if (importer.status.value?.finished == true) return@repeat; Thread.sleep(20) }

        val status = importer.status.value!!
        assertTrue(status.finished)
        assertTrue(status.intoLiked)
        assertEquals(null, status.playlistId)
        assertTrue(lib.playlists.value.isEmpty())  // no playlist created
        assertEquals(listOf("vid00000001", song(9).id), lib.liked.value.map { it.id })  // no duplicate
    }

    @Test
    fun topArtistsRankLikesAndPlays() {
        val liked = listOf(song(1, artist = "Band A feat. Singer B"), song(2, artist = "band a"))
        val recent = listOf(song(3, artist = "Band C - Topic"), song(1, artist = "Band A feat. Singer B"))
        val top = com.wavemoth.app.data.TopArtists.from(liked, recent)
        assertEquals(listOf("Band A", "Singer B", "Band C"), top.map { it.name })  // case-insensitive merge, "- Topic" dropped
        assertEquals(2, com.wavemoth.app.data.TopArtists.from(liked, recent, limit = 2).size)
    }
}
