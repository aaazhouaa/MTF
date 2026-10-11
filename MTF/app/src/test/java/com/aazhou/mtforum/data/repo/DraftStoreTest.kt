package com.aazhou.mtforum.data.repo

import com.aazhou.mtforum.data.model.PostDraft
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [DraftStore] 的本机落盘行为：按版块覆盖、多选删除、清空与重启恢复。 */
class DraftStoreTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun tempDir(): File = Files.createTempDirectory("mtf_drafts_test").toFile()

    private fun draftFile(dir: File): File = File(dir, "post_drafts.json")

    private fun draft(fid: String, subject: String, savedAt: Long = 1_000L) = PostDraft(
        fid = fid,
        forumName = "版块$fid",
        subject = subject,
        message = "$subject 的正文",
        typeId = "3",
        savedAt = savedAt,
    )

    /**
     * 等文件内容变成 [expected] 描述的形态。
     *
     * 落盘在独立作用域里异步做，且是整份覆写：只能按落盘后的内容等，等「文件非空」
     * 会在上一次写入的内容上就返回。用 delay 而不是 yield：后者在同一事件循环里
     * 只是重排自己，不给 IO 线程真正落地的时间。
     */
    private suspend fun awaitFile(dir: File, expected: List<String>) {
        val file = draftFile(dir)
        repeat(100) {
            delay(10)
            val onDisk = runCatching {
                json.decodeFromString<List<PostDraft>>(file.readText())
            }.getOrNull() ?: return@repeat
            if (onDisk.map { it.fid } == expected) return
        }
        assertEquals(expected, readDisk(dir).map { it.fid })
    }

    private fun readDisk(dir: File): List<PostDraft> {
        val file = draftFile(dir)
        if (!file.isFile) return emptyList()
        return runCatching { json.decodeFromString<List<PostDraft>>(file.readText()) }
            .getOrDefault(emptyList())
    }

    @Test
    fun `save upserts by fid and sorts by savedAt`() = runBlocking {
        val dir = tempDir()
        val store = DraftStore(dir)
        store.save(draft("2", "旧", savedAt = 1_000L))
        store.save(draft("37", "新", savedAt = 2_000L))
        awaitFile(dir, listOf("37", "2"))
        assertEquals(listOf("37", "2"), store.items.first().map { it.fid })

        // 同一版块再存一份：只覆盖，不新增
        store.save(draft("2", "改过的标题", savedAt = 3_000L))
        awaitFile(dir, listOf("2", "37"))
        val items = store.items.first()
        assertEquals(2, items.size)
        assertEquals("改过的标题", items.first { it.fid == "2" }.subject)
    }

    @Test
    fun `remove deletes only the given fids`() = runBlocking {
        val dir = tempDir()
        val store = DraftStore(dir)
        store.save(draft("2", "甲", savedAt = 1_000L))
        store.save(draft("37", "乙", savedAt = 2_000L))
        store.save(draft("88", "丙", savedAt = 3_000L))
        awaitFile(dir, listOf("88", "37", "2"))

        store.remove(listOf("2", "88"))
        awaitFile(dir, listOf("37"))
        assertEquals(listOf("37"), store.items.first().map { it.fid })
    }

    @Test
    fun `clear empties the box`() = runBlocking {
        val dir = tempDir()
        val store = DraftStore(dir)
        store.save(draft("2", "甲", savedAt = 1_000L))
        store.save(draft("37", "乙", savedAt = 2_000L))
        awaitFile(dir, listOf("37", "2"))

        store.clear()
        awaitFile(dir, emptyList())
        assertTrue(store.items.first().isEmpty())
    }

    @Test
    fun `drafts survive a new store over the same dir`() = runBlocking {
        val dir = tempDir()
        val first = DraftStore(dir)
        first.save(draft("2", "重启前", savedAt = 5_000L))
        awaitFile(dir, listOf("2"))

        // 模拟应用重启：同一目录再建一个实例（进程内无共享状态）
        val second = DraftStore(dir)
        val restored = second.items.first()
        assertEquals(1, restored.size)
        assertEquals("重启前", restored.first().subject)
        assertEquals("3", restored.first().typeId)
        assertEquals("版块2", restored.first().forumName)
    }

    @Test
    fun `broken file is treated as an empty box`() = runBlocking {
        val dir = tempDir()
        dir.mkdirs()
        draftFile(dir).writeText("{ 不是 JSON")
        val store = DraftStore(dir)
        assertTrue(store.items.first().isEmpty())
    }

    @Test
    fun `sizeBytes reflects the file on disk`() = runBlocking {
        val dir = tempDir()
        val store = DraftStore(dir)
        assertEquals(0L, store.sizeBytes())
        store.save(draft("2", "甲"))
        awaitFile(dir, listOf("2"))
        assertTrue(store.sizeBytes() > 0L)
    }
}
