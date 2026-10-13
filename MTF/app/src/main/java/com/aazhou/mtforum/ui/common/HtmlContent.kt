package com.aazhou.mtforum.ui.common

import com.aazhou.mtforum.data.parser.ForumParser
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.nodes.Comment

/** 段落内联表情的占位字符池（私用区），避免与正文冲突。 */
private const val SMILEY_CHAR_BASE = 0xE000

/**
 * 帖子正文的块级内容：引用、文本段、表格、表情或图片，按原始顺序排列以保留图文混排。
 *
 * [Text.smiles] 把正文里的表情抽成占位字符 -> 图片地址，渲染时以行内小图标还原，
 * 站点表情本身也是 <img>，若不单独识别会被当作正文大图铺满整屏。
 *
 * [Text.spans] 记录一段文字里局部的样式（加粗、颜色、标题、链接等），
 * 范围是 [Text.text] 中字符的下标，渲染时叠加到同一段文本上。
 */
sealed interface ContentBlock {
    data class Quote(val text: String) : ContentBlock
    data class Text(
        val text: String,
        val smiles: Map<Char, String> = emptyMap(),
        val spans: List<StyleSpan> = emptyList(),
    ) : ContentBlock
    data class Image(val url: String) : ContentBlock

    /**
     * 正文内嵌视频：附件视频直链或 <video>/<embed> 标签，封面取同级的懒加载图属性。
     * [poster] 为空时播放器与内联卡片用黑底占位。
     */
    data class Video(val url: String, val poster: String?) : ContentBlock

    /** 连续排列的图片组，交由网格布局展示。 */
    data class ImageGroup(val urls: List<String>) : ContentBlock

    /** 表格：可选的表头行与若干数据行，单元格仅取纯文本。 */
    data class Table(
        val header: List<String>?,
        val rows: List<List<String>>,
    ) : ContentBlock

    /**
     * 站点 `[code]` 块的代码内容：逐行保留原始缩进与空行。
     *
     * 行首空白是代码缩进的一部分，故不经正文那套「吃掉换行后行首空白」的处理，
     * 渲染时另起等宽字体卡片，不当成带序号的普通列表。
     */
    data class CodeBlock(val lines: List<String>) : ContentBlock

    /**
     * 正文附件下载框（div.comiis_attach）：文件名、下载链接与大小/积分说明。
     * 渲染成可下载卡片，还原网页端「文件」形态而非正文图片。
     */
    data class Attachment(
        val fileName: String,
        val url: String,
        val sizeText: String?,
    ) : ContentBlock
}

/**
 * 按正文原顺序提取全部图片地址，供全屏查看器把单图与其它图片串成一组左右翻页。
 */
fun collectImageUrls(html: String): List<String> =
    parseContentBlocks(html).flatMap { block ->
        when (block) {
            is ContentBlock.Image -> listOf(block.url)
            is ContentBlock.ImageGroup -> block.urls
            else -> emptyList()
        }
    }

/** 正文的纯文本（用于复制评论内容），图片/表格不产出文本。 */
fun plainTextOf(html: String): String =
    parseContentBlocks(html)
        .mapNotNull { block ->
            when (block) {
                is ContentBlock.Text -> block.text.takeIf { it.isNotBlank() }
                is ContentBlock.Quote -> block.text.takeIf { it.isNotBlank() }
                is ContentBlock.CodeBlock -> block.lines.joinToString("\n").takeIf { it.isNotBlank() }
                else -> null
            }
        }
        .joinToString("\n")
        .trim()

/** 单元格在表格行内的水平对齐。 */
enum class CellAlign { Start, Center, End }

/**
 * 一段文字的局部样式，[start]..[end] 为 [ContentBlock.Text.text] 内的字符区间（左闭右开）。
 *
 * 站点正文的强调都是内联标签（strong/em/u/s/code/font/a/h2 等），统一用该结构承载，
 * 渲染层再翻译成 Compose 的 SpanStyle / ParagraphStyle / LinkAnnotation。
 */
data class StyleSpan(
    val start: Int,
    val end: Int,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val code: Boolean = false,
    val headingLevel: Int = 0,
    val fontSizeScale: Float = 1f,
    val align: CellAlign? = null,
    val linkUrl: String? = null,
)

