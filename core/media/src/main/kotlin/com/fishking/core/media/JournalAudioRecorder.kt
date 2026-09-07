package com.fishking.core.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import java.io.File
import java.io.FileInputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface JournalAudioRecorder {
    /** Starts a foreground microphone recording. The caller must already hold RECORD_AUDIO. */
    fun start()

    /** Stops the active recording and returns the completed private media file. */
    suspend fun stop(): ImportedJournalMedia

    /** Cancels the active recording and removes its partial file. */
    fun cancel()
}

class PrivateJournalAudioRecorder(
    context: Context,
    private val newName: () -> String = { UUID.randomUUID().toString() },
    private val backendFactory: () -> AudioRecorderBackend = { AndroidAudioRecorderBackend() },
) : JournalAudioRecorder {
    private val root = File(context.filesDir, JOURNAL_MEDIA_DIRECTORY)
    private var active: ActiveRecording? = null

    @Synchronized
    override fun start() {
        check(active == null) { "A journal recording is already active" }
        root.mkdirs()
        val target = File(root, "${newName()}.m4a")
        require(target.canonicalFile.parentFile == root.canonicalFile) { "Invalid recording target" }
        val backend = backendFactory()
        try {
            backend.prepareAndStart(target.absolutePath)
            active = ActiveRecording(backend, target)
        } catch (error: Throwable) {
            runCatching { backend.release() }
            target.delete()
            throw error
        }
    }

    override suspend fun stop(): ImportedJournalMedia = withContext(Dispatchers.IO) {
        val recording = takeActive()
        try {
            recording.backend.stop()
        } catch (error: Throwable) {
            recording.file.delete()
            throw error
        } finally {
            runCatching { recording.backend.release() }
        }
        try {
            check(recording.file.isFile && recording.file.length() > 0L) { "Recording produced no audio" }
            ImportedJournalMedia(
                privatePath = recording.file.absolutePath,
                mimeType = AUDIO_MIME_TYPE,
                sizeBytes = recording.file.length(),
                checksum = recording.file.sha256(),
                durationMillis = recording.file.durationMillis(),
            )
        } catch (error: Throwable) {
            recording.file.delete()
            throw error
        }
    }

    @Synchronized
    override fun cancel() {
        val recording = active ?: return
        active = null
        runCatching { recording.backend.stop() }
        runCatching { recording.backend.release() }
        recording.file.delete()
    }

    @Synchronized
    private fun takeActive(): ActiveRecording =
        active?.also { active = null } ?: error("No journal recording is active")

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(this).use { source ->
            DigestInputStream(source, digest).use { checked ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (checked.read(buffer) >= 0) Unit
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun File.durationMillis(): Long? = runCatching {
        // MediaMetadataRetriever.close()/use() is only available from API 29.
        // release() keeps duration probing compatible with the app's minSdk 26.
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } finally {
            retriever.release()
        }
    }.getOrNull()

    private data class ActiveRecording(
        val backend: AudioRecorderBackend,
        val file: File,
    )

    private companion object {
        const val AUDIO_MIME_TYPE = "audio/mp4"
    }
}

interface AudioRecorderBackend {
    fun prepareAndStart(outputPath: String)
    fun stop()
    fun release()
}

@Suppress("DEPRECATION")
private class AndroidAudioRecorderBackend : AudioRecorderBackend {
    private val recorder = MediaRecorder()

    override fun prepareAndStart(outputPath: String) {
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        recorder.setAudioEncodingBitRate(128_000)
        recorder.setAudioSamplingRate(44_100)
        recorder.setOutputFile(outputPath)
        recorder.prepare()
        recorder.start()
    }

    override fun stop() = recorder.stop()
    override fun release() = recorder.release()
}
