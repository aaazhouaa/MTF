package com.aazhou.mtforum.ui.screen.threaddetail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplyComposerAtTokenTest {

    @Test
    fun tokenAtEndOfReply() {
        // 「回复 @张三 你好」里光标在末尾，@token 早在空格处断开
        val text = "回复 @张三 你好"
        val result = atTokenAt(text, text.length)
        assertNull(result)
    }

    @Test
    fun tokenRightAfterAt() {
        val text = "你好 @"
        val result = atTokenAt(text, text.length)
        assertEquals(3 to "", result)
    }

    @Test
    fun tokenWithKeyword() {
        val text = "回复 @张三"
        val result = atTokenAt(text, text.length)
        assertEquals(3 to "张三", result)
    }

    @Test
    fun cursorInMiddleOfToken() {
        val text = "回复 @张三"
        // 光标停在「张」后面，只认到已输入的部分
        val result = atTokenAt(text, 5)
        assertEquals(3 to "张", result)
    }

    @Test
    fun tokenAfterNewline() {
        val text = "第一行\n@abc"
        val result = atTokenAt(text, text.length)
        assertEquals(4 to "abc", result)
    }

    @Test
    fun atInsideWordIsNotMention() {
        // 邮箱地址里的 @ 不是提及
        val text = "a@b.com"
        assertNull(atTokenAt(text, text.length))
    }

    @Test
    fun cursorAfterSpaceIsNotMention() {
        val text = "哈哈 @abc def"
        assertNull(atTokenAt(text, text.length))
    }

    @Test
    fun cursorOutOfRange() {
        assertNull(atTokenAt("@abc", -1))
        assertNull(atTokenAt("@abc", 99))
    }

    @Test
    fun filterPrefixFirstThenContains() {
        val users = listOf("xabc", "abc", "abcd")
        assertEquals(listOf("abc", "abcd", "xabc"), filterAtUsers(users, "ab"))
    }

    @Test
    fun filterIsCaseInsensitive() {
        val users = listOf("ZhangSan", "lisi")
        assertEquals(listOf("ZhangSan"), filterAtUsers(users, "zhang"))
    }

    @Test
    fun filterEmptyKeywordReturnsAll() {
        val users = listOf("b", "a", "c")
        assertEquals(users, filterAtUsers(users, ""))
    }

    @Test
    fun filterNoMatch() {
        assertEquals(emptyList<String>(), filterAtUsers(listOf("abc"), "zz"))
        assertEquals(emptyList<String>(), filterAtUsers(emptyList(), "ab"))
    }

    @Test
    fun normalizeAddsMissingSeparator() {
        // 输入法吞掉 @ 后的尾随空格，站点就只认到「Bin你好」这个不存在的用户名
        assertEquals("@Bin 你好", normalizeAtMentions("@Bin你好", listOf("Bin")))
    }

    @Test
    fun normalizeKeepsExistingSeparator() {
        val text = "@Bin 你好"
        assertEquals(text, normalizeAtMentions(text, listOf("Bin")))
    }

    @Test
    fun normalizeHandlesChinesePunctuation() {
        // 中文逗号不是站点正则里的 \s，同样要补
        assertEquals("@Bin ，你好", normalizeAtMentions("@Bin，你好", listOf("Bin")))
    }

    @Test
    fun normalizeLeavesUnknownAtAlone() {
        // 邮箱与手输的陌生人名不在名单里，补了空格站点也解析不了，不动用户正文
        assertEquals("a@b.com", normalizeAtMentions("a@b.com", listOf("Bin")))
        assertEquals("@路人甲 你好", normalizeAtMentions("@路人甲 你好", listOf("Bin")))
    }

    @Test
    fun normalizeMatchesLongestNameFirst() {
        // 名单同时含 Bin 与 Binx 时，第一个 @ 必须认长名，否则会切成「@Bin x你好」
        assertEquals(
            "@Binx 你好@Bin 你好",
            normalizeAtMentions("@Binx你好@Bin你好", listOf("Bin", "Binx")),
        )
    }

    @Test
    fun normalizeSkipsEndOfMessage() {
        // 行尾不补：站点提交时自己会追加一个空格
        assertEquals("你好@Bin", normalizeAtMentions("你好@Bin", listOf("Bin")))
        assertEquals("@Bin\n下一行", normalizeAtMentions("@Bin\n下一行", listOf("Bin")))
    }

    @Test
    fun normalizeWithoutNamesIsNoop() {
        val text = "@Bin你好"
        assertEquals(text, normalizeAtMentions(text, emptyList()))
    }

    @Test
    fun normalizeIsCaseInsensitive() {
        assertEquals("@bin 你好", normalizeAtMentions("@bin你好", listOf("Bin")))
    }
}
