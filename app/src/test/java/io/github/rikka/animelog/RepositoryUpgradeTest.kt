package io.github.rikka.animelog

import androidx.test.core.app.ApplicationProvider
import io.github.rikka.animelog.data.AppJson
import io.github.rikka.animelog.data.BgmInfo
import io.github.rikka.animelog.data.DATA_VERSION
import io.github.rikka.animelog.data.Library
import io.github.rikka.animelog.data.MatchState
import io.github.rikka.animelog.data.Repository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** 手机上装着 v1.0、文件里是旧数据，升级到 v1.1 后第一次启动。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RepositoryUpgradeTest {
    @Test
    fun v1FileIsUpgradedOnLoad() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val seed = Repository(ctx).seed()
        val file = File(ctx.filesDir, "library.json")
        // 还原成 v1.0 的样子：错配 + 旧条目名，另外用户在砂糖上记了进度
        val v1 = seed.map {
            when (it.id) {
                50L -> it.copy(part = "OVA", bgm = BgmInfo(567520, nameCn = "血契之约"), match = MatchState.UNSURE)
                170L -> it.copy(bgm = BgmInfo(351373, nameCn = "银砂糖师"), match = MatchState.UNSURE, progress = 4, rating = 9)
                else -> it
            }
        }
        // v1.0 写文件时不带 version 字段
        file.writeText(AppJson.encodeToString(Library.serializer(), Library(entries = v1)))
        assertTrue("v1 文件里不应有 version", !file.readText().contains("\"version\""))

        val repo = Repository(ctx)
        val e50 = repo.get(50)!!
        assertEquals(373247, e50.bgm?.id)
        assertEquals("第二季 第1部分", e50.part)
        val e170 = repo.get(170)!!
        assertEquals(240828, e170.bgm?.id)
        assertEquals(4, e170.progress)
        assertEquals(9, e170.rating)
        // 升级后写回了新版本号，并留了升级前的备份
        assertEquals(DATA_VERSION, AppJson.decodeFromString(Library.serializer(), file.readText()).version)
        assertTrue(File(ctx.filesDir, "library.v1.json").exists())
    }
}
