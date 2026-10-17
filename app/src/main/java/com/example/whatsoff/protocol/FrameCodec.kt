package com.example.whatsoff.protocol

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Robust binary frame codec implementing length-prefixed framing.
 *
 * Wire format:
 * [4-byte Big-Endian Int: Payload Length] + [Payload: Encrypted / Plaintext Bytes]
 *
 * Guarantees:
 * - Solves the newline corruption bug: multiline chat text is safely transferred.
 * - Enforces MAX_FRAME_SIZE (4MB) to prevent buffer-exhaustion attacks.
 */
object FrameCodec {

    const val MAX_FRAME_SIZE = 4 * 1024 * 1024 // 4 MB safety limit

    /**
     * Writes a length-prefixed frame to the stream.
     */
    @Throws(IOException::class)
    fun writeFrame(outputStream: DataOutputStream, payload: ByteArray) {
        if (payload.size > MAX_FRAME_SIZE) {
            throw IOException("Frame payload exceeds limit (${payload.size} > $MAX_FRAME_SIZE)")
        }
        synchronized(outputStream) {
            outputStream.writeInt(payload.size)
            outputStream.write(payload)
            outputStream.flush()
        }
    }

    /**
     * Reads a length-prefixed frame from the stream.
     * Throws EOFException if peer closed socket cleanly.
     */
    @Throws(IOException::class)
    fun readFrame(inputStream: DataInputStream): ByteArray {
        val length = inputStream.readInt()
        if (length <= 0 || length > MAX_FRAME_SIZE) {
            throw IOException("Illegal frame length: $length")
        }
        val buffer = ByteArray(length)
        inputStream.readFully(buffer)
        return buffer
    }
}
