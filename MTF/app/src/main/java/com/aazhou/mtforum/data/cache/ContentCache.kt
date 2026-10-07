package com.aazhou.mtforum.data.cache

import java.io.File
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 本地内容缓存：一个 key 一个 JSON 文件，整页读、整页覆写。
 *
 * 只依赖 [File]（不碰 Android Context），故可在 JVM 单测里直接验证。
 * 写入走「临时文件 + rename」原子替换：写一半被杀进程也留不下能解析出半截内容的文件，
 * 顶多留一个 .tmp，被当作坏文件清掉。
 * [CacheEntry.data] 是调用方编码好的 JSON 字符串：模型的反序列化由调用方的解码器完成，
 * 这样缓存层不需要知道任何模型的序列化器。
 */
class ContentCache(
    private val dir: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 读取缓存；过期、不存在、内容损坏都返回 null 并清掉坏文件，
     * 绝不以异常形式冒出——缓存坏了退化成「没有缓存」，走正常网络路径。
     */
    suspend fun <T> read(key: String, ttlMillis: Long, decode: (String) -> T?): T? =
        withContext(Dispatchers.IO) {
            val file = fileFor(key)
            if (!file.isFile) return@withContext null
            val entry = runCatching {
                json.decodeFromString<CacheEntry>(file.readText())
            }.getOrNull()
            if (entry == null) {
                file.delete()
                return@withContext null
            }
            if (System.currentTimeMillis() - entry.savedAt > ttlMillis) {
                file.delete()
                return@withContext null
            }
            runCatching { decode(entry.data) }.getOrNull() ?: run {
                file.delete()
                null
            }
        }

    /** 写入缓存；写完按容量上限淘汰最旧的文件（刚写入的这份不参与淘汰）。 */
    suspend fun <T> write(key: String, value: T, encode: (T) -> String) {
        withContext(Dispatchers.IO) {
            runCatching {
                dir.mkdirs()
                val file = fileFor(key)
                val payload = json.encodeToString(
                    CacheEntry.serializer(),
                    CacheEntry(savedAt = System.currentTimeMillis(), data = encode(value)),
                )
                val tmp = File(dir, file.name + TMP_SUFFIX)
                tmp.writeText(payload)
                if (!tmp.renameTo(file)) {
                    // rename 失败（极端：目标被占用）时退化为覆盖写，再清掉临时文件
                    tmp.copyTo(file, overwrite = true)
                    tmp.delete()
                }
                evictIfTooLarge(keep = file)
            }.getOrDefault(Unit)
        }
    }

    /** 清空整个缓存目录。 */
    suspend fun clear() {
        withContext(Dispatchers.IO) {
            dir.listFiles()?.forEach { it.delete() }
        }
    }

    /** 当前占用字节数（含没写干净的 .tmp）。 */
    fun sizeBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    /** 按最后修改时间从旧到新淘汰，直到低于 [maxBytes]；[keep] 是刚写入的文件，不删。 */
    private fun evictIfTooLarge(keep: File) {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (total <= maxBytes) return
            if (file == keep) return@forEach
            val size = file.length()
            if (file.delete()) total -= size
        }
    }

    private fun fileFor(key: String): File {
        val name = Base64.getUrlEncoder().withoutPadding().encodeToString(key.toByteArray())
        return File(dir, name)
    }

    private companion object {
        /** 单文件写入中途的临时后缀。 */
        const val TMP_SUFFIX = ".tmp"

        /** 默认总量上限：超出后按最后修改时间淘汰最旧的。 */
        const val DEFAULT_MAX_BYTES = 48L * 1024 * 1024
    }
}

/** 缓存文件的内容包装：[savedAt] 由缓存自己维护，用于过期判定。 */
@Serializable
data class CacheEntry(
    val savedAt: Long,
    val data: String,
)
