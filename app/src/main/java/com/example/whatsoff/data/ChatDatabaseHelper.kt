package com.example.whatsoff.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.whatsoff.model.ChatMessage
import com.example.whatsoff.model.DeliveryStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Enterprise SQLite Persistence Layer for WhatsOff messages.
 *
 * Provides:
 * - Thread-safe indexed message storage
 * - Instant reactive StateFlow emissions upon DB mutations
 * - Message lifecycle status tracking (PENDING, SENT, DELIVERED, READ)
 * - Safe transaction management on Dispatchers.IO
 */
class ChatDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    companion object {
        const val DATABASE_NAME = "whatsoff_chat.db"
        const val DATABASE_VERSION = 1

        const val TABLE_MESSAGES = "messages"
        const val COL_ID = "id"
        const val COL_PEER_ADDRESS = "peer_address"
        const val COL_MESSAGE = "message"
        const val COL_IS_MINE = "is_mine"
        const val COL_TIMESTAMP = "timestamp"
        const val COL_STATUS = "status"

        private const val CREATE_TABLE_MESSAGES = """
            CREATE TABLE $TABLE_MESSAGES (
                $COL_ID TEXT PRIMARY KEY,
                $COL_PEER_ADDRESS TEXT NOT NULL,
                $COL_MESSAGE TEXT NOT NULL,
                $COL_IS_MINE INTEGER NOT NULL,
                $COL_TIMESTAMP INTEGER NOT NULL,
                $COL_STATUS TEXT NOT NULL
            );
        """

        private const val CREATE_INDEX_PEER = """
            CREATE INDEX idx_messages_peer_timestamp 
            ON $TABLE_MESSAGES ($COL_PEER_ADDRESS, $COL_TIMESTAMP);
        """
    }

    private val _messagesFlow = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messagesFlow: StateFlow<List<ChatMessage>> = _messagesFlow.asStateFlow()

    private var currentPeerAddress: String = ""

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TABLE_MESSAGES)
        db.execSQL(CREATE_INDEX_PEER)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MESSAGES")
        onCreate(db)
    }

    /**
     * Sets the active peer address filter and reloads messages into the reactive Flow.
     */
    suspend fun setActivePeer(peerAddress: String) = withContext(Dispatchers.IO) {
        currentPeerAddress = peerAddress
        reloadMessagesForCurrentPeer()
    }

    /**
     * Inserts a new chat message into SQLite and notifies observers.
     */
    suspend fun insertMessage(message: ChatMessage) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(COL_ID, message.id)
            put(COL_PEER_ADDRESS, message.peerAddress)
            put(COL_MESSAGE, message.message)
            put(COL_IS_MINE, if (message.isMine) 1 else 0)
            put(COL_TIMESTAMP, message.timestamp)
            put(COL_STATUS, message.status.name)
        }

        writableDatabase.insertWithOnConflict(
            TABLE_MESSAGES,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )

        reloadMessagesForCurrentPeer()
    }

    /**
     * Updates delivery status for a specific message.
     */
    suspend fun updateMessageStatus(messageId: String, status: DeliveryStatus) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(COL_STATUS, status.name)
        }
        writableDatabase.update(
            TABLE_MESSAGES,
            values,
            "$COL_ID = ?",
            arrayOf(messageId)
        )
        reloadMessagesForCurrentPeer()
    }

    /**
     * Retrieves all messages for a peer address ordered by timestamp ascending.
     */
    suspend fun getMessagesForPeer(peerAddress: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ChatMessage>()
        val cursor: Cursor = readableDatabase.query(
            TABLE_MESSAGES,
            null,
            "$COL_PEER_ADDRESS = ?",
            arrayOf(peerAddress),
            null,
            null,
            "$COL_TIMESTAMP ASC"
        )
        cursor.use {
            val idIdx = it.getColumnIndexOrThrow(COL_ID)
            val peerIdx = it.getColumnIndexOrThrow(COL_PEER_ADDRESS)
            val msgIdx = it.getColumnIndexOrThrow(COL_MESSAGE)
            val isMineIdx = it.getColumnIndexOrThrow(COL_IS_MINE)
            val tsIdx = it.getColumnIndexOrThrow(COL_TIMESTAMP)
            val statusIdx = it.getColumnIndexOrThrow(COL_STATUS)

            while (it.moveToNext()) {
                val statusStr = it.getString(statusIdx)
                val status = try {
                    DeliveryStatus.valueOf(statusStr)
                } catch (e: Exception) {
                    DeliveryStatus.SENT
                }

                list.add(
                    ChatMessage(
                        id = it.getString(idIdx),
                        peerAddress = it.getString(peerIdx),
                        message = it.getString(msgIdx),
                        isMine = it.getInt(isMineIdx) == 1,
                        timestamp = it.getLong(tsIdx),
                        status = status
                    )
                )
            }
        }
        list
    }

    /**
     * Clears messages for a specific peer.
     */
    suspend fun clearMessagesForPeer(peerAddress: String) = withContext(Dispatchers.IO) {
        writableDatabase.delete(TABLE_MESSAGES, "$COL_PEER_ADDRESS = ?", arrayOf(peerAddress))
        reloadMessagesForCurrentPeer()
    }

    private suspend fun reloadMessagesForCurrentPeer() {
        if (currentPeerAddress.isBlank()) {
            _messagesFlow.value = emptyList()
        } else {
            _messagesFlow.value = getMessagesForPeer(currentPeerAddress)
        }
    }
}