/** 解析器递归时携带的当前内联样式，子元素在此基础上叠加后再交还父级。 */
private data class InlineStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val code: Boolean = false,
    val headingLevel: Int = 0,
    val fontSizeScale: Float = 1f,
    val align: CellAlign? = null,
    val linkUrl: String? = null,
)

/** 渲染时叠加在当前文字上的"未收尾"样式，Range 用于在标签闭合时补上 [end]。 */
private class OpenSpan(val style: InlineStyle, val start: Int, val linkUrl: String?)

/**
 * 把 Discuz 正文 HTML 拆成块。
 *
 * 站点的图片是懒加载的：img 的 src 常为 none.gif，真实地址在 file / zoomfile /
 * data-original 属性里，故按此优先级取值。
 */
fun parseContentBlocks(html: String): List<ContentBlock> {
    if (html.isBlank()) return emptyList()
    // 同一段正文会被反复请求解析（LazyColumn 回收条目后滚回视口会重新组合），
    // 而解析要跑 Jsoup DOM，开销远大于一次查表，故按 html 原文缓存结果。
    synchronized(contentBlocksCache) {
        contentBlocksCache[html]?.let { return it }
    }
    val blocks = parseContentBlocksUncached(html)
    synchronized(contentBlocksCache) {
        contentBlocksCache[html] = blocks
    }
    return blocks
}

/** 缓存条目上限（按帖计算）；单帖正文通常数 KB，上限取 128 条控制常驻内存。 */
private const val CONTENT_CACHE_MAX_ENTRIES = 128

/** 按访问顺序淘汰的 LRU；用 stdlib 实现，不用 android.util.LruCache，便于纯 JVM 单测。 */
private val contentBlocksCache = object : LinkedHashMap<String, List<ContentBlock>>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: Map.Entry<String, List<ContentBlock>>): Boolean =
        size > CONTENT_CACHE_MAX_ENTRIES
}

private fun parseContentBlocksUncached(html: String): List<ContentBlock> {
    // 隐藏内容卡片是引用容器，但里面的图片是真实附件图，必须先进图片序列，不能当引用纯文本丢掉
    val source = hoistQuoteImages(html)
    val body = Jsoup.parseBodyFragment(collapseBlankBlocks(stripEdgeWhitespace(source))).body()
    val builder = BlocksBuilder()
    builder.appendNodes(body.childNodes())
    return groupImages(dropEdgeBlankBlocks(builder.build()))
}

/**
 * 把引用容器内的图片提到容器前面。
 *
 * 「回复可见」的正文整块被站点包在 comiis_quote 里，引用块渲染只取文本，若不在拆块前
 * 把其中图片移出，这些附件图会随容器一起被当作引用文字丢掉。
 */
private fun hoistQuoteImages(html: String): String {
    if ("<img" !in html.lowercase()) return html
    val doc = Jsoup.parseBodyFragment(html)
    val quotes = doc.select("div.comiis_quote, div.locked")
    if (quotes.isEmpty()) return html
    for (quote in quotes) {
        val images = quote.select("img")
        if (images.isEmpty()) continue
        for (img in images) {
            quote.before(img.outerHtml())
            img.remove()
        }
    }
    return doc.body().html()
}

/**
 * 去掉正文首尾多余的换行与空白。
 *
 * 站点正文常以“&nbsp;<br>”开头，若不清掉，会在昵称行与正文之间多出一行空白；
 * 首尾行首空白只会让各段左边界不齐（网页会把行首空白吃掉），一并去掉。
 */
private fun stripEdgeWhitespace(html: String): String = html
    .replace(Regex("^(?:\\s|&nbsp;|&#160;|<br\\s*/?>|\\u00A0)+", RegexOption.IGNORE_CASE), "")
    .replace(Regex("(?:\\s|&nbsp;|&#160;|<br\\s*/?>|\\u00A0)+$", RegexOption.IGNORE_CASE), "")

/**
 * 把连续排列的图片合并成图片组。
 *
 * 图与图之间只有空白块（模板换行、空段落）时视为连续排列，交给网格渲染；
 * 中间夹着文字或引用的图片是分散在段落里的，各自单张展示。
 */
