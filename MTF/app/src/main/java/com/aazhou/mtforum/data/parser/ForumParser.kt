package com.aazhou.mtforum.data.parser

import com.aazhou.mtforum.data.model.Forum
import com.aazhou.mtforum.data.model.ForumGroup
import com.aazhou.mtforum.data.model.ForumPage
import com.aazhou.mtforum.data.model.HomeData
import com.aazhou.mtforum.data.model.LikeResult
import com.aazhou.mtforum.data.model.LikeUser
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

    /** 列表卡片配图张数上限：卡片最多铺 3 张，剩下的用右上角「N图」角标概括。 */
    private const val MAX_CARD_IMAGES = 9

    /**
     * 配图过滤规则：命中任一者都不是帖子附件图。
     *
     * 懒加载占位（none.png / blank.gif / loading）、头像接口、站点自带的表情与
     * 文件类型小图都在 static/image 下，取它们会把整卡第一格填成站点素材。
     */
    private val RE_NON_IMAGE = Regex(
        "none\\.png|none\\.gif|blank\\.gif|loading|avatar\\.php|" +
            "/static/image/(common|filetype|smiley)/|smiley|emoticon",
        RegexOption.IGNORE_CASE,
    )

    /** 帖子首帖/楼层正文里的编辑标记：「本帖最后由 X 于 <时间> 编辑」。 */
    private val RE_EDITED = Regex("本[帖贴]最后由\\s*.+?\\s*于\\s*.+?\\s*编辑")

    /** 引用块时间是否为绝对日期（站点对新鲜被引楼层渲染的是「N 分钟前」这类相对时间）。 */
    private val RE_ABS_DATE = Regex("\\d{4}-\\d{1,2}-\\d{1,2}")

    /** 「本帖最后由 X 于 2026-10-5 12:39 编辑」里的时间，用于「时间 编辑」小字。 */
    private val RE_EDIT_TIME = Regex("\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}\\s+\\d{1,2}:\\d{2}(?::\\d{2})?")

    /** 「回复可见」锁定提示文案（站点模板：XX，如果您要查看本帖隐藏内容请回复）。 */
    private val RE_HIDDEN_PROMPT = Regex("查看本帖隐藏内容|回复可见|隐藏内容|回复后.{0,6}查看")

    private val JS_FID = Regex("var\\s+fid\\s*=\\s*parseInt\\(['\"](\\d+)")
    private val JS_AUTHOR_ID = Regex("authorid\\s*=\\s*['\"](\\d+)")

    /** 页面 JS 里的当前登录用户 uid（未登录为 "0"）。 */
    private val RE_UID_VAR = Regex("uid\\s*=\\s*['\"](\\d+)['\"]")

    /** 点赞响应里的结构化剩余次数（优先于中文文案）。 */
    private val RE_LIKES_DAYCOUNT = Regex("daycount'?\\s*:\\s*'?(\\d+)")

    /** 点赞响应里的「您今天还能评价 N 次」（文案兜底）。 */
    private val RE_REMAIN_LIKES = Regex("还能评价\\s*(\\d+)\\s*次")

    /** 点赞响应里的该帖总赞数。 */
    private val RE_RECOMMEND_COUNT = Regex("recommendc'?\\s*:\\s*'?(\\d+)")

    /** 点赞失败文案。 */
    private val RE_LIKE_ERROR = Regex(
        "没有点赞权限|不能点赞|今日评价机会已用完|已用完|关闭的主题|请先登录|formhash错误|非法操作",
    )

    /** 点赞成功标识。 */
    private val RE_LIKE_SUCCESS = Regex("succeedhandle|recommendv|recommendc|点赞成功|评价成功")

    /** 响应里的提示正文（messagetext 下的 p）。 */
    private val RE_TIP_MESSAGE = Regex("(?s)<p>(.*?)(?:<script|</p>)")

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
                    imageUrls = collectThreadImages(li),
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

    /**
     * 列表卡片配图：按页面顺序取全部附件图，最多 9 张（卡片最多铺 3 张 + 角标张数）。
     *
     * 站点懒加载时 src 是 none.png 占位，真实地址在 comiis_loadimages；主容器缺失时
     * 退回正文容器里的 img。过滤占位图与站点自带小图，避免把头像/表情当配图。
     */
    private fun collectThreadImages(li: Element): List<String> {
        val urls = LinkedHashSet<String>()
        val containers = listOf(
            ".comiis_pyqlist_imgs img, .comiis_pyqlist_img img",
            ".mmlist_li_box img",
        )
        for (selector in containers) {
            for (img in li.select(selector)) {
                if (urls.size >= MAX_CARD_IMAGES) break
                val url = listOf("comiis_loadimages", "data-original", "data-src", "file", "src")
                    .firstNotNullOfOrNull { attr ->
                        img.attr(attr).trim().takeIf { it.isNotEmpty() }
                    }
                if (url != null && isCardImage(url)) urls.add(url)
            }
            if (urls.isNotEmpty()) break
        }
        return urls.toList()
    }

    /** 是否是帖子配图而非占位图/站点自带小图（头像、表情、文件类型图标）。 */
    private fun isCardImage(url: String): Boolean {
        val lower = url.lowercase()
        return RE_NON_IMAGE.containsMatchIn(lower).not()
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
        val action = parseActionState(document, html)
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
            isLiked = action.liked,
            likedStateKnown = action.likedKnown,
            isFavorited = action.favorited,
            favoritedStateKnown = action.favoritedKnown,
        )
    }

    /** 首帖的点赞/收藏状态；两个 known 分别表示站点是否明确给出该状态。 */
    private data class ActionState(
        val liked: Boolean = false,
        val likedKnown: Boolean = false,
        val favorited: Boolean = false,
        val favoritedKnown: Boolean = false,
    )

    /**
     * 解析当前登录用户对首帖的点赞/收藏状态。
     *
     * 站点只在登录态渲染点赞/收藏控件，未登录（JS uid=0）或控件缺失时状态视为「未知」，
     * UI 不应据此覆盖本地状态，否则会把已点赞的帖子显示成未点赞。
     */
    private fun parseActionState(document: Document, html: String): ActionState {
        val uid = RE_UID_VAR.find(html)?.groupValues?.get(1)
        if (uid == null || uid == "0") return ActionState()
        var liked = false
        var likedKnown = false
        val recommend = document.selectFirst(
            "a.comiis_recommend_addkey, a.comiis_recommend_new, .comiis_recommend_addkey",
        )
        if (recommend != null) {
            val icon = recommend.selectFirst("i.comiis_recommend_color")
            val classes = recommend.className() + " " + (icon?.className() ?: "")
            val iconHtml = icon?.html().orEmpty()
            liked = classes.contains("f_a") || iconHtml.contains("e654")
            likedKnown = true
        }
        var favorited = false
        var favoritedKnown = false
        val favoriteIcon = document.selectFirst("#comiis_favorite_a i.comiis_favorite_a_color")
        if (favoriteIcon != null) {
            val classes = favoriteIcon.className()
            val iconHtml = favoriteIcon.html()
            favorited = classes.contains("f_a") || iconHtml.contains("e64c")
            favoritedKnown = true
        }
        return ActionState(liked, likedKnown, favorited, favoritedKnown)
    }

    private fun parsePost(li: Element, authorId: String?): Post {
        val authorA = li.selectFirst("a.top_user")
        // 首帖的时间在 span.kmtime，其余楼层在 span.comiis_tm，地点都在 code.comiis_iplocality
        val timeEl = li.selectFirst("span.kmtime") ?: li.selectFirst("span.comiis_tm")
        val contentEl = li.selectFirst("div.comiis_a")
        // 正文里可能同时有引用回复与「隐藏内容」提示两大类引用块，只有前者构成楼中楼，
        // 其余的（如隐藏内容提示）会从正文里摘掉，改由客户端的锁卡呈现。
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
        // 「回复可见」隐藏内容：站点把锁定提示包在 div.comiis_quote（或 div.locked）里，
        // 与真正的引用回复（含 blockquote / 「发表于」）能区分；命中提示文案才算锁定。
        // 提示只是站点排版用的说明，正文里留着与客户端锁卡重复，据此置标志位后摘除节点；
        // 标志位是 HiddenContentCard 的唯一来源，晚了摘会让解锁入口一起消失。
        val hiddenNodes = if (firstPost) {
            contentEl?.select("div.locked, div.comiis_quote")?.filter {
                it.selectFirst("blockquote") == null &&
                    !it.text().contains("发表于") &&
                    RE_HIDDEN_PROMPT.containsMatchIn(it.text())
            }.orEmpty()
        } else {
            emptyList()
        }
        hiddenNodes.forEach { it.remove() }
        // 编辑标记混在正文里会污染引用卡快照与展示，整句剥离成单独字段
        val rawHtml = extractContentHtml(li, contentEl)
        val editedText = RE_EDITED.find(rawHtml)?.value?.trim()
        return Post(
            pid = li.id().removePrefix("pid"),
            authorName = authorA?.text()?.trim() ?: "",
            authorUid = uid,
            avatarUrl = li.selectFirst("img.top_tximg")?.attr("src"),
            level = li.selectFirst("a.top_lev")?.text()?.trim(),
            timeText = timeEl?.ownText()?.trim(),
            // 站点地点形如「来自 河南」（可能带前导换行）；只保留地点本身
            location = (timeEl?.selectFirst("code.comiis_iplocality font")?.text()
                ?: timeEl?.selectFirst("code.comiis_iplocality")?.text())
                ?.replace(Regex("^\\s*来自\\s*"), "")
                ?.trim(),
            contentHtml = RE_EDITED.replace(rawHtml, "").trim(),
            isOp = li.selectFirst("span.top_lev.bg_c") != null || (authorId != null && uid == authorId),
            isFirstPost = firstPost,
            quote = quote,
            editedText = editedBadge(editedText),
            hasHiddenContent = hiddenNodes.isNotEmpty(),
        )
    }

    /**
     * 抽取楼层正文 HTML。
     *
     * 站点把附件图从正文里抽出，集中放在与 comiis_a 平级的 ul.comiis_img_list
     * （单张为 div.comiis_img_one）中，只取 comiis_a 会把图片整体丢掉；
     * 这里按容器子元素原顺序把它们并回正文，保持图文相对位置。
     */
    private fun extractContentHtml(li: Element, contentEl: Element?): String {
        val container = contentEl?.parent() ?: li.selectFirst("div.comiis_messages")
        if (container == null) return contentEl?.html().orEmpty()
        val html = StringBuilder()
        for (child in container.children()) {
            when {
                child === contentEl -> html.append(child.html())
                child.tagName() == "ul" && child.hasClass("comiis_img_list") -> html.append(child.outerHtml())
                child.hasClass("comiis_img_one") -> html.append(child.outerHtml())
            }
        }
        return html.toString()
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
        // 站点 meta 形如「X 发表于 8 分钟前 来自 河南」；时间字段要去掉「来自 地点」，只留时间
        val timePart = metaLine.substringAfter("发表于")
            .substringBefore("来自")
            .trim().trimEnd(']').trim()
        return PostQuote(
            author = metaLine.substringBefore("发表于").trim().ifEmpty { null },
            // 引用块里的时间烘在楼层 HTML 中，是「引用那一刻」的快照：被引楼层当时新鲜，
            // 站点渲染成「N 分钟前」「19 秒前」，隔天回看就是错的；页面没有 dateline，
            // 客户端也推算不出真实时间。只有绝对日期才是可靠时间，相对时间一律不取。
            time = timePart.takeIf { RE_ABS_DATE.containsMatchIn(it) },
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

    /** 编辑页里该楼删除用的校验哈希（找不到返回 null，调用方退回通用 formhash）。 */
    fun extractDeleteHash(html: String, pid: String): String? {
        Regex("formhash=([0-9a-zA-Z]+)[^\"'<>]{0,200}?pid=$pid").find(html)
            ?.let { return it.groupValues[1] }
        Regex("pid=$pid[^\"'<>]{0,200}?formhash=([0-9a-zA-Z]+)").find(html)
            ?.let { return it.groupValues[1] }
        return null
    }

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
    /** 个人空间页的 uid：优先 discuz_uid，其次 JS 变量 uid（指定 uid 查看时指向被查看者）。 */
    fun parseUserProfile(html: String): UserProfile {
        val document = doc(html)
        val uid = Regex("discuz_uid\\s*=\\s*'(\\d+)'").find(html)?.groupValues?.get(1)
            ?: RE_UID_VAR.find(html)?.groupValues?.get(1)
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

    /** 点赞用户列表（misc.php?op=recommend）：昵称取自链接文本，需剔掉图标字体私用区字符。 */
    fun parseLikeUsers(html: String): List<LikeUser> {
        val box = doc(html).selectFirst("div.comiis_userlist") ?: return emptyList()
        return box.select("li a[href*=uid=]").mapNotNull { a ->
            val uid = RE_UID.find(a.attr("href"))?.groupValues?.get(1) ?: return@mapNotNull null
            val name = a.text().replace(Regex("[\uE000-\uF8FF]"), "").trim().ifEmpty { "用户$uid" }
            LikeUser(uid = uid, name = name, avatarUrl = a.selectFirst("img")?.attr("src"))
        }
    }

    /**
     * 解析点赞/取消点赞的接口响应。
     *
     * 点赞：剩余可赞次数优先取 `daycount`，缺失时用「您今天还能评价 N 次」兜底。
     * 取消：站点只回 `ok`（无次数/计数），只需判断是否成功。
     */
    fun parseLikeResult(body: String, like: Boolean): LikeResult {
        val bad = RE_LIKE_ERROR.containsMatchIn(body) || body.contains("action=login", true)
        val success = body.isNotBlank() && !bad && if (like) {
            RE_LIKE_SUCCESS.containsMatchIn(body) || !body.contains("<html", true)
        } else {
            body.contains(">ok<") || body.contains("'ok'") || !body.contains("<html", true)
        }
        val remaining = RE_LIKES_DAYCOUNT.find(body)?.groupValues?.get(1)?.toIntOrNull()
            ?: RE_REMAIN_LIKES.find(body)?.groupValues?.get(1)?.toIntOrNull()
        val count = RE_RECOMMEND_COUNT.find(body)?.groupValues?.get(1)?.toIntOrNull()
        val message = RE_TIP_MESSAGE.find(body)?.groupValues?.get(1)
            ?.replace(Regex("<[^>]+>"), "")
            ?.replace(Regex("您今天还能评价\\s*\\d+\\s*次"), "")
            ?.trim()
            ?.ifBlank { null }
        return LikeResult(success = success, remainingToday = remaining, serverCount = count, message = message)
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

    /**
     * 帖子详情总页数。
     *
     * 站点分页链接有两种形态：正序是 `thread-{tid}-{page}-1.html`，
     * 倒序（带 ordertype 等参数）是 `...&page={page}`（页码在查询串里）；
     * 只认前一种会让倒序页恒得出 1 页，从而只加载首屏。
     */
    private fun parseTotalPages(document: Document): Int {
        var max = 1
        for (a in document.select("a[href*=thread-], a[href*=page]")) {
            val href = a.attr("href")
            val n = Regex("thread-\\d+-(\\d+)-1\\.html").find(href)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("[?&]page=(\\d+)").find(href)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("page-(\\d+)\\.html").find(href)?.groupValues?.get(1)?.toIntOrNull()
                ?: continue
            if (n > max) max = n
        }
        return max
    }

    private fun hasNextPage(document: Document): Boolean =
        document.selectFirst("a.nxt, a[href*=page=] i.icon-next") != null
}
