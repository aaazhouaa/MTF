package com.aazhou.mtforum

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.aazhou.mtforum.ui.common.ContentBlock
import com.aazhou.mtforum.ui.common.StyleSpan
import com.aazhou.mtforum.ui.common.buildBodyText
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
    fun smileyIsDownloadedButMarkedForInlineSize() {
        // 表情也要下载（离线完整），但必须打上 smiley class，CSS 才能把它限成行内小图；
        // smilieid 要保留，应用内渲染才认得出它是表情
        val html = "<img src=\"https://cdn.binmt.cc/static/image/smiley/doge/7.png\" smilieid=\"1359\" />"
        val out = com.aazhou.mtforum.data.export.sanitizeContentHtml(html) { "file:///x.jpg" }
        assertTrue("应打上 smiley class: $out", out.contains("class=\"smiley\""))
        assertTrue("应保留 smilieid: $out", out.contains("smilieid=\"1\""))
        assertTrue("src 应改写成本地地址: $out", out.contains("src=\"file:///x.jpg\""))
        // 普通配图不带 smiley class
        val plain = com.aazhou.mtforum.data.export.sanitizeContentHtml(
            "<img src=\"https://cdn.binmt.cc/data/attachment/forum/x.png\" />",
        ) { "file:///y.jpg" }
        assertTrue("普通配图不应有 smiley class: $plain", !plain.contains("smiley"))
    }

    @Test
    fun localFileImageStaysLocalAddress() {
        // 已下载帖子的离线图：src 是 file:// 本地地址，不能拼成站点域名
        val html = "<img src=\"file:///data/user/0/com.aazhou.mtforum/files/thread_downloads/174181/images/abc.jpg\" />"
        val blocks = parseContentBlocks(html)
        val image = blocks.filterIsInstance<ContentBlock.Image>().single()
        assertEquals(
            "file:///data/user/0/com.aazhou.mtforum/files/thread_downloads/174181/images/abc.jpg",
            image.url,
        )
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
        // 站点正文常以 &nbsp;<br> 开头：空行要去掉（否则昵称行与正文之间多一行空白），
        // 行首空白也一律吃掉，与网页吞掉行首空白的渲染一致
        val blocks = parseContentBlocks("&nbsp;<br>&nbsp;看看隐藏")
        assertEquals(1, blocks.size)
        assertEquals("看看隐藏", (blocks[0] as ContentBlock.Text).text)
    }

    @Test
    fun lineStartWhitespaceIsEatenLikeBrowser() {
        // 网页会把每个换行后的行首空白吃掉，正文所有行都顶到同一条左边界
        val text = parseContentBlocks("&nbsp;&nbsp;第一段<br><br>&nbsp;&nbsp;第二段<br>&nbsp;&nbsp;第三行")
            .filterIsInstance<ContentBlock.Text>().single().text
        assertEquals("第一段\n\n第二段\n第三行", text)
    }

    @Test
    fun whitespaceInsideLineIsKept() {
        // 行中间的空白是排版分隔，不能被误删
        val text = parseContentBlocks("文字 中 间")
            .filterIsInstance<ContentBlock.Text>().single().text
        assertEquals("文字 中 间", text)
    }

    @Test
    fun middleBlankLineIsKept() {
        // 段落中间的空行是作者本意的分段，必须保留（网页上就是一个空行）
        val blocks = parseContentBlocks("第一段<br><br>第二段")
        val texts = blocks.filterIsInstance<ContentBlock.Text>().map { it.text }
        assertEquals(1, texts.size)
        assertTrue(texts[0].contains("第一段") && texts[0].contains("第二段"))
        assertTrue(texts[0].contains("第一段\n\n第二段"))
    }

    @Test
    fun excessiveBreaksCollapseToOneBlankLine() {
        // 3 个以上连续换行是模板/图片残留，压成一个空行，不叠成大片空白
        val text = parseContentBlocks("上<br><br><br><br>下").filterIsInstance<ContentBlock.Text>().single().text
        assertEquals("上\n\n下", text)
    }

    @Test
    fun breaksAroundImagesDoNotLeaveBlankBlocks() {
        // 图片前后的连续换行只是抽图残留，不能变成空白文本块，图片仍要相邻成组
        val blocks = parseContentBlocks(
            img("https://a/1.png") + "<br><br><br>" + img("https://a/2.png"),
        )
        assertTrue(blocks.none { it is ContentBlock.Text })
        assertEquals(2, blocks.filterIsInstance<ContentBlock.ImageGroup>().single().urls.size)
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
    fun codeBlockBecomesCodeBlockWithOneLinePerLi() {
        // 站点把 [code] 烘成 div.comiis_blockcode > ol > li，一个 li 一行，
        // 不能退化成正文那种带 "1. " 前缀的编号列表
        val html = "有人找我要，顺便冒个泡<br>" +
            "<div class=\"comiis_blockcode comiis_bodybg b_ok f_b\"><div class=\"bg_f b_l\"><ol>" +
            "<li>#2026_1008_1712<br>\n</li>" +
            "<li>#com.catchingnow.icebox<br>\n</li>" +
            "<li>&nbsp;&nbsp;.registers 1<br>\n</li>" +
            "<li><br>\n</li>" +
            "<li>.end method<br>\n</li>" +
            "<li>```</li></ol></div></div><br>" +
            "<a href=\"https://bbs.binmt.cc/forum.php?mod=attachment&aid=1\">原包</a>"
        val blocks = parseContentBlocks(html)
        val code = blocks.filterIsInstance<ContentBlock.CodeBlock>().single()
        assertEquals(
            listOf(
                "#2026_1008_1712",
                "#com.catchingnow.icebox",
                "  .registers 1",
                "",
                ".end method",
                "```",
            ),
            code.lines,
        )
        assertTrue("代码行不能被加上编号前缀: ${code.lines}", code.lines.none { it.startsWith("1. ") })
    }

    @Test
    fun codeBlockKeepsLeadingIndentOfCode() {
        // 行首的 &nbsp; 是代码缩进，归一成普通空格后必须留下，不能像正文行首空白被吃掉
        val code = parseContentBlocks(
            "<div class=\"comiis_blockcode\"><ol>" +
                "<li>&nbsp;&nbsp;.registers 1</li>" +
                "<li>.end method</li></ol></div>",
        ).filterIsInstance<ContentBlock.CodeBlock>().single()
        assertEquals("  .registers 1", code.lines[0])
        assertTrue(code.lines.none { it.contains('\u00A0') })
    }

    @Test
    fun codeBlockKeepsBlankLineSoLineNumberDoesNotSkip() {
        // 空行也要占一个行号，渲染层按 lines.size 编号才不会跳号
        val code = parseContentBlocks(
            "<div class=\"comiis_blockcode\"><ol>" +
                "<li>.registers 1</li>" +
                "<li><br></li>" +
                "<li>.end method</li></ol></div>",
        ).filterIsInstance<ContentBlock.CodeBlock>().single()
        assertEquals(listOf(".registers 1", "", ".end method"), code.lines)
    }

    @Test
    fun textAroundCodeBlockStaysText() {
        // 代码块前后的普通文字仍是正文块，且顺序不变
        val html = "前面<br><div class=\"comiis_blockcode\"><ol><li>code</li></ol></div><br>后面"
        val blocks = parseContentBlocks(html)
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is ContentBlock.Text)
        assertEquals("前面", (blocks[0] as ContentBlock.Text).text)
        assertTrue(blocks[1] is ContentBlock.CodeBlock)
        assertEquals(listOf("code"), (blocks[1] as ContentBlock.CodeBlock).lines)
        assertTrue(blocks[2] is ContentBlock.Text)
        assertEquals("后面", (blocks[2] as ContentBlock.Text).text)
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
        // font color 不产出任何 span：站点颜色一律不采用，见 siteInlineColorIsNotApplied
        val redIndex = text.text.indexOf('红')
        assertTrue(
            "font color 不应产出 span: ${text.spans}",
            text.spans.none { it.start == redIndex && it.end == redIndex + 1 },
        )
        assertEquals("https://bbs.binmt.cc/x", spanOf('链').linkUrl)
    }

    @Test
    fun blockBoundaryBecomesLineBreak() {
        // 站点把每行烘成 <div align="left">，块与块之间连空白都没有（实测 </div><div align="left">）；
        // 网页按块边界换行，解析器必须补上同一个换行，否则整段粘成一行
        val text = parseContentBlocks(
            "<div align=\"left\">软件特点</div>" +
                "<div align=\"left\">第三方开发的酷安桌面客户端，把移动端社区搬上电脑大屏。</div>" +
                "<div align=\"left\">支持浏览帖子、查看评论、点赞收藏等操作，日常刷酷安够用。</div>",
        ).filterIsInstance<ContentBlock.Text>().single().text
        assertEquals(
            "软件特点\n" +
                "第三方开发的酷安桌面客户端，把移动端社区搬上电脑大屏。\n" +
                "支持浏览帖子、查看评论、点赞收藏等操作，日常刷酷安够用。",
            text,
        )
    }

    @Test
    fun blockBoundaryWithWhitespaceStillOneBreak() {
        // 块之间有空白（空格或 &nbsp;）时也不能多出一个空行：网页上仍是一行一句
        val text = parseContentBlocks("<div>甲</div>&nbsp;&nbsp;<div>乙</div>  <div>丙</div>")
            .filterIsInstance<ContentBlock.Text>().single().text
        assertEquals("甲\n乙\n丙", text)
    }

    @Test
    fun paragraphBoundaryBecomesLineBreak() {
        val text = parseContentBlocks("<p>甲</p><p>乙</p>")
            .filterIsInstance<ContentBlock.Text>().single().text
        assertEquals("甲\n乙", text)
    }

    @Test
    fun blankBlockDoesNotProduceBlankLine() {
        // 模板空块（只有空白/&nbsp;/<br> 的 div）不能凭空撑出空行
        val text = parseContentBlocks("<div>甲</div><div>&nbsp;</div><div><br><br></div><div>乙</div>")
            .filterIsInstance<ContentBlock.Text>().single().text
        assertEquals("甲\n乙", text)
    }

    @Test
    fun imagesInBlockContainersStillGroup() {
        // 每张图各占一个 div 时仍要相邻成组，块边界不能把它们拆散
        val blocks = parseContentBlocks(
            "<div>" + img("https://a/1.png") + "</div><div>" + img("https://a/2.png") + "</div>",
        )
        assertEquals(2, blocks.filterIsInstance<ContentBlock.ImageGroup>().single().urls.size)
        assertTrue(blocks.none { it is ContentBlock.Text })
    }

    @Test
    fun siteInlineColorIsNotApplied() {
        // 站点 UBB 编辑器挑的彩虹字/荧光字一律按正文字色显示（tid=174566 的 12# 评论实例）
        val text = parseContentBlocks(
            "随便涨涨到1波万，嘿嘿，" +
                "<font color=\"#FF0000\">我</font><font color=\"#00FF00\">有</font><font color=\"#0000FF\">了</font>" +
                "，早买早享受，再拖说不定还得涨，",
        ).filterIsInstance<ContentBlock.Text>().single()
        assertEquals("随便涨涨到1波万，嘿嘿，我有了，早买早享受，再拖说不定还得涨，", text.text)
        assertTrue("font color 不应产出任何 span: ${text.spans}", text.spans.isEmpty())
    }

    @Test
    fun siteInlineColorWithSizeKeepsSizeOnly() {
        // 同一个 font 上的 size 仍要生效，只是颜色被丢掉
        val text = parseContentBlocks("<font color=\"#FF0000\" size=\"5\">标题</font>正文")
            .filterIsInstance<ContentBlock.Text>().single()
        assertEquals("标题正文", text.text)
        assertEquals(1, text.spans.size)
        assertEquals(1.3f, text.spans.single().fontSizeScale)
    }

    @Test
    fun fontSizeScaleAppliesToHeadingSpanOnly() {
        // 站点把标题包进 <font size="6">、同段后面的普通行不包：网页上逐行各自生效，
        // 整块取最大缩放会把普通行一起放大
        val base = 17.sp
        val block = parseContentBlocks("""<font size="6">大标题</font>普通文字""")
            .filterIsInstance<ContentBlock.Text>().single()
        val body = buildBodyText(
            text = block.text,
            smiles = block.smiles,
            spans = block.spans,
            baseFontSize = base,
            linkColor = Color.Unspecified,
            codeBackground = Color.White,
        )
        assertEquals(1, body.spanStyles.size)
        val heading = body.spanStyles.single()
        assertEquals(0, heading.start)
        assertEquals(3, heading.end)
        assertEquals(base * 1.45f, heading.item.fontSize)
    }

    @Test
    fun fontSizeScaleFiveIsSmallerThanSix() {
        val base = 17.sp
        val block = parseContentBlocks("""<font size="5">小标题</font>普通文字""")
            .filterIsInstance<ContentBlock.Text>().single()
        val body = buildBodyText(
            text = block.text,
            smiles = block.smiles,
            spans = block.spans,
            baseFontSize = base,
            linkColor = Color.Unspecified,
            codeBackground = Color.White,
        )
        assertEquals(base * 1.3f, body.spanStyles.single().item.fontSize)
    }

    @Test
    fun plainBodySpansCarryNoFontSize() {
        // 不含 font size 的正文不能产出任何带字号的 span，否则普通段落会被无谓改变字号
        val block = parseContentBlocks("<b>粗</b>普通文字")
            .filterIsInstance<ContentBlock.Text>().single()
        val body = buildBodyText(
            text = block.text,
            smiles = block.smiles,
            spans = block.spans,
            baseFontSize = 17.sp,
            linkColor = Color.Unspecified,
            codeBackground = Color.White,
        )
        assertEquals(1, body.spanStyles.size)
        assertTrue(body.spanStyles.all { it.item.fontSize == TextUnit.Unspecified })
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

    @Test
    fun bareUrlInPlainTextBecomesLink() {
        // 用户贴图床直链时常不加链接标签，网页靠浏览器自动识别，客户端必须自己补
        val url = "http://i0.hdslb.com/bfs/new_dyn/be7adf.png?bili_only=0"
        val text = parseContentBlocks("B站加图测试：$url 为什么打不开。")
            .filterIsInstance<ContentBlock.Text>().single()
        val span = text.spans.single { it.linkUrl != null }
        assertEquals(url, span.linkUrl)
        assertEquals(url, text.text.substring(span.start, span.end))
    }

    @Test
    fun bareUrlTailPunctuationIsNotPartOfLink() {
        val text = parseContentBlocks("看图（http://i0.hdslb.com/x.png）。")
            .filterIsInstance<ContentBlock.Text>().single()
        assertEquals("http://i0.hdslb.com/x.png", text.spans.single { it.linkUrl != null }.linkUrl)
    }

    @Test
    fun anchorWrappedUrlIsNotDuplicatedAsBareLink() {
        val url = "http://i0.hdslb.com/x.png"
        val text = parseContentBlocks("<a href=\"$url\">$url</a>")
            .filterIsInstance<ContentBlock.Text>().single()
        assertEquals(1, text.spans.count { it.linkUrl != null })
        assertEquals(url, text.spans.single { it.linkUrl != null }.linkUrl)
    }

    @Test
    fun atMentionAnchorBecomesLinkSpan() {
        // 站点把 @ 到的人烘成指向其用户空间的链接（评论与首帖同一套楼层结构）
        val html = "底包用的<a href=\"https://bbs.binmt.cc/space-uid-113924.html\" target=\"_blank\">" +
            "@清扬婉兮</a> ，感谢坛友提供的帖子"
        val text = parseContentBlocks(html).filterIsInstance<ContentBlock.Text>().single()
        val span = text.spans.single { it.linkUrl != null }
        assertEquals("https://bbs.binmt.cc/space-uid-113924.html", span.linkUrl)
        assertEquals("@清扬婉兮", text.text.substring(span.start, span.end))
    }

    @Test
    fun externalHttpImageIsUpgradedToHttps() {
        // 网络策略只对站内域名放开明文，站外图床的 http 直链会被系统拦掉、图显示成空白占位
        val image = parseContentBlocks("<img src=\"http://i0.hdslb.com/bfs/new_dyn/4fb5b4d5.jpg\" />")
            .filterIsInstance<ContentBlock.Image>().single()
        assertEquals("https://i0.hdslb.com/bfs/new_dyn/4fb5b4d5.jpg", image.url)
    }

    @Test
    fun videoTagBecomesVideoBlock() {
        val html = "看视频<video src=\"https://cdn.binmt.cc/data/attachment/forum/v.mp4\" controls></video>"
        val video = parseContentBlocks(html).filterIsInstance<ContentBlock.Video>().single()
        assertEquals("https://cdn.binmt.cc/data/attachment/forum/v.mp4", video.url)
        assertTrue(parseContentBlocks(html).none { it is ContentBlock.Image })
    }

    @Test
    fun videoSourceTagIsUsedAsVideoUrl() {
        // 移动端模板把真实地址放在内部 source 上，video 自身 src 为空
        val html = "<video controls><source src=\"https://cdn.binmt.cc/a.mov\"></video>"
        val video = parseContentBlocks(html).filterIsInstance<ContentBlock.Video>().single()
        assertEquals("https://cdn.binmt.cc/a.mov", video.url)
    }

    @Test
    fun anchorWithVideoHrefAndCoverImgBecomesVideoWithPoster() {
        // 附件视频被包在链接里、封面是普通图；不能再额外产出一张静态图
        val html = "<a href=\"https://cdn.binmt.cc/clip.mp4\">" +
            "<img src=\"https://cdn.binmt.cc/cover.jpg\" /></a>"
        val blocks = parseContentBlocks(html)
        val video = blocks.filterIsInstance<ContentBlock.Video>().single()
        assertEquals("https://cdn.binmt.cc/clip.mp4", video.url)
        assertEquals("https://cdn.binmt.cc/cover.jpg", video.poster)
        assertTrue(blocks.none { it is ContentBlock.Image })
    }

    @Test
    fun bareVideoLinkWithoutPosterBecomesVideoBlock() {
        val html = "下载<a href=\"https://bbs.binmt.cc/forum.php?mod=attachment&aid=9\">附件</a>" +
            "<a href=\"https://cdn.binmt.cc/plain.mp4?token=1\">播放</a>"
        val videos = parseContentBlocks(html).filterIsInstance<ContentBlock.Video>()
        assertEquals(1, videos.size)
        assertEquals("https://cdn.binmt.cc/plain.mp4?token=1", videos.single().url)
        assertTrue(videos.single().poster == null)
    }

    @Test
    fun embedLegacyAttachmentIsTreatedAsVideo() {
        val html = "<embed src=\"https://cdn.binmt.cc/old.webm\" type=\"video/webm\"></embed>"
        val video = parseContentBlocks(html).filterIsInstance<ContentBlock.Video>().single()
        assertEquals("https://cdn.binmt.cc/old.webm", video.url)
    }

    @Test
    fun relativeVideoUrlIsNormalizedToHttps() {
        val html = "<video src=\"data/attachment/forum/rel.mp4\"></video>"
        val video = parseContentBlocks(html).filterIsInstance<ContentBlock.Video>().single()
        assertEquals("https://bbs.binmt.cc/data/attachment/forum/rel.mp4", video.url)
    }

    @Test
    fun imageAttachmentListIsStillImagesNotVideos() {
        val html = "<ul class=\"comiis_img_list\"><li>" +
            "<a href=\"https://bbs.binmt.cc/forum.php?mod=attachment&aid=3\">" +
            "<img src=\"none.gif\" file=\"https://cdn.binmt.cc/pic.jpg\" /></a></li></ul>"
        val blocks = parseContentBlocks(html)
        assertTrue(blocks.none { it is ContentBlock.Video })
        assertEquals("https://cdn.binmt.cc/pic.jpg", blocks.filterIsInstance<ContentBlock.Image>().single().url)
    }

    @Test
    fun videoInsideAttachmentListIsNotDroppedAsText() {
        // 附件列表的 li 里是 video 时不能走列表文本化（否则视频整体丢失）
        val html = "<ul class=\"comiis_img_list\"><li>" +
            "<video src=\"https://cdn.binmt.cc/att.mp4\"></video></li></ul>"
        val video = parseContentBlocks(html).filterIsInstance<ContentBlock.Video>().single()
        assertEquals("https://cdn.binmt.cc/att.mp4", video.url)
    }

    @Test
    fun residualQuoteBlockDropsPublishedByMetaLine() {
        // 引用块没被收成 PostQuote 时整块留在正文里，开头「作者 发表于 时间」不是被引内容
        val html = "<div class=\"comiis_quote\"><blockquote>" +
            "<font size=\"2\">回复</font> <font color=\"#999999\">heidong4686 发表于 1 分钟前</font><br>" +
            "频道里面是你的账号被封了</blockquote></div>我自己的正文"
        val blocks = parseContentBlocks(html)
        val quote = blocks.filterIsInstance<ContentBlock.Quote>().single()
        assertEquals("频道里面是你的账号被封了", quote.text)
        assertEquals("我自己的正文", blocks.filterIsInstance<ContentBlock.Text>().single().text)
    }

    @Test
    fun residualQuoteBlockKeepsBodyWithoutMetaLine() {
        // 开头不是「发表于」元信息时整块都是被引正文，一个字符都不能少
        val html = "<blockquote>只有被引正文<br>第二行</blockquote>"
        val quote = parseContentBlocks(html).filterIsInstance<ContentBlock.Quote>().single()
        assertEquals("只有被引正文 第二行", quote.text)
    }

    @Test
    fun residualQuoteBlockDropsMetaLineWhenSeparatedByBlockElement() {
        // 编辑提交后站点烘回来的是 PC 版结构：meta 与正文各占一个 div、没有 <br>。
        // 只按 <br> 切会把 meta 连同正文一起当被引内容，meta 于是残在正文里
        val html = "<div class=\"comiis_quote\"><blockquote>" +
            "<div><a href=\"x\"><font color=\"#999999\">heidong4686 发表于 7 天前</font></a></div>" +
            "<div>频道里面是你的账号被封了</div>" +
            "</blockquote></div>我自己的正文"
        val blocks = parseContentBlocks(html)
        val quote = blocks.filterIsInstance<ContentBlock.Quote>().single()
        assertEquals("频道里面是你的账号被封了", quote.text)
        assertEquals("我自己的正文", blocks.filterIsInstance<ContentBlock.Text>().single().text)
    }

    @Test
    fun residualQuoteBlockDropsMetaLineWhenBlockElementPrecedesIt() {
        // meta 前还压着别的块级元素：仍要按行找「发表于」，不能认第一行就是 meta
        val html = "<div class=\"comiis_quote\"><blockquote>" +
            "<div>引用提示</div>" +
            "<div>heidong4686 发表于 7 天前</div>" +
            "<div>频道里面是你的账号被封了</div>" +
            "</blockquote></div>"
        val quote = parseContentBlocks(html).filterIsInstance<ContentBlock.Quote>().single()
        assertEquals("频道里面是你的账号被封了", quote.text)
    }

    @Test
    fun unlockedHiddenContentBecomesHiddenBlockWithClickableLink() {
        // 解锁后站点把内容包在 div.comiis_quote 里，首个子元素是 h2.f_a 标签；
        // 它是已解锁的隐藏内容，不是引用也不该只当纯文本
        val html = "<div class=\"comiis_quote bg_h f_c\"><h2 class=\"f_a\">本帖隐藏的内容: </h2>" +
            "https://github.com/aaazhouaa/MTF/releases</div>"
        val hidden = parseContentBlocks(html).filterIsInstance<ContentBlock.Hidden>().single()
        assertEquals("本帖隐藏的内容:", hidden.label)
        val text = hidden.blocks.filterIsInstance<ContentBlock.Text>().single()
        assertEquals("https://github.com/aaazhouaa/MTF/releases", text.text)
        assertEquals(
            "https://github.com/aaazhouaa/MTF/releases",
            text.spans.single { it.linkUrl != null }.linkUrl,
        )
    }

    @Test
    fun unlockedHiddenContentKeepsAnchorLinkAndMultipleParagraphs() {
        // 容器内带 <a> 的链接与 <br> 分隔的多行都要保留，不能被压成一行纯文本
        val html = "<div class=\"comiis_quote bg_h f_c\"><h2 class=\"f_a\">本帖隐藏的内容: </h2>" +
            "<a href=\"https://pan.baidu.com/s/abc\">度盘下载</a><br />密码：4xq6</div>"
        val hidden = parseContentBlocks(html).filterIsInstance<ContentBlock.Hidden>().single()
        val text = hidden.blocks.filterIsInstance<ContentBlock.Text>().single()
        assertEquals("度盘下载\n密码：4xq6", text.text)
        val span = text.spans.single { it.linkUrl != null }
        assertEquals("https://pan.baidu.com/s/abc", span.linkUrl)
        assertEquals("度盘下载", text.text.substring(span.start, span.end))
    }

    @Test
    fun lockedHiddenPromptIsStillAQuoteNotHidden() {
        // 锁定态容器里没有 h2.f_a 标签，仍是引用块路径（锁定提示由 ForumParser 剥除）
        val html = "<div class=\"comiis_quote bg_h f_c\">游客，如果您要查看本帖隐藏内容请回复</div>正文"
        val blocks = parseContentBlocks(html)
        assertTrue(blocks.none { it is ContentBlock.Hidden })
        assertTrue(blocks.first() is ContentBlock.Quote)
    }

    @Test
    fun imagesInsideUnlockedHiddenContentStayInCard() {
        // 已解锁隐藏内容里的图片属于卡片内容，不能被提到卡片外面（那是锁定态的补救）
        val html = "正文<div class=\"comiis_quote bg_h f_c\"><h2 class=\"f_a\">本帖隐藏的内容: </h2>" +
            "<img src=\"https://cdn.binmt.cc/data/attachment/forum/p.jpg\" /></div>"
        val blocks = parseContentBlocks(html)
        assertTrue(blocks.none { it is ContentBlock.Image })
        val hidden = blocks.filterIsInstance<ContentBlock.Hidden>().single()
        assertEquals(
            "https://cdn.binmt.cc/data/attachment/forum/p.jpg",
            hidden.blocks.filterIsInstance<ContentBlock.Image>().single().url,
        )
    }

    @Test
    fun unlockedHiddenContentImageEntersImageSequence() {
        // 全帖图片序列（全屏查看/离线下载）要递归进隐藏内容卡片收图
        val html = "<div class=\"comiis_quote bg_h f_c\"><h2 class=\"f_a\">本帖隐藏的内容: </h2>" +
            "<img src=\"https://cdn.binmt.cc/data/attachment/forum/p.jpg\" /></div>"
        assertEquals(
            listOf("https://cdn.binmt.cc/data/attachment/forum/p.jpg"),
            com.aazhou.mtforum.ui.common.collectImageUrls(html),
        )
    }

    @Test
    fun hiddenContentTextEntersPlainText() {
        // 复制正文取纯文本时隐藏内容也要在，不能整块丢
        val html = "正文<div class=\"comiis_quote bg_h f_c\"><h2 class=\"f_a\">本帖隐藏的内容: </h2>" +
            "https://github.com/aaazhouaa/MTF</div>"
        val text = com.aazhou.mtforum.ui.common.plainTextOf(html)
        assertTrue(text.contains("正文"))
        assertTrue(text.contains("https://github.com/aaazhouaa/MTF"))
    }
}
