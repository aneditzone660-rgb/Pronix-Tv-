package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.IptvRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class IptvViewModel(private val repository: IptvRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<String?>("all")
    val selectedCategoryId = _selectedCategoryId.asStateFlow()

    private val _activeChannel = MutableStateFlow<Channel?>(null)
    val activeChannel = _activeChannel.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    // Expose repository data
    val categories = repository.categories
    val banners = repository.banners
    val notices = repository.notices
    val appUpdate = repository.appUpdate
    val firebaseConnected = repository.firebaseConnected
    val isLoading = repository.isLoading

    val favorites = repository.favorites
    val recents = repository.recents

    // Combined & Filtered Channels
    val filteredChannels: StateFlow<List<Channel>> = combine(
        repository.channels,
        _searchQuery,
        _selectedCategoryId
    ) { channels, query, categoryId ->
        channels.filter { channel ->
            val matchesQuery = channel.name.contains(query, ignoreCase = true) ||
                    channel.description.contains(query, ignoreCase = true)
            val matchesCategory = categoryId == "all" || categoryId == null || channel.category == categoryId
            matchesQuery && matchesCategory
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val featuredChannels: StateFlow<List<Channel>> = repository.channels
        .map { list -> list.filter { it.isFeatured } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(categoryId: String?) {
        _selectedCategoryId.value = categoryId
    }

    fun setActiveChannel(channel: Channel?) {
        _activeChannel.value = channel
        if (channel != null) {
            viewModelScope.launch {
                repository.recordWatchHistory(channel)
            }
        }
    }

    fun toggleFavorite(channel: Channel, isFav: Boolean) {
        viewModelScope.launch {
            if (isFav) {
                repository.removeFavorite(channel.id)
            } else {
                repository.addFavorite(channel)
            }
        }
    }

    fun removeRecent(channelId: String) {
        viewModelScope.launch {
            repository.removeRecent(channelId)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.loadData()
            _isRefreshing.value = false
        }
    }

    class Factory(private val repository: IptvRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(IptvViewModel::class.java)) {
                return IptvViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
