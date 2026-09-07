package com.fishking.core.media

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PrivateJournalMediaStoreTest {
    private lateinit var context: Context
    private lateinit var root: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        root = File(context.filesDir, "journal_media")
        root.deleteRecursively()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun importsAPrivateCopyWithSizeAndChecksum() = runBlocking {
        val bytes = "real journal image bytes".encodeToByteArray()
        val source = File(context.cacheDir, "selected.jpg").apply { writeBytes(bytes) }
        val store = PrivateJournalMediaStore(context, newName = { "asset-one" })

        val imported = store.import(Uri.fromFile(source))

        val target = File(imported.privatePath)
        assertTrue(target.isFile)
        assertEquals(root.canonicalFile, target.parentFile?.canonicalFile)
        assertArrayEquals(bytes, target.readBytes())
        assertEquals(bytes.size.toLong(), imported.sizeBytes)
        assertEquals(sha256(bytes), imported.checksum)
        assertArrayEquals(bytes, source.readBytes())
        source.delete()
        Unit
    }

    @Test
    fun deletesOnlyOwnedExistingFiles() = runBlocking {
        val source = File(context.cacheDir, "selected-delete.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val outside = File(context.cacheDir, "outside.bin").apply { writeBytes(byteArrayOf(9)) }
        val store = PrivateJournalMediaStore(context, newName = { "owned" })
        val imported = store.import(Uri.fromFile(source))

        assertFalse(store.delete(outside.absolutePath))
        assertTrue(outside.isFile)
        assertTrue(store.delete(imported.privatePath))
        assertFalse(File(imported.privatePath).exists())
        assertFalse(store.delete(imported.privatePath))
        source.delete()
        outside.delete()
        Unit
    }

    @Test
    fun optionalMetadataFailureDoesNotPreventImport() = runBlocking {
        val uri = Uri.parse("content://broken-metadata/photo.png")
        val bytes = byteArrayOf(1, 2, 3, 4)
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("broken-metadata", object : android.content.ContentProvider() {
            override fun onCreate() = true
            override fun getType(uri: Uri): String = throw IllegalArgumentException("metadata not supported")
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): android.database.Cursor = throw IllegalArgumentException("metadata not supported")
            override fun insert(uri: Uri, values: android.content.ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun update(uri: Uri, values: android.content.ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
        })
        org.robolectric.Shadows.shadowOf(context.contentResolver).registerInputStream(uri, java.io.ByteArrayInputStream(bytes))
        val imported = PrivateJournalMediaStore(context).import(uri)
        assertArrayEquals(bytes, File(imported.privatePath).readBytes())
        assertEquals("image/png", imported.mimeType)
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
