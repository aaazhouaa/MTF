package com.aazhou.mtforum.data.log

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [LogStore] 的本机日志行为：读写顺序、id 连续性、条数上限、坏行、清空与崩溃标记。 */
class LogStoreTest {

    private fun tempStore(maxEntries: Int = 50): Pair<LogStore, java.io.File> {
        val dir = Files.createTempDirectory("mtf_log_test").toFile()
        return LogStore(java.io.File(dir, "mtf_log.jsonl"), maxEntries) to dir
    }

    @Test
    fun `写入后按新的在前读回`() = runBlocking {
        val (store, _) = tempStore()
        store.append(LogLevel.INFO, "A", "第一条")
        store.append(LogLevel.WARN, "B", "第二条")

        val entries = store.entries()
        assertEquals(2, entries.size)
        assertEquals("第二条", entries[0].message)
        assertEquals("第一条", entries[1].message)
        assertEquals(LogLevel.WARN, entries[0].level)
    }

    @Test
    fun `id 单调递增且重启后不重号`() = runBlocking {
        val dir = Files.createTempDirectory("mtf_log_restart_test").toFile()
        val file = java.io.File(dir, "mtf_log.jsonl")

        LogStore(file).append(LogLevel.INFO, "A", "第一条")
        LogStore(file).append(LogLevel.INFO, "A", "第二条")
        // 换一个实例模拟进程重启：缓存没了，但 id 必须接着磁盘上的往下走
        val reopened = LogStore(file)
        reopened.append(LogLevel.ERROR, "Crash", "第三条")

        val ids = reopened.entries().map { it.id }
        assertEquals(listOf(3L, 2L, 1L), ids)
    }

    @Test
    fun `超过条数上限时淘汰最旧并保住最新`() = runBlocking {
        // 上限设 3：写入第 4 条时必须淘汰最旧的第一条
        val (store, _) = tempStore(maxEntries = 3)
        repeat(4) { index -> store.append(LogLevel.INFO, "T", "第 $index 条") }

        val messages = store.entries().map { it.message }
        assertEquals(listOf("第 3 条", "第 2 条", "第 1 条"), messages)
        // 淘汰后 id 仍接着走：不能因为丢了最旧的条目就把 id 重新编号
        store.append(LogLevel.INFO, "T", "第 4 条")
        assertEquals(5L, store.entries().first().id)
    }

    @Test
    fun `单行损坏只丢该行`() = runBlocking {
        val (store, dir) = tempStore()
        store.append(LogLevel.INFO, "T", "正常条目")
        val file = java.io.File(dir, "mtf_log.jsonl")
        file.appendText("\n完全不是 JSON")

        val entries = store.entries()
        assertEquals(1, entries.size)
        assertEquals("正常条目", entries[0].message)
    }

    @Test
    fun `clear 清空文件与读取结果`() = runBlocking {
        val (store, dir) = tempStore()
        store.append(LogLevel.INFO, "T", "待清空")
        assertTrue(store.sizeBytes() > 0)

        store.clear()
        assertTrue(store.entries().isEmpty())
        assertFalse(java.io.File(dir, "mtf_log.jsonl").exists())
    }

    @Test
    fun `按 id 删除单条且不影响其他条目`() = runBlocking {
        val (store, _) = tempStore()
        store.append(LogLevel.INFO, "T", "第一条")
        store.append(LogLevel.WARN, "T", "第二条")
        store.append(LogLevel.ERROR, "T", "第三条")
        val target = store.entries().first { it.message == "第二条" }.id

        store.remove(target)

        assertEquals(listOf("第三条", "第一条"), store.entries().map { it.message })
        // id 仍接着走：删过的号不复用
        store.append(LogLevel.INFO, "T", "第四条")
        assertEquals(4L, store.entries().first().id)
    }

    @Test
    fun `删除不存在的 id 时内容不变`() = runBlocking {
        val (store, _) = tempStore()
        store.append(LogLevel.INFO, "T", "第一条")
        val before = store.entries()

        store.remove(999L)

        assertEquals(before, store.entries())
    }

    @Test
    fun `异常堆栈随条目一起落盘`() = runBlocking {
        val (store, _) = tempStore()
        store.append(LogLevel.ERROR, "Crash", "未捕获异常", "java.lang.IllegalStateException: boom")

        val entry = store.entries().first()
        assertEquals("java.lang.IllegalStateException: boom", entry.detail)
        assertTrue(entry.toText().contains("java.lang.IllegalStateException: boom"))
    }

    @Test
    fun `崩溃标记只消费一次`() = runBlocking {
        val (store, _) = tempStore()
        store.markCrashed()
        assertTrue(store.consumeCrashMarker())
        assertFalse(store.consumeCrashMarker())
    }

    @Test
    fun `没有崩溃标记时消费返回 false`() = runBlocking {
        val (store, _) = tempStore()
        assertFalse(store.consumeCrashMarker())
    }
}
