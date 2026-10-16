package com.kherphy.bchat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Conversation(
    val address: String,
    val name: String,
    val lastText: String,
    val lastTime: Long
)

object ChatStore {

    private const val FILE_NAME = "conversations.json"
    private const val IMAGE_DIR = "images"
    private const val MAX_MESSAGES = 500

    private var file: File? = null
    private var imageDirectory: File? = null
    private var data = JSONObject()

    fun init(context: Context) {
        if (file != null) return
        val root = context.applicationContext.filesDir
        val target = File(root, FILE_NAME)
        file = target
        imageDirectory = File(root, IMAGE_DIR).also { it.mkdirs() }
        data = try {
            if (target.exists()) JSONObject(target.readText()) else JSONObject()
        } catch (e: Exception) {
            JSONObject()
        }
        removeUnusedImages()
    }

    fun imageDir(): File {
        val dir = checkNotNull(imageDirectory)
        dir.mkdirs()
        return dir
    }

    fun newImageFile(): File =
        File(imageDir(), "img_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg")

    fun save(address: String, name: String, messages: List<Message>) {
        val chatMessages = messages
            .filter { it.type != MessageType.NOTICE }
            .takeLast(MAX_MESSAGES)
        if (chatMessages.isEmpty()) return

        val array = JSONArray()
        chatMessages.forEach { message ->
            val item = JSONObject()
                .put("text", message.text)
                .put("time", message.timestamp)
                .put("sent", message.type == MessageType.SENT)
            message.imagePath?.let { path -> item.put("image", File(path).name) }
            array.put(item)
        }
        data.put(address, JSONObject().put("name", name).put("messages", array))
        write()
    }

    fun loadMessages(address: String): List<Message> {
        val entry = data.optJSONObject(address) ?: return emptyList()
        val array = entry.optJSONArray("messages") ?: return emptyList()
        val result = mutableListOf<Message>()
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val type = if (item.optBoolean("sent")) MessageType.SENT else MessageType.RECEIVED
            val time = item.getLong("time")
            val imageName = item.optString("image", "")
            if (imageName.isNotEmpty()) {
                val image = File(imageDir(), imageName)
                if (image.exists()) result.add(Message("", time, type, image.absolutePath))
            } else {
                result.add(Message(item.getString("text"), time, type))
            }
        }
        return result
    }

    fun conversations(): List<Conversation> {
        val result = mutableListOf<Conversation>()
        val keys = data.keys()
        while (keys.hasNext()) {
            val address = keys.next()
            val entry = data.getJSONObject(address)
            val array = entry.optJSONArray("messages") ?: continue
            if (array.length() == 0) continue
            val last = array.getJSONObject(array.length() - 1)
            val lastText = if (last.optString("image", "").isNotEmpty()) {
                "📷 Photo"
            } else {
                last.getString("text")
            }
            result.add(
                Conversation(
                    address,
                    entry.optString("name", address),
                    lastText,
                    last.getLong("time")
                )
            )
        }
        return result.sortedByDescending { it.lastTime }
    }

    fun clearAll() {
        data = JSONObject()
        write()
        imageDir().listFiles()?.forEach { it.delete() }
    }

    private fun removeUnusedImages() {
        val used = mutableSetOf<String>()
        val keys = data.keys()
        while (keys.hasNext()) {
            val array = data.optJSONObject(keys.next())?.optJSONArray("messages") ?: continue
            for (i in 0 until array.length()) {
                val name = array.optJSONObject(i)?.optString("image", "").orEmpty()
                if (name.isNotEmpty()) used.add(name)
            }
        }
        imageDir().listFiles()?.forEach { if (it.name !in used) it.delete() }
    }

    private fun write() {
        try {
            file?.writeText(data.toString())
        } catch (e: Exception) {
        }
    }
}