package io.github.rikka.animelog.data

import kotlinx.serialization.json.Json

private val PrettyJson = Json(AppJson) { prettyPrint = true }

object Backup {
    fun toJson(list: List<Entry>): String = PrettyJson.encodeToString(Library.serializer(), Library(version = DATA_VERSION, entries = list))

    /** 旧版本导出的备份也按种子修正升级。 */
    fun fromJson(text: String, seed: () -> List<Entry>): List<Entry> {
        val lib = AppJson.decodeFromString(Library.serializer(), text)
        return if (lib.version >= DATA_VERSION) lib.entries else migrateEntries(lib.entries, lib.version, seed())
    }

    /** 给 Excel 打开用，带 BOM 防中文乱码。 */
    fun toCsv(list: List<Entry>): String {
        val head = listOf("作品", "条目", "状态", "年份", "开始日期", "看完日期", "评分", "进度", "总集数", "重看", "备注",
            "Bangumi ID", "Bangumi 名称", "类型", "首播", "制作")
        val rows = list.sortedBy { it.order }.map { e ->
            listOf(e.series, e.part, e.status.label, e.statYear?.toString() ?: "", e.startDate ?: "", e.finishDate ?: "",
                e.rating?.toString() ?: "", e.progress.toString(), e.totalEps?.toString() ?: "", e.rewatch.toString(), e.note,
                e.bgm?.id?.toString() ?: "", e.bgm?.displayName ?: "", e.bgm?.platform ?: "", e.bgm?.date ?: "", e.bgm?.studio ?: "")
        }
        return "﻿" + (listOf(head) + rows).joinToString("\r\n") { row -> row.joinToString(",") { csvCell(it) } }
    }

    private fun csvCell(s: String) =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}
