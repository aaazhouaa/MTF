package com.aazhou.mtforum.data.cache

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
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

    @Test
    fun `写入后可以原样读回`() = runBlocking {
        val (cache, _) = tempCache()
        cache.write("key", Payload("hello", 42)) { json.encodeToString(Payload.serializer(), it) }
        val read = cache.read("key", ttlMillis = 60_000) {
            json.decodeFromString(Payload.serializer(), it)
        }
        assertEquals(Payload("hello", 42), read)
    }

    @Test
    fun `过期后读不到并清掉文件`() = runBlocking {
        val (cache, dir) = tempCache()
        cache.write("key", Payload("a", 1)) { json.encodeToString(Payload.serializer(), it) }
        // ttl 为 0：savedAt 与当前时间之差必然大于 0，视为已过期
        val read = cache.read("key", ttlMillis = 0) {
            json.decodeFromString(Payload.serializer(), it)
        }
        assertNull(read)
        assertTrue("过期文件应被删除", dir.listFiles()?.all { !it.isFile } == true)
    }

    @Test
    fun `payload 损坏时按无缓存处理并清掉文件`() = runBlocking {
        val (cache, dir) = tempCache()
        cache.write("key", Payload("a", 1)) { json.encodeToString(Payload.serializer(), it) }
        // 保留外层结构、只把 payload 换成非法 JSON：解码器失败也要退化成无缓存
        val file = dir.listFiles()?.first() ?: error("写入后应有一个缓存文件")
        file.writeText("""{"savedAt":${System.currentTimeMillis()},"data":"{ not json"}""")
        val read = cache.read("key", ttlMillis = 60_000) {
            json.decodeFromString(Payload.serializer(), it)
        }
        assertNull(read)
        assertTrue("坏文件应被删除", dir.listFiles()?.all { !it.isFile } == true)
    }

    @Test
    fun `外层结构都不是 JSON 时同样按无缓存处理`() = runBlocking {
        val (cache, dir) = tempCache()
        cache.write("key", Payload("a", 1)) { json.encodeToString(Payload.serializer(), it) }
        // 连 CacheEntry 外壳都解析不了：与上一个测试（外壳正常、payload 坏）区分开
        dir.listFiles()?.first()?.writeText("完全不是 JSON")
        val read = cache.read("key", ttlMillis = 60_000) {
            json.decodeFromString(Payload.serializer(), it)
        }
        assertNull(read)
        assertTrue("坏文件应被删除", dir.listFiles()?.all { !it.isFile } == true)
    }

    @Test
    fun `超过容量上限时淘汰最旧的文件并保住刚写入的`() = runBlocking {
        // 每条 payload 约 40 字节，上限设 120：写入第三条时必须淘汰最旧的第一条
        val (cache, _) = tempCache(maxBytes = 120)
        cache.write("k1", Payload("1", 1)) { json.encodeToString(Payload.serializer(), it) }
        cache.write("k2", Payload("2", 2)) { json.encodeToString(Payload.serializer(), it) }
        cache.write("k3", Payload("3", 3)) { json.encodeToString(Payload.serializer(), it) }
        val read1 = cache.read("k1", ttlMillis = 60_000) {
            json.decodeFromString(Payload.serializer(), it)
        }
        val read3 = cache.read("k3", ttlMillis = 60_000) {
            json.decodeFromString(Payload.serializer(), it)
        }
        assertNull("最旧的 k1 应被淘汰", read1)
        assertEquals("刚写入的 k3 必须保住", Payload("3", 3), read3)
    }

    @Test
    fun `clear 清空目录、sizeBytes 反映占用`() = runBlocking {
        val (cache, _) = tempCache()
        assertEquals(0L, cache.sizeBytes())
        cache.write("key", Payload("hello", 42)) { json.encodeToString(Payload.serializer(), it) }
        assertTrue("写入后应有占用", cache.sizeBytes() > 0)
        cache.clear()
        assertEquals(0L, cache.sizeBytes())
        val read = cache.read("key", ttlMillis = 60_000) {
            json.decodeFromString(Payload.serializer(), it)
        }
        assertNull(read)
    }

    @Test
    fun `同一个 key 重复写入以最后一次为准`() = runBlocking {
        val (cache, dir) = tempCache()
        cache.write("key", Payload("old", 1)) { json.encodeToString(Payload.serializer(), it) }
        cache.write("key", Payload("new", 2)) { json.encodeToString(Payload.serializer(), it) }
        val read = cache.read("key", ttlMillis = 60_000) {
            json.decodeFromString(Payload.serializer(), it)
        }
        assertEquals(Payload("new", 2), read)
        assertEquals("同一 key 只应有一个文件", 1, dir.listFiles()?.size)
    }
}

@kotlinx.serialization.Serializable
private data class Payload(val text: String, val number: Int)