private fun groupImages(blocks: List<ContentBlock>): List<ContentBlock> {
    if (blocks.none { it is ContentBlock.Image }) return blocks
    val result = mutableListOf<ContentBlock>()
    var index = 0
    while (index < blocks.size) {
        if (blocks[index] !is ContentBlock.Image) {
            result.add(blocks[index])
            index++
            continue
        }
        val images = mutableListOf<String>()
        var end = index
        while (end < blocks.size && (blocks[end] is ContentBlock.Image || isBlankTextBlock(blocks[end]))) {
            (blocks[end] as? ContentBlock.Image)?.let { images.add(it.url) }
            end++
        }
        if (images.size >= 2) {
            result.add(ContentBlock.ImageGroup(images))
        } else {
            for (i in index until end) result.add(blocks[i])
        }
        index = end
    }
    return result
}

private fun isBlankTextBlock(block: ContentBlock): Boolean =
    block is ContentBlock.Text && isBlankOrNbsp(block.text)

/** 首/尾只剩下空白内容的块直接丢弃，中间的保留。 */
private fun dropEdgeBlankBlocks(blocks: List<ContentBlock>): List<ContentBlock> {
    fun isBlankBlock(block: ContentBlock) = block is ContentBlock.Text && isBlankOrNbsp(block.text)
    val start = blocks.indexOfFirst { !isBlankBlock(it) }
    if (start < 0) return emptyList()
    val end = blocks.indexOfLast { !isBlankBlock(it) }
    return blocks.subList(start, end + 1)
}

/**
 * 块级标签：网页在它们的边界换行，解析器必须补上同一个换行。
 *
 * 站点正文的行既可能是 `<br>`，也可能是每行一个 div/p（编辑器回车、从网页粘贴），
 * 后者在源码里常常紧邻无空白，只有显式认块边界才与网页一致。内联元素
 * （span/font/a/strong/em/code 等）不在其列，行内样式仍由 [InlineStyle] 承载。
 */
private val BLOCK_TAGS = setOf(
    "address", "article", "aside", "blockquote", "center", "dd", "details", "dialog", "div",
    "dl", "dt", "fieldset", "figcaption", "figure", "footer", "form", "h1", "h2", "h3", "h4",
    "h5", "h6", "header", "hr", "li", "main", "nav", "ol", "p", "pre", "section", "summary",
    "table", "td", "th", "tr", "ul",
)

/**
 * 压掉模板空块，并把 3 个以上连续换行压成一个空行。

 * 两个连续 <br> 是作者的分段（网页上就是一空行），必须原样留下；
 * 3 个以上多为模板/图片抽走后的残留，压成一个空行即可。<br>
 * 图片会被抽成独立块，抽走后原位残留的换行、以及模板空块，若无内容承接，
 * 会被逐行渲染成一片空白，因此先在 HTML 层压掉。
 */
private fun collapseBlankBlocks(html: String): String = html
    .replace(Regex("(?i)(?:<br\\s*/?>\\s*){3,}"), "<br><br>")
    .replace(Regex("(?i)<p\\s*>\\s*(?:&nbsp;|&#160;|\\s)*</p>"), "")
    .replace(Regex("(?i)<div[^>]*>\\s*(?:&nbsp;|&#160;|<br\\s*/?>|\\s)*</div>"), "")

private class BlocksBuilder {
    private val blocks = mutableListOf<ContentBlock>()
    private val text = StringBuilder()
    private val smiles = LinkedHashMap<String, Char>()
    private val spans = mutableListOf<StyleSpan>()
    private val openSpans = ArrayDeque<OpenSpan>()

    fun build(): List<ContentBlock> {
        flushText()
        return blocks
    }

    fun appendNodes(nodes: List<Node>, style: InlineStyle = InlineStyle()) {
        for (node in nodes) {
            when (node) {
                is Comment -> Unit
                is TextNode -> appendText(node.text(), style)
                is Element -> appendElement(node, style)
            }
        }
    }

    private fun appendText(value: String, style: InlineStyle) {
        if (value.isEmpty()) return
        // 浏览器会吃掉每个换行后的行首空白，Jsoup 却把多个空格折叠成一个留下，
        // 于是同一篇正文里有的行缩进、有的行不缩进，与网页不一致，故在追加时同步该行为
        val body = eatLineStartWhitespace(value)
        if (body.isEmpty()) return
        val start = text.length
        text.append(body)
        syncSpans(style, start, text.length)
    }

