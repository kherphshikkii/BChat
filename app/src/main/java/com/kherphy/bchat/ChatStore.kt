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
    private const val MAX_MESSAGES = 500

    private var file: File? = null
    private var data = JSONObject()

    fun init(context: Context) {
        if (file != null) return
        val target = File(context.applicationContext.filesDir, FILE_NAME)
        file = target
        data = try {
            if (target.exists()) JSONObject(target.readText()) else JSONObject()
        } catch (e: Exception) {
            JSONObject()
        }
    }

    fun save(address: String, name: String, messages: List<Message>) {
        val chatMessages = messages
            .filter { it.type != MessageType.NOTICE }
            .takeLast(MAX_MESSAGES)
        if (chatMessages.isEmpty()) return

        val array = JSONArray()
        chatMessages.forEach {
            array.put(
                JSONObject()
                    .put("text", it.text)
                    .put("time", it.timestamp)
                    .put("sent", it.type == MessageType.SENT)
            )
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
            result.add(Message(item.getString("text"), item.getLong("time"), type))
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
            result.add(
                Conversation(
                    address,
                    entry.optString("name", address),
                    last.getString("text"),
                    last.getLong("time")
                )
            )
        }
        return result.sortedByDescending { it.lastTime }
    }

    fun clearAll() {
        data = JSONObject()
        write()
    }

    private fun write() {
        try {
            file?.writeText(data.toString())
        } catch (e: Exception) {
        }
    }
}