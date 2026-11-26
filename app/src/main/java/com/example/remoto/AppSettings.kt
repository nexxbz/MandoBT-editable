package com.example.remoto

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Ajustes de la app (se guardan solos). */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("config", Context.MODE_PRIVATE)

    var touchpadDefault by mutableStateOf(prefs.getBoolean("touchpad", false))
        private set
    var volumeKeys by mutableStateOf(prefs.getBoolean("volkeys", false))
        private set
    var pointerSpeed by mutableFloatStateOf(prefs.getFloat("ptr", 1f))
        private set
    var scrollSpeed by mutableFloatStateOf(prefs.getFloat("scr", 1f))
        private set
    var invertScroll by mutableStateOf(prefs.getBoolean("inv", false))
        private set
    var clearAfterSend by mutableStateOf(prefs.getBoolean("clear", true))
        private set

    fun updateTouchpadDefault(v: Boolean) { touchpadDefault = v; prefs.edit().putBoolean("touchpad", v).apply() }
    fun updateVolumeKeys(v: Boolean) { volumeKeys = v; prefs.edit().putBoolean("volkeys", v).apply() }
    fun updatePointerSpeed(v: Float) { pointerSpeed = v; prefs.edit().putFloat("ptr", v).apply() }
    fun updateScrollSpeed(v: Float) { scrollSpeed = v; prefs.edit().putFloat("scr", v).apply() }
    fun updateInvertScroll(v: Boolean) { invertScroll = v; prefs.edit().putBoolean("inv", v).apply() }
    fun updateClearAfterSend(v: Boolean) { clearAfterSend = v; prefs.edit().putBoolean("clear", v).apply() }
}
