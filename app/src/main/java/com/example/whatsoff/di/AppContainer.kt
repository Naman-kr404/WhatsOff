package com.example.whatsoff.di

import android.content.Context
import com.example.networkcore.connection.PeerConnectionManager
import com.example.whatsoff.data.ChatDatabaseHelper
import com.example.whatsoff.data.MessageDao
import com.example.whatsoff.data.SqliteMessageDao
import com.example.whatsoff.discovery.DiscoveryManager
import com.example.whatsoff.repository.ChatRepository

/**
 * Dependency Injection Container contract for WhatsOff.
 */
interface AppContainer {
    val databaseHelper: ChatDatabaseHelper
    val messageDao: MessageDao
    val connectionManager: PeerConnectionManager
    val discoveryManager: DiscoveryManager
    val chatRepository: ChatRepository
}

/**
 * Default implementation managing singletons.
 */
class DefaultAppContainer(private val context: Context) : AppContainer {

    override val databaseHelper: ChatDatabaseHelper by lazy {
        ChatDatabaseHelper(context)
    }

    override val messageDao: MessageDao by lazy {
        SqliteMessageDao(databaseHelper)
    }

    override val connectionManager: PeerConnectionManager by lazy {
        PeerConnectionManager()
    }

    override val discoveryManager: DiscoveryManager by lazy {
        DiscoveryManager(context)
    }

    override val chatRepository: ChatRepository by lazy {
        ChatRepository(
            context = context,
            connectionManager = connectionManager,
            discoveryManager = discoveryManager,
            messageDao = messageDao
        )
    }
}
