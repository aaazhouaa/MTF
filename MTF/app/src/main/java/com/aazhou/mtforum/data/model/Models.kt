package com.aazhou.mtforum.data.model

/** 版块分组（如「MT专区」「交流与讨论」）。 */
data class ForumGroup(
    val name: String,
    val forums: List<Forum>,
)

/** 单个版块。 */
data class Forum(
    val fid: String,
    val name: String,
    val icon: String? = null,
)

/** 帖子摘要，用于列表页。 */
data class Thread(
    val tid: String,
    val subject: String,
    val authorName: String? = null,
    val authorUid: String? = null,
    val avatarUrl: String? = null,
    val level: String? = null,
    val timeText: String? = null,
    val replyCount: String? = null,
    val viewCount: String? = null,
    val excerpt: String? = null,
    val isAnnouncement: Boolean = false,
)

/** 版块页（帖子列表）。 */
data class ForumPage(
    val forumName: String?,
    val announcements: List<Thread>,
    val threads: List<Thread>,
    val page: Int,
    val hasNext: Boolean,
)

/** 帖子详情页。 */
data class ThreadPage(
    val tid: String,
    val subject: String,
    val forumName: String?,
    val posts: List<Post>,
    val page: Int,
    val totalPages: Int,
    val formhash: String?,
    val replyUrl: String?,
    val fid: String? = null,
)

/** 单个楼层/回复。 */
data class Post(
    val pid: String,
    val authorName: String,
    val authorUid: String? = null,
    val avatarUrl: String? = null,
    val level: String? = null,
    val timeText: String? = null,
    val location: String? = null,
    val contentHtml: String = "",
    val isOp: Boolean = false,
)

/** 登录结果。 */
data class LoginResult(
    val success: Boolean,
    val message: String? = null,
    val needsCaptcha: Boolean = false,
    val captchaUrl: String? = null,
)

/** 当前用户信息。 */
data class UserProfile(
    val uid: String? = null,
    val name: String? = null,
    val avatarUrl: String? = null,
    val level: String? = null,
) {
    val isLoggedIn: Boolean get() = !uid.isNullOrEmpty() && uid != "0"
}

/** 搜索结果。 */
data class SearchResult(
    val threads: List<Thread>,
    val hasNext: Boolean,
)

/** 首页数据：热版推荐 + 热帖排行。 */
data class HomeData(
    val hotForums: List<Forum>,
    val hotThreads: List<Thread>,
)
