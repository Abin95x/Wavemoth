package com.wavemoth.app.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.wavemoth.app.WavemothApp
import com.wavemoth.app.data.YouTubeSource
import java.io.IOException

/**
 * Where song audio comes from. Every layer keys songs by their `wavemoth://song/<id>` URI, so the
 * playback cache, the download store and the downloader all agree on what a song is.
 */
@UnstableApi
object AudioSources {

    val cacheKey = CacheKeyFactory { spec -> spec.key ?: spec.uri.toString() }

    /** Resolve the song id to a stream URL, then fetch it in short ranged requests. */
    fun network(app: WavemothApp): DataSource.Factory {
        val http = OkHttpDataSource.Factory(app.okHttp)
        val chunked = DataSource.Factory { ChunkedDataSource(http.createDataSource()) }
        return ResolvingDataSource.Factory(chunked, ::resolve)
    }

    /**
     * Playback: downloaded songs first (read only), then the recently-played cache, then the network.
     * A cached or downloaded song plays without even looking up its URL.
     */
    fun forPlayback(app: WavemothApp): DataSource.Factory {
        val streamCache = CacheDataSource.Factory()
            .setCache(app.audioCache)
            .setUpstreamDataSourceFactory(network(app))
            .setCacheKeyFactory(cacheKey)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        return CacheDataSource.Factory()
            .setCache(app.downloadCache)
            .setUpstreamDataSourceFactory(streamCache)
            .setCacheWriteDataSinkFactory(null)
            .setCacheKeyFactory(cacheKey)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /** Downloads: written to the download store. A song already in the playback cache is copied from it. */
    fun forDownloads(app: WavemothApp): CacheDataSource.Factory {
        val fromPlaybackCache = CacheDataSource.Factory()
            .setCache(app.audioCache)
            .setUpstreamDataSourceFactory(network(app))
            .setCacheWriteDataSinkFactory(null)
            .setCacheKeyFactory(cacheKey)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        return CacheDataSource.Factory()
            .setCache(app.downloadCache)
            .setUpstreamDataSourceFactory(fromPlaybackCache)
            .setCacheKeyFactory(cacheKey)
    }

    /** Swap `wavemoth://song/<id>` for the real audio URL. Runs on a loader thread. */
    private fun resolve(spec: DataSpec): DataSpec {
        if (spec.uri.scheme != SONG_SCHEME) return spec
        val id = spec.uri.lastPathSegment ?: throw IOException("Bad song URI ${spec.uri}")
        val audio = try {
            YouTubeSource.resolveAudio(id)
        } catch (e: Exception) {
            throw IOException("Couldn't load this song", e)
        }
        return spec.buildUpon()
            .setUri(audio.url)
            .setHttpRequestHeaders(spec.httpRequestHeaders + audio.headers)
            .build()
    }
}
