package com.example.networkcore.socket

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

class TcpServer {

    companion object {
        private const val TAG = "WhatsOffServer"
    }

    private var serverSocket: ServerSocket? = null
    @Volatile
    private var isStopped = false

    suspend fun start(port: Int): Socket? = withContext(Dispatchers.IO) {
        stop()

        isStopped = false
        val s = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(port))
        }
        serverSocket = s

        Log.d(TAG, "Server listening on port $port. Awaiting client connection...")

        try {
            val clientSocket = s.accept()
            Log.d(TAG, "Client accepted from: ${clientSocket.inetAddress?.hostAddress}")
            clientSocket
        } catch (e: SocketException) {
            if (isStopped) {
                Log.d(TAG, "Server accept cancelled gracefully")
                null
            } else {
                throw e
            }
        }
    }

    fun stop() {
        isStopped = true
        try {
            serverSocket?.let {
                if (!it.isClosed) {
                    it.close()
                    Log.d(TAG, "ServerSocket closed")
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error closing ServerSocket", e)
        } finally {
            serverSocket = null
        }
    }

    fun isRunning(): Boolean = serverSocket?.isClosed == false
}
