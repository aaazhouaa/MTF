package com.aazhou.mtforum.data.cache

/**
 * 缓存 key 与过期时间：集中一处，避免各处拼写不一致导致同一条数据存两份。
 */
object CacheKeys {

    /** 列表类缓存的有效期：首页/版块/导读/搜索变化快，过期即重新拉。 */
    const val LIST_TTL: Long = 30 * 60 * 1000L

    fun notice(): String = "notice"

    fun notice(category: String): String = "notice|$category"

    fun forum(fid: String): String = "forum|$fid"

    fun home(): String = "home"

    /** 导读列表按 view 分槽：各 tab 共用一套加载机制但缓存互不覆盖。 */
    fun guide(view: String): String = "guide|$view"

    fun search(keyword: String): String = "search|$keyword"

    /** 圈子/用户搜索按关键词分槽，与帖子搜索互不覆盖。 */
    fun searchForum(keyword: String): String = "search_forum|$keyword"

    fun searchUser(keyword: String): String = "search_user|$keyword"

    fun forumList(): String = "forumlist"

    /** 个人中心按 uid 分槽：换账号后不闪出上一个账号的资料。 */
    fun profile(uid: String): String = "profile|$uid"
}
