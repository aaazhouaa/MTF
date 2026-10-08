package com.aazhou.mtforum.data.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [LogEntry] 文本渲染与 [LogLevel] 严重度比较。 */
class LogEntryTest {

    @Test
    fun `单条文本含时间等级 tag 正文与堆栈`() {
        val entry = LogEntry(
            id = 1,
            time = 0L,
            level = LogLevel.ERROR,
            tag = "HttpClient",
            message = "GET 失败：https://bbs.binmt.cc/",
            detail = "java.io.IOException: timeout",
        )

        val text = entry.toText()
        assertTrue(text.contains("[ERROR]"))
        assertTrue(text.contains("[HttpClient]"))
        assertTrue(text.contains("GET 失败：https://bbs.binmt.cc/"))
        assertTrue(text.contains("java.io.IOException: timeout"))
        assertTrue(text.startsWith("["))
    }

    @Test
    fun `无堆栈时不追加空行`() {
        val text = LogEntry(1, 0L, LogLevel.INFO, "Session", "已恢复会话").toText()
        assertFalse(text.contains("\n"))
        assertTrue(text.endsWith("已恢复会话"))
    }

    @Test
    fun `导出文本按时间正序并带版本与等级说明`() {
        val older = LogEntry(1, 1_000L, LogLevel.WARN, "A", "旧的一条")
        val newer = LogEntry(2, 2_000L, LogLevel.ERROR, "B", "新的一条")

        val text = listOf(newer, older).toExportText("0.1.0", LogLevel.INFO)
        assertTrue(text.contains("MTF v0.1.0 日志导出"))
        assertTrue(text.contains("记录等级：Info"))
        assertTrue(text.contains("共 2 条"))
        assertTrue(text.indexOf("旧的一条") < text.indexOf("新的一条"))
    }

    @Test
    fun `等级按严重程度准入`() {
        assertTrue(LogLevel.INFO.admits(LogLevel.INFO))
        assertTrue(LogLevel.INFO.admits(LogLevel.ERROR))
        assertTrue(LogLevel.WARN.admits(LogLevel.ERROR))
        assertFalse(LogLevel.WARN.admits(LogLevel.INFO))
        assertFalse(LogLevel.ERROR.admits(LogLevel.WARN))
    }
}
