package com.kherphy.bchat

import java.util.UUID

enum class MessageType {
    SENT,
    RECEIVED,
    NOTICE
}

data class Message(
    val text: String,
    val timestamp: Long,
    val type: MessageType,
    val imagePath: String? = null,
    val id: String = UUID.randomUUID().toString(),
    val deleted: Boolean = false,
    val pending: Boolean = false,
    val unsendPending: Boolean = false,
    val replyToId: String? = null,
    val replyText: String = "",
    val replyMine: Boolean = false,
    val myReaction: String = "",
    val theirReaction: String = "",
    val reactPending: Boolean = false,
    val saved: Boolean = false,
    val nameOld: String = "",
    val nameNew: String = "",
    val seen: Boolean = false
)