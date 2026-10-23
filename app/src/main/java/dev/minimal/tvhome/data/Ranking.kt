package dev.minimal.tvhome.data

/** Pure sorting and filtering rules for the home screen rows. */
object Ranking {
    const val WATCH_NEXT_LIMIT = 20

    /** At most this many entries per app, so one binge doesn't push every other app off the row. */
    const val WATCH_NEXT_PER_APP = 3
    const val RECENT_LIMIT = 10

    /** Apps left out of Continue watching. YouTube's played-item art was often another video's. */
    val HIDDEN_FROM_WATCH_NEXT = setOf("com.google.android.youtube.tv")

    const val PLAYED_LIMIT = 30
    const val PLAYED_MAX_AGE_MILLIS = 30L * 24 * 60 * 60 * 1000

    /** Shorter than this is a clip, a sound or a live stream with no length; not worth resuming. */
    const val PLAYED_MIN_DURATION_MILLIS = 60_000L

    /** Past this point a video counts as finished and leaves Up Next. */
    const val PLAYED_FINISHED_FRACTION = 0.95f

    fun progress(positionMillis: Long?, durationMillis: Long?): Float? {
        if (positionMillis == null || durationMillis == null) return null
        if (positionMillis <= 0 || durationMillis <= 0) return null
        return (positionMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
    }

    /** "S2 E5 · The Episode" style subtitle, or null when there is nothing to show. */
    fun subtitle(episodeTitle: String?, season: String?, episode: String?): String? {
        val numbering = buildString {
            if (!season.isNullOrBlank()) append("S").append(season.trim())
            if (!episode.isNullOrBlank()) {
                if (isNotEmpty()) append(" ")
                append("E").append(episode.trim())
            }
        }
        return listOf(numbering, episodeTitle?.trim().orEmpty())
            .filter { it.isNotEmpty() }
            .joinToString(" · ")
            .ifEmpty { null }
    }

    /**
     * Newest first. Drops rows the user dismissed (not browsable), rows without a title, and rows
     * from [HIDDEN_FROM_WATCH_NEXT] apps.
     * Keeps one entry per show per app, so a series doesn't fill the row with episodes, and at
     * most [perApp] entries per app, so the apps you use most don't crowd out the rest.
     */
    fun rankWatchNext(
        items: List<WatchNextItem>,
        limit: Int = WATCH_NEXT_LIMIT,
        perApp: Int = WATCH_NEXT_PER_APP,
    ): List<WatchNextItem> {
        val perAppCount = HashMap<String, Int>()
        return items.asSequence()
            .filter { it.browsable && it.title.isNotBlank() && it.packageName !in HIDDEN_FROM_WATCH_NEXT }
            .sortedByDescending { it.lastEngagementMillis }
            .distinctBy { it.packageName to it.title.trim().lowercase() }
            .filter { item ->
                val n = perAppCount.getOrDefault(item.packageName, 0)
                perAppCount[item.packageName] = n + 1
                n < perApp
            }
            .take(limit)
            .toList()
    }

    /** Collapses usage entries to the latest timestamp per package. */
    fun mergeLastUsed(entries: List<Pair<String, Long>>): Map<String, Long> {
        val result = HashMap<String, Long>()
        for ((pkg, time) in entries) {
            if (time > (result[pkg] ?: Long.MIN_VALUE)) result[pkg] = time
        }
        return result
    }

    fun rankRecent(
        lastUsed: Map<String, Long>,
        launchable: Set<String>,
        excluded: Set<String>,
        limit: Int = RECENT_LIMIT,
    ): List<String> =
        lastUsed.entries.asSequence()
            .filter { (pkg, time) -> time > 0 && pkg in launchable && pkg !in excluded }
            .sortedByDescending { it.value }
            .map { it.key }
            .take(limit)
            .toList()

    fun togglePin(favorites: List<String>, pkg: String): List<String> =
        if (pkg in favorites) favorites - pkg else favorites + pkg

    /**
     * Moves [pkg] one place among the favorites the user can see. Pinned apps that are no longer
     * installed stay in the list (so a reinstall brings the pin back) but are stepped over, so a
     * move always changes the visible order.
     */
    fun move(favorites: List<String>, pkg: String, delta: Int, present: Set<String>? = null): List<String> {
        val visible = if (present == null) favorites else favorites.filter { it in present }
        val from = visible.indexOf(pkg)
        if (from < 0) return favorites
        val to = (from + delta).coerceIn(0, visible.lastIndex)
        if (from == to) return favorites
        val neighbour = visible[to]
        return favorites.toMutableList().apply {
            remove(pkg)
            add(indexOf(neighbour) + (if (delta > 0) 1 else 0), pkg)
        }
    }

    fun encodeList(packages: List<String>): String = packages.joinToString("\n")

    fun decodeList(raw: String?): List<String> =
        raw.orEmpty().split("\n").map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    /** Grid order for the Top Shelf layout: favorites first in their order, then the rest as given. */
    fun gridOrder(favorites: List<String>, alphabetical: List<String>): List<String> {
        val available = alphabetical.toSet()
        val pinned = favorites.filter { it in available }.distinct()
        val pinnedSet = pinned.toSet()
        return pinned + alphabetical.filter { it !in pinnedSet }
    }

    /** Items for the focused app if it has any, otherwise all items. */
    fun shelfContent(watchNext: List<WatchNextItem>, focusedPackage: String?): ShelfContent {
        val forApp = if (focusedPackage == null) emptyList() else watchNext.filter { it.packageName == focusedPackage }
        return if (forApp.isNotEmpty()) ShelfContent(forApp, focusedPackage) else ShelfContent(watchNext, null)
    }

    /** Whether a playback is worth keeping: long enough to resume and not finished. */
    fun isResumable(item: PlayedItem): Boolean =
        item.title.isNotBlank() &&
            item.durationMillis >= PLAYED_MIN_DURATION_MILLIS &&
            item.positionMillis < item.durationMillis * PLAYED_FINISHED_FRACTION

    /**
     * Adds or replaces [item] (same app and title) at the front. Finished items are removed rather
     * than kept, and the list is capped at [limit] newest entries.
     */
    fun upsertPlayed(history: List<PlayedItem>, item: PlayedItem, limit: Int = PLAYED_LIMIT): List<PlayedItem> {
        val rest = history.filterNot { it.packageName == item.packageName && it.title.equals(item.title, ignoreCase = true) }
        val withItem = if (isResumable(item)) listOf(item) + rest else rest
        return withItem.sortedByDescending { it.lastPlayedMillis }.take(limit)
    }

    /**
     * Up Next: Watch Next rows plus recorded playbacks from apps that publish no Watch Next rows
     * of their own, so an app's own list is never mixed with our guesses. Played items must come
     * from a launchable app and be newer than [PLAYED_MAX_AGE_MILLIS].
     */
    fun mergePlayed(
        watchNext: List<WatchNextItem>,
        played: List<PlayedItem>,
        launchable: Set<String>,
        nowMillis: Long,
        limit: Int = WATCH_NEXT_LIMIT,
        perApp: Int = WATCH_NEXT_PER_APP,
    ): List<WatchNextItem> {
        val publishers = watchNext.map { it.packageName }.toSet()
        val extra = played
            .filter { it.packageName !in publishers && it.packageName in launchable }
            .filter { nowMillis - it.lastPlayedMillis <= PLAYED_MAX_AGE_MILLIS && isResumable(it) }
            .map { it.toWatchNextItem() }
        return rankWatchNext(watchNext + extra, limit, perApp)
    }

    /** Played items have no deep link, so opening one opens its app. Negative ids keep keys apart from real rows. */
    fun PlayedItem.toWatchNextItem() = WatchNextItem(
        id = -1L - ((packageName + "\u0000" + title.lowercase()).hashCode().toLong() and 0x7fffffffL),
        packageName = packageName,
        title = title,
        subtitle = subtitle,
        imageUris = imageUris,
        progress = progress(positionMillis, durationMillis),
        lastEngagementMillis = lastPlayedMillis,
        intentUri = null,
        type = 0,
        browsable = true,
    )
}
