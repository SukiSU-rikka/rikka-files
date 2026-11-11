package io.github.rikka.animelog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.rikka.animelog.data.Entry
import io.github.rikka.animelog.data.MatchState
import io.github.rikka.animelog.data.Status

/** 封面；没有就显示首字。 */
@Composable
fun Cover(url: String?, title: String, width: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    val m = modifier.size(width, width * 1.4f).clip(shape)
    if (url != null) {
        AsyncImage(
            model = url, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = m.background(MaterialTheme.colorScheme.surfaceVariant),
        )
    } else {
        Box(m.background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Text(
                title.take(1), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
fun statusColor(s: Status): Color = when (s) {
    Status.WATCHING -> MaterialTheme.colorScheme.primary
    Status.WANT -> MaterialTheme.colorScheme.tertiary
    Status.DONE -> MaterialTheme.colorScheme.outline
    Status.ON_HOLD -> MaterialTheme.colorScheme.secondary
    Status.DROPPED -> MaterialTheme.colorScheme.error
}

/** 列表副标题：状态 · 进度 · 评分 · 类型 */
fun Entry.subtitle(): String = buildList {
    if (status != Status.DONE) add(status.label)
    if (match == MatchState.UNSURE) add("Bangumi 待确认")
    if (status == Status.WATCHING || (status != Status.DONE && progress > 0)) add("$progress/${totalEps ?: "?"} 集")
    rating?.let { add("★ $it") }
    bgm?.platform?.let { add(it) }
    if (status == Status.DONE) totalEps?.let { add("$it 集") }
    if (rewatch > 0) add("重看 $rewatch 次")
    finishDate?.let { if (status == Status.DONE) add(it) }
}.joinToString(" · ")

fun formatHours(minutes: Int): String {
    val h = minutes / 60.0
    return if (h >= 48) "%.0f 小时（%.1f 天）".format(h, h / 24) else "%.0f 小时".format(h)
}
