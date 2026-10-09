package com.aazhou.mtforum.data.parser

import com.aazhou.mtforum.data.model.Forum
import com.aazhou.mtforum.data.model.ForumGroup
import com.aazhou.mtforum.data.model.ForumPage
import com.aazhou.mtforum.data.model.HomeData
import com.aazhou.mtforum.data.model.LikeResult
import com.aazhou.mtforum.data.model.LikeUser
import com.aazhou.mtforum.data.model.LoginResult
import com.aazhou.mtforum.data.model.Notice
import com.aazhou.mtforum.data.model.NoticePage
import com.aazhou.mtforum.data.model.Post
import com.aazhou.mtforum.data.model.PostQuote
import com.aazhou.mtforum.data.model.ReplyResult
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
    // 贴图上传凭据：comiis 模板把 uid/hash 写进帖子页内联的上传配置里
    private val RE_UPLOAD_UID = Regex("uploadformdata:\\{uid:\"(\\d+)\"")
    private val RE_UPLOAD_HASH = Regex("uploadformdata:\\{uid:\"\\d+\",\\s*hash:\"([0-9a-fA-F]+)\"")
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
    // 帖子页 JS 里的 tid 赋值（tid = '173660'）；parseInt('173660') 形态紧随其后同值，不必另行匹配
    private val RE_JS_TID = Regex("\\btid\\s*=\\s*['\"](\\d+)")

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

    /**
     * 剥掉不可见字符后再 trim。
     *
     * 站点往列表文本里混图标字体字符：标题锚点里有 `<span class="bg_a f_f">` 装着私用区
     * 字符（实测 U+E64E），`Element.text()` 会把子元素文本一并拼进标题，而 `trim()` 只去
     * 空白、去不掉它。该字符无字形、不打印，在 Compose 里却仍占一个字符宽度，于是标题比
     * 同一张卡的正文多缩进一点，且只发生在带这类标记的帖子上——详情页标题取自
     * `document.title()`、不含它，所以详情页看着是左对齐的。
     */
    private fun String.stripInvisibleChars(): String = filterNot { it.isInvisibleChar() }.trim()

    /**
     * 不可见但占位的字符：Unicode 私用区（站点图标字体都落在这里）与零宽连接符、软连字符等
     * 无字形格式字符。注意 `Char.isWhitespace()` 对它们一律返回 false，`trim()` 拦不住。
     */
    private fun Char.isInvisibleChar(): Boolean {
        val cp = code
        return cp in 0xE000..0xF8FF ||
            cp == 0x200B || cp == 0x200C || cp == 0x200D || cp == 0x2060 ||
            cp == 0xFEFF || cp == 0x00AD || cp == 0x180E
    }

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
                    subject = a.text().stripInvisibleChars().removePrefix("置顶").trim(),
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
                    subject = titleA.text().stripInvisibleChars(),
                    authorName = authorA?.text()?.trim(),
                    authorUid = RE_UID.find(authorA?.attr("href") ?: "")?.groupValues?.get(1),
                    avatarUrl = li.selectFirst("img.top_tximg")?.attr("src"),
                    level = li.selectFirst("span.top_lev")?.text()?.trim(),
                    timeText = li.selectFirst("div.forumlist_li_time span")?.text()?.trim(),
                    replyCount = li.selectFirst("div.comiis_xznalist_bottom li a[href*=thread-] span.comiis_tm")?.text()?.trim(),
                    viewCount = li.select("div.comiis_xznalist_bottom li span.comiis_tm").lastOrNull()?.text()?.trim(),
                    excerpt = li.selectFirst("div.list_body a")?.text()?.stripInvisibleChars(),
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
        val tid = extractTid(document, html)
        val postNodes = document.select("div.comiis_postli[id^=pid]")
        // 楼层号是帖子内绝对楼层（第 2 页起是「11#」这类数字标签，第 1 页常整页不给标签），
        // 而标题栏给的是作者 uid，故只能由「本页第一个带楼层标签的节点 + 页内偏移」推出。
        // 锚点取第一个带标签的节点而非首个节点：首帖没有楼层标签，拿它当锚点会整页都推不出楼层号。
        val anchorIndex = postNodes.indexOfFirst {
            floorNumberOf(it.selectFirst("span.f_d.y")?.text()?.trim()) != null
        }
        val anchorFloor = anchorIndex.takeIf { it >= 0 }?.let {
            floorNumberOf(postNodes[it].selectFirst("span.f_d.y")?.text()?.trim())
        }
        val authorId = JS_AUTHOR_ID.find(html)?.groupValues?.get(1)
        val firstPostIndex = postNodes.indexOfFirst { it.selectFirst("div.comiis_postli_topv1") != null }
        val posts = postNodes.mapIndexed { index, li ->
            val post = parsePost(li, authorId)
            val label = li.selectFirst("span.f_d.y")?.text()?.trim()
            val explicit = floorNumberOf(label)
            val floor = when {
                post.isFirstPost -> 0
                // 站点给了楼层标签（「沙发」「11#」）就直接换算成楼层号
                explicit != null -> explicit
                // 第 1 页整页没标签时按页内序号推：站点第一条回复恒为沙发（2 楼）
                page == 1 && firstPostIndex >= 0 -> FIRST_REPLY_FLOOR + index - firstPostIndex - 1
                // 其余页从第一个带标签的楼层往后推
                anchorFloor != null -> anchorFloor + (index - anchorIndex)
                else -> null
            }
            post.copy(
                floor = floor,
                // 楼层显示统一成 «N#»：站点第 1 页不给标签，给了也是「沙发」这类称谓
                floorLabel = floor?.let { "$it#" },
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
        val noticeAuthor = document.selectFirst("form#fastpostform input[name=noticeauthor]")
            ?.attr("value")?.trim()?.ifBlank { null }
        val action = parseActionState(document, html)
        val follow = parseFollowState(document)
        return ThreadPage(
            tid = tid,
            subject = subject,
            // 详情页顶部面包屑给出所属版块（如「求助问答」）
            forumName = document.selectFirst("a.kmtit")?.text()?.trim(),
            posts = posts,
            page = page,
            totalPages = parseTotalPages(document, tid),
            formhash = parseFormhash(html),
            replyUrl = replyUrl,
            noticeAuthor = noticeAuthor,
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
            isFollowingOp = follow.first,
            followingStateKnown = follow.second,
            uploadUid = RE_UPLOAD_UID.find(html)?.groupValues?.get(1),
            uploadHash = RE_UPLOAD_HASH.find(html)?.groupValues?.get(1),
        )
    }

    /**
     * 解析当前登录用户对楼主的关注状态；[Pair.second] 为 false 表示页面没给出该状态。
     *
     * 站点用关注控件的 op 参数区分（add=未关注，del=已关注），链接文本兜底；
     * 未登录或控件缺失时状态未知，界面应回落到本地记录，否则已关注的人重进帖子
     * 会被显示成未关注。
     */
    private fun parseFollowState(document: Document): Pair<Boolean, Boolean> {
        val link = document.selectFirst("a[href*=ac=follow]") ?: return false to false
        val href = link.attr("href")
        val text = link.text()
        if (href.contains("op=del") || text.contains("取消关注")) return true to true
        if (href.contains("op=add")) return false to true
        if (text.contains("已关注")) return true to true
        return false to true
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

    /**
     * 第 1 条回复的楼层号：站点把它称作「沙发」，与下方 [FLOOR_NAMES] 的映射一致。
     * 第 1 页整页不给楼层标签时按它逐条往下推。
     */
    private const val FIRST_REPLY_FLOOR = 2


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
        // comiis 模板的个人中心：kmlevs 是等级（Lv.3），kmlev 是用户组（初中生）；
        // 头衔在个别模板里嵌在 h2 内，故昵称取 ownText，取不到再整段去掉头衔
        val nameEl = document.selectFirst(".comiis_space_tx h2")
        val level = document.selectFirst(".comiis_space_tx .kmlevs")?.text()?.trim()
            ?: document.selectFirst(".comiis_space_tx .user_lev")?.text()?.trim()
        val name = nameEl?.ownText()?.trim()?.ifEmpty { null }
            ?: nameEl?.text()?.trim()?.removeSuffix(level.orEmpty())?.trim()
            ?: document.selectFirst("a.sidenv_user")?.text()?.trim()
        val avatar = document.selectFirst(".comiis_space_tx .user_img img")?.attr("src")
            ?: document.selectFirst("img.sidenv_tximg, div.sidenv_user img")?.attr("src")
        return UserProfile(
            uid = uid,
            name = name,
            avatarUrl = avatar,
            level = level,
            groupName = document.selectFirst(".comiis_space_tx .kmlev")?.text()?.trim(),
            threads = numberByLabel(document, "帖子", "主题"),
            posts = numberByLabel(document, "回复", "回帖"),
            friends = numberByLabel(document, "好友"),
            following = numberByLabel(document, "关注"),
            followers = numberByLabel(document, "粉丝"),
            credits = numberByLabel(document, "积分"),
            gold = numberByLabel(document, "金币"),
            onlineTime = textByLabel(document, "在线时间"),
        )
    }

    /**
     * 按标签取数值（统计 / 积分 / 金币 / 关注）。
     *
     * 标签与数值的排布有三种：同节点（«span: 帖子 1»）、数值在前（«span: 437»积分）、
     * 拆成兄弟节点（«span:帖子»«span:42»）。一律先看标签所在节点自身的文本——范围最紧，
     * 不会串到隔壁项——再看它的父行；标签后、标签前两个方向都找。
     */
    private fun numberByLabel(root: Element, vararg labels: String): Int? {
        for (scope in profileScopes(root)) {
            for (label in labels) {
                for (holder in scope.select(":containsOwn($label)")) {
                    numberNearLabel(holder.text(), label)?.let { return it }
                    numberNearLabel((holder.parent() ?: holder).text(), label)?.let { return it }
                }
            }
        }
        return null
    }

    /** 标签后的数字优先，否则取标签前的最后一个数字。 */
    private fun numberNearLabel(text: String, label: String): Int? {
        Regex("""\d+""").find(text.substringAfter(label, ""))?.value?.toIntOrNull()
            ?.let { return it }
        return Regex("""\d+""").findAll(text.substringBefore(label, ""))
            .lastOrNull()?.value?.toIntOrNull()
    }

    /** 按标签取文本（在线时长这类「数字 + 单位」的值）。 */
    private fun textByLabel(root: Element, vararg labels: String): String? {
        for (scope in profileScopes(root)) {
            for (label in labels) {
                for (holder in scope.select(":containsOwn($label)")) {
                    textNearLabel(holder.text(), label)?.let { return it }
                    textNearLabel((holder.parent() ?: holder).text(), label)?.let { return it }
                }
            }
        }
        return null
    }

    /** 标签后的时长片段优先，否则取标签前紧邻的一段。 */
    private fun textNearLabel(text: String, label: String): String? {
        val after = text.substringAfter(label, "").trim().trimStart(':', '：')
            .takeWhile { it.isDigit() || it.isWhitespace() || it in "小时分钟天秒" }
            .trim()
        if (after.isNotEmpty()) return after
        return text.substringBefore(label, "").trim()
            .takeLastWhile { it.isDigit() || it.isWhitespace() || it in "小时分钟天秒" }
            .trim()
            .ifEmpty { null }
    }

    /** 资料数值的查找范围：从最具体的容器逐步放宽到整页。 */
    private fun profileScopes(root: Element): List<Element> {
        val scopes = LinkedHashSet<Element>()
        for (selector in listOf(
            ".comiis_space_profileico",
            ".comiis_space_profilejf",
            ".comiis_space_profile",
            ".comiis_space_tx",
            ".comiis_space",
        )) {
            scopes.addAll(root.select(selector))
        }
        scopes.add(root)
        return scopes.toList()
    }

/**
     * 单条提醒里出现的时间文本：相对时间（N分钟前/今天/昨天）或绝对日期。
     * 「昨天」这类相对日词后面常跟钟点（昨天 15:22），一并吃进来，否则只剩「昨天」。
     */
    private val RE_NOTICE_TIME = Regex(
        "\\d{4}-\\d{1,2}-\\d{1,2}(\\s+\\d{1,2}:\\d{2}(:\\d{2})?)?" +
            "|\\d{1,2}-\\d{1,2}\\s+\\d{1,2}:\\d{2}" +
            "|(?:今天|昨天|前天)(?:\\s+\\d{1,2}:\\d{2}(?::\\d{2})?)?" +
            "|\\d+\\s*(分钟|小时|天|秒)前",
    )

    /** 提醒行里指向主题的链接：移动模板用 redirect&goto=findpost&ptid=TID，旧式用 thread-TID。 */
    private val RE_NOTICE_TID = Regex("""(?:ptid|tid)=(\d+)""")

    /** 提醒行里指向具体楼层的链接：goto=findpost&pid=PID。 */
    private val RE_NOTICE_PID = Regex("""[?&]pid=(\d+)""")

    /** 提醒里帖子标题的最大显示长度，超出部分用省略号代替。 */
    private const val NOTICE_TITLE_MAX = 8

    /** 帖子地址里的页码：`viewthread&tid=X&page=N` 或 `thread-X-N-1.html`。 */
    private val RE_THREAD_PAGE = Regex("""[?&]page=(\d+)|thread-\d+-(\d+)-""")

    /** 从（跟随重定向后的）帖子地址里取页码；取不到按第 1 页处理。 */
    fun pageFromThreadUrl(url: String): Int {
        val m = RE_THREAD_PAGE.find(url) ?: return 1
        return (m.groupValues[1].ifEmpty { m.groupValues[2] }).toIntOrNull()?.coerceAtLeast(1) ?: 1
    }

    /** 楼层正文的纯文本摘要（提醒列表里显示回复内容用）：去标签、压空白。 */
    fun plainText(html: String): String =
        doc(html).text().replace(Regex("""\s+"""), " ").trim()

    /**
     * 「我的」→「消息提醒」的某个类别页（home.php?mod=space&do=notice&view=…）。
     *
     * 每条提醒是 div.comiis_notices_box 里的一个 li：头像链接 + .ntc_body 正文 +
     * h2 里的时间与操作。类别菜单（我的消息/我的帖子/…）也是 li，靠容器与
     * 「有正文或有主题链接」排除掉。容器类名随模板版本变化，故再留两层兜底。
     *
     * 未登录只能看登录表单：登录态页面同样带着退出登录链接
     * （member.php?mod=logging&action=logout），用 mod=logging/action=login 的宽松
     * 子串判断会把已登录用户误判成未登录，消息页直接摆「请先登录」。
     */
    fun parseNotices(html: String): NoticePage {
        val document = doc(html)
        val loggedOut = document.selectFirst("form#loginform") != null

        var rows: List<Element> = document.select("div.comiis_notices_box li").filter { li ->
            li.selectFirst(".ntc_body") != null || li.select("a[href]").any {
                RE_NOTICE_TID.containsMatchIn(it.attr("href")) ||
                    RE_TID.containsMatchIn(it.attr("href"))
            }
        }
        if (rows.isEmpty()) {
            rows = document.select("li").filter { li ->
                li.select("a[href]").any {
                    RE_NOTICE_TID.containsMatchIn(it.attr("href")) ||
                        RE_TID.containsMatchIn(it.attr("href"))
                }
            }
        }
        if (rows.isEmpty()) {
            rows = document.select("li").filter { RE_NOTICE_TIME.containsMatchIn(it.text()) }
        }
        val notices = rows.mapIndexedNotNull { index, li -> noticeFromItem(li, index) }
        return NoticePage(loggedOut = loggedOut, notices = notices)
    }

    /** 单个 li 转成提醒：正文取 .ntc_body（没有则整行），时间取 h2 里的时间段。 */
    private fun noticeFromItem(li: Element, index: Int): Notice? {
        val body = li.selectFirst(".ntc_body")
        // 站点用 &nbsp; 撑空白，Jsoup 会转成不换行空格，先归一成普通空格再裁剪
        val raw = (body ?: li).text()
            .replace(Regex("[\\uE000-\\uF8FF]"), "")
            .replace('\u00A0', ' ')
            .trim()
        if (raw.isEmpty()) return null
        val time = RE_NOTICE_TIME.findAll(li.selectFirst("h2")?.text() ?: raw)
            .lastOrNull()?.value
        var text = raw
        if (time != null && text.endsWith(time)) text = text.dropLast(time.length).trim()
        // 行尾的「查看」是跳转操作链接，不是正文
        text = text.replace(Regex("\\s*查看\\s*$"), "").trim()
            .trimEnd('·', ' ', ',', '，')
            .ifBlank { raw }
        val tid = li.select("a[href]").asSequence()
            .map { it.attr("href") }
            .mapNotNull { href ->
                RE_NOTICE_TID.find(href)?.groupValues?.get(1)
                    ?: RE_TID.find(href)?.groupValues?.get(1)
            }
            .firstOrNull()
        val pid = li.select("a[href]").asSequence()
            .map { it.attr("href") }
            .mapNotNull { href -> RE_NOTICE_PID.find(href)?.groupValues?.get(1) }
            .firstOrNull()
        // 帖子标题超长时截断：提醒行只有一行多点，长标题会把正文挤没了
        val titleLink = body?.select("a[href]")?.firstOrNull {
            RE_NOTICE_TID.containsMatchIn(it.attr("href"))
        }
        val fullTitle = titleLink?.text()?.trim()?.ifBlank { null }
        if (fullTitle != null && fullTitle.length > NOTICE_TITLE_MAX) {
            text = text.replace(fullTitle, fullTitle.take(NOTICE_TITLE_MAX) + "…")
        }
        // 站点给每条提醒一个稳定 id（屏蔽链接的 a_note_NNNN）；列表 key 用它，
        // 新消息插到前面时后面的行不会因为下标平移而整批重建
        val noteId = li.selectFirst("a[id^=a_note_]")?.id()?.removePrefix("a_note_")
        val id = noteId ?: pid ?: tid ?: "notice-$index"
        // 互动类提醒的头像链接：a.notice_img 包着头像，href 带 uid=；系统类提醒没有它，两项自然为 null
        val avatarLink = li.selectFirst("a.notice_img")
        val avatarUrl = avatarLink?.selectFirst("img")?.attr("src")?.trim()?.takeIf { it.isNotBlank() }
        val uid = avatarLink?.attr("href")?.let { RE_UID.find(it)?.groupValues?.get(1) }
        return Notice(
            id = id,
            text = text,
            timeText = time,
            tid = tid,
            pid = pid,
            avatarUrl = avatarUrl,
            uid = uid,
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

    /** inajax 响应外裹的 XML CDATA。 */
    private val RE_CDATA = Regex("(?s)<!\\[CDATA\\[(.*?)\\]\\]>")

    /** inajax 回执的 XML 外壳：站点给了处理脚本才是完整回执，没给也说明走了 inajax 分支。 */
    private val RE_INAJAX_XML = Regex("(?s)<root>\\s*<!\\[CDATA\\[")

    /** errorhandle_xxx('提示', …) 第一参：站点回执的提示原文。 */
    private val RE_ERROR_HANDLE_MSG = Regex("errorhandle_[A-Za-z0-9_]+\\s*\\(\\s*['\"]([^'\"]+)['\"]")

    /**
     * 快速回复（inajax）接口响应判定。
     *
     * 带 inajax=1 时站点返回 XML，CDATA 里是 succeedhandle_（成功）或 errorhandle_（失败）
     * 脚本；据此判成败，失败时摘出站点提示原文。失败路径一定会带 errorhandle_，故回执是
     * inajax XML 却既无错误脚本、也没给成功脚本时按成功算——宁可放过一个静默成功，也不要把
     * 已发出的回复报成失败、诱使用户重发。整页 HTML（未走 inajax、被重定向到登录页等）不认。
     */
    fun parseReplyResult(body: String): ReplyResult {
        if (body.contains("errorhandle_")) return ReplyResult(false, extractReplyError(body))
        val success = body.contains("succeedhandle_") || RE_INAJAX_XML.containsMatchIn(body)
        return ReplyResult(success, if (success) null else extractReplyError(body))
    }

    /** 摘站点提示原文：优先 errorhandle_xxx('提示', …) 第一参，兜底取 CDATA 去标签纯文本。 */
    private fun extractReplyError(body: String): String? {
        val cdata = RE_CDATA.find(body)?.groupValues?.get(1) ?: body
        RE_ERROR_HANDLE_MSG.find(cdata)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }?.let { return it }
        return cdata
            .replace(Regex("(?s)<script.*?</script>"), "")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { null }
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
        // tid 取值按可靠性排序：正文前部常引用其它帖（thread-其它tid-1-1.html），
        // 直接扫 thread-\d+ 会误取被引帖的 tid，分页链接随之全被 parseTotalPages 过滤、
        // 总页数算成 1。故优先用页面 JS 的 tid 赋值，再 canonical、fastpostform action，
        // 最后才回退扫 thread-\d+。
        RE_JS_TID.find(html)?.groupValues?.get(1)?.let { return it }
        RE_TID.find(document.selectFirst("link[rel=canonical]")?.attr("href") ?: "")?.let {
            return it.groupValues[1]
        }
        Regex("[?&]tid=(\\d+)")
            .find(document.selectFirst("form#fastpostform")?.attr("action") ?: "")
            ?.groupValues?.get(1)?.let { return it }
        return RE_TID.find(html)?.groupValues?.get(1) ?: ""
    }

    /**
     * 帖子详情总页数。
     *
     * 站点分页链接有两种形态：正序是 `thread-{tid}-{page}-1.html`，
     * 倒序（带 ordertype 等参数）是 `...&page={page}`（页码在查询串里）；
     * 只认前一种会让倒序页恒得出 1 页，从而只加载首屏。
     *
     * 只统计指向本帖 [tid] 的分页链接：正文里引用的其它帖子地址
     * （`thread-其它tid-178-1.html`、`...tid=其它&page=178`）不是本页的分页元素，
     * 采信它们会把总页数抬成随便一篇被引帖的页码，续拉据此一直去敲不存在的页。
     */
    private fun parseTotalPages(document: Document, tid: String): Int {
        var max = 1
        for (a in document.select("a[href*=thread-], a[href*=page]")) {
            val href = a.attr("href")
            val page = Regex("thread-\\d+-(\\d+)-1\\.html").find(href)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("[?&]page=(\\d+)").find(href)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("page-(\\d+)\\.html").find(href)?.groupValues?.get(1)?.toIntOrNull()
                ?: continue
            // 只排除「明确指向其它帖子」的分页链接：正文引用的其它帖地址会带别的
            // tid；不带 tid 的链接（部分分页形态会省略）视为本页分页，不能误排
            if (page > max && (tid.isBlank() || linkTid(href)?.let { it == tid } != false)) max = page
        }
        return max
    }

    /** 分页链接指向的帖子 tid；链接不带 tid 时返回 null。 */
    private fun linkTid(href: String): String? =
        Regex("thread-(\\d+)-").find(href)?.groupValues?.get(1)
            ?: Regex("[?&]tid=(\\d+)").find(href)?.groupValues?.get(1)

    private fun hasNextPage(document: Document): Boolean =
        document.selectFirst("a.nxt, a[href*=page=] i.icon-next") != null
}
