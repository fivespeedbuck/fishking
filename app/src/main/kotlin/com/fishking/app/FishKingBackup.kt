package com.fishking.app

import android.content.Context
import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.withTransaction
import com.fishking.core.database.FishKingDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Logical SQLite snapshot + original media. Never mixes live DB and stale WAL files. */
class FishKingBackup(private val context: Context, private val database: FishKingDatabase) {
    class Prepared internal constructor(internal val root: File, internal val document: JSONObject, val summary: String)
    private val mediaRoot = File(context.filesDir, "journal_media")
    private val preferences = context.getSharedPreferences("fishking_settings", Context.MODE_PRIVATE)
    private fun work() = File(context.cacheDir, "backup-${UUID.randomUUID()}").apply { check(mkdirs()) }
    private fun tableOrder(): List<String> {
        val sql = database.openHelper.writableDatabase
        val names = sql.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata','room_master_table')").use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        require(names.all { it.matches(Regex("[a-z_]+")) })
        val pending = names.toMutableSet(); val sorted = mutableListOf<String>()
        while (pending.isNotEmpty()) {
            val next = pending.firstOrNull { table -> sql.query("PRAGMA foreign_key_list(`$table`)").use { c ->
                var ready = true; while (c.moveToNext()) if (c.getString(c.getColumnIndexOrThrow("table")) in pending) ready = false; ready
            } } ?: error("备份表依赖不可解析")
            sorted += next; pending -= next
        }
        return sorted
    }
    suspend fun export(uri: Uri, password: String) = withContext(Dispatchers.IO) {
        require(password.length >= 8) { "备份密码至少8个字符" }
        val root = work()
        try {
            val payload = File(root, "payload.zip")
            ZipOutputStream(payload.outputStream().buffered()).use { zip ->
                val document = database.withTransaction {
                    val tables = JSONObject()
                    tableOrder().forEach { table ->
                        val rows = JSONArray()
                        database.openHelper.writableDatabase.query("SELECT * FROM `$table`").use { cursor ->
                            while (cursor.moveToNext()) rows.put(JSONObject().apply { cursor.columnNames.forEachIndexed { index, name ->
                                put(name, when (cursor.getType(index)) {
                                    Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                                    Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                                    Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                                    Cursor.FIELD_TYPE_BLOB -> JSONObject().put("blob", android.util.Base64.encodeToString(cursor.getBlob(index), android.util.Base64.NO_WRAP))
                                    else -> cursor.getString(index)
                                })
                            } })
                        }
                        tables.put(table, rows)
                    }
                    val snapshot = JSONObject().put("format", "fishking-backup-v1").put("schema", database.openHelper.writableDatabase.version)
                        .put("tables", tables).put("settings", JSONObject().put("skin", preferences.getString("skin", "dave"))
                            .put("tags", preferences.getString("tags", "")))
                    // Keep the logical rows and owned media on the same read snapshot.
                    // The settings screen blocks ordinary edits, but background saves
                    // can still race unless the transaction remains open here.
                    val paths = JSONObject(); val assets = tables.getJSONArray("media_assets")
                    for (index in 0 until assets.length()) {
                        val asset = assets.getJSONObject(index)
                        for (column in listOf("privatePath", "previewPath")) {
                            val path = asset.optString(column).takeUnless { it.isBlank() || it == "null" } ?: continue
                            if (paths.has(path)) continue
                            val source = File(path).canonicalFile
                            require(source.parentFile == mediaRoot.canonicalFile) { "媒体不在应用私有目录" }
                            if (!source.isFile) { require(!asset.isNull("pendingDeleteAt")) { "媒体文件缺失，未生成不完整备份" }; continue }
                            val name = "media/${paths.length()}.bin"
                            paths.put(path, name); zip.putNextEntry(ZipEntry(name)); source.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                        }
                    }
                    snapshot.put("media", paths)
                }
                zip.putNextEntry(ZipEntry("data.json")); zip.write(document.toString().toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            val encrypted = File(root, "backup.fkb")
            payload.inputStream().use { input -> encrypted.outputStream().use { BackupCrypto.encrypt(input, it, password) } }
            requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { output -> encrypted.inputStream().use { it.copyTo(output) } }
        } finally { root.deleteRecursively() }
    }
    suspend fun prepare(uri: Uri, password: String): Prepared = withContext(Dispatchers.IO) {
        val root = work()
        try {
            val payload = File(root, "payload.zip")
            requireNotNull(context.contentResolver.openInputStream(uri)).use { input -> payload.outputStream().use { BackupCrypto.decrypt(input, it, password) } }
            val entries = mutableSetOf<String>(); var bytes = 0L
            ZipInputStream(payload.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(entries.add(entry.name) && entries.size <= 10000) { "备份条目重复或过多" }
                    require(entry.name == "data.json" || entry.name.matches(Regex("media/[0-9]+\\.bin"))) { "备份含非法路径" }
                    val file = File(root, entry.name); file.parentFile!!.mkdirs()
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) { val count = zip.read(buffer); if (count < 0) break
                            bytes += count; require(bytes <= 2L * 1024 * 1024 * 1024) { "解包超出大小限制" }; output.write(buffer, 0, count)
                        }
                    }
                    require(entry.name != "data.json" || file.length() <= 64 * 1024 * 1024) { "数据摘要过大" }
                }
            }
            val document = JSONObject(File(root, "data.json").readText())
            require(document.getString("format") == "fishking-backup-v1") { "备份类型不匹配" }
            require(document.getInt("schema") == database.openHelper.writableDatabase.version) { "备份数据版本不同，请使用对应版本应用导入" }
            val tables = document.getJSONObject("tables"); val order = tableOrder()
            require(tables.keys().asSequence().toSet() == order.toSet()) { "备份数据表不完整" }
            val candidate = SQLiteDatabase.openOrCreateDatabase(File(root, "validate.db"), null)
            candidate.use { db ->
                db.execSQL("PRAGMA foreign_keys=ON")
                order.forEach { table -> database.openHelper.writableDatabase.query("SELECT sql FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { c -> check(c.moveToFirst()); db.execSQL(c.getString(0)) } }
                order.forEach { table ->
                    database.openHelper.writableDatabase.query(
                        "SELECT sql FROM sqlite_master WHERE type='index' AND tbl_name=? AND sql IS NOT NULL",
                        arrayOf(table),
                    ).use { cursor -> while (cursor.moveToNext()) db.execSQL(cursor.getString(0)) }
                }
                order.forEach { table ->
                    val columns = db.rawQuery("PRAGMA table_info(`$table`)", null).use { c -> buildSet { while (c.moveToNext()) add(c.getString(1)) } }
                    val rows = tables.getJSONArray(table)
                    for (index in 0 until rows.length()) require(rows.getJSONObject(index).keys().asSequence().toSet() == columns) { "备份字段不完整或不匹配" }
                    insertRows(rows) { values -> db.insertOrThrow(table, null, values) }
                }
                db.rawQuery("PRAGMA foreign_key_check", null).use { require(!it.moveToFirst()) { "备份关联损坏" } }
                db.rawQuery("PRAGMA quick_check", null).use { require(it.moveToFirst() && it.getString(0) == "ok") { "备份结构损坏" } }
            }
            val media = document.getJSONObject("media")
            media.keys().forEach { path ->
                val name = media.getString(path)
                require(name.matches(Regex("media/[0-9]+\\.bin")) && name in entries && File(root, name).isFile) { "备份媒体路径无效或不完整" }
            }
            val assets = tables.getJSONArray("media_assets")
            for (index in 0 until assets.length()) {
                val asset = assets.getJSONObject(index)
                if (asset.isNull("pendingDeleteAt")) require(media.has(asset.getString("privatePath"))) { "备份缺少原始媒体" }
            }
            Prepared(root, document, "待办 ${tables.getJSONArray("todo_occurrences").length()} 条，习惯 ${tables.getJSONArray("habits").length()} 项，日记 ${tables.getJSONArray("journals").length()} 条，媒体 ${media.length()} 个")
        } catch (failure: Throwable) { root.deleteRecursively(); throw failure }
    }
    suspend fun restore(prepared: Prepared, password: String) = withContext(Dispatchers.IO) {
        val rollback = File(context.filesDir, "backups").apply { check(isDirectory || mkdirs()) }
        export(Uri.fromFile(File(rollback, "pre-import-${System.currentTimeMillis()}.fkb")), password)
        val document = JSONObject(prepared.document.toString()); val tables = document.getJSONObject("tables")
        val media = document.getJSONObject("media"); val copied = mutableListOf<File>()
        val oldPreferences = preferences.all
        try {
            mediaRoot.mkdirs(); val remapped = mutableMapOf<String, String>()
            media.keys().forEach { path ->
                val destination = File(mediaRoot, "restored-${UUID.randomUUID()}.${File(path).extension.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) } ?: "bin"}")
                File(prepared.root, media.getString(path)).copyTo(destination)
                copied += destination; remapped[path] = destination.absolutePath
            }
            val assets = tables.getJSONArray("media_assets")
            for (index in 0 until assets.length()) for (column in listOf("privatePath", "previewPath")) {
                val row = assets.getJSONObject(index)
                val original = row.optString(column).takeUnless { it.isBlank() || it == "null" } ?: continue
                val restored = remapped[original]
                when {
                    restored != null -> row.put(column, restored)
                    column == "privatePath" && !row.isNull("pendingDeleteAt") ->
                        row.put(column, File(mediaRoot, "missing-${UUID.randomUUID()}.bin").absolutePath)
                    column == "previewPath" -> row.put(column, JSONObject.NULL)
                }
            }
            database.withTransaction {
                val db = database.openHelper.writableDatabase; val order = tableOrder()
                order.asReversed().forEach { db.execSQL("DELETE FROM `$it`") }
                order.forEach { table -> insertRows(tables.getJSONArray(table)) { values ->
                    check(db.insert(table, SQLiteDatabase.CONFLICT_ABORT, values) != -1L) { "导入 $table 数据失败" }
                } }
                db.query("PRAGMA foreign_key_check").use { require(!it.moveToFirst()) { "导入关联校验失败" } }
                val settings = document.getJSONObject("settings")
                check(preferences.edit().putString("skin", settings.optString("skin", "dave")).putString("tags", settings.optString("tags")).commit())
            }
        } catch (failure: Throwable) {
            preferences.edit().putString("skin", oldPreferences["skin"] as? String ?: "dave").putString("tags", oldPreferences["tags"] as? String ?: "").commit()
            copied.forEach { it.delete() }; throw failure
        } finally { discard(prepared) }
    }
    fun discard(prepared: Prepared) { prepared.root.deleteRecursively() }
    private fun insertRows(rows: JSONArray, insert: (ContentValues) -> Unit) {
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index); val values = ContentValues()
            row.keys().forEach { name -> when (val value = row.get(name)) {
                JSONObject.NULL -> values.putNull(name)
                is JSONObject -> values.put(name, android.util.Base64.decode(value.getString("blob"), android.util.Base64.NO_WRAP))
                is Int -> values.put(name, value)
                is Long -> values.put(name, value)
                is Number -> values.put(name, value.toDouble())
                else -> values.put(name, value.toString())
            } }; insert(values)
        }
    }
}
