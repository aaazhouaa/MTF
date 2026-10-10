package com.aazhou.mtforum.data.repo

import com.aazhou.mtforum.data.model.ForumGroup
import com.aazhou.mtforum.data.model.AtUser
import com.aazhou.mtforum.data.model.CheckInResult
import com.aazhou.mtforum.data.model.CheckInStatus
import com.aazhou.mtforum.data.model.ForumPage
import com.aazhou.mtforum.data.model.HomeData
import com.aazhou.mtforum.data.model.LikeResult
import com.aazhou.mtforum.data.model.LikeUser
import com.aazhou.mtforum.data.model.LoginResult
import com.aazhou.mtforum.data.model.Notice
import com.aazhou.mtforum.data.model.NoticePage
import com.aazhou.mtforum.data.model.PrivateMessage
import com.aazhou.mtforum.data.model.ChatConversation
import com.aazhou.mtforum.data.model.FollowerUser
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
import kotlinx.coroutines.withContext
import java.io.File

/** 回复未拿到 inajax XML 回执时（整页 HTML）兜底判失败、提示重新登录的提示词。 */
private val REPLY_ERROR_PATTERN = Regex("抱歉|非法操作|请先登录|需要先登录|没有权限|审核")

/** 点赞/收藏失败提示词。 */
private val LIKE_ERROR_PATTERN = Regex(
    "没有点赞权限|不能点赞|今日评价机会已用完|关闭的主题|请先登录|formhash错误|非法操作|不能收藏",
)

/** 举报失败：返回体仍是举报表单或跳登录页。 */
private val REPORT_ERROR_PATTERN = Regex("举报理由|action=login|请先登录")

/** 删除未生效：返回的仍是编辑表单（含 editsubmit）或跳登录页。
 * 不能拿 name="message" 判定——删除成功后重定向到的帖子页也带快速回复框。 */
private val DELETE_STILL_FORM_PATTERN = Regex("editsubmit|action=login")

/** 消息提醒的内容类别（默认页只是类别菜单，没有提醒内容）。 */
private val NOTICE_VIEWS = listOf("mypost", "interactive", "system", "app")

/** 打赏成功标识。 */
private val REWARD_SUCCESS_PATTERN =
    Regex("succeedhandle_rate|rate_success|评分成功|打赏成功|评价成功|您已成功评分")

/** 打赏失败/受限（当日已评、积分不足、无权限等）。 */
private val REWARD_FAIL_PATTERN = Regex(
    "24小时|24 小时|已经评分|已评价|已评分|重复评分|评分失败|积分不足|余额不足|formhash错误|非法操作|没有权限|请先登录",
)

/** 站点笑脸表地址：CDN 上的 JS 缓存文件，支持 HTTP 条件请求。 */
private val SMILEY_URL = "${HttpClient.BASE_URL}data/cache/common_smilies_var.js"

/** 表情表的保鲜期：超过这个时间才带 etag 回站确认一次，期内零网络请求。 */
private const val SMILEY_REVALIDATE_INTERVAL_MS = 12 * 60 * 60 * 1000L

/** guide 模块里由门户插件出列表的视图：站点把 view=hot 做成门户首页，帖子列表走 AJAX。 */
private const val GUIDE_VIEW_PORTAL = "hot"

/** 论坛数据入口：封装请求 + 解析，UI 层只与它交互。 */
class ForumRepository(
    private val http: HttpClient,
    private val smileyStore: SmileyStore,
) {

    /** 会话内缓存的表情包：站点不常变，避免每次打开表情面板都拉一次。 */
    private var smileyCache: List<SmileyPack>? = null


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

    suspend fun forumPage(fid: String, page: Int): ForumPage = withContext(Dispatchers.IO) {
        ForumParser.parseForumPage(http.get(http.url("forum-$fid-$page.html")), page)
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
    ): String? = withContext(Dispatchers.IO) {
        val body = http.upload(
            url = "${HttpClient.BASE_URL}misc.php?mod=swfupload&operation=upload&type=image&inajax=yes&infloat=yes&simple=2",
            file = file,
            fieldName = "Filedata",
            extraFields = mapOf("uid" to uid, "hash" to hash),
        )
        // 响应为 DISCUZUPLOAD|1|0|<aid>|<...>|<附件路径>|<文件名>|...，第 3 段为状态码
        val parts = body.split('|')
        if (parts.size >= 4 && parts[0] == "DISCUZUPLOAD" && parts[2] == "0") parts[3] else null
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
            val url = http.url(
                "home.php?mod=spacecp&ac=favorite&op=delete&favid=$favid&type=all&mobile=2",
            )
            val body = http.post(
                url,
                mapOf(
                    "formhash" to formhash,
                    "deletesubmit" to "true",
                    "handlekey" to "comiis",
                ),
                referer = "https://bbs.binmt.cc/home.php?mod=space&do=favorite&type=all&mobile=2",
            )
            body.isNotBlank() && !LIKE_ERROR_PATTERN.containsMatchIn(body)
        }
    }

    /** 从收藏列表页找出该主题的 favid。 */
    private suspend fun favoriteFavid(tid: String): String? {
        val html = http.get(http.url("home.php?mod=space&do=favorite&type=all&mobile=2"))
        return ForumParser.parseFavoriteFavid(html, tid)
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
     * 消息提醒：网页端「我的」→「消息提醒」。
     *
     * 默认地址只是类别菜单（我的消息/我的帖子/坛友互动/系统提醒/应用提醒），
     * 提醒内容按类别分页，故把四个内容类别各拉一份合并成一条列表。未登录时
     * 第一份就被换成登录表单，直接返回 loggedOut，不再多打请求。
     */
    suspend fun notices(): NoticePage = withContext(Dispatchers.IO) {
        val merged = mutableListOf<Notice>()
        for (view in NOTICE_VIEWS) {
            val page = ForumParser.parseNotices(
                http.get(http.url("home.php?mod=space&do=notice&view=$view&mobile=2")),
            )
            if (page.loggedOut) return@withContext NoticePage(loggedOut = true)
            merged += page.notices
        }
        // id 用站点给的提醒 id：新消息插到前面时下标会平移，位置 id 会让后面的行
        // 整批重建；同一 id 只留一条，避免列表 key 重复
        NoticePage(notices = merged.distinctBy { it.id })
    }

    /** 单个分类下的消息提醒（mypost / interactive / system / app）。 */
    suspend fun noticesByView(view: String): NoticePage = withContext(Dispatchers.IO) {
        val page = ForumParser.parseNotices(
            http.get(http.url("home.php?mod=space&do=notice&view=$view&mobile=2")),
        )
        if (page.loggedOut) return@withContext NoticePage(loggedOut = true)
        NoticePage(notices = page.notices.distinctBy { it.id })
    }

    /** 我的消息（私信列表）。 */
    suspend fun privateMessages(): List<PrivateMessage> = withContext(Dispatchers.IO) {
        val html = http.get(http.url("home.php?mod=space&do=pm&mobile=2"))
        ForumParser.parsePrivateMessages(html)
    }

    /** 与特定用户的私信会话详情（聊天记录）。 */
    suspend fun chatConversation(touid: String): ChatConversation = withContext(Dispatchers.IO) {
        val html = http.get(http.url("home.php?mod=space&do=pm&subop=view&touid=$touid&mobile=2"))
        ForumParser.parseChatConversation(html, touid)
    }

    /** 我的粉丝列表。 */
    suspend fun followers(): List<FollowerUser> = withContext(Dispatchers.IO) {
        val html = http.get(http.url("home.php?mod=follow&do=follower&mobile=2"))
        ForumParser.parseFollowers(html)
    }

    /**
     * 提醒里「回复了您的帖子」指向的楼层在第几页。
     *
     * 站点的楼层链接是 redirect 中转地址，302 到 `viewthread&tid=X&page=N`；
     * 直接把这一页拉回来，比从第 1 页顺着自动链翻到末页快得多。
     * 中转失败（风控拦截等）返回 null，调用方退化成只打开帖子。
     */
    suspend fun noticeReplyPage(tid: String, pid: String): Int? = withContext(Dispatchers.IO) {
        val target = runCatching {
            http.redirectLocation(
                http.url("forum.php?mod=redirect&goto=findpost&ptid=$tid&pid=$pid"),
            )
        }.getOrNull() ?: return@withContext null
        ForumParser.pageFromThreadUrl(target)
    }

    /**
     * 一批提醒指向的楼层正文摘要（pid -> 纯文本）。
     *
     * 站点提醒页只给「谁 回复了您的帖子 标题」，没有回复内容；要显示内容得按
     * 楼层链接找到所在页再摘出该楼层。走 [HttpClient.getWithFinalUrl]：中转地址
     * 302 到的那一页就是内容来源，且与其它请求同一套 WAF 处理。已经取回来的页里
     * 若已含目标楼层就不再取（同帖多条提醒通常落在同一页）。
     */
    suspend fun noticeReplyTexts(items: List<Pair<String, String>>): Map<String, String> =
        withContext(Dispatchers.IO) {
            val result = HashMap<String, String>()
            // 最终地址 -> 已解析的那一页（同一页只解析一次）
            val pages = HashMap<String, ThreadPage>()
            for ((tid, pid) in items) {
                if (result.containsKey(pid)) continue
                val page = pages.values.firstOrNull { it.posts.any { post -> post.pid == pid } }
                    ?: run {
                        val fetched = runCatching {
                            http.getWithFinalUrl(
                                http.url("forum.php?mod=redirect&goto=findpost&ptid=$tid&pid=$pid"),
                            )
                        }.getOrNull() ?: continue
                        pages.getOrPut(fetched.first) {
                            ForumParser.parseThreadPage(
                                fetched.second,
                                ForumParser.pageFromThreadUrl(fetched.first),
                            )
                        }
                    }
                val post = page.posts.firstOrNull { it.pid == pid } ?: continue
                ForumParser.plainText(post.contentHtml)
                    .takeIf { it.isNotBlank() }
                    ?.let { result[pid] = it }
            }
            result
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
        body.isNotBlank() && !DELETE_STILL_FORM_PATTERN.containsMatchIn(body)
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

    /** 我的收藏列表（type: all=全部, thread=帖子, forum=版块）。 */
    suspend fun myFavorites(type: String = "all", page: Int = 1): FavoritePage = withContext(Dispatchers.IO) {
        val html = http.get(http.url("home.php?mod=space&do=favorite&type=$type&page=$page&mobile=2"))
        ForumParser.parseFavorites(html, page)
    }

    /** 按 favid 删除收藏。 */
    suspend fun deleteFavorite(favid: String): Boolean = withContext(Dispatchers.IO) {
        val html = http.get(http.url("home.php?mod=space&do=favorite&type=all&mobile=2"))
        val formhash = ForumParser.parseFormhash(html) ?: return@withContext false
        val url = http.url("home.php?mod=spacecp&ac=favorite&op=delete&favid=$favid&type=all&mobile=2")
        val body = http.post(
            url,
            mapOf(
                "formhash" to formhash,
                "deletesubmit" to "true",
                "handlekey" to "comiis",
            ),
            referer = "https://bbs.binmt.cc/home.php?mod=space&do=favorite&type=all&mobile=2",
        )
        body.isNotBlank() && !LIKE_ERROR_PATTERN.containsMatchIn(body)
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
        val html = http.get(http.url("home.php?mod=follow&do=following&mobile=2"))
        val formhash = ForumParser.parseFormhash(html) ?: ""
        val url = http.url(
            "home.php?mod=spacecp&ac=follow&op=${if (follow) "add" else "del"}" +
                "&fuid=$uid&hash=$formhash&mobile=2",
        )
        val res = http.get(url)
        res.isNotBlank()
    }

    /** 关注 / 取消关注用户，返回站点提示文本。 */
    suspend fun followUser(uid: String, formhash: String?, follow: Boolean): String = withContext(Dispatchers.IO) {
        val fh = formhash?.takeIf { it.isNotBlank() } ?: run {
            val html = http.get(http.url("home.php?mod=follow&do=following&mobile=2"))
            ForumParser.parseFormhash(html) ?: ""
        }
        val url = http.url(
            "home.php?mod=spacecp&ac=follow&op=${if (follow) "add" else "del"}" +
                "&fuid=$uid&hash=$fh&inajax=1&mobile=2",
        )
        val res = http.get(url)
        ForumParser.parseAjaxMessage(res) ?: if (follow) "已关注" else "已取消关注"
    }

    /** 添加好友（发送好友申请并带附言）。 */
    suspend fun addFriend(uid: String, formhash: String, note: String = ""): Result<String> = withContext(Dispatchers.IO) {
        val url = http.url("home.php?mod=spacecp&ac=friend&op=add&uid=$uid&inajax=1")
        val params = mapOf(
            "referer" to http.url("home.php?mod=space&uid=$uid&do=profile&mobile=2"),
            "addsubmit" to "true",
            "addsubmit_btn" to "true",
            "formhash" to formhash,
            "note" to note,
            "gid" to "1",
        )
        val res = http.post(url, params)
        val msg = ForumParser.parseAjaxMessage(res) ?: if (res.isNotBlank()) "请求已发送" else "发送失败"
        Result.success(msg)
    }

    /** 发送私信（聊聊天）。 */
    suspend fun sendPrivateMessage(touid: String, formhash: String, message: String): Result<String> = withContext(Dispatchers.IO) {
        if (message.isBlank()) return@withContext Result.failure(IllegalArgumentException("消息内容不能为空"))
        val url = http.url("home.php?mod=spacecp&ac=pm&op=send&pmid=0&daterange=2&pmsubmit=yes&inajax=1&mobile=2")
        val params = mapOf(
            "formhash" to formhash,
            "touid" to touid,
            "message" to message,
            "pmsubmit" to "yes",
        )
        val res = http.post(url, params)
        val msg = ForumParser.parseAjaxMessage(res) ?: if (res.isNotBlank()) "发送成功" else "发送失败"
        Result.success(msg)
    }

    /** 将用户加入黑名单（拉黑ta）。 */
    suspend fun addUserBlacklist(username: String, formhash: String): Result<String> = withContext(Dispatchers.IO) {
        val url = http.url("home.php?mod=spacecp&ac=friend&op=blacklist&start=&inajax=1")
        val params = mapOf(
            "blacklistsubmit" to "true",
            "blacklistsubmit_btn" to "true",
            "formhash" to formhash,
            "username" to username,
        )
        val res = http.post(url, params)
        val msg = ForumParser.parseAjaxMessage(res) ?: if (res.isNotBlank()) "已加入黑名单" else "操作失败"
        Result.success(msg)
    }
}

/** 站点用户列表每页条数（好友/关注都是 24）。 */
private const val AT_USER_PAGE_SIZE = 24

/** 好友/关注列表最多翻几页，见 [ForumRepository.fetchFriends]。 */
private const val MAX_AT_USER_PAGES = 5
