package com.wavemoth.app.playback

import kotlin.random.Random

/**
 * Shuffle reorders the queue itself instead of using the player's hidden shuffle order, so the
 * queue on screen, "Play next", "Add to queue" and autoplay all see the order songs really play in.
 * Both functions return the new order as indices into the current queue.
 */
object QueueOrder {

    /** Shuffle on: the current song moves to the top and everything else is shuffled after it. */
    fun shuffle(size: Int, current: Int, random: Random = Random.Default): List<Int> {
        if (size == 0) return emptyList()
        val c = current.coerceIn(0, size - 1)
        return listOf(c) + (0 until size).filter { it != c }.shuffled(random)
    }

    /**
     * Shuffle off: songs go back to their [original] order (ids, as they were before shuffling).
     * Songs added while shuffled stay right after the song they followed, and songs added at the
     * end (Add to queue, autoplay) stay at the end.
     */
    fun restore(ids: List<String>, original: List<String>): List<Int> {
        // Where each original id sat; an id can appear more than once.
        val slots = HashMap<String, ArrayDeque<Int>>()
        original.forEachIndexed { pos, id -> slots.getOrPut(id) { ArrayDeque() }.addLast(pos) }
        val originalPos = arrayOfNulls<Int>(ids.size)
        ids.forEachIndexed { i, id -> originalPos[i] = slots[id]?.removeFirstOrNull() }

        val lastOriginal = ids.indices.lastOrNull { originalPos[it] != null } ?: -1
        val result = ids.indices.filter { originalPos[it] != null }.sortedBy { originalPos[it] }.toMutableList()
        (0 until lastOriginal).filter { originalPos[it] == null }.forEach { added ->
            if (added == 0) result.add(0, added) else result.add(result.indexOf(added - 1) + 1, added)
        }
        return result + (lastOriginal + 1 until ids.size)
    }
}
