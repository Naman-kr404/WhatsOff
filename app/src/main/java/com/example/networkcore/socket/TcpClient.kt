package com.example.networkcore.socket

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

class TcpClient {

    companion object {
        private const val TAG = "WhatsOffClient"
        private const val CONNECT_TIMEOUT_MS = 6000
    }

    suspend fun connect(ip: String, port: Int): Socket = withContext(Dispatchers.IO) {
        Log.d(TAG, "Connecting to $ip:$port (timeout: ${CONNECT_TIMEOUT_MS}ms)...")
        val socket = Socket()
        socket.tcpNoDelay = true
        socket.connect(InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS)
        Log.d(TAG, "Socket connected to $ip:$port")
        socket
    }
}
