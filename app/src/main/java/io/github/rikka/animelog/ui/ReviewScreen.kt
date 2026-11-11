package io.github.rikka.animelog.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.rikka.animelog.data.MatchState

/** 检查自动匹配的结果：不确定的、没找到的优先。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(vm: AppViewModel, onBack: () -> Unit, open: (Page) -> Unit) {
    val all by vm.entries.collectAsState()
    val tabs = listOf(MatchState.UNSURE to "不确定", MatchState.NOT_FOUND to "没找到", MatchState.AUTO to "自动匹配")
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val list = all.filter { it.match == tabs[tab].first }.sortedBy { it.order }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("检查匹配结果") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, (s, label) ->
                    Tab(tab == i, { tab = i }, text = { Text("$label ${all.count { it.match == s }}") })
                }
            }
            Text(
                when (tabs[tab].first) {
                    MatchState.UNSURE -> "名字差得比较多的。对的点 ✓，不对的点进去换一个。"
                    MatchState.NOT_FOUND -> "Bangumi 上没搜到。点进去换个叫法搜，或者就不关联了。"
                    else -> "把握比较大的，有空扫一眼就行。"
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            LazyColumn {
                items(list, key = { it.id }) { e ->
                    Row(
                        Modifier.fillMaxWidth().clickable { open(Page.Detail(e.id)) }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Cover(e.bgm?.cover, e.series, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                e.bgm?.let { "→ ${it.displayName}" + (it.platform?.let { p -> " · $p" } ?: "") + (it.date?.let { d -> " · ${d.take(4)}" } ?: "") }
                                    ?: "→ 无",
                                style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (e.bgm != null && e.match != MatchState.CONFIRMED) {
                            IconButton({ vm.setBgm(e.id, e.bgm, MatchState.CONFIRMED) }) { Icon(Icons.Outlined.Check, "没错") }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }
    }
}
