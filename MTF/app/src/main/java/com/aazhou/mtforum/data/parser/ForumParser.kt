package com.aazhou.mtforum.data.parser

import com.aazhou.mtforum.data.model.Forum
import com.aazhou.mtforum.data.model.ForumGroup
import com.aazhou.mtforum.data.model.ForumPage
import com.aazhou.mtforum.data.model.HomeData
import com.aazhou.mtforum.data.model.LoginResult
import com.aazhou.mtforum.data.model.Post
import com.aazhou.mtforum.data.model.SearchResult
import com.aazhou.mtforum.data.model.Thread
import com.aazhou.mtforum.data.model.ThreadPage
import com.aazhou.mtforum.data.model.UserProfile
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Discuz（comiis 移动模板）HTML 解析。
 *
 * 站点模板与类名是解析的唯一契约，站点改版会导致选择器失配；因此在
 * [parseForumPage] 等方法里对缺失节点一律容错返回空，不抛异常。
 */
object ForumParser {

    private const val BASE = "https://bbs.binmt.cc/"
    private val RE_TID = Regex("thread-(\\d+)")
    private val RE_FID = Regex("forum-(\\d+)-")
    private val RE_UID = Regex("uid=(\\d+)")
    private val RE_FORMHASH_INPUT = Regex("formhash[\"']?\\s+value=[\"']([0-9a-fA-F]+)")
    private val RE_FORMHASH_JS = Regex("formhash\\s*=\\s*['\"]([0-9a-fA-F]+)['\"]")
    private val RE_LOGINHASH = Regex("loginhash=([A-Za-z0-9]+)")

    private fun doc(html: String): Document = Jsoup.parse(html, BASE)

    /** 版块列表页（forum.php?forumlist=1）。 */
    fun parseForumList(html: String): List<ForumGroup> {
        val document = doc(html)
        val groups = mutableListOf<ForumGroup>()
        for (block in document.select("div.comiis_forumlist")) {
            val name = block.selectFirst("div.comiis_bbs_show h2 a")?.text()?.trim()
                ?: block.selectFirst("div.comiis_bbs_show h2")?.text()?.trim()
                ?: continue
            val forums = mutableListOf<Forum>()
            for (a in block.select("div[id^=sub_forum_] li a")) {
                val fid = RE_FID.find(a.attr("href"))?.groupValues?.get(1) ?: continue
                val forumName = a.selectFirst("p")?.text()?.trim() ?: a.text().trim()
                if (forumName.isEmpty()) continue
                forums.add(Forum(fid = fid, name = forumName, icon = a.selectFirst("em img")?.attr("src")))
            }
            if (forums.isNotEmpty()) groups.add(ForumGroup(name, forums))
        }
        return groups
    }

    /** 版块页帖子列表（forum-{fid}-{page}.html）。 */
    fun parseForumPage(html: String, page: Int): ForumPage {
        val document = doc(html)
        // 置顶帖单独用 class="b_t"，而普通帖的 class 也含 b_t（forumlist_li comiis_znalist b_t ...），
        // 需用 :not(.forumlist_li) 排除，否则会把普通帖当作公告并产生重复 tid（LazyColumn 重复 key 崩溃）。
        val announcements = mutableListOf<Thread>()
        for (a in document.select("li.b_t:not(.forumlist_li) a[href*=thread-]")) {
            val tid = RE_TID.find(a.attr("href"))?.groupValues?.get(1) ?: continue
            announcements.add(
                Thread(
                    tid = tid,
                    subject = a.text().trim().removePrefix("置顶").trim(),
                    isAnnouncement = true,
                )
            )
        }

        val threads = mutableListOf<Thread>()
        for (li in document.select("li.forumlist_li")) {
            val titleA = li.selectFirst("div.mmlist_li_box h2 a[href*=thread-]") ?: continue
            val tid = RE_TID.find(titleA.attr("href"))?.groupValues?.get(1) ?: continue
            val authorA = li.selectFirst("a.top_user")
                threads.add(
                Thread(
                    tid = tid,
                    subject = titleA.text().trim(),
                    authorName = authorA?.text()?.trim(),
                    authorUid = RE_UID.find(authorA?.attr("href") ?: "")?.groupValues?.get(1),
                    avatarUrl = li.selectFirst("img.top_tximg")?.attr("src"),
                    level = li.selectFirst("span.top_lev")?.text()?.trim(),
                    timeText = li.selectFirst("div.forumlist_li_time span")?.text()?.trim(),
                    replyCount = li.selectFirst("div.comiis_xznalist_bottom li a[href*=thread-] span.comiis_tm")?.text()?.trim(),
                    viewCount = li.select("div.comiis_xznalist_bottom li span.comiis_tm").lastOrNull()?.text()?.trim(),
                    excerpt = li.selectFirst("div.list_body a")?.text()?.trim(),
                )
            )
        }

        return ForumPage(
            forumName = document.selectFirst("div.comiis_view_header1 h1")?.text()?.trim(),
            announcements = announcements.distinctBy { it.tid },
            threads = threads.distinctBy { it.tid },
            page = page,
            hasNext = hasNextPage(document),
        )
    }

