package com.aazhou.mtforum.data.repo

import com.aazhou.mtforum.data.model.Notice
import com.aazhou.mtforum.data.model.NoticeTab
import com.aazhou.mtforum.data.network.HttpClient
import com.aazhou.mtforum.data.parser.ForumParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** 消息提醒某个分类下的一页内容。 */
data class NoticeListPage(
    val notices: List<Notice> = emptyList(),
    /** 站点分页器给出的总页数；没有分页器（只有一页）为 1。 */
    val totalPages: Int = 1,
    val loggedOut: Boolean = false,
)

/**
 * 一次多子类提醒查询的结果。
 *
 * [perTab] 各子类各自的列表不能丢：分类未读是各 tab 各记一份账的，合并成一条后
 * 就分辨不出哪条属于哪个 tab。
 */
data class NoticeFetch(
    val perTab: Map<NoticeTab, List<Notice>> = emptyMap(),
    val merged: List<Notice> = emptyList(),
    val loggedOut: Boolean = false,
)

/**
 * 消息提醒分类页：取数与分页解析。
 *
 * 站点把提醒内容按类别分页（`home.php?mod=space&do=notice&view=X&type=Y&page=N&mobile=2`，
 * 每页一条列表），行结构交给 [ForumParser.parseNotices]；总页数在这里自己解——站点这
 * 套模板的分页器是链接式 `div.pg`（页码在查询串里、另有「共 N 页」），和帖子列表同
 * 一套 markup，下拉式 `div.comiis_page` 也一并认。页码解不出来时总页数恒为 1，翻页
 * 判据永远不成立，滑到底也就不会有任何反应。
 */
class NoticeRepo(private val http: HttpClient) {

    suspend fun notices(view: String, type: String? = null, page: Int = 1): NoticeListPage =
        withContext(Dispatchers.IO) {
            val typeQuery = if (type == null) "" else "&type=$type"
            val html = http.get(
                http.url(
                    "home.php?mod=space&do=notice&view=$view$typeQuery&page=$page&mobile=2",
                ),
            )
        val parsed = ForumParser.parseNotices(html)
        if (parsed.loggedOut) return@withContext NoticeListPage(loggedOut = true)
        NoticeListPage(
            notices = parsed.notices.distinctBy { it.id },
            totalPages = noticeTotalPages(Jsoup.parse(html), page, parsed.notices.size),
        )
    }

    /**
     * 分页器给出的总页数。
     *
     * 站点这套模板的分页器有三处可用的页码信息：页码链接（查询串 `page=N`）、
     * 「共 N 页」、以及下拉式分页器（`select#dumppage`）的 option 值。
     * 「还有下一页」只看链接里有没有比当前页更大的页码——末页上「下一页」是禁用的
     * `javascript:` 链接，认文字会把它当成还有下一页。三样都取不到时按只有一页算，
     * 或（连分页器都没有）看这一页是不是满的。
     */
    private fun noticeTotalPages(document: Document, page: Int, noticeCount: Int): Int {
        val pagers = document.select("div.comiis_multi_box, div.pg, div.comiis_page")
        if (pagers.isEmpty()) {
            // 分页器整个没渲染（站点可能换成了「点击加载更多」那类 AJAX 形态）：整页都
            // 是提醒时按还有下一页算，多要一次即可确认——空页与重复页都会让调用方收手
            return if (noticeCount >= FULL_PAGE_HINT) page + 1 else 1
        }
        var total = 1
        var hasNextLink = false
        for (pager in pagers) {
            // 「共 N 页」只在 title 属性里，text() 取不到，两处都要看
            val text = buildString {
                append(pager.text())
                for (titled in pager.select("[title]")) {
                    append(' ').append(titled.attr("title"))
                }
            }
            for (match in RE_TOTAL_PAGES.findAll(text)) {
                total = maxOf(total, match.groupValues[1].toIntOrNull() ?: 1)
            }
            for (option in pager.select("select option")) {
                total = maxOf(total, option.attr("value").trim().toIntOrNull() ?: 1)
            }
            for (link in pager.select("a[href]")) {
                val linked = pageFromHref(link.attr("href")) ?: continue
                total = maxOf(total, linked)
                if (linked > page) hasNextLink = true
            }
        }
        // 还有链接指向更大的页码：至少还有一页——分页器没给出总页数时唯一的判据
        if (hasNextLink) total = maxOf(total, page + 1)
        return total.coerceAtLeast(1)
    }
}

/** 分页器缺失时，一页至少有这么多条提醒才认为还有下一页。 */
private const val FULL_PAGE_HINT = 10

/** 分页器上的「共 N 页」。 */
private val RE_TOTAL_PAGES = Regex("""共\s*(\d+)\s*页""")

/** 分页链接里的页码：查询串 `page=N`。 */
private val RE_PAGE_PARAM = Regex("""[?&]page=(\d+)""")

/** 从分页链接里取页码；链接不带页码（javascript:、伪静态地址）时返回 null。 */
private fun pageFromHref(href: String): Int? =
    RE_PAGE_PARAM.find(href)?.groupValues?.get(1)?.toIntOrNull()?.coerceAtLeast(1)
