package com.example.whatsoff.repository

import android.content.Context
import android.util.Log
import com.example.networkcore.connection.ConnectionManagerListener
import com.example.networkcore.connection.PeerConnectionManager
import com.example.whatsoff.data.MessageDao
import com.example.whatsoff.discovery.DiscoveryManager
import com.example.whatsoff.model.ChatMessage
import com.example.whatsoff.model.ConnectionState
import com.example.whatsoff.model.DeliveryStatus
import com.example.whatsoff.model.DiscoveredDevice
import com.example.whatsoff.service.PeerConnectionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Single Source of Truth coordinating:
 * - PeerConnectionManager (E2E TCP socket)
 * - DiscoveryManager (mDNS + UDP discovery)
 * - MessageDao (SQLite persistence)
 * - PeerConnectionService (Foreground execution)
 */
class ChatRepository(
    private val context: Context,
    private val connectionManager: PeerConnectionManager,
    private val discoveryManager: DiscoveryManager,
    private val messageDao: MessageDao
) {
    companion object {
        private const val TAG = "WhatsOffRepo"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val connectionState: StateFlow<ConnectionState> = connectionManager.connectionState
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = discoveryManager.discoveredDevices
    val sessionFingerprint: StateFlow<String?> = connectionManager.sessionFingerprint
    val peerIsTyping: StateFlow<Boolean> = connectionManager.peerIsTyping
    val messages: StateFlow<List<ChatMessage>> = messageDao.messagesFlow

    val deviceName: String
        get() = discoveryManager.deviceName

    val localIpAddress: String?
        get() = discoveryManager.getLocalIpAddress()

    init {
        connectionManager.listener = object : ConnectionManagerListener {
            override fun onMessageReceived(id: String, message: String, timestamp: Long) {
                scope.launch {
                    val peerAddr = connectionManager.peerAddress
                    val chatMsg = ChatMessage(
                        id = id,
                        message = message,
                        isMine = false,
                        timestamp = timestamp,
                        status = DeliveryStatus.DELIVERED,
                        peerAddress = peerAddr
                    )
                    messageDao.insertMessage(chatMsg)
                }
            }

            override fun onDeliveryAck(messageId: String, status: String) {
                scope.launch {
                    val deliveryStatus = try {
                        DeliveryStatus.valueOf(status)
                    } catch (e: Exception) {
                        DeliveryStatus.DELIVERED
                    }
                    messageDao.updateMessageStatus(messageId, deliveryStatus)
                }
            }

            override fun onTypingChanged(isTyping: Boolean) {
                // Handled reactively by peerIsTyping StateFlow
            }
        }

        // Monitor connection state for service lifecycle and DB active peer
        scope.launch {
            connectionState.collect { state ->
                when (state) {
                    is ConnectionState.Connected -> {
                        discoveryManager.isHosting = false
                        messageDao.setActivePeer(state.peerAddress)
                        PeerConnectionService.startConnected(context, state.peerName)
                    }
                    is ConnectionState.Hosting -> {
                        PeerConnectionService.startHosting(context, state.port)
                    }
                    is ConnectionState.Idle, is ConnectionState.Error -> {
                        PeerConnectionService.stop(context)
                    }
                    is ConnectionState.Connecting -> {
                        // In transit
                    }
                }
            }
        }
    }

    fun startDiscovery() {
        discoveryManager.startDiscovery()
    }

    fun stopDiscovery() {
        discoveryManager.stopDiscovery()
    }

    fun refreshDiscovery() {
        discoveryManager.refreshDiscovery()
    }

    fun startServer(port: Int = 5000) {
        discoveryManager.isHosting = true
        connectionManager.startServer(port, deviceName)
    }

    fun stopServer() {
        discoveryManager.isHosting = false
        connectionManager.stopServer()
    }

    fun connect(ip: String, port: Int = 5000, peerName: String = "") {
        discoveryManager.isHosting = false
        connectionManager.connect(ip.trim(), port, deviceName, peerName)
    }

    fun connectToDevice(device: DiscoveredDevice) {
        connect(device.ip, device.port, device.name)
    }

    fun sendMessage(messageText: String) {
        val trimmed = messageText.trim()
        if (trimmed.isBlank()) return

        val peerAddr = connectionManager.peerAddress
        val chatMsg = ChatMessage(
            message = trimmed,
            isMine = true,
            status = DeliveryStatus.SENT,
            peerAddress = peerAddr
        )

        scope.launch {
            messageDao.insertMessage(chatMsg)
            connectionManager.sendMessage(chatMsg.id, chatMsg.message, chatMsg.timestamp)
        }
    }

    fun sendTyping(isTyping: Boolean) {
        connectionManager.sendTyping(isTyping)
    }

    fun disconnect() {
        discoveryManager.isHosting = false
        connectionManager.disconnect()
        PeerConnectionService.stop(context)
    }

    fun clearError() {
        connectionManager.clearError()
    }
}