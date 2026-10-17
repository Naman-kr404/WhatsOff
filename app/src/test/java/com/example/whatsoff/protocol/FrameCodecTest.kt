package com.example.whatsoff.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

class FrameCodecTest {

    @Test
    fun testLengthPrefixedFramingRoundtrip() {
        val originalData = "Multiline\nPayload\r\nWith binary and emojis 🔥🔒\nEnd.".toByteArray(Charsets.UTF_8)

        val byteOut = ByteArrayOutputStream()
        val dataOut = DataOutputStream(byteOut)
        FrameCodec.writeFrame(dataOut, originalData)

        val wireBytes = byteOut.toByteArray()
        // Must contain 4-byte header + payload
        assertEquals(originalData.size + 4, wireBytes.size)

        val byteIn = ByteArrayInputStream(wireBytes)
        val dataIn = DataInputStream(byteIn)
        val decoded = FrameCodec.readFrame(dataIn)

        assertEquals(String(originalData, Charsets.UTF_8), String(decoded, Charsets.UTF_8))
    }

    @Test
    fun testExcessiveFrameSizeRejected() {
        val byteOut = ByteArrayOutputStream()
        val dataOut = DataOutputStream(byteOut)

        // Write an illegal frame header claiming 5 MB (limit is 4 MB)
        dataOut.writeInt(5 * 1024 * 1024)
        dataOut.flush()

        val byteIn = ByteArrayInputStream(byteOut.toByteArray())
        val dataIn = DataInputStream(byteIn)

        try {
            FrameCodec.readFrame(dataIn)
            fail("Must throw IOException on frame exceeding MAX_FRAME_SIZE")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("Illegal frame length") == true)
        }
    }

    @Test
    fun testPacketSerializationRoundtrips() {
        // 1. Handshake Packet
        val handshake = Packet.Handshake("MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE...", "Pixel 8 Pro")
        val decodedHandshake = Packet.fromJson(handshake.toJson()) as Packet.Handshake
        assertEquals(handshake.publicKeyBase64, decodedHandshake.publicKeyBase64)
        assertEquals(handshake.deviceName, decodedHandshake.deviceName)

        // 2. Multiline Message Packet
        val message = Packet.Message(
            id = "msg-1234",
            text = "Line 1\nLine 2\nLine 3 with : colons and emojis 🚀",
            timestamp = 1710000000L
        )
        val decodedMessage = Packet.fromJson(message.toJson()) as Packet.Message
        assertEquals(message.id, decodedMessage.id)
        assertEquals(message.text, decodedMessage.text)
        assertEquals(message.timestamp, decodedMessage.timestamp)

        // 3. DeliveryAck Packet
        val ack = Packet.DeliveryAck("msg-1234", "DELIVERED")
        val decodedAck = Packet.fromJson(ack.toJson()) as Packet.DeliveryAck
        assertEquals(ack.messageId, decodedAck.messageId)
        assertEquals(ack.status, decodedAck.status)

        // 4. Typing Packet
        val typing = Packet.Typing(true)
        val decodedTyping = Packet.fromJson(typing.toJson()) as Packet.Typing
        assertEquals(true, decodedTyping.isTyping)

        // 5. Ping & Pong
        val ping = Packet.Ping(12345L)
        val decodedPing = Packet.fromJson(ping.toJson()) as Packet.Ping
        assertEquals(12345L, decodedPing.timestamp)

        // 6. Disconnect
        val disconnect = Packet.Disconnect("Session timeout")
        val decodedDisconnect = Packet.fromJson(disconnect.toJson()) as Packet.Disconnect
        assertEquals("Session timeout", decodedDisconnect.reason)
    }
}