    /** 丢掉 value 中每个换行之后（含缓冲区正处于行首时）的空白字符。 */
    private fun eatLineStartWhitespace(value: String): String {
        if (value.isEmpty()) return value
        val out = StringBuilder(value.length)
        var atLineStart = text.isEmpty() || text[text.length - 1] == '\n'
        for (c in value) {
            when {
                c == '\n' -> {
                    out.append(c)
                    atLineStart = true
                }
                atLineStart && (c.isWhitespace() || c == '\u00A0') -> Unit
                else -> {
                    out.append(c)
                    atLineStart = false
                }
            }
        }
        return out.toString()
    }

    /** 换行：连续换行最多留两个（一个空行即作者分段），更多视为模板残留，不叠成大片空白。 */
    private fun appendBreak(style: InlineStyle) {
        val cursor = text.length
        if (cursor >= 2 && text[cursor - 1] == '\n' && text[cursor - 2] == '\n') return
        appendText("\n", style)
    }

    /**
     * 块级元素边界的换行：与 [appendBreak] 的区别是只补「还没有换行」的情况。
     *
     * 块与块之间本来就没有分隔符，进块与出块各补一次会变成两个换行（一个空行），
     * 故缓冲为空或已在行尾就直接跳过——既补上行边界，又不凭空造出空行。
     */
    private fun appendBlockBreak(style: InlineStyle) {
        if (text.isEmpty() || text[text.length - 1] == '\n') return
        appendText("\n", style)
    }

    private fun appendSmiley(url: String, style: InlineStyle) {
        val placeholder = smiles.getOrPut(url) {
            (SMILEY_CHAR_BASE + smiles.size).toChar()
        }
        val start = text.length
        text.append(placeholder)
        syncSpans(style, start, text.length)
    }

    /**
     * 让已开启的 span 栈与当前样式保持一致：父样式收尾、子样式开启。
     * 标签闭合时靠此补上 end，避免逐个标签手写 start/end。
     */
    private fun syncSpans(style: InlineStyle, start: Int, end: Int) {
        var depth = 0
        while (depth < openSpans.size && openSpans[depth].style == style) depth++
        while (openSpans.size > depth) {
            closeSpan(openSpans.size - 1, start)
        }
        if (depth == openSpans.size && style != InlineStyle()) {
            openSpans.add(OpenSpan(style, start, style.linkUrl))
        }
    }

    private fun closeSpan(index: Int, end: Int) {
        val open = openSpans.removeAt(index)
        if (end > open.start) {
            spans.add(
                StyleSpan(
                    start = open.start,
                    end = end,
                    bold = open.style.bold,
                    italic = open.style.italic,
                    underline = open.style.underline,
                    strikethrough = open.style.strikethrough,
                    code = open.style.code,
                    headingLevel = open.style.headingLevel,
                    fontSizeScale = open.style.fontSizeScale,
                    align = open.style.align,
                    linkUrl = open.linkUrl,
                ),
            )
        }
    }

