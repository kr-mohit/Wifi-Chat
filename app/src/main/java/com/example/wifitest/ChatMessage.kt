package com.example.wifitest

data class ChatMessage(
    val message: String,
    val isSent: Boolean // true if this device sent it
)