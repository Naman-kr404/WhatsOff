package com.example.whatsoff.security

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Enterprise-grade Cryptographic Engine for WhatsOff.
 *
 * Implements:
 * 1. Elliptic-Curve Diffie-Hellman (ECDH) Key Agreement over NIST P-256 (secp256r1)
 * 2. Key Derivation Function (KDF) using SHA-256
 * 3. Authenticated Symmetric Encryption using AES-256-GCM (12-byte IV, 128-bit Auth Tag)
 * 4. Human-readable Security Fingerprint generation for Peer Verification
 */
object AesGcmCipher {

    private const val EC_CURVE = "secp256r1"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private val secureRandom = SecureRandom()

    /**
     * Generates an ephemeral EC key pair for the session.
     */
    fun generateEcKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        val ecSpec = ECGenParameterSpec(EC_CURVE)
        keyPairGenerator.initialize(ecSpec, secureRandom)
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Deserializes a peer's public key from X.509 encoded bytes.
     */
    fun decodePublicKey(bytes: ByteArray): PublicKey {
        val keyFactory = KeyFactory.getInstance("EC")
        val keySpec = X509EncodedKeySpec(bytes)
        return keyFactory.generatePublic(keySpec)
    }

    /**
     * Computes the shared secret using ECDH and derives an AES-256 key via SHA-256 KDF.
     */
    fun deriveSharedKey(myKeyPair: KeyPair, peerPublicKey: PublicKey): SecretKey {
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(myKeyPair.private)
        keyAgreement.doPhase(peerPublicKey, true)
        val sharedSecret = keyAgreement.generateSecret()

        val digest = MessageDigest.getInstance("SHA-256")
        val derivedKeyBytes = digest.digest(sharedSecret)
        return SecretKeySpec(derivedKeyBytes, "AES")
    }

    /**
     * Encrypts plaintext bytes using AES-256-GCM with a fresh random 12-byte IV.
     * Output format: [12 bytes IV] + [Ciphertext + 16 bytes Auth Tag]
     */
    fun encrypt(plainText: ByteArray, secretKey: SecretKey): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val cipherText = cipher.doFinal(plainText)
        val output = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, output, 0, iv.size)
        System.arraycopy(cipherText, 0, output, iv.size, cipherText.size)
        return output
    }

    /**
     * Decrypts AES-256-GCM ciphertext bytes.
     * Input format: [12 bytes IV] + [Ciphertext + 16 bytes Auth Tag]
     */
    fun decrypt(encryptedBytes: ByteArray, secretKey: SecretKey): ByteArray {
        require(encryptedBytes.size > GCM_IV_LENGTH) { "Ciphertext too short" }

        val iv = ByteArray(GCM_IV_LENGTH)
        System.arraycopy(encryptedBytes, 0, iv, 0, GCM_IV_LENGTH)

        val cipherTextLength = encryptedBytes.size - GCM_IV_LENGTH
        val cipherText = ByteArray(cipherTextLength)
        System.arraycopy(encryptedBytes, GCM_IV_LENGTH, cipherText, 0, cipherTextLength)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        return cipher.doFinal(cipherText)
    }

    /**
     * Computes a deterministic 16-character hex fingerprint (e.g. "4C92 · A17E · 8B33 · F019")
     * from both public keys sorted lexicographically, allowing users to verify they have the same session.
     */
    fun computeFingerprint(key1: ByteArray, key2: ByteArray): String {
        val combined = if (compareByteArrays(key1, key2) < 0) key1 + key2 else key2 + key1
        val hash = MessageDigest.getInstance("SHA-256").digest(combined)
        val hex = hash.take(8).joinToString("") { "%02X".format(it) }
        return hex.chunked(4).joinToString(" · ")
    }

    private fun compareByteArrays(a: ByteArray, b: ByteArray): Int {
        val minLen = minOf(a.size, b.size)
        for (i in 0 until minLen) {
            val cmp = (a[i].toInt() and 0xFF).compareTo(b[i].toInt() and 0xFF)
            if (cmp != 0) return cmp
        }
        return a.size.compareTo(b.size)
    }
}
