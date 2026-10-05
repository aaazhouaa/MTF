package com.aazhou.mtforum.ui.common

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.nodes.Comment

/** 帖子正文的块级内容：文本段或图片，按原始顺序排列以保留图文混排。 */
sealed interface ContentBlock {
    data class Text(val text: String) : ContentBlock
    data class Image(val url: String) : ContentBlock
}

/**
 * 把 Discuz 正文 HTML 拆成块。
 *
 * 站点的图片是懒加载的：img 的 src 常为 none.gif，真实地址在 file / zoomfile /
 * data-original 属性里，故按此优先级取值。
 */
fun parseContentBlocks(html: String): List<ContentBlock> {
    if (html.isBlank()) return emptyList()
    val body = Jsoup.parseBodyFragment(html).body()
    val blocks = mutableListOf<ContentBlock>()
    appendNodes(body.childNodes(), blocks)
    return blocks
}

private fun appendNodes(nodes: List<Node>, out: MutableList<ContentBlock>) {
    for (node in nodes) {
        when (node) {
            is Comment -> Unit
            is TextNode -> {
                val text = node.text()
                if (text.isNotBlank()) out.add(ContentBlock.Text(text))
            }
            is Element -> appendElement(node, out)
        }
    }
}

private fun appendElement(element: Element, out: MutableList<ContentBlock>) {
    when (element.tagName()) {
        "script", "style" -> return
        "img" -> {
            imageUrl(element)?.let { out.add(ContentBlock.Image(it)) }
        }
        "br" -> out.add(ContentBlock.Text("\n"))
        "a" -> {
            val img = element.selectFirst("img")
            if (img != null) {
                imageUrl(img)?.let { out.add(ContentBlock.Image(it)) }
            } else {
                val text = element.text()
                if (text.isNotBlank()) out.add(ContentBlock.Text(text))
            }
        }
        else -> {
            // 容器节点（div/p/h2/ignore_js_op 等）递归展开，保留其中的图片与文字
            if (element.children().isEmpty()) {
                val text = element.text()
                if (text.isNotBlank()) out.add(ContentBlock.Text(text))
            } else {
                appendNodes(element.childNodes().toList(), out)
            }
        }
    }
}

private fun imageUrl(img: Element): String? {
    val raw = listOf("file", "zoomfile", "data-original", "src")
        .map { img.attr(it) }
        .firstOrNull { it.isNotBlank() && !it.contains("none.gif") && !it.startsWith("data:") }
        ?: return null
    return when {
        raw.startsWith("http") -> raw
        raw.startsWith("//") -> "https:$raw"
        raw.startsWith("/") -> "https://bbs.binmt.cc$raw"
        else -> "https://bbs.binmt.cc/$raw"
    }
}
