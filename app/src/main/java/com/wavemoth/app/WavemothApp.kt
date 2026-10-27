package com.wavemoth.app

import android.app.Application
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.wavemoth.app.data.LibraryRepository
import com.wavemoth.app.data.LrcLib
import com.wavemoth.app.data.PlaylistImporter
import com.wavemoth.app.data.Settings
import com.wavemoth.app.data.SkipHistory
import com.wavemoth.app.data.Suggestions
import com.wavemoth.app.data.YouTubeSource
import com.wavemoth.app.download.Downloads
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

@UnstableApi
class WavemothApp : Application(), ImageLoaderFactory {

    val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val library: LibraryRepository by lazy {
        LibraryRepository(File(filesDir, "library.json")).also { lib -> lib.onLiked = { id -> skips.forgive(id) } }
    }

    val importer: PlaylistImporter by lazy { PlaylistImporter(library) }

    val suggestions: Suggestions by lazy { Suggestions(library, skipped = { skips.all() }) }

    val settings: Settings by lazy { Settings(this) }

    val skips: SkipHistory by lazy { SkipHistory(this) }

    val databaseProvider: StandaloneDatabaseProvider by lazy { StandaloneDatabaseProvider(this) }

    /** Recently played audio is kept on disk so replays and seeks don't hit the network. One per process. */
    val audioCache: SimpleCache by lazy {
        SimpleCache(File(cacheDir, "audio"), LeastRecentlyUsedCacheEvictor(300L * 1024 * 1024), databaseProvider)
    }

    /** Downloaded songs. Never evicted, and not touched by "Clear cache". */
    val downloadCache: SimpleCache by lazy {
        SimpleCache(File(filesDir, "downloads"), NoOpCacheEvictor(), databaseProvider)
    }

    val downloads: Downloads by lazy { Downloads(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        YouTubeSource.init(okHttp)
        LrcLib.client = okHttp
        removeStarterPlaylistOnce()
    }

    /**
     * Older versions added a starter playlist on first launch. Remove it once, but only if it is
     * still exactly as it was added (same name, same 60 songs in the same order).
     */
    private fun removeStarterPlaylistOnce() {
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        if (!prefs.getBoolean("starter_playlist_added", false) || prefs.getBoolean("starter_playlist_removed", false)) return
        prefs.edit().putBoolean("starter_playlist_removed", true).apply()
        library.playlists.value
            .filter { it.name == "My Playlist" && it.songs.size == 60 && it.songs.first().id == "Rbgw_rduQpM" }
            .forEach { library.deletePlaylist(it.id) }
    }

    /** Shared image loader: memory + disk cache, and a short fade so covers don't pop in. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(okHttp)
        .crossfade(220)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
        .diskCache { DiskCache.Builder().directory(File(cacheDir, "images")).maxSizeBytes(150L * 1024 * 1024).build() }
        .respectCacheHeaders(false)  // cover art never changes for a given URL
        .build()

    companion object {
        lateinit var instance: WavemothApp
            private set
    }
}
