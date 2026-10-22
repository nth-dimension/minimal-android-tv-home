package dev.minimal.tvhome.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreensaverRulesTest {
    private fun photo(id: String, at: Long = 0) = Wallpaper(id, "/f/$id.jpg", "P", at, "Nature")
    private val min = 60_000L

    @Test fun swapsEveryFiveMinutes() = assertEquals(5 * min, ScreensaverRules.SWAP_MS)

    @Test fun fetchesEveryFifteenMinutes() = assertEquals(15 * min, ScreensaverRules.FETCH_EVERY_MS)

    @Test fun fetchesWhenNothingSaved() = assertTrue(ScreensaverRules.shouldFetch(null, 0, saved = 0))

    @Test fun fetchesWhenOnlyOneSaved() = assertTrue(ScreensaverRules.shouldFetch(10 * min, 12 * min, saved = 1))

    @Test fun cyclesSavedPhotosBetweenFetches() = assertFalse(ScreensaverRules.shouldFetch(10 * min, 20 * min, saved = 4))

    @Test fun fetchesNearTheFifteenMinuteMark() = assertTrue(ScreensaverRules.shouldFetch(10 * min, 24 * min + 31_000, saved = 4))

    @Test fun fetchesIfTheClockWentBack() = assertTrue(ScreensaverRules.shouldFetch(10 * min, 5 * min, saved = 4))

    @Test fun addPutsNewestFirstAndTrims() {
        val pool = (1..8).map { photo("p$it") }
        val (kept, dropped) = ScreensaverRules.add(pool, photo("new"))
        assertEquals(8, kept.size)
        assertEquals("new", kept.first().id)
        assertEquals(listOf("p8"), dropped.map { it.id })
    }

    @Test fun addDoesNotDuplicate() {
        val (kept, dropped) = ScreensaverRules.add(listOf(photo("a"), photo("b")), photo("b"))
        assertEquals(listOf("b", "a"), kept.map { it.id })
        assertTrue(dropped.isEmpty())
    }

    @Test fun nextCyclesAndWraps() {
        val pool = listOf(photo("a"), photo("b"), photo("c"))
        assertEquals("b", ScreensaverRules.next(pool, "a")?.id)
        assertEquals("a", ScreensaverRules.next(pool, "c")?.id)
        assertEquals("a", ScreensaverRules.next(pool, "gone")?.id)
        assertNull(ScreensaverRules.next(emptyList(), "a"))
    }
}
