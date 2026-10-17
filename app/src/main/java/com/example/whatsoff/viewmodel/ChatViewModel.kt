package com.example.whatsoff.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.whatsoff.WhatsOffApplication
import com.example.whatsoff.model.ChatMessage
import com.example.whatsoff.model.ConnectionState
import com.example.whatsoff.model.DiscoveredDevice
import com.example.whatsoff.repository.ChatRepository
import kotlinx.coroutines.flow.StateFlow

class ChatViewModel(private val repository: ChatRepository) : ViewModel() {

    // Persistent messages backed by SQLite
    val messages: StateFlow<List<ChatMessage>> = repository.messages

    val connectionState: StateFlow<ConnectionState> = repository.connectionState
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = repository.discoveredDevices
    val sessionFingerprint: StateFlow<String?> = repository.sessionFingerprint
    val peerIsTyping: StateFlow<Boolean> = repository.peerIsTyping

    val deviceName: String
        get() = repository.deviceName

    val localIp: String
        get() = repository.localIpAddress ?: "Not Connected"

    init {
        repository.startDiscovery()
    }

    fun startDiscovery() {
        repository.startDiscovery()
    }

    fun stopDiscovery() {
        repository.stopDiscovery()
    }

    fun refreshDiscovery() {
        repository.refreshDiscovery()
    }

    fun startServer(port: Int = 5000) {
        repository.startServer(port)
    }

    fun stopServer() {
        repository.stopServer()
    }

    fun connect(ip: String, port: Int = 5000, peerName: String = "") {
        if (ip.isBlank()) return
        repository.connect(ip.trim(), port, peerName)
    }

    fun connectToDevice(device: DiscoveredDevice) {
        connect(device.ip, device.port, device.name)
    }

    fun sendMessage(message: String) {
        repository.sendMessage(message)
    }

    fun sendTyping(isTyping: Boolean) {
        repository.sendTyping(isTyping)
    }

    fun disconnect() {
        repository.disconnect()
    }

    fun clearError() {
        repository.clearError()
    }

    override fun onCleared() {
        repository.stopDiscovery()
        repository.disconnect()
        super.onCleared()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(
                    extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as? WhatsOffApplication
                ) { "WhatsOffApplication must be initialized" }
                return ChatViewModel(application.container.chatRepository) as T
            }
        }
    }
}