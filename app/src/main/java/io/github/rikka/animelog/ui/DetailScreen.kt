package io.github.rikka.animelog.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.rikka.animelog.data.Entry
import io.github.rikka.animelog.data.MatchState
import io.github.rikka.animelog.data.Status
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(vm: AppViewModel, id: Long, onBack: () -> Unit) {
    val all by vm.entries.collectAsState()
    val e = all.firstOrNull { it.id == id }
    if (e == null) {
        LaunchedEffect(id) { onBack() }
        return
    }
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var dateField by remember { mutableStateOf<String?>(null) } // "start" / "finish"
    val ctx = LocalContext.current

    if (picking) {
        Scaffold(topBar = {
            TopAppBar(
                title = { Text("选择对应的 Bangumi 条目") },
                navigationIcon = { IconButton({ picking = false }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } },
            )
        }) { pad ->
            BgmSearchPanel(vm, e.title, Modifier.padding(pad).fillMaxSize(), header = {
                if (e.bgm != null) item {
                    TextButton({ vm.setBgm(id, null, MatchState.NOT_FOUND); picking = false },
                        Modifier.padding(horizontal = 8.dp)) { Text("取消关联（Bangumi 上没有这部）") }
                }
            }) { info ->
                vm.setBgm(id, info, MatchState.CONFIRMED)
                picking = false
            }
        }
        return
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(e.series, maxLines = 1) },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } },
            actions = { IconButton({ confirmDelete = true }) { Icon(Icons.Outlined.Delete, "删除") } },
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- Bangumi 信息 ----
            Row {
                Cover(e.bgm?.cover, e.series, 96.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val b = e.bgm
                    if (b != null) {
                        Text(b.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (b.name != b.displayName) Text(b.name, style = MaterialTheme.typography.bodySmall)
                        Text(
                            listOfNotNull(b.platform, b.date, b.eps?.let { "$it 集" }, b.minutes?.let { "每集 $it 分钟" })
                                .joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            listOfNotNull(b.studio, b.score?.let { "Bangumi 评分 $it" }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (e.match == MatchState.UNSURE) {
                            Text("自动匹配，不太确定对不对", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary)
                        }
                    } else {
                        Text(if (e.match == MatchState.NOT_FOUND) "Bangumi 上没找到" else "还没关联 Bangumi",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip({ picking = true }, { Text(if (b == null) "关联" else "换一个") })
                        if (b != null && e.match == MatchState.UNSURE) {
                            AssistChip({ vm.setBgm(id, b, MatchState.CONFIRMED) }, { Text("没错") })
                        }
                        if (b != null) {
                            AssistChip({ ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(b.url))) }, { Text("打开") })
                            AssistChip({ vm.refreshBgm(id) }, { Text("刷新") })
                        }
                    }
                }
            }
            if (!e.bgm?.tags.isNullOrEmpty()) {
                Text(e.bgm!!.tags.take(12).joinToString("  "), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()

            // ---- 名称 ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("作品", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(e.series, style = MaterialTheme.typography.bodyLarge)
                }
                TextButton({ renaming = true }) { Text("改名 / 合并") }
            }
            var part by remember(id) { mutableStateOf(e.part) }
            OutlinedTextField(
                part, { part = it; vm.update(id) { x -> x.copy(part = it) } },
                label = { Text("季 / 篇 / 剧场版（可空）") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            // ---- 状态 ----
            Section("状态") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Status.entries.forEach { s -> FilterChip(e.status == s, { vm.setStatus(id, s) }, { Text(s.label) }) }
                }
            }

            // ---- 进度 ----
            Section("进度") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ vm.update(id) { it.copy(progress = (it.progress - 1).coerceAtLeast(0)) } }) {
                        Icon(Icons.Outlined.Remove, "减一集")
                    }
                    // 旧记录看完了但没记进度，按满集显示
                    val shown = if (e.status == Status.DONE && e.totalEps != null) maxOf(e.progress, e.totalEps!!) else e.progress
                    Text("$shown / ${e.totalEps ?: "?"} 集", style = MaterialTheme.typography.titleMedium)
                    IconButton({ vm.plusOne(id) }) { Icon(Icons.Outlined.Add, "加一集") }
                }
            }

            // ---- 评分 ----
            Section("评分：" + (e.rating?.let { "$it / 10  ${ratingWord(it)}" } ?: "未评分")) {
                Slider(
                    value = (e.rating ?: 0).toFloat(), onValueChange = { v ->
                        val r = v.toInt()
                        vm.update(id) { it.copy(rating = r.takeIf { r > 0 }) }
                    }, valueRange = 0f..10f, steps = 9,
                )
            }

            // ---- 日期 ----
            Section("日期") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ dateField = "start" }) { Text("开始：" + (e.startDate ?: "—")) }
                    OutlinedButton({ dateField = "finish" }) { Text("看完：" + (e.finishDate ?: "—")) }
                }
                var yearText by remember(id) { mutableStateOf(e.year?.toString() ?: "") }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    yearText, { t ->
                        yearText = t.filter { it.isDigit() }.take(4)
                        vm.update(id) { it.copy(year = yearText.toIntOrNull()?.takeIf { y -> y in 1900..2100 }) }
                    },
                    label = { Text("统计年份") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("旧记录只有年份；填了看完日期会自动用它的年份") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ---- 重看 ----
            Section("重看次数") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ vm.update(id) { it.copy(rewatch = (it.rewatch - 1).coerceAtLeast(0)) } }) {
                        Icon(Icons.Outlined.Remove, "减")
                    }
                    Text("${e.rewatch}", style = MaterialTheme.typography.titleMedium)
                    IconButton({ vm.update(id) { it.copy(rewatch = it.rewatch + 1) } }) { Icon(Icons.Outlined.Add, "加") }
                }
            }

            // ---- 短评 ----
            var note by remember(id) { mutableStateOf(e.note) }
            OutlinedTextField(
                note, { note = it; vm.update(id) { x -> x.copy(note = it) } },
                label = { Text("短评 / 备注") }, minLines = 3, modifier = Modifier.fillMaxWidth(),
            )

            e.source?.let {
                Text("原始记录：$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    dateField?.let { which ->
        val cur = if (which == "start") e.startDate else e.finishDate
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (cur?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now())
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { dateField = null },
            confirmButton = {
                TextButton({
                    val d = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                    vm.update(id) { x -> setDate(x, which, d) }
                    dateField = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton({ vm.update(id) { x -> setDate(x, which, null) }; dateField = null }) { Text("清除") }
            },
        ) { DatePicker(state) }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("删除「${e.title}」？") },
        text = { Text("删除后不能恢复（除非你之前导出过备份）。") },
        confirmButton = { TextButton({ confirmDelete = false; vm.delete(id); onBack() }) { Text("删除") } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("取消") } },
    )

    if (renaming) RenameSeriesDialog(e.series, onDismiss = { renaming = false }) { vm.renameSeries(e.series, it); renaming = false }
}

private fun setDate(e: Entry, which: String, d: String?): Entry =
    if (which == "start") e.copy(startDate = d)
    else e.copy(finishDate = d, year = d?.take(4)?.toIntOrNull() ?: e.year)

private fun ratingWord(r: Int) = when (r) {
    10 -> "神作"; 9 -> "超好看"; 8 -> "很好看"; 7 -> "不错"; 6 -> "还行"
    5 -> "一般"; 4 -> "较差"; 3 -> "差"; 2 -> "很差"; else -> "不忍直视"
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        content()
    }
}
