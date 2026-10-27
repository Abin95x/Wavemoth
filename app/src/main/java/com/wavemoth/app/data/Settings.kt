package com.wavemoth.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** App preferences, saved on the device. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _stopOnSwipe = MutableStateFlow(prefs.getBoolean(KEY_STOP_ON_SWIPE, true))

    /** Stop playback when the app is swiped away from the recent-apps screen. On by default. */
    val stopOnSwipe: StateFlow<Boolean> = _stopOnSwipe.asStateFlow()

    fun setStopOnSwipe(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_STOP_ON_SWIPE, enabled).apply()
        _stopOnSwipe.value = enabled
    }

    private val _libraryGrid = MutableStateFlow(prefs.getBoolean(KEY_LIBRARY_GRID, false))

    /** Show the Library as a grid of covers instead of a list. Off by default. */
    val libraryGrid: StateFlow<Boolean> = _libraryGrid.asStateFlow()

    fun setLibraryGrid(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LIBRARY_GRID, enabled).apply()
        _libraryGrid.value = enabled
    }

    private val _likedSort = MutableStateFlow(SongSort.fromName(prefs.getString(KEY_LIKED_SORT, null)))

    /** How Liked Songs is sorted. Recently added by default. */
    val likedSort: StateFlow<SongSort> = _likedSort.asStateFlow()

    fun setLikedSort(sort: SongSort) {
        prefs.edit().putString(KEY_LIKED_SORT, sort.name).apply()
        _likedSort.value = sort
    }

    private companion object {
        const val KEY_STOP_ON_SWIPE = "stop_on_swipe"
        const val KEY_LIBRARY_GRID = "library_grid"
        const val KEY_LIKED_SORT = "liked_sort"
    }
}
