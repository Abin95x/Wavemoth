package com.wavemoth.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * "Suggested for you" on Home: new songs related to what you played last and liked, scored by
 * [Radio] (so skips and junk are filtered the same way). Songs you already liked are left out.
 */
class Suggestions(
    private val library: LibraryRepository,
    private val skipped: () -> List<Song>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private var job: Job? = null
    private var lastSeeds: List<String> = emptyList()
    private var lastRefresh = 0L

    /** Fetches new suggestions if what you listen to changed and the last fetch is a while ago. */
    fun refresh() {
        val recent = library.recent.value
        val liked = library.liked.value
        val seeds = (recent.take(3) + liked.take(1)).map { it.id }.distinct()
        if (seeds.isEmpty() || job?.isActive == true) return
        val stale = clock() - lastRefresh > MIN_INTERVAL_MS
        if (_songs.value.isNotEmpty() && (seeds == lastSeeds || !stale)) return
        job = scope.launch {
            val pools = seeds.map { id -> async { runCatching { Catalog.related(id) }.getOrDefault(emptyList()) } }.awaitAll()
            if (pools.all { it.isEmpty() }) return@launch  // offline: try again next time
            val taste = Radio.taste(liked, recent, skipped())
            val picks = Radio.pick(pools, liked.mapTo(HashSet()) { it.id }, taste, likedSongs = emptyList(), count = COUNT)
            if (picks.isNotEmpty()) _songs.value = picks
            lastSeeds = seeds
            lastRefresh = clock()
        }
    }

    private companion object {
        const val COUNT = 12
        const val MIN_INTERVAL_MS = 15 * 60 * 1000L
    }
}
