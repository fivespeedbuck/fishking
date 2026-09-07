package com.fishking.core.media

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.media.MediaMetadataRetriever
import java.io.File
import java.io.FileOutputStream
import java.io.FileNotFoundException
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val JOURNAL_MEDIA_DIRECTORY = "journal_media"

data class ImportedJournalMedia(
    val privatePath: String,
    val previewPath: String? = null,
    val mimeType: String,
    val sizeBytes: Long,
    val checksum: String,
    val durationMillis: Long? = null,
)

interface JournalMediaStore {
    suspend fun import(uri: Uri): ImportedJournalMedia
    /** Disposable playback projection; the original photo remains the source of truth. */
    suspend fun motionVideoPath(privatePath: String): String? = null

    /** Deletes only files owned by this store. Returns false for missing or out-of-scope paths. */
    suspend fun delete(privatePath: String): Boolean
}

class PrivateJournalMediaStore(
    context: Context,
    private val newName: () -> String = { UUID.randomUUID().toString() },
) : JournalMediaStore {
    private val resolver: ContentResolver = context.contentResolver
    private val root = File(context.filesDir, JOURNAL_MEDIA_DIRECTORY)
    private val motionCache = File(context.cacheDir, "journal_motion")
    override suspend fun motionVideoPath(privatePath: String): String? = withContext(Dispatchers.IO) {
        val file = File(privatePath).canonicalFile
        if (file.parentFile != root.canonicalFile || !file.isFile) return@withContext null
        runCatching { MotionPhotoReader.extract(file, motionCache)?.absolutePath }.getOrNull()
    }

    override suspend fun import(uri: Uri): ImportedJournalMedia = withContext(Dispatchers.IO) {
        // Picker/cloud providers may allow opening bytes but reject metadata queries.
        val displayName = runCatching { resolver.displayName(uri) }.getOrNull()
        val guessedExtension = (displayName ?: uri.lastPathSegment).orEmpty().substringAfterLast('.', "").lowercase()
        val mimeType = runCatching { resolver.getType(uri) }.getOrNull()?.takeIf(String::isNotBlank)
            ?: android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(guessedExtension)
            ?: when (guessedExtension) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "heic", "heif" -> "image/heif"
                "mp4", "m4v" -> "video/mp4"
                "mov" -> "video/quicktime"
                "webm" -> "video/webm"
                else -> null
            }
            ?: "application/octet-stream"
        val extension = extensionFor(mimeType, displayName)
        check(root.isDirectory || root.mkdirs()) { "Cannot create private media directory" }
        val target = File(root, newName() + extension)
        require(target.canonicalFile.parentFile == root.canonicalFile) { "Invalid media target" }
        try {
            val checksum = copyProviderBytes(uri, target)
            check(target.length() > 0L) { "Selected media is empty or not downloaded" }
            ImportedJournalMedia(
                privatePath = target.absolutePath,
                mimeType = mimeType,
                sizeBytes = target.length(),
                checksum = checksum,
                durationMillis = target.durationMillis(mimeType),
            )
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
    }

    /**
     * OEM galleries and cloud-backed photo pickers do not all implement the same
     * ContentResolver entry point. Try the ordinary stream first, then descriptor
     * and typed-descriptor routes while the picker grant is still alive.
     */
    private fun copyProviderBytes(uri: Uri, target: File): String {
        var firstFailure: Throwable? = null
        fun attempt(copy: (OutputStream) -> Unit): String? {
            val digest = MessageDigest.getInstance("SHA-256")
            return try {
                FileOutputStream(target, false).use { file ->
                    DigestOutputStream(file, digest).use(copy)
                }
                if (target.length() <= 0L) throw FileNotFoundException("Provider returned an empty stream")
                digest.digest().joinToString("") { "%02x".format(it) }
            } catch (error: Throwable) {
                if (error is CancellationException || error is Error) throw error
                if (firstFailure == null) firstFailure = error
                null
            }
        }

        attempt { output ->
            val input = resolver.openInputStream(uri) ?: throw FileNotFoundException("Provider returned no input stream")
            input.use { it.copyTo(output) }
        }?.let { return it }

        attempt { output ->
            val descriptor = resolver.openAssetFileDescriptor(uri, "r")
                ?: throw FileNotFoundException("Provider returned no asset descriptor")
            descriptor.use { asset -> asset.createInputStream().use { it.copyTo(output) } }
        }?.let { return it }

        attempt { output ->
            val descriptor = resolver.openTypedAssetFileDescriptor(uri, "*/*", null)
                ?: throw FileNotFoundException("Provider returned no typed descriptor")
            descriptor.use { asset -> asset.createInputStream().use { it.copyTo(output) } }
        }?.let { return it }

        throw firstFailure ?: FileNotFoundException("Selected media cannot be opened")
    }

    override suspend fun delete(privatePath: String): Boolean = withContext(Dispatchers.IO) {
        val target = File(privatePath).canonicalFile
        val owner = root.canonicalFile
        if (target.parentFile != owner || !target.isFile) return@withContext false
        target.delete()
    }

    private fun ContentResolver.displayName(uri: Uri): String? =
        query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            cursor.getString(0)
        }

    private fun extensionFor(mimeType: String, displayName: String?): String {
        val fromName = displayName?.substringAfterLast('.', "")?.takeIf { it.matches(EXTENSION_PATTERN) }
        if (fromName != null) return ".${fromName.lowercase()}"
        return when (mimeType.lowercase()) {
            "image/jpeg" -> ".jpg"
            "image/png" -> ".png"
            "image/gif" -> ".gif"
            "image/webp" -> ".webp"
            "video/mp4" -> ".mp4"
            "audio/mp4" -> ".m4a"
            else -> ".bin"
        }
    }

    private fun File.durationMillis(mimeType: String): Long? {
        if (!mimeType.startsWith("video/") && !mimeType.startsWith("audio/")) return null
        return runCatching {
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
    }

    private companion object {
        val EXTENSION_PATTERN = Regex("[A-Za-z0-9]{1,8}")
    }
}
