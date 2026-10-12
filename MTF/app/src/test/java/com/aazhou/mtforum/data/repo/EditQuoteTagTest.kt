package com.aazhou.mtforum.data.repo

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 编辑提交前的引用标签转换。
 *
 * 站点编辑提交会剥掉小写的 `[quote]`/`[/quote]`/`[size=N]`/`[/size]`（引用块只剩内部文本、
 * 被引快照混进正文），渲染端却大小写不敏感，故提交前统一转成大写。
 */
class EditQuoteTagTest {

    @Test
    fun quoteAndSizeTagsBecomeUppercase() {
        val message = "[quote][size=2][url=forum.php?mod=redirect&goto=findpost&pid=1&ptid=2]" +
            "[color=#999999]大佬 发表于 3 小时前[/color][/url][/size]\n快照[/quote]\n\n正文"
        val out = protectQuoteTagsOnEdit(message)
        assertEquals(
            "[QUOTE][SIZE=2][url=forum.php?mod=redirect&goto=findpost&pid=1&ptid=2]" +
                "[color=#999999]大佬 发表于 3 小时前[/color][/url][/SIZE]\n快照[/QUOTE]\n\n正文",
            out,
        )
    }

    @Test
    fun alreadyUppercaseStaysUnchanged() {
        val message = "[QUOTE][SIZE=2][url=u][color=#999999]x 发表于 1 小时前[/color][/url][/SIZE]\n快照[/QUOTE]\n\n正文"
        assertEquals(message, protectQuoteTagsOnEdit(message))
    }

    @Test
    fun otherTagsAndTextUntouched() {
        val message = "[b]粗[/b][url=https://x]链接[/url][#呵呵][attachimg]12[/attachimg]普通文字"
        assertEquals(message, protectQuoteTagsOnEdit(message))
    }

    @Test
    fun messageWithoutQuoteStaysUnchanged() {
        val message = "就是一条普通回复[#哈哈]"
        assertEquals(message, protectQuoteTagsOnEdit(message))
    }

    @Test
    fun mixedCaseQuoteTagsAlsoProtected() {
        val message = "[Quote]快照[/quote]\n\n正文[size=3]大[/size]"
        assertEquals("[QUOTE]快照[/QUOTE]\n\n正文[SIZE=3]大[/SIZE]", protectQuoteTagsOnEdit(message))
    }
}
