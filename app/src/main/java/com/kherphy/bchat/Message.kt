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
    val pending: Boolean = false
)