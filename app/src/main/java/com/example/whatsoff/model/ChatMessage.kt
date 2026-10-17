package com.example.whatsoff.model

import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val message: String,
    val isMine: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val status: DeliveryStatus = if (isMine) DeliveryStatus.SENT else DeliveryStatus.DELIVERED,
    val peerAddress: String = ""
)