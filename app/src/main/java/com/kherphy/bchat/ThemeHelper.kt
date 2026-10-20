package com.kherphy.bchat

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeHelper {

    const val LIGHT = "light"
    const val DARK = "dark"
    const val SYSTEM = "system"

    fun apply(context: Context) {
        AppCompatDelegate.setDefaultNightMode(modeFor(Prefs.getTheme(context)))
    }

    private fun modeFor(value: String): Int = when (value) {
        LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        DARK -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    fun label(value: String): String = when (value) {
        LIGHT -> "Light"
        DARK -> "Dark"
        else -> "System default"
    }
}