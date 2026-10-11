package com.aazhou.mtforum.data.repo

import com.aazhou.mtforum.data.model.FriendRequestItem
import com.aazhou.mtforum.data.model.FriendRequestPage
import com.aazhou.mtforum.data.network.HttpClient
import com.aazhou.mtforum.data.parser.ForumParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 「我的好友」的好友请求与好友管理。
 *
 * 站点是 Discuz! X3.4：好友请求列表在 spacecp 下（home.php?mod=spacecp&ac=friend&op=request），
 * 不在 do=friend 的 view 里；spacecp 的写操作全部经 submitcheck 校验，formhash 必须出现在
 * URL 查询串里（服务端只读 $_GET['formhash']），请求体里的不看。回执带 inajax=1 与
 * handlekey 时，成功是 succeedhandle_xxx(...)、失败是 errorhandle_xxx(...)。
 */
class FriendsRepo(private val http: HttpClient) {

    private val favoritePageUrl: String
        get() = http.url("home.php?mod=space&do=favorite&view=me&type=all&mobile=2")

    private val friendPageUrl: String
        get() = http.url("home.php?mod=space&do=friend&mobile=2")

    // 黑名单页的「退出登录」链接带 formhash，是这几个页面里最稳的凭证来源
    private val blacklistPageUrl: String
        get() = http.url("home.php?mod=space&do=friend&view=blacklist&mobile=2")

    private fun requestPageUrl(page: Int) =
        http.url("home.php?mod=spacecp&ac=friend&op=request&page=$page&mobile=2")

    /** 好友请求列表。 */
    suspend fun friendRequests(page: Int = 1): FriendRequestPage = withContext(Dispatchers.IO) {
        ForumParser.parseFriendRequests(http.get(requestPageUrl(page)), page)
    }


    /** 同意好友请求：uid 是发起申请的好友。 */
    suspend fun approveFriendRequest(uid: String, formhash: String): Result<String> =
        withContext(Dispatchers.IO) {
            if (uid.isBlank()) return@withContext Result.failure(IllegalStateException("用户不存在"))
            val fh = formhash.ifBlank {
                requestFormhash() ?: friendFormhash() ?: blacklistFormhash() ?: ""
            }
            if (fh.isBlank()) {
                return@withContext Result.failure(IllegalStateException("尚未登录或会话已过期"))
            }
            val url = http.url(
                "home.php?mod=spacecp&ac=friend&op=add&uid=$uid" +
                    "&formhash=$fh&add2submit=true&gid=1&handlekey=friendhk&inajax=1&mobile=2",
            )
            val body = http.post(
                url,
                mapOf("formhash" to fh, "add2submit" to "true", "gid" to "1"),
                referer = requestPageUrl(1),
            )
            readWriteResult(body, "已同意")
        }

    /** 忽略好友请求。 */
    suspend fun ignoreFriendRequest(uid: String, formhash: String): Result<String> =
        removeFriend(uid, formhash, "已忽略")

    /**
     * 删除好友：站点与忽略请求同一个 op=ignore，uid 语义相同
     * （是好友就删好友，是申请就删申请）。
     */
    suspend fun removeFriend(uid: String, formhash: String, successText: String = "已删除好友"): Result<String> =
        withContext(Dispatchers.IO) {
            if (uid.isBlank()) return@withContext Result.failure(IllegalStateException("用户不存在"))
            val fh = formhash.ifBlank {
                friendFormhash() ?: blacklistFormhash() ?: requestFormhash() ?: ""
            }
            if (fh.isBlank()) {
                return@withContext Result.failure(IllegalStateException("尚未登录或会话已过期"))
            }
            val url = http.url(
                "home.php?mod=spacecp&ac=friend&op=ignore&uid=$uid" +
                    "&formhash=$fh&friendsubmit=true&handlekey=friendhk&inajax=1&mobile=2",
            )
            val body = http.post(
                url,
                mapOf("formhash" to fh, "friendsubmit" to "true"),
                referer = friendPageUrl,
            )
            readWriteResult(body, successText)
        }

    /** 黑名单页的 formhash。 */
    suspend fun blacklistFormhash(): String? = withContext(Dispatchers.IO) {
        ForumParser.parseFormhashAny(http.get(blacklistPageUrl))
    }

    /** 好友请求页自身的 formhash。 */
    suspend fun requestFormhash(): String? = withContext(Dispatchers.IO) {
        ForumParser.parseFormhashAny(http.get(requestPageUrl(1)))
    }

    /** 好友列表页的 formhash。 */
    suspend fun friendFormhash(): String? = withContext(Dispatchers.IO) {
        ForumParser.parseFormhashAny(http.get(friendPageUrl))
    }

    private val RE_WRITE_OK = Regex(
        "succeedhandle_|do_success|操作成功|删除成功|添加成功|已成为好友|已忽略|忽略成功",
    )

    private val RE_WRITE_FAIL = Regex(
        "errorhandle_|submit_invalid|formhash错误|请先登录|非法操作|没有权限|不存在|失败|错误",
    )

    /** spacecp 写操作回执：inajax 回执看 succeedhandle_/errorhandle_，整页回执看提示词。 */
    private fun readWriteResult(body: String, successText: String): Result<String> {
        if (body.isBlank()) return Result.failure(IllegalStateException("站点没有返回结果"))
        val payload = Regex("(?s)<!\\[CDATA\\[(.*?)\\]\\]>").find(body)?.groupValues?.get(1) ?: body
        if (RE_WRITE_FAIL.containsMatchIn(payload)) {
            return Result.failure(IllegalStateException(ForumParser.parseAjaxMessage(body) ?: "操作失败"))
        }
        if (RE_WRITE_OK.containsMatchIn(payload)) return Result.success(successText)
        return Result.failure(IllegalStateException(ForumParser.parseAjaxMessage(body) ?: "操作失败"))
    }
}
