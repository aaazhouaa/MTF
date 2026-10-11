package com.aazhou.mtforum.data.repo

import com.aazhou.mtforum.data.model.CheckInResult
import com.aazhou.mtforum.data.model.CheckInStatus
import com.aazhou.mtforum.data.network.HttpClient
import com.aazhou.mtforum.data.parser.ForumParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * k_misign 签到的抓取与提交。
 *
 * 签到页（k_misign-sign.html）的 formhash 只以 URL 参数形态出现在签到按钮的 JS 里
 * （plugin.php?id=k_misign:sign&operation=qiandao&format=text&formhash=xxx），
 * 没有 input 也没有带引号的 JS 赋值，[ForumParser.parseFormhash] 的两种形状都取不到。
 */
class CheckInRepo(private val httpClient: HttpClient) {

    /** 拉签到页，取「是否已签」与 formhash。 */
    suspend fun status(): CheckInStatus = withContext(Dispatchers.IO) {
        val html = httpClient.get(httpClient.url(SIGN_PAGE))
        CheckInStatus(
            hasSigned = ForumParser.parseCheckInStatus(html).hasSigned,
            formhash = ForumParser.parseFormhashAny(html),
        )
    }

    /** 提交签到。 */
    suspend fun sign(formhash: String): CheckInResult = withContext(Dispatchers.IO) {
        val body = httpClient.get(
            url = httpClient.url(
                "plugin.php?id=k_misign:sign&operation=qiandao&format=text&formhash=$formhash",
            ),
        )
        ForumParser.parseCheckInResponse(body)
    }

    companion object {
        private const val SIGN_PAGE = "k_misign-sign.html"
    }
}
