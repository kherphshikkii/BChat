package com.kherphy.bchat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Conversation(
    val address: String,
    val name: String,
    val lastText: String,
    val lastTime: Long,
    val unread: Int = 0
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

        if (chatMessages.isEmpty()) {
            data.remove(address)
            write()
            return
        }

        val array = JSONArray()
        chatMessages.forEach { message ->
            val item = JSONObject()
                .put("id", message.id)
                .put("text", message.text)
                .put("time", message.timestamp)
                .put("sent", message.type == MessageType.SENT)
                .put("deleted", message.deleted)
                .put("pending", message.pending)
                .put("unsendPending", message.unsendPending)
                .put("myReact", message.myReaction)
                .put("theirReact", message.theirReaction)
                .put("reactPending", message.reactPending)
            message.imagePath?.let { path -> item.put("image", File(path).name) }
            message.replyToId?.let { replyId ->
                item.put("replyId", replyId)
                    .put("replyText", message.replyText)
                    .put("replyMine", message.replyMine)
            }
            array.put(item)
        }

        val existing = data.optJSONObject(address)?.optJSONArray("unread")
        val savedIds = chatMessages.map { it.id }.toSet()
        val unread = JSONArray()
        if (existing != null) {
            for (i in 0 until existing.length()) {
                val id = existing.optString(i)
                if (id in savedIds) unread.put(id)
            }
        }

        data.put(
            address,
            JSONObject().put("name", name).put("messages", array).put("unread", unread)
        )
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
            val id = item.optString("id", "").ifEmpty { UUID.randomUUID().toString() }
            val imageName = item.optString("image", "")
            val replyId = item.optString("replyId", "").ifEmpty { null }
            val myReact = item.optString("myReact", "")
            val theirReact = item.optString("theirReact", "")
            val reactPending = item.optBoolean("reactPending")
            when {
                item.optBoolean("deleted") ->
                    result.add(
                        Message("", time, type, null, id, true, false, item.optBoolean("unsendPending"))
                    )
                imageName.isNotEmpty() -> {
                    val image = File(imageDir(), imageName)
                    if (image.exists()) {
                        result.add(
                            Message(
                                "",
                                time,
                                type,
                                image.absolutePath,
                                id,
                                myReaction = myReact,
                                theirReaction = theirReact,
                                reactPending = reactPending
                            )
                        )
                    }
                }
                else -> result.add(
                    Message(
                        item.getString("text"),
                        time,
                        type,
                        null,
                        id,
                        false,
                        item.optBoolean("pending"),
                        false,
                        replyId,
                        item.optString("replyText", ""),
                        item.optBoolean("replyMine"),
                        myReact,
                        theirReact,
                        reactPending
                    )
                )
            }
        }
        return result
    }

    fun unreadCount(address: String): Int =
        data.optJSONObject(address)?.optJSONArray("unread")?.length() ?: 0

    fun addUnread(address: String, id: String) {
        val entry = data.optJSONObject(address) ?: return
        val array = entry.optJSONArray("unread") ?: JSONArray().also { entry.put("unread", it) }
        for (i in 0 until array.length()) {
            if (array.optString(i) == id) return
        }
        array.put(id)
        write()
    }

    fun removeUnread(address: String, id: String): Boolean {
        val array = data.optJSONObject(address)?.optJSONArray("unread") ?: return false
        for (i in 0 until array.length()) {
            if (array.optString(i) == id) {
                array.remove(i)
                write()
                return true
            }
        }
        return false
    }

    fun clearUnread(address: String) {
        val entry = data.optJSONObject(address) ?: return
        val array = entry.optJSONArray("unread")
        if (array == null || array.length() == 0) return
        entry.put("unread", JSONArray())
        write()
    }

    fun pendingAddresses(): List<String> {
        val result = mutableListOf<String>()
        val keys = data.keys()
        while (keys.hasNext()) {
            val address = keys.next()
            val array = data.optJSONObject(address)?.optJSONArray("messages") ?: continue
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val deleted = item.optBoolean("deleted")
                val waitingText = item.optBoolean("pending") && !deleted
                val waitingUnsend = deleted && item.optBoolean("unsendPending")
                val waitingReact = item.optBoolean("reactPending") && !deleted
                if (waitingText || waitingUnsend || waitingReact) {
                    result.add(address)
                    break
                }
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
            val lastText = when {
                last.optBoolean("deleted") -> if (last.optBoolean("sent")) {
                    "You unsent a message"
                } else {
                    "${entry.optString("name", address)} unsent a message"
                }
                last.optString("image", "").isNotEmpty() -> "📷 Photo"
                else -> last.getString("text")
            }
            result.add(
                Conversation(
                    address,
                    entry.optString("name", address),
                    lastText,
                    last.getLong("time"),
                    entry.optJSONArray("unread")?.length() ?: 0
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