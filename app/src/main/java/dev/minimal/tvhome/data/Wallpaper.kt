package dev.minimal.tvhome.data

import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

enum class BackgroundMode {
    Solid,
    Unsplash;

    companion object {
        fun parse(raw: String?): BackgroundMode = entries.firstOrNull { it.name == raw } ?: Solid
    }
}

enum class WallpaperTopic(val label: String, val query: String) {
    Nature("Nature", "nature"),
    Landscapes("Landscapes", "landscape"),
    Architecture("Architecture", "architecture"),
    Abstract("Abstract", "abstract minimal"),
    NightSky("Night sky", "night sky stars");

    companion object {
        fun parse(raw: String?): WallpaperTopic = entries.firstOrNull { it.name == raw } ?: Nature
    }
}

enum class WallpaperInterval(val label: String, val millis: Long) {
    Hourly("Every hour", TimeUnit.HOURS.toMillis(1)),
    Daily("Every day", TimeUnit.DAYS.toMillis(1));

    companion object {
        fun parse(raw: String?): WallpaperInterval = entries.firstOrNull { it.name == raw } ?: Hourly
    }
}

data class WallpaperSettings(
    val mode: BackgroundMode = BackgroundMode.Solid,
    val topic: WallpaperTopic = WallpaperTopic.Nature,
    val interval: WallpaperInterval = WallpaperInterval.Hourly,
)

/** The photo currently saved on the device. */
data class Wallpaper(
    val id: String,
    val file: String,
    val photographer: String,
    val fetchedAt: Long,
    val topic: String,
)

/** The parts of an Unsplash "random photo" response we use. */
data class UnsplashPhoto(
    val id: String,
    val rawUrl: String,
    val photographer: String,
    val downloadLocation: String?,
)

/** Pure rules for the Unsplash background, kept free of Android types so they are unit-testable. */
object WallpaperRules {
    const val API = "https://api.unsplash.com"

    fun randomPhotoUrl(query: String): String =
        "$API/photos/random?orientation=landscape&content_filter=high&query=" +
            URLEncoder.encode(query, "UTF-8").replace("+", "%20")

    /** Unsplash serves any size from the raw URL via query parameters (imgix). */
    fun sizedImageUrl(rawUrl: String, widthPx: Int): String {
        val separator = if ('?' in rawUrl) '&' else '?'
        return "$rawUrl${separator}w=$widthPx&fm=jpg&q=80&fit=max"
    }

    /** A new photo is due when there is none, the topic changed, or the interval has passed. */
    fun isStale(current: Wallpaper?, settings: WallpaperSettings, now: Long): Boolean {
        if (current == null) return true
        if (current.topic != settings.topic.name) return true
        if (now < current.fetchedAt) return true // clock moved backwards
        return now - current.fetchedAt >= settings.interval.millis
    }

    fun parsePhoto(json: String): UnsplashPhoto {
        val o = JSONObject(json)
        val user = o.optJSONObject("user")
        return UnsplashPhoto(
            id = o.getString("id"),
            rawUrl = o.getJSONObject("urls").getString("raw"),
            photographer = user?.optString("name")?.takeIf { it.isNotBlank() } ?: "Unknown",
            downloadLocation = o.optJSONObject("links")?.optString("download_location")?.takeIf { it.isNotBlank() },
        )
    }

    fun encode(w: Wallpaper): String = JSONObject()
        .put("id", w.id)
        .put("file", w.file)
        .put("photographer", w.photographer)
        .put("fetchedAt", w.fetchedAt)
        .put("topic", w.topic)
        .toString()

    fun decode(json: String): Wallpaper {
        val o = JSONObject(json)
        return Wallpaper(
            id = o.getString("id"),
            file = o.getString("file"),
            photographer = o.optString("photographer", "Unknown"),
            fetchedAt = o.getLong("fetchedAt"),
            topic = o.optString("topic", WallpaperTopic.Nature.name),
        )
    }
}
