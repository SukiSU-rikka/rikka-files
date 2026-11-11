package io.github.rikka.animelog.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.HttpURLConnection
import java.net.URL

/** Bangumi 搜索结果（带别名，用来打分）。 */
data class BgmCandidate(val info: BgmInfo, val aliases: List<String>, val rank: Int)

data class MatchResult(val info: BgmInfo?, val confidence: Double) {
    val state: MatchState
        get() = when {
            info == null -> MatchState.NOT_FOUND
            confidence >= 0.75 -> MatchState.AUTO
            else -> MatchState.UNSURE
        }
}

/**
 * Bangumi 番组计划 API（https://bangumi.github.io/api/）。
 * 只用公开接口，不需要登录。
 */
object Bangumi {
    private const val API = "https://api.bgm.tv"
    private const val UA = "RikkaAnimeLog/1.0 (Android)"

    private fun http(path: String, body: String? = null): JsonElement? {
        repeat(3) { attempt ->
            try {
                val conn = URL(API + path).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 20000
                conn.setRequestProperty("User-Agent", UA)
                conn.setRequestProperty("Accept", "application/json")
                if (body != null) {
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.outputStream.use { it.write(body.toByteArray()) }
                }
                val code = conn.responseCode
                if (code == 404) return null
                if (code in 200..299) {
                    return AppJson.parseToJsonElement(conn.inputStream.bufferedReader().use { it.readText() })
                }
            } catch (_: Exception) {
            }
            Thread.sleep(1000L * (attempt + 1))
        }
        throw java.io.IOException("Bangumi 请求失败：$path")
    }

