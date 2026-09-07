package com.fishking.core.media

import java.io.File
import java.io.RandomAccessFile

internal data class MotionVideoRange(val offset: Long, val length: Long)

/** Reads an embedded MP4/MOV, never infers motion from the filename alone.
 * https://developer.android.com/media/platform/motion-photo-format
 */
internal object MotionPhotoReader {
    fun find(file: File): MotionVideoRange? = runCatching {
        RandomAccessFile(file, "r").use { input ->
            val size = input.length()
            if (size < 32) return@use null
            val header = ByteArray(minOf(size, 2L * 1024 * 1024).toInt())
            input.readFully(header)
            val jpeg = header[0] == 0xff.toByte() && header[1] == 0xd8.toByte()
            val isoImage = header.size >= 12 && String(header, 4, 4, Charsets.ISO_8859_1) == "ftyp" &&
                String(header, 8, 4, Charsets.ISO_8859_1) in setOf("heic", "heix", "heif", "mif1", "msf1", "avif", "avis")
            if (!jpeg && !isoImage) return@use null
            val text = header.toString(Charsets.ISO_8859_1)
            val flag = Regex("(?:[A-Za-z0-9_]+:)?MotionPhoto\\s*=\\s*[\"'](-?\\d+)[\"']").find(text)?.groupValues?.get(1)?.toIntOrNull()
            if (flag != null && flag != 1) return@use null
            val directoryLength = Regex("<[^>]+>").findAll(text).mapNotNull { tag ->
                if (!Regex("(?:[A-Za-z0-9_]+:)?Semantic\\s*=\\s*[\"']MotionPhoto[\"']").containsMatchIn(tag.value)) null
                else Regex("(?:[A-Za-z0-9_]+:)?Length\\s*=\\s*[\"'](\\d+)[\"']").find(tag.value)?.groupValues?.get(1)?.toLongOrNull()
            }.firstOrNull()
            // Legacy MicroVideo uses distance from EOF; modern MotionPhoto metadata takes priority.
            val oldOffset = if (flag == null) Regex("(?:[A-Za-z0-9_]+:)?MicroVideoOffset\\s*=\\s*[\"'](\\d+)[\"']")
                .find(text)?.groupValues?.get(1)?.toLongOrNull() else null
            (directoryLength ?: oldOffset)?.takeIf { it in 24 until size }?.let { length ->
                validatedVideo(input, size - length, size)?.let { return@use it }
            }
            // Vendor JPEGs may omit standard XMP. Only accept a structurally valid appended video.
            val buffer = ByteArray(64 * 1024)
            var position = 4L
            while (position < size) {
                input.seek(position)
                val count = input.read(buffer)
                if (count < 4) break
                for (index in 0..count - 4) {
                    if (buffer[index] == 'f'.code.toByte() && buffer[index + 1] == 't'.code.toByte() &&
                        buffer[index + 2] == 'y'.code.toByte() && buffer[index + 3] == 'p'.code.toByte()) {
                        val start = position + index - 4
                        if (start > 0) validatedVideo(input, start, size)?.let { return@use it }
                    }
                }
                position += (count - 3).coerceAtLeast(1)
            }
            null
        }
    }.getOrNull()

    private fun validatedVideo(input: RandomAccessFile, start: Long, end: Long): MotionVideoRange? {
        if (start < 0 || start + 16 > end) return null
        input.seek(start + 8)
        val brand = ByteArray(4).also(input::readFully).toString(Charsets.ISO_8859_1)
        if (brand !in setOf("isom", "iso2", "iso4", "iso5", "iso6", "mp41", "mp42", "avc1", "M4V ", "qt  ")) return null
        var position = start
        var moov = false
        var mdat = false
        var boxes = 0
        while (position + 8 <= end && boxes++ < 10000) {
            input.seek(position)
            var length = input.readInt().toLong() and 0xffffffffL
            val type = ByteArray(4).also(input::readFully).toString(Charsets.ISO_8859_1)
            val header = if (length == 1L) 16L else 8L
            if (length == 1L) { if (position + 16 > end) break; length = input.readLong() }
            if (length == 0L) length = end - position
            if (length < header || length > end - position) break
            if (position == start && type != "ftyp") return null
            if (type !in setOf("ftyp", "moov", "mdat", "free", "skip", "wide", "uuid", "meta", "moof", "mfra", "sidx", "styp")) break
            moov = moov || type == "moov"
            mdat = mdat || type == "mdat"
            position += length
        }
        return if (moov && mdat) MotionVideoRange(start, position - start) else null
    }

    fun extract(source: File, cache: File): File? {
        val range = find(source) ?: return null
        if (!cache.isDirectory && !cache.mkdirs()) return null
        val id = java.security.MessageDigest.getInstance("SHA-256")
            .digest("${source.canonicalPath}:${source.length()}:${source.lastModified()}:${range.offset}".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val target = File(cache, "$id.mp4")
        synchronized(this) {
            if (target.isFile && target.length() == range.length) return target
            val temporary = File.createTempFile("motion-", ".part", cache)
            try {
                RandomAccessFile(source, "r").use { input ->
                    input.seek(range.offset)
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var remaining = range.length
                        while (remaining > 0) {
                            val count = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                            check(count > 0) { "Incomplete motion photo" }
                            output.write(buffer, 0, count); remaining -= count
                        }
                    }
                }
                check(temporary.renameTo(target)) { "Cannot cache motion photo video" }
            } finally { temporary.delete() }
        }
        return target
    }
}
