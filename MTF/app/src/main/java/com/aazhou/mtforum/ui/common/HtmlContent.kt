package com.aazhou.mtforum.ui.common

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

    /** 连续排列的图片组，交由网格布局展示。 */
    data class ImageGroup(val urls: List<String>) : ContentBlock

    /** 表格：可选的表头行与若干数据行，单元格仅取纯文本。 */
    data class Table(
        val header: List<String>?,
        val rows: List<List<String>>,
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
    val colorArgb: Long? = null,
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
    val colorArgb: Long? = null,
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
                    colorArgb = open.style.colorArgb,
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

    private fun appendElement(element: Element, style: InlineStyle) {
        if (element.hasClass("comiis_quote")) {
            flushText()
            val quote = element.text().trim()
            if (quote.isNotEmpty()) blocks.add(ContentBlock.Quote(quote))
            return
        }
        when (element.tagName()) {
            "script", "style" -> Unit
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
                // 附件图列表（ul.comiis_img_list）的 li 承载的是图片，按列表取文本会把图丢掉，故有图时走通用容器展开
                if (element.select("img").isNotEmpty()) appendContainer(element, style)
                else appendList(element, style, ordered = element.tagName() == "ol")
            "blockquote" -> {
                flushText()
                val quote = element.text().trim()
                if (quote.isNotEmpty()) blocks.add(ContentBlock.Quote(quote))
            }
            else -> appendContainer(element, style)
        }
    }

    /** 按标签自身的样式语义扩展内联样式后递归；无样式语义的容器原样展开。 */
    private fun appendContainer(element: Element, style: InlineStyle) {
        val childStyle = when (element.tagName()) {
            "strong", "b" -> style.copy(bold = true)
            "em", "i" -> style.copy(italic = true)
            "u" -> style.copy(underline = true)
            "s", "strike", "del" -> style.copy(strikethrough = true)
            "code", "kbd", "samp" -> style.copy(code = true)
            "font" -> style.copy(
                colorArgb = parseColor(element.attr("color")) ?: style.colorArgb,
                fontSizeScale = fontSizeScale(element.attr("size")) ?: style.fontSizeScale,
            )
            else -> style.copy(align = parseAlign(element) ?: style.align)
        }
        appendNodes(element.childNodes().toList(), childStyle)
    }

    private fun appendAnchor(element: Element, style: InlineStyle) {
        val img = element.selectFirst("img")
        if (img != null) {
            appendImage(img, style)
            return
        }
        val href = element.attr("href").trim().takeIf { it.isNotBlank() && !it.startsWith("javascript:", true) }
        appendNodes(element.childNodes().toList(), style.copy(linkUrl = href ?: style.linkUrl))
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

    private fun isSmiley(img: Element): Boolean =
        img.hasAttr("smilieid") || img.attr("src").contains("/smiley/")
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

private val HEX_COLOR = Regex("#([0-9a-fA-F]{6}|[0-9a-fA-F]{3})")
private val RGB_COLOR = Regex("rgb\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)")

private fun parseColor(value: String): Long? {
    val raw = value.trim()
    if (raw.isEmpty()) return null
    HEX_COLOR.matchEntire(raw)?.let { match ->
        val hex = match.groupValues[1]
        val full = if (hex.length == 3) hex.map { "$it$it" }.joinToString("") else hex
        return parseHex(full)
    }
    RGB_COLOR.matchEntire(raw)?.let { match ->
        val r = match.groupValues[1].toIntOrNull() ?: return null
        val g = match.groupValues[2].toIntOrNull() ?: return null
        val b = match.groupValues[3].toIntOrNull() ?: return null
        return ((r.coerceIn(0, 255).toLong() shl 16) or (g.coerceIn(0, 255).toLong() shl 8) or b.coerceIn(0, 255).toLong())
    }
    return when (raw.lowercase()) {
        "black" -> 0xFF000000L
        "red" -> 0xFFFF0000L
        "green" -> 0xFF008000L
        "blue" -> 0xFF0000FFL
        "white" -> 0xFFFFFFFFL
        "orange" -> 0xFFFFA500L
        "purple" -> 0xFF800080L
        "gray", "grey" -> 0xFF808080L
        else -> null
    }
}

private fun parseHex(hex: String): Long? {
    val value = hex.toLongOrNull(16) ?: return null
    return if (hex.length == 8) value else 0xFF000000L or value
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

private fun imageUrl(img: Element): String? {
    // 站点图片懒加载：src 常是 none.png / imageloading.gif 占位，真实地址在后面几个属性里
    val raw = listOf(
        "zoomfile", "file", "comiis_loadimages", "data-original", "data-src",
        "data-file", "data-lazy-src", "src",
    )
        .map { img.attr(it) }
        .firstOrNull { it.isNotBlank() && !isPlaceholderImage(it) }
        ?: return null
    val absolute = when {
        raw.startsWith("http") -> raw
        raw.startsWith("//") -> "https:$raw"
        raw.startsWith("/") -> "https://bbs.binmt.cc$raw"
        else -> "https://bbs.binmt.cc/$raw"
    }
    return upgradeToHttps(absolute)
}

/** 站内静态资源服务器均支持 https；明文地址会被系统当不安全流量处理，这里统一升到 https。 */
private fun upgradeToHttps(url: String): String {
    if (!url.startsWith("http://")) return url
    val lower = url.lowercase()
    return if ("binmt.cc" in lower || "mt2.cn" in lower) {
        "https://" + url.substring("http://".length)
    } else {
        url
    }
}

/** 占位图不是真实内容：懒加载预置的透明图与内联 data URI。 */
private fun isPlaceholderImage(url: String): Boolean {
    if (url.startsWith("data:")) return true
    val lower = url.lowercase()
    return "none.gif" in lower || "none.png" in lower ||
        "blank.gif" in lower || "imageloading" in lower
}
