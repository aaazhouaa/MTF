package com.aazhou.mtforum

import com.aazhou.mtforum.ui.common.ContentBlock
import com.aazhou.mtforum.ui.common.parseContentBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlContentTest {

    @Test
    fun smileyIsInlinedNotRenderedAsFullWidthImage() {
        val html = "你住论坛了，随处可见都是你" +
            "<img src=\"https://cdn.binmt.cc/static/image/smiley/doge/7.png\" smilieid=\"1359\" border=\"0\" />"
        val blocks = parseContentBlocks(html)
        assertEquals(1, blocks.size)
        val text = blocks[0] as ContentBlock.Text
        assertTrue(blocks.none { it is ContentBlock.Image })
        // 表情被抽成占位字符，正文文字保留
        assertEquals("你住论坛了，随处可见都是你\uE000", text.text)
        assertEquals("https://cdn.binmt.cc/static/image/smiley/doge/7.png", text.smiles['\uE000'])
    }

    @Test
    fun lazyLoadedAttachmentIsStillAnImage() {
        val html = "看图<br /><img src=\"none.gif\" file=\"https://cdn.binmt.cc/data/attachment/forum/x.png\" />"
        val blocks = parseContentBlocks(html)
        assertEquals(1, blocks.filterIsInstance<ContentBlock.Image>().size)
        assertEquals(
            "https://cdn.binmt.cc/data/attachment/forum/x.png",
            blocks.filterIsInstance<ContentBlock.Image>().single().url,
        )
    }

    @Test
    fun nonePngPlaceholderFallsBackToLazyAttribute() {
        // 站点懒加载：src 是 none.png 占位，真实地址在 comiis_loadimages
        val html = "看图<img src=\"https://cdn.binmt.cc/template/comiis_app/pic/none.png\" " +
            "comiis_loadimages=\"https://cdn.binmt.cc/data/attachment/forum/real.jpg\" class=\" comiis_loadimages\" />"
        val image = parseContentBlocks(html).filterIsInstance<ContentBlock.Image>().single()
        assertEquals("https://cdn.binmt.cc/data/attachment/forum/real.jpg", image.url)
    }

    @Test
    fun imageloadingPlaceholderIsSkippedForFileAttribute() {
        val html = "图<img src=\"https://cdn.binmt.cc/template/comiis_app/pic/imageloading.gif\" " +
            "file=\"https://cdn.binmt.cc/data/attachment/forum/y.png\" />"
        val image = parseContentBlocks(html).filterIsInstance<ContentBlock.Image>().single()
        assertEquals("https://cdn.binmt.cc/data/attachment/forum/y.png", image.url)
    }

    @Test
    fun placeholderOnlyImageIsDropped() {
        // 只剩下占位图时不应产出图片块
        val html = "文字<img src=\"https://cdn.binmt.cc/template/comiis_app/pic/none.png\" class=\"comiis_loadimages\" />"
        assertTrue(parseContentBlocks(html).none { it is ContentBlock.Image })
    }

    @Test
    fun quoteBlockBecomesQuoteBlock() {
        val blocks = parseContentBlocks("<div class=\"comiis_quote\">游客，回复可见</div>正文")
        assertTrue(blocks.first() is ContentBlock.Quote)
    }

    @Test
    fun blankParagraphsAndRepeatedBreaksAreDropped() {
        // 模板空块与图片抽走后的连续换行不应渲染成空白
        val blocks = parseContentBlocks(
            "<p>&nbsp;</p><p>正文</p><div><br><br></div><br><br>结尾",
        )
        val texts = blocks.filterIsInstance<ContentBlock.Text>().map { it.text }
        assertTrue(texts.none { it.isBlank() })
        assertTrue(texts.any { it.contains("正文") })
        assertTrue(texts.any { it.contains("结尾") })
    }

    @Test
    fun leadingWhitespaceBeforeContentIsTrimmed() {
        // 正文以 &nbsp;<br> 开头时，不要在昵称行与正文之间多出空行
        val blocks = parseContentBlocks("&nbsp;<br>&nbsp;看看隐藏")
        assertEquals(1, blocks.size)
        assertEquals("看看隐藏", (blocks[0] as ContentBlock.Text).text)
    }

    @Test
    fun middleBlankLineIsKept() {
        // 段落中间的空行是作者本意的分段，必须保留
        val blocks = parseContentBlocks("第一段<br><br>第二段")
        val texts = blocks.filterIsInstance<ContentBlock.Text>().map { it.text }
        assertEquals(1, texts.size)
        assertTrue(texts[0].contains("第一段") && texts[0].contains("第二段"))
    }

    private fun img(url: String) = "<img src=\"$url\" />"

    @Test
    fun continuousImagesAreGrouped() {
        val blocks = parseContentBlocks(img("https://a/1.png") + img("https://a/2.png"))
        assertEquals(1, blocks.size)
        val group = blocks.single() as ContentBlock.ImageGroup
        assertEquals(listOf("https://a/1.png", "https://a/2.png"), group.urls)
        assertTrue(blocks.none { it is ContentBlock.Image })
    }

    @Test
    fun imagesSeparatedByBreaksOrBlankParagraphsStayGrouped() {
        val blocks = parseContentBlocks(
            img("https://a/1.png") + "<br><br><p>&nbsp;</p>" + img("https://a/2.png") + img("https://a/3.png"),
        )
        val group = blocks.filterIsInstance<ContentBlock.ImageGroup>().single()
        assertEquals(3, group.urls.size)
    }

    @Test
    fun imagesSplitByTextAreNotGrouped() {
        val blocks = parseContentBlocks(img("https://a/1.png") + "中间有文字" + img("https://a/2.png"))
        assertTrue(blocks.none { it is ContentBlock.ImageGroup })
        assertEquals(2, blocks.filterIsInstance<ContentBlock.Image>().size)
    }

    @Test
    fun imagesSplitByQuoteAreNotGrouped() {
        val blocks = parseContentBlocks(
            img("https://a/1.png") + "<div class=\"comiis_quote\">游客，回复可见</div>" + img("https://a/2.png"),
        )
        assertTrue(blocks.none { it is ContentBlock.ImageGroup })
        assertEquals(2, blocks.filterIsInstance<ContentBlock.Image>().size)
    }

    @Test
    fun singleImageStaysSingle() {
        val blocks = parseContentBlocks("看图" + img("https://a/1.png"))
        assertEquals(1, blocks.filterIsInstance<ContentBlock.Image>().size)
        assertTrue(blocks.none { it is ContentBlock.ImageGroup })
    }
}
