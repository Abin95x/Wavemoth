package com.wavemoth.app.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import com.wavemoth.app.data.YouTubeSource
import kotlin.math.min

/**
 * Reads YouTube audio as a series of small ranged requests instead of one long download.
 * YouTube throttles or cuts off long single requests; short ranges keep playback smooth.
 * Non-YouTube URLs pass straight through.
 */
@UnstableApi
class ChunkedDataSource(
    private val upstream: HttpDataSource,
    private val chunkSize: Long = 512 * 1024L,
) : DataSource {

    private var spec: DataSpec? = null
    private var chunked = false
    private var position = 0L                 // absolute offset of the next byte
    private var end = C.LENGTH_UNSET.toLong() // absolute exclusive end, if known
    private var chunkLeft = 0L
    private var upstreamOpen = false

    override fun addTransferListener(transferListener: TransferListener) =
        upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        spec = dataSpec
        chunked = dataSpec.uri.path?.startsWith("/videoplayback") == true
        if (!chunked) {
            upstreamOpen = true
            return upstream.open(dataSpec)
        }

        position = dataSpec.position
        val total = YouTubeSource.contentLengthOf(dataSpec.uri.toString())
        end = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.position + dataSpec.length
            total > 0 -> total
            else -> C.LENGTH_UNSET.toLong()
        }
        if (end != C.LENGTH_UNSET.toLong() && position >= end) return 0

        openChunk()
        if (end == C.LENGTH_UNSET.toLong()) {
            totalFromContentRange()?.let { end = it }
        }
        return if (end == C.LENGTH_UNSET.toLong()) C.LENGTH_UNSET.toLong() else end - dataSpec.position
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!chunked) return upstream.read(buffer, offset, length)
        if (length == 0) return 0
        var freshChunk = false
        while (true) {
            if (end != C.LENGTH_UNSET.toLong() && position >= end) return C.RESULT_END_OF_INPUT

            if (chunkLeft == 0L) {
                closeUpstream()
                try {
                    openChunk()
                } catch (e: HttpDataSource.InvalidResponseCodeException) {
                    if (e.responseCode == 416) return C.RESULT_END_OF_INPUT  // read past the end
                    throw e
                }
                freshChunk = true
            }

            val read = upstream.read(buffer, offset, min(length.toLong(), chunkLeft).toInt())
            if (read == C.RESULT_END_OF_INPUT) {
                // Unknown size: this is the real end of the file.
                if (end == C.LENGTH_UNSET.toLong()) return C.RESULT_END_OF_INPUT
                // A brand-new range that returns nothing would loop forever; give up instead.
                if (freshChunk) throw java.io.IOException("Audio server returned an empty range at $position")
                chunkLeft = 0  // short chunk: request the rest as a new range
                continue
            }
            position += read
            chunkLeft -= read
            return read
        }
    }

    private fun openChunk() {
        val base = spec ?: error("open() not called")
        val length = if (end == C.LENGTH_UNSET.toLong()) chunkSize else min(chunkSize, end - position)
        val opened = upstream.open(base.buildUpon().setPosition(position).setLength(length).build())
        upstreamOpen = true
        chunkLeft = if (opened == C.LENGTH_UNSET.toLong()) length else min(opened, length)
    }

    /** Parse the full size from a header like `Content-Range: bytes 0-524287/3456789`. */
    private fun totalFromContentRange(): Long? =
        upstream.responseHeaders.entries
            .firstOrNull { it.key.equals("Content-Range", ignoreCase = true) }
            ?.value?.firstOrNull()?.substringAfter('/')?.toLongOrNull()

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        closeUpstream()
        spec = null
    }

    private fun closeUpstream() {
        if (upstreamOpen) {
            upstreamOpen = false
            upstream.close()
        }
    }
}
