package com.aazhou.mtforum.data.cache

import java.nio.file.Files
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [ContentCache] 的本机缓存行为：读写、过期、坏文件、容量淘汰、清空与占用统计。 */
class ContentCacheTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun tempCache(maxBytes: Long = 1024 * 1024): Pair<ContentCache, java.io.File> {
        val dir = Files.createTempDirectory("mtf_cache_test").toFile()
        return ContentCache(dir, maxBytes) to dir
    }

    private fun decode(it: String) = json.decodeFromString(Payload.serializer(), it)

    /** 缓存文件名与 [ContentCache] 的编码方式一致，供测试直接定位文件。 */
    private fun entryFile(dir: java.io.File, key: String): java.io.File {
        val name = Base64.getUrlEncoder().withoutPadding().encodeToString(key.toByteArray())
        return java.io.File(dir, name)
    }

    /** 直接落一个缓存文件（绕过 write），用于构造「已过期」「旧版本格式」等现场。 */
    private fun putEntry(
        dir: java.io.File,
        key: String,
        savedAt: Long,
        ttlMillis: Long?,
        value: Payload,
    ) {
        val data = json.encodeToString(Payload.serializer(), value)
        val entry = buildString {
            append("{\"savedAt\":").append(savedAt)
            if (ttlMillis != null) append(",\"ttlMillis\":").append(ttlMillis)
            append(",\"data\":").append(json.encodeToString(String.serializer(), data))
            append("}")
        }
        entryFile(dir, key).writeText(entry)
    }

    @Test
    fun `写入后可以原样读回`() = runBlocking {
        val (cache, _) = tempCache()
        cache.write("key", ttlMillis = 60_000, value = Payload("hello", 42)) {
            json.encodeToString(Payload.serializer(), it)
        }
        val read = cache.read("key", ttlMillis = 60_000) { decode(it) }
        assertEquals(Payload("hello", 42), read)
    }

    @Test
    fun `过期后读不到并清掉文件`() = runBlocking {
        val (cache, dir) = tempCache()
        cache.write("key", ttlMillis = 60_000, value = Payload("a", 1)) {
            json.encodeToString(Payload.serializer(), it)
        }
        // ttl 为 0：savedAt 与当前时间之差必然大于 0，视为已过期
        val read = cache.read("key", ttlMillis = 0) { decode(it) }
        assertNull(read)
        assertTrue("过期文件应被删除", dir.listFiles()?.all { !it.isFile } == true)
    }

    @Test
    fun `allowExpired 时过期内容仍可读且不删文件`() = runBlocking {
        val (cache, dir) = tempCache()
        val tenSecondsAgo = System.currentTimeMillis() - 10_000
        putEntry(dir, "key", savedAt = tenSecondsAgo, ttlMillis = 1_000, value = Payload("a", 1))

        val read = cache.read("key", ttlMillis = 1_000, allowExpired = true) { decode(it) }
        assertEquals("过期内容也应原样返回", Payload("a", 1), read)
        assertTrue("过期内容不能被删掉，还要留着上屏", entryFile(dir, "key").isFile)

        // 不放开 allowExpired 时依旧是「按无缓存处理并删文件」
        val again = cache.read("key", ttlMillis = 1_000) { decode(it) }
        assertNull(again)
        assertTrue("默认路径下过期文件应被删除", !entryFile(dir, "key").isFile)
    }

    @Test
    fun `payload 损坏时按无缓存处理并清掉文件`() = runBlocking {
        val (cache, dir) = tempCache()
        cache.write("key", ttlMillis = 60_000, value = Payload("a", 1)) {
            json.encodeToString(Payload.serializer(), it)
        }
        // 保留外层结构、只把 payload 换成非法 JSON：解码器失败也要退化成无缓存
        val file = dir.listFiles()?.first() ?: error("写入后应有一个缓存文件")
        file.writeText("""{"savedAt":${System.currentTimeMillis()},"ttlMillis":60000,"data":"{ not json"}""")
        val read = cache.read("key", ttlMillis = 60_000) { decode(it) }
        assertNull(read)
        assertTrue("坏文件应被删除", dir.listFiles()?.all { !it.isFile } == true)
    }

    @Test
    fun `外层结构都不是 JSON 时同样按无缓存处理`() = runBlocking {
        val (cache, dir) = tempCache()
        cache.write("key", ttlMillis = 60_000, value = Payload("a", 1)) {
            json.encodeToString(Payload.serializer(), it)
        }
        // 连 CacheEntry 外壳都解析不了：与上一个测试（外壳正常、payload 坏）区分开
        dir.listFiles()?.first()?.writeText("完全不是 JSON")
        val read = cache.read("key", ttlMillis = 60_000) { decode(it) }
        assertNull(read)
        assertTrue("坏文件应被删除", dir.listFiles()?.all { !it.isFile } == true)
    }

    @Test
    fun `超过容量上限时淘汰最旧的文件并保住刚写入的`() = runBlocking {
        // 每条 entry 约 80 字节，上限设 120：写入第三条时必须淘汰最旧的第一条
        val (cache, dir) = tempCache(maxBytes = 120)
        cache.write("k1", ttlMillis = 60_000, value = Payload("1", 1)) {
            json.encodeToString(Payload.serializer(), it)
        }
        cache.write("k2", ttlMillis = 60_000, value = Payload("2", 2)) {
            json.encodeToString(Payload.serializer(), it)
        }
        cache.write("k3", ttlMillis = 60_000, value = Payload("3", 3)) {
            json.encodeToString(Payload.serializer(), it)
        }
        val read1 = cache.read("k1", ttlMillis = 60_000) { decode(it) }
        val read3 = cache.read("k3", ttlMillis = 60_000) { decode(it) }
        assertNull("最旧的 k1 应被淘汰", read1)
        assertEquals("刚写入的 k3 必须保住", Payload("3", 3), read3)
    }

    @Test
    fun `超容量时优先淘汰已过期的文件`() = runBlocking {
        // 每条 entry 约 80 字节，上限 200：三条共 240 超限，删掉已过期的那条后降到 160 即收手，
        // 未过期的那条因此保住——若只按写入时间删，先走的会是写入更早的 fresh
        val (cache, dir) = tempCache(maxBytes = 200)
        val now = System.currentTimeMillis()
        putEntry(dir, "fresh", savedAt = now, ttlMillis = 60_000, value = Payload("fresh", 1))
        putEntry(dir, "stale", savedAt = now - 10_000, ttlMillis = 1_000, value = Payload("stale", 2))
        cache.write("new", ttlMillis = 60_000, value = Payload("3", 3)) {
            json.encodeToString(Payload.serializer(), it)
        }
        assertTrue("未过期的 fresh 应保住", entryFile(dir, "fresh").isFile)
        assertTrue("已过期的 stale 应让位", !entryFile(dir, "stale").isFile)
        assertEquals(Payload("3", 3), cache.read("new", ttlMillis = 60_000) { decode(it) })
    }

    @Test
    fun `没记录 ttl 的旧文件不判过期、只按写入时间参与淘汰`() = runBlocking {
        // 旧版本写下的文件没有 ttlMillis：即便 savedAt 很早也不按过期清，让位顺序仍看写入时间
        val (cache, dir) = tempCache(maxBytes = 200)
        val dayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        putEntry(dir, "legacy", savedAt = dayAgo, ttlMillis = null, value = Payload("legacy", 1))
        putEntry(dir, "stale", savedAt = dayAgo, ttlMillis = 60_000, value = Payload("stale", 2))
        cache.write("new", ttlMillis = 60_000, value = Payload("3", 3)) {
            json.encodeToString(Payload.serializer(), it)
        }
        assertTrue("旧版本文件不判过期，应保住", entryFile(dir, "legacy").isFile)
        assertTrue("已过期的 stale 应让位", !entryFile(dir, "stale").isFile)
    }

    @Test
    fun `clear 清空目录、sizeBytes 反映占用`() = runBlocking {
        val (cache, _) = tempCache()
        assertEquals(0L, cache.sizeBytes())
        cache.write("key", ttlMillis = 60_000, value = Payload("hello", 42)) {
            json.encodeToString(Payload.serializer(), it)
        }
        assertTrue("写入后应有占用", cache.sizeBytes() > 0)
        cache.clear()
        assertEquals(0L, cache.sizeBytes())
        val read = cache.read("key", ttlMillis = 60_000) { decode(it) }
        assertNull(read)
    }

    @Test
    fun `同一个 key 重复写入以最后一次为准`() = runBlocking {
        val (cache, dir) = tempCache()
        cache.write("key", ttlMillis = 60_000, value = Payload("old", 1)) {
            json.encodeToString(Payload.serializer(), it)
        }
        cache.write("key", ttlMillis = 60_000, value = Payload("new", 2)) {
            json.encodeToString(Payload.serializer(), it)
        }
        val read = cache.read("key", ttlMillis = 60_000) { decode(it) }
        assertEquals(Payload("new", 2), read)
        assertEquals("同一 key 只应有一个文件", 1, dir.listFiles()?.size)
    }
}

@kotlinx.serialization.Serializable
private data class Payload(val text: String, val number: Int)
