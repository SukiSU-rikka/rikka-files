package io.github.rikka.animelog.data

data class Count(val label: String, val value: Int)

data class YearStat(val year: Int, val entries: Int, val works: Int, val eps: Int, val minutes: Int)

data class Stats(
    val doneEntries: Int,
    val doneWorks: Int,
    val watching: Int,
    val want: Int,
    val dropped: Int,
    val totalEps: Int,
    val totalMinutes: Int,
    /** 有集数信息的看完条目 / 看完条目 —— 时长统计的覆盖率 */
    val epsCoverage: Pair<Int, Int>,
    val byYear: List<YearStat>,
    /** 今年每月看完的条目数（只算有具体日期的） */
    val byMonth: List<Count>,
    val thisYear: Int,
    val ratings: List<Count>,
    val avgRating: Double?,
    val platforms: List<Count>,
    val tags: List<Count>,
    val studios: List<Count>,
    /** 条目最多的系列 */
    val bigSeries: List<Count>,
    val rewatched: Int,
)

/** 只统计题材类标签；网友标签里还有制作公司、出版社、声优，不算。 */
private val genreTags = setOf(
    "恋爱", "搞笑", "喜剧", "奇幻", "校园", "百合", "后宫", "日常", "战斗", "异世界", "科幻", "治愈", "悬疑", "推理",
    "冒险", "热血", "运动", "音乐", "偶像", "机战", "机甲", "恐怖", "惊悚", "致郁", "催泪", "青春", "萌", "萌系",
    "卖肉", "魔法少女", "穿越", "转生", "末日", "神作", "泡面番", "美食", "职场", "历史", "战争", "剧情", "猎奇",
    "乐队", "妹控", "兄控", "伪娘", "吸血鬼", "超能力", "魔法", "群像", "原创", "漫画改", "轻小说改", "游戏改",
    "黑暗", "脑洞", "温馨", "励志", "社团", "露营", "旅行", "无厘头", "子供向", "少女向", "少年向", "青年向",
)

fun computeStats(all: List<Entry>, thisYear: Int): Stats {
    val done = all.filter { it.status == Status.DONE }

    val byYear = done.filter { it.statYear != null }.groupBy { it.statYear!! }.toSortedMap().map { (y, l) ->
        YearStat(y, l.size, l.map { it.series }.distinct().size, l.sumOf { it.watchedEps }, l.sumOf { it.watchedMinutes })
    }

    val months = IntArray(12)
    done.forEach { e ->
        val d = e.finishDate ?: return@forEach
        if (d.take(4).toIntOrNull() == thisYear) d.substring(5, 7).toIntOrNull()?.let { months[it - 1]++ }
    }

    val rated = all.mapNotNull { it.rating }
    val ratings = (1..10).map { r -> Count("$r", rated.count { it == r }) }

    fun top(items: List<String>, n: Int) = items.groupingBy { it }.eachCount()
        .entries.sortedByDescending { it.value }.take(n).map { Count(it.key, it.value) }

    // 标签/制作公司按作品去重：一个系列多季只算一次
    val doneWithBgm = done.filter { it.bgm != null }
    val seriesTags = doneWithBgm.groupBy { it.series }.mapValues { (_, l) -> l.flatMap { it.bgm!!.tags }.toSet() }
    val seriesStudios = doneWithBgm.groupBy { it.series }.mapValues { (_, l) -> l.mapNotNull { it.bgm!!.studio }.toSet() }

    return Stats(
        doneEntries = done.size,
        doneWorks = done.map { it.series }.distinct().size,
        watching = all.count { it.status == Status.WATCHING },
        want = all.count { it.status == Status.WANT },
        dropped = all.count { it.status == Status.DROPPED },
        totalEps = done.sumOf { it.watchedEps } + all.filter { it.status != Status.DONE }.sumOf { it.progress },
        totalMinutes = all.sumOf { it.watchedMinutes },
        epsCoverage = done.count { it.totalEps != null } to done.size,
        byYear = byYear,
        byMonth = months.mapIndexed { i, c -> Count("${i + 1}", c) },
        thisYear = thisYear,
        ratings = ratings,
        avgRating = rated.takeIf { it.isNotEmpty() }?.average(),
        platforms = top(done.map { it.bgm?.platform ?: "未匹配" }, 6),
        tags = top(seriesTags.values.flatten().filter { it in genreTags }, 15),
        studios = top(seriesStudios.values.flatten(), 10),
        bigSeries = done.groupBy { it.series }.filter { it.value.size > 1 }
            .entries.sortedByDescending { it.value.size }.take(10).map { Count(it.key, it.value.size) },
        rewatched = done.count { it.rewatch > 0 },
    )
}