    private fun flushText() {
        val cursor = text.length
        while (openSpans.isNotEmpty()) closeSpan(openSpans.size - 1, cursor)
        if (isBlankOrNbsp(text)) {
            text.clear()
            smiles.clear()
            spans.clear()
            return
        }
        val raw = text.toString()
        val lead = raw.indexOfFirst { it.code != 0xA0 && !it.isWhitespace() }
        val tail = raw.indexOfLast { it.code != 0xA0 && !it.isWhitespace() }
        // 首行一律顶到左边界：Jsoup 会把 &nbsp;/连续空格折叠成一个半角空格，与浏览器
        // “吃掉行首空白”的渲染不一致，会让同一篇正文里有的段落缩进、有的不缩进
        val start = if (lead < 0) 0 else lead
        val end = if (tail < 0) raw.length else tail + 1
        // 作者手打的裸地址没有 <a> 包裹，补成链接 span，否则整段只是纯文本、点不开
        detectBareLinks(raw, start, end, spans)
        blocks.add(
            ContentBlock.Text(
                text = raw.substring(start, end),
                smiles = smiles.entries.associate { (url, placeholder) -> placeholder to url },
                spans = normalizeSpans(spans, start, end),
            ),
        )
        text.clear()
        smiles.clear()
        spans.clear()
    }

/**
 * 引用块的被引正文。
 *
 * 站点在引用块最前面烘一条「作者 发表于 时间」的元信息，它不是被引内容：解析器没把
 * 该块收成 PostQuote 时（快照为空、结构不匹配），这一行会残在正文里原样显示。
 * meta 之前可能还压着别的块级元素（PC 版结构里两者各占一个 div），故按行找
 * 「发表于」而不是认第一行；找不到这一行才原样返回全文。
 */
private fun quoteBodyText(element: Element): String {
    val lines = element.html()
        .split(ForumParser.RE_QUOTE_LINE_BREAK)
        .map { Jsoup.parseBodyFragment(it).body().text().trim() }
        .filter { it.isNotEmpty() }
    val metaAt = lines.indexOfFirst { it.contains("发表于") }
    if (metaAt < 0) return element.text().trim()
    return lines.drop(metaAt + 1).joinToString("\n").trim()
}
    private fun appendElement(element: Element, style: InlineStyle) {
        if (element.hasClass("comiis_quote")) {
            flushText()
            val quote = quoteBodyText(element)
            if (quote.isNotEmpty()) blocks.add(ContentBlock.Quote(quote))
            return
        }
        // 附件下载框整体是一块「文件」，不能按普通 div 展开——里面的 filetype 小图标
        // 会被当成正文大图，文件名/大小也被拆成散文本
        if (element.hasClass("comiis_attach")) {
            flushText()
            parseAttachment(element)?.let { blocks.add(it) }
            return
        }
        if (element.hasClass("comiis_blockcode") || element.tagName() == "pre") {
            flushText()
            codeLinesOf(element)?.let { blocks.add(ContentBlock.CodeBlock(it)) }
            return
        }
        // 站点把正文每行烘成 div/p（编辑器回车即一个块），网页上块的边界就是换行；
        // 当内联容器展开会把相邻两行粘进同一段，而实测源码里块与块之间常常连空白都
        // 没有（</div><div align="left">），于是行与行之间连个空格都不留
        val blockBoundary = element.tagName() in BLOCK_TAGS
        if (blockBoundary) appendBlockBreak(style)
        when (element.tagName()) {
            "script", "style" -> Unit
            "video", "embed", "object" -> appendVideoElement(element, style)
            "img" -> appendImage(element, style)
            "br" -> appendBreak(style)
            "table" -> {
                flushText()
                buildTable(element)?.let { blocks.add(it) }
            }
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                val level = element.tagName().substring(1).toInt()
                appendNodes(element.childNodes().toList(), style.copy(headingLevel = level))
            }
            "a" -> appendAnchor(element, style)
            "ul", "ol" ->
                // 附件图列表（ul.comiis_img_list）的 li 承载的是图片，按列表取文本会把图丢掉，
                // 若 li 内是 <video> 附件则连视频一起丢掉，故有图或视频时走通用容器展开
                if (element.select("img, video, embed, object").isNotEmpty()) appendContainer(element, style)
                else appendList(element, style, ordered = element.tagName() == "ol")
            "blockquote" -> {
                flushText()
                val quote = quoteBodyText(element)
                if (quote.isNotEmpty()) blocks.add(ContentBlock.Quote(quote))
            }
            else -> appendContainer(element, style)
        }
        if (blockBoundary) appendBlockBreak(style)
    }

    /**
     * 代码块取行：站点把 `[code]` 烘成 div.comiis_blockcode 里的 ol > li，一个 li 一行；
     * 没有 li（如 <pre>）时按元素整体文本拆行。行首的 &nbsp; 归一成普通空格，缩进是有效内容。
     *
     * 不走 [Element.text]：它会把连续空白折叠成一个空格并 trim 掉首尾，代码缩进会整个消失。
     */
    private fun codeLinesOf(element: Element): List<String>? {
        val items = element.select("li")
        val raw = if (items.isNotEmpty()) {
            items.map { li -> decodedTextOf(li) }
        } else {
            decodedTextOf(element).split('\n')
        }
        val lines = raw.map { it.replace('\u00A0', ' ').trimEnd() }
        return lines.takeIf { candidates -> candidates.any { it.isNotBlank() } }
    }

    /** 按源码取元素文本：<br> 记为一个换行，其余标签只递归取其文本，行首空白原样留下。 */
    private fun decodedTextOf(element: Element): String {
        val out = StringBuilder()
        for (node in element.childNodes()) {
            when (node) {
                is TextNode -> out.append(node.wholeText)
                is Element ->
                    if (node.tagName() == "br") out.append('\n') else out.append(decodedTextOf(node))
                else -> Unit
            }
        }
        return out.toString()
    }

    /** 按标签自身的样式语义扩展内联样式后递归；无样式语义的容器原样展开。 */
    private fun appendContainer(element: Element, style: InlineStyle) {
        val childStyle = when (element.tagName()) {
            "strong", "b" -> style.copy(bold = true)
            "em", "i" -> style.copy(italic = true)
            "u" -> style.copy(underline = true)
            "s", "strike", "del" -> style.copy(strikethrough = true)
            "code", "kbd", "samp" -> style.copy(code = true)
            // font 只认 size：站点 UBB 编辑器挑的颜色（彩虹字、荧光字）一律不采用，
            // 统一按正文字色显示，链接另有主题链接色
            "font" -> style.copy(fontSizeScale = fontSizeScale(element.attr("size")) ?: style.fontSizeScale)
            else -> style.copy(align = parseAlign(element) ?: style.align)
        }
        appendNodes(element.childNodes().toList(), childStyle)
    }

    private fun appendAnchor(element: Element, style: InlineStyle) {
        val img = element.selectFirst("img")
        val href = element.attr("href").trim()
        // 附件视频常被包在链接里：<a href="xx.mp4"><img ...封面...></a>，
        // 此时封面已由视频卡展示，再当正文大图会重复出一张静态图
        if (img != null && isVideoUrl(href)) {
            appendVideo(url = normalizeMediaUrl(href), poster = imageUrl(img), style = style)
            return
        }
        if (img != null) {
            appendImage(img, style)
            return
        }
        // 正文贴出的视频直链没有封面，按视频卡展示而不是一行纯链接
        if (isVideoUrl(href)) {
            appendVideo(url = normalizeMediaUrl(href), poster = null, style = style)
            return
        }
        val link = href.takeIf { it.isNotBlank() && !it.startsWith("javascript:", true) }
        appendNodes(element.childNodes().toList(), style.copy(linkUrl = link ?: style.linkUrl))
    }

    /**
     * 视频标签与历史附件容器：<video>（移动端附件）、<embed>/<object>（旧版 flash 附件）。
     * <video> 的真实地址可能在自身 src、内部 <source>，取两者中先出现且可用的。
     */
    private fun appendVideoElement(element: Element, style: InlineStyle) {
        val candidates = listOf(element.attr("src"), element.attr("data-src")) +
            element.select("source").map { it.attr("src") } +
            element.select("img").flatMap { listOf(it.attr("file"), it.attr("zoomfile")) }
        val url = candidates.firstOrNull { it.isNotBlank() && isVideoUrl(it) }
            ?: return
        val posterImage = element.selectFirst("img")
        val poster = posterImage?.let { imageUrl(it) } ?: element.attr("poster").takeIf { it.isNotBlank() }
        appendVideo(url = normalizeMediaUrl(url), poster = poster?.let { normalizeMediaUrl(it) }, style = style)
    }

    private fun appendVideo(url: String?, poster: String?, style: InlineStyle) {
        if (url == null) return
        flushText()
        blocks.add(ContentBlock.Video(url = url, poster = poster))
    }

    private fun appendList(element: Element, style: InlineStyle, ordered: Boolean) {
        val items = element.children().filter { it.tagName() == "li" }
        if (items.isEmpty()) {
            appendNodes(element.childNodes().toList(), style)
            return
        }
        flushText()
        val buffer = StringBuilder()
        items.forEachIndexed { index, li ->
            if (index > 0) buffer.append('\n')
            buffer.append(if (ordered) "${index + 1}. " else "· ")
            buffer.append(li.text())
        }
        buffer.append('\n')
        appendText(buffer.toString(), style)
    }

    private fun appendImage(img: Element, style: InlineStyle) {
        val url = imageUrl(img) ?: return
        if (isSmiley(img)) {
            appendSmiley(url, style)
        } else {
            flushText()
            blocks.add(ContentBlock.Image(url))
        }
    }

    /**
     * 解析附件下载框：取下载链接、文件名与大小/积分说明。
     * 链接或文件名缺失时不产出块（按普通内容处理）。
     */
    private fun parseAttachment(element: Element): ContentBlock.Attachment? {
        val link = element.selectFirst("a[href]") ?: return null
        val url = link.attr("href").trim()
        val fileName = link.selectFirst("span.f_ok")?.text()?.trim().orEmpty()
        if (url.isEmpty() || fileName.isEmpty()) return null
        return ContentBlock.Attachment(
            fileName = fileName,
            url = normalizeMediaUrl(url),
            sizeText = link.selectFirst("p.attach_size")?.text()?.trim()?.ifBlank { null },
        )
    }

    private fun buildTable(element: Element): ContentBlock.Table? {
        val trs = element.select("tr")
        val rows = trs.map { tr ->
            tr.children().filter { it.tagName() == "td" || it.tagName() == "th" }.map { it.text().trim() }
        }.filter { it.isNotEmpty() }
        if (rows.isEmpty()) return null
        // 首行是表头：含 th，或整行单元格都用加粗标注（站点用加粗行代替 th）
        val firstTr = trs.firstOrNull()
        val firstIsHeader = firstTr != null && (
            firstTr.children().any { it.tagName() == "th" } ||
                firstTr.children().filter { it.tagName() == "td" }
                    .let { it.isNotEmpty() && it.all { cell -> cell.selectFirst("strong, b") != null } }
            )
        val width = rows.maxOf { it.size }
        fun pad(row: List<String>) = if (row.size < width) row + List(width - row.size) { "" } else row
        val header = if (firstIsHeader) pad(rows.first()) else null
        val body = (if (firstIsHeader) rows.drop(1) else rows).map(::pad)
        return ContentBlock.Table(header = header, rows = body)
    }

}

