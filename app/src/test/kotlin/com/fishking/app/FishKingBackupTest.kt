package com.fishking.app

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fishking.core.database.FishKingDatabase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FishKingBackupTest {
    private lateinit var context: Context
    private lateinit var database: FishKingDatabase
    private lateinit var backup: FishKingBackup

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, FishKingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        backup = FishKingBackup(context, database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun exportThenRestoreRoundTripsDatabaseMediaAndSettings() = runBlocking {
        val mediaRoot = File(context.filesDir, "journal_media").apply { mkdirs() }
        val sourceMedia = File(mediaRoot, "backup-source.jpg").apply { writeBytes("image-body".toByteArray()) }
        val db = database.openHelper.writableDatabase
        db.execSQL(
            """INSERT INTO todo_occurrences
                (id, seriesId, seriesVersionId, nominalDate, displayDate, title, priority,
                 accentColor, status, completedAt, position, isSeriesException, deletedAt,
                 createdAt, updatedAt, planScope, planDeadline)
                VALUES ('todo-backup', NULL, NULL, 20000, 20000, '备份待办', 'NORMAL',
                 NULL, 'OPEN', NULL, 0, 0, NULL, 10, 10, 'MONTH', 20020)""",
        )
        db.execSQL("INSERT INTO journals (id, entryDate, locationName, latitude, longitude, createdAt, updatedAt, entryTime, deletedAt) VALUES ('journal-backup', 20000, '深圳', NULL, NULL, 10, 10, '09:10', NULL)")
        db.execSQL("INSERT INTO journal_blocks VALUES ('block-backup', 'journal-backup', 0, 'IMAGE_ROW', NULL, NULL, NULL, NULL, 10, 10)")
        db.execSQL("INSERT INTO media_assets VALUES ('asset-backup', ?, NULL, 'image/jpeg', ?, 'checksum', NULL, NULL, 10)", arrayOf(sourceMedia.absolutePath, sourceMedia.length()))
        db.execSQL("INSERT INTO journal_block_media_cross_ref VALUES ('block-backup', 'asset-backup', 0)")
        context.getSharedPreferences("fishking_settings", Context.MODE_PRIVATE).edit()
            .putString("skin", "paper").putString("tags", "工作 健康").commit()

        val archive = File(context.cacheDir, "fishking-roundtrip.fkb")
        backup.export(Uri.fromFile(archive), PASSWORD)
        assertTrue(archive.length() > 0)

        db.execSQL("DELETE FROM journal_block_media_cross_ref")
        db.execSQL("DELETE FROM journal_blocks")
        db.execSQL("DELETE FROM media_assets")
        db.execSQL("DELETE FROM journals")
        db.execSQL("DELETE FROM todo_occurrences")
        context.getSharedPreferences("fishking_settings", Context.MODE_PRIVATE).edit()
            .putString("skin", "dave").putString("tags", "临时").commit()

        val prepared = backup.prepare(Uri.fromFile(archive), PASSWORD)
        backup.restore(prepared, PASSWORD)

        db.query("SELECT title, planScope, planDeadline FROM todo_occurrences WHERE id='todo-backup'").use {
            it.moveToFirst(); assertEquals("备份待办", it.getString(0)); assertEquals("MONTH", it.getString(1)); assertEquals(20020L, it.getLong(2))
        }
        val restoredPath = db.query("SELECT privatePath FROM media_assets WHERE id='asset-backup'").use {
            it.moveToFirst(); it.getString(0)
        }
        assertTrue(File(restoredPath).canonicalFile.parentFile == mediaRoot.canonicalFile)
        assertEquals("image-body", File(restoredPath).readText())
        val settings = context.getSharedPreferences("fishking_settings", Context.MODE_PRIVATE)
        assertEquals("paper", settings.getString("skin", null))
        assertEquals("工作 健康", settings.getString("tags", null))
    }

    @Test
    fun prepareRejectsRowsThatViolateLiveUniqueIndexes() = runBlocking {
        val valid = File(context.cacheDir, "fishking-valid.fkb")
        backup.export(Uri.fromFile(valid), PASSWORD)
        val document = readArchive(valid).also { entries ->
            val json = JSONObject(entries.getValue("data.json").toString(Charsets.UTF_8))
            val tags = json.getJSONObject("tables").getJSONArray("tags")
            tags.put(JSONObject().put("id", "tag-a").put("name", "工作").put("normalizedName", "工作").put("createdAt", 1))
            tags.put(JSONObject().put("id", "tag-b").put("name", "工作重复").put("normalizedName", "工作").put("createdAt", 2))
            entries["data.json"] = json.toString().toByteArray()
        }
        val forged = File(context.cacheDir, "fishking-duplicate-tag.fkb")
        writeArchive(forged, document)

        assertThrows(Throwable::class.java) {
            runBlocking { backup.prepare(Uri.fromFile(forged), PASSWORD) }
        }
        Unit
    }

    private fun readArchive(file: File): MutableMap<String, ByteArray> {
        val zipBytes = ByteArrayOutputStream().also { output ->
            file.inputStream().use { BackupCrypto.decrypt(it, output, PASSWORD) }
        }.toByteArray()
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }
        return entries
    }

    private fun writeArchive(file: File, entries: Map<String, ByteArray>) {
        val zipBytes = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip -> entries.forEach { (name, value) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(value); zip.closeEntry()
            } }
        }.toByteArray()
        file.outputStream().use { output -> BackupCrypto.encrypt(ByteArrayInputStream(zipBytes), output, PASSWORD) }
    }

    private companion object {
        const val PASSWORD = "fishking-backup-test"
    }
}
