package com.example.whatsoff.data

import com.example.whatsoff.model.ChatMessage
import com.example.whatsoff.model.DeliveryStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * Data Access Object abstraction for chat messages.
 */
interface MessageDao {
    val messagesFlow: StateFlow<List<ChatMessage>>
    suspend fun setActivePeer(peerAddress: String)
    suspend fun insertMessage(message: ChatMessage)
    suspend fun updateMessageStatus(messageId: String, status: DeliveryStatus)
    suspend fun getMessagesForPeer(peerAddress: String): List<ChatMessage>
    suspend fun clearMessagesForPeer(peerAddress: String)
}

class SqliteMessageDao(private val dbHelper: ChatDatabaseHelper) : MessageDao {
    override val messagesFlow: StateFlow<List<ChatMessage>> = dbHelper.messagesFlow

    override suspend fun setActivePeer(peerAddress: String) {
        dbHelper.setActivePeer(peerAddress)
    }

    override suspend fun insertMessage(message: ChatMessage) {
        dbHelper.insertMessage(message)
    }

    override suspend fun updateMessageStatus(messageId: String, status: DeliveryStatus) {
        dbHelper.updateMessageStatus(messageId, status)
    }

    override suspend fun getMessagesForPeer(peerAddress: String): List<ChatMessage> {
        return dbHelper.getMessagesForPeer(peerAddress)
    }

    override suspend fun clearMessagesForPeer(peerAddress: String) {
        dbHelper.clearMessagesForPeer(peerAddress)
    }
}
