package io.github.rikka.animelog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.rikka.animelog.data.Count
import io.github.rikka.animelog.data.computeStats
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: AppViewModel, modifier: Modifier) {
    val all by vm.entries.collectAsState()
    val thisYear = LocalDate.now().year
    val s = remember(all) { computeStats(all, thisYear) }

    Scaffold(modifier = modifier, topBar = { TopAppBar(title = { Text("统计") }) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- 总数 ----
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("看完的作品", "${s.doneWorks}", "部", Modifier.weight(1f))
                Tile("看完的条目", "${s.doneEntries}", "季/部", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("总集数", "${s.totalEps}", "集", Modifier.weight(1f))
                Tile("总时长", "%.0f".format(s.totalMinutes / 60.0), "小时", Modifier.weight(1f))
            }
            Text(
                "在看 ${s.watching} · 想看 ${s.want} · 弃坑 ${s.dropped} · 重看过 ${s.rewatched}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val (cov, tot) = s.epsCoverage
            if (cov < tot) {
                Text(
                    "集数和时长只算了有 Bangumi 信息的 $cov/$tot 个条目。去「更多 → 检查匹配结果」补上剩下的。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary,
                )
            }

            // ---- 每年 ----
            ChartCard("每年看完") {
                val max = s.byYear.maxOfOrNull { it.entries } ?: 1
                s.byYear.reversed().forEach { y ->
                    BarRow(
                        "${y.year}", y.entries.toFloat() / max,
                        "${y.entries} 条 · ${y.works} 部" + (if (y.minutes > 0) " · ${y.minutes / 60} 小时" else ""),
                        maxWidth = 0.3f, labelWidth = 48,
                    )
                }
                if (s.byYear.isEmpty()) Empty()
            }

            // ---- 今年每月 ----
            if (s.byMonth.any { it.value > 0 }) {
                ChartCard("${s.thisYear} 年每月看完（有日期的）") { Columns(s.byMonth) }
            }

            // ---- 评分 ----
            ChartCard("评分分布" + (s.avgRating?.let { "  ·  平均 %.1f".format(it) } ?: "")) {
                if (s.avgRating == null) Text("还没有评分。在条目详情里打分后这里会出现。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Columns(s.ratings)
            }

            // ---- 类型 / 标签 / 制作 / 系列 ----
            ChartCard("类型") { Bars(s.platforms) }
            ChartCard("常看的标签（按作品计）") { if (s.tags.isEmpty()) Empty() else Bars(s.tags) }
            ChartCard("常看的制作公司（按作品计）") { if (s.studios.isEmpty()) Empty() else Bars(s.studios) }
            ChartCard("条目最多的系列") { if (s.bigSeries.isEmpty()) Empty() else Bars(s.bigSeries, unit = " 条") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Empty() = Text("匹配 Bangumi 后才有", style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Tile(label: String, value: String, unit: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(4.dp))
                Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp))
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        content()
        HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

/** 横向条：标签 | 条 | 数值。单一数据系列，用一个颜色，数值直接写在旁边。 */
@Composable
private fun BarRow(label: String, fraction: Float, value: String, maxWidth: Float = 0.62f, labelWidth: Int = 96) {
    Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(labelWidth.dp))
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.fillMaxWidth(fraction.coerceIn(0.01f, 1f) * maxWidth).height(12.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)),
                )
                Spacer(Modifier.width(6.dp))
                Text(value, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun Bars(items: List<Count>, unit: String = "") {
    val max = items.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1
    items.forEach { BarRow(it.label, it.value.toFloat() / max, "${it.value}$unit") }
}

/** 纵向柱：用于 1–12 月、1–10 分这种有顺序的类别。 */
@Composable
private fun Columns(items: List<Count>) {
    val max = items.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1
    Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEach { c ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom) {
                if (c.value > 0) Text("${c.value}", style = MaterialTheme.typography.labelSmall)
                Box(
                    Modifier.fillMaxWidth(0.6f).height((100f * c.value / max).dp.coerceAtLeast(if (c.value > 0) 2.dp else 0.dp))
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(c.label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
