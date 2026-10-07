package com.aazhou.mtforum.data.export

import com.aazhou.mtforum.data.model.Post
import com.aazhou.mtforum.data.model.ThreadPage
import com.aazhou.mtforum.ui.common.imageUrl
import com.aazhou.mtforum.ui.common.isSmiley
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * 把一页帖子（首帖 + 评论，正文 HTML 已是本地化后的引用）导出成自包含 HTML。
 *
 * 图片以 base64 data URI 内嵌，单个文件即可离线浏览、也可直接发给别人用浏览器打开。
 * [localSrc] 给出图片地址到内嵌形式的映射，缺图（下载失败）的地址原样保留。
 */
internal fun buildThreadHtml(page: ThreadPage, localSrc: (String) -> String?): String {
    val title = page.subject?.takeIf { it.isNotBlank() } ?: "MTF 帖子"
    val posts = page.posts
    val op = posts.firstOrNull { it.isFirstPost } ?: posts.firstOrNull()
    val replies = posts.filter { it !== op }
    val body = buildString {
        append("<header class=\"meta\">")
        append("<div class=\"forum\">").append(escapeHtml(page.forumName.orEmpty())).append("</div>")
        append("<div class=\"saved\">由 MTF 客户端保存于 ")
            .append(formatSavedAt(System.currentTimeMillis())).append("</div>")
        append("</header>")
        append("<h1>").append(escapeHtml(title)).append("</h1>")
        if (op != null) {
            append(postHtml(op, isOpPost = true, localSrc))
        }
        append("<h2 class=\"replies-title\">全部评论（").append(replies.size).append("）</h2>")
        replies.forEach { append(postHtml(it, isOpPost = false, localSrc)) }
        append("<footer class=\"source\">原文：<a href=\"")
            .append(escapeHtml("https://bbs.binmt.cc/thread-${page.tid}-1-1.html"))
            .append("\">https://bbs.binmt.cc/thread-").append(escapeHtml(page.tid))
            .append("-1-1.html</a></footer>")
    }
    return """
        <!DOCTYPE html>
        <html lang="zh-CN">
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>${escapeHtml(title)}</title>
        <style>
        $PAGE_CSS
        </style>
        </head>
        <body>
        <main>
        $body
        </main>
        </body>
        </html>
    """.trimIndent()
}

private fun postHtml(post: Post, isOpPost: Boolean, localSrc: (String) -> String?): String = buildString {
    append("<section class=\"post").append(if (isOpPost) " op" else "").append("\">")
    append("<div class=\"author\">")
    append("<span class=\"name\">").append(escapeHtml(post.authorName.ifBlank { "匿名" })).append("</span>")
    if (post.isOp && !isOpPost) append("<span class=\"badge\">楼主</span>")
    val meta = listOfNotNull(
        post.floorLabel?.takeIf { it.isNotBlank() }?.let { escapeHtml(it) },
        post.timeText?.takeIf { it.isNotBlank() }?.let { escapeHtml(it) },
        post.location?.takeIf { it.isNotBlank() }?.let { escapeHtml(it) },
    ).joinToString(" · ")
    if (meta.isNotBlank()) append("<span class=\"sub\">").append(meta).append("</span>")
    append("</div>")
    post.quote?.let { quote ->
        append("<blockquote>")
        append("<div class=\"quote-meta\">")
        if (!quote.author.isNullOrBlank()) {
            append("回复 ").append(escapeHtml(quote.author)).append("：")
        }
        quote.time?.takeIf { it.isNotBlank() }?.let { append("（").append(escapeHtml(it)).append("）") }
        append("</div>")
        append("<div class=\"quote-text\">").append(escapeHtml(quote.text)).append("</div>")
        append("</blockquote>")
    }
    if (post.editedText != null) {
        append("<div class=\"edited\">").append(escapeHtml(post.editedText)).append("</div>")
    }
    append("<div class=\"content\">").append(sanitizeContentHtml(post.contentHtml, localSrc)).append("</div>")
    if (post.hasHiddenContent) {
        append("<div class=\"hidden\">本帖含回复可见内容，保存时未解锁。</div>")
    }
    append("</section>")
}

/**
 * 清理站点正文片段，并把图片地址换成 [localSrc] 给出的形式（data URI 或本地文件地址）。
 *
 * 站点正文是半可信 HTML：直接摆进浏览器或应用渲染器会带进脚本、事件属性与懒加载
 * 占位属性，故统一剥掉脚本类标签与 on* 事件；图片按 [com.aazhou.mtforum.ui.common.imageUrl]
 * 的懒加载属性优先级解析真实地址后重写 src。下载失败的图片保留原 src，交给渲染侧兜底。
 */
