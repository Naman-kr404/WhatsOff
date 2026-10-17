package com.example.whatsoff.model

/**
 * Message delivery status lifecycle state.
 */
enum class DeliveryStatus {
    PENDING,   // Created locally, waiting for socket transmission
    SENT,      // Transmitted across TCP socket
    DELIVERED, // Peer acknowledged receipt
    READ,      // Peer opened the chat
    FAILED     // Transmission error
}
