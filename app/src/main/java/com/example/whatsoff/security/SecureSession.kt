package com.example.whatsoff.security

import android.util.Base64
import java.security.KeyPair
import java.security.PublicKey
import javax.crypto.SecretKey

/**
 * Encapsulates the cryptographic state for an active peer-to-peer connection.
 */
class SecureSession(
    val myKeyPair: KeyPair = AesGcmCipher.generateEcKeyPair()
) {
    val myPublicKeyBytes: ByteArray = myKeyPair.public.encoded
    val myPublicKeyBase64: String = Base64.encodeToString(myPublicKeyBytes, Base64.NO_WRAP)

    var peerPublicKey: PublicKey? = null
        private set

    var peerPublicKeyBytes: ByteArray? = null
        private set

    var sharedSecretKey: SecretKey? = null
        private set

    var sessionFingerprint: String? = null
        private set

    val isEstablished: Boolean
        get() = sharedSecretKey != null

    /**
     * Initializes the secure session with the peer's public key (Base64 encoded).
     */
    fun establishWithPeer(peerPublicKeyBase64: String) {
        val peerBytes = Base64.decode(peerPublicKeyBase64, Base64.NO_WRAP)
        val peerKey = AesGcmCipher.decodePublicKey(peerBytes)
        peerPublicKey = peerKey
        peerPublicKeyBytes = peerBytes

        val derivedKey = AesGcmCipher.deriveSharedKey(myKeyPair, peerKey)
        sharedSecretKey = derivedKey
        sessionFingerprint = AesGcmCipher.computeFingerprint(myPublicKeyBytes, peerBytes)
    }

    /**
     * Encrypts plaintext bytes.
     */
    fun encrypt(plainText: ByteArray): ByteArray {
        val key = checkNotNull(sharedSecretKey) { "Session is not yet established" }
        return AesGcmCipher.encrypt(plainText, key)
    }

    /**
     * Decrypts ciphertext bytes.
     */
    fun decrypt(cipherText: ByteArray): ByteArray {
        val key = checkNotNull(sharedSecretKey) { "Session is not yet established" }
        return AesGcmCipher.decrypt(cipherText, key)
    }
}
