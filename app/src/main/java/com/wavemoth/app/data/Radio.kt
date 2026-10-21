package com.wavemoth.app.data

import kotlin.random.Random

/**
 * Picks what plays after the queue runs out ("autoplay radio"). Pure JVM so it can be unit tested.
 *
 * Candidates are songs YouTube lists as related to the last few songs. Each gets a score:
 *  - agreement: songs related to several recent songs, and ranked high, score more
 *  - taste: artists you like and play often score more; liked songs get a small boost
 *  - freshness: anything played recently is penalised (the last few are excluded outright)
 *  - skips: songs you skipped are excluded, and artists you keep skipping are penalised
 *  - sanity: very short/long items and mixes, covers, sped-up edits are dropped
 * Then picks greedily for variety: never the same artist twice in a row, at most two per batch,
 * and every few songs one of your liked songs from a related artist is slipped in.
 */
object Radio {

    data class Taste(
        val likedIds: Set<String>,
        /** Artist (normalised) → affinity 0..1 from likes and plays. */
        val artistAffinity: Map<String, Double>,
        /** Recently played ids, newest first. */
        val recentIds: List<String>,
        val skippedIds: Set<String>,
        /** Artist (normalised) → number of skips. */
        val skippedArtists: Map<String, Int>,
    )

    fun taste(liked: List<Song>, recent: List<Song>, skipped: List<Song>): Taste {
        val weights = HashMap<String, Double>()
        liked.forEach { s -> artistsOf(s).forEach { weights.merge(it, 2.0, Double::plus) } }
        recent.forEachIndexed { i, s -> artistsOf(s).forEach { weights.merge(it, 1.0 / (1 + i * 0.05), Double::plus) } }
        val max = weights.values.maxOrNull() ?: 1.0
        return Taste(
            likedIds = liked.mapTo(HashSet()) { it.id },
            artistAffinity = weights.mapValues { it.value / max },
            recentIds = recent.map { it.id },
            skippedIds = skipped.mapTo(HashSet()) { it.id },
            skippedArtists = skipped.flatMap { artistsOf(it) }.groupingBy { it }.eachCount(),
        )
    }

    /**
     * @param pools related-song lists, one per seed song, most recent seed first
     * @param queueIds songs already queued (never picked again)
     * @param likedSongs your liked songs, for the occasional familiar pick
     */
    fun pick(
        pools: List<List<Song>>,
        queueIds: Set<String>,
        taste: Taste,
        likedSongs: List<Song>,
        count: Int = 10,
        random: Random = Random.Default,
    ): List<Song> {
        val seedWeights = listOf(1.0, 0.6, 0.4, 0.3)
        val agreement = HashMap<String, Double>()
        val byId = LinkedHashMap<String, Song>()
        pools.forEachIndexed { p, pool ->
            pool.forEachIndexed { rank, song ->
                byId.putIfAbsent(song.id, song)
                agreement.merge(song.id, seedWeights.getOrElse(p) { 0.2 } / (1 + rank * 0.15), Double::plus)
            }
        }
        val veryRecent = taste.recentIds.take(15).toSet()
        val recent = taste.recentIds.toSet()

        val scored = byId.values
            .filter { it.id !in queueIds && it.id !in veryRecent && it.id !in taste.skippedIds && isMusic(it) }
            .map { song ->
                val artists = artistsOf(song)
                var score = agreement.getValue(song.id)
                score += 0.6 * (artists.maxOfOrNull { taste.artistAffinity[it] ?: 0.0 } ?: 0.0)
                if (song.id in taste.likedIds) score += 0.3
                if (song.id in recent) score -= 0.8
                artists.forEach { a -> score -= 0.25 * (taste.skippedArtists[a] ?: 0).coerceAtMost(3) }
                score += random.nextDouble(0.0, 0.15)  // a little variety between sessions
                song to score
            }
            .sortedByDescending { it.second }
            .map { it.first }

        // Familiar picks: liked songs by artists that appear among the candidates, not played lately.
        val candidateArtists = byId.values.flatMap { artistsOf(it) }.toSet()
        val familiar = likedSongs
            .filter { it.id !in queueIds && it.id !in recent && it.id !in taste.skippedIds && artistsOf(it).any { a -> a in candidateArtists } }
            .shuffled(random)
            .toMutableList()

        val out = mutableListOf<Song>()
        val perArtist = HashMap<String, Int>()
        val remaining = scored.toMutableList()
        while (out.size < count && (remaining.isNotEmpty() || familiar.isNotEmpty())) {
            val wantFamiliar = (out.size + 1) % 5 == 0 && familiar.isNotEmpty()
            val source = if (wantFamiliar || remaining.isEmpty()) familiar else remaining
            val lastArtists = out.lastOrNull()?.let { artistsOf(it) }.orEmpty()
            val index = source.indexOfFirst { s ->
                s.id !in out.map { it.id } &&
                    artistsOf(s).none { it in lastArtists } &&
                    artistsOf(s).all { (perArtist[it] ?: 0) < 2 }
            }
            if (index < 0) {
                if (source === familiar) familiar.clear() else break
                continue
            }
            val song = source.removeAt(index)
            out += song
            artistsOf(song).forEach { perArtist.merge(it, 1, Int::plus) }
        }
        return out
    }

    private val junk = Regex(
        "\\b(full album|mix|mixtape|compilation|playlist|1 hour|10 hours|hour loop|cover|karaoke|sped up|slowed|reverb|nightcore|8d audio|reaction|tutorial|lyrics? video)\\b",
        RegexOption.IGNORE_CASE,
    )

    /** Drops things that aren't a normal song: too short/long, mixes, covers, edits. */
    fun isMusic(song: Song): Boolean {
        if (song.durationSec in 1..59 || song.durationSec > 15 * 60) return false
        return !junk.containsMatchIn(song.title)
    }

    /** Lower-cased individual artist names: "A, B & C feat. D" → [a, b, c, d]. */
    fun artistsOf(song: Song): List<String> =
        song.artist.lowercase()
            .split(Regex(",|&| x | feat\\.? | ft\\.? | and |/"))
            .map { it.trim().removeSuffix(" - topic") }
            .filter { it.isNotEmpty() && it != "unknown artist" }
            .distinct()
}
