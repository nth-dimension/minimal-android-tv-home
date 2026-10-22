package dev.minimal.tvhome.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.homeDataStore by preferencesDataStore(name = "home")

/** On-device preferences: pinned apps in order, and the chosen home style. */
class HomePrefs(private val context: Context) {
    private val favoritesKey = stringPreferencesKey("favorites")
    private val styleKey = stringPreferencesKey("home_style")
    private val backgroundKey = stringPreferencesKey("background_mode")
    private val topicsKey = stringPreferencesKey("wallpaper_topics")
    private val legacyTopicKey = stringPreferencesKey("wallpaper_topic")
    private val intervalKey = stringPreferencesKey("wallpaper_interval")
    private val askedTvListingsKey = booleanPreferencesKey("asked_tv_listings")

    // A damaged preferences file must not take the home screen down: read defaults instead.
    private val data: Flow<Preferences> = context.homeDataStore.data.catch { e ->
        if (e !is IOException) throw e
        Log.w(TAG, "Preferences unreadable, using defaults", e)
        emit(emptyPreferences())
    }

    val favorites: Flow<List<String>> =
        data.map { Ranking.decodeList(it[favoritesKey]) }.distinctUntilChanged()

    val style: Flow<HomeStyle> =
        data.map { HomeStyle.parse(it[styleKey]) }.distinctUntilChanged()

    val wallpaperSettings: Flow<WallpaperSettings> =
        data.map {
            WallpaperSettings(
                mode = BackgroundMode.parse(it[backgroundKey]),
                topics = WallpaperTopic.parseSet(it[topicsKey] ?: it[legacyTopicKey]),
                interval = WallpaperInterval.parse(it[intervalKey]),
            )
        }.distinctUntilChanged()

    val askedTvListings: Flow<Boolean> = data.map { it[askedTvListingsKey] ?: false }

    suspend fun markTvListingsAsked() {
        edit { it[askedTvListingsKey] = true }
    }

    suspend fun setWallpaperSettings(settings: WallpaperSettings) {
        edit {
            it[backgroundKey] = settings.mode.name
            it[topicsKey] = WallpaperTopic.encodeSet(settings.topics)
            it[intervalKey] = settings.interval.name
        }
    }

    suspend fun updateFavorites(transform: (List<String>) -> List<String>) {
        edit { prefs ->
            prefs[favoritesKey] = Ranking.encodeList(transform(Ranking.decodeList(prefs[favoritesKey])))
        }
    }

    suspend fun setStyle(style: HomeStyle) {
        edit { it[styleKey] = style.name }
    }

    /** A write that fails (disk full, damaged file) is logged; the screen keeps its current values. */
    private suspend fun edit(transform: suspend (MutablePreferences) -> Unit) {
        try {
            context.homeDataStore.edit(transform)
        } catch (e: IOException) {
            Log.w(TAG, "Could not save preferences", e)
        }
    }

    private companion object {
        const val TAG = "MinimalHome"
    }
}
