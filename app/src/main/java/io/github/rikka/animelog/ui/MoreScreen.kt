package io.github.rikka.animelog.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.rikka.animelog.data.MatchState
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MoreScreen(vm: AppViewModel, modifier: Modifier, open: (Page) -> Unit) {
    val all by vm.entries.collectAsState()
    val batch by vm.batch.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.exportTo(it, csv = false) }
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { vm.exportTo(it, csv = true) }
    }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        confirmImport = uri
    }

    val counts = MatchState.entries.associateWith { s -> all.count { it.match == s } }

    Scaffold(modifier = modifier, topBar = { TopAppBar(title = { Text("更多") }) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Bangumi 匹配", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "按名字去 bgm.tv 找对应的条目，补上封面、集数、类型、制作公司。" +
                            "统计里的总集数、时长、标签都靠它。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "未匹配 ${counts[MatchState.NONE]} · 确定 ${(counts[MatchState.AUTO] ?: 0) + (counts[MatchState.CONFIRMED] ?: 0)}" +
                            " · 不确定 ${counts[MatchState.UNSURE]} · 没找到 ${counts[MatchState.NOT_FOUND]}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    batch?.let { b ->
                        if (b.total > 0) LinearProgressIndicator(
                            progress = { b.done.toFloat() / b.total }, modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            when {
                                b.error != null -> b.error
                                b.running -> "${b.done}/${b.total}  ${b.current}"
                                else -> "完成 ${b.done}/${b.total}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (b.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (batch?.running == true) {
                            OutlinedButton(vm::stopBatch) { Text("暂停") }
                        } else {
                            Button({ vm.startBatch(retryFailed = false) }) { Text("开始匹配") }
                            if ((counts[MatchState.NOT_FOUND] ?: 0) > 0) {
                                OutlinedButton({ vm.startBatch(retryFailed = true) }) { Text("重试没找到的") }
                            }
                        }
                        OutlinedButton({ open(Page.Review) }) { Text("检查匹配结果") }
                    }
                    Text("四百多条大概要几分钟，可以切到别的页面，别关掉 App。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("备份", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("数据只存在这台手机上。换机、卸载前记得导出 JSON 备份。", style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ exportJson.launch("六花追番备份-${LocalDate.now()}.json") }) { Text("导出备份") }
                        OutlinedButton({ importJson.launch(arrayOf("application/json", "*/*")) }) { Text("从备份恢复") }
                        OutlinedButton({ exportCsv.launch("六花追番-${LocalDate.now()}.csv") }) { Text("导出表格 CSV") }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("最初的记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("首次打开时从「动漫记录表」导入了 2024–2026 年的记录。改乱了可以恢复成最初那份（会丢掉之后的所有修改）。",
                        style = MaterialTheme.typography.bodySmall)
                    TextButton({ confirmReset = true }) { Text("恢复成最初导入的记录") }
                }
            }
        }
    }

    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("恢复最初记录？") },
        text = { Text("现在的 ${all.size} 条记录、评分、短评和 Bangumi 匹配都会被替换掉。建议先导出备份。") },
        confirmButton = { TextButton({ confirmReset = false; vm.resetToSeed() }) { Text("恢复") } },
        dismissButton = { TextButton({ confirmReset = false }) { Text("取消") } },
    )
    confirmImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            title = { Text("从备份恢复？") },
            text = { Text("现在的 ${all.size} 条记录会被备份文件里的内容替换。") },
            confirmButton = { TextButton({ confirmImport = null; vm.importFrom(uri) }) { Text("恢复") } },
            dismissButton = { TextButton({ confirmImport = null }) { Text("取消") } },
        )
    }
}
