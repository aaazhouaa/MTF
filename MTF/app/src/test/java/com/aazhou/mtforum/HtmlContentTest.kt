package com.aazhou.mtforum

import com.aazhou.mtforum.ui.common.ContentBlock
import com.aazhou.mtforum.ui.common.StyleSpan
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
    fun dataLazySrcIsUsedForLazyLoadedImage() {
        val html = "看图<img src=\"none.png\" data-lazy-src=\"https://cdn.binmt.cc/data/attachment/forum/z.jpg\" />"
        val image = parseContentBlocks(html).filterIsInstance<ContentBlock.Image>().single()
        assertEquals("https://cdn.binmt.cc/data/attachment/forum/z.jpg", image.url)
    }

    @Test
    fun binmtHttpImageIsUpgradedToHttps() {
        val html = "看图<img src=\"http://cdn.binmt.cc/data/attachment/forum/w.jpg\" />"
        val image = parseContentBlocks(html).filterIsInstance<ContentBlock.Image>().single()
        assertEquals("https://cdn.binmt.cc/data/attachment/forum/w.jpg", image.url)
    }

    @Test
    fun protocolRelativeImageBecomesHttps() {
        val html = "看图<img src=\"//cdn.binmt.cc/data/attachment/forum/v.jpg\" />"
        val image = parseContentBlocks(html).filterIsInstance<ContentBlock.Image>().single()
        assertEquals("https://cdn.binmt.cc/data/attachment/forum/v.jpg", image.url)
    }

    @Test
    fun imagesInsideQuoteContainerArePreserved() {
        // 「回复可见」的正文整块包在引用容器里，里面的图片不能被当作引用纯文本丢掉
        val html = "浏览图：<div class=\"comiis_quote\"><br>" +
            "<img src=\"https://cdn.binmt.cc/data/attachment/forum/p.jpg\" /><br>" +
            "<img src=\"https://cdn.binmt.cc/data/attachment/forum/q.jpg\" />" +
            "</div>"
        val blocks = parseContentBlocks(html)
        val group = blocks.filterIsInstance<ContentBlock.ImageGroup>().single()
        assertEquals(
            listOf(
                "https://cdn.binmt.cc/data/attachment/forum/p.jpg",
                "https://cdn.binmt.cc/data/attachment/forum/q.jpg",
            ),
            group.urls,
        )
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
    fun imageListContainerBecomesImageGroup() {
        // 附件图被解析器从 ul.comiis_img_list 并回正文后，渲染层要能展开 ul>li>span>img 并成组
        val html = "看看图<ul class=\"comiis_img_list cl\">" +
            "<li><span>" + img("https://oos.binmt.cc/1.jpg") + "</span></li>" +
            "<li><span>" + img("https://oos.binmt.cc/2.jpg") + "</span></li></ul>"
        val group = parseContentBlocks(html).filterIsInstance<ContentBlock.ImageGroup>().single()
        assertEquals(
            listOf("https://oos.binmt.cc/1.jpg", "https://oos.binmt.cc/2.jpg"),
            group.urls,
        )
    }

    @Test
    fun singleImageStaysSingle() {
        val blocks = parseContentBlocks("看图" + img("https://a/1.png"))
        assertEquals(1, blocks.filterIsInstance<ContentBlock.Image>().size)
        assertTrue(blocks.none { it is ContentBlock.ImageGroup })
    }

    @Test
    fun tableIsParsedIntoRowsAndCells() {
        // 帖子正文里的「▎新增」表格：每一行两列，单元格文本不能拼成一串
        val html = "<strong>▎新增</strong><br><br><table><tbody>" +
            "<tr><td><strong>1</strong></td><td>工作区 / 沙盒工具族接入 MCP</td></tr>" +
            "<tr><td>2</td><td>MCP 开关</td></tr>" +
            "<tr><td>3</td><td>默认工作区挂载虚拟环境</td></tr>" +
            "</tbody></table>"
        val table = parseContentBlocks(html).filterIsInstance<ContentBlock.Table>().single()
        assertEquals(3, table.rows.size)
        assertEquals(listOf("1", "工作区 / 沙盒工具族接入 MCP"), table.rows[0])
        assertEquals(listOf("3", "默认工作区挂载虚拟环境"), table.rows[2])
    }

    @Test
    fun tableWithThFirstRowGetsHeader() {
        val html = "<table><tr><th>名称</th><th>值</th></tr><tr><td>a</td><td>b</td></tr></table>"
        val table = parseContentBlocks(html).filterIsInstance<ContentBlock.Table>().single()
        assertEquals(listOf("名称", "值"), table.header)
        assertEquals(listOf(listOf("a", "b")), table.rows)
    }

    @Test
    fun tableRaggedRowsArePaddedToSameWidth() {
        val html = "<table><tr><td>a</td><td>b</td><td>c</td></tr><tr><td>d</td></tr></table>"
        val table = parseContentBlocks(html).filterIsInstance<ContentBlock.Table>().single()
        assertTrue(table.rows.all { it.size == 3 })
        assertEquals(listOf("d", "", ""), table.rows[1])
    }

    @Test
    fun inlineStylesProduceSpans() {
        val html = "<strong>粗</strong>平<em>斜</em><code>码</code>" +
            "<font color=\"#FF0000\">红</font><a href=\"https://bbs.binmt.cc/x\">链</a>"
        val text = parseContentBlocks(html).filterIsInstance<ContentBlock.Text>().single()
        assertEquals("粗平斜码红链", text.text)
        fun spanOf(ch: Char): StyleSpan {
            val i = text.text.indexOf(ch)
            return text.spans.first { it.start == i && it.end == i + 1 }
        }
        assertTrue(spanOf('粗').bold)
        assertTrue(spanOf('斜').italic)
        assertTrue(spanOf('码').code)
        assertEquals(0xFFFF0000L, spanOf('红').colorArgb)
        assertEquals("https://bbs.binmt.cc/x", spanOf('链').linkUrl)
    }

    @Test
    fun headingLevelIsCaptured() {
        val text = parseContentBlocks("<h2>标题</h2>").filterIsInstance<ContentBlock.Text>().single()
        assertEquals("标题", text.text)
        assertEquals(2, text.spans.first().headingLevel)
    }

    @Test
    fun unorderedListBecomesPrefixedLines() {
        val text = parseContentBlocks("<ul><li>甲</li><li>乙</li></ul>")
            .filterIsInstance<ContentBlock.Text>().single()
        assertTrue(text.text.contains("· 甲"))
        assertTrue(text.text.contains("· 乙"))
    }

    @Test
    fun orderedListBecomesNumberedLines() {
        val text = parseContentBlocks("<ol><li>a</li><li>b</li></ol>")
            .filterIsInstance<ContentBlock.Text>().single()
        assertTrue(text.text.contains("1. a"))
        assertTrue(text.text.contains("2. b"))
    }

    @Test
    fun javascriptLinkIsNotCarried() {
        val text = parseContentBlocks("<a href=\"javascript:void(0)\">点</a>")
            .filterIsInstance<ContentBlock.Text>().single()
        assertTrue(text.spans.all { it.linkUrl == null })
    }
}
