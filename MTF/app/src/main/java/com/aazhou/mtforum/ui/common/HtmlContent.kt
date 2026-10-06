package com.aazhou.mtforum.ui.common

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.nodes.Comment

/** 段落内联表情的占位字符池（私用区），避免与正文冲突。 */
private const val SMILEY_CHAR_BASE = 0xE000

/**
 * 帖子正文的块级内容：引用、文本段、表情或图片，按原始顺序排列以保留图文混排。
 *
 * [Text.smiles] 把正文里的表情抽成占位字符 -> 图片地址，渲染时以行内小图标还原，
 * 站点表情本身也是 <img>，若不单独识别会被当作正文大图铺满整屏。
 */
sealed interface ContentBlock {
    data class Quote(val text: String) : ContentBlock
    data class Text(
        val text: String,
        val smiles: Map<Char, String> = emptyMap(),
    ) : ContentBlock
    data class Image(val url: String) : ContentBlock

    /** 连续排列的图片组，交由网格布局展示。 */
    data class ImageGroup(val urls: List<String>) : ContentBlock
}

/**
 * 把 Discuz 正文 HTML 拆成块。
 *
 * 站点的图片是懒加载的：img 的 src 常为 none.gif，真实地址在 file / zoomfile /
 * data-original 属性里，故按此优先级取值。
 */
fun parseContentBlocks(html: String): List<ContentBlock> {
    if (html.isBlank()) return emptyList()
    val source = collapseBlankBlocks(stripEdgeWhitespace(html))
    val body = Jsoup.parseBodyFragment(source).body()
    val builder = BlocksBuilder()
    builder.appendNodes(body.childNodes())
    return groupImages(dropEdgeBlankBlocks(builder.build()))
}

/**
 * 去掉正文首尾的空白、`&nbsp;` 与换行标签。
 *
 * 站点正文常以“&nbsp;<br>”开头，若不清掉，会在昵称行与正文之间多出一行空白；
 * 段落中间的空行不动，避免破坏作者本意的分段。
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
 * 压掉模板自带的空段落与连续换行。
 *
 * 图片会被抽成独立块，抽走后原位残留的换行、以及模板空块，若无内容承接，
 * 会被逐行渲染成一片空白，因此先在 HTML 层压掉。
 */
private fun collapseBlankBlocks(html: String): String = html
    .replace(Regex("(?i)(?:<br\\s*/?>\\s*){2,}"), "<br>")
    .replace(Regex("(?i)<p\\s*>\\s*(?:&nbsp;|&#160;|\\s)*</p>"), "")
    .replace(Regex("(?i)<div[^>]*>\\s*(?:&nbsp;|&#160;|<br\\s*/?>|\\s)*</div>"), "")

private class BlocksBuilder {
    private val blocks = mutableListOf<ContentBlock>()
    private val text = StringBuilder()
    private val smiles = LinkedHashMap<String, Char>()

    fun build(): List<ContentBlock> {
        flushText()
        return blocks
    }

    fun appendNodes(nodes: List<Node>) {
        for (node in nodes) {
            when (node) {
                is Comment -> Unit
                is TextNode -> appendText(node.text())
                is Element -> appendElement(node)
            }
        }
    }

    private fun appendText(value: String) {
        text.append(value)
    }

    private fun appendSmiley(url: String) {
        val placeholder = smiles.getOrPut(url) {
            (SMILEY_CHAR_BASE + smiles.size).toChar()
        }
        text.append(placeholder)
    }
    private fun flushText() {
        if (isBlankOrNbsp(text)) {
            text.clear()
            smiles.clear()
            return
        }
        blocks.add(
            ContentBlock.Text(
                text = text.toString().trim { it.code == 0xA0 || it.isWhitespace() },
                smiles = smiles.entries.associate { (url, placeholder) -> placeholder to url },
            ),
        )
        text.clear()
        smiles.clear()
    }

    private fun appendElement(element: Element) {
        if (element.hasClass("comiis_quote")) {
            flushText()
            val quote = element.text().trim()
            if (quote.isNotEmpty()) blocks.add(ContentBlock.Quote(quote))
            return
        }
        when (element.tagName()) {
            "script", "style" -> Unit
            "img" -> appendImage(element)
            "br" -> appendText("\n")
            "a" -> {
                val img = element.selectFirst("img")
                if (img != null) {
                    appendImage(img)
                } else {
                    appendText(element.text())
                }
            }
            else -> {
                // 容器节点（div/p/h2/ignore_js_op 等）递归展开，保留其中的图片与文字
                if (element.children().isEmpty()) {
                    appendText(element.text())
                } else {
                    appendNodes(element.childNodes().toList())
                }
            }
        }
    }

    private fun appendImage(img: Element) {
        val url = imageUrl(img) ?: return
        if (isSmiley(img)) {
            appendSmiley(url)
        } else {
            flushText()
            blocks.add(ContentBlock.Image(url))
        }
    }

    private fun isSmiley(img: Element): Boolean =
        img.hasAttr("smilieid") || img.attr("src").contains("/smiley/")
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
    val raw = listOf("zoomfile", "file", "comiis_loadimages", "data-original", "data-src", "data-file", "src")
        .map { img.attr(it) }
        .firstOrNull { it.isNotBlank() && !isPlaceholderImage(it) }
        ?: return null
    return when {
        raw.startsWith("http") -> raw
        raw.startsWith("//") -> "https:$raw"
        raw.startsWith("/") -> "https://bbs.binmt.cc$raw"
        else -> "https://bbs.binmt.cc/$raw"
    }
}

/** 占位图不是真实内容：懒加载预置的透明图与内联 data URI。 */
private fun isPlaceholderImage(url: String): Boolean {
    if (url.startsWith("data:")) return true
    val lower = url.lowercase()
    return "none.gif" in lower || "none.png" in lower ||
        "blank.gif" in lower || "imageloading" in lower
}
