package com.kherphy.bchat

enum class MessageType {
    SENT,
    RECEIVED,
    NOTICE
}

data class Message(
    val text: String,
    val timestamp: Long,
    val type: MessageType
)