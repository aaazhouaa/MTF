package com.aazhou.mtforum.data.parser

import com.aazhou.mtforum.data.model.Forum
import com.aazhou.mtforum.data.model.FriendRequestItem
import com.aazhou.mtforum.data.model.FriendRequestPage
import com.aazhou.mtforum.data.model.ForumGroup
import com.aazhou.mtforum.data.model.ForumPage
import com.aazhou.mtforum.data.model.AtUser
import com.aazhou.mtforum.data.model.HomeData
import com.aazhou.mtforum.data.model.LikeResult
import com.aazhou.mtforum.data.model.LikeUser
import com.aazhou.mtforum.data.model.LoginResult
import com.aazhou.mtforum.data.model.Notice
import com.aazhou.mtforum.data.model.NoticePage
import com.aazhou.mtforum.data.model.PmPage
import com.aazhou.mtforum.data.model.PrivateMessage
import com.aazhou.mtforum.data.model.ChatMessage
import com.aazhou.mtforum.data.model.ChatConversation
import com.aazhou.mtforum.data.model.Post
import com.aazhou.mtforum.data.model.PostQuote
import com.aazhou.mtforum.data.model.ReplyResult
import com.aazhou.mtforum.data.model.CheckInStatus
import com.aazhou.mtforum.data.model.CheckInResult
import com.aazhou.mtforum.data.model.SearchResult
import com.aazhou.mtforum.data.model.Thread
import com.aazhou.mtforum.data.model.ThreadPage
import com.aazhou.mtforum.data.model.UserProfile
import com.aazhou.mtforum.data.model.NewThreadPageData
import com.aazhou.mtforum.data.model.NewThreadResult
import com.aazhou.mtforum.data.model.EditPostPageData
import com.aazhou.mtforum.data.model.EditAttachment
import com.aazhou.mtforum.data.model.EditPostResult
import com.aazhou.mtforum.data.model.ThreadType
import com.aazhou.mtforum.data.model.FavoriteItem
import com.aazhou.mtforum.data.model.FavoritePage
import com.aazhou.mtforum.data.model.FollowUserItem
import com.aazhou.mtforum.data.model.FollowUserPage
import com.aazhou.mtforum.data.model.FriendUserItem
import com.aazhou.mtforum.data.model.FriendUserPage
import com.aazhou.mtforum.data.model.MyThreadPage
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

    /** URL 参数形态的 formhash：&formhash=xxx。 */
    private val RE_FORMHASH_PARAM = Regex("formhash=([0-9A-Za-z]{8,})")
    // 贴图上传凭据：comiis 模板把 uid/hash 写进帖子页内联的上传配置里
    private val RE_UPLOAD_UID = Regex("uploadformdata:\\{uid:\"(\\d+)\"")
    private val RE_UPLOAD_HASH = Regex("uploadformdata:\\{uid:\"\\d+\",\\s*hash:\"([0-9a-fA-F]+)\"")
    private val RE_UPLOAD_PAIR = Regex("uploadformdata:\\{uid:\"(\\d+)\",\\s*hash:\"([0-9a-fA-F]+)\"")
    private val RE_QUOTE_PID = Regex("pid=(\\d+)")
    private val RE_TAG = Regex("<[^>]+>")
    private val RE_BLOCK_END = Regex(
        "</(?:p|div|li|ul|ol|blockquote|quote|h[1-6]|tr|table|pre|hr)>",
        RegexOption.IGNORE_CASE,
    )
    private val RE_WHITESPACE = Regex("\\s+")
    /** 站点笑脸码形如 [#呵呵]：渲染成 img 前会以文本形式留在正文里。 */
    private val RE_SMILEY_CODE = Regex("\\[#[^\\]\\s]*]")
    private val RE_FLOOR_NUMBER = Regex("^(\\d+)")

    /** 列表卡片配图张数上限：卡片最多铺 3 张，剩下的用右上角「N图」角标概括。 */
    private const val MAX_CARD_IMAGES = 9

    /** 引用快照的截断长度与省略后缀，与站点截断被引正文的入参一致。 */
    private const val QUOTE_SNIPPET_LIMIT = 100
    private const val QUOTE_SNIPPET_DOT = " ..."

    /** 站点快照最多保留的行数：messagecutstr 之后还过了一道 array_slice(explode, 0, 3)。 */
    private const val QUOTE_SNIPPET_MAX_LINES = 3

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

    /**
     * 引用块内的行边界：<br>，或块级元素（div/p）的开闭标签。
     *
     * 站点回复路径用 <br> 分隔 meta 与正文；编辑提交后烘回来的是 PC 版结构，两者各占
     * 一个 div、没有 <br>。两种都要能切开。渲染层兜底取被引正文时也用这一份
     * （见 ui/common/HtmlContent.kt 的 quoteBodyText），两处语义必须一致。
     */
    internal val RE_QUOTE_LINE_BREAK = Regex(
        """<br\s*/?>|</?(?:div|p|li|tr|h[1-6]|blockquote|table|ul|ol)\b[^>]*>""",
        RegexOption.IGNORE_CASE,
    )


    /** 「本帖最后由 X 于 2026-10-5 12:39 编辑」里的时间，用于「时间 编辑」小字。 */
    private val RE_EDIT_TIME = Regex("\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}\\s+\\d{1,2}:\\d{2}(?::\\d{2})?")

    /** 编辑成功标识：站点提示语与 inajax 回执（函数定义形参不是字符串，匹配不上）。 */
    private val RE_EDIT_SUCCESS = Regex("succeedhandle_[A-Za-z0-9_]*\\s*\\(\\s*['\"]|编辑成功|更新成功|已经更新|post_edit_succeed")

    /**
     * errorhandle_xxx('站点提示原文')：站点回执里的提示。
     *
     * 帖子页自带的快速回复回调也叫这名（function errorhandle_fastposts(message, param)），
     * 但那是函数定义、形参不是字符串，匹配不上，不会把模板函数名当成提示念给用户。
     */
    private val RE_EDIT_TIP = Regex("errorhandle_[A-Za-z0-9_]*\\s*\\(\\s*['\"]([^'\"]{1,80})['\"]")

    /**
     * 编辑表单被原样退回的标记：表单 action 带 action=edit，且页面上有提交按钮名 editsubmit。
     *
     * 两个标记必须同时成立：帖子页里自己楼层的「编辑」链接也带 action=edit，而站点提交成功
     * 时是 301 跳回帖子页、回执就是那一页，只认 action=edit 会把成功判成「表单还在」；
     * editsubmit 只出现在表单的 action 与提交按钮上，帖子页没有。
     */
    private val RE_EDIT_FORM_ACTION = Regex("action=edit")
    private val RE_EDIT_SUBMIT = Regex("editsubmit")

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

    /**
     * 登录成功的提示词。
     *
     * Discuz 的 showmessage「提示信息」页登录成功与失败共用，不能只凭「有没有登录表单」
     * 判定成败。这里采用正向判据：只有正文明确是登录成功才算成功，其余提示页一律归失败，
     * 避免登录被拒时落成 success，上层只能笼统报「登录未生效」。
     */
    private val LOGIN_SUCCESS_HINT = Regex("登录成功|欢迎您回来|欢迎回来|现在将转入|正在转入")

    /** 提示页里提示句的失败关键词，用于在不假定页面结构时定位站点原话。 */
    private val TIP_FAILURE_KEYWORD = Regex("密码|验证码|失败|错误|次数|不存在|锁定|禁用|不正确|无效|稍后|请填写|请输入|安全提问")

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

    /**
     * 门户帖子流（comiis_app_portal 插件）：响应是 XML，帖子列表整段包在 CDATA 里。
     * 卡片骨架与导读列表一致，统计行与版块名的模板差异由下面两个分支分别兼容。
     */
    fun parsePortalFeed(xml: String): List<Thread> {
        val html = RE_CDATA.find(xml)?.groupValues?.get(1) ?: return emptyList()
        return parseForumPage(html, 1).threads
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
            // 有标题帖：标题在 mmlist_li_box 的 h2 里；无标题帖（朋友圈式纯图/纯文帖）站点
            // 不渲染 h2 标题块，标题与正文都落在 list_body 的链接里，需回退到它，否则整条帖被跳过
            val titleA = li.selectFirst("div.mmlist_li_box h2 a[href*=thread-]")
            val threadA = titleA ?: li.selectFirst("div.list_body a[href*=thread-]") ?: continue
            val tid = RE_TID.find(threadA.attr("href"))?.groupValues?.get(1) ?: continue
            val authorA = li.selectFirst("a.top_user")
            val (replyCount, viewCount, likeCount) = readCardStats(li)
            threads.add(
                Thread(
                    tid = tid,
                    subject = threadA.text().stripInvisibleChars(),
                    authorName = authorA?.text()?.trim(),
                    authorUid = RE_UID.find(authorA?.attr("href") ?: "")?.groupValues?.get(1),
                    avatarUrl = li.selectFirst("img.top_tximg")?.attr("src"),
                    level = li.selectFirst("span.top_lev")?.text()?.trim(),
                    timeText = li.selectFirst("div.forumlist_li_time span")?.text()?.trim(),
                    replyCount = replyCount,
                    viewCount = viewCount,
                    // 无标题帖没有独立摘要：标题已取 list_body 正文，摘要置空避免重复显示
                    excerpt = if (titleA != null) {
                        li.selectFirst("div.list_body a")?.text()?.stripInvisibleChars()
                    } else null,
                    forumName = li.selectFirst("div.comiis_xznalist_bk a[href*=forum-]")?.let { a ->
                        a.ownText().trim().ifEmpty { a.text().trim() }
                    }?.ifEmpty { null }
                        // 门户片段没有 xznalist_bk：版块名挂在时间行的链接上，文案形如「来自 编程开发」
                        // （前缀还有个 &nbsp;，Kotlin 的 trim 去不掉 U+00A0，故按分隔词切而不比前缀）
                        ?: li.selectFirst("div.forumlist_li_time a[href*=forum-]")?.text()
                            ?.substringAfter("来自")?.trim()?.takeIf { it.isNotEmpty() },
                    imageUrls = collectThreadImages(li),
                    likeCount = likeCount,
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
     * 卡片底部统计，依次为 评论 / 阅读 / 赞。
     *
     * guide 列表把三个数包在 `span.comiis_tm` 里，顺序是 赞/评论/阅读；门户片段是
     * 「291阅读」这样的裸文本且顺序反过来，只能按标签文字取。
     */
    private fun readCardStats(li: Element): Triple<String?, String?, String?> {
        val box = li.selectFirst("div[class*=znalist_bottom]") ?: return Triple(null, null, null)
        val numbers = box.select("li span.comiis_tm")
        if (numbers.isNotEmpty()) {
            return Triple(
                box.selectFirst("li a[href*=thread-] span.comiis_tm")?.text()?.trim(),
                numbers.lastOrNull()?.text()?.trim(),
                numbers.firstOrNull()?.text()?.trim(),
            )
        }
        val items = box.select("li")
        fun statText(label: String): String? =
            items.firstOrNull { it.text().contains(label) }
                ?.text()?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() }
        return Triple(statText("评论"), statText("阅读"), statText("赞"))
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
            val label = li.selectFirst("span.f_d.y")?.text()?.stripInvisibleChars()
            val explicit = floorNumberOf(label)
            val floor = when {
                post.isFirstPost -> 0
                // 置顶楼层没有楼层号，站点只给「置顶」标记，别按锚点推一个错号出来
                post.sticky -> null
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
                floorLabel = floorLabelOf(label, floor),
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
        for (el in contentEl?.select("div.comiis_quote, div.quote").orEmpty()) {
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
            // 置顶后站点把楼层号换成「置顶」标记，位置就在楼层号那个 span 里
            sticky = li.selectFirst("span.f_d.y span.f_g")?.text()?.contains("置顶") == true,
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
        // 站点有两套引用排版：回复路径把 meta 与正文用 <br> 分开，编辑路径（PC 版
        // quote 块烘回来）两者各占一个块级元素、没有 <br>。只按 <br> 切会把整块当成
        // 一行，meta 与正文粘在一起、被引正文取空，引用卡于是整块不渲染。
        // 故按块级边界一并切分，<br> 仍算边界
        val lines = quote.html()
            .split(RE_QUOTE_LINE_BREAK)
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
     * 楼层显示按网页端来：站点第 1 页给的是「沙发」这类称谓，原样显示；
     * 「置顶 来自2#」与 «N#» 都归一到楼层号，免得列表里混进一串前缀。
     */
    private fun floorLabelOf(label: String?, floor: Int?): String? {
        val text = label.orEmpty()
        if (text.isNotEmpty() && FLOOR_NAMES.containsKey(text)) return text
        return floor?.let { "$it#" }
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

    /**
     * 任意形态的 formhash。
     *
     * 收藏页、签到页、好友请求页没有表单输入框，令牌只以 URL 参数形态出现在页面里
     * （退出链接、签到按钮等的 &formhash=xxx），[parseFormhash] 取不到，故兜底扫全文。
     */
    fun parseFormhashAny(html: String): String? {
        if (html.isBlank()) return null
        Jsoup.parse(html).selectFirst("input[name=formhash]")?.attr("value")
            ?.takeIf { it.isNotBlank() }?.let { return it }
        return parseFormhash(html)
            ?: RE_FORMHASH_PARAM.find(html)?.groupValues?.get(1)
    }

    /** 登录页的 loginhash。 */
    fun extractLoginHash(html: String): String? =
        RE_LOGINHASH.find(html)?.groupValues?.get(1)

    /**
     * 回复某楼层时提交的 noticetrimstr。
     *
     * 站点把它原样拼在回复正文最前面（noticetrimstr + "\n\n" + message），是引用
     * 回复的唯一依据；只传正文摘录不会生成引用块，回复就只是一条普通评论。
     *
     * 排版用站点 PC 版引用块：meta 行套 [url=...pid] 链接。服务端只对整块做一次
     * dhtmlspecialchars，这里不能再转义，否则引用块里会留下 `&amp;` 这类双转义字样。
     *
     * 带上 pid 链接是为了让渲染出来的引用块含被引楼层 pid：客户端提交后自己也要解析
     * 这个页面，[parseQuote] 靠它把楼中楼父子关系钉死，短正文（如「嗯嗯」）才不会
     * 退化成按昵称猜父楼层。
     */
    fun replyQuoteNotice(
        author: String,
        time: String?,
        contentHtml: String,
        quotePid: String,
        tid: String,
    ): String {
        val meta = "$author 发表于 ${time.orEmpty()}".trimEnd()
        val url = "forum.php?mod=redirect&goto=findpost&pid=$quotePid&ptid=$tid"
        return "[quote][size=2][url=$url][color=#999999]$meta[/color][/url][/size]\n" +
            "${replyQuoteSnippet(contentHtml)}[/quote]"
    }

    /**
     * 引用快照正文：取楼层纯文本，保留块间换行，按站点同款长度与行数截断。
     *
     * 站点截断的是数据库里的 BBCode 原文，客户端手上只有渲染后的 HTML，只能提取
     * 纯文本；长度对齐站点的 messagecutstr(message, 100)，行数对齐其后的
     * array_slice(explode, 0, 3)——快照超过三行时站点也只留前三行。
     */
    fun replyQuoteSnippet(contentHtml: String): String {
        val lines = RE_BLOCK_END.replace(contentHtml, "\n")
            .split("\n")
            .map { line ->
                val text = Jsoup.parse(line.replace(RE_TAG, " ")).text()
                RE_SMILEY_CODE.replace(text, "")
                    .replace('\u00A0', ' ')
                    .replace(RE_WHITESPACE, " ")
                    .trim()
            }
            .filter { it.isNotEmpty() }
        val snippet = lines.joinToString("\n")
        val cut = if (snippet.length <= QUOTE_SNIPPET_LIMIT) {
            snippet
        } else {
            snippet.take(QUOTE_SNIPPET_LIMIT).trimEnd() + QUOTE_SNIPPET_DOT
        }
        return cut.split("\n").take(QUOTE_SNIPPET_MAX_LINES).joinToString("\n")
    }

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
        val captcha = document.selectFirst("img[src*=seccode], img[src*=secqaa]")?.attr("src")
        val stillLoginForm = document.selectFirst("form#loginform") != null
        if (stillLoginForm) {
            val message = document.select("div.comiis_tip, div.alert_error, div.alert_info")
                .firstOrNull { it.text().isNotBlank() }?.text()?.trim()
            return LoginResult(success = false, message = message, needsCaptcha = captcha != null, captchaUrl = captcha)
        }
        // showmessage「提示信息」页：登录成功与失败共用此页，没有登录表单不能当成功。
        // 实测该页 title 固定为「提示信息 - 站点名」，以此识别；命中成功词算成功，其余
        // （含验证码、密码错、次数过多）归失败并把站点原文回传，否则失败会被误判成功，
        // 上层只能笼统报「登录未生效」。非提示页（如直接回首页）维持成功，交资料页复核。
        // 提示页正文很短，容器选择器不命中时会围绕失败关键词取窗口，直接把站点原话回传。
        if (extractTitle(html)?.contains("提示信息") != true) return LoginResult(success = true)
        val message = extractTipText(html)
        val succeeded = message != null && LOGIN_SUCCESS_HINT.containsMatchIn(message)
        return LoginResult(
            success = succeeded,
            message = message ?: "登录失败，请重试",
            needsCaptcha = captcha != null,
            captchaUrl = captcha,
        )
    }

    /** 页面标题，用于识别站点 showmessage「提示信息」结果页。 */
    fun extractTitle(html: String): String? =
        Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * 站点 showmessage「提示信息」页的正文。
     *
     * 移动模板把提示放在 div.comiis_password_top 的 <p> 里（实测：成功页为「欢迎您回来…
     * 现在将转入…」，失败页为「密码错误次数过多，请 15 分钟后重新登录」）。其余容器
     * （#messagetext / div.comiis_tip）是桌面/其它模板的写法，兼作兼容。都不命中时退到
     * 页面可见文本，围绕首个提示关键词取窗口，避免把整页导航文字倒给用户。
     */
    fun extractTipText(html: String): String? {
        val document = doc(html)
        document.selectFirst("div.comiis_password_top p")?.text()?.trim()
            ?.ifEmpty { null }?.let { return it }
        document.selectFirst("#messagetext p, #messagetext")?.text()?.trim()
            ?.ifEmpty { null }?.let { return it }
        document.selectFirst("div.comiis_tip p, div.comiis_tip")?.text()?.trim()
            ?.ifEmpty { null }?.let { return it }
        val full = messagePageText(html) ?: return null
        val hit = TIP_FAILURE_KEYWORD.find(full) ?: return full.take(120)
        val start = (hit.range.first - 30).coerceAtLeast(0)
        val end = (hit.range.last + 60).coerceAtMost(full.length)
        return full.substring(start, end).trim()
    }

    /** 提示页里想要的跳转地址：showmessage 用 JS 延迟跳转，OkHttp 不会跟随。 */
    fun extractMessageRedirect(html: String): String? =
        Regex("location\\.href\\s*=\\s*['\"]([^'\"]+)['\"]").find(html)?.groupValues?.get(1)

    /**
     * 提示页的可见正文（调试用）。
     *
     * 站点改过 showmessage 模板，容器选择器不一定命中；这里退到 body 全文，剥掉
     * script/style 后取可见文本，保证日志里总能拿到站点原话。
     */
    fun messagePageText(html: String): String? {
        val document = doc(html)
        document.select("script, style, noscript").remove()
        return document.body()?.text()?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** 登录表单里各 input 的 name，用于核对客户端提交是否漏字段。 */
    fun loginFormFields(html: String): List<String> =
        doc(html).selectFirst("form#loginform")?.select("input[name]")
            ?.map { it.attr("name") }?.filter { it.isNotEmpty() } ?: emptyList()

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

    /** 从任意页面提取当前用户；未登录时 uid 为 "0"。 */
    /** 个人空间页的 uid：优先指定 fallbackUid，其次 JS 变量 uid 或 discuz_uid。 */
    fun parseUserProfile(html: String, fallbackUid: String? = null): UserProfile {
        val document = doc(html)
        val uid = fallbackUid?.takeIf { it.isNotBlank() }
            ?: Regex("discuz_uid\\s*=\\s*'(\\d+)'").find(html)?.groupValues?.get(1)
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

        val followEl = document.selectFirst("#followmod")
        val isFollowing = when {
            followEl == null -> null
            followEl.hasClass("kmok") || followEl.attr("href").contains("op=del") -> true
            followEl.attr("href").contains("op=add") -> false
            else -> null
        }

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
            isFollowing = isFollowing,
            formhash = parseFormhash(html),
        )
    }

    /** 从 Discuz AJAX 响应中提取提示消息（如「成功收听」「好友请求已发送」「黑名单添加成功」等）。 */
    fun parseAjaxMessage(response: String): String? {
        // 整页与 WAF 挑战页不是回执：写操作失败时站点会跳转到别的页面，从整页里抓到的
        // 「消息」只是页面中任意一段文字（常见的是当前用户名），不能当操作结果
        val head = response.trimStart()
        if (head.startsWith("<html", ignoreCase = true) || head.startsWith("<!DOCTYPE", ignoreCase = true)) {
            return null
        }
        val pMatch = Regex("""<p>([\s\S]*?)(?:<script|</p>)""").find(response)?.groupValues?.get(1)?.trim()
        if (!pMatch.isNullOrBlank()) {
            val text = doc(pMatch).text().trim()
            if (text.isNotBlank()) return text
        }
        val dtMatch = Regex("""id=["']messagetext["'][^>]*>([\s\S]*?)</dt>""").find(response)?.groupValues?.get(1)?.trim()
        if (!dtMatch.isNullOrBlank()) {
            val text = doc(dtMatch).text().trim()
            if (text.isNotBlank()) return text
        }
        val alertMatch = Regex("""(?:popup\.open|alert)\(['"]([^'"]+)['"]""").find(response)?.groupValues?.get(1)?.trim()
        if (!alertMatch.isNullOrBlank() && !alertMatch.contains("<img") && !alertMatch.contains("http")) {
            return alertMatch
        }
        return null
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

    /** 分页器里指向某一页的链接：do=notice&view=X&page=N。 */
    private val RE_NOTICE_PAGE = Regex("""[?&]page=(\d+)""")

    /** 帖子地址里的页码：`viewthread&tid=X&page=N` 或 `thread-X-N-1.html`。 */
    private val RE_THREAD_PAGE = Regex("""[?&]page=(\d+)|thread-\d+-(\d+)-""")


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
        return NoticePage(
            loggedOut = loggedOut,
            notices = notices,
            totalPages = parseNoticeTotalPages(document),
        )
    }

    /**
     * 站点分页器（div.comiis_page）给出的总页数。
     *
     * 页码在 select#dumppage 的 option 上；取不到就退到「下一页」链接带的 page
     * 参数。两者都没有说明只有一页。
     */
    private fun parseNoticeTotalPages(document: Document): Int {
        val pager = document.selectFirst("div.comiis_page") ?: return 1
        pager.select("select option")
            .mapNotNull { it.attr("value").trim().toIntOrNull() }
            .maxOrNull()
            ?.let { return it.coerceAtLeast(1) }
        return pager.select("a[href]")
            .mapNotNull { RE_NOTICE_PAGE.find(it.attr("href"))?.groupValues?.get(1)?.toIntOrNull() }
            .maxOrNull()
            ?.coerceAtLeast(1)
            ?: 1
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
        // 站点用 &nbsp; 撑空白，Jsoup 转成不换行空格；正则的 \s 匹配不到它，
        // 会丢掉相对时间（「2 小时前」整段落空）或日词后的钟点（「昨天 20:28」只剩「昨天」）。
        val time = RE_NOTICE_TIME.findAll(
            (li.selectFirst("h2")?.text() ?: raw).replace('\u00A0', ' '),
        ).lastOrNull()?.value
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
        // 站点给每条提醒一个稳定 id（屏蔽链接的 a_note_NNNN）；列表 key 用它，
        // 新消息插到前面时后面的行不会因为下标平移而整批重建
        val noteId = li.selectFirst("a[id^=a_note_]")?.id()?.removePrefix("a_note_")
        val id = noteId ?: tid ?: "notice-$index"
        // 互动类提醒的头像链接：a.notice_img 包着头像，href 带 uid=；系统类提醒没有它，两项自然为 null
        val avatarLink = li.selectFirst("a.notice_img")
        val avatarUrl = avatarLink?.selectFirst("img")?.attr("src")?.trim()?.takeIf { it.isNotBlank() }
        val uid = avatarLink?.attr("href")?.let { RE_UID.find(it)?.groupValues?.get(1) }
        return Notice(
            id = id,
            text = text,
            timeText = time,
            tid = tid,
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

    /**
     * 「@朋友」的好友名单：站点编辑器用的同一个接口，返回 CDATA 里逗号分隔的用户名。
     *
     * 没有好友时 CDATA 为空，返回空列表（界面据此提示可以手输用户名）。
     */
    fun parseAtUsers(body: String): List<String> =
        RE_CDATA.find(body)?.groupValues?.get(1)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    /**
     * 用户列表行：好友、关注、全局用户搜索三个页面在 comiis 模板下同结构。
     *
     * 昵称链接认 `p.tit` 里指向 `do=profile` 的那个：好友行的操作区还有「发消息」
     * （mod=space&do=pm...&touid=）和「备注」（...&uid=）两个链接，按 uid 或
     * mod=space 匹配都会先撞上它们，把昵称取成「发消息」。
     */
    fun parseAtUserList(html: String): List<AtUser> =
        doc(html).select("div.comiis_userlist01 > li").mapNotNull { li ->
            val link = li.select("p.tit a[href*=\"do=profile\"]").firstOrNull()
                ?: return@mapNotNull null
            val uid = RE_UID.find(link.attr("href"))?.groupValues?.get(1) ?: return@mapNotNull null
            val name = link.text().trim()
            if (name.isEmpty()) return@mapNotNull null
            val avatar = li.select("a.list01_limg img").firstOrNull()
                ?.absUrl("src")
                ?.takeIf { it.isNotBlank() }
            AtUser(
                uid = uid,
                name = name,
                avatarUrl = avatar,
                subtitle = li.select("p.txt").firstOrNull()?.text()?.trim().orEmpty(),
            )
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

    /**
     * 解析私人消息 / 公共消息列表（home.php?mod=space&do=pm&filter=privatepm|announcepm&inajax=1）。
     *
     * 站点把列表放在 Tab 的 inajax 回执里：正文是 XML 外壳，列表 HTML 包在 CDATA 中，
     * 与两个 Tab 的导航混在同一段，故先剥 CDATA 再只认 comiis_pmlist 里的行——Tab 自身
     * 也是带 do=pm 链接的 li，按「li:has(a[href*=do=pm])」兜底会把「私人消息」
     * 「公共消息」两条导航当成会话。未登录时回执只有一段登录跳转脚本，按 loggedOut 还原。
     */
    fun parsePmMessages(response: String): PmPage {
        val payload = RE_CDATA.find(response)?.groupValues?.get(1) ?: response
        if (payload.contains("mod=logging") || payload.contains("action=login")) {
            return PmPage(loggedOut = true)
        }
        val document = doc(payload)
        val items = document.select("div.comiis_pmlist li, div.pm_list li")
        return PmPage(
            messages = items.mapNotNull { li ->
                val link = li.selectFirst("a[href*=\"do=pm\"]") ?: li.selectFirst("a")
                val href = link?.attr("href").orEmpty()
                val touid = Regex("""[?&]touid=(\d+)""").find(href)?.groupValues?.get(1)
                    ?: Regex("""[?&]uid=(\d+)""").find(href)?.groupValues?.get(1)
                val plid = Regex("""[?&]plid=(\d+)""").find(href)?.groupValues?.get(1)

                val h2 = li.selectFirst("h2")
                val timeText = h2?.selectFirst("span")?.text()?.trim()
                    ?: li.selectFirst("span.date, span.time")?.text()?.trim()

                // 用户名取 h2 剔除时间 span 后的文本
                val username = h2?.clone()?.apply { select("span").remove() }?.text()?.trim()
                    ?.ifBlank { null }
                    ?: link?.selectFirst(".name, .username")?.text()?.trim()
                    ?: "系统消息"

                val summary = li.selectFirst("p")?.text()?.trim().orEmpty()
                val avatarUrl = li.selectFirst("img")?.absUrl("src")?.takeIf { it.isNotBlank() }
                val unread = li.selectFirst(".kmnum, .kmnums, .new") != null

                PrivateMessage(
                    plid = plid,
                    touid = touid,
                    username = username,
                    avatarUrl = avatarUrl,
                    summary = summary,
                    timeText = timeText,
                    unread = unread,
                )
            },
        )
    }

    /**
     * 解析与特定用户的聊天对话记录（home.php?mod=space&do=pm&subop=view&touid=X&mobile=2）。
     */
    fun parseChatConversation(html: String, defaultTouid: String): ChatConversation {
        val document = doc(html)
        val formhash = document.selectFirst("input[name=formhash]")?.attr("value")
            ?.takeIf { it.isNotBlank() }
            ?: parseFormhash(html)

        // 站点把昵称与在线状态一起写在顶部标题行里：`<h2 class="flex">昵称 <font>(在线)</font></h2>`。
        // 整段 text 会把括号也带进昵称，故先摘掉再判状态；标题行缺失时两项都为 null
        val headName = document.selectFirst(
            "#comiis_head h2, .comiis_head h2, .header .name, .category .name, header span:not(.none)",
        )
        val headText = headName?.text().orEmpty()
        val targetName = headName?.ownText()?.trim()?.ifBlank { null }
            ?: headText.trim().removeSuffix("(在线)").removeSuffix("(离线)").trim().ifEmpty { null }
        val targetOnline = headText.takeIf { it.contains("(在线)") || it.contains("(离线)") }
            ?.contains("(在线)")
        // 真实 comiis 结构是 `div.comiis_self_msg`（自己）/ `div.comiis_friend_msg`（对方），
        // 旧版（测试用例沿用）是 `div.self_msg` / `div.friend_msg`；日期分隔 `comiis_msg_date`
        // 与外层容器 `comiis_msgbox` 不含这两类 class，自然被排除。类选择器按 token 匹配，
        // 能正确命中 `class="comiis_self_msg cl"` 这种带浮动工具类的节点。
        val nodes = document.select(
            "div.comiis_self_msg, div.comiis_friend_msg, div.self_msg, div.friend_msg",
        )
            .ifEmpty {
                document.select(".msgbox li, .comiis_pmlist li")
            }
            .ifEmpty {
                document.select("div.dialog_green, div.dialog_white")
            }

        val messages = nodes.mapIndexedNotNull { index, node ->
            // 只信 class 名里的 self 与头像的浮动方向（.y/.right = 自己），不能拿气泡颜色判：
            // 真实站点自己=dialog_green，旧版测试用例自己=dialog_white，两者语义相反。
            val isSelf = node.classNames().any { it.contains("self", ignoreCase = true) } ||
                node.selectFirst(".msg_avt.y, .msg_avt.right, .avat.y, .avat.right") != null

            // 正文只取消息内容节点，不带头像/时间；表情是 <img>（无文本），text() 自然略过
            val text = node.selectFirst(".msg_mes")?.text()?.trim()
                ?: node.selectFirst(".dialog_t, .dialog_c")?.text()?.trim()
                ?: node.selectFirst("p.msg, .msg")?.text()?.trim()
                ?: node.text().trim()
            if (text.isBlank()) return@mapIndexedNotNull null

            val time = node.selectFirst(".msg_time")?.text()?.trim()
                ?: node.selectFirst(".date, .time, span.date")?.text()?.trim()

            // 头像必须是消息框外的头像图（msg_avt / .avat 里的 <img>），不能落到正文里的表情
            // <img>。表情图地址带 static/image/smiley，最后一级兜底据此排除。
            val avatarUrl = node.selectFirst("img.msg_avt")?.absUrl("src")?.takeIf { it.isNotBlank() }
                ?: node.selectFirst(".avat img, a > img")?.absUrl("src")?.takeIf { it.isNotBlank() }
                ?: node.select("img").firstOrNull { img ->
                    val src = img.absUrl("src")
                    src.isNotBlank() &&
                        !src.contains("smiley", ignoreCase = true) &&
                        !src.contains("static/image", ignoreCase = true)
                }?.absUrl("src")

            ChatMessage(
                id = node.id().ifBlank { "${index}_${time.orEmpty()}" },
                text = text,
                time = time,
                isSelf = isSelf,
                avatarUrl = avatarUrl,
            )
        }

        return ChatConversation(
            touid = defaultTouid,
            targetName = targetName,
            messages = messages,
            formhash = formhash,
            targetOnline = targetOnline,
        )
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

    /** 解析移动端签到页面状态（是否已签到、formhash）。 */
    fun parseCheckInStatus(html: String): CheckInStatus {
        val formhash = parseFormhash(html)
        val canSign = html.contains("id=\"signresult\"")
        val hasSigned = html.contains(">已签到<") ||
            html.contains("class=\"comiis_btn bg_b f_c\">已签到") ||
            (!canSign && html.contains("已签到"))
        return CheckInStatus(hasSigned = hasSigned, formhash = formhash)
    }

    /**
     * 解析签到接口返回内容。
     *
     * 接口返回 XML 且结果包在 CDATA 中，成功通常为「已签到」或「今日已签」。
     */
    fun parseCheckInResponse(response: String): CheckInResult {
        val cdata = RE_CDATA.find(response)?.groupValues?.get(1)?.trim() ?: response.trim()
        if (cdata.contains("已签到") || cdata.contains("今日已签")) {
            return CheckInResult(success = true, message = if (cdata.contains("今日已签")) "今日已签到" else "签到成功")
        }
        val cleanMsg = cdata
            .replace(Regex("(?s)<script.*?</script>"), "")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return CheckInResult(
            success = false,
            message = cleanMsg.ifBlank { "签到失败" },
        )
    }

    private fun hasNextPage(document: Document): Boolean =
        document.selectFirst("a.nxt, a[href*=page=] i.icon-next, .pg a.nxt") != null

    /** 解析「我的帖子」（主题 / 回复）。 */
    fun parseMyThreads(html: String, page: Int): MyThreadPage {
        val document = doc(html)
        val threads = mutableListOf<Thread>()
        for (li in document.select("li.forumlist_li")) {
            val titleA = li.selectFirst("div.mmlist_li_box h2 a[href*=thread-]")
                ?: li.selectFirst("a[href*=thread-]")
                ?: continue
            val tid = RE_TID.find(titleA.attr("href"))?.groupValues?.get(1) ?: continue
            val authorA = li.selectFirst("a.top_user")
            val (replyCount, viewCount, likeCount) = readCardStats(li)
            threads.add(
                Thread(
                    tid = tid,
                    subject = titleA.text().stripInvisibleChars(),
                    authorName = authorA?.text()?.trim(),
                    authorUid = RE_UID.find(authorA?.attr("href") ?: "")?.groupValues?.get(1),
                    avatarUrl = li.selectFirst("img.top_tximg")?.attr("src"),
                    level = li.selectFirst("span.top_lev")?.text()?.trim(),
                    timeText = li.selectFirst("div.forumlist_li_time span")?.text()?.trim(),
                    replyCount = replyCount,
                    viewCount = viewCount,
                    excerpt = li.selectFirst("div.list_body a")?.text()?.stripInvisibleChars(),
                    forumName = li.selectFirst("div.comiis_xznalist_bk a[href*=forum-]")?.let { a ->
                        a.ownText().trim().ifEmpty { a.text().trim() }
                    }?.ifEmpty { null }
                        ?: li.selectFirst("div.forumlist_li_time a[href*=forum-]")?.text()
                            ?.substringAfter("来自")?.trim()?.takeIf { it.isNotEmpty() },
                    imageUrls = collectThreadImages(li),
                    likeCount = likeCount,
                )
            )
        }
        return MyThreadPage(
            threads = threads.distinctBy { it.tid },
            page = page,
            hasNext = hasNextPage(document),
        )
    }

    /** 解析「我的收藏」（全部 / 帖子 / 版块）。 */
    fun parseFavorites(html: String, page: Int): FavoritePage {
        val document = doc(html)
        val items = mutableListOf<FavoriteItem>()
        val listElements = document.select("ul.comiis_favoritelist li, .comiis_p12 li, ul#favorite_ul li, div.comiis_body li, li[id^=fav_], li.mysclist_li")
        for (el in listElements) {
            val deleteA = el.selectFirst("a[href*=op=delete]") ?: el.selectFirst("a[href*=favid=]")
            val favid = deleteA?.let { Regex("""favid=(\d+)""").find(it.attr("href"))?.groupValues?.get(1) }
                ?: Regex("""fav_(\d+)""").find(el.attr("id"))?.groupValues?.get(1)
                ?: ""
            val titleA = el.selectFirst("a[href*=thread-], a[href*=mod=viewthread], a[href*=forum-], a[href*=mod=forumdisplay]")
                ?: continue
            val href = titleA.attr("href")
            val isForum = href.contains("forum-") || href.contains("mod=forumdisplay")
            val id = (if (isForum) RE_FID.find(href)?.groupValues?.get(1) else RE_TID.find(href)?.groupValues?.get(1))
                ?: Regex("""[?&](?:tid|id)=(\d+)""").find(href)?.groupValues?.get(1)
                ?: ""
            val title = titleA.text().stripInvisibleChars().ifEmpty { continue }
            val dateText = el.selectFirst("span.f_d, span.xg1, div.forumlist_li_time span")?.text()?.trim()
            val desc = el.selectFirst("div.list_body, p.txt")?.text()?.stripInvisibleChars()
            items.add(
                FavoriteItem(
                    favid = favid,
                    id = id,
                    title = title,
                    description = desc,
                    dateText = dateText,
                    type = if (isForum) "forum" else "thread",
                )
            )
        }
        return FavoritePage(
            items = items.distinctBy { if (it.favid.isNotEmpty()) it.favid else it.id },
            page = page,
            hasNext = hasNextPage(document),
        )
    }

    /**
     * 取一行里的用户名链接。
     *
     * 站点头像链接（a.list01_limg）排在用户名（p.tit）之前，href 同样带 uid 与
     * do=profile，把两者放进同一组选择器会按文档顺序先选到头像，其 text 为空，
     * 整行就会被当作无效数据跳过。
     */
    private fun profileLink(li: Element): Element? =
        li.selectFirst("p.tit a[href*=do=profile]")
            ?: li.selectFirst("a.top_user")
            ?: li.select("a[href*=do=profile]").firstOrNull { it.text().isNotBlank() }

    /** 解析「我的好友」。 */
    fun parseFriends(html: String, page: Int): FriendUserPage {
        val document = doc(html)
        val items = mutableListOf<FriendUserItem>()
        val listElements = document.select("div.comiis_userlist01 > li, ul.comiis_friend_list > li, li[id^=comiis_friendbox_], li[id^=friend_], .comiis_p12 li")
        for (li in listElements) {
            val link = profileLink(li) ?: continue
            val href = link.attr("href")
            val uid = RE_UID.find(href)?.groupValues?.get(1)
                ?: Regex("""comiis_friendbox_(\d+)""").find(li.attr("id"))?.groupValues?.get(1)
                ?: continue
            val name = link.text().trim().ifEmpty { continue }
            val avatarUrl = li.selectFirst("img")?.absUrl("src")?.takeIf { it.isNotBlank() }
            // 描述只取等级/时间那几行小字：黑名单页的 p.txt 里是「解除黑名单」操作链接，
            // 整段文本照抄会把按钮文字显示成描述
            val desc = li.selectFirst("p.txt")?.select("font")
                ?.joinToString(" ") { it.text().trim() }?.trim()?.takeIf { it.isNotEmpty() }
                ?: li.selectFirst("span.f_d")?.text()?.trim()?.takeIf { it.isNotEmpty() }
            items.add(FriendUserItem(uid = uid, username = name, avatarUrl = avatarUrl, description = desc))
        }
        return FriendUserPage(
            users = items.distinctBy { it.uid },
            page = page,
            hasNext = hasNextPage(document),
        )
    }

    /** 解析「好友请求」列表（spacecp&ac=friend&op=request）。 */
    fun parseFriendRequests(html: String, page: Int): FriendRequestPage {
        val document = doc(html)
        val rows = document.select(
            "div.comiis_userlist01 > li, li.b_t, ul.comiis_friend_list > li, .comiis_p12 li",
        ).ifEmpty {
            // 容器类名与模板不一致时，退回「行内有用户资料链接」这一形态本身
            document.select("li:has(a[href*=\"do=profile\"])")
        }
        val items = rows.mapNotNull { li ->
            val link = li.selectFirst("p.tit a[href*=profile]")
                ?: li.selectFirst("a[href*=do=profile]")
                ?: return@mapNotNull null
            val uid = RE_UID.find(link.attr("href"))?.groupValues?.get(1) ?: return@mapNotNull null
            val name = link.text().trim().ifBlank { return@mapNotNull null }
            val avatar = li.selectFirst("a.list01_limg img, img[src*=avatar]")?.absUrl("src")
                ?.takeIf { it.isNotBlank() }
            val desc = li.selectFirst("p.txt")?.text()?.trim()?.takeIf { it.isNotEmpty() }
            FriendRequestItem(uid = uid, username = name, avatarUrl = avatar, description = desc)
        }.distinctBy { it.uid }
        return FriendRequestPage(
            requests = items,
            page = page,
            hasNext = hasNextPage(document),
            formhash = parseFormhashAny(html),
        )
    }

    /** 解析「关注」与「粉丝」列表。 */
    fun parseFollowUsers(html: String, page: Int, defaultIsFollowing: Boolean): FollowUserPage {
        val document = doc(html)
        val items = mutableListOf<FollowUserItem>()
        val elements = document.select("div.comiis_userlist01 > li, li[id^=comiis_friendbox_]")
        for (li in elements) {
            val link = li.selectFirst("p.tit a[href*=do=profile]") ?: li.selectFirst("a[href*=do=profile]") ?: continue
            val href = link.attr("href")
            val uid = RE_UID.find(href)?.groupValues?.get(1)
                ?: Regex("""comiis_friendbox_(\d+)""").find(li.attr("id"))?.groupValues?.get(1)
                ?: continue
            val name = link.text().trim().ifEmpty { continue }
            val avatarUrl = li.selectFirst("a.list01_limg img, img")?.absUrl("src")?.takeIf { it.isNotBlank() }
            val txt = li.selectFirst("p.txt")?.text().orEmpty()
            val fansCount = Regex("""(\d+)\s*粉丝""").find(txt)?.groupValues?.get(1)
                ?: li.selectFirst("code[id^=followernum_]")?.text()?.trim()
            val followCount = Regex("""(\d+)\s*关注""").find(txt)?.groupValues?.get(1)
            val hasCancelA = li.selectFirst("a.user_gz[href*=op=del]") != null
            val isFollowing = if (hasCancelA) true else defaultIsFollowing
            // 备注：行内「备注」链接（op=bkname）旁的 font#followbkame_UID 存备注文本，
            // 与「备注」链接同 id 前缀但不同 id（fbkname_UID vs followbkame_UID）
            val remark = li.selectFirst("font[id^=followbkame_]")
                ?.text()?.trim()?.takeIf { it.isNotEmpty() }
            items.add(
                FollowUserItem(
                    uid = uid,
                    username = name,
                    avatarUrl = avatarUrl,
                    fansCount = fansCount,
                    followCount = followCount,
                    isFollowing = isFollowing,
                    remark = remark,
                )
            )
        }
        return FollowUserPage(
            users = items.distinctBy { it.uid },
            page = page,
            hasNext = hasNextPage(document),
        )
    }

    private val RE_POSTTIME_INPUT = Regex("""name="posttime"\s+(?:id="posttime"\s+)?value="(\d+)"""")
    private val RE_POSTTIME_JS = Regex("""posttime\s*=\s*['"](\d+)['"]""")
    private val RE_TID_MATCH = Regex("""thread-(\d+)|tid=(\d+)""")

    /**
     * 从页面内联上传配置里取贴图凭据（uid + hash）。
     *
     * 供私聊借页面取凭据用：站点只在帖子页/发帖页给这份配置，hash 与会话绑定、
     * 与取自哪个页面无关。两个字段缺任何一个都按没有处理。
     */
    fun parseUploadCredentials(html: String): Pair<String, String>? {
        val match = RE_UPLOAD_PAIR.find(html) ?: return null
        return match.groupValues[1] to match.groupValues[2]
    }

    /**
     * 解析发帖页初始数据（formhash、posttime、图片上传凭据 uid/hash、主题分类 types、无权限提示）。
     */
    fun parseNewThreadInit(html: String): NewThreadPageData {
        val formhash = parseFormhash(html).orEmpty()
        val posttime = RE_POSTTIME_INPUT.find(html)?.groupValues?.get(1)
            ?: RE_POSTTIME_JS.find(html)?.groupValues?.get(1)
        val uploadUid = RE_UPLOAD_UID.find(html)?.groupValues?.get(1)
        val uploadHash = RE_UPLOAD_HASH.find(html)?.groupValues?.get(1)

        val document = doc(html)
        val types = threadTypes(document)
        val permissionDenied = if (html.contains("抱歉，您没有权限在该版块发帖") || html.contains("您目前没有权限发帖")) {
            "抱歉，您没有权限在该版块发帖"
        } else if (html.contains("抱歉，您尚未登录，没有权限发帖")) {
            "请先登录后再发帖"
        } else {
            null
        }

        return NewThreadPageData(
            formhash = formhash,
            posttime = posttime,
            uploadUid = uploadUid,
            uploadHash = uploadHash,
            types = types,
            permissionDenied = permissionDenied,
        )
    }

    /** 发帖页与编辑页共用的主题分类下拉（值 0 与「请选择」是占位项，不是分类）。 */
    private fun threadTypes(document: Document): List<ThreadType> {
        val select = document.selectFirst("select[name=typeid], select#typeid") ?: return emptyList()
        return select.select("option").mapNotNull { opt ->
            val value = opt.attr("value").trim()
            val text = opt.text().trim()
            if (value.isNotEmpty() && value != "0" && text.isNotEmpty() && text != "请选择") {
                ThreadType(id = value, name = text)
            } else {
                null
            }
        }
    }

    /**
     * 编辑页初始数据：待编辑的 BBCode 原文 + 表单令牌 + 上传凭据与主题分类。
     *
     * 站点编辑表单的 textarea 里就是数据库中的 BBCode 原文，取 [Element.wholeText] 而非
     * text()：后者会把连续空白与换行折叠成单空格，多段正文会黏成一行。
     */
    fun parseEditPostInit(html: String): EditPostPageData {
        val document = doc(html)
        val subject = document.selectFirst("input#subject, input[name=subject]")?.attr("value")
        val permissionDenied = when {
            document.selectFirst("form#loginform") != null || html.contains("action=login") -> "请先登录后再编辑"
            html.contains("没有权限") -> "没有编辑这条内容的权限"
            // 站点对超过可编辑时限的楼层不再返回表单，只给这句提示页；页面里没有 textarea，
            // 若不识别这里，message 会解析成空字符串、编辑面板照常打开却一片空白
            html.contains("不能再进行编辑") -> "该内容已超过可编辑时限，不能再进行编辑"
            else -> null
        }
        return EditPostPageData(
            formhash = parseFormhash(html).orEmpty(),
            subject = subject,
            message = editMessageText(document),
            attachments = editAttachments(document),
            uploadUid = RE_UPLOAD_UID.find(html)?.groupValues?.get(1),
            uploadHash = RE_UPLOAD_HASH.find(html)?.groupValues?.get(1),
            types = threadTypes(document),
            permissionDenied = permissionDenied,
        )
    }

    /**
     * 编辑表单里已挂上的图片附件：站点把它们列在 `ul#imglist`，每条 li 带 `span.del[aid]` 与
     * `img#aimg_<aid>`，缩略图地址即正文里 `[attachimg]<aid>[/attachimg]` 指向的那张图。
     * li.up_btn 是上传按钮，不是附件。
     */
    private fun editAttachments(document: Document): List<EditAttachment> {
        return document.select("ul.comiis_post_imglist > li").mapNotNull { li ->
            if (li.hasClass("up_btn")) return@mapNotNull null
            val img = li.selectFirst("img[id^=aimg_]") ?: return@mapNotNull null
            val aid = li.selectFirst("span.del[aid]")?.attr("aid")?.trim()
                ?: img.id().removePrefix("aimg_").ifEmpty { null }
            val url = img.attr("src").trim()
            if (aid.isNullOrEmpty() || url.isEmpty()) return@mapNotNull null
            EditAttachment(aid = aid, url = url, fileName = img.attr("title").trim())
        }
    }

    /** 编辑表单里的原文：首选 message 文本域，退化到页面第一个文本域或同名隐藏域。 */
    private fun editMessageText(document: Document): String {
        val textarea = document.selectFirst("textarea[name=message], textarea#message")
            ?: document.selectFirst("textarea")
        val raw = textarea?.wholeText().orEmpty()
        if (raw.isNotBlank()) return raw
        return document.selectFirst("input[name=message]")?.attr("value").orEmpty()
    }

    /**
     * 编辑提交结果判定。
     *
     * 编辑提交不走 inajax：站点处理完 301 回帖子页，被拒时把编辑表单原样退回。判据只认这
     * 两点，不认 succeedhandle_/errorhandle_ 字样本身——帖子页自带的快速回复回调就叫这俩
     * 名字（function errorhandle_fastposts(message, param)），拿它们当回执会把成功的提交
     * 判成失败，再把整页文本当提示念给用户。
     */
    fun parseEditPostResult(body: String, tid: String): EditPostResult {
        val stillForm =
            RE_EDIT_FORM_ACTION.containsMatchIn(body) && RE_EDIT_SUBMIT.containsMatchIn(body)
        val landedOnThread = tid.isNotBlank() && (
            body.contains("thread-$tid") ||
                body.contains("viewthread&tid=$tid") ||
                body.contains("viewthread&amp;tid=$tid")
            )
        if (!stillForm && (landedOnThread || RE_EDIT_SUCCESS.containsMatchIn(body))) {
            return EditPostResult(success = true)
        }
        val tip = RE_EDIT_TIP.find(body)?.groupValues?.getOrNull(1)?.trim()
        return EditPostResult(success = false, message = tip ?: "编辑未生效，请稍后重试")
    }

    /**
     * 发新帖结果判定（inajax 回执与整页跳转判定）。
     */
    fun parseNewThreadResult(body: String): NewThreadResult {
        if (body.contains("errorhandle_")) {
            return NewThreadResult(success = false, message = extractReplyError(body))
        }
        if (body.contains("succeedhandle_")) {
            val tid = RE_TID_MATCH.find(body)?.let { m ->
                m.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() }
                    ?: m.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }
            }
            return NewThreadResult(success = true, tid = tid)
        }
        val tid = RE_TID_MATCH.find(body)?.let { m ->
            m.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() }
                ?: m.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }
        }
        if (tid != null) {
            return NewThreadResult(success = true, tid = tid)
        }
        if (body.contains("非常感谢") || body.contains("主题已经发布") || body.contains("帖子发布成功")) {
            return NewThreadResult(success = true)
        }
        val errorMsg = extractReplyError(body)
            ?: RE_TIP_MESSAGE.find(body)?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), "")?.trim()
            ?: if (body.contains("两次发表间隔")) "抱歉，您两次发表间隔过短，请稍后再试" else null
        return NewThreadResult(success = false, message = errorMsg ?: "发帖未成功，请检查内容或重试")
    }
}
