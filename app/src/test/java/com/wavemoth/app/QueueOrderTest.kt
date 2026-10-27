package com.wavemoth.app

import com.wavemoth.app.playback.QueueOrder
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class QueueOrderTest {

    @Test
    fun shuffleMovesCurrentSongToTopAndKeepsEverySong() {
        val order = QueueOrder.shuffle(size = 10, current = 6, random = Random(1))
        assertEquals(6, order.first())
        assertEquals((0 until 10).toList(), order.sorted())
        assertEquals(emptyList<Int>(), QueueOrder.shuffle(0, 0))
    }

    @Test
    fun restoreGoesBackToOriginalOrder() {
        val original = listOf("a", "b", "c", "d")
        val shuffled = listOf("c", "a", "d", "b")
        assertEquals(listOf("a", "b", "c", "d"), QueueOrder.restore(shuffled, original).map { shuffled[it] })
    }

    @Test
    fun songsAddedWhileShuffledKeepTheirPlace() {
        val original = listOf("a", "b", "c", "d")
        // "x" was added with Play next while "c" played; "r1", "r2" came from autoplay at the end.
        val now = listOf("c", "x", "a", "d", "b", "r1", "r2")
        assertEquals(listOf("a", "b", "c", "x", "d", "r1", "r2"), QueueOrder.restore(now, original).map { now[it] })
    }

    @Test
    fun restoreHandlesRepeatedAndRemovedSongs() {
        val original = listOf("a", "b", "a", "c")
        val now = listOf("a", "c", "a")  // "b" was removed
        assertEquals(listOf("a", "a", "c"), QueueOrder.restore(now, original).map { now[it] })
    }
}
