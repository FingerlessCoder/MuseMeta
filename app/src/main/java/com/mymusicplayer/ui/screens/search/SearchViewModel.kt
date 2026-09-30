package com.mymusicplayer.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymusicplayer.data.preferences.SearchHistoryDataStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SearchViewModel(
    private val searchHistoryDataStore: SearchHistoryDataStore
) : ViewModel() {

    val searchHistory: StateFlow<List<String>> = searchHistoryDataStore.searchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addQuery(query: String) {
        viewModelScope.launch {
            searchHistoryDataStore.addQuery(query)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            searchHistoryDataStore.clearHistory()
        }
    }

    fun removeQuery(query: String) {
        viewModelScope.launch {
            searchHistoryDataStore.removeQuery(query)
        }
    }
}
