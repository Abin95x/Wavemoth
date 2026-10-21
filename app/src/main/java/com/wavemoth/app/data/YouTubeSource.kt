package com.wavemoth.app.data

import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Music catalogue backed by YouTube Music, via NewPipeExtractor.
 * Pure JVM (no Android classes) so it can be exercised from unit tests.
 * All calls block on the network: call them off the main thread.
 */
object YouTubeSource {

    data class SearchPage(val songs: List<Song>, val nextPage: Page?)

    data class Artist(val name: String, val url: String, val thumbnail: String, val subscribers: Long)

    /** A playlist or an album on YouTube Music. */
    data class RemotePlaylist(val name: String, val url: String, val thumbnail: String, val owner: String, val isAlbum: Boolean)

    data class PlaylistPage(val playlist: RemotePlaylist, val songs: List<Song>, val nextPage: Page?)

    /** A playable audio URL plus the headers YouTube expects when fetching it. */
    data class ResolvedAudio(val url: String, val headers: Map<String, String>, val contentLength: Long)

    private val service get() = ServiceList.YouTube
    private val songFilter = listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS)

    private const val STREAM_TTL_MS = 3 * 60 * 60 * 1000L  // YouTube URLs expire after ~6h
    private val streamCache = ConcurrentHashMap<String, Pair<StreamInfo, Long>>()

    @Volatile private var initialized = false

    fun init(client: OkHttpClient, locale: Locale = Locale.getDefault()) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val country = locale.country.ifBlank { "US" }
            NewPipe.init(OkHttpDownloader(client), Localization.fromLocale(locale), ContentCountry(country))
            initialized = true
        }
    }

    fun searchSongs(query: String): SearchPage {
        val info = SearchInfo.getInfo(service, service.searchQHFactory.fromQuery(query, songFilter, ""))
        return SearchPage(info.relatedItems.toSongs(), info.nextPage)
    }

    fun searchMore(query: String, page: Page): SearchPage {
        val handler = service.searchQHFactory.fromQuery(query, songFilter, "")
        val more = SearchInfo.getMoreItems(service, handler, page)
        return SearchPage(more.items.toSongs(), more.nextPage)
    }

    fun searchArtists(query: String): List<Artist> {
        val info = SearchInfo.getInfo(service, service.searchQHFactory.fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS), ""))
        return info.relatedItems.filterIsInstance<ChannelInfoItem>().map {
            Artist(it.name, it.url, bestImage(it.thumbnails) ?: "", it.subscriberCount)
        }
    }

    fun searchPlaylists(query: String, albums: Boolean): List<RemotePlaylist> {
        val filter = if (albums) YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS else YoutubeSearchQueryHandlerFactory.MUSIC_PLAYLISTS
        val info = SearchInfo.getInfo(service, service.searchQHFactory.fromQuery(query, listOf(filter), ""))
        return info.relatedItems.filterIsInstance<PlaylistInfoItem>().map {
            RemotePlaylist(it.name, it.url, bestImage(it.thumbnails).orEmpty(), it.uploaderName.orEmpty(), albums)
        }
    }

    /** First page of a YouTube Music playlist or album. */
    fun playlist(url: String): PlaylistPage {
        val info = PlaylistInfo.getInfo(service, url)
        val isAlbum = info.name.startsWith("Album – ") || info.id.startsWith("OLAK5uy_")
        val meta = RemotePlaylist(
            name = info.name.removePrefix("Album – ").removePrefix("Album - "),
            url = url,
            thumbnail = bestImage(info.thumbnails).orEmpty(),
            owner = info.uploaderName.orEmpty().removePrefix("by "),
            isAlbum = isAlbum,
        )
        return PlaylistPage(meta, info.relatedItems.toSongs(), info.nextPage)
    }

    fun playlistMore(url: String, page: Page): SearchPage {
        val more = PlaylistInfo.getMoreItems(service, url, page)
        return SearchPage(more.items.toSongs(), more.nextPage)
    }

    private fun bestImage(images: List<org.schabi.newpipe.extractor.Image>): String? =
        images.maxByOrNull { it.width.toLong() * it.height }?.url?.let { artworkUrl(it, "") }

    /** Songs YouTube considers related, used for autoplay when the queue runs out. */
    fun relatedSongs(id: String): List<Song> =
        streamInfo(id).relatedItems.toSongs().filter { it.id != id }

    fun resolveAudio(id: String): ResolvedAudio {
        val streams = streamInfo(id).audioStreams.filter {
            it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl && !it.content.isNullOrBlank()
        }
        // Skip dubbed / audio-description tracks when the original is available.
        val original = streams.filter { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
        val best = original.ifEmpty { streams }.maxWithOrNull(bestQualityFirst)
            ?: throw ExtractionException("No playable audio stream for $id")
        val url = best.content
        return ResolvedAudio(url, headersFor(url), contentLengthOf(url))
    }

    /**
     * Highest quality wins: the highest bitrate, and Opus over AAC when they're close, since Opus
     * sounds better at the same bitrate. Without an account YouTube's best is Opus ~160 kbps (itag 251).
     */
    private val bestQualityFirst = compareBy<AudioStream>(
        { kbps(it) + if (it.format == MediaFormat.WEBMA_OPUS) 20 else 0 },
        { kbps(it) },
    )

    private fun kbps(s: AudioStream): Int = maxOf(s.averageBitrate, s.bitrate / 1000)

    /** Drop a cached stream (e.g. after its URL was rejected) so the next play re-resolves it. */
    fun invalidate(id: String) {
        streamCache.remove(id)
    }

    private fun streamInfo(id: String): StreamInfo {
        val now = System.currentTimeMillis()
        streamCache[id]?.let { (info, at) -> if (now - at < STREAM_TTL_MS) return info }
        val info = StreamInfo.getInfo(service, "https://www.youtube.com/watch?v=$id")
        streamCache[id] = info to now
        return info
    }

    fun headersFor(url: String): Map<String, String> = buildMap {
        if (YoutubeParsingHelper.isVisionOsStreamingUrl(url)) {
            put("User-Agent", YoutubeParsingHelper.getVisionOsUserAgent(null))
        } else {
            put("User-Agent", OkHttpDownloader.USER_AGENT)
        }
        if (YoutubeParsingHelper.isWebStreamingUrl(url)) {
            put("Origin", "https://www.youtube.com")
            put("Referer", "https://www.youtube.com/")
        }
    }

    /** Progressive YouTube URLs carry their byte size in the `clen` query parameter. */
    fun contentLengthOf(url: String): Long =
        Regex("[?&]clen=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull() ?: -1L

    private fun List<out InfoItem>.toSongs(): List<Song> =
        mapNotNull { (it as? StreamInfoItem)?.toSong() }.distinctBy { it.id }

    private fun StreamInfoItem.toSong(): Song? {
        val id = videoId(url) ?: return null
        val thumb = thumbnails.maxByOrNull { it.width.toLong() * it.height }?.url
        return Song(
            id = id,
            title = name,
            artist = uploaderName?.removeSuffix(" - Topic")?.ifBlank { null } ?: "Unknown artist",
            durationSec = duration,
            thumbnail = artworkUrl(thumb, id),
        )
    }

    private val idPattern = Regex("(?:v=|youtu\\.be/|/shorts/)([A-Za-z0-9_-]{11})")

    fun videoId(url: String?): String? = url?.let { idPattern.find(it)?.groupValues?.get(1) }

    /** Ask for a large square cover when the thumbnail host supports sizing parameters. */
    fun artworkUrl(thumb: String?, id: String): String = when {
        thumb == null -> "https://i.ytimg.com/vi/$id/hqdefault.jpg"
        thumb.contains("googleusercontent.com") || thumb.contains("ggpht.com") ->
            thumb.replace(Regex("=w\\d+-h\\d+.*$"), "=w1000-h1000-l90-rj")
        else -> thumb
    }
}
