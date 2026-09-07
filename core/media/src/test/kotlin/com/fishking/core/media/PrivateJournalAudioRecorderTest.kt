package com.fishking.core.media

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PrivateJournalAudioRecorderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun stopReturnsPrivateM4aWithRealSizeAndChecksum() = runBlocking {
        val backend = FakeAudioRecorderBackend(bytes = "voice".toByteArray())
        val recorder = PrivateJournalAudioRecorder(context, newName = { "recording" }, backendFactory = { backend })

        recorder.start()
        val result = recorder.stop()

        assertTrue(result.privatePath.endsWith("journal_media${File.separator}recording.m4a"))
        assertEquals("audio/mp4", result.mimeType)
        assertEquals(5L, result.sizeBytes)
        assertEquals(sha256("voice".toByteArray()), result.checksum)
        assertTrue(backend.stopped)
        assertTrue(backend.released)
    }

    @Test
    fun cancelDeletesPartialRecording() {
        val backend = FakeAudioRecorderBackend(bytes = "partial".toByteArray())
        val recorder = PrivateJournalAudioRecorder(context, newName = { "cancelled" }, backendFactory = { backend })

        recorder.start()
        val file = requireNotNull(backend.outputPath).let(::File)
        assertTrue(file.isFile)

        recorder.cancel()

        assertFalse(file.exists())
        assertTrue(backend.released)
    }

    @Test
    fun startFailureDeletesPartialRecordingAndReleasesBackend() {
        val backend = FakeAudioRecorderBackend(bytes = "broken".toByteArray(), failOnStart = true)
        val recorder = PrivateJournalAudioRecorder(context, newName = { "failed" }, backendFactory = { backend })

        runCatching { recorder.start() }

        assertFalse(File(context.filesDir, "journal_media${File.separator}failed.m4a").exists())
        assertTrue(backend.released)
    }
}

private class FakeAudioRecorderBackend(
    private val bytes: ByteArray,
    private val failOnStart: Boolean = false,
) : AudioRecorderBackend {
    var outputPath: String? = null
    var stopped = false
    var released = false

    override fun prepareAndStart(outputPath: String) {
        this.outputPath = outputPath
        File(outputPath).writeBytes(bytes)
        if (failOnStart) error("microphone unavailable")
    }

    override fun stop() {
        stopped = true
    }

    override fun release() {
        released = true
    }
}

private fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
