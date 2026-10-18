package com.kherphy.bchat

import android.content.Context
import android.os.Build

object Prefs {
    private const val KEY_ALERTS = "message_alerts"
    private const val KEY_PREVIEW = "message_preview"

    fun isAlerts(context: Context): Boolean = prefs(context).getBoolean(KEY_ALERTS, true)

    fun setAlerts(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ALERTS, value).apply()
    }

    fun isPreview(context: Context): Boolean = prefs(context).getBoolean(KEY_PREVIEW, true)

    fun setPreview(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_PREVIEW, value).apply()
    }
    private const val FILE = "bchat_prefs"
    private const val KEY_NAME = "username"
    private const val KEY_VISIBLE = "visible"
    private const val KEY_SAVE = "save_history"
    private const val KEY_ASK = "ask_before_connecting"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun hasName(context: Context): Boolean = prefs(context).contains(KEY_NAME)

    fun getName(context: Context): String =
        prefs(context).getString(KEY_NAME, null) ?: Build.MODEL

    fun setName(context: Context, name: String) {
        val clean = name.trim().take(30).ifBlank { Build.MODEL }
        prefs(context).edit().putString(KEY_NAME, clean).apply()
    }

    fun isVisible(context: Context): Boolean = prefs(context).getBoolean(KEY_VISIBLE, true)

    fun setVisible(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_VISIBLE, value).apply()
    }

    fun isSaveHistory(context: Context): Boolean = prefs(context).getBoolean(KEY_SAVE, true)

    fun setSaveHistory(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SAVE, value).apply()
    }

    fun isAskBefore(context: Context): Boolean = prefs(context).getBoolean(KEY_ASK, false)

    fun setAskBefore(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ASK, value).apply()
    }
    private const val KEY_ASKED_NOTIF = "asked_notifications"

    fun hasAskedNotifications(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ASKED_NOTIF, false)

    fun setAskedNotifications(context: Context) {
        prefs(context).edit().putBoolean(KEY_ASKED_NOTIF, true).apply()

    }
}