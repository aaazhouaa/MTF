package com.aazhou.mtforum.data.repo

import com.aazhou.mtforum.data.model.ForumGroup
import com.aazhou.mtforum.data.model.AtUser
import com.aazhou.mtforum.data.model.CheckInResult
import com.aazhou.mtforum.data.model.CheckInStatus
import com.aazhou.mtforum.data.model.EditPostPageData
import com.aazhou.mtforum.data.model.EditPostResult
import com.aazhou.mtforum.data.model.ForumPage
import com.aazhou.mtforum.data.model.HomeData
import com.aazhou.mtforum.data.model.LikeResult
import com.aazhou.mtforum.data.model.LikeUser
import com.aazhou.mtforum.data.model.LoginResult
import com.aazhou.mtforum.data.model.Notice
import com.aazhou.mtforum.data.model.NoticePage
import com.aazhou.mtforum.data.model.NoticeTab
import com.aazhou.mtforum.data.model.PmPage
import com.aazhou.mtforum.data.model.PrivateMessage
import com.aazhou.mtforum.data.model.ChatConversation
import com.aazhou.mtforum.data.model.ReplyResult
import com.aazhou.mtforum.data.model.SearchResult
import com.aazhou.mtforum.data.model.ThreadPage
import com.aazhou.mtforum.data.model.UserProfile
import com.aazhou.mtforum.data.model.FavoriteItem
import com.aazhou.mtforum.data.model.FavoritePage
import com.aazhou.mtforum.data.model.FollowUserItem
import com.aazhou.mtforum.data.model.FollowUserPage
import com.aazhou.mtforum.data.model.FriendUserItem
import com.aazhou.mtforum.data.model.FriendUserPage
import com.aazhou.mtforum.data.model.MyThreadPage
import com.aazhou.mtforum.data.model.NewThreadPageData
import com.aazhou.mtforum.data.model.NewThreadResult
import com.aazhou.mtforum.data.network.HttpClient
import com.aazhou.mtforum.data.parser.ForumParser
import com.aazhou.mtforum.data.parser.SmileyPack
import com.aazhou.mtforum.data.parser.SmileyParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File

/** 回复未拿到 inajax XML 回执时（整页 HTML）兜底判失败、提示重新登录的提示词。 */
private val REPLY_ERROR_PATTERN = Regex("抱歉|非法操作|请先登录|需要先登录|没有权限|审核")

/** 点赞/收藏失败提示词。 */
private val LIKE_ERROR_PATTERN = Regex(
    "没有点赞权限|不能点赞|今日评价机会已用完|关闭的主题|请先登录|formhash错误|非法操作|不能收藏|submit_invalid|errorhandle_",
)

/** 举报失败：返回体仍是举报表单或跳登录页。 */
private val REPORT_ERROR_PATTERN = Regex("举报理由|action=login|请先登录")

/** 删除未生效：返回的仍是编辑表单（含 editsubmit）或跳登录页。
 * 不能拿 name="message" 判定——删除成功后重定向到的帖子页也带快速回复框。
 * 两个标记都要有才算「还在表单里」：删除成功会被重定向回帖子页，页面里自己其它楼层
 * 的「编辑」链接（action=edit）单独出现是正常回执，只认一个标记会把删成功的回执误判
 * 成失败（编辑链路的判据同理，见 ForumParser.parseEditPostResult）。 */
private val DELETE_FORM_ACTION = Regex("action=edit")
private val DELETE_FORM_SUBMIT = Regex("editsubmit")

/** 屏蔽对话页的表单 id（ignoreform_XXX）：提交时作 handlekey，取不到也能屏蔽成功。 */
private val RE_IGNORE_FORM_ID = Regex("""id=["'](ignoreform_[0-9A-Za-z]+)["']""")

/**
 * 版块帖子流的排序档位，与站点 forumdisplay 的 filter 参数一一对应；[label] 即顶栏 tab 文案。
 */
enum class ForumSort(val label: String) {
    LATEST("最新"),
    HOT("热门"),
    DIGEST("精华"),
    POLL("投票"),
}

/** 打赏成功标识。 */
private val REWARD_SUCCESS_PATTERN =
    Regex("succeedhandle_rate|rate_success|评分成功|打赏成功|评价成功|您已成功评分")

/** 打赏失败/受限（当日已评、积分不足、无权限等）。 */
private val REWARD_FAIL_PATTERN = Regex(
    "24小时|24 小时|已经评分|已评价|已评分|重复评分|评分失败|积分不足|余额不足|formhash错误|非法操作|没有权限|请先登录",
)

/** 主题管理（置顶回复等）成功标识。 */
private val TOPICADMIN_SUCCESS = Regex("管理操作成功")

/** 站点笑脸表地址：CDN 上的 JS 缓存文件，支持 HTTP 条件请求。 */
private val SMILEY_URL = "${HttpClient.BASE_URL}data/cache/common_smilies_var.js"

/** 表情表的保鲜期：超过这个时间才带 etag 回站确认一次，期内零网络请求。 */
private const val SMILEY_REVALIDATE_INTERVAL_MS = 12 * 60 * 60 * 1000L

/** 正文里引用已上传附件的标记：`[attachimg]<aid>[/attachimg]`。 */
private val RE_ATTACHIMG_AID = Regex("""\[attachimg\](\d+)\[/attachimg\]""", RegexOption.IGNORE_CASE)

/**
 * 编辑提交前的引用标签：`[quote]`、`[/quote]`、`[size=N]`、`[/size]`。
 *
 * 站点的编辑提交会剥掉小写的这几个标签（`[/quote]` 还被换成一个换行），引用块只剩
 * 内部文本、被引快照混进正文，引用卡整个消失；而 Discuz 的渲染端大小写不敏感，
 * 大写形式照样渲染成引用卡（pid 链接也在）。已用站点实测确认两种写法提交后的落库
 * 形态，故编辑提交前统一转成大写。新回复走 noticetrimstr，站点不做这个剥离，不受影响。
 */
private val RE_EDIT_QUOTE_TAG = Regex("""\[/?(?:quote|size)\b[^\]]*]""", RegexOption.IGNORE_CASE)

/** 把正文里的引用相关标签转成大写，避开站点编辑提交对它们的剥离（见 [RE_EDIT_QUOTE_TAG]）。 */
internal fun protectQuoteTagsOnEdit(message: String): String =
    RE_EDIT_QUOTE_TAG.replace(message) { it.value.uppercase() }

/** guide 模块里由门户插件出列表的视图：站点把 view=hot 做成门户首页，帖子列表走 AJAX。 */
private const val GUIDE_VIEW_PORTAL = "hot"

/** 论坛数据入口：封装请求 + 解析，UI 层只与它交互。 */
/** 站点附件 CDN：上传回执只给相对路径，拼图地址用（与站点前端同一前缀）。 */
private const val ATTACHMENT_CDN = "https://cdn.binmt.cc/"

class ForumRepository(
    private val http: HttpClient,
    private val smileyStore: SmileyStore,
) {

    /** 会话内缓存的表情包：站点不常变，避免每次打开表情面板都拉一次。 */
    private var smileyCache: List<SmileyPack>? = null

    /** 提交正文里引用的附件 id：发新帖与回复都靠它把附件挂到本楼。 */
    private fun attachAids(message: String): List<String> =
        RE_ATTACHIMG_AID.findAll(message).map { it.groupValues[1] }.distinct().toList()

    /** 正文里还引用着的附件 id：编辑时据此算出哪些附件被删掉了。 */
    fun attachAidsIn(message: String): List<String> = attachAids(message)

    /**
     * 解除附件与楼层的关联（站点编辑器里点图片上的「删除」走的就是这个接口）。
     *
     * 注意不是编辑提交表单的参数：站点在提交前单独发一次
     * `mod=ajax&action=deleteattach&aids[]=<aid>&tid=<tid>&pid=<pid>`，
     * 编辑表单本身只带 `attachnew[aid][description]`，从不带删除语义的字段。
     * `up=1` 表示该附件已落库，此时才需要 tid/pid 定位楼层。
     *
     * @return 站点回执 CDATA 里的内容（无效 aid 回 "0"）。
     */
    suspend fun deleteAttach(
        tid: String,
        pid: String,
        aid: String,
        formhash: String,
    ): String = withContext(Dispatchers.IO) {
        val url = http.url(
            "forum.php?mod=ajax&action=deleteattach&inajax=yes&formhash=$formhash" +
                "&aids[]=$aid&tid=$tid&pid=$pid",
        )
        val body = http.get(url)
        val cd = Regex("""<!\[CDATA\[([\s\S]*?)\]\]>""").find(body)
        cd?.groupValues?.get(1)?.trim().orEmpty()
    }


    /** 发帖页的 formhash（新版帖接口必需）。 */
    suspend fun postFormhash(fid: String): String? = withContext(Dispatchers.IO) {
        ForumParser.parseFormhash(http.get(http.url("forum.php?mod=post&action=newthread&fid=$fid&mobile=2")))
    }

    /** 发帖页初始数据（包含 formhash、posttime、图片上传凭据 uid/hash、主题分类 types、权限状态）。 */
    suspend fun fetchNewThreadInit(fid: String): NewThreadPageData = withContext(Dispatchers.IO) {
        val html = http.get(http.url("forum.php?mod=post&action=newthread&fid=$fid&mobile=2"))
        ForumParser.parseNewThreadInit(html)
    }

    fun isLoggedIn(): Boolean = http.isLoggedIn()

    /**
     * 表情包列表（回复面板的表情 tab 用）。
     *
     * 站点把笑脸表放在 CDN 上的一个 JS 缓存文件里，内容不常变但站点会加表情：整份存本地，
     * 应用重启不必重拉；超过保鲜期才带上次的 etag 回站确认一次，没变只记时间，变了才换新。
     */
    suspend fun smileyPacks(): List<SmileyPack> = withContext(Dispatchers.IO) {
        // 空列表不认账：拉取失败时存进内存会让整个进程期都不再重试
        smileyCache ?: loadSmileyPacks().also { packs -> if (packs.isNotEmpty()) smileyCache = packs }
    }

    private suspend fun loadSmileyPacks(): List<SmileyPack> {
        val cached = smileyStore.packs() ?: run {
            // 本地没有（首次安装或刚清理过）：整份拉回来存上；拉不动就空着，面板提示加载失败
            val body = runCatching { http.get(SMILEY_URL) }.getOrNull() ?: return emptyList()
            return SmileyParser.parse(body).also { smileyStore.savePacks(it, null) }
        }
        if (System.currentTimeMillis() - cached.checkedAt < SMILEY_REVALIDATE_INTERVAL_MS) return cached.packs

        val fetched = try {
            http.getIfChanged(SMILEY_URL, cached.etag)
        } catch (e: Exception) {
            // 断网、超时或被 WAF 拦下：本地那份照常用，也不推进确认时间，下次开门接着试
            return cached.packs
        }
        // 304：站点内容没变，只推进确认时间
        if (fetched == null) {
            smileyStore.markChecked()
            return cached.packs
        }
        return SmileyParser.parse(fetched.body).also { smileyStore.savePacks(it, fetched.etag) }
    }

    /** 当前会话 Cookie 头，供登录后持久化。 */
    fun sessionCookieHeader(): String = http.cookieHeader()

    suspend fun forumGroups(): List<ForumGroup> = withContext(Dispatchers.IO) {
        ForumParser.parseForumList(http.get(http.url("forum.php?forumlist=1&mobile=2")))
    }

    /** 首页：热版推荐 + 热帖排行。 */
    suspend fun home(): HomeData = withContext(Dispatchers.IO) {
        ForumParser.parseHome(http.get(http.url("forum.php?mod=guide&view=hot&mobile=2")))
    }

    /**
     * 导读列表：guide 模块的各个 view —— 最新发表 / 热门 / 精华 / 回复 / 抢沙发。
     *
     * 翻页参数只能是 page。站点模板生成的「下一页」链接把 mod 写坏成
     * `mod=index=1`（少了 guide），而 index 本身不参与分页：index=1 与 index=2
     * 返回的是同一页。故地址由客户端自己拼，不取页面上的翻页链接。
     */
    suspend fun guideFeed(view: String, page: Int): ForumPage = withContext(Dispatchers.IO) {
        if (view == GUIDE_VIEW_PORTAL) {
            // 门户流的列表是插件经 AJAX 单独出的：guide&view=hot 本身是门户首页，HTML 里没有列表。
            // 插件固定 10 页，超出后原样重复最后一页，故这里只能给出「本页有没有内容」，
            // 真正的到底判定由调用方按去重结果做。
            val threads = ForumParser.parsePortalFeed(
                http.get(http.url("plugin.php?id=comiis_app_portal&pid=1&page=$page&inajax=1")),
            )
            ForumPage(
                forumName = null,
                announcements = emptyList(),
                threads = threads,
                page = page,
                hasNext = threads.isNotEmpty(),
            )
        } else {
            ForumParser.parseForumPage(
                http.get(http.url("forum.php?mod=guide&view=$view&page=$page&mobile=2")),
                page,
            )
        }
    }

    suspend fun forumPage(fid: String, page: Int, sort: ForumSort = ForumSort.LATEST): ForumPage =
        withContext(Dispatchers.IO) {
            // 最新档走静态地址（其余档位站点只给带 query 的 forumdisplay 地址）；
            // 翻页参数统一是 page，与站点翻页链接的拼法一致
            val path = when (sort) {
                ForumSort.LATEST -> "forum-$fid-$page.html"
                ForumSort.HOT -> "forum.php?mod=forumdisplay&fid=$fid&filter=heat&orderby=heats&page=$page"
                ForumSort.DIGEST -> "forum.php?mod=forumdisplay&fid=$fid&filter=digest&digest=1&page=$page"
                ForumSort.POLL -> "forum.php?mod=forumdisplay&fid=$fid&filter=specialtype&specialtype=poll&page=$page"
            }
            ForumParser.parseForumPage(http.get(http.url(path)), page)
        }

    /** 只看楼主（authorid）与排序（ordertype）都需重新请求，站点无客户端侧筛选。 */
    suspend fun threadPage(
        tid: String,
        page: Int,
        orderType: Int = 0,
        authorId: String? = null,
    ): ThreadPage = withContext(Dispatchers.IO) {
        val order = if (orderType == 1) "&ordertype=1" else ""
        val author = if (!authorId.isNullOrBlank()) "&authorid=$authorId" else ""
        ForumParser.parseThreadPage(
            http.get(http.url("forum.php?mod=viewthread&tid=$tid&page=$page$order$author&mobile=2")),
            page,
        )
    }

    /**
     * 回复帖子：[noticeTrimStr] 非空即引用回复，[noticeAuthor] 为快速回复框隐藏域。
     * 返回站点回执结果。
     *
     * 站点把 noticetrimstr 原样拼在回复正文最前面，因此它必须是完整 [quote] 块（见
     * [com.aazhou.mtforum.data.parser.ForumParser.replyQuoteNotice]）；只传正文摘录
     * 不会生成引用块，回复就只是一条普通评论。repquote/reppost/noticeauthormsg 只
     * 服务于站点渲染回复表单，提交阶段服务端不读，故不进请求体。
     */
    suspend fun reply(
        fid: String,
        tid: String,
        formhash: String,
        message: String,
        noticeAuthor: String? = null,
        noticeTrimStr: String? = null,
    ): ReplyResult = withContext(Dispatchers.IO) {
        // 站点快速回复框由 JS 代发，且仅在带 inajax=1 时返回 XML 回执；否则回整页 HTML，
        // 老的「无错误词即成功」判据会把整页 HTML 误判成成功。replysubmit 是 type=button，
        // 不进序列化请求体，只需出现在 URL 查询串里。引用与否由 noticetrimstr 区分，URL 相同。
        val url = http.url(
            "forum.php?mod=post&action=reply&fid=$fid&tid=$tid&extra=&replysubmit=yes&mobile=2" +
                "&handlekey=fastpost&loc=1&inajax=1",
        )
        val params = mutableMapOf(
            "formhash" to formhash,
            "message" to message,
        )
        if (!noticeAuthor.isNullOrBlank()) params["noticeauthor"] = noticeAuthor
        if (!noticeTrimStr.isNullOrBlank()) params["noticetrimstr"] = noticeTrimStr
        // 站点只把 attachnew 里列出的 aid 挂到本楼并渲染 [attachimg]；漏了它就静默丢弃标签，
        // 表现为「发送成功但只有文字」
        for (aid in attachAids(message)) params["attachnew[$aid][description]"] = ""
        val body = http.post(url, params, referer = "https://bbs.binmt.cc/thread-$tid-1-1.html")
        val result = ForumParser.parseReplyResult(body)
        // 未拿到 XML 回执（无 succeedhandle_/errorhandle_，如会话过期被重定向到登录页）时一律判
        // 失败；命中错误词则提示重新登录，而不是笼统的「可能风控」。
        if (!result.success && !body.contains("errorhandle_") && REPLY_ERROR_PATTERN.containsMatchIn(body)) {
            ReplyResult(success = false, message = "尚未登录或会话已过期，请重新进入帖子")
        } else {
            result
        }
    }

    /**
     * 上传回复里要贴的图，返回附件 id（回复正文用 `[attachimg]<aid>[/attachimg]` 引用）。
     *
     * 与网页端一致：multipart 的 [HttpClient.upload] 带 uid/hash，站点按会话校验。
     * 失败返回 null（体积超限、类型不允许、令牌过期等，提示词由调用方给）。
     */
    suspend fun uploadReplyImage(
        file: File,
        uid: String,
        hash: String,
    ): String? = uploadImage(file, uid, hash)?.aid

    /**
     * 上传结果：[aid] 给 `[attachimg]` 引用，[path] 是站点回的附件相对路径，
     * [url] 由它拼出 CDN 地址，供 `[img]` 直接引用（私聊没有附件，只能走外链图）。
     */
    data class ImageUpload(val aid: String, val path: String) {
        val url: String get() = ATTACHMENT_CDN + "data/attachment/forum/" + path
    }

    /** 上传一张图，拿到 aid 与附件路径；失败返回 null。 */
    suspend fun uploadImage(
        file: File,
        uid: String,
        hash: String,
    ): ImageUpload? = withContext(Dispatchers.IO) {
        val body = http.upload(
            url = "${HttpClient.BASE_URL}misc.php?mod=swfupload&operation=upload&type=image&inajax=yes&infloat=yes&simple=2",
            file = file,
            fieldName = "Filedata",
            extraFields = mapOf("uid" to uid, "hash" to hash),
        )
        // 响应为 DISCUZUPLOAD|1|0|<aid>|<...>|<附件路径>|<文件名>|...，第 3 段为状态码
        val parts = body.split('|')
        if (parts.size >= 6 && parts[0] == "DISCUZUPLOAD" && parts[2] == "0") {
            ImageUpload(aid = parts[3], path = parts[5])
        } else {
            null
        }
    }

    /**
     * 取贴图上传凭据（uid + hash）。
     *
     * 站点只在帖子页与发帖页的内联脚本里给这份凭据，私聊页没有上传表单；而 hash 是
     * 会话级的（同一会话在各页面完全一致），与取自哪个页面无关，故借这两个页面取。
     * 按「自己的帖子 -> 首页热帖 -> 任一版块发帖页」的顺序试，全部落空返回 null。
     */
    suspend fun fetchUploadCredentials(): Pair<String, String>? = withContext(Dispatchers.IO) {
        val pages = mutableListOf<String>()
        runCatching { myThreads() }.getOrNull()?.threads?.firstOrNull()?.tid
            ?.let { pages += "thread-$it-1-1.html" }
        runCatching { home() }.getOrNull()?.hotThreads?.firstOrNull()?.tid
            ?.let { pages += "thread-$it-1-1.html" }
        runCatching { forumGroups() }.getOrNull()?.firstOrNull()?.forums?.firstOrNull()?.fid
            ?.let { pages += "forum.php?mod=post&action=newthread&fid=$it&mobile=2" }
        for (page in pages) {
            val html = runCatching { http.get(http.url(page)) }.getOrNull() ?: continue
            ForumParser.parseUploadCredentials(html)?.let { return@withContext it }
        }
        null
    }

    /**
     * 「@朋友」的可选名单：站点网页端编辑器用的同一个接口。
     *
     * 名单只是候选，提到谁最终由用户在输入框里确认（站点同样允许直接手输用户名）。
     */
    suspend fun fetchAtUsers(): List<String> = withContext(Dispatchers.IO) {
        ForumParser.parseAtUsers(
            http.get("${HttpClient.BASE_URL}misc.php?mod=getatuser&inajax=1"),
        )
    }

    /**
     * 「@朋友」选择页的好友列表（站点「我的好友」）。
     *
     * 站点按 24 人一页，这里最多翻 [MAX_AT_USER_PAGES] 页：再往后的好友本来也只能
     * 靠搜索定位，全量翻页会把首屏拖成十几秒。
     */
    suspend fun fetchFriends(): List<AtUser> = withContext(Dispatchers.IO) {
        atUserPages { page -> "home.php?mod=space&do=friend&page=$page&mobile=2" }
    }

    /** 「@朋友」选择页的关注列表（站点「我的关注」）。 */
    suspend fun fetchFollows(): List<AtUser> = withContext(Dispatchers.IO) {
        atUserPages { page -> "home.php?mod=follow&do=following&page=$page&mobile=2" }
    }

    /**
     * 全局用户搜索：站点搜索页的「用户」标签。
     *
     * 站点按用户名包含匹配，最多返回 100 条且不分页；关键字为空时站点直接返回空列表。
     */
    suspend fun searchUsers(keyword: String): List<AtUser> = withContext(Dispatchers.IO) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        val encoded = java.net.URLEncoder.encode(trimmed, "UTF-8")
        ForumParser.parseAtUserList(
            http.get(http.url("search.php?mod=user&searchsubmit=yes&srchtxt=$encoded&mobile=2")),
        )
    }

    private suspend fun atUserPages(url: (Int) -> String): List<AtUser> {
        val all = mutableListOf<AtUser>()
        var page = 1
        while (page <= MAX_AT_USER_PAGES) {
            val batch = ForumParser.parseAtUserList(http.get(http.url(url(page))))
            all += batch
            if (batch.size < AT_USER_PAGE_SIZE) break
            page++
        }
        return all
    }

    /**
     * 点赞 / 取消点赞。
     *
     * 点赞走 Discuz 的 recommend add；取消走 comiis 插件 re_recommend
     * （已实测：返回 ok 且帖子点赞状态回退）。
     * 注意：取消只撤销帖子上的赞，不退还当日已用的评价次数（站点如此记账）。
     */
    suspend fun setLike(tid: String, formhash: String, like: Boolean): LikeResult = withContext(Dispatchers.IO) {
        val url = if (like) {
            http.url(
                "forum.php?mod=misc&action=recommend&handlekey=recommend_add" +
                    "&do=add&tid=$tid&hash=$formhash&inajax=1&mobile=2",
            )
        } else {
            http.url("plugin.php?id=comiis_app&comiis=re_recommend&tid=$tid&inajax=1")
        }
        ForumParser.parseLikeResult(http.get(url), like)
    }

    /**
     * 收藏 / 取消收藏。
     *
     * 添加直接走 spacecp ac=favorite；取消需要该主题在收藏夹里的 favid，
     * 先拉收藏列表页解析出 favid 再删除——站点没有按 tid 直接删除的入口。
     */
    suspend fun setFavorite(tid: String, formhash: String, favorite: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (favorite) {
            val url = http.url(
                "home.php?mod=spacecp&ac=favorite&type=thread&id=$tid" +
                    "&formhash=$formhash&handlekey=comiis&inajax=1&mobile=2",
            )
            val body = http.get(url)
            body.isNotBlank() && !LIKE_ERROR_PATTERN.containsMatchIn(body)
        } else {
            val favid = favoriteFavid(tid) ?: return@withContext false
            deleteFavorite(favid)
        }
    }

    /**
     * 从收藏列表页找出该主题的 favid。
     *
     * 站点「我的收藏」导航指向 view=me 页，删除链接（op=delete&favid=）就在收藏行内。
     */
    private suspend fun favoriteFavid(tid: String): String? {
        val html = http.get(http.url("home.php?mod=space&do=favorite&view=me&type=all&mobile=2"))
        return ForumParser.parseFavoriteFavid(html, tid)
    }

    /**
     * 按 favid 取消收藏。
     *
     * 站点 submitcheck 只读 URL 查询串里的 formhash（请求体里的不看），收藏页也没有
     * form 输入框，令牌只以 URL 参数形态出现在页面里，故统一把 formhash 拼进 URL。
     */
    suspend fun deleteFavorite(favid: String): Boolean = withContext(Dispatchers.IO) {
        if (favid.isBlank()) return@withContext false
        val favoritePage = http.url("home.php?mod=space&do=favorite&view=me&type=all&mobile=2")
        val pageHtml = runCatching { http.get(favoritePage) }.getOrDefault("")
        val formhash = ForumParser.parseFormhashAny(pageHtml) ?: return@withContext false
        val url = http.url(
            "home.php?mod=spacecp&ac=favorite&op=delete&favid=$favid&type=all" +
                "&formhash=$formhash&deletesubmit=true&handlekey=comiis&inajax=1&mobile=2",
        )
        val body = http.post(
            url,
            mapOf(
                "formhash" to formhash,
                "deletesubmit" to "true",
                "handlekey" to "comiis",
            ),
            referer = favoritePage,
        )
        body.isNotBlank() && !LIKE_ERROR_PATTERN.containsMatchIn(body)
    }

    /** 关注/取消关注：站点用同一入口的 op 参数区分。 */
    suspend fun followTopic(uid: String, formhash: String, follow: Boolean): String = withContext(Dispatchers.IO) {
        val url = http.url(
            "home.php?mod=spacecp&ac=follow&op=${if (follow) "add" else "del"}" +
                "&fuid=$uid&hash=$formhash&mobile=2",
        )
        http.get(url)
    }

    suspend fun search(keyword: String, page: Int): SearchResult = withContext(Dispatchers.IO) {
        val encoded = java.net.URLEncoder.encode(keyword, "UTF-8")
        ForumParser.parseSearchResult(
            http.get(http.url("search.php?mod=forum&searchsubmit=yes&srchtxt=$encoded&page=$page&mobile=2")),
            page,
        )
    }

    /**
     * 当前登录用户的个人中心（统计/积分/金币/在线时长等）。
     *
     * 必须带 do=profile：不带的地址在 comiis 模板下是「广播」动态页，整页没有
     * 头像、统计与积分，只有昵称/uid 这类每个页面都有的 JS 变量。
     */
    suspend fun me(): UserProfile = withContext(Dispatchers.IO) {
        ForumParser.parseUserProfile(http.get(http.url("home.php?mod=space&do=profile&mobile=2")))
    }

    /**
     * 消息提醒：把 [tabs] 里各子类的第一页合并成一条列表。
     *
     * 站点把提醒内容按 view+type 分页，且不带 type 只返回默认子类，故要把子类逐个
     * 列出来各拉一份；私信两类（[NoticeTab.pmFilter] 非空）不是 Notice，跳过。
     * 未登录时每一份都会被换成登录表单，此时返回 loggedOut，不再多打请求。
     */
    suspend fun notices(tabs: List<NoticeTab>): NoticePage = withContext(Dispatchers.IO) {
        val requests = tabs.mapNotNull { tab ->
            val view = tab.view ?: return@mapNotNull null
            view to tab.type
        }.distinct()
        val pages = coroutineScope {
            requests.map { (view, type) ->
                async {
                    val query = if (type == null) {
                        "home.php?mod=space&do=notice&view=$view&mobile=2"
                    } else {
                        "home.php?mod=space&do=notice&view=$view&type=$type&mobile=2"
                    }
                    runCatching { ForumParser.parseNotices(http.get(http.url(query))) }.getOrNull()
                }
            }.awaitAll()
        }
        val merged = mutableListOf<Notice>()
        var loggedOut = false
        for (page in pages) {
            if (page == null) continue
            if (page.loggedOut) {
                loggedOut = true
                continue
            }
            merged += page.notices
        }
        // id 用站点给的提醒 id：新消息插到前面时下标会平移，位置 id 会让后面的行
        // 整批重建；同一 id 只留一条，避免列表 key 重复
        NoticePage(notices = merged.distinctBy { it.id }, loggedOut = loggedOut && merged.isEmpty())
    }

    /**
     * 屏蔽某位用户的某类提醒（网页端每条提醒行内的「屏蔽」）。
     *
     * 站点是两段式：先 GET 确认对话页拿表单里的 formhash，再 POST 同一个 action。
     * 提交形状必须照网页端来：comiis 把这类对话表单按 inajax 发（action 后面拼
     * `&inajax=1`，body 还要带提交按钮 feedignoresubmit）。少了 inajax 站点只会把
     * 表单再渲染一遍，屏蔽根本不生效；handlekey 只决定回执里 JS 回调的名字，可有可无。
     */
    suspend fun ignoreNotice(authorid: String, type: String, formhash: String): Boolean =
        withContext(Dispatchers.IO) {
            val formUrl = http.url(
                "home.php?mod=spacecp&ac=common&op=ignore&authorid=$authorid&type=$type&mobile=2",
            )
            val page = runCatching { http.get(formUrl) }.getOrDefault("")
            val fh = ForumParser.parseFormhash(page) ?: formhash
            if (fh.isBlank()) return@withContext false
            val handlekey = RE_IGNORE_FORM_ID.find(page)?.groupValues?.get(1)
            val handle = if (handlekey.isNullOrBlank()) "" else "&handlekey=$handlekey"
            val url = http.url(
                "home.php?mod=spacecp&ac=common&op=ignore&type=$type$handle&inajax=1",
            )
            val params = mapOf(
                "ignoresubmit" to "true",
                "formhash" to fh,
                "referer" to formUrl,
                "authorid" to authorid,
                "feedignoresubmit" to "true",
            )
            val body = http.post(url, params, referer = formUrl)
            isIgnoreSucceeded(body)
        }

    /**
     * 某楼层现在在第几页（正序口径）；取不到返回 null。
     *
     * 站点没有「跳到某楼」的接口，只有 redirect&goto=findpost 的中转：跟着 302 只要
     * Location 头，不把整页下回来（一页几十 KB，还有节流）。目标在第 1 页时站点
     * 不给 page 参数，此时按 1 算。
     */
    suspend fun postPageOf(tid: String, pid: String): Int? = withContext(Dispatchers.IO) {
        val location = runCatching {
            http.redirectLocation(
                http.url("forum.php?mod=redirect&goto=findpost&pid=$pid&ptid=$tid"),
            )
        }.getOrNull() ?: return@withContext null
        Regex("""[?&]page=(\d+)""").find(location)?.groupValues?.get(1)?.toIntOrNull()
            ?.coerceAtLeast(1)
    }

    /**
     * 私人消息 / 公共消息列表（网页端「我的消息」的两个 Tab）。
     *
     * 站点把列表放在 Tab 的 inajax 回执里：整页 HTML 的列表容器是空的，直接取页
     * 永远拿到 0 条，故按 filter 打这个接口。[loggedOut] 由解析器从登录跳转回执认出。
     */
    suspend fun pmMessages(filter: String): PmPage = withContext(Dispatchers.IO) {
        val body = http.get(http.url("home.php?mod=space&do=pm&filter=$filter&inajax=1"))
        ForumParser.parsePmMessages(body)
    }

    /** 与特定用户的私信会话详情（聊天记录）。 */
    suspend fun chatConversation(touid: String): ChatConversation = withContext(Dispatchers.IO) {
        val html = http.get(http.url("home.php?mod=space&do=pm&subop=view&touid=$touid&mobile=2"))
        ForumParser.parseChatConversation(html, touid)
    }

    /** 指定用户的个人中心（他人主页）。 */
    suspend fun userProfile(uid: String): UserProfile = withContext(Dispatchers.IO) {
        val html = http.get(http.url("home.php?mod=space&uid=$uid&do=profile&mobile=2"))
        ForumParser.parseUserProfile(html, fallbackUid = uid)
    }

    /** 举报本帖（[pid] 为空）或某条回复；理由与说明由弹窗传入。 */
    suspend fun reportPost(
        tid: String,
        fid: String,
        formhash: String,
        pid: String?,
        reason: String,
        message: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val rtype = if (pid.isNullOrBlank()) "thread" else "post"
        val rid = if (rtype == "thread") tid else pid!!
        val formUrl = http.url(
            "misc.php?mod=report&rtype=$rtype&rid=$rid&tid=$tid&fid=$fid&inajax=1&mobile=2",
        )
        val page = runCatching { http.get(formUrl) }.getOrDefault("")
        val fh = ForumParser.parseFormhash(page) ?: formhash
        if (fh.isBlank()) return@withContext false
        val params = mutableMapOf(
            "formhash" to fh,
            "tid" to tid,
            "fid" to fid,
            "rtype" to rtype,
            "rid" to rid,
            "reportsubmit" to "yes",
            "reason" to reason.ifBlank { "其他" },
            "message" to message.ifBlank { "该内容涉嫌违规，请核实处理。" },
        )
        if (rtype == "post" && pid != null) params["pid"] = pid
        val url = http.url(
            "misc.php?mod=report&rtype=$rtype&rid=$rid&tid=$tid&fid=$fid&reportsubmit=yes&inajax=1&mobile=2",
        )
        val body = http.post(url, params)
        body.isNotBlank() && !REPORT_ERROR_PATTERN.containsMatchIn(body)
    }

    /**
     * 编辑页初始数据：[pid] 为首帖时改标题与正文，为评论时只改正文。
     *
     * 与删除同一个入口（站点用 action=edit 的表单承载编辑与删除），故取回的是
     * 表单里的 BBCode 原文与一次性令牌。
     */
    suspend fun fetchEditPostInit(tid: String, pid: String): EditPostPageData = withContext(Dispatchers.IO) {
        val html = http.get(http.url("forum.php?mod=post&action=edit&tid=$tid&pid=$pid&mobile=2"))
        ForumParser.parseEditPostInit(html)
    }

    /**
     * 提交编辑；[subject] 仅首帖编辑时传（站点只在首帖表单里给标题输入框）。
     *
     * 提交参数与删除同源：Discuz 以 editsubmit 判定表单提交，写成别的名字会被当成
     * 又打开了一次编辑页。
     */
    suspend fun editPost(
        tid: String,
        pid: String,
        formhash: String,
        message: String,
        subject: String? = null,
    ): EditPostResult = withContext(Dispatchers.IO) {
        val url = http.url("forum.php?mod=post&action=edit&extra=&tid=$tid&pid=$pid&page=1&editsubmit=yes&mobile=2")
        // 站点编辑提交会剥掉小写的 quote/size 标签，提交前转成大写（渲染端大小写不敏感）
        val text = protectQuoteTagsOnEdit(message)
        val params = mutableMapOf(
            "formhash" to formhash,
            "message" to text,
            "editsubmit" to "yes",
            "tid" to tid,
            "pid" to pid,
        )
        if (!subject.isNullOrBlank()) params["subject"] = subject
        for (aid in attachAids(text)) params["attachnew[$aid][description]"] = ""
        val body = http.post(
            url,
            params,
            referer = "https://bbs.binmt.cc/forum.php?mod=post&action=edit&tid=$tid&pid=$pid&mobile=2",
        )
        ForumParser.parseEditPostResult(body, tid)
    }

    /** 删除自己的回复（pid 为该楼 pid）；返回是否真正删除。 */
    suspend fun deletePost(tid: String, pid: String, formhash: String): Boolean = withContext(Dispatchers.IO) {        val formUrl = http.url("forum.php?mod=post&action=edit&tid=$tid&pid=$pid&mobile=2")
        val page = http.get(formUrl)
        val fh = ForumParser.extractDeleteHash(page, pid)
            ?: ForumParser.parseFormhash(page)
            ?: formhash
        if (fh.isBlank()) return@withContext false
        val params = mapOf(
            "formhash" to fh,
            "delete" to "1",
            "pid" to pid,
            "tid" to tid,
            // Discuz 用 submitcheck('editsubmit') 判定提交，写成 deletesubmit 会被当成打开编辑页
            "editsubmit" to "yes",
        )
        val url = http.url("forum.php?mod=post&action=edit&extra=&tid=$tid&pid=$pid&page=1&delete=1&mobile=2")
        val body = http.post(url, params)
        // 未真正删除时编辑页仍返回完整表单，以此区分「已删除」与「服务端拒删却 200」
        val stillForm = DELETE_FORM_ACTION.containsMatchIn(body) && DELETE_FORM_SUBMIT.containsMatchIn(body)
        body.isNotBlank() && !stillForm
    }

    /**
     * 置顶/解除置顶某条回复（楼主权限）。站点 stickreply 走两步：先回确认表单，
     * 客户端自己确认，故直接打带 modsubmit 的第二步。
     *
     * 置顶后站点把该楼提到首帖之后，楼层号位置换成「置顶」标记，需重拉本页才能上屏。
     */
    suspend fun stickReply(
        tid: String,
        fid: String,
        formhash: String,
        pid: String,
        stick: Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        val params = mapOf(
            "formhash" to formhash,
            "fid" to fid,
            "tid" to tid,
            "page" to "1",
            "reason" to "手机版主题操作",
            "topiclist[]" to pid,
            "stickreply" to if (stick) "1" else "0",
        )
        val url = http.url(
            "forum.php?mod=topicadmin&action=stickreply&modsubmit=yes&modclick=yes&mobile=2&inajax=1",
        )
        val body = http.post(url, params, referer = "https://bbs.binmt.cc/thread-$tid-1-1.html")
        TOPICADMIN_SUCCESS.containsMatchIn(body)
    }

    /**
     * 打赏（Discuz 评分 rate，score2 为金币数）；返回是否成功。
     * 未提供 pid（首帖）时先重拉详情页取 postPid，评分接口必须带 pid。
     */
    suspend fun rewardAuthor(
        tid: String,
        pid: String?,
        formhash: String,
        amount: Int,
        message: String?,
    ): Boolean = withContext(Dispatchers.IO) {
        val targetPid = pid?.takeIf { it.isNotBlank() } ?: run {
            val latest = ForumParser.parseThreadPage(
                http.get(http.url("forum.php?mod=viewthread&tid=$tid&mobile=2")),
                1,
            )
            latest.posts.firstOrNull { it.isFirstPost }?.pid
        } ?: return@withContext false
        val ratePage = runCatching {
            http.get(http.url("forum.php?mod=misc&action=rate&tid=$tid&pid=$targetPid&showratetip=1&inajax=1&mobile=2"))
        }.getOrDefault("")
        val fh = ForumParser.parseFormhash(ratePage) ?: formhash
        if (fh.isBlank()) return@withContext false
        val params = mutableMapOf(
            "formhash" to fh,
            "tid" to tid,
            "pid" to targetPid,
            "ratesubmit" to "yes",
            "referer" to "https://bbs.binmt.cc/thread-$tid-1-1.html",
            "score1" to "1",
            "score2" to amount.toString(),
        )
        if (!message.isNullOrBlank()) params["reason"] = message
        val url = http.url("forum.php?mod=misc&action=rate&tid=$tid&pid=$targetPid&ratesubmit=yes&inajax=1&mobile=2")
        val body = http.post(url, params)
        body.isNotBlank() && !REWARD_FAIL_PATTERN.containsMatchIn(body) &&
            REWARD_SUCCESS_PATTERN.containsMatchIn(body)
    }

    /** 点赞用户列表（免登录）。 */
    suspend fun likeUsers(tid: String): List<LikeUser> = withContext(Dispatchers.IO) {
        ForumParser.parseLikeUsers(
            http.get(http.url("misc.php?op=recommend&tid=$tid&mod=faq&mobile=2")),
        )
    }

    /** 发新帖。 */
    suspend fun newThread(
        fid: String,
        formhash: String,
        subject: String,
        message: String,
    ): String = withContext(Dispatchers.IO) {
        val url = http.url("forum.php?mod=post&action=newthread&fid=$fid&topicsubmit=yes&mobile=2")
        http.post(
            url,
            mapOf(
                "formhash" to formhash,
                "subject" to subject,
                "message" to message,
                "topicsubmit" to "yes",
            ),
            referer = "https://bbs.binmt.cc/forum-$fid-1.html",
        )
    }

    /** 发新帖（支持主题分类、时间戳、已上传图片附件并返回结构化结果）。 */
    suspend fun submitNewThread(
        fid: String,
        formhash: String,
        subject: String,
        message: String,
        posttime: String? = null,
        typeid: String? = null,
        aids: List<String> = emptyList(),
    ): NewThreadResult = withContext(Dispatchers.IO) {
        val url = http.url("forum.php?mod=post&action=newthread&fid=$fid&extra=&topicsubmit=yes&mobile=2&handlekey=postform&inajax=1")
        val params = mutableMapOf(
            "formhash" to formhash,
            "subject" to subject,
            "message" to message,
            "topicsubmit" to "yes",
        )
        if (!posttime.isNullOrBlank()) params["posttime"] = posttime
        if (!typeid.isNullOrBlank() && typeid != "0") params["typeid"] = typeid
        for (aid in aids) {
            params["attachnew[$aid][description]"] = ""
        }
        val body = http.post(
            url,
            params,
            referer = "https://bbs.binmt.cc/forum.php?mod=post&action=newthread&fid=$fid&mobile=2",
        )
        ForumParser.parseNewThreadResult(body)
    }

    /** 登录：先取登录页的 hash，再提交凭据。 */
    suspend fun login(username: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        val loginPage = http.get(http.url("member.php?mod=logging&action=login&mobile=2"))
        val loginhash = ForumParser.extractLoginHash(loginPage)
            ?: return@withContext LoginResult(false, "无法获取登录令牌（可能被风控拦截）")
        val formhash = ForumParser.extractLoginFormhash(loginPage) ?: ""
        val result = http.post(
            http.url("member.php?mod=logging&action=login&loginsubmit=yes&loginhash=$loginhash"),
            mapOf(
                "formhash" to formhash,
                "referer" to http.baseUrl,
                "fastloginfield" to "username",
                "cookietime" to "31104000",
                "username" to username,
                "password" to password,
                "submit" to "true",
            ),
            referer = http.url("member.php?mod=logging&action=login&mobile=2"),
        )
        ForumParser.parseLoginResult(result)
    }

    /** 获取移动端签到页面状态（是否已签、formhash）。严格单次只读请求。 */
    suspend fun checkInStatus(): CheckInStatus = withContext(Dispatchers.IO) {
        val html = http.get(http.url("k_misign-sign.html"))
        ForumParser.parseCheckInStatus(html)
    }

    /** 提交签到请求。严格单次串行请求，带 formhash。 */
    suspend fun submitCheckIn(formhash: String): CheckInResult = withContext(Dispatchers.IO) {
        val body = http.get(
            url = http.url("plugin.php?id=k_misign:sign&operation=qiandao&format=text&formhash=$formhash"),
        )
        ForumParser.parseCheckInResponse(body)
    }

    /** 我的帖子列表（type: thread=我发表的主题, reply=我参与的回复）。 */
    suspend fun myThreads(type: String = "thread", page: Int = 1): MyThreadPage = withContext(Dispatchers.IO) {
        val html = http.get(http.url("home.php?mod=space&do=thread&view=me&type=$type&page=$page&mobile=2"))
        ForumParser.parseMyThreads(html, page)
    }

    /** 指定用户的帖子列表（type: thread=ta发表的主题, reply=ta参与的回复）。 */
    suspend fun userThreads(uid: String, type: String = "thread", page: Int = 1): MyThreadPage = withContext(Dispatchers.IO) {
        val html = http.get(http.url("home.php?mod=space&uid=$uid&do=thread&view=me&type=$type&page=$page&mobile=2"))
        ForumParser.parseMyThreads(html, page)
    }

    /** 我的收藏列表（type: all=全部, thread=帖子, forum=版块）。 */
    suspend fun myFavorites(type: String = "all", page: Int = 1): FavoritePage = withContext(Dispatchers.IO) {
        val html = http.get(http.url("home.php?mod=space&do=favorite&view=me&type=$type&page=$page&mobile=2"))
        ForumParser.parseFavorites(html, page)
    }

    /** 好友列表（view: 空为全部好友, visitor=最近来访, trace=我看过谁, blacklist=黑名单）。 */
    suspend fun myFriends(view: String = "", page: Int = 1): FriendUserPage = withContext(Dispatchers.IO) {
        val path = if (view.isBlank()) {
            "home.php?mod=space&do=friend&page=$page&mobile=2"
        } else {
            "home.php?mod=space&do=friend&view=$view&page=$page&mobile=2"
        }
        val html = http.get(http.url(path))
        ForumParser.parseFriends(html, page)
    }

    /** 关注与粉丝列表（type: following=我的关注, follower=我的粉丝）。 */
    suspend fun followList(type: String, page: Int = 1): FollowUserPage = withContext(Dispatchers.IO) {
        val path = if (type == "following") {
            "home.php?mod=follow&do=following&page=$page&mobile=2"
        } else {
            "home.php?mod=follow&do=follower&page=$page&mobile=2"
        }
        val html = http.get(http.url(path))
        ForumParser.parseFollowUsers(html, page, defaultIsFollowing = (type == "following"))
    }

    /** 关注 / 取消关注用户。 */
    suspend fun toggleFollowUser(uid: String, follow: Boolean): Boolean = withContext(Dispatchers.IO) {
        val formhash = fetchFormhash(FORMHASH_PAGE)
        if (formhash.isBlank()) return@withContext false
        val url = http.url(
            "home.php?mod=spacecp&ac=follow&op=${if (follow) "add" else "del"}" +
                "&fuid=$uid&hash=$formhash&handlekey=followmod&inajax=1&mobile=2",
        )
        isWriteSucceeded(http.get(url))
    }

    /** 关注 / 取消关注用户，返回站点提示文本。 */
    suspend fun followUser(uid: String, formhash: String?, follow: Boolean): String = withContext(Dispatchers.IO) {
        // 个人中心页与关注操作页都不含 formhash：黑名单页的退出登录链接带 formhash 参数
        val fh = formhash?.takeIf { it.isNotBlank() } ?: fetchFormhash(FORMHASH_PAGE)
        if (fh.isBlank()) return@withContext "获取操作凭据失败，请重试"
        val url = http.url(
            "home.php?mod=spacecp&ac=follow&op=${if (follow) "add" else "del"}" +
                "&fuid=$uid&hash=$fh&handlekey=followmod&inajax=1&mobile=2",
        )
        readWriteResult(http.get(url), if (follow) "已关注" else "已取消关注")
            .fold({ it }, { it.message ?: "操作失败，请重试" })
    }

    /** 依次抓页面提取 formhash；都取不到返回空串，调用方据此中止写操作。 */
    private suspend fun fetchFormhash(vararg paths: String): String {
        for (path in paths) {
            ForumParser.parseFormhashAny(http.get(http.url(path)))?.let { return it }
        }
        return ""
    }

    /**
     * spacecp 写操作回执（GET/POST 都带 inajax=1）：站点返回
     * `<root><![CDATA[<div class=comiis_tip><dt id=messagetext><p>提示文本
     * <script>...succeedhandle_xxx(...)]]></root>`，成功与失败分别由 succeedhandle_ /
     * errorhandle_ 标记；未登录、formhash 失配、WAF 挑战都会落到没有标记的分支。
     */
    private fun readWriteResult(body: String, successText: String): Result<String> {
        if (body.isBlank()) return Result.failure(IllegalStateException("站点没有返回结果"))
        val payload = Regex("(?s)<!\\[CDATA\\[(.*?)\\]\\]>").find(body)?.groupValues?.get(1) ?: body
        val ok = Regex("succeedhandle_|do_success|操作成功|成功收听|已成为好友").containsMatchIn(payload)
        if (ok) return Result.success(successText)
        val message = ForumParser.parseAjaxMessage(body)
        val failed = Regex("errorhandle_|submit_invalid|formhash|请先登录|Access Denied|非法操作|没有权限")
            .containsMatchIn(payload)
        if (failed || message != null) {
            return Result.failure(IllegalStateException(message ?: "操作失败"))
        }
        return Result.failure(IllegalStateException("操作失败，请重试"))
    }

    private fun isWriteSucceeded(body: String): Boolean = readWriteResult(body, "").isSuccess

    /** 添加好友（发送好友申请并带附言）。 */
    suspend fun addFriend(uid: String, formhash: String, note: String = ""): Result<String> = withContext(Dispatchers.IO) {
        val referer = http.url("home.php?mod=space&uid=$uid&do=profile&mobile=2")
        // 个人中心页不含 formhash，传入为空时抓加好友操作页取新鲜的
        val fh = formhash.takeIf { it.isNotBlank() }
            ?: fetchFormhash("home.php?mod=spacecp&ac=friend&op=add&uid=$uid&mobile=2")
        if (fh.isBlank()) return@withContext Result.failure(IllegalStateException("获取操作凭据失败，请重试"))
        val url = http.url("home.php?mod=spacecp&ac=friend&op=add&uid=$uid&inajax=1&mobile=2")
        val params = mapOf(
            "referer" to referer,
            "addsubmit" to "true",
            "addsubmit_btn" to "true",
            "formhash" to fh,
            "note" to note,
            "gid" to "1",
        )
        val res = http.post(url, params, referer = referer)
        val msg = ForumParser.parseAjaxMessage(res) ?: if (res.isNotBlank()) "请求已发送" else "发送失败"
        Result.success(msg)
    }

    /** 发送私信（聊聊天）。 */
    suspend fun sendPrivateMessage(touid: String, formhash: String, message: String): Result<String> = withContext(Dispatchers.IO) {
        if (message.isBlank()) return@withContext Result.failure(IllegalArgumentException("消息内容不能为空"))
        // 个人中心页不含 formhash；会话页带有效值，传入为空时从会话页取
        val fh = formhash.takeIf { it.isNotBlank() }
            ?: chatConversation(touid).formhash.orEmpty()
        if (fh.isBlank()) return@withContext Result.failure(IllegalStateException("获取操作凭据失败，请重试"))
        val referer = http.url("home.php?mod=space&do=pm&mobile=2")
        val url = http.url("home.php?mod=spacecp&ac=pm&op=send&pmid=0&daterange=2&pmsubmit=yes&inajax=1&mobile=2")
        val params = mapOf(
            "formhash" to fh,
            "touid" to touid,
            "message" to message,
            "pmsubmit" to "yes",
        )
        val res = http.post(url, params, referer = referer)
        val msg = ForumParser.parseAjaxMessage(res) ?: if (res.isNotBlank()) "发送成功" else "发送失败"
        Result.success(msg)
    }

    /** 将用户加入黑名单（拉黑ta）。 */
    suspend fun addUserBlacklist(username: String, formhash: String): Result<String> = withContext(Dispatchers.IO) {
        // 个人中心页不含 formhash，传入为空时抓黑名单页取新鲜的；
        // submitcheck 只读 URL 查询串里的 formhash，请求体里的不看
        val fh = formhash.takeIf { it.isNotBlank() }
            ?: fetchFormhash("home.php?mod=space&do=friend&view=blacklist&mobile=2")
        if (fh.isBlank()) return@withContext Result.failure(IllegalStateException("获取操作凭据失败，请重试"))
        val referer = http.url("home.php?mod=space&do=friend&view=blacklist&mobile=2")
        val url = http.url(
            "home.php?mod=spacecp&ac=friend&op=blacklist&start=&formhash=$fh&inajax=1&mobile=2",
        )
        val params = mapOf(
            "blacklistsubmit" to "true",
            "blacklistsubmit_btn" to "true",
            "formhash" to fh,
            "username" to username,
        )
        val res = http.post(url, params, referer = referer)
        readWriteResult(res, "已加入黑名单")
    }

    /**
     * 将用户移出黑名单。
     *
     * 站点的 subop=delete 分支直接删 home_blacklist，不走 submitcheck，无需 formhash。
     */
    suspend fun removeUserBlacklist(uid: String): Result<String> = withContext(Dispatchers.IO) {
        if (uid.isBlank()) return@withContext Result.failure(IllegalStateException("用户不存在"))
        val res = http.get(
            http.url(
                "home.php?mod=spacecp&ac=friend&op=blacklist&subop=delete&uid=$uid" +
                    "&inajax=1&mobile=2",
            ),
        )
        readWriteResult(res, "已移出黑名单")
    }
}

/**
 * formhash 的可靠来源页：黑名单页的「退出登录」链接带 formhash 参数，而个人主页、
 * 关注操作页都不含。写操作取不到凭证时抓这一页。
 */
private const val FORMHASH_PAGE = "home.php?mod=space&do=friend&view=blacklist&mobile=2"

/** 站点用户列表每页条数（好友/关注都是 24）。 */
private const val AT_USER_PAGE_SIZE = 24

/** 好友/关注列表最多翻几页，见 [ForumRepository.fetchFriends]。 */
private const val MAX_AT_USER_PAGES = 5

/**
 * 屏蔽是否成功。
 *
 * 成功回执是 inajax XML 里的 succeedhandle_ 脚本（站点还带一句「操作成功」）；
 * 没屏蔽成时站点回整页表单，两项都取不到。不能用「不含错误词」判——失败回执里
 * 同样可能出现 formhash 之类的字样。
 */
internal fun isIgnoreSucceeded(body: String): Boolean =
    body.contains("succeedhandle_") || body.contains("操作成功")
