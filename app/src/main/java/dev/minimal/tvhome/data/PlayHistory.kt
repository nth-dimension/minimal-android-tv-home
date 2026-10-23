package dev.minimal.tvhome.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.scale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * What apps played recently, recorded by [MediaListenerService] and shown in Up Next.
 * Kept in memory and in a small JSON file; the service and the home screen share one process.
 */
object PlayHistory {
    private const val FILE = "play_history.json"
    private const val ART_DIR = "play_art"
    private const val ART_WIDTH = 640

    private val state = MutableStateFlow<List<PlayedItem>>(emptyList())
    private var loaded = false

    /** Current entries. Empty until [load] has run on a background thread. */
    val items: StateFlow<List<PlayedItem>> = state.asStateFlow()

    /** Reads the saved history. Disk work: call off the main thread. */
    fun load(context: Context) = ensureLoaded(context)

    @Synchronized
    fun record(context: Context, item: PlayedItem) {
        ensureLoaded(context)
        val previous = state.value
        val next = Ranking.upsertPlayed(previous, item)
        if (next == previous) return
        state.value = next
        // Artwork only becomes unused when an entry drops out, so only scan the folder then.
        val kept = next.map { it.packageName to it.title.lowercase() }.toSet()
        save(context, next, pruneArt = previous.any { (it.packageName to it.title.lowercase()) !in kept })
    }

    /** Saves a scaled copy of the app's artwork and returns its file URI. Same title, same file. */
    fun saveArt(context: Context, packageName: String, title: String, art: Bitmap): String? = runCatching {
        val dir = File(context.filesDir, ART_DIR).apply { mkdirs() }
        val file = File(dir, artName(packageName, title))
        val scaled = if (art.width > ART_WIDTH) art.scale(ART_WIDTH, art.height * ART_WIDTH / art.width) else art
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        Uri.fromFile(file).toString()
    }.getOrNull()

    @Synchronized
    private fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val saved = runCatching { decode(File(context.filesDir, FILE).readText()) }.getOrDefault(emptyList())
        // Plays recorded before their app was hidden would never be shown; drop them and their artwork.
        val shown = saved.filter { it.packageName !in Ranking.HIDDEN_FROM_WATCH_NEXT }
        state.value = shown
        if (shown.size < saved.size) save(context, shown, pruneArt = true)
    }

    private fun save(context: Context, items: List<PlayedItem>, pruneArt: Boolean) {
        runCatching {
            val file = File(context.filesDir, FILE)
            val tmp = File(context.filesDir, "$FILE.tmp")
            tmp.writeText(encode(items))
            tmp.renameTo(file)
        }
        if (!pruneArt) return
        val keep = items.flatMap { it.imageUris }.mapNotNull { Uri.parse(it).lastPathSegment }.toSet()
        File(context.filesDir, ART_DIR).listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
    }

    private fun artName(packageName: String, title: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest("$packageName\u0000${title.lowercase()}".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(24) + ".jpg"
    }

    fun encode(items: List<PlayedItem>): String = JSONArray().apply {
        items.forEach { i ->
            put(
                JSONObject()
                    .put("pkg", i.packageName)
                    .put("title", i.title)
                    .put("subtitle", i.subtitle ?: JSONObject.NULL)
                    .put("images", JSONArray(i.imageUris))
                    .put("pos", i.positionMillis)
                    .put("dur", i.durationMillis)
                    .put("at", i.lastPlayedMillis),
            )
        }
    }.toString()

    fun decode(raw: String): List<PlayedItem> {
        val array = JSONArray(raw)
        return (0 until array.length()).mapNotNull { n ->
            val o = array.optJSONObject(n) ?: return@mapNotNull null
            val images = o.optJSONArray("images")
            PlayedItem(
                packageName = o.optString("pkg").ifEmpty { return@mapNotNull null },
                title = o.optString("title").ifEmpty { return@mapNotNull null },
                subtitle = if (o.isNull("subtitle")) null else o.optString("subtitle"),
                imageUris = if (images == null) emptyList() else (0 until images.length()).map { images.getString(it) },
                positionMillis = o.optLong("pos"),
                durationMillis = o.optLong("dur"),
                lastPlayedMillis = o.optLong("at"),
            )
        }
    }
}
