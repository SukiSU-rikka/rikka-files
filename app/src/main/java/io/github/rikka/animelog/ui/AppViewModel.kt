package io.github.rikka.animelog.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.rikka.animelog.AnimeLogApp
import io.github.rikka.animelog.data.Backup
import io.github.rikka.animelog.data.Bangumi
import io.github.rikka.animelog.data.BgmInfo
import io.github.rikka.animelog.data.Entry
import io.github.rikka.animelog.data.MatchState
import io.github.rikka.animelog.data.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class BatchProgress(val done: Int, val total: Int, val current: String, val running: Boolean, val error: String? = null)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = (app as AnimeLogApp).repo
    val entries = repo.entries

    private val _batch = MutableStateFlow<BatchProgress?>(null)
    val batch = _batch.asStateFlow()
    private var batchJob: Job? = null

    private val _toast = MutableStateFlow<String?>(null)
    val toast = _toast.asStateFlow()
    fun toastShown() { _toast.value = null }

    fun today(): String = LocalDate.now().toString()

    // ---------- 编辑 ----------

    fun update(id: Long, f: (Entry) -> Entry) = repo.update(id, f)

    /** 改状态时顺手补上日期/进度。 */
    fun setStatus(id: Long, s: Status) = repo.update(id) { e ->
        when (s) {
            Status.DONE -> {
                val fd = e.finishDate ?: today()
                e.copy(status = s, finishDate = fd, year = e.year ?: fd.take(4).toInt(), progress = e.totalEps ?: e.progress)
            }
            Status.WATCHING -> e.copy(status = s, startDate = e.startDate ?: today())
            else -> e.copy(status = s)
        }
    }

    fun plusOne(id: Long) {
        val e = repo.get(id) ?: return
        val next = e.progress + 1
        if (e.totalEps != null && next >= e.totalEps!!) {
            repo.update(id) { it.copy(progress = e.totalEps!!) }
            setStatus(id, Status.DONE)
            _toast.value = "「${e.title}」看完啦"
        } else {
            repo.update(id) { it.copy(progress = next, status = Status.WATCHING, startDate = it.startDate ?: today()) }
        }
    }

    fun delete(id: Long) = repo.delete(id)

    fun renameSeries(old: String, new: String) {
        if (new.isNotBlank() && new != old) repo.renameSeries(old, new.trim())
    }

    /** 新建条目，返回 id。 */
    fun create(series: String, part: String, status: Status, bgm: BgmInfo?): Long {
        val id = repo.nextId()
        repo.upsert(
            Entry(
                id = id, series = series.trim(), part = part.trim(), status = status,
                order = repo.nextOrder(), bgm = bgm, match = if (bgm != null) MatchState.CONFIRMED else MatchState.NONE,
            )
        )
        if (status == Status.DONE || status == Status.WATCHING) setStatus(id, status)
        if (bgm != null && bgm.studio == null) fillStudio(id, bgm)
        return id
    }

    /**
     * 从 Bangumi 名称猜作品/条目：如果名字以已有作品开头，就挂到那个作品下面。
     * 「进击的巨人 第二季」 -> 进击的巨人 / 第二季
     */
    fun guessSeries(name: String): Pair<String, String> {
        val existing = entries.value.map { it.series }.distinct().sortedByDescending { it.length }
        existing.firstOrNull { name.startsWith(it) && name.length > it.length }?.let {
            return it to name.removePrefix(it).trim(' ', '：', ':', '·', '-', '～', '~')
        }
        return name to ""
    }

    fun setBgm(id: Long, info: BgmInfo?, state: MatchState) {
        repo.update(id) { it.copy(bgm = info, match = state) }
        if (info != null && info.studio == null) fillStudio(id, info)
    }

    private fun fillStudio(id: Long, info: BgmInfo) = viewModelScope.launch {
        val full = runCatching { Bangumi.withStudio(info) }.getOrNull() ?: return@launch
        repo.update(id) { if (it.bgm?.id == info.id) it.copy(bgm = full) else it }
    }

    suspend fun search(keyword: String) = Bangumi.search(keyword, 20)

    fun refreshBgm(id: Long) = viewModelScope.launch {
        val e = repo.get(id) ?: return@launch
        val bid = e.bgm?.id ?: return@launch
        runCatching { Bangumi.subject(bid) }.onSuccess { c ->
            if (c != null) {
                val full = Bangumi.withStudio(c.info)
                repo.update(id) { it.copy(bgm = full) }
            }
            _toast.value = "已刷新"
        }.onFailure { _toast.value = "刷新失败：${it.message}" }
    }

    // ---------- 批量匹配 ----------

    fun startBatch(retryFailed: Boolean) {
        if (batchJob?.isActive == true) return
        val todo = entries.value.filter { it.match == MatchState.NONE || (retryFailed && it.match == MatchState.NOT_FOUND) }
            .sortedBy { it.order }
        if (todo.isEmpty()) {
            _toast.value = "没有需要匹配的条目"
            return
        }
        batchJob = viewModelScope.launch {
            var done = 0
            for (e in todo) {
                _batch.value = BatchProgress(done, todo.size, e.title, true)
                try {
                    val r = Bangumi.autoMatch(e.series, e.part)
                    // 期间用户可能手动改过，别覆盖
                    repo.update(e.id) { cur -> if (cur.match == MatchState.CONFIRMED) cur else cur.copy(bgm = r.info, match = r.state) }
                } catch (ex: Exception) {
                    _batch.value = BatchProgress(done, todo.size, e.title, false, "网络出错，已暂停：${ex.message}")
                    return@launch
                }
                done++
                delay(250)
            }
            _batch.value = BatchProgress(done, todo.size, "", false)
        }
    }

    fun stopBatch() {
        batchJob?.cancel()
        _batch.value = _batch.value?.copy(running = false)
    }

    // ---------- 备份 ----------

    fun exportTo(uri: Uri, csv: Boolean) = viewModelScope.launch {
        val text = if (csv) Backup.toCsv(entries.value) else Backup.toJson(entries.value)
        runCatching {
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
            }
        }.onSuccess { _toast.value = "已导出 ${entries.value.size} 条" }
            .onFailure { _toast.value = "导出失败：${it.message}" }
    }

    fun importFrom(uri: Uri) = viewModelScope.launch {
        runCatching {
            val text = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            }
            Backup.fromJson(text, repo::seed)
        }.onSuccess {
            repo.replaceAll(it)
            _toast.value = "已恢复 ${it.size} 条"
        }.onFailure { _toast.value = "导入失败：不是有效的备份文件" }
    }

    fun resetToSeed() {
        repo.replaceAll(repo.seed())
        _toast.value = "已恢复成最初导入的记录"
    }
}
