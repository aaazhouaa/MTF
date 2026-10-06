package com.aazhou.mtforum.data.parser

import com.aazhou.mtforum.data.model.Forum
import com.aazhou.mtforum.data.model.ForumGroup
import com.aazhou.mtforum.data.model.ForumPage
import com.aazhou.mtforum.data.model.HomeData
import com.aazhou.mtforum.data.model.LoginResult
import com.aazhou.mtforum.data.model.Post
import com.aazhou.mtforum.data.model.PostQuote
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
    private val RE_QUOTE_PID = Regex("pid=(\\d+)")
    private val RE_BR = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
    private val RE_FLOOR_NUMBER = Regex("^(\\d+)")

    /** 帖子首帖/楼层正文里的编辑标记：「本帖最后由 X 于 <时间> 编辑」。 */
    private val RE_EDITED = Regex("本[帖贴]最后由\\s*.+?\\s*于\\s*.+?\\s*编辑")

    /** 「本帖最后由 X 于 2026-10-5 12:39 编辑」里的时间，用于「时间 编辑」小字。 */
    private val RE_EDIT_TIME = Regex("\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}\\s+\\d{1,2}:\\d{2}(?::\\d{2})?")

    private val JS_FID = Regex("var\\s+fid\\s*=\\s*parseInt\\(['\"](\\d+)")
    private val JS_AUTHOR_ID = Regex("authorid\\s*=\\s*['\"](\\d+)")

    /** 首页楼层的中文称谓，用于反推绝对楼层号。 */
    private val FLOOR_NAMES = mapOf(
        "沙发" to 2, "椅子" to 3, "板凳" to 4, "地毯" to 5, "凉席" to 6,
        "报纸" to 7, "地板" to 8, "地下室" to 9, "下水道" to 10,
    )

    private fun doc(html: String): Document = Jsoup.parse(html, BASE)

    /** 编辑标记保留「时间 编辑」同行小字；取不到时间则返回 null，不显示。 */
    private fun editedBadge(marker: String?): String? =
        RE_EDIT_TIME.find(marker.orEmpty())?.value?.let { "$it 编辑" }

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
                    forumName = li.selectFirst("div.comiis_xznalist_bk a[href*=forum-]")?.let { a ->
                        a.ownText().trim().ifEmpty { a.text().trim() }
                    }?.ifEmpty { null },
                    thumbnail = li.selectFirst("img[comiis_loadimages]")?.attr("comiis_loadimages")?.takeIf { it.isNotBlank() },
                    likeCount = li.select("div.comiis_xznalist_bottom li span.comiis_tm").firstOrNull()?.text()?.trim(),
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
        val postNodes = document.select("div.comiis_postli[id^=pid]")
        // 楼层号是帖子内绝对楼层（首页为「沙发」等称谓），而标题栏给的是作者 uid，
        // 故楼层号只能由 JS 变量 + 本页首个楼层的编号共同推出。
        val floorBase = postNodes.firstOrNull()?.let { floorNumberOf(it.selectFirst("span.f_d.y")?.text()?.trim()) }
        val authorId = JS_AUTHOR_ID.find(html)?.groupValues?.get(1)
        val firstPostIndex = postNodes.indexOfFirst { it.selectFirst("div.comiis_postli_topv1") != null }
        val posts = postNodes.map { li ->
            val post = parsePost(li, authorId)
            val explicit = floorNumberOf(li.selectFirst("span.f_d.y")?.text()?.trim())
            val floor = when {
                post.isFirstPost -> 0
                explicit != null -> explicit
                floorBase != null && firstPostIndex >= 0 ->
                    floorBase + (postNodes.indexOf(li) - firstPostIndex)
                else -> null
            }
            post.copy(
                floor = floor,
                floorLabel = floorName(li.selectFirst("span.f_d.y")?.text()?.trim(), floor),
            )
        }
        // 「全部评论」只计回复，首帖不计入
        val totalReplies = document.selectFirst("div.comiis_pltit h2 span")
            ?.text()?.trim()?.toIntOrNull()
        // 底部互动行在移动版也带真实计数：点赞 em.comiis_recommend_num 的「N赞」、收藏 span.comiis_favorite_a_num
        val likeCount = document.selectFirst("em.comiis_recommend_num")
            ?.text()?.let { numberFromText(it) }
        val favoriteCount = document.selectFirst("span.comiis_favorite_a_num")
            ?.text()?.let { numberFromText(it) }
        val replyUrl = document.selectFirst("form#fastpostform")?.attr("action")
        return ThreadPage(
            tid = extractTid(document, html),
            subject = subject,
            // 详情页顶部面包屑给出所属版块（如「求助问答」）
            forumName = document.selectFirst("a.kmtit")?.text()?.trim(),
            posts = posts,
            page = page,
            totalPages = parseTotalPages(document),
            formhash = parseFormhash(html),
            replyUrl = replyUrl,
            fid = JS_FID.find(html)?.groupValues?.get(1)
                ?: Regex("fid=(\\d+)").find(replyUrl ?: "")?.groupValues?.get(1),
            opUid = authorId,
            totalReplies = totalReplies,
            likeCount = likeCount,
            favoriteCount = favoriteCount,
        )
    }

    private fun parsePost(li: Element, authorId: String?): Post {
        val authorA = li.selectFirst("a.top_user")
        // 首帖的时间在 span.kmtime，其余楼层在 span.comiis_tm，地点都在 code.comiis_iplocality
        val timeEl = li.selectFirst("span.kmtime") ?: li.selectFirst("span.comiis_tm")
        val contentEl = li.selectFirst("div.comiis_a")
        // 正文里可能同时有引用回复与「隐藏内容」提示两大类引用块，只有前者构成楼中楼，
        // 其余的（如隐藏内容提示）保持在正文 HTML 中，由 PostContent 渲染成卡片。
        var quote: PostQuote? = null
        for (el in contentEl?.select("div.comiis_quote").orEmpty()) {
            val parsed = parseQuote(el)
            if (parsed != null) {
                quote = parsed
                el.remove()
                break
            }
        }
        val uid = RE_UID.find(authorA?.attr("href") ?: "")?.groupValues?.get(1)
        // 只有首页第一层才是主题首帖；楼主后续的回复仍要靠 uid 判定为楼主
        val firstPost = li.selectFirst("div.comiis_postli_topv1") != null
        // 编辑标记混在正文里会污染引用卡快照与展示，整句剥离成单独字段
        val rawHtml = contentEl?.html() ?: ""
        val editedText = RE_EDITED.find(rawHtml)?.value?.trim()
        return Post(
            pid = li.id().removePrefix("pid"),
            authorName = authorA?.text()?.trim() ?: "",
            authorUid = uid,
            avatarUrl = li.selectFirst("img.top_tximg")?.attr("src"),
            level = li.selectFirst("a.top_lev")?.text()?.trim(),
            timeText = timeEl?.ownText()?.trim(),
            location = timeEl?.selectFirst("code.comiis_iplocality font")?.text()?.trim()
                ?: timeEl?.selectFirst("code.comiis_iplocality")?.text()?.trim(),
            contentHtml = RE_EDITED.replace(rawHtml, "").trim(),
            isOp = li.selectFirst("span.top_lev.bg_c") != null || (authorId != null && uid == authorId),
            isFirstPost = firstPost,
            quote = quote,
            editedText = editedBadge(editedText),
        )
    }

    /**
     * 解析引用块；不是「引用某人回复」（无「发表于」元信息）时返回 null，
     * 由调用方保留在原正文里。
     *
     * 两种排版：
     * 1. `<font color="#999999">作者 发表于 时间</font><br />正文`；
     * 2. `<a href="...pid=123"><font color="#999999">作者 发表于 时间 来自 地点]</font></a><br />正文`。
     * <br> 不会出现在 text() 里，因此切行要基于 HTML 而不是文本。
     */
    private fun parseQuote(quote: Element): PostQuote? {
        val pid = RE_QUOTE_PID.find(quote.html())?.groupValues?.get(1)
        val lines = quote.html()
            .split(RE_BR)
            .map { Jsoup.parse(it).text().trim() }
            .filter { it.isNotEmpty() }
        val metaLine = lines.firstOrNull()?.removePrefix("回复")?.trim().orEmpty()
        if (!metaLine.contains("发表于")) return null
        return PostQuote(
            author = metaLine.substringBefore("发表于").trim().ifEmpty { null },
            time = metaLine.substringAfter("发表于").trim().trimEnd(']').trim().ifEmpty { null },
            text = lines.drop(1).joinToString("\n"),
            pid = pid,
        )
    }

    /** 楼层标签转为楼层号：「沙发」为 2，「16#」为 16；无标签（首帖）返回 null。 */
    private fun floorNumberOf(label: String?): Int? {
        val text = label?.trim() ?: return null
        if (text.isEmpty()) return null
        // 第二页起站点给的标签形如 «16<sup>#</sup>»，取开头数字即可
        RE_FLOOR_NUMBER.find(text)?.groupValues?.get(1)?.let { return it.toInt() }
        return FLOOR_NAMES[text]
    }

    private fun floorName(label: String?, floor: Int?): String? {
        if (label.isNullOrBlank() || floor == null) return null
        // 数字型标签统一成 «N#»；「沙发」等中文称谓按站点原文保留
        if (RE_FLOOR_NUMBER.containsMatchIn(label)) return "$floor#"
        return label
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
        if (uid == null || uid == "0") return UserProfile(uid = uid)
        // comiis 移动模板的个人空间主页
        val name = document.selectFirst(".comiis_space_tx h2")?.text()?.trim()
            ?: document.selectFirst("a.sidenv_user")?.text()?.trim()
        val avatar = document.selectFirst(".comiis_space_tx img[src*=avatar], .comiis_space_tx .user_img img")
            ?.attr("src")
            ?: document.selectFirst("img.sidenv_tximg, div.sidenv_user img")?.attr("src")
        fun ico(label: String): Int? = document
            .select(".comiis_space_profileico li:contains($label) span")
            .firstOrNull()?.text()?.let(::numberFromText)
        val jf = document.select(".comiis_space_profilejf ul li")
        fun jfValue(index: Int): Int? =
            jf.getOrNull(index)?.selectFirst(".f_0, span")?.text()?.let(::numberFromText)
        return UserProfile(
            uid = uid,
            name = name,
            avatarUrl = avatar,
            level = document.selectFirst(".comiis_space_tx .kmlev, .comiis_space_tx .user_lev")?.text()?.trim(),
            groupName = document.selectFirst(".comiis_space_tx .kmlev")?.text()?.trim(),
            threads = ico("帖子"),
            posts = ico("回复"),
            friends = ico("好友"),
            following = ico("关注"),
            followers = ico("粉丝"),
            credits = jfValue(0),
            gold = jfValue(2),
            onlineTime = document.select(".comiis_space_profile li:contains(在线时间) .profile_rs")
                .firstOrNull()?.text()?.trim(),
        )
    }

    private fun numberFromText(text: String): Int? =
        Regex("\\d+").find(text)?.value?.toIntOrNull()

    /** 从收藏列表页找出指定主题的 favid（取消收藏需要 favid 而非 tid）。 */
    fun parseFavoriteFavid(html: String, tid: String): String? {
        val document = doc(html)
        // 链接或行内含该主题，同一段 HTML 里带 favid
        for (a in document.select("a[href*=favid=]")) {
            val href = a.attr("href")
            val li = a.closest("li") ?: a
            val context = href + " " + li.html()
            if (context.contains("tid=$tid") || context.contains("thread-$tid-")) {
                Regex("favid=(\\d+)").find(context)?.groupValues?.get(1)?.let { return it }
            }
        }
        return null
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