    /** 帖子详情页（thread-{tid}-{page}-1.html）。 */
    fun parseThreadPage(html: String, page: Int): ThreadPage {
        val document = doc(html)
        val subject = document.title().removeSuffix("- MT论坛").trim()
        val posts = mutableListOf<Post>()
        for (li in document.select("div.comiis_postli[id^=pid]")) {
            val pid = li.id().removePrefix("pid")
            if (pid.isEmpty()) continue
            val authorA = li.selectFirst("a.top_user")
            val kmtime = li.selectFirst("span.kmtime")
            val content = li.selectFirst("div.comiis_a")?.html() ?: ""
            posts.add(
                Post(
                    pid = pid,
                    authorName = authorA?.text()?.trim() ?: "",
                    authorUid = RE_UID.find(authorA?.attr("href") ?: "")?.groupValues?.get(1),
                    avatarUrl = li.selectFirst("img.top_tximg")?.attr("src"),
                    level = li.selectFirst("a.top_lev")?.text()?.trim(),
                    timeText = kmtime?.ownText()?.trim(),
                    location = kmtime?.selectFirst("font")?.text()?.trim(),
                    contentHtml = content,
                    isOp = posts.isEmpty(),
                )
            )
        }
        val replyUrl = document.selectFirst("form#fastpostform")?.attr("action")
        return ThreadPage(
            tid = extractTid(document, html),
            subject = subject,
            forumName = null,
            posts = posts,
            page = page,
            totalPages = parseTotalPages(document),
            formhash = parseFormhash(html),
            replyUrl = replyUrl,
            fid = Regex("fid=(\\d+)").find(replyUrl ?: "")?.groupValues?.get(1),
        )
    }

    /** 表单 hash，发帖/回复接口必需。页面中存在 input 属性与 JS 赋值两种形式。 */
    fun parseFormhash(html: String): String? =
        RE_FORMHASH_INPUT.find(html)?.groupValues?.get(1)
            ?: RE_FORMHASH_JS.find(html)?.groupValues?.get(1)

    /** 登录页的 loginhash。 */
    fun extractLoginHash(html: String): String? =
        RE_LOGINHASH.find(html)?.groupValues?.get(1)

    /** 登录页的 formhash（在 input#formhash）。 */
    fun extractLoginFormhash(html: String): String? =
        doc(html).selectFirst("input#formhash")?.attr("value")?.ifEmpty { null }

    /** 判断登录结果：仍存在登录表单即视为失败。 */
    fun parseLoginResult(html: String): LoginResult {
        val document = doc(html)
        val stillLoginForm = document.selectFirst("form#loginform") != null
        if (stillLoginForm) {
            val message = document.select("div.comiis_tip, div.alert_error, div.alert_info")
                .firstOrNull { it.text().isNotBlank() }?.text()?.trim()
            val captcha = document.selectFirst("img[src*=seccode], img[src*=secqaa]")?.attr("src")
            return LoginResult(success = false, message = message, needsCaptcha = captcha != null, captchaUrl = captcha)
        }
        return LoginResult(success = true)
    }

    /** 首页（guide&view=hot）：热版推荐 + 热帖排行。 */
    fun parseHome(html: String): HomeData {
        val document = doc(html)
        val hotForums = document.select("div.comiis_mh_hotbk li a[href*=forum-]").mapNotNull { a ->
            val fid = RE_FID.find(a.attr("href"))?.groupValues?.get(1) ?: return@mapNotNull null
            val name = a.selectFirst("p")?.text()?.trim().orEmpty()
            if (name.isEmpty()) null
            else Forum(fid = fid, name = name, icon = a.selectFirst("img")?.attr("src"))
        }
        // 热帖排行：优先限定在排行容器内；序号会混进链接文本，需剔除
        val ranked = document.select("div[class*=comiis_mh_txtlist] a[href*=thread-]")
        val links = if (ranked.isNotEmpty()) ranked else document.select("a[href*=thread-]")
        val hotThreads = links.mapNotNull { a ->
            val tid = RE_TID.find(a.attr("href"))?.groupValues?.get(1) ?: return@mapNotNull null
            val subject = a.text().trim().replace(Regex("^\\d+\\s*"), "")
            if (subject.length < 4) return@mapNotNull null
            Thread(tid = tid, subject = subject)
        }.distinctBy { it.tid }.take(15)
        return HomeData(hotForums = hotForums, hotThreads = hotThreads)
    }

    /** 导读页（guide&view=newthread）：结构与版块页帖子列表一致。 */
    fun parseGuidePage(html: String, page: Int): ForumPage = parseForumPage(html, page)

    /** 从任意页面提取当前用户；未登录时 uid 为 "0"。 */
    fun parseUserProfile(html: String): UserProfile {
        val document = doc(html)
        val uid = Regex("discuz_uid\\s*=\\s*'(\\d+)'").find(html)?.groupValues?.get(1)
        val name = document.selectFirst("a.sidenv_user")?.text()?.trim()
        val avatar = document.selectFirst("img.sidenv_tximg, div.sidenv_user img")?.attr("src")
        return UserProfile(uid = uid, name = name, avatarUrl = avatar)
    }

    /** 搜索结果页；结构与版块页帖子列表一致。 */
    fun parseSearchResult(html: String, page: Int): SearchResult {
        val pageData = parseForumPage(html, page)
        return SearchResult(threads = pageData.threads, hasNext = pageData.hasNext)
    }

    private fun extractTid(document: Document, html: String): String {
        RE_TID.find(document.selectFirst("link[rel=canonical]")?.attr("href") ?: "")?.let {
            return it.groupValues[1]
        }
        return RE_TID.find(html)?.groupValues?.get(1) ?: ""
    }

    private fun parseTotalPages(document: Document): Int {
        var max = 1
        for (a in document.select("a[href*=thread-][href$=-1.html], a[href*=page-]")) {
            Regex("(?:thread-\\d+-(\\d+)-1|page-(\\d+))\\.html").find(a.attr("href"))?.let { m ->
                val n = (m.groupValues[1].ifEmpty { m.groupValues[2] }).toIntOrNull() ?: return@let
                if (n > max) max = n
            }
        }
        return max
    }

    private fun hasNextPage(document: Document): Boolean =
        document.selectFirst("a.nxt, a[href*=page=] i.icon-next") != null
}