internal fun sanitizeContentHtml(html: String, localSrc: (String) -> String?): String {
    if (html.isBlank()) return ""
    val doc: Document = Jsoup.parseBodyFragment(html)
    doc.select("script,style,iframe,object,embed,form,input,button,link,meta").remove()
    stripEventAttributes(doc)
    doc.select("img").forEach { img ->
        val smiley = isSmiley(img)
        val original = img.attr("src")
        val realUrl = imageUrl(img)
        val replacement = realUrl?.let(localSrc)
        val hadSmilieId = img.hasAttr("smilieid")
        img.clearAttributes()
        img.attr("src", replacement ?: original)
        if (smiley) {
            // 表情按行内小图排版：加 class 供 CSS 限死尺寸（否则被 .content img 的
            // max-width:100% 按自然尺寸撑成占满宽度的大图）；smilieid 保留，应用内
            // 渲染仍能认出行内占位（本地 file:// 地址里没有 /smiley/ 可判）
            img.addClass("smiley")
            if (hadSmilieId) img.attr("smilieid", "1")
        } else {
            img.attr("alt", "")
        }
    }
    doc.select("a[href]").forEach { a ->
        val href = a.attr("href")
        if (href.isNotBlank() && !href.startsWith("javascript:", true)) {
            a.attr("target", "_blank")
            a.attr("rel", "noopener")
        }
    }
    return doc.body().html()
}

/**
 * 正文里全部图片地址（含表情小图）：与 [sanitizeContentHtml] 同一套懒加载优先级解析，
 * 供下载侧按同一批地址取字节，保证改写后的 src 都能本地化。
 *
 * 表情也一起取：体积极小，换来的离线完整；它不会被当成大图，尺寸由 CSS 的
 * `.smiley` 规则限死（见 [sanitizeContentHtml] 里打的 class）。
 */
internal fun collectContentImageUrls(html: String): List<String> {
    if (html.isBlank()) return emptyList()
    return Jsoup.parseBodyFragment(html).select("img").mapNotNull { imageUrl(it) }
}

/** 去掉所有元素上的 on* 事件属性（站点模板把点击统计等脚本挂在标签上）。 */
private fun stripEventAttributes(doc: Document) {
    doc.allElements.forEach { element ->
        val eventKeys = element.attributes()
            .filter { it.key.startsWith("on", ignoreCase = true) }
            .map { it.key }
        eventKeys.forEach { element.removeAttr(it) }
    }
}

/** 图片字节转 data URI；mime 与保存侧同一套口径（按扩展名）。 */
internal fun dataUriOf(url: String, bytes: ByteArray): String {
    val mime = when {
        url.contains(".png", true) -> "image/png"
        url.contains(".gif", true) -> "image/gif"
        url.contains(".webp", true) -> "image/webp"
        else -> "image/jpeg"
    }
    return "data:$mime;base64," + Base64.getEncoder().encodeToString(bytes)
}

private fun escapeHtml(text: String): String = escapeHtmlValue(text)

private val savedAtFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

private fun formatSavedAt(millis: Long): String = savedAtFormat.format(Date(millis))

/** 供存档层复用的同一转义实现（HTML 文本节点与属性值都够用）。 */
internal fun escapeHtmlValue(text: String): String = text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")

private val PAGE_CSS = """
    :root { color-scheme: light; }
    * { box-sizing: border-box; }
    body { margin: 0; background: #f2f2f6; color: #0f172a;
           font: 15px/1.7 -apple-system, "PingFang SC", "Noto Sans CJK SC", sans-serif; }
    main { max-width: 720px; margin: 0 auto; padding: 16px 14px 48px; }
    header.meta { color: #475569; font-size: 12px; display: flex; justify-content: space-between; }
    h1 { font-size: 20px; line-height: 1.45; margin: 10px 0 14px; }
    h2.replies-title { font-size: 15px; margin: 18px 2px 8px; }
    section.post { background: #fff; border-radius: 12px; padding: 14px 16px; margin-bottom: 10px; }
    section.post.op { margin-top: 4px; }
    .author { display: flex; align-items: baseline; flex-wrap: wrap; gap: 8px; margin-bottom: 6px; }
    .name { font-weight: 700; font-size: 14px; }
    .badge { color: #2563eb; background: rgba(37,99,235,.08); border-radius: 4px;
             font-size: 10px; padding: 1px 5px; }
    .sub { color: #94a3b8; font-size: 11px; }
    .edited { color: #475569; font-size: 11px; font-style: italic; margin-bottom: 4px; }
    .content { word-break: break-word; }
    .content img { max-width: 100%; height: auto; border-radius: 6px; vertical-align: middle; }
    .content img.smiley { width: 1.4em; height: 1.4em; max-width: 1.4em; border-radius: 0; vertical-align: -0.2em; }
    .content table { border-collapse: collapse; max-width: 100%; }
    .content td, .content th { border: 1px solid #e2e8f0; padding: 4px 8px; font-size: 13px; }
    blockquote { margin: 8px 0; padding: 8px 10px; background: #f1f5f9; border-radius: 6px;
                 color: #475569; font-size: 13px; }
    .quote-meta { font-size: 11px; color: #94a3b8; margin-bottom: 2px; }
    blockquote p { margin: 0; }
    .hidden { margin-top: 8px; padding: 8px 10px; border: 1px dashed #e2e8f0; border-radius: 6px;
              color: #475569; font-size: 12px; }
    footer.source { margin-top: 20px; color: #94a3b8; font-size: 11px; text-align: center; }
    footer.source a { color: #64748b; }
""".trimIndent()
