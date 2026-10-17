package com.example.whatsoff.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException

class AesGcmCipherTest {

    @Test
    fun testEcKeyPairGeneration() {
        val keyPair = AesGcmCipher.generateEcKeyPair()
        assertNotNull(keyPair.public)
        assertNotNull(keyPair.private)
        assertEquals("EC", keyPair.public.algorithm)
    }

    @Test
    fun testEcdhKeyAgreementSymmetry() {
        val aliceKeyPair = AesGcmCipher.generateEcKeyPair()
        val bobKeyPair = AesGcmCipher.generateEcKeyPair()

        // Alice derives secret using Bob's public key
        val aliceDerivedKey = AesGcmCipher.deriveSharedKey(aliceKeyPair, bobKeyPair.public)

        // Bob derives secret using Alice's public key
        val bobDerivedKey = AesGcmCipher.deriveSharedKey(bobKeyPair, aliceKeyPair.public)

        // Both derived symmetric keys MUST be identical
        assertArrayEquals(
            "ECDH shared secret keys must match",
            aliceDerivedKey.encoded,
            bobDerivedKey.encoded
        )
    }

    @Test
    fun testAesGcmEncryptionDecryptionRoundtrip() {
        val alice = AesGcmCipher.generateEcKeyPair()
        val bob = AesGcmCipher.generateEcKeyPair()
        val secretKey = AesGcmCipher.deriveSharedKey(alice, bob.public)

        val sampleText = "Hello from WhatsOff! 🔒 Direct P2P Session with Multiline\nSecond line with unicode: 🚀✨\nThird line."
        val plainBytes = sampleText.toByteArray(Charsets.UTF_8)

        val encrypted = AesGcmCipher.encrypt(plainBytes, secretKey)
        assertNotNull(encrypted)
        // Ciphertext must be larger than plaintext due to 12-byte IV + 16-byte GCM tag
        assertTrue(encrypted.size >= plainBytes.size + 28)

        val decrypted = AesGcmCipher.decrypt(encrypted, secretKey)
        val decryptedText = String(decrypted, Charsets.UTF_8)

        assertEquals("Decrypted text must match original plaintext perfectly", sampleText, decryptedText)
    }

    @Test
    fun testTamperedCiphertextThrowsBadTagException() {
        val alice = AesGcmCipher.generateEcKeyPair()
        val bob = AesGcmCipher.generateEcKeyPair()
        val secretKey = AesGcmCipher.deriveSharedKey(alice, bob.public)

        val plainBytes = "Secret message".toByteArray(Charsets.UTF_8)
        val encrypted = AesGcmCipher.encrypt(plainBytes, secretKey)

        // Tamper with one byte in the ciphertext payload
        encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1].toInt() xor 0xFF).toByte()

        try {
            AesGcmCipher.decrypt(encrypted, secretKey)
            fail("Decryption of tampered ciphertext must throw AEADBadTagException")
        } catch (e: Exception) {
            assertTrue(
                "Must fail with security exception due to GCM authentication tag mismatch",
                e is AEADBadTagException || e is GeneralSecurityException
            )
        }
    }

    @Test
    fun testDeterministicFingerprint() {
        val key1 = byteArrayOf(1, 2, 3, 4, 5)
        val key2 = byteArrayOf(6, 7, 8, 9, 10)

        // Fingerprint order must be independent of who called it (Alice or Bob)
        val fp1 = AesGcmCipher.computeFingerprint(key1, key2)
        val fp2 = AesGcmCipher.computeFingerprint(key2, key1)

        assertEquals("Fingerprints must match symmetrically", fp1, fp2)
        assertTrue("Fingerprint must contain formatted chunks", fp1.contains(" · "))
    }
}
