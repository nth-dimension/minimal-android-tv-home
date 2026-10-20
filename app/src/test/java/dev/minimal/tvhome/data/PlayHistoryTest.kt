package dev.minimal.tvhome.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayHistoryTest {

    private val minute = 60_000L
    private val day = 24 * 60 * minute

    private fun played(
        title: String,
        at: Long,
        pkg: String = "in.startv.hotstar",
        pos: Long = 10 * minute,
        dur: Long = 45 * minute,
    ) = PlayedItem(pkg, title, null, emptyList(), pos, dur, at)

    private fun row(title: String, at: Long, pkg: String) = WatchNextItem(
        id = at, packageName = pkg, title = title, subtitle = null, imageUris = emptyList(), progress = null,
        lastEngagementMillis = at, intentUri = null, type = 0, browsable = true,
    )

    @Test fun shortClipsLiveStreamsAndFinishedVideosAreNotResumable() {
        assertTrue(Ranking.isResumable(played("Show", 0)))
        assertFalse(Ranking.isResumable(played("Clip", 0, dur = 30_000)))
        assertFalse(Ranking.isResumable(played("Live match", 0, dur = 0)))
        assertFalse(Ranking.isResumable(played("Done", 0, pos = 44 * minute)))
    }

    @Test fun upsertReplacesSameTitleAndKeepsNewestFirst() {
        val history = listOf(played("A", 1), played("B", 2))
        val next = Ranking.upsertPlayed(history, played("a", 3, pos = 20 * minute))
        assertEquals(listOf("a", "B"), next.map { it.title })
        assertEquals(20 * minute, next.first().positionMillis)
    }

    @Test fun finishingAVideoRemovesIt() {
        val history = listOf(played("A", 1), played("B", 2))
        val next = Ranking.upsertPlayed(history, played("A", 3, pos = 45 * minute))
        assertEquals(listOf("B"), next.map { it.title })
    }

    @Test fun upsertCapsTheList() {
        val history = (1..5L).map { played("T$it", it) }
        assertEquals(listOf("T6", "T5", "T4"), Ranking.upsertPlayed(history, played("T6", 6), limit = 3).map { it.title })
    }

    @Test fun mergeSkipsAppsWithTheirOwnRows() {
        val rows = listOf(row("Lupin", 10, "com.netflix.ninja"))
        val played = listOf(played("Lupin again", 20, pkg = "com.netflix.ninja"), played("Aspirants", 15))
        val merged = Ranking.mergePlayed(rows, played, setOf("com.netflix.ninja", "in.startv.hotstar"), nowMillis = 30)
        assertEquals(listOf("Aspirants", "Lupin"), merged.map { it.title })
    }

    @Test fun mergeDropsOldAndUnlaunchableEntries() {
        val now = 100 * day
        val played = listOf(
            played("Recent", now - day),
            played("Old", now - 31 * day),
            played("System", now - day, pkg = "com.sony.dtv.tvx"),
        )
        val merged = Ranking.mergePlayed(emptyList(), played, setOf("in.startv.hotstar"), now)
        assertEquals(listOf("Recent"), merged.map { it.title })
    }

    @Test fun playedItemsGetNegativeStableIdsAndProgress() {
        val item = with(Ranking) { played("Show", 5).toWatchNextItem() }
        assertTrue(item.id < 0)
        assertEquals(item.id, with(Ranking) { played("show", 9).toWatchNextItem() }.id)
        assertEquals(10f / 45f, item.progress!!, 0.0001f)
    }

    @Test fun jsonRoundTrip() {
        val items = listOf(
            PlayedItem("in.startv.hotstar", "Show", "S1 E2", listOf("https://img/x.jpg", "file:///a.jpg"), 1, 2 * minute, 3),
            PlayedItem("com.apple.atve.sony.appletv", "Film", null, emptyList(), 4, 5 * minute, 6),
        )
        assertEquals(items, PlayHistory.decode(PlayHistory.encode(items)))
    }
}
