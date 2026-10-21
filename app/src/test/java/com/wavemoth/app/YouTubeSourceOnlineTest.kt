package com.wavemoth.app

import com.wavemoth.app.data.YouTubeSource
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Talks to YouTube for real. Skipped unless WAVEMOTH_ONLINE_TESTS=1, e.g.
 *   WAVEMOTH_ONLINE_TESTS=1 ./gradlew testDebugUnitTest
 */
class YouTubeSourceOnlineTest {

    companion object {
        private val client = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()

        @BeforeClass @JvmStatic
        fun setUp() {
            assumeTrue(System.getenv("WAVEMOTH_ONLINE_TESTS") == "1")
            YouTubeSource.init(client)
        }
    }

    @Test
    fun searchResolveAndFetchAudio() {
        val page = YouTubeSource.searchSongs("daft punk get lucky")
        println("search: ${page.songs.size} songs, more=${page.nextPage != null}")
        page.songs.take(3).forEach { println("  ${it.id} | ${it.title} | ${it.artist} | ${it.durationSec}s | ${it.thumbnail}") }
        assertTrue(page.songs.isNotEmpty())

        val song = page.songs.first()
        val audio = YouTubeSource.resolveAudio(song.id)
        println("audio host=${audio.url.substringAfter("://").substringBefore('/')} clen=${audio.contentLength} headers=${audio.headers.keys}")
        // Highest quality available without an account: Opus ~160 kbps.
        val itag = Regex("[?&]itag=(\\d+)").find(audio.url)?.groupValues?.get(1)
        println("audio itag=$itag")
        assertTrue("expected best-quality itag 251, got $itag", itag == "251")

        // Two separate 256 KB ranges, the way the player requests audio in chunks.
        for (start in listOf(0L, 1_048_576L)) {
            val end = start + 262_143
            val get = fetch(audio.url, audio.headers, start, end, post = false)
            val post = fetch(audio.url, audio.headers, start, end, post = true)
            println("range $start-$end: GET=$get POST=$post")
            assertTrue("range $start failed: GET=$get POST=$post", get.first == 206 || post.first == 206)
        }

        val related = YouTubeSource.relatedSongs(song.id)
        println("related: ${related.size}, e.g. ${related.take(2).map { it.title }}")
    }

    private fun fetch(url: String, headers: Map<String, String>, start: Long, end: Long, post: Boolean): Pair<Int, Long> {
        val builder = Request.Builder().url(url).header("Range", "bytes=$start-$end")
        headers.forEach { (k, v) -> builder.header(k, v) }
        if (post) builder.post(byteArrayOf(0x78, 0).toRequestBody())
        client.newCall(builder.build()).execute().use { r ->
            return r.code to (r.body?.bytes()?.size?.toLong() ?: -1)
        }
    }
}
