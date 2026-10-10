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
     * 读取缓存；不存在、内容损坏都返回 null 并清掉坏文件，
     * 绝不以异常形式冒出——缓存坏了退化成「没有缓存」，走正常网络路径。
     *
     * 过期时的处理由 [allowExpired] 决定：默认按无缓存处理并删掉文件；为 true 时过期内容
     * 也原样返回且不删文件，供调用方先摆旧内容、后台再拉新的。
     */
    suspend fun <T> read(
        key: String,
        ttlMillis: Long,
        allowExpired: Boolean = false,
        decode: (String) -> T?,
    ): T? =
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
            if (System.currentTimeMillis() - entry.savedAt > ttlMillis && !allowExpired) {
                file.delete()
                return@withContext null
            }
            runCatching { decode(entry.data) }.getOrNull() ?: run {
                file.delete()
                null
            }
        }

    /**
     * 写入缓存；[ttlMillis] 随条目一起落盘，供容量淘汰时判「这份是否已过期」——
     * 删除发生在写入方看不到的时机，不能依赖调用方当场告知新鲜度。
     * 写完按容量上限淘汰：先清已过期的，仍超限再删写入时间最旧的（刚写入的这份不删）。
     */
    suspend fun <T> write(key: String, ttlMillis: Long, value: T, encode: (T) -> String) {
        withContext(Dispatchers.IO) {
            runCatching {
                dir.mkdirs()
                val file = fileFor(key)
                val payload = json.encodeToString(
                    CacheEntry.serializer(),
                    CacheEntry(
                        savedAt = System.currentTimeMillis(),
                        ttlMillis = ttlMillis,
                        data = encode(value),
                    ),
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

    /**
     * 按容量淘汰：先清已过期的，仍超限再按最后修改时间从旧到新删，直到低于 [maxBytes]；
     * [keep] 是刚写入的文件，不删。
     *
     * 过期文件即便没有再被读到也只是占地方，所以优先让位；从最旧的开始扫，腾够就停手，
     * 不必把整个目录读一遍。
     */
    private fun evictIfTooLarge(keep: File) {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        val now = System.currentTimeMillis()
        val oldestFirst = files.sortedBy { it.lastModified() }
        oldestFirst.forEach { file ->
            if (total <= maxBytes) return
            if (file == keep) return@forEach
            if (!isExpired(file, now)) return@forEach
            val size = file.length()
            if (file.delete()) total -= size
        }
        oldestFirst.forEach { file ->
            if (total <= maxBytes) return
            if (file == keep) return@forEach
            val size = file.length()
            if (file.delete()) total -= size
        }
    }

    /**
     * 这份文件是否已过期。解析不了按过期处理（与读取时一致：坏文件直接清掉）；
     * 没记 ttl 的是旧版本写下的文件，不判过期，只按写入时间参与容量淘汰。
     */
    private fun isExpired(file: File, now: Long): Boolean {
        val entry = runCatching {
            json.decodeFromString<CacheEntry>(file.readText())
        }.getOrNull() ?: return true
        val ttl = entry.ttlMillis ?: return false
        return now - entry.savedAt > ttl
    }

    private fun fileFor(key: String): File {
        val name = Base64.getUrlEncoder().withoutPadding().encodeToString(key.toByteArray())
        return File(dir, name)
    }

    private companion object {
        /** 单文件写入中途的临时后缀。 */
        const val TMP_SUFFIX = ".tmp"

        /**
         * 默认总量上限：超出后先清已过期的，再按最后修改时间淘汰最旧的。
         *
         * 条目是解析后的正文快照（纯文本，图片另走 Coil 磁盘缓存）：详情快照几十 KB 到上百 KB，
         * 列表类几十 KB，128MB 可容纳上百份详情，正常浏览撞不到上限。
         */
        const val DEFAULT_MAX_BYTES = 128L * 1024 * 1024
    }
}

/**
 * 缓存文件的内容包装：[savedAt] 由缓存自己维护，[ttlMillis] 是写入方声明的有效期。
 *
 * [ttlMillis] 为 null 表示旧版本写下的文件（当时不记录有效期）：容量淘汰时不判它过期，
 * 过期与否仍由读取方按自己传入的有效期决定。
 */
@Serializable
data class CacheEntry(
    val savedAt: Long,
    val ttlMillis: Long? = null,
    val data: String,
)
