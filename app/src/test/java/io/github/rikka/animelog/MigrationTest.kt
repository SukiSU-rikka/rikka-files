package io.github.rikka.animelog

import io.github.rikka.animelog.data.AppJson
import io.github.rikka.animelog.data.BgmInfo
import io.github.rikka.animelog.data.DATA_VERSION
import io.github.rikka.animelog.data.Entry
import io.github.rikka.animelog.data.Library
import io.github.rikka.animelog.data.MatchState
import io.github.rikka.animelog.data.migrateEntries
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** v1.0 的数据升级到 v1.1：只修还停在旧错配上的条目。 */
class MigrationTest {
    private val seed = AppJson.decodeFromString(Library.serializer(), File(System.getProperty("seedFile")!!).readText())
    private fun s(id: Long) = seed.entries.first { it.id == id }

    /** 按 v1.0 种子的样子还原一条旧记录 */
    private fun old(id: Long, bgm: Int?, part: String? = null, state: MatchState = MatchState.UNSURE): Entry =
        s(id).copy(part = part ?: s(id).part, bgm = bgm?.let { BgmInfo(id = it, nameCn = "旧的错配") }, match = state)

    @Test
    fun seedIsV2WithFixes() {
        assertEquals(DATA_VERSION, seed.version)
        val expect = mapOf(50L to 373247, 117L to 444557, 170L to 240828, 295L to 376565, 357L to 4291, 421L to 502499, 416L to 362335)
        expect.forEach { (id, bgm) ->
            assertEquals("entry $id", bgm, s(id).bgm?.id)
            assertEquals(MatchState.CONFIRMED, s(id).match)
        }
        assertEquals(listOf("第一季 第1部分", "第一季 第2部分", "第二季 第1部分", "第二季 第2部分"),
            listOf(48L, 49L, 50L, 117L).map { s(it).part })
        assertEquals(277554, s(48).bgm?.id)
        assertEquals(325585, s(49).bgm?.id)
    }

    @Test
    fun untouchedOldEntriesGetFixed() {
        val v1 = seed.entries.map {
            when (it.id) {
                48L -> old(48, 277554, "第一季", MatchState.AUTO).copy(bgm = s(48).bgm)
                49L -> old(49, 325585, "第二季", MatchState.AUTO).copy(bgm = s(49).bgm)
                50L -> old(50, 567520, "OVA")
                117L -> old(117, 325585, "第二季", MatchState.AUTO)
                170L -> old(170, 351373)
                295L -> old(295, 2969)
                357L -> old(357, 90789)
                421L -> old(421, 202240)
                416L -> old(416, null, state = MatchState.NOT_FOUND)
                else -> it
            }
        }
        val out = migrateEntries(v1, 1, seed.entries)
        // 修完后应该和 v1.1 种子完全一致
        assertEquals(seed.entries, out)
    }

    @Test
    fun userEditsAreKept() {
        val manual = BgmInfo(id = 999, nameCn = "我自己选的")
        val v1 = listOf(
            // 用户已经手动换过关联、改过名字
            old(50, 999, "我改的名字", MatchState.CONFIRMED).copy(bgm = manual),
            // 用户在错配的条目上记了进度和评分：关联要修，进度评分不能丢
            old(170, 351373).copy(progress = 5, rating = 8, note = "好看", finishDate = "2025-03-01"),
            // 用户点过「没错」确认了旧关联：尊重用户
            old(295, 2969, state = MatchState.CONFIRMED),
            // 用户已经给古利特宇宙手动关联了别的
            old(416, 888, state = MatchState.CONFIRMED),
        )
        val out = migrateEntries(v1, 1, seed.entries).associateBy { it.id }
        assertEquals(manual, out[50]!!.bgm)
        assertEquals("我改的名字", out[50]!!.part)
        assertEquals(240828, out[170]!!.bgm?.id)
        assertEquals(MatchState.CONFIRMED, out[170]!!.match)
        assertEquals(5, out[170]!!.progress)
        assertEquals(8, out[170]!!.rating)
        assertEquals("好看", out[170]!!.note)
        assertEquals("2025-03-01", out[170]!!.finishDate)
        assertEquals(2969, out[295]!!.bgm?.id)
        assertEquals(888, out[416]!!.bgm?.id)
    }

    @Test
    fun currentVersionIsNotTouched() {
        val weird = listOf(old(50, 567520, "OVA"))
        assertEquals(weird, migrateEntries(weird, DATA_VERSION, seed.entries))
    }
}
