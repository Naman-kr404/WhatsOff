package com.example.whatsoff.protocol

import org.json.JSONObject

/**
 * Type-safe wire protocol packets exchanged over the peer-to-peer TCP channel.
 */
sealed class Packet {

    abstract fun toJson(): String

    data class Handshake(
        val publicKeyBase64: String,
        val deviceName: String,
        val protocolVersion: Int = 1
    ) : Packet() {
        override fun toJson(): String = JSONObject().apply {
            put(KEY_TYPE, TYPE_HANDSHAKE)
            put("publicKey", publicKeyBase64)
            put("deviceName", deviceName)
            put("version", protocolVersion)
        }.toString()
    }

    data class Message(
        val id: String,
        val text: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : Packet() {
        override fun toJson(): String = JSONObject().apply {
            put(KEY_TYPE, TYPE_MESSAGE)
            put("id", id)
            put("text", text)
            put("timestamp", timestamp)
        }.toString()
    }

    data class DeliveryAck(
        val messageId: String,
        val status: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : Packet() {
        override fun toJson(): String = JSONObject().apply {
            put(KEY_TYPE, TYPE_ACK)
            put("messageId", messageId)
            put("status", status)
            put("timestamp", timestamp)
        }.toString()
    }

    data class Typing(
        val isTyping: Boolean
    ) : Packet() {
        override fun toJson(): String = JSONObject().apply {
            put(KEY_TYPE, TYPE_TYPING)
            put("isTyping", isTyping)
        }.toString()
    }

    data class Ping(val timestamp: Long = System.currentTimeMillis()) : Packet() {
        override fun toJson(): String = JSONObject().apply {
            put(KEY_TYPE, TYPE_PING)
            put("timestamp", timestamp)
        }.toString()
    }

    data class Pong(val timestamp: Long = System.currentTimeMillis()) : Packet() {
        override fun toJson(): String = JSONObject().apply {
            put(KEY_TYPE, TYPE_PONG)
            put("timestamp", timestamp)
        }.toString()
    }

    data class Disconnect(val reason: String = "User left session") : Packet() {
        override fun toJson(): String = JSONObject().apply {
            put(KEY_TYPE, TYPE_DISCONNECT)
            put("reason", reason)
        }.toString()
    }

    companion object {
        const val KEY_TYPE = "type"
        const val TYPE_HANDSHAKE = "HANDSHAKE"
        const val TYPE_MESSAGE = "MESSAGE"
        const val TYPE_ACK = "ACK"
        const val TYPE_TYPING = "TYPING"
        const val TYPE_PING = "PING"
        const val TYPE_PONG = "PONG"
        const val TYPE_DISCONNECT = "DISCONNECT"

        fun fromJson(jsonStr: String): Packet {
            val json = JSONObject(jsonStr)
            return when (val type = json.optString(KEY_TYPE)) {
                TYPE_HANDSHAKE -> Handshake(
                    publicKeyBase64 = json.getString("publicKey"),
                    deviceName = json.optString("deviceName", "Peer"),
                    protocolVersion = json.optInt("version", 1)
                )
                TYPE_MESSAGE -> Message(
                    id = json.getString("id"),
                    text = json.getString("text"),
                    timestamp = json.optLong("timestamp", System.currentTimeMillis())
                )
                TYPE_ACK -> DeliveryAck(
                    messageId = json.getString("messageId"),
                    status = json.getString("status"),
                    timestamp = json.optLong("timestamp", System.currentTimeMillis())
                )
                TYPE_TYPING -> Typing(
                    isTyping = json.optBoolean("isTyping", false)
                )
                TYPE_PING -> Ping(
                    timestamp = json.optLong("timestamp", System.currentTimeMillis())
                )
                TYPE_PONG -> Pong(
                    timestamp = json.optLong("timestamp", System.currentTimeMillis())
                )
                TYPE_DISCONNECT -> Disconnect(
                    reason = json.optString("reason", "Disconnected")
                )
                else -> throw IllegalArgumentException("Unknown packet type: $type")
            }
        }
    }
}
