package dev.minimal.tvhome.data

import java.net.URLEncoder
import java.text.Normalizer

/** Pure rules for the Search screen: matching what's on the TV, and building app search links. */
object SearchRules {

    /**
     * Apps whose search opens from a link. Others are asked with ACTION_SEARCH, which only
     * some apps handle. Tried on a Sony Google TV (2026-10-20): YouTube and Prime Video show results
     * for the words, Prime after its profile picker. JioHotstar opens its search page but leaves the
     * box empty. Netflix and Apple TV accept the link but block screenshots, so their landing page
     * is unconfirmed. Try a new one on a TV first.
     *
     * Fire TV builds, tried on a Fire TV Stick 4K Max (2026-10-22): Prime Video (com.amazon.firebat)
     * shows results, even when it is already open. YouTube (com.amazon.firetv.youtube) only ever
     * opens its home page, whatever the link, so it has none: a tile that opens YouTube without
     * searching would mislead.
     */
    private val LINKS = mapOf(
        "com.google.android.youtube.tv" to "https://www.youtube.com/results?search_query=%s",
        "com.netflix.ninja" to "nflx://www.netflix.com/search?q=%s",
        "com.amazon.amazonvideo.livingroom" to "https://app.primevideo.com/search?phrase=%s",
        "com.apple.atve.sony.appletv" to "https://tv.apple.com/search?term=%s",
        "com.apple.atve.androidtv.appletv" to "https://tv.apple.com/search?term=%s",
        "in.startv.hotstar" to "https://www.hotstar.com/in/explore?search_query=%s",
        // Fire TV builds. A link the app refuses falls back to opening the app.
        "com.amazon.firebat" to "https://app.primevideo.com/search?phrase=%s",
        "com.apple.atve.amazon.appletv" to "https://tv.apple.com/search?term=%s",
    )

    /** Apps that answer ACTION_SEARCH but don't search titles or videos. */
    val NOT_VIDEO = setOf("com.android.vending")

    fun hasLink(packageName: String): Boolean = packageName in LINKS

    fun link(packageName: String, query: String): String? {
        val template = LINKS[packageName] ?: return null
        // URLEncoder writes spaces as "+", which not every app decodes in a path or custom scheme.
        return template.format(URLEncoder.encode(query.trim(), "UTF-8").replace("+", "%20"))
    }

    /** Lowercase, accents removed, so "amelie" finds "Amélie". */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()

    private fun words(text: String): List<String> = normalize(text).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    /** Every word typed must start a word in one of the fields: "lon ro" finds "The Long Road". */
    fun matches(query: String, fields: List<String?>): Boolean {
        val wanted = words(query)
        if (wanted.isEmpty()) return false
        val have = fields.filterNotNull().flatMap(::words)
        return wanted.all { w -> have.any { it.startsWith(w) } }
    }

    fun <T> filter(query: String, items: List<T>, fields: (T) -> List<String?>): List<T> =
        if (query.isBlank()) emptyList() else items.filter { matches(query, fields(it)) }

    /** Apps you used most recently first, then the rest in the order given. */
    fun orderTargets(packages: List<String>, recent: List<String>): List<String> {
        val rank = recent.withIndex().associate { (i, p) -> p to i }
        return packages.withIndex()
            .sortedWith(compareBy({ rank[it.value] ?: Int.MAX_VALUE }, { it.index }))
            .map { it.value }
    }
}
