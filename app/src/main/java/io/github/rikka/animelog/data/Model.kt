package io.github.rikka.animelog.data

import kotlinx.serialization.Serializable

@Serializable
enum class Status(val label: String) {
    WATCHING("在看"),
    WANT("想看"),
    DONE("看完"),
    ON_HOLD("搁置"),
    DROPPED("弃坑"),
}

/** Bangumi 匹配状态。 */
@Serializable
enum class MatchState {
    /** 还没匹配过 */
    NONE,

    /** 自动匹配，把握大 */
    AUTO,

    /** 自动匹配，把握不大，建议人工确认 */
    UNSURE,

    /** 搜不到 */
    NOT_FOUND,

    /** 用户确认过（或手动选的） */
    CONFIRMED,
}

@Serializable
data class BgmInfo(
    val id: Int,
    val name: String = "",
    val nameCn: String = "",
    val cover: String? = null,
    val date: String? = null,
    /** TV / 剧场版 / OVA / WEB ... */
    val platform: String? = null,
    val eps: Int? = null,
    /** 单集时长（分钟），来自 infobox「片长」 */
    val minutes: Int? = null,
    val tags: List<String> = emptyList(),
    val studio: String? = null,
    val score: Double? = null,
) {
    val displayName: String get() = nameCn.ifBlank { name }
    val url: String get() = "https://bgm.tv/subject/$id"

    /** 单集时长；Bangumi 没写就按类型估计。 */
    val minutesOrGuess: Int
        get() = minutes ?: when (platform) {
            "剧场版" -> 100
            "WEB" -> 15
            else -> 24
        }
}

@Serializable
data class Entry(
    val id: Long,
    /** 作品（系列）名，同一系列的季/剧场版共用 */
    val series: String,
    /** 季 / 篇 / 剧场版 / OVA；整部作品只有一条时为空 */
    val part: String = "",
    val status: Status = Status.DONE,
    /** 看完的年份（旧记录只有年份） */
    val year: Int? = null,
    /** 排序用：越大越新 */
    val order: Long = 0,
    /** yyyy-MM-dd */
    val startDate: String? = null,
    /** yyyy-MM-dd */
    val finishDate: String? = null,
    /** 1..10 */
    val rating: Int? = null,
    /** 已看集数（在看时用） */
    val progress: Int = 0,
    /** 重看次数 */
    val rewatch: Int = 0,
    val note: String = "",
    /** 导入时的原文 */
    val source: String? = null,
    val bgm: BgmInfo? = null,
    val match: MatchState = MatchState.NONE,
    val updatedAt: Long = 0,
) {
    val title: String
        get() = when {
            part.isBlank() -> series
            // 「物语系列 化物语」只显示「化物语」
            series.endsWith("系列") -> part
            else -> "$series $part"
        }

    /** 归到哪一年统计。 */
    val statYear: Int? get() = year ?: finishDate?.take(4)?.toIntOrNull()

    val totalEps: Int? get() = bgm?.eps?.takeIf { it > 0 }

    /** 实际看过的集数（含重看）。 */
    val watchedEps: Int
        get() = when (status) {
            Status.DONE -> (totalEps ?: 0) * (1 + rewatch)
            else -> progress
        }

    val watchedMinutes: Int get() = watchedEps * (bgm?.minutesOrGuess ?: 24)
}

@Serializable
data class Library(
    /** 没写版本号的是 v1.0 的数据 */
    val version: Int = 1,
    val entries: List<Entry> = emptyList(),
)
