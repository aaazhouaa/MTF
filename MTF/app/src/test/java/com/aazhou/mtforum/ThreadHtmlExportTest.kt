package com.aazhou.mtforum

import com.aazhou.mtforum.data.export.dataUriOf
import com.aazhou.mtforum.data.export.sanitizeContentHtml
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadHtmlExportTest {

    @Test
    fun scriptsAndEventAttributesAreStripped() {
        val html = "<p onclick=\"track()\">正文<script>alert(1)</script></p>"
        val result = sanitizeContentHtml(html) { null }
        assertTrue(result.contains("正文"))
        assertFalse(result.contains("script"))
        assertFalse(result.contains("alert"))
        assertFalse(result.contains("onclick"))
    }

    @Test
    fun lazyLoadedImageIsRewrittenToLocalSource() {
        // 站点懒加载：src 是占位图，真实地址在 file 属性里；改写后只留本地 src
        val html = "<img src=\"none.gif\" file=\"https://cdn.binmt.cc/a.png\" onerror=\"x()\">"
        val rewritten = mutableMapOf("https://cdn.binmt.cc/a.png" to "file:///data/a.png")
        val result = sanitizeContentHtml(html) { rewritten[it] }
        assertTrue(result.contains("src=\"file:///data/a.png\""))
        assertFalse(result.contains("none.gif"))
        assertFalse(result.contains("file="))
        assertFalse(result.contains("onerror"))
    }

    @Test
    fun imageWithoutLocalSourceKeepsOriginal() {
        // 下载失败的图保留原 src，交给渲染侧兜底
        val html = "<img src=\"https://cdn.binmt.cc/b.jpg\">"
        val result = sanitizeContentHtml(html) { null }
        assertTrue(result.contains("src=\"https://cdn.binmt.cc/b.jpg\""))
    }

    @Test
    fun placeholderOnlyImageIsLeftAlone() {
        // 纯占位图（无真实地址可解析）不应被改写
        val html = "<img src=\"none.gif\">"
        val result = sanitizeContentHtml(html) { "file:///never" }
        assertTrue(result.contains("src=\"none.gif\""))
    }

    @Test
    fun dataUriCarriesMimeByExtension() {
        assertTrue(dataUriOf("https://x/a.png", byteArrayOf(1)).startsWith("data:image/png;base64,"))
        assertTrue(dataUriOf("https://x/a.gif", byteArrayOf(1)).startsWith("data:image/gif;base64,"))
        assertTrue(dataUriOf("https://x/a.webp", byteArrayOf(1)).startsWith("data:image/webp;base64,"))
        assertTrue(dataUriOf("https://x/a", byteArrayOf(1)).startsWith("data:image/jpeg;base64,"))
    }
}
