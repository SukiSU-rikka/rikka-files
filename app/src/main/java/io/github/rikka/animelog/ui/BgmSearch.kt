package io.github.rikka.animelog.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.rikka.animelog.data.BgmCandidate
import io.github.rikka.animelog.data.BgmInfo
import kotlinx.coroutines.launch

class BgmSearchState {
    var query by mutableStateOf("")
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var results by mutableStateOf<List<BgmCandidate>>(emptyList())
    var searched by mutableStateOf(false)
}

/** 搜索框 + 结果列表，点结果回调 onPick。 */
@Composable
fun BgmSearchPanel(
    vm: AppViewModel,
    initial: String,
    modifier: Modifier = Modifier,
    autoSearch: Boolean = true,
    header: LazyListScope.() -> Unit = {},
    onPick: (BgmInfo) -> Unit,
) {
    val st = remember { BgmSearchState().apply { query = initial } }
    val scope = rememberCoroutineScope()
    fun go() {
        if (st.query.isBlank()) return
        scope.launch {
            st.loading = true; st.error = null
            runCatching { vm.search(st.query.trim()) }
                .onSuccess { st.results = it; st.searched = true }
                .onFailure { st.error = "搜索失败：${it.message}" }
            st.loading = false
        }
    }
    LaunchedEffect(Unit) { if (autoSearch && initial.isNotBlank()) go() }

    Column(modifier) {
        OutlinedTextField(
            value = st.query, onValueChange = { st.query = it }, singleLine = true,
            label = { Text("在 Bangumi 搜索") },
            trailingIcon = { IconButton(::go) { Icon(Icons.Outlined.Search, "搜索") } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { go() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        LazyColumn(Modifier.fillMaxWidth()) {
            header()
            if (st.loading) item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            st.error?.let { item { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) } }
            if (!st.loading && st.searched && st.results.isEmpty()) item {
                Text("没搜到，换个叫法试试（日文名、简称都行）", Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(st.results, key = { it.info.id }) { c -> BgmRow(c.info) { onPick(c.info) } }
        }
    }
}

@Composable
fun BgmRow(b: BgmInfo, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(b.cover, b.displayName, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(b.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (b.nameCn.isNotBlank() && b.name != b.nameCn) {
                Text(b.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                listOfNotNull(b.platform, b.date, b.eps?.let { "$it 集" }, b.score?.let { "Bangumi $it" }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
