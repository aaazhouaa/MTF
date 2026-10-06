package com.aazhou.mtforum.data.repo

import com.aazhou.mtforum.data.model.ForumGroup
import com.aazhou.mtforum.data.model.ForumPage
import com.aazhou.mtforum.data.model.HomeData
import com.aazhou.mtforum.data.model.LikeResult
import com.aazhou.mtforum.data.model.LikeUser
import com.aazhou.mtforum.data.model.LoginResult
import com.aazhou.mtforum.data.model.SearchResult
import com.aazhou.mtforum.data.model.ThreadPage
import com.aazhou.mtforum.data.model.UserProfile
import com.aazhou.mtforum.data.network.HttpClient
import com.aazhou.mtforum.data.parser.ForumParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 回复/操作失败响应里会出现的提示词。 */
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

/** 打赏成功标识。 */
private val REWARD_SUCCESS_PATTERN =
    Regex("succeedhandle_rate|rate_success|评分成功|打赏成功|评价成功|您已成功评分")

/** 打赏失败/受限（当日已评、积分不足、无权限等）。 */
private val REWARD_FAIL_PATTERN = Regex(
    "24小时|24 小时|已经评分|已评价|已评分|重复评分|评分失败|积分不足|余额不足|formhash错误|非法操作|没有权限|请先登录",
)

/** 论坛数据入口：封装请求 + 解析，UI 层只与它交互。 */
class ForumRepository(
    private val http: HttpClient,
) {

    /** 发帖页的 formhash（新版帖接口必需）。 */
    suspend fun postFormhash(fid: String): String? = withContext(Dispatchers.IO) {
        ForumParser.parseFormhash(http.get(http.url("forum.php?mod=post&action=newthread&fid=$fid&mobile=2")))
    }

    fun isLoggedIn(): Boolean = http.isLoggedIn()

    /** 当前会话 Cookie 头，供登录后持久化。 */
    fun sessionCookieHeader(): String = http.cookieHeader()

    suspend fun forumGroups(): List<ForumGroup> = withContext(Dispatchers.IO) {
        ForumParser.parseForumList(http.get(http.url("forum.php?forumlist=1&mobile=2")))
    }

    /** 首页：热版推荐 + 热帖排行。 */
    suspend fun home(): HomeData = withContext(Dispatchers.IO) {
        ForumParser.parseHome(http.get(http.url("forum.php?mod=guide&view=hot&mobile=2")))
    }

    /** 导读-最新发表。 */
    suspend fun guide(page: Int): ForumPage = withContext(Dispatchers.IO) {
        ForumParser.parseGuidePage(
            http.get(http.url("forum.php?mod=guide&view=newthread&index=$page&mobile=2")),
            page,
        )
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

    /** 回复帖子时携带的引用信息：[quotePid] 非空则引用该楼层。返回是否成功。 */
    suspend fun reply(
        fid: String,
        tid: String,
        formhash: String,
        message: String,
        quotePid: String? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        val url = http.url("forum.php?mod=post&action=reply&fid=$fid&tid=$tid&replysubmit=yes&mobile=2")
        val params = mutableMapOf(
            "formhash" to formhash,
            "message" to message,
            "replysubmit" to "yes",
        )
        if (!quotePid.isNullOrBlank()) params["quotepid"] = quotePid
        val body = http.post(url, params, referer = "https://bbs.binmt.cc/thread-$tid-1-1.html")
        body.isNotBlank() && !REPLY_ERROR_PATTERN.containsMatchIn(body)
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

    /** 首页帖子流（导读-最新发表），与版块页列表同结构。 */
    suspend fun homeFeed(page: Int): ForumPage = withContext(Dispatchers.IO) {
        ForumParser.parseForumPage(
            http.get(http.url("forum.php?mod=guide&view=newthread&page=$page&mobile=2")),
            page,
        )
    }

    /** 当前登录用户的个人空间主页（统计/积分/金币等）。 */
    suspend fun me(): UserProfile = withContext(Dispatchers.IO) {
        ForumParser.parseUserProfile(http.get(http.url("home.php?mod=space&mobile=2")))
    }

    /** 指定用户的个人空间（他人主页）。 */
    suspend fun userProfile(uid: String): UserProfile = withContext(Dispatchers.IO) {
        ForumParser.parseUserProfile(http.get(http.url("home.php?mod=space&uid=$uid&mobile=2")))
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
}
