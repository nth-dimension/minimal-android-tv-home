package dev.minimal.tvhome.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RankingTest {

    private fun item(
        id: Long,
        title: String,
        time: Long,
        pkg: String = "com.example.video",
        browsable: Boolean = true,
    ) = WatchNextItem(
        id = id, packageName = pkg, title = title, subtitle = null, imageUris = emptyList(), progress = null,
        lastEngagementMillis = time, intentUri = null, type = 0, browsable = browsable,
    )

    @Test fun progressIsFractionClampedToOne() {
        assertEquals(0.25f, Ranking.progress(250, 1000)!!, 0.0001f)
        assertEquals(1f, Ranking.progress(1500, 1000)!!, 0.0001f)
    }

    @Test fun progressIsNullWhenUnknownOrZero() {
        assertNull(Ranking.progress(null, 1000))
        assertNull(Ranking.progress(100, null))
        assertNull(Ranking.progress(0, 1000))
        assertNull(Ranking.progress(100, 0))
    }

    @Test fun subtitleCombinesNumberingAndTitle() {
        assertEquals("S2 E5 · Pilot", Ranking.subtitle("Pilot", "2", "5"))
        assertEquals("E5", Ranking.subtitle(null, null, "5"))
        assertEquals("Pilot", Ranking.subtitle("Pilot", " ", null))
        assertNull(Ranking.subtitle(null, null, null))
    }

    @Test fun watchNextIsNewestFirst() {
        val ranked = Ranking.rankWatchNext(listOf(item(1, "Old", 10), item(2, "New", 30), item(3, "Mid", 20)))
        assertEquals(listOf("New", "Mid", "Old"), ranked.map { it.title })
    }

    @Test fun watchNextDropsDismissedAndUntitledRows() {
        val ranked = Ranking.rankWatchNext(
            listOf(item(1, "Keep", 10), item(2, "Dismissed", 20, browsable = false), item(3, "  ", 30)),
        )
        assertEquals(listOf("Keep"), ranked.map { it.title })
    }

    @Test fun watchNextKeepsOneEntryPerShowPerApp() {
        val ranked = Ranking.rankWatchNext(
            listOf(
                item(1, "Show", 10),
                item(2, "show ", 30),
                item(3, "Show", 20, pkg = "com.other.app"),
            ),
        )
        assertEquals(listOf(2L, 3L), ranked.map { it.id })
    }

    @Test fun watchNextRespectsLimit() {
        val many = (1..50).map { item(it.toLong(), "Title $it", it.toLong()) }
        assertEquals(Ranking.WATCH_NEXT_LIMIT, Ranking.rankWatchNext(many).size)
    }

    @Test fun mergeLastUsedKeepsLatestPerPackage() {
        val merged = Ranking.mergeLastUsed(listOf("a" to 5L, "b" to 3L, "a" to 9L, "a" to 1L))
        assertEquals(mapOf("a" to 9L, "b" to 3L), merged)
    }

    @Test fun recentFiltersAndSorts() {
        val ranked = Ranking.rankRecent(
            lastUsed = mapOf("a" to 10L, "b" to 30L, "self" to 50L, "service" to 40L, "never" to 0L),
            launchable = setOf("a", "b", "self", "never"),
            excluded = setOf("self"),
        )
        assertEquals(listOf("b", "a"), ranked)
    }

    @Test fun recentRespectsLimit() {
        val usage = (1..30).associate { "p$it" to it.toLong() }
        assertEquals(listOf("p30", "p29", "p28"), Ranking.rankRecent(usage, usage.keys, emptySet(), limit = 3))
    }

    @Test fun togglePinAddsToEndAndRemoves() {
        assertEquals(listOf("a", "b"), Ranking.togglePin(listOf("a"), "b"))
        assertEquals(listOf("b"), Ranking.togglePin(listOf("a", "b"), "a"))
    }

    @Test fun moveShiftsWithinBounds() {
        val list = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), Ranking.move(list, "b", -1))
        assertEquals(listOf("a", "c", "b"), Ranking.move(list, "b", 1))
        assertEquals(list, Ranking.move(list, "a", -1))
        assertEquals(list, Ranking.move(list, "c", 1))
        assertEquals(list, Ranking.move(list, "missing", 1))
    }

    @Test fun listCodecRoundTripsAndCleans() {
        val list = listOf("com.a", "com.b")
        assertEquals(list, Ranking.decodeList(Ranking.encodeList(list)))
        assertEquals(listOf("com.a"), Ranking.decodeList("com.a\n\n com.a \n"))
        assertEquals(emptyList<String>(), Ranking.decodeList(null))
    }

    @Test fun gridOrderPutsFavoritesFirstAndSkipsUninstalled() {
        val order = Ranking.gridOrder(
            favorites = listOf("yt", "gone", "netflix"),
            alphabetical = listOf("music", "netflix", "settings", "yt"),
        )
        assertEquals(listOf("yt", "netflix", "music", "settings"), order)
    }

    @Test fun gridOrderWithoutFavoritesIsAlphabeticalInput() {
        assertEquals(listOf("a", "b"), Ranking.gridOrder(emptyList(), listOf("a", "b")))
    }

    @Test fun shelfShowsFocusedAppItemsWhenItHasAny() {
        val items = listOf(item(1, "A", 30, pkg = "yt"), item(2, "B", 20, pkg = "netflix"), item(3, "C", 10, pkg = "yt"))
        val shelf = Ranking.shelfContent(items, "yt")
        assertEquals("yt", shelf.fromPackage)
        assertEquals(listOf(1L, 3L), shelf.items.map { it.id })
    }

    @Test fun shelfFallsBackToUpNextForAppsWithoutItems() {
        val items = listOf(item(1, "A", 30, pkg = "yt"))
        assertEquals(ShelfContent(items, null), Ranking.shelfContent(items, "settings"))
        assertEquals(ShelfContent(items, null), Ranking.shelfContent(items, null))
    }

    @Test fun homeStyleParsesStoredNamesAndDefaultsToMinimal() {
        assertEquals(HomeStyle.Shelf, HomeStyle.parse("Shelf"))
        assertEquals(HomeStyle.Minimal, HomeStyle.parse(null))
        assertEquals(HomeStyle.Minimal, HomeStyle.parse("Unknown"))
    }

    @Test fun moveStepsOverFavoritesThatAreNotInstalled() {
        val favorites = listOf("a", "gone", "b", "c")
        val present = setOf("a", "b", "c")
        assertEquals(listOf("gone", "b", "a", "c"), Ranking.move(favorites, "a", 1, present))
        assertEquals(listOf("a", "gone", "c", "b"), Ranking.move(favorites, "c", -1, present))
        assertEquals(favorites, Ranking.move(favorites, "c", 1, present))
        assertEquals(favorites, Ranking.move(favorites, "gone", 1, present))
        assertEquals(listOf("b", "a", "c"), Ranking.move(listOf("a", "b", "c"), "a", 1))
    }
}
