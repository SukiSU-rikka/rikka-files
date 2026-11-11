package io.github.rikka.animelog.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

sealed interface Page {
    data class Detail(val id: Long) : Page
    data object Add : Page
    data object Review : Page
}

enum class Tab(val label: String) { LIST("片单"), STATS("统计"), MORE("更多") }

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    val ctx = LocalContext.current
    val toast by vm.toast.collectAsState()
    LaunchedEffect(toast) {
        toast?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); vm.toastShown() }
    }

    var tab by rememberSaveable { mutableStateOf(Tab.LIST) }
    val stack = remember { mutableStateListOf<Page>() }
    val open: (Page) -> Unit = { stack.add(it) }
    val back: () -> Unit = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }

    // 进详情页再回来时保留列表的滚动位置和筛选
    val holder = rememberSaveableStateHolder()

    BackHandler(enabled = stack.isNotEmpty()) { back() }
    BackHandler(enabled = stack.isEmpty() && tab != Tab.LIST) { tab = Tab.LIST }

    when (val top = stack.lastOrNull()) {
        is Page.Detail -> DetailScreen(vm, top.id, back)
        Page.Add -> AddScreen(vm, onBack = back, onCreated = { id -> back(); open(Page.Detail(id)) })
        Page.Review -> ReviewScreen(vm, back, open)
        null -> Scaffold(
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = {
                                Icon(
                                    when (t) {
                                        Tab.LIST -> Icons.AutoMirrored.Outlined.List
                                        Tab.STATS -> Icons.Outlined.BarChart
                                        Tab.MORE -> Icons.Outlined.MoreHoriz
                                    }, null
                                )
                            },
                            label = { Text(t.label) },
                        )
                    }
                }
            },
        ) { pad ->
            val m = Modifier.padding(bottom = pad.calculateBottomPadding())
            holder.SaveableStateProvider(tab.name) {
                when (tab) {
                    Tab.LIST -> ListScreen(vm, m, open)
                    Tab.STATS -> StatsScreen(vm, m)
                    Tab.MORE -> MoreScreen(vm, m, open)
                }
            }
        }
    }
}
