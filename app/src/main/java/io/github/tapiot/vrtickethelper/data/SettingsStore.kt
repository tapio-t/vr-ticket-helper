package io.github.tapiot.vrtickethelper.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "settings")

/** A recent route: station codes and names only. */
@Serializable
data class RecentRoute(val fromCode: String, val fromName: String, val toCode: String, val toName: String)

class SettingsStore(context: Context) {

    private val store = context.applicationContext.dataStore
    private val json = Json { ignoreUnknownKeys = true }

    private val historyOn = booleanPreferencesKey("history_enabled")
    private val recent = stringPreferencesKey("recent_routes")
    private val stationsBody = stringPreferencesKey("stations_body")
    private val stationsAt = longPreferencesKey("stations_fetched_at")

    val historyEnabled: Flow<Boolean> = store.data.map { it[historyOn] ?: true }

    val recentRoutes: Flow<List<RecentRoute>> = store.data.map { prefs ->
        prefs[recent]?.let { runCatching { json.decodeFromString<List<RecentRoute>>(it) }.getOrNull() } ?: emptyList()
    }

    suspend fun addRecent(route: RecentRoute) {
        if (!historyEnabled.first()) return
        store.edit { prefs ->
            val list = prefs[recent]?.let { runCatching { json.decodeFromString<List<RecentRoute>>(it) }.getOrNull() } ?: emptyList()
            val updated = (listOf(route) + list.filterNot { it.fromCode == route.fromCode && it.toCode == route.toCode }).take(MAX_RECENT)
            prefs[recent] = json.encodeToString(updated)
        }
    }

    suspend fun clearHistory() {
        store.edit { it.remove(recent) }
    }

    suspend fun setHistoryEnabled(enabled: Boolean) {
        store.edit {
            it[historyOn] = enabled
            if (!enabled) it.remove(recent)
        }
    }

    suspend fun cachedStations(): Pair<Long, String>? {
        val prefs = store.data.first()
        val body = prefs[stationsBody] ?: return null
        return (prefs[stationsAt] ?: 0L) to body
    }

    suspend fun saveStations(body: String, at: Long) {
        store.edit {
            it[stationsBody] = body
            it[stationsAt] = at
        }
    }

    companion object {
        const val MAX_RECENT = 6
    }
}
