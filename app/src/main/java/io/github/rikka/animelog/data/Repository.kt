package io.github.rikka.animelog.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    prettyPrint = false
}

/**
 * 全部数据就是一个 JSON 文件（files/library.json）。
 * 几百条的量直接整份读写，备份导出也是这份文件。
 */
class Repository(private val context: Context) {
    private val file = File(context.filesDir, "library.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveSignal = Channel<Unit>(Channel.CONFLATED)

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    init {
        scope.launch {
            for (unused in saveSignal) writeFile(_entries.value)
        }
    }

    private fun load(): List<Entry> {
        if (file.exists()) {
            val lib = runCatching { AppJson.decodeFromString<Library>(file.readText()) }.getOrNull()
            if (lib != null) {
                if (lib.version >= DATA_VERSION) return lib.entries
                // 旧版数据：按种子修正升级，并先留一份升级前的备份
                file.copyTo(File(context.filesDir, "library.v${lib.version}.json"), overwrite = true)
                return migrateEntries(lib.entries, lib.version, seed()).also { writeFile(it) }
            }
            // 文件坏了就留一份，免得被覆盖
            file.copyTo(File(context.filesDir, "library.broken.json"), overwrite = true)
        }
        return seed().also { writeFile(it) }
    }

    fun seed(): List<Entry> {
        val text = context.assets.open("seed.json").bufferedReader().use { it.readText() }
        val now = System.currentTimeMillis()
        return AppJson.decodeFromString<Library>(text).entries.map { it.copy(updatedAt = now) }
    }

    private fun writeFile(list: List<Entry>) {
        val tmp = File(file.parentFile, "library.json.tmp")
        tmp.writeText(AppJson.encodeToString(Library.serializer(), Library(version = DATA_VERSION, entries = list)))
        tmp.renameTo(file)
    }

    private fun commit(transform: (List<Entry>) -> List<Entry>) {
        _entries.update(transform)
        saveSignal.trySend(Unit)
    }

    fun nextId(): Long = (_entries.value.maxOfOrNull { it.id } ?: 0) + 1
    fun nextOrder(): Long = (_entries.value.maxOfOrNull { it.order } ?: 0) + 1

    fun upsert(entry: Entry) = commit { list ->
        val e = entry.copy(updatedAt = System.currentTimeMillis())
        if (list.any { it.id == e.id }) list.map { if (it.id == e.id) e else it } else list + e
    }

    fun update(id: Long, f: (Entry) -> Entry) = commit { list ->
        list.map { if (it.id == id) f(it).copy(updatedAt = System.currentTimeMillis()) else it }
    }

    fun delete(id: Long) = commit { list -> list.filterNot { it.id == id } }

    fun replaceAll(list: List<Entry>) = commit { list }

    /** 改整个系列的名字（合并/拆分系列）。 */
    fun renameSeries(old: String, new: String) = commit { list ->
        list.map { if (it.series == old) it.copy(series = new) else it }
    }

    fun get(id: Long): Entry? = _entries.value.firstOrNull { it.id == id }
}
