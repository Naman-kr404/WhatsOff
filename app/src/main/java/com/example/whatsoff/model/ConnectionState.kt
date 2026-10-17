package com.example.whatsoff.model

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data class Hosting(val port: Int) : ConnectionState
    data class Connecting(val ip: String, val port: Int) : ConnectionState
    data class Connected(val peerAddress: String, val peerName: String = "Peer") : ConnectionState
    data class Error(val message: String) : ConnectionState
}
