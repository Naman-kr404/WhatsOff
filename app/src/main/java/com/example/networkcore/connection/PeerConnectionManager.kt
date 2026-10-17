package com.example.networkcore.connection

import android.util.Log
import com.example.networkcore.socket.TcpClient
import com.example.networkcore.socket.TcpServer
import com.example.whatsoff.model.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface ConnectionManagerListener {
    fun onMessageReceived(id: String, message: String, timestamp: Long)
    fun onDeliveryAck(messageId: String, status: String)
    fun onTypingChanged(isTyping: Boolean)
}

/**
 * High-level peer connection coordinator supporting both Server (Host) and Client modes.
 */
class PeerConnectionManager {

    companion object {
        private const val TAG = "WhatsOffConnManager"
    }

    private val tcpServer = TcpServer()
    private val tcpClient = TcpClient()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var activeConnection: PeerConnection? = null
    private var serverJob: Job? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _sessionFingerprint = MutableStateFlow<String?>(null)
    val sessionFingerprint: StateFlow<String?> = _sessionFingerprint.asStateFlow()

    private val _peerIsTyping = MutableStateFlow(false)
    val peerIsTyping: StateFlow<Boolean> = _peerIsTyping.asStateFlow()

    var listener: ConnectionManagerListener? = null

    val peerAddress: String
        get() = activeConnection?.peerAddress ?: ""

    val peerName: String
        get() = activeConnection?.peerName ?: "Peer"

    val isConnected: Boolean
        get() = activeConnection?.isConnected == true

    fun startServer(port: Int, myDeviceName: String) {
        disconnect()
        _connectionState.value = ConnectionState.Hosting(port)

        serverJob = scope.launch {
            try {
                Log.d(TAG, "Starting hosting server on port $port...")
                val clientSocket = tcpServer.start(port) ?: return@launch

                val conn = PeerConnection(
                    socket = clientSocket,
                    myDeviceName = myDeviceName,
                    events = createConnectionEvents()
                )
                activeConnection = conn
                conn.start()
            } catch (e: Exception) {
                if (_connectionState.value is ConnectionState.Hosting) {
                    Log.e(TAG, "Server error", e)
                    _connectionState.value = ConnectionState.Error("Server error: ${e.localizedMessage}")
                }
            }
        }
    }

    fun stopServer() {
        tcpServer.stop()
        serverJob?.cancel()
        serverJob = null
        if (_connectionState.value is ConnectionState.Hosting) {
            _connectionState.value = ConnectionState.Idle
        }
    }

    fun connect(ip: String, port: Int, myDeviceName: String, peerNameHint: String = "") {
        disconnect()
        _connectionState.value = ConnectionState.Connecting(ip, port)

        scope.launch {
            try {
                val socket = tcpClient.connect(ip, port)
                val conn = PeerConnection(
                    socket = socket,
                    myDeviceName = myDeviceName,
                    events = createConnectionEvents(peerNameHint)
                )
                activeConnection = conn
                conn.start()
            } catch (e: Exception) {
                Log.e(TAG, "Failed connecting to $ip:$port", e)
                _connectionState.value = ConnectionState.Error(
                    "Could not connect to $ip:$port: ${e.localizedMessage ?: "Timeout"}"
                )
            }
        }
    }

    fun sendMessage(id: String, text: String, timestamp: Long = System.currentTimeMillis()) {
        activeConnection?.sendMessage(id, text, timestamp)
    }

    fun sendTyping(isTyping: Boolean) {
        activeConnection?.sendTyping(isTyping)
    }

    fun disconnect() {
        stopServer()
        activeConnection?.close("Local user disconnected")
        activeConnection = null
        _sessionFingerprint.value = null
        _peerIsTyping.value = false
        _connectionState.value = ConnectionState.Idle
    }

    fun clearError() {
        if (_connectionState.value is ConnectionState.Error) {
            _connectionState.value = ConnectionState.Idle
        }
    }

    private fun createConnectionEvents(nameHint: String = ""): PeerConnectionEvents {
        return object : PeerConnectionEvents {
            override fun onHandshakeComplete(peerAddress: String, peerName: String, fingerprint: String) {
                _sessionFingerprint.value = fingerprint
                val resolvedName = if (peerName.isNotBlank() && peerName != "Peer") {
                    peerName
                } else if (nameHint.isNotBlank()) {
                    nameHint
                } else {
                    peerAddress
                }

                _connectionState.value = ConnectionState.Connected(
                    peerAddress = peerAddress,
                    peerName = resolvedName
                )
            }

            override fun onMessageReceived(id: String, text: String, timestamp: Long) {
                listener?.onMessageReceived(id, text, timestamp)
            }

            override fun onDeliveryAckReceived(messageId: String, status: String) {
                listener?.onDeliveryAck(messageId, status)
            }

            override fun onTypingStatusChanged(isTyping: Boolean) {
                _peerIsTyping.value = isTyping
                listener?.onTypingChanged(isTyping)
            }

            override fun onDisconnected(reason: String) {
                Log.d(TAG, "Connection event: onDisconnected ($reason)")
                _sessionFingerprint.value = null
                _peerIsTyping.value = false
                _connectionState.value = ConnectionState.Idle
            }

            override fun onError(e: Exception) {
                Log.e(TAG, "Connection event: onError", e)
                _sessionFingerprint.value = null
                _peerIsTyping.value = false
                _connectionState.value = ConnectionState.Error(
                    e.localizedMessage ?: "Network error occurred"
                )
            }
        }
    }
}
