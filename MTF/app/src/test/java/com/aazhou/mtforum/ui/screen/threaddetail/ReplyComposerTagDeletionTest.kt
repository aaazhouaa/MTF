package com.aazhou.mtforum.ui.screen.threaddetail

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReplyComposerTagDeletionTest {

    @Test
    fun deleteOnClosedTag_stripsBothTags_leavesContent() {
        // [audio]哈哈[/audio]
        val oldText = "[audio]哈哈[/audio]"
        // 用户光标原本在末尾 (下标 17)，按退格删除了最后的 ']'
        val oldValue = TextFieldValue(oldText, TextRange(17))
        val newValue = TextFieldValue("[audio]哈哈[/audio", TextRange(16))

        val result = handleTagPairDeletion(oldValue, newValue)
        assertNotNull(result)
        assertEquals("哈哈", result!!.text)
        assertEquals(2, result.selection.start)
    }

    @Test
    fun deleteEmptyTagPair_removesEntireBlock() {
        // [audio][/audio]
        val oldText = "[audio][/audio]"
        // 用户光标在两括号中间 (下标 7)，按退格删除了 [audio] 结尾的 ']'
        val oldValue = TextFieldValue(oldText, TextRange(7))
        val newValue = TextFieldValue("[audio[/audio]", TextRange(6))

        val result = handleTagPairDeletion(oldValue, newValue)
        assertNotNull(result)
        assertEquals("", result!!.text)
        assertEquals(0, result.selection.start)
    }

    @Test
    fun deleteUrlTagWithContent_stripsTagsLeavesContent() {
        // [url=https://binmt.cc]我的博客[/url]
        val oldText = "[url=https://binmt.cc]我的博客[/url]"
        val len = oldText.length
        val oldValue = TextFieldValue(oldText, TextRange(len))
        val newValue = TextFieldValue(oldText.dropLast(1), TextRange(len - 1))

        val result = handleTagPairDeletion(oldValue, newValue)
        assertNotNull(result)
        assertEquals("我的博客", result!!.text)
        assertEquals("我的博客".length, result.selection.start)
    }

    @Test
    fun deleteNormalText_returnsNull() {
        val oldText = "你好世界"
        val oldValue = TextFieldValue(oldText, TextRange(4))
        val newValue = TextFieldValue("你好世", TextRange(3))

        val result = handleTagPairDeletion(oldValue, newValue)
        assertNull(result)
    }
}
