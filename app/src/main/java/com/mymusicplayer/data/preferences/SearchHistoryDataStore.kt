package com.mymusicplayer.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.searchHistoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "search_history")

class SearchHistoryDataStore(private val context: Context) {

    companion object {
        val SEARCH_HISTORY = stringPreferencesKey("search_history")
        const val MAX_HISTORY_SIZE = 20
    }

    val searchHistory: Flow<List<String>> = context.searchHistoryDataStore.data.map { prefs ->
        prefs[SEARCH_HISTORY]?.split("|||")?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun addQuery(query: String) {
        if (query.isBlank()) return
        context.searchHistoryDataStore.edit { prefs ->
            val current = prefs[SEARCH_HISTORY]?.split("|||")?.filter { it.isNotBlank() }?.toMutableList() ?: mutableListOf()
            current.remove(query)
            current.add(0, query)
            val trimmed = current.take(MAX_HISTORY_SIZE)
            prefs[SEARCH_HISTORY] = trimmed.joinToString("|||")
        }
    }

    suspend fun clearHistory() {
        context.searchHistoryDataStore.edit { prefs ->
            prefs.remove(SEARCH_HISTORY)
        }
    }

    suspend fun removeQuery(query: String) {
        context.searchHistoryDataStore.edit { prefs ->
            val current = prefs[SEARCH_HISTORY]?.split("|||")?.filter { it.isNotBlank() }?.toMutableList() ?: mutableListOf()
            current.remove(query)
            prefs[SEARCH_HISTORY] = current.joinToString("|||")
        }
    }
}
