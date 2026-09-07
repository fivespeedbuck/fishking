package com.fishking.app

import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Adapted from AssetKing PinCipher: portable HMAC PBKDF2, independent format, streamed payload. */
internal object BackupCrypto {
    private val magic = "FKBACK1".toByteArray()
    private fun key(password: String, salt: ByteArray): SecretKeySpec {
        require(password.length >= 8) { "备份密码至少8个字符" }
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(password.toByteArray(Charsets.UTF_8), "HmacSHA256")) }
        var previous = mac.doFinal(salt + byteArrayOf(0, 0, 0, 1))
        val derived = previous.copyOf()
        repeat(119999) {
            val next = mac.doFinal(previous)
            for (i in derived.indices) derived[i] = (derived[i].toInt() xor next[i].toInt()).toByte()
            previous.fill(0); previous = next
        }
        previous.fill(0)
        return SecretKeySpec(derived, "AES").also { derived.fill(0) }
    }
    private fun read(input: InputStream, size: Int): ByteArray {
        val result = ByteArray(size); var position = 0
        while (position < size) { val count = input.read(result, position, size - position); require(count > 0) { "备份文件不完整" }; position += count }
        return result
    }
    fun encrypt(input: InputStream, output: OutputStream, password: String) {
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes); val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        output.write(magic); output.write(salt); output.write(iv)
        transfer(input, output, cipher)
    }
    fun decrypt(input: InputStream, output: OutputStream, password: String) {
        require(read(input, magic.size).contentEquals(magic)) { "不是咸鱼大王备份" }
        val salt = read(input, 16); val iv = read(input, 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        transfer(input, output, cipher)
    }
    private fun transfer(input: InputStream, output: OutputStream, cipher: Cipher) {
        val buffer = ByteArray(64 * 1024); var total = 0L
        while (true) {
            val count = input.read(buffer); if (count < 0) break
            total += count; require(total <= 2L * 1024 * 1024 * 1024) { "备份超过2GB上限" }
            cipher.update(buffer, 0, count)?.let(output::write)
        }
        output.write(cipher.doFinal())
    }
}
