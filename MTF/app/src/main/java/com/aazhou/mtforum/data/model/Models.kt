package com.aazhou.mtforum.data.model

import kotlinx.serialization.Serializable

/** 版块分组（如「MT专区」「交流与讨论」）。 */
@Serializable
data class ForumGroup(
    val name: String,
    val forums: List<Forum>,
)

/** 单个版块。 */
@Serializable
data class Forum(
    val fid: String,
    val name: String,
    val icon: String? = null,
)

/** 帖子摘要，用于列表页。 */
@Serializable
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
    /** 卡片配图地址，按页面顺序，最多 9 张；多图排布由此列表长度决定。 */
    val imageUrls: List<String> = emptyList(),
    /** 点赞数（列表卡片三数之首，站点未登录时为 0）。 */
    val likeCount: String? = null,
    /** 是否置顶帖。 */
    val isSticky: Boolean = false,
)

/** 版块页（帖子列表）。 */
@Serializable
data class ForumPage(
    val forumName: String?,
    val announcements: List<Thread>,
    val threads: List<Thread>,
    val page: Int,
    val hasNext: Boolean,
)

/** 帖子详情页。 */
@Serializable
data class ThreadPage(
    val tid: String,
    val subject: String?,
    val forumName: String?,
    val posts: List<Post>,
    val page: Int,
    val totalPages: Int,
    val formhash: String?,
    val replyUrl: String?,
    /** 快速回复框 form#fastpostform 的 noticeauthor 隐藏域（每次进页面都会变，AJAX 回复必需）。 */
    val noticeAuthor: String? = null,
    val fid: String? = null,
    /** 楼主 uid（关注接口需要）。 */
    val opUid: String? = null,
    /** 全部评论数（含被折叠的）。 */
    val totalReplies: Int? = null,
    /** 站点显示的点滴数（em.comiis_recommend_num）。 */
    val likeCount: Int? = null,
    /** 收藏数（span.comiis_favorite_a_num）。 */
    val favoriteCount: Int? = null,
    /** 当前用户是否已点赞；[likedStateKnown] 为 false 时该值无意义。 */
    val isLiked: Boolean = false,
    /** 页面是否明确给出点赞状态（登录态且站点渲染了点赞控件）。 */
    val likedStateKnown: Boolean = false,
    /** 当前用户是否已收藏；[favoritedStateKnown] 为 false 时该值无意义。 */
    val isFavorited: Boolean = false,
    /** 页面是否明确给出收藏状态。 */
    val favoritedStateKnown: Boolean = false,
    /** 当前用户是否已关注楼主；[followingStateKnown] 为 false 时该值无意义。 */
    val isFollowingOp: Boolean = false,
    /** 页面是否明确给出关注状态（登录态且站点渲染了关注控件）。 */
    val followingStateKnown: Boolean = false,
    /** 贴图上传接口的 uid / hash（帖子页内联脚本里，一次性，随页面刷新）。 */
    val uploadUid: String? = null,
    val uploadHash: String? = null,
)

/** 单个楼层/回复。 */
@Serializable
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
    /** 首帖是否含「回复可见」隐藏内容（处于锁定态，需回复后解锁）。 */
    val hasHiddenContent: Boolean = false,
)

/**
 * 某个拉取口径（全部评论 / 只看楼主）已加载到第几页。
 *
 * 楼层一律按「正序」拉：楼主过滤只是站点参数，倒序由客户端排，所以口径只此两种。
 * 页码只决定「还要不要再拉」，不影响已加载楼层本身。
 */
@Serializable
data class PageProgress(
    val authorId: String? = null,
    val maxPage: Int = 0,
    val totalPages: Int = 0,
)

/**
 * 供落盘的详情快照：楼主 / 正序 / 倒序共用一份。
 *
 * 三种视图只是同一批楼层的不同看法，缓存就不该按排序与楼主过滤分开存：分开存会让
 * 「正序加载完，切倒序还得整帖重拉」，而且某个视图只加载到一半就切走时，另一份
 * 缓存里就躺着半份数据。
 * [posts] 是已加载楼层按 pid 去重后的合集，[progress] 记每种口径各拉到第几页。
 * 会话态（formhash / replyUrl / 点赞收藏）一律不入缓存：一次性令牌与账号会话
 * 缓存住会在下次进入时用过期的令牌发请求、显示错的点赞态。
 */
