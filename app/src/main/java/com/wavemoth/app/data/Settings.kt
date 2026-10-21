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

    private companion object {
        const val KEY_STOP_ON_SWIPE = "stop_on_swipe"
    }
}
