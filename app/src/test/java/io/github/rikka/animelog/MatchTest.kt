package io.github.rikka.animelog

import io.github.rikka.animelog.data.AppJson
import io.github.rikka.animelog.data.Bangumi
import io.github.rikka.animelog.data.DATA_VERSION
import io.github.rikka.animelog.data.Library
import io.github.rikka.animelog.data.MatchState
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.github.rikka.animelog.data.Status
import io.github.rikka.animelog.data.computeStats
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class MatchTest {
    private val seedFile = File(System.getProperty("seedFile")!!)

    @Test
    fun seedParses() {
        val lib = AppJson.decodeFromString(Library.serializer(), seedFile.readText())
        assertEquals(422, lib.entries.size)
        assertTrue(lib.entries.all { it.status == Status.DONE && it.year in 2024..2026 })
        val s = computeStats(lib.entries, 2026)
        assertEquals(422, s.doneEntries)
        println("works=${s.doneWorks} years=${s.byYear.map { "${it.year}:${it.entries}/${it.works}" }}")
    }

    @Test
    fun seasonParsing() {
        assertEquals(2, Bangumi.seasonOf("第二季"))
        assertEquals(3, Bangumi.seasonOf("为美好的世界献上祝福！第三季"))
        assertEquals(2, Bangumi.seasonOf("Kaguya-sama 2nd Season"))
        assertEquals(null, Bangumi.seasonOf("进击的巨人"))
    }

    /** 联网：用 -PseedMatch=1 运行，把 tools/seed_fixes.json 里人工复核的修正写回 seed.json。 */
    @Test
    fun applySeedFixes() = runBlocking {
        assumeTrue(System.getProperty("seedMatch").orEmpty().isNotEmpty())
        val fixes = AppJson.parseToJsonElement(File(System.getProperty("seedFixesFile")!!).readText())
            .jsonObject["fixes"]!!.jsonArray.map { it.jsonObject }
        val lib = AppJson.decodeFromString(Library.serializer(), seedFile.readText())
        val out = lib.entries.map { e ->
            val f = fixes.firstOrNull { it["id"]!!.jsonPrimitive.int.toLong() == e.id } ?: return@map e
            var x = e
            f["part"]?.jsonPrimitive?.content?.let { x = x.copy(part = it) }
            f["bgm"]?.jsonPrimitive?.int?.let { id ->
                val info = Bangumi.withStudio(Bangumi.subject(id)!!.info)
                println("${e.id}\t${x.title}\t=> ${info.id} ${info.displayName} [${info.platform}]")
                x = x.copy(bgm = info, match = MatchState.CONFIRMED)
                Thread.sleep(250)
            }
            x
        }
        seedFile.writeText(AppJson.encodeToString(Library.serializer(), Library(version = DATA_VERSION, entries = out)))
    }

    /** 联网：用 -PseedMatch=1 运行，匹配全部初始记录并写回 seed.json。 */
    @Test
    fun matchSeed() = runBlocking {
        assumeTrue(System.getProperty("seedMatch").orEmpty().isNotEmpty())
        val lib = AppJson.decodeFromString(Library.serializer(), seedFile.readText())
        val out = lib.entries.map { e ->
            if (e.bgm != null) {
                if (e.bgm!!.studio != null) return@map e
                Thread.sleep(200)
                return@map e.copy(bgm = Bangumi.withStudio(e.bgm!!))
            }
            val r = Bangumi.autoMatch(e.series, e.part)
            println("%.2f\t%s\t=> %s [%s]".format(r.confidence, e.title, r.info?.displayName, r.info?.platform))
            Thread.sleep(250)
            e.copy(bgm = r.info, match = r.state)
        }
        seedFile.writeText(AppJson.encodeToString(Library.serializer(), Library(version = DATA_VERSION, entries = out)))
    }
}
