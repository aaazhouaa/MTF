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
}
