package dev.minimal.tvhome.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperRulesTest {

    private val hour = 60 * 60 * 1000L
    private fun saved(topic: WallpaperTopic = WallpaperTopic.Nature, at: Long = 1_000_000L) =
        Wallpaper("abc", "/data/wallpaper/abc.jpg", "Ansel", at, topic.name)

    @Test fun randomPhotoUrlAsksForLandscapeAndEncodesQuery() {
        assertEquals(
            "https://api.unsplash.com/photos/random?orientation=landscape&content_filter=high&query=night%20sky%20stars",
            WallpaperRules.randomPhotoUrl("night sky stars"),
        )
    }

    @Test fun sizedImageUrlAppendsToExistingQuery() {
        assertEquals(
            "https://images.unsplash.com/photo-1?ixid=x&w=1920&fm=jpg&q=80&fit=max",
            WallpaperRules.sizedImageUrl("https://images.unsplash.com/photo-1?ixid=x", 1920),
        )
        assertEquals(
            "https://images.unsplash.com/photo-1?w=1280&fm=jpg&q=80&fit=max",
            WallpaperRules.sizedImageUrl("https://images.unsplash.com/photo-1", 1280),
        )
    }

    @Test fun staleWhenMissingOrTopicChanged() {
        val settings = WallpaperSettings(BackgroundMode.Unsplash, setOf(WallpaperTopic.Nature), WallpaperInterval.Hourly)
        assertTrue(WallpaperRules.isStale(null, settings, 0))
        assertTrue(WallpaperRules.isStale(saved(WallpaperTopic.Architecture), settings, 1_000_001L))
    }

    @Test fun notStaleWhileItsTopicIsStillChosen() {
        val both = WallpaperSettings(BackgroundMode.Unsplash, setOf(WallpaperTopic.Nature, WallpaperTopic.Architecture), WallpaperInterval.Hourly)
        assertFalse(WallpaperRules.isStale(saved(WallpaperTopic.Architecture), both, 1_000_001L))
    }

    @Test fun topicSetsRoundTripAndNeverEmpty() {
        val set = setOf(WallpaperTopic.Ocean, WallpaperTopic.Space)
        assertEquals(set, WallpaperTopic.parseSet(WallpaperTopic.encodeSet(set)))
        assertEquals(setOf(WallpaperTopic.Nature), WallpaperTopic.parseSet(""))
        assertEquals(setOf(WallpaperTopic.Nature), WallpaperTopic.parseSet("Bogus"))
        assertEquals(setOf(WallpaperTopic.Mountains), WallpaperTopic.parseSet("Mountains")) // old single-topic value
    }

    @Test fun lastTopicCannotBeRemoved() {
        assertEquals(setOf(WallpaperTopic.Nature), setOf(WallpaperTopic.Nature).toggled(WallpaperTopic.Nature))
        assertEquals(setOf(WallpaperTopic.Nature, WallpaperTopic.Ocean), setOf(WallpaperTopic.Nature).toggled(WallpaperTopic.Ocean))
        assertEquals(setOf(WallpaperTopic.Ocean), setOf(WallpaperTopic.Nature, WallpaperTopic.Ocean).toggled(WallpaperTopic.Nature))
    }

    @Test fun staleOnlyAfterIntervalPasses() {
        val hourly = WallpaperSettings(BackgroundMode.Unsplash, setOf(WallpaperTopic.Nature), WallpaperInterval.Hourly)
        val daily = hourly.copy(interval = WallpaperInterval.Daily)
        val w = saved(at = 10 * hour)
        assertFalse(WallpaperRules.isStale(w, hourly, 10 * hour + hour - 1))
        assertTrue(WallpaperRules.isStale(w, hourly, 11 * hour))
        assertFalse(WallpaperRules.isStale(w, daily, 11 * hour))
        assertTrue(WallpaperRules.isStale(w, daily, 34 * hour))
    }

    @Test fun staleWhenClockMovedBackwards() {
        val settings = WallpaperSettings(BackgroundMode.Unsplash)
        assertTrue(WallpaperRules.isStale(saved(at = 10 * hour), settings, 9 * hour))
    }

    @Test fun parsesUnsplashRandomPhotoResponse() {
        val json = """
            {
              "id": "Dwu85P9SOIk",
              "width": 2448, "height": 3264,
              "urls": {
                "raw": "https://images.unsplash.com/photo-1417325384643-aac51acc9e5d?ixid=M3w",
                "full": "https://images.unsplash.com/photo-1417325384643-aac51acc9e5d?q=85",
                "regular": "https://images.unsplash.com/photo-1417325384643-aac51acc9e5d?w=1080"
              },
              "links": {
                "self": "https://api.unsplash.com/photos/Dwu85P9SOIk",
                "download_location": "https://api.unsplash.com/photos/Dwu85P9SOIk/download?ixid=M3w"
              },
              "user": { "id": "QPxL2MGqfrw", "username": "exampleuser", "name": "Joe Example" }
            }
        """.trimIndent()
        val photo = WallpaperRules.parsePhoto(json)
        assertEquals("Dwu85P9SOIk", photo.id)
        assertEquals("https://images.unsplash.com/photo-1417325384643-aac51acc9e5d?ixid=M3w", photo.rawUrl)
        assertEquals("Joe Example", photo.photographer)
        assertEquals("https://api.unsplash.com/photos/Dwu85P9SOIk/download?ixid=M3w", photo.downloadLocation)
    }

    @Test fun parseToleratesMissingUserAndLinks() {
        val photo = WallpaperRules.parsePhoto("""{"id":"x","urls":{"raw":"https://i/x"}}""")
        assertEquals("Unknown", photo.photographer)
        assertNull(photo.downloadLocation)
    }

    @Test fun savedWallpaperRoundTrips() {
        val w = saved()
        assertEquals(w, WallpaperRules.decode(WallpaperRules.encode(w)))
    }

    @Test fun settingsParseDefaults() {
        assertEquals(BackgroundMode.Solid, BackgroundMode.parse(null))
        assertEquals(BackgroundMode.Unsplash, BackgroundMode.parse("Unsplash"))
        assertEquals(WallpaperTopic.Nature, WallpaperTopic.parse("gone"))
        assertEquals(WallpaperTopic.NightSky, WallpaperTopic.parse("NightSky"))
        assertEquals(WallpaperInterval.Hourly, WallpaperInterval.parse(null))
        assertEquals(WallpaperInterval.Daily, WallpaperInterval.parse("Daily"))
    }
}
