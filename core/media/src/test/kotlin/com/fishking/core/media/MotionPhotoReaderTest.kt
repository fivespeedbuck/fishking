package com.fishking.core.media

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.ByteBuffer

class MotionPhotoReaderTest {
    @get:Rule val folder = TemporaryFolder()
    private fun box(type: String, payload: ByteArray = byteArrayOf()) =
        ByteBuffer.allocate(8 + payload.size).putInt(8 + payload.size).put(type.toByteArray()).put(payload).array()
    private fun video() = box("ftyp", "isom0000".toByteArray()) + box("moov") + box("mdat", byteArrayOf(1, 2, 3, 4))
    private fun photo(metadata: String, tail: ByteArray): java.io.File = folder.newFile().apply {
        writeBytes(byteArrayOf(0xff.toByte(), 0xd8.toByte()) + metadata.toByteArray() + byteArrayOf(0xff.toByte(), 0xd9.toByte()) + tail)
    }

    @Test fun readsStandardMotionPhotoAndExtractsOnlyVideoWithoutChangingOriginal() {
        val video = video()
        val file = photo("<x Camera:MotionPhoto='1'/><Container:Item Item:Semantic='MotionPhoto' Item:Length='${video.size}' Item:Mime='video/mp4'/>", video)
        val before = file.readBytes()
        val range = MotionPhotoReader.find(file)!!
        assertEquals(video.size.toLong(), range.length)
        assertEquals(file.length() - video.size, range.offset)
        val extracted = MotionPhotoReader.extract(file, folder.newFolder())!!
        assertArrayEquals(video, extracted.readBytes())
        assertArrayEquals(before, file.readBytes())
    }
    @Test fun supportsLegacyMicroVideoOffsetAndVendorEmbeddedVideo() {
        val video = video()
        assertNotNull(MotionPhotoReader.find(photo("<x GCamera:MicroVideoOffset='${video.size}'/>", video)))
        assertNotNull(MotionPhotoReader.find(photo("vendor still", video)))
    }
    @Test fun strippedOrOptedOutPhotosNeverShowMotionButton() {
        assertNull(MotionPhotoReader.find(photo("<x Camera:MotionPhoto='1'/>", byteArrayOf())))
        assertNull(MotionPhotoReader.find(photo("<x Camera:MotionPhoto='0'/>", video())))
    }
    @Test fun truncatedVideoAndOutOfRangeMetadataAreRejected() {
        assertNull(MotionPhotoReader.find(photo("<x Camera:MotionPhoto='1'/><Item Semantic='MotionPhoto' Length='999999999'/>", box("ftyp", "isom0000".toByteArray()))))
        assertNull(MotionPhotoReader.find(photo("", video().dropLast(2).toByteArray())))
    }
    @Test fun ordinaryVideoIsNotMistakenForPhoto() {
        val file = folder.newFile().apply { writeBytes(video()) }
        assertNull(MotionPhotoReader.find(file))
    }
}
