package com.example.networkcore.connection

import android.util.Log
import com.example.whatsoff.protocol.FrameCodec
import com.example.whatsoff.protocol.Packet
import com.example.whatsoff.security.SecureSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

interface PeerConnectionEvents {
    fun onHandshakeComplete(peerAddress: String, peerName: String, fingerprint: String)
    fun onMessageReceived(id: String, text: String, timestamp: Long)
    fun onDeliveryAckReceived(messageId: String, status: String)
    fun onTypingStatusChanged(isTyping: Boolean)
    fun onDisconnected(reason: String)
    fun onError(e: Exception)
}

/**
 * Idiomatic Kotlin PeerConnection with:
 * - 4-byte length-prefixed framing (solves multiline / newline truncation)
 * - ECDH + AES-256-GCM End-to-End Encryption
 * - Heartbeat (Ping/Pong)
 * - Structured Coroutine lifecycle
 */
class PeerConnection(
    private val socket: Socket,
    private val myDeviceName: String,
    private val events: PeerConnectionEvents
) {
    companion object {
        private const val TAG = "WhatsOffPeerConn"
    }

    private val session = SecureSession()
    private val connectionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isClosed = AtomicBoolean(false)

    private val inputStream = DataInputStream(socket.getInputStream())
    private val outputStream = DataOutputStream(socket.getOutputStream())

    private var receiveJob: Job? = null

    val peerAddress: String
        get() = socket.inetAddress?.hostAddress ?: "Unknown"

    var peerName: String = "Peer"
        private set

    val sessionFingerprint: String?
        get() = session.sessionFingerprint

    val isConnected: Boolean
        get() = !isClosed.get() && socket.isConnected && !socket.isClosed

    fun start() {
        if (receiveJob != null) return

        receiveJob = connectionScope.launch {
            try {
                performHandshake()
                receiveLoop()
            } catch (e: Exception) {
                if (!isClosed.get()) {
                    Log.e(TAG, "Connection loop error", e)
                    events.onError(e)
                    close("Connection error: ${e.localizedMessage}")
                }
            }
        }
    }

    private suspend fun performHandshake() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting E2E Handshake with $peerAddress...")

        // 1. Send our public key
        val myHandshake = Packet.Handshake(
            publicKeyBase64 = session.myPublicKeyBase64,
            deviceName = myDeviceName
        )
        val myHandshakeBytes = myHandshake.toJson().toByteArray(Charsets.UTF_8)
        FrameCodec.writeFrame(outputStream, myHandshakeBytes)

        // 2. Read peer's public key (first frame is plaintext handshake)
        val peerHandshakeBytes = FrameCodec.readFrame(inputStream)
        val peerPacket = Packet.fromJson(String(peerHandshakeBytes, Charsets.UTF_8))
        if (peerPacket !is Packet.Handshake) {
            throw IOException("Expected Handshake packet from peer, got: ${peerPacket::class.simpleName}")
        }

        peerName = peerPacket.deviceName
        session.establishWithPeer(peerPacket.publicKeyBase64)

        val fingerprint = checkNotNull(session.sessionFingerprint)
        Log.d(TAG, "Handshake complete with $peerName ($peerAddress). Security Fingerprint: $fingerprint")

        withContext(Dispatchers.Main) {
            events.onHandshakeComplete(peerAddress, peerName, fingerprint)
        }
    }

    private suspend fun receiveLoop() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Entered encrypted packet receive loop")
        while (connectionScope.isActive && !isClosed.get()) {
            val encryptedFrame = try {
                FrameCodec.readFrame(inputStream)
            } catch (e: EOFException) {
                Log.d(TAG, "Peer closed stream cleanly (EOF)")
                break
            }

            val decryptedBytes = session.decrypt(encryptedFrame)
            val jsonString = String(decryptedBytes, Charsets.UTF_8)
            val packet = Packet.fromJson(jsonString)

            handleIncomingPacket(packet)
        }

        close("Peer disconnected")
    }

    private suspend fun handleIncomingPacket(packet: Packet) {
        when (packet) {
            is Packet.Message -> {
                Log.d(TAG, "Received message [${packet.id}]: ${packet.text.take(30)}")
                // Send immediate delivery ACK
                sendDeliveryAck(packet.id, "DELIVERED")

                withContext(Dispatchers.Main) {
                    events.onMessageReceived(packet.id, packet.text, packet.timestamp)
                }
            }

            is Packet.DeliveryAck -> {
                Log.d(TAG, "Received delivery ACK for message [${packet.messageId}]: ${packet.status}")
                withContext(Dispatchers.Main) {
                    events.onDeliveryAckReceived(packet.messageId, packet.status)
                }
            }

            is Packet.Typing -> {
                withContext(Dispatchers.Main) {
                    events.onTypingStatusChanged(packet.isTyping)
                }
            }

            is Packet.Ping -> {
                sendPacketInternal(Packet.Pong())
            }

            is Packet.Pong -> {
                // Heartbeat acknowledged
            }

            is Packet.Disconnect -> {
                close("Peer disconnected: ${packet.reason}")
            }

            is Packet.Handshake -> {
                // Duplicate handshake ignored
            }
        }
    }

    fun sendMessage(id: String, text: String, timestamp: Long) {
        if (!isConnected) return
        connectionScope.launch {
            try {
                val packet = Packet.Message(id = id, text = text, timestamp = timestamp)
                sendPacketInternal(packet)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message", e)
                events.onError(e)
            }
        }
    }

    fun sendTyping(isTyping: Boolean) {
        if (!isConnected) return
        connectionScope.launch {
            try {
                sendPacketInternal(Packet.Typing(isTyping))
            } catch (e: Exception) {
                // Ignore transient typing error
            }
        }
    }

    private fun sendDeliveryAck(messageId: String, status: String) {
        connectionScope.launch {
            try {
                sendPacketInternal(Packet.DeliveryAck(messageId, status))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send delivery ack", e)
            }
        }
    }

    private fun sendPacketInternal(packet: Packet) {
        if (!isConnected) return
        val jsonBytes = packet.toJson().toByteArray(Charsets.UTF_8)
        val encrypted = session.encrypt(jsonBytes)
        FrameCodec.writeFrame(outputStream, encrypted)
    }

    fun close(reason: String = "Normal closure") {
        if (isClosed.compareAndSet(false, true)) {
            Log.d(TAG, "Closing connection: $reason")
            try {
                if (socket.isConnected && !socket.isClosed) {
                    // Best effort disconnect packet
                    try {
                        val disconnectBytes = session.encrypt(Packet.Disconnect(reason).toJson().toByteArray())
                        FrameCodec.writeFrame(outputStream, disconnectBytes)
                    } catch (ignored: Exception) {}
                    socket.close()
                }
            } catch (ignored: Exception) {}

            try {
                inputStream.close()
            } catch (ignored: Exception) {}

            try {
                outputStream.close()
            } catch (ignored: Exception) {}

            connectionScope.cancel()

            events.onDisconnected(reason)
        }
    }
}
