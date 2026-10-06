package com.aazhou.mtforum.data.repo

import com.aazhou.mtforum.data.model.ForumGroup
import com.aazhou.mtforum.data.model.ForumPage
import com.aazhou.mtforum.data.model.HomeData
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

/** 点赞/收藏成功标识（Discuz ajax 响应片段）。 */
private val SUCCESS_PATTERN = Regex("succeedhandle|recommendv|recommendc|点赞成功|评价成功|favorite_ok|收藏成功")

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
     * 点赞 / 取消点赞（Discuz misc recommend）。
     * 写操作需携带 formhash，成功响应含 recommendv/recommendc/succeedhandle 等标识。
     */
    suspend fun setLike(tid: String, formhash: String, like: Boolean): Boolean = withContext(Dispatchers.IO) {
        val action = if (like) "add" else "del"
        val url = http.url(
            "forum.php?mod=misc&action=recommend&handlekey=recommend_$action" +
                "&do=$action&tid=$tid&hash=$formhash&inajax=1&mobile=2",
        )
        val body = http.get(url)
        body.isNotBlank() && !LIKE_ERROR_PATTERN.containsMatchIn(body) &&
            (SUCCESS_PATTERN.containsMatchIn(body) || !body.contains("<html", true))
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