    suspend fun search(keyword: String, limit: Int = 10): List<BgmCandidate> = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("keyword", keyword)
            putJsonObject("filter") { putJsonArray("type") { add(JsonPrimitive(2)) } }
        }.toString()
        val data = http("/v0/search/subjects?limit=$limit", body)?.jsonObject?.get("data") as? JsonArray
            ?: return@withContext emptyList()
        data.mapIndexed { i, el -> parseSubject(el.jsonObject).copy(rank = i) }
    }

    suspend fun subject(id: Int): BgmCandidate? = withContext(Dispatchers.IO) {
        http("/v0/subjects/$id")?.jsonObject?.let { parseSubject(it) }
    }

    /** infobox 里没写「动画制作」时，从关联公司里找。 */
    suspend fun withStudio(info: BgmInfo): BgmInfo {
        if (info.studio != null) return info
        val studio = withContext(Dispatchers.IO) {
            val arr = runCatching { http("/v0/subjects/${info.id}/persons") }.getOrNull() as? JsonArray
            arr?.map { it.jsonObject }?.filter { it.str("relation") == "动画制作" }
                ?.mapNotNull { it.str("name") }?.distinct()?.joinToString(" / ")?.ifBlank { null }
        }
        return info.copy(studio = studio)
    }

    /** 动画类的「续集」。 */
    private suspend fun sequel(id: Int): Int? = withContext(Dispatchers.IO) {
        val arr = http("/v0/subjects/$id/subjects") as? JsonArray ?: return@withContext null
        arr.map { it.jsonObject }.firstOrNull {
            it.str("relation") == "续集" && it["type"]?.jsonPrimitive?.intOrNull == 2
        }?.get("id")?.jsonPrimitive?.intOrNull
    }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull

    private fun parseSubject(o: JsonObject): BgmCandidate {
        val infobox = (o["infobox"] as? JsonArray)?.map { it.jsonObject } ?: emptyList()
        fun box(key: String): List<String> = infobox.filter { it.str("key") == key }.flatMap { item ->
            when (val v = item["value"]) {
                is JsonPrimitive -> listOfNotNull(v.contentOrNull)
                is JsonArray -> v.mapNotNull { (it as? JsonObject)?.str("v") }
                else -> emptyList()
            }
        }.filter { it.isNotBlank() }

        val images = o["images"] as? JsonObject
        val tags = (o["tags"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.str("name") } ?: emptyList()
        val meta = (o["meta_tags"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
        val eps = (o["eps"] as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 }
            ?: (o["total_episodes"] as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 }
            ?: box("话数").firstOrNull()?.toIntOrNull()
        val minutes = box("片长").firstOrNull()?.let { Regex("""(\d+)""").find(it)?.value?.toIntOrNull() }
        val score = ((o["rating"] as? JsonObject)?.get("score") as? JsonPrimitive)?.doubleOrNull?.takeIf { it > 0 }

        val info = BgmInfo(
            id = o["id"]!!.jsonPrimitive.intOrNull ?: 0,
            name = o.str("name") ?: "",
            nameCn = o.str("name_cn") ?: "",
            cover = images?.str("common") ?: images?.str("medium") ?: images?.str("large"),
            date = o.str("date"),
            platform = o.str("platform"),
            eps = eps,
            minutes = minutes,
            // meta_tags 是官方标签，比较干净，放前面
            tags = (meta + tags).distinct().take(20),
            studio = box("动画制作").firstOrNull(),
            score = score,
        )
        val aliases = (listOf(info.nameCn, info.name) + box("中文名") + box("别名")).filter { it.isNotBlank() }.distinct()
        return BgmCandidate(info, aliases, -1)
    }

    // ---------------- 自动匹配 ----------------

    private const val CN = "零一二三四五六七八九十"

    private fun cnToInt(s: String): Int? {
        s.toIntOrNull()?.let { return it }
        val t = s.replace('两', '二')
        return when {
            t == "十" -> 10
            t.length == 1 -> CN.indexOf(t[0]).takeIf { it >= 0 }
            t.startsWith("十") -> 10 + CN.indexOf(t[1])
            t.contains("十") -> {
                val (a, b) = t.split("十")
                CN.indexOf(a[0]) * 10 + (if (b.isEmpty()) 0 else CN.indexOf(b[0]))
            }
            else -> null
        }
    }

    fun seasonOf(s: String): Int? {
        Regex("""第([一二三四五六七八九十两\d]+)[季期]""").find(s)?.let { return cnToInt(it.groupValues[1]) }
        Regex("""(\d+)(?:st|nd|rd|th) ?season""", RegexOption.IGNORE_CASE).find(s)?.let { return it.groupValues[1].toInt() }
        Regex("""season ?(\d+)""", RegexOption.IGNORE_CASE).find(s)?.let { return it.groupValues[1].toInt() }
        Regex("""\s(II|III|IV|Ⅱ|Ⅲ|Ⅳ)(\s|$)""").find(s)?.let {
            return when (it.groupValues[1]) { "II", "Ⅱ" -> 2; "III", "Ⅲ" -> 3; else -> 4 }
        }
        return null
    }

    private val seasonWords = Regex("""第[一二三四五六七八九十两\d]+[季期]|\d+(st|nd|rd|th) ?season|season ?\d+""", RegexOption.IGNORE_CASE)
    private val nonWord = Regex("""[\s\p{P}\p{S}_]+""")

    private fun norm(s: String) = s.lowercase().replace(seasonWords, "").replace(nonWord, "")

    private fun dice(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        fun bi(s: String) = if (s.length < 2) setOf(s) else (0 until s.length - 1).map { s.substring(it, it + 2) }.toSet()
        val x = bi(a)
        val y = bi(b)
        return 2.0 * (x intersect y).size / (x.size + y.size)
    }

    private fun kindOf(part: String) = when {
        "剧场版" in part || "电影" in part -> "剧场版"
        "OVA" in part || "OAD" in part -> "OVA"
        else -> null
    }

    fun score(series: String, part: String, c: BgmCandidate): Double {
        val want = norm(series + part)
        val wantS = norm(series)
        val names = c.aliases.map { norm(it) }
        var sim = names.maxOfOrNull { n -> maxOf(dice(want, n), dice(wantS, n) * 0.9) } ?: 0.0
        // 名字互为前缀：Bangumi 的正式名常带副标题，如「辉夜大小姐想让我告白～天才们的恋爱头脑战～」
        if (wantS.length >= 2 && names.any { n -> n.length >= 2 && (n.startsWith(wantS) || wantS.startsWith(n)) }) {
            sim = maxOf(sim, 0.78)
        }
        if (c.rank == 0) sim += 0.1
        val sw = seasonOf(part)
        val sc = seasonOf(c.info.nameCn) ?: seasonOf(c.info.name)
        if (sw != null) {
            sim += if ((sc ?: 1) != sw) -0.35 else 0.1
        } else if (sc != null && sc > 1 && part.isBlank()) {
            sim -= 0.2
        }
        val plat = c.info.platform ?: ""
        when (kindOf(part)) {
            "剧场版" -> sim += if (plat == "剧场版") 0.1 else -0.4
            "OVA" -> sim += if (plat == "OVA" || plat == "OAD") 0.1 else -0.4
            // 「其他」一般是同人、MV、PV
            else -> if (plat !in setOf("TV", "WEB", "剧场版", "OVA", "OAD")) sim -= 0.15
        }
        return sim
    }

    suspend fun autoMatch(series: String, part: String): MatchResult {
        val r = autoMatchRaw(series, part)
        return if (r.info != null) r.copy(info = withStudio(r.info)) else r
    }

    private suspend fun autoMatchRaw(series: String, part: String): MatchResult {
        // 「物语系列 / 化物语」这种合起来的系列名，直接用条目名搜
        if (series.endsWith("系列") && part.isNotBlank()) {
            return autoMatchRaw(part.replace(Regex("（.*?）"), ""), if ("剧场版" in part) "剧场版" else "")
        }
        // 「第N季」：先找第一季，再顺着「续集」往后走。Bangumi 的续作名字经常不带季数。
        val sw = seasonOf(part)
        if (sw != null && sw > 1 && Regex("""第[一二三四五六七八九十]+季""").matches(part)) {
            val base = autoMatchRaw(series, "")
            if (base.info != null && base.confidence >= 0.75) {
                // 只数 TV/WEB 的续集，跳过中间的剧场版（比如哥布林杀手第一季的续集是剧场版）
                var cur: BgmCandidate? = null
                var id: Int? = base.info.id
                var need = sw - 1
                var hops = 0
                while (need > 0 && id != null && hops < 8) {
                    hops++
                    id = sequel(id)
                    cur = id?.let { subject(it) }
                    if (cur?.info?.platform == "TV" || cur?.info?.platform == "WEB") need--
                }
                if (need == 0 && cur != null) return MatchResult(cur.info, 0.9)
            }
        }
        val kw = "$series $part".trim().removeSuffix("第一季").trim()
        var cands = search(kw)
        if (cands.isEmpty() && part.isNotBlank()) cands = search(series)
        if (cands.isEmpty()) return MatchResult(null, 0.0)
        val scored = cands.map { it to score(series, part, it) }
        val top = scored.maxOf { it.second }
        // 分数差不多时选最早播出的（原作 / 第一部）
        val best = scored.filter { it.second >= top - 0.02 }.minBy { it.first.info.date ?: "9999" }
        return MatchResult(best.first.info, best.second)
    }
}
