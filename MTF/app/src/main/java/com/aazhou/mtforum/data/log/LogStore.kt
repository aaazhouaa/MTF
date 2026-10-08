package com.aazhou.mtforum.data.log

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * 本地日志存储：JSON Lines（一行一条）+ 内存缓存，超条数上限时整文件重写保留最新。
 *
 * 只依赖 [File]（不碰 Android Context），故可在 JVM 单测里直接验证，与
 * [com.aazhou.mtforum.data.cache.ContentCache] 同一思路。
 * 写盘走「临时文件 + rename」原子替换：写一半被杀进程也留不下半截文件。
 * 文件放在 filesDir 下（内部存储），不占应用缓存、系统空间紧张时不会被回收。
 */
class LogStore(
    private val file: File,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    /** 时间正序（旧 → 新）的内存缓存；null 表示尚未从磁盘加载。 */
    private var cached: List<LogEntry>? = null

    /** 记一条日志；id 取当前最大 id + 1，保证重启后不与历史条目重号。 */
    suspend fun append(level: LogLevel, tag: String, message: String, detail: String? = null) {
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                val current = load()
                val entry = LogEntry(
                    id = (current.lastOrNull()?.id ?: 0L) + 1,
                    time = System.currentTimeMillis(),
                    level = level,
                    tag = tag,
                    message = message,
                    detail = detail,
                )
                persist(current + entry)
            }
        }
    }

    /** 全部日志，新的在前（列表页直接可用）。 */
    suspend fun entries(): List<LogEntry> = withContext(Dispatchers.IO) {
        synchronized(lock) { load() }.asReversed()
    }

    /** 清空日志文件与内存缓存。 */
    suspend fun clear() {
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                cached = emptyList()
                file.delete()
            }
        }
    }

    /** 删除单条；id 不存在时什么都不做。删除同样走原子重写，不留半截文件。 */
    suspend fun remove(id: Long) {
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                val current = load()
                val kept = current.filterNot { it.id == id }
                if (kept.size != current.size) persist(kept)
            }
        }
    }

    /** 日志文件当前占用字节数（不存在为 0）。 */
    fun sizeBytes(): Long = file.length()

    /** 崩溃标记：进程即将被杀前落下，下次启动时用于指认「上次是崩溃退出的」。 */
    suspend fun markCrashed() {
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                runCatching {
                    file.parentFile?.mkdirs()
                    File(file.parentFile, CRASH_MARKER).writeText(
                        System.currentTimeMillis().toString(),
                    )
                }
            }
        }
    }

    /** 读取并清除崩溃标记；true 表示上一次运行异常退出。 */
    suspend fun consumeCrashMarker(): Boolean = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val marker = File(file.parentFile, CRASH_MARKER)
            val existed = marker.isFile
            if (existed) marker.delete()
            existed
        }
    }

    /** 必须在 [lock] 内调用。 */
    private fun load(): List<LogEntry> {
        cached?.let { return it }
        val loaded = if (file.isFile) {
            file.readLines().mapNotNull { line ->
                if (line.isBlank()) {
                    null
                } else {
                    // 单行坏掉只丢这一行：日志损坏不该让整个日志页不可用
                    runCatching { json.decodeFromString<LogEntry>(line) }.getOrNull()
                }
            }
        } else {
            emptyList()
        }
        cached = loaded
        return loaded
    }

    /** 必须在 [lock] 内调用：超上限淘汰最旧，再整文件原子重写。 */
    private fun persist(entries: List<LogEntry>) {
        val trimmed = if (entries.size > maxEntries) entries.takeLast(maxEntries) else entries
        cached = trimmed
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + TMP_SUFFIX)
            tmp.writeText(trimmed.joinToString("\n") { json.encodeToString(LogEntry.serializer(), it) })
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }
    }

    private companion object {
        /** 单文件写入中途的临时后缀。 */
        const val TMP_SUFFIX = ".tmp"

        /** 崩溃标记文件名（与日志文件同目录）。 */
        const val CRASH_MARKER = "crash_marker"

        /** 条数上限：超出后按时间淘汰最旧的，避免无限增长。 */
        const val DEFAULT_MAX_ENTRIES = 500
    }
}
