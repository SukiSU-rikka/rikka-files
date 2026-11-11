package io.github.rikka.animelog.data

/** 当前数据格式 / 种子版本。 */
const val DATA_VERSION = 2

/**
 * 种子数据的修正，用于已经装过旧版的手机（种子只在第一次打开时导入）。
 *
 * 只改还停留在旧值上的条目：用户手动改过的 Bangumi 关联或条目名不动；
 * 进度、评分、日期、短评等永远不碰。新值从当前 assets/seed.json 里取。
 */
private data class SeedFix(
    val id: Long,
    /** 旧的 Bangumi id；null 表示旧版没关联上 */
    val oldBgm: Int?,
    val fixBgm: Boolean,
    /** 旧的条目名；null 表示不改名 */
    val oldPart: String? = null,
)

// v1 -> v2：人工复核出的 7 条错配，以及无职转生四条的条目名（见 tools/seed_fixes.json）
private val fixesV2 = listOf(
    SeedFix(48, oldBgm = 277554, fixBgm = false, oldPart = "第一季"),
    SeedFix(49, oldBgm = 325585, fixBgm = false, oldPart = "第二季"),
    SeedFix(50, oldBgm = 567520, fixBgm = true, oldPart = "OVA"),
    SeedFix(117, oldBgm = 325585, fixBgm = true, oldPart = "第二季"),
    SeedFix(170, oldBgm = 351373, fixBgm = true),
    SeedFix(295, oldBgm = 2969, fixBgm = true),
    SeedFix(357, oldBgm = 90789, fixBgm = true),
    SeedFix(421, oldBgm = 202240, fixBgm = true),
    SeedFix(416, oldBgm = null, fixBgm = true),
)

fun migrateEntries(entries: List<Entry>, fromVersion: Int, seed: List<Entry>): List<Entry> {
    if (fromVersion >= 2) return entries
    val seedById = seed.associateBy { it.id }
    val fixes = fixesV2.associateBy { it.id }
    return entries.map { e ->
        val fix = fixes[e.id] ?: return@map e
        val s = seedById[e.id] ?: return@map e
        // id 相同但原文不同，说明不是同一条（理论上不会发生），不动
        if (e.source != s.source) return@map e
        var out = e
        if (fix.fixBgm && e.bgm?.id == fix.oldBgm && e.match != MatchState.CONFIRMED) {
            out = out.copy(bgm = s.bgm, match = s.match)
        }
        if (fix.oldPart != null && e.part == fix.oldPart) out = out.copy(part = s.part)
        out
    }
}
