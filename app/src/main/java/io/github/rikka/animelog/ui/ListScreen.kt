package io.github.rikka.animelog.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.rikka.animelog.data.Entry
import io.github.rikka.animelog.data.Status

enum class GroupMode(val label: String) { YEAR("按年份"), SERIES("按作品"), FLAT("不分组") }

private sealed interface Row_ {
    data class Header(val key: String, val title: String, val sub: String, val series: String? = null) : Row_
    data class Item(val e: Entry, val full: Boolean = true) : Row_
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ListScreen(vm: AppViewModel, modifier: Modifier, open: (Page) -> Unit) {
    val all by vm.entries.collectAsState()
    var filter by rememberSaveable { mutableStateOf<Status?>(null) }
    var group by rememberSaveable { mutableStateOf(GroupMode.YEAR) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var groupMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    val shown = remember(all, filter, query) {
        val q = query.trim().lowercase()
        all.filter { e ->
            (filter == null || e.status == filter) && (q.isEmpty() ||
                e.title.lowercase().contains(q) || e.note.lowercase().contains(q) ||
                e.bgm?.nameCn?.lowercase()?.contains(q) == true || e.bgm?.name?.lowercase()?.contains(q) == true)
        }
    }
    val rows = remember(shown, group) { buildRows(shown, group) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        TextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            placeholder = { Text("搜索标题、备注") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else Text("六花追番")
                },
                actions = {
                    IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                        Icon(if (searching) Icons.Outlined.Close else Icons.Outlined.Search, "搜索")
                    }
                    IconButton(onClick = { groupMenu = true }) { Icon(Icons.Outlined.ViewAgenda, "分组") }
                    DropdownMenu(groupMenu, { groupMenu = false }) {
                        GroupMode.entries.forEach { g ->
                            DropdownMenuItem(
                                text = { Text(if (g == group) "✓ ${g.label}" else g.label) },
                                onClick = { group = g; groupMenu = false },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { open(Page.Add) }) { Icon(Icons.Outlined.Add, "添加") }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(filter == null, { filter = null }, { Text("全部 ${all.size}") })
                Status.entries.forEach { s ->
                    val n = all.count { it.status == s }
                    if (n > 0 || s == Status.WATCHING || s == Status.WANT) {
                        FilterChip(filter == s, { filter = if (filter == s) null else s }, { Text("${s.label} $n") })
                    }
                }
            }
            if (rows.isEmpty()) {
                Text(
                    if (query.isNotBlank()) "没有找到「$query」" else "这里还没有东西",
                    Modifier.padding(32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 88.dp)) {
                items(rows, key = {
                    when (it) {
                        is Row_.Header -> "h:" + it.key
                        is Row_.Item -> "e:${it.e.id}"
                    }
                }) { r ->
                    when (r) {
                        is Row_.Header -> Surface(color = MaterialTheme.colorScheme.surface) {
                            Row(
                                Modifier.fillMaxWidth()
                                    .combinedClickable(onClick = {}, onLongClick = { r.series?.let { renaming = it } })
                                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                Text(r.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.width(8.dp))
                                Text(r.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        is Row_.Item -> EntryRow(r.e, showSeries = r.full,
                            onClick = { open(Page.Detail(r.e.id)) }, onPlus = { vm.plusOne(r.e.id) })
                    }
                }
            }
        }
    }

    renaming?.let { old -> RenameSeriesDialog(old, onDismiss = { renaming = null }) { vm.renameSeries(old, it); renaming = null } }
}

@Composable
fun RenameSeriesDialog(old: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf(old) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("改作品名") },
        text = {
            Column {
                Text("这个作品下的所有条目都会改。改成另一个已有作品的名字，就会合并到一起。",
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.padding(4.dp))
                OutlinedTextField(text, { text = it }, singleLine = true)
            }
        },
        confirmButton = { TextButton({ onOk(text) }) { Text("确定") } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    )
}

@Composable
private fun EntryRow(e: Entry, showSeries: Boolean, onClick: () -> Unit, onPlus: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(e.bgm?.cover, e.series, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (showSeries) e.title else e.part.ifBlank { e.series },
                style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            val sub = e.subtitle()
            if (sub.isNotEmpty()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (e.status == Status.DONE) MaterialTheme.colorScheme.onSurfaceVariant else statusColor(e.status))
            }
        }
        if (e.status == Status.WATCHING) {
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onPlus, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("+1") }
        }
    }
    HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

private fun buildRows(list: List<Entry>, mode: GroupMode): List<Row_> = when (mode) {
    GroupMode.FLAT -> list.sortedByDescending { it.order }.map { Row_.Item(it) }
    GroupMode.YEAR -> {
        // 没年份的（在看/想看）放最前
        val groups = list.groupBy { it.statYear }.entries
            .sortedWith(compareBy<Map.Entry<Int?, List<Entry>>> { it.key != null }.thenByDescending { it.key })
        groups.flatMap { (y, l) ->
            val works = l.map { it.series }.distinct().size
            listOf(Row_.Header("y$y", y?.let { "$it 年" } ?: "进行中 / 未完成", "${l.size} 条 · $works 部")) +
                l.sortedByDescending { it.order }.map { Row_.Item(it) }
        }
    }
    GroupMode.SERIES -> list.groupBy { it.series }.entries
        .sortedByDescending { g -> g.value.maxOf { it.order } }
        .flatMap { (s, l) ->
            // 只有一条的作品不单独加标题
            if (l.size == 1) listOf(Row_.Item(l[0]))
            else listOf(Row_.Header("s$s", s, "${l.size} 条", series = s)) +
                l.sortedBy { it.order }.map { Row_.Item(it, full = false) }
        }
}