/**
 * 把 span 收束到 [start, end) 区间并可合并相邻同款段。
 * 越界区间在此裁掉，渲染层无须再判边界。
 */
private fun normalizeSpans(spans: List<StyleSpan>, start: Int, end: Int): List<StyleSpan> {
    if (spans.isEmpty()) return emptyList()
    val result = mutableListOf<StyleSpan>()
    for (span in spans) {
        val s = span.start.coerceAtLeast(start)
        val e = span.end.coerceAtMost(end)
        if (e <= s) continue
        val moved = span.copy(start = s - start, end = e - start)
        val last = result.lastOrNull()
        if (last != null && sameStyle(last, moved) && last.end == moved.start) {
            result[result.size - 1] = last.copy(end = moved.end)
        } else {
            result.add(moved)
        }
    }
    return result
}

private fun sameStyle(a: StyleSpan, b: StyleSpan): Boolean = a.copy(start = 0, end = 0) == b.copy(start = 0, end = 0)

/** 裸地址形态：http/https 开头，遇空白、引号、尖括号或表情占位字符（私用区）即止。 */
private val BARE_URL = Regex("https?://[^\\s\\u00A0\\u3000\\uE000-\\uF8FF<>\"']+")

/** 句读标点不属于地址：中英标点混排时字符类排除不干净，命中后再修掉尾巴。 */
private const val URL_TAIL_CHARS = ".,;:!?，。；、！？…～~）)】]〉》\"'”’"

