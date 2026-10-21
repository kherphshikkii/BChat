package com.kherphy.bchat

import android.content.Context
import android.content.SharedPreferences
import java.io.File

object AvatarStore {

    private const val DIR = "avatars"
    private const val PREFS = "bchat_avatars"
    private const val MINE = "me"

    private var dir: File? = null
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (dir != null) return
        val app = context.applicationContext
        dir = File(app.filesDir, DIR).also { it.mkdirs() }
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private fun folder(): File {
        val d = checkNotNull(dir)
        d.mkdirs()
        return d
    }

    private fun store(): SharedPreferences = checkNotNull(prefs)

    private fun fileFor(key: String): File =
        File(folder(), key.replace(Regex("[^A-Za-z0-9]"), "_") + ".jpg")

    fun myFile(): File? = fileFor(MINE).takeIf { it.exists() }

    fun myVersion(): Long = store().getLong("v_$MINE", 0L)

    fun setMine(source: File): Boolean = try {
        source.copyTo(fileFor(MINE), overwrite = true)
        store().edit().putLong("v_$MINE", System.currentTimeMillis()).apply()
        true
    } catch (e: Exception) {
        false
    }

    fun removeMine() {
        fileFor(MINE).delete()
        store().edit().putLong("v_$MINE", System.currentTimeMillis()).apply()
    }

    fun theirFile(address: String): File? =
        if (address.isBlank()) null else fileFor(address).takeIf { it.exists() }

    fun theirVersion(address: String): Long = store().getLong("v_$address", 0L)

    fun saveTheirs(address: String, version: Long, bytes: ByteArray): Boolean = try {
        fileFor(address).writeBytes(bytes)
        store().edit().putLong("v_$address", version).apply()
        true
    } catch (e: Exception) {
        false
    }

    fun removeTheirs(address: String, version: Long) {
        fileFor(address).delete()
        store().edit().putLong("v_$address", version).apply()
    }

    fun clearTheirs() {
        val keep = fileFor(MINE).name
        folder().listFiles()?.forEach { if (it.name != keep) it.delete() }
        val editor = store().edit()
        store().all.keys.filter { it != "v_$MINE" }.forEach { editor.remove(it) }
        editor.apply()
    }
}