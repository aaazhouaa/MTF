package com.aazhou.mtforum.data.cache

/**
 * 缓存 key 与过期时间：集中一处，避免各处拼写不一致导致同一条数据存两份。
 */
object CacheKeys {

    /** 列表类缓存的有效期：首页/版块/导读/搜索变化快，过期即重新拉。 */
    const val LIST_TTL: Long = 30 * 60 * 1000L

    fun notice(): String = "notice"

    fun notice(category: String): String = "notice|$category"

    /** 版块列表按排序档位分槽：各档位内容不同，共用一个 key 会互相覆盖。 */
    fun forum(fid: String, sort: String): String = "forum|$fid|$sort"

    fun home(): String = "home"

    /** 导读列表按 view 分槽：各 tab 共用一套加载机制但缓存互不覆盖。 */
    fun guide(view: String): String = "guide|$view"

    fun search(keyword: String): String = "search|$keyword"

    /** 圈子/用户搜索按关键词分槽，与帖子搜索互不覆盖。 */
    fun searchForum(keyword: String): String = "search_forum|$keyword"

    fun searchUser(keyword: String): String = "search_user|$keyword"

    fun forumList(): String = "forumlist"

    /**
     * 登录态私有列表按 uid 与页内分类分槽：换账号后不闪出上一个账号的列表。
     */
    fun myThreads(uid: String, type: String): String = "my_threads|$uid|$type"

    fun myFavorites(uid: String, type: String): String = "my_favorites|$uid|$type"

    /** 好友/来访/黑名单按 view 分槽；view 为空串时是全部好友。 */
    fun myFriends(uid: String, view: String): String = "my_friends|$uid|$view"

    fun friendRequests(uid: String): String = "friend_requests|$uid"

    fun followList(uid: String, type: String): String = "follow_list|$uid|$type"

    /** 他人个人主页按 uid 分槽，与「个人中心」的资料缓存互不覆盖。 */
    fun userProfile(uid: String): String = "user_profile|$uid"

    /** 个人中心按 uid 分槽：换账号后不闪出上一个账号的资料。 */
    fun profile(uid: String): String = "profile|$uid"
}