@Serializable
data class ThreadSnapshot(
    val subject: String? = null,
    val forumName: String? = null,
    val opUid: String? = null,
    val fid: String? = null,
    /** 站点给出的全部评论数（不含首帖）；用于判断楼层池是否已收齐。 */
    val totalReplies: Int? = null,
    val posts: List<Post> = emptyList(),
    val progress: List<PageProgress> = emptyList(),
)

/**
 * 已下载帖子的一条登记：tid + 保存产物信息，供「已下载的帖子」列表展示与打开。
 *
 * 条目的删除只由用户在列表里手动触发（应用不做任何自动清理，也不设存储上限）；
 * [uri] 是外置 HTML 文件在媒体库中的地址，[fileName] 即其在 Download/MTF 下的文件名。
 */
@Serializable
data class DownloadedThread(
    val tid: String,
    val subject: String,
    val forumName: String? = null,
    val fileName: String,
    val uri: String,
    val savedAt: Long,
    val replyCount: Int? = null,
    val sizeBytes: Long = 0,
)

/** 楼层内的引用（回复某人时站点插入的引用块）。 */
@Serializable
data class PostQuote(
    /** 被引用者昵称。 */
    val author: String? = null,
    /**
     * 被引用楼层的时间文本。
     * 站点把引用块连同时间一起烘进楼层 HTML，时间是「引用那一刻」的快照：
     * 被引楼层当时新鲜会渲染成「N 分钟前」，隔天回看是错的，因此只保留绝对日期，
     * 相对时间解析为 null（引用卡不显示时间）。
     */
    val time: String? = null,
    /** 被引用层正文的纯文本。 */
    val text: String = "",
    /** 被引用楼层 pid，站点仅部分模板会带上。 */
    val pid: String? = null,
)

/** 点赞用户（点赞列表接口返回）。 */
data class LikeUser(
    val uid: String,
    val name: String,
    val avatarUrl: String? = null,
)

/**
 * 点赞结果。
 *
 * [remainingToday] 与 [serverCount] 均取自站点响应（daycount / recommendc），
 * 取不到时为 null，不得用任何写死的默认值代替。
 */
data class LikeResult(
    val success: Boolean,
    val remainingToday: Int? = null,
    val serverCount: Int? = null,
    /** 服务端提示原文（成功或失败）。 */
    val message: String? = null,
)

/** 快速回复结果：[message] 为站点回执的提示原文（成功时通常为空）。 */
data class ReplyResult(
    val success: Boolean,
    val message: String? = null,
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
@Serializable
data class SearchResult(
    val threads: List<Thread>,
    val hasNext: Boolean,
)

/** 首页数据：热版推荐 + 热帖排行。 */
@Serializable
data class HomeData(
    val hotForums: List<Forum>,
    val hotThreads: List<Thread>,
)

/**
 * 单条消息提醒（网页端「我的」→「消息提醒」）。
 *
 * [text] 为站点渲染的提醒正文，[timeText] 是页面上的相对/绝对时间文本；
 * [tid] 为关联主题，站点未给帖子链接时为 null（该条不可点击）。
 */
@Serializable
data class Notice(
    val id: String,
    val text: String,
    val timeText: String? = null,
    val tid: String? = null,
    /** 提醒指向的楼层 pid（「回复了您的帖子」这类提醒有），用于跳转到对应楼层。 */
    val pid: String? = null,
    /** 该楼层正文的纯文本摘要（按需拉取，用于列表里显示回复内容）。 */
    val content: String? = null,
    /** 该条提醒关联用户的头像地址（系统类提醒没有用户头像，为 null）。 */
    val avatarUrl: String? = null,
    /** 该条提醒关联用户的 uid；点头像进入其个人主页，为 null 时不可点。 */
    val uid: String? = null,
)

/** 消息提醒页解析结果；[loggedOut] 为 true 表示页面被重定向到了登录表单。 */
data class NoticePage(
    val loggedOut: Boolean = false,
    val notices: List<Notice> = emptyList(),
)
