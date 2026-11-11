package io.github.rikka.animelog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.rikka.animelog.data.Status

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScreen(vm: AppViewModel, onBack: () -> Unit, onCreated: (Long) -> Unit) {
    var status by remember { mutableStateOf(Status.WATCHING) }
    var manual by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("添加") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } },
            actions = { TextButton({ manual = true }) { Text("手动添加") } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Status.WATCHING, Status.WANT, Status.DONE).forEach { s ->
                    FilterChip(status == s, { status = s }, { Text("加到「${s.label}」") })
                }
            }
            BgmSearchPanel(vm, "", Modifier.fillMaxSize(), autoSearch = false) { info ->
                val (series, part) = vm.guessSeries(info.displayName)
                onCreated(vm.create(series, part, status, info))
            }
        }
    }

    if (manual) {
        var series by remember { mutableStateOf("") }
        var part by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { manual = false },
            title = { Text("手动添加") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("不在 Bangumi 上的（比如 AI 做的）可以手动加，之后也能再关联。",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(series, { series = it }, label = { Text("作品名") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(part, { part = it }, label = { Text("季 / 篇（可空）") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton({
                    if (series.isNotBlank()) { manual = false; onCreated(vm.create(series, part, status, null)) }
                }) { Text("添加") }
            },
            dismissButton = { TextButton({ manual = false }) { Text("取消") } },
        )
    }
}
