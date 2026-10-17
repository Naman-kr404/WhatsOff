package com.example.whatsoff.model

data class DiscoveredDevice(
    val name: String,
    val ip: String,
    val port: Int = 5000,
    val isHosting: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
)
