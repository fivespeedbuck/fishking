package com.fishking.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.fishking.core.ui.DavePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SettingsPanel(backup: FishKingBackup, paper: Boolean, tags: String,
    onPaper: (Boolean) -> Unit, onTags: (String) -> Unit, onDismiss: () -> Unit, onDataRestored: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var tagDraft by remember(tags) { mutableStateOf(tags) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var prepared by remember { mutableStateOf<FishKingBackup.Prepared?>(null) }
    var release by remember { mutableStateOf<UpdateChecker.Release?>(null) }
    fun execute(action: suspend () -> String) {
        if (busy) return
        scope.launch { busy = true; status = runCatching { action() }.getOrElse { it.message ?: "操作失败，原数据未覆盖" }; busy = false }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) execute { backup.export(uri, password); "已导出加密备份（包含日记媒体）" }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) execute { prepared?.let(backup::discard); prepared = null; prepared = backup.prepare(uri, password); "校验通过，请核对摘要后确认" }
    }
    DisposableEffect(Unit) { onDispose { prepared?.let(backup::discard) } }
    androidx.compose.ui.window.Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color = DavePalette.Card) {
            Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("设置", style = MaterialTheme.typography.headlineSmall)
                Text("皮肤", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (paper) "纸质笔记本" else "Dave 渐变")
                    Switch(paper, onCheckedChange = onPaper, enabled = !busy)
                }
                OutlinedTextField(tagDraft, { tagDraft = it }, label = { Text("预设 TAG · 空格分隔") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                TextButton(onClick = { onTags(tagDraft); status = "预设 TAG 已保存" }, enabled = !busy) { Text("保存标签") }
                HorizontalDivider()
                Text("数据备份与导入", style = MaterialTheme.typography.titleMedium)
                Text("包含待办、习惯、日记、原始附件和主题/TAG设置。密码不保存，遗忘将无法恢复。", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(password, { password = it }, label = { Text("备份密码（至少8字符）") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy)
                Row {
                    TextButton(onClick = { export.launch("fishking-${java.time.LocalDate.now()}.fkb") }, enabled = !busy && password.length >= 8) { Text("备份/导出") }
                    TextButton(onClick = { import.launch(arrayOf("application/octet-stream", "*/*")) }, enabled = !busy && password.length >= 8) { Text("导入备份") }
                }
                prepared?.let { candidate ->
                    Text(candidate.summary)
                    Text("确认后替换当前数据；替换前会自动保存一份加密回滚备份。损坏文件不会导入。", color = DavePalette.Urgent, style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = { backup.discard(candidate); prepared = null }, enabled = !busy) { Text("取消导入") }
                        TextButton(onClick = { execute { prepared = null; backup.restore(candidate, password); onDataRestored(); "导入完成" } }, enabled = !busy) { Text("确认替换数据") }
                    }
                }
                HorizontalDivider()
                TextButton(onClick = { execute {
                    when (val result = withContext(Dispatchers.IO) { UpdateChecker.fetchLatest() }) {
                        is UpdateChecker.FetchResult.Success -> {
                            if (UpdateChecker.isNewer(result.release.tag, BuildConfig.VERSION_NAME)) { release = result.release; "可升级到 ${result.release.tag}" }
                            else "当前已是最新版本"
                        }
                        is UpdateChecker.FetchResult.Failure -> result.message
                    }
                } }, enabled = !busy) { Text("检查 GitHub 更新") }
                release?.let { available -> TextButton(onClick = { execute {
                    val apk = UpdateInstaller.download(context, available) { bytes, total -> scope.launch { status = "下载 ${bytes * 100 / total.coerceAtLeast(1)}%" } }
                    if (UpdateInstaller.canRequestPackageInstalls(context)) { UpdateInstaller.launchSystemInstaller(context, apk.file); "已交给系统安装器确认" }
                    else { context.startActivity(UpdateInstaller.unknownSourcesIntent(context)); "请授权后再次点击更新" }
                } }, enabled = !busy) { Text("下载并更新 ${available.tag}") } }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onDismiss, enabled = !busy) { Text("返回") }
            }
        }
    }
}
