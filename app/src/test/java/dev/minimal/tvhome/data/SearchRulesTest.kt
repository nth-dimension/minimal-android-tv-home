package dev.minimal.tvhome.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRulesTest {

    @Test fun wordsMatchByPrefixInAnyOrderAndField() {
        assertTrue(SearchRules.matches("lon ro", listOf("The Long Road", null)))
        assertTrue(SearchRules.matches("road long", listOf("The Long Road")))
        assertTrue(SearchRules.matches("lupin s2", listOf("Lupin", "S2 E3 · Chapter 8")))
        assertFalse(SearchRules.matches("ong", listOf("The Long Road")))
        assertFalse(SearchRules.matches("long sea", listOf("The Long Road")))
    }

    @Test fun matchingIgnoresCaseAccentsAndPunctuation() {
        assertTrue(SearchRules.matches("AMELIE", listOf("Amélie")))
        assertTrue(SearchRules.matches("spider-man", listOf("Spider-Man: Far From Home")))
    }

    @Test fun blankQueryMatchesNothing() {
        assertFalse(SearchRules.matches("  ", listOf("Anything")))
        assertEquals(emptyList<String>(), SearchRules.filter(" ", listOf("a", "b")) { listOf(it) })
    }

    @Test fun linksEncodeTheQuery() {
        assertEquals(
            "https://www.youtube.com/results?search_query=lo-fi%20beats%20%26%20rain",
            SearchRules.link("com.google.android.youtube.tv", " lo-fi beats & rain "),
        )
        assertEquals("nflx://www.netflix.com/search?q=Am%C3%A9lie", SearchRules.link("com.netflix.ninja", "Amélie"))
        assertNull(SearchRules.link("tv.airtel.xstream.tvapp", "x"))
    }

    @Test fun fireTvLinksAreTheOnesThatLandOnResults() {
        assertEquals("https://app.primevideo.com/search?phrase=the%20boys", SearchRules.link("com.amazon.firebat", "the boys"))
        // Fire TV's YouTube opens its home page whatever the link says.
        assertFalse(SearchRules.hasLink("com.amazon.firetv.youtube"))
    }

    @Test fun recentAppsComeFirst() {
        val apps = listOf("a", "b", "c", "d")
        assertEquals(listOf("c", "a", "b", "d"), SearchRules.orderTargets(apps, recent = listOf("c", "x", "a")))
    }
}
