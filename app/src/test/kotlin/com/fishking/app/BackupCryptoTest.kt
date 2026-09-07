package com.fishking.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCryptoTest {
    @Test
    fun encryptedBackupRoundTripsWithoutExposingPlaintext() {
        val marker = "journal-secret-marker".toByteArray()
        val source = marker + ByteArray(192 * 1024) { index -> (index * 31).toByte() }
        val encrypted = ByteArrayOutputStream().also { output ->
            BackupCrypto.encrypt(ByteArrayInputStream(source), output, PASSWORD)
        }.toByteArray()

        assertFalse(encrypted.toString(Charsets.ISO_8859_1).contains("journal-secret-marker"))
        val restored = ByteArrayOutputStream().also { output ->
            BackupCrypto.decrypt(ByteArrayInputStream(encrypted), output, PASSWORD)
        }.toByteArray()

        assertArrayEquals(source, restored)
    }

    @Test
    fun wrongPasswordAndTamperingAreRejected() {
        val encrypted = ByteArrayOutputStream().also { output ->
            BackupCrypto.encrypt(ByteArrayInputStream("private journal".toByteArray()), output, PASSWORD)
        }.toByteArray()

        assertThrows(Throwable::class.java) {
            BackupCrypto.decrypt(ByteArrayInputStream(encrypted), ByteArrayOutputStream(), "wrong-password")
        }

        val tampered = encrypted.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertThrows(Throwable::class.java) {
            BackupCrypto.decrypt(ByteArrayInputStream(tampered), ByteArrayOutputStream(), PASSWORD)
        }
    }

    private companion object {
        const val PASSWORD = "fishking-test-password"
    }
}
