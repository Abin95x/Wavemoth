package com.wavemoth.app

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wavemoth.app.playback.ChunkedDataSource
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@UnstableApi
@RunWith(AndroidJUnit4::class)
class ChunkedDataSourceTest {

    private val size = 3_500
    private val data = ByteArray(size) { (it % 251).toByte() }
    private val ytUrl = "https://rr1.googlevideo.com/videoplayback?itag=251&clen=$size&x=1"

    /** Serves [data] for ranged requests and records each requested range. */
    private inner class FakeHttp(
        private val shortFirstChunkBy: Int = 0,
        private val reportLength: Boolean = true,
    ) : HttpDataSource {
        val ranges = mutableListOf<Pair<Long, Long>>()
        private var pos = 0
        private var end = 0
        private var uri: Uri? = null
        private var headers: Map<String, List<String>> = emptyMap()

        override fun open(dataSpec: DataSpec): Long {
            uri = dataSpec.uri
            ranges += dataSpec.position to dataSpec.length
            if (dataSpec.position >= size) {
                throw HttpDataSource.InvalidResponseCodeException(416, "Range Not Satisfiable", null, emptyMap(), dataSpec, ByteArray(0))
            }
            pos = dataSpec.position.toInt()
            val wanted = if (dataSpec.length == C.LENGTH_UNSET.toLong()) size - pos else minOf(dataSpec.length.toInt(), size - pos)
            val served = if (ranges.size == 1) wanted - shortFirstChunkBy else wanted
            end = pos + served
            headers = mapOf("Content-Range" to listOf("bytes $pos-${pos + wanted - 1}/$size"))
            return if (reportLength) wanted.toLong() else C.LENGTH_UNSET.toLong()
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (pos >= end) return C.RESULT_END_OF_INPUT
            val n = minOf(length, end - pos)
            System.arraycopy(data, pos, buffer, offset, n)
            pos += n
            return n
        }

        override fun close() {}
        override fun getUri(): Uri? = uri
        override fun getResponseHeaders(): Map<String, List<String>> = headers
        override fun addTransferListener(transferListener: TransferListener) {}
        override fun setRequestProperty(name: String, value: String) {}
        override fun clearRequestProperty(name: String) {}
        override fun clearAllRequestProperties() {}
        override fun getResponseCode(): Int = 206
    }

    private fun readAll(source: ChunkedDataSource): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(700)
        while (true) {
            val n = source.read(buf, 0, buf.size)
            if (n == C.RESULT_END_OF_INPUT) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    @Test
    fun readsWholeFileInFixedRanges() {
        val http = FakeHttp()
        val source = ChunkedDataSource(http, chunkSize = 1_000)
        assertEquals(size.toLong(), source.open(DataSpec(Uri.parse(ytUrl))))
        assertArrayEquals(data, readAll(source))
        assertEquals(listOf(0L to 1000L, 1000L to 1000L, 2000L to 1000L, 3000L to 500L), http.ranges)
    }

    @Test
    fun startsFromSeekPosition() {
        val http = FakeHttp()
        val source = ChunkedDataSource(http, chunkSize = 1_000)
        val spec = DataSpec.Builder().setUri(ytUrl).setPosition(1_200).build()
        assertEquals((size - 1_200).toLong(), source.open(spec))
        assertArrayEquals(data.copyOfRange(1_200, size), readAll(source))
        assertEquals(1_200L, http.ranges.first().first)
    }

    @Test
    fun shortResponseIsResumedWithoutLosingBytes() {
        val http = FakeHttp(shortFirstChunkBy = 400)
        val source = ChunkedDataSource(http, chunkSize = 1_000)
        source.open(DataSpec(Uri.parse(ytUrl)))
        assertArrayEquals(data, readAll(source))
        assertEquals(600L, http.ranges[1].first)  // asked again from where the short chunk stopped
    }

    @Test
    fun learnsSizeFromContentRangeWhenUrlHasNone() {
        val http = FakeHttp()
        val source = ChunkedDataSource(http, chunkSize = 1_000)
        val url = "https://rr1.googlevideo.com/videoplayback?itag=251"
        assertEquals(size.toLong(), source.open(DataSpec(Uri.parse(url))))
        assertArrayEquals(data, readAll(source))
    }

    @Test
    fun otherUrlsPassStraightThrough() {
        val http = FakeHttp()
        val source = ChunkedDataSource(http, chunkSize = 1_000)
        source.open(DataSpec(Uri.parse("https://example.com/cover.jpg")))
        assertArrayEquals(data, readAll(source))
        assertEquals(listOf(0L to C.LENGTH_UNSET.toLong()), http.ranges)
    }
}