/**
 * 把 [raw] 里 [start, end) 区间中的裸地址补成链接 span（下标与 [raw] 对齐）。
 * 站点用户贴图床直链时常常不加链接标签，网页上浏览器会自动识别，客户端不补就点不开。
 * 已被 <a> 覆盖的区间跳过，避免同一段文字叠两个链接注解。
 */
private fun detectBareLinks(raw: String, start: Int, end: Int, spans: MutableList<StyleSpan>) {
    for (match in BARE_URL.findAll(raw)) {
        val url = match.value.trimEnd(*URL_TAIL_CHARS.toCharArray())
        val from = match.range.first
        val to = from + url.length
        if (to - from <= "https://".length) continue
        if (from < start || to > end) continue
        if (spans.any { it.linkUrl != null && it.start < to && from < it.end }) continue
        spans.add(StyleSpan(start = from, end = to, linkUrl = url))
    }
}

/** 从 align 属性或内联 text-align 样式解析水平对齐。 */
private fun parseAlign(element: Element): CellAlign? {
    val attr = element.attr("align").trim().lowercase()
    val css = Regex("text-align\\s*:\\s*(\\w+)", RegexOption.IGNORE_CASE)
        .find(element.attr("style"))?.groupValues?.get(1)?.lowercase()
        .orEmpty()
    return when (attr.ifEmpty { css }) {
        "center" -> CellAlign.Center
        "right" -> CellAlign.End
        "left", "start" -> CellAlign.Start
        else -> null
    }
}

