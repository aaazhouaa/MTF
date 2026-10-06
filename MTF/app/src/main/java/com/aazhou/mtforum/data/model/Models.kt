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
    /** 所属版块名（列表卡片底部那条 forum-N 链接）。 */
    val forumName: String? = null,
    /** 卡片封面图地址，无图时为 null。 */
    val thumbnail: String? = null,
    /** 点赞数（列表卡片三数之首，站点未登录时为 0）。 */
    val likeCount: String? = null,
    /** 是否置顶帖。 */
    val isSticky: Boolean = false,
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
    /** 楼主 uid（关注接口需要）。 */
    val opUid: String? = null,
    /** 全部评论数（含被折叠的）。 */
    val totalReplies: Int? = null,
    /** 站点显示的点滴数（em.comiis_recommend_num）。 */
    val likeCount: Int? = null,
    /** 收藏数（span.comiis_favorite_a_num）。 */
    val favoriteCount: Int? = null,
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
    /** 是否为主题首帖（首页第一层，站点用 comiis_postli_topv1 / comiis_top_nums2 标记）。 */
    val isFirstPost: Boolean = false,
    /** 楼层：楼主帖为 0，其余为帖子内绝对楼层号。 */
    val floor: Int? = null,
    /** 楼层显示文本：首屏为「沙发」「板凳」，之后为「16#」。 */
    val floorLabel: String? = null,
    /** 该楼引用的楼层，用于把回复归入被引用楼层下。 */
    val quote: PostQuote? = null,
    /** 「本帖最后由 X 于 T 编辑」的编辑标记；无编辑为 null。 */
    val editedText: String? = null,
)

/** 楼层内的引用（回复某人时站点插入的引用块）。 */
data class PostQuote(
    /** 被引用者昵称。 */
    val author: String? = null,
    /** 被引用楼层的时间文本。 */
    val time: String? = null,
    /** 被引用层正文的纯文本。 */
    val text: String = "",
    /** 被引用楼层 pid，站点仅部分模板会带上。 */
    val pid: String? = null,
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
    /** 用户组名（comiis 模板 span.kmlev）。 */
    val groupName: String? = null,
    /** 帖子数 / 回复数 / 好友 / 关注 / 粉丝。 */
    val threads: Int? = null,
    val posts: Int? = null,
    val friends: Int? = null,
    val following: Int? = null,
    val followers: Int? = null,
    /** 积分 / 金币 / 在线时长。 */
    val credits: Int? = null,
    val gold: Int? = null,
    val onlineTime: String? = null,
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
