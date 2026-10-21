package com.wavemoth.app.data

import org.schabi.newpipe.extractor.Page

/** Where song searches and lyrics come from (YouTube Music, LRCLIB). Tests swap in fakes. */
object Catalog {
    @Volatile var search: (query: String) -> YouTubeSource.SearchPage = { YouTubeSource.searchSongs(it) }
    @Volatile var searchArtists: (query: String) -> List<YouTubeSource.Artist> = { YouTubeSource.searchArtists(it) }
    @Volatile var searchPlaylists: (query: String, albums: Boolean) -> List<YouTubeSource.RemotePlaylist> = { q, a -> YouTubeSource.searchPlaylists(q, a) }
    @Volatile var playlist: (url: String) -> YouTubeSource.PlaylistPage = { YouTubeSource.playlist(it) }
    @Volatile var playlistMore: (url: String, page: Page) -> YouTubeSource.SearchPage = { u, p -> YouTubeSource.playlistMore(u, p) }
    @Volatile var related: (id: String) -> List<Song> = { YouTubeSource.relatedSongs(it) }
    @Volatile var lyrics: (song: Song) -> Lyrics? = { LrcLib.lyricsFor(it) }
    @Volatile var searchMore: (query: String, page: Page) -> YouTubeSource.SearchPage = { q, p -> YouTubeSource.searchMore(q, p) }
}