/** Discuz 的 font size 是 1..7 的相对档位，映射为相对基础字号的缩放。 */private fun fontSizeScale(value: String): Float? = when (value.trim().trimStart('+')) {
    "1" -> 0.75f
    "2" -> 0.85f
    "3" -> 1f
    "4" -> 1.15f
    "5" -> 1.3f
    "6" -> 1.45f
    "7" -> 1.6f
    else -> null
}

/** 只含空白与不换行空格（&nbsp;）的文本块不产生可见内容，按空块丢弃。 */
private fun isBlankOrNbsp(s: CharSequence): Boolean {
    for (c in s) {
        if (c.code != 0xA0 && !c.isWhitespace()) return false
    }
    return true
}

/**
 * 表情判定：站点用 smilieid 属性标记表情，URL 里也带 /smiley/。
 *
 * 渲染层把表情当行内小图、下载层必须排除它（不当成配图存盘），两边共用同一判定，
 * 否则下载离线页里表情会变成占满宽度的大图。
 */
internal fun isSmiley(img: Element): Boolean =
    img.hasAttr("smilieid") || img.attr("src").contains("/smiley/")

internal fun imageUrl(img: Element): String? {
    // 站点图片懒加载：src 常是 none.png / imageloading.gif 占位，真实地址在后面几个属性里
    val raw = listOf(
        "zoomfile", "file", "comiis_loadimages", "data-original", "data-src",
        "data-file", "data-lazy-src", "src",
    )
        .map { img.attr(it) }
        .firstOrNull { it.isNotBlank() && !isPlaceholderImage(it) && !isFileTypeIcon(it) }
        ?: return null
    return normalizeMediaUrl(raw)
}

/**
 * 媒体地址一律升到 https。
 *
 * 网络策略只对站内域名（binmt.cc / mt2.cn）放开明文，其余 http 直链会被系统直接拦掉，
 * 图片显示成空白占位（站点正文里的图片常是用户贴的站外图床直链，如 B 站 i0.hdslb.com）。
 * 这些图床都支持 https，升级后不依赖明文放行；升不上去的地址与现状一样是加载失败，不会更差。
 */
private fun upgradeToHttps(url: String): String {
    if (!url.startsWith("http://", ignoreCase = true)) return url
    return "https://" + url.substring("http://".length)
}

/** 占位图不是真实内容：懒加载预置的透明图与内联 data URI。 */
private fun isPlaceholderImage(url: String): Boolean {
    if (url.startsWith("data:")) return true
    val lower = url.lowercase()
    return "none.gif" in lower || "none.png" in lower ||
        "blank.gif" in lower || "imageloading" in lower
}

/** 文件类型小图标（附件框左侧 text.gif 等）不是正文内容，不作为图片渲染。 */
private fun isFileTypeIcon(url: String): Boolean =
    "/static/image/filetype/" in url.lowercase()

/**
 * 视频直链判定的集中入口：附件后缀（带 query 也认）。
 *
 * 不把 forum.php?mod=attachment&aid=N 这类无后缀附件链接判为视频：无法从 URL 区分
 * 视频与图片附件，误判会把图片附件整块变成视频卡。
 */
private fun isVideoUrl(value: String): Boolean {
    val path = value.substringBefore('?').substringBefore('#').lowercase()
    return VIDEO_EXTENSIONS.any { path.endsWith(".$it") }
}

private val VIDEO_EXTENSIONS = listOf("mp4", "mov", "m4v", "webm", "m3u8", "mkv")

/** 媒体地址归一化：相对路径补站点域名，站内明文统一升到 https（与图片同规则）。 */
internal fun normalizeMediaUrl(raw: String): String {
    // 本地文件（已下载帖子的离线图）与 data URI 是完整地址，不能拼站点域名：
    // 拼出来是 https://bbs.binmt.cc/file:///...，离线页的图一张都加载不出来
    if (raw.startsWith("file:", ignoreCase = true) ||
        raw.startsWith("content:", ignoreCase = true) ||
        raw.startsWith("data:", ignoreCase = true)
    ) {
        return raw
    }
    val absolute = when {
        raw.startsWith("http") -> raw
        raw.startsWith("//") -> "https:$raw"
        raw.startsWith("/") -> "https://bbs.binmt.cc$raw"
        else -> "https://bbs.binmt.cc/$raw"
    }
    return upgradeToHttps(absolute)
}
