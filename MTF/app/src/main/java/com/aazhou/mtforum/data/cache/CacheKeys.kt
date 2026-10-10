package com.aazhou.mtforum.data.cache

/**
 * 缓存 key 与过期时间：集中一处，避免各处拼写不一致导致同一条数据存两份。
 *
 * 详情 key 只带 tid：楼主 / 正序 / 倒序共用一份快照
 * （见 com.aazhou.mtforum.data.model.ThreadSnapshot）。
 */
object CacheKeys {

    /** 列表类缓存的有效期：首页/版块/导读/搜索变化快，过期即重新拉。 */
    const val LIST_TTL: Long = 30 * 60 * 1000L

    /** 详情类缓存的有效期：帖子内容变化慢，长一点以减少重复请求。 */
    const val THREAD_TTL: Long = 24 * 60 * 60 * 1000L

    /**
     * v2 之前按排序与楼主过滤各存一份详情缓存，改成共用一份后旧文件不会再被读到，
     * 由缓存自身的 LRU 淘汰自然清掉；版本号用来保证两种 key 永不打架。
     */
    fun thread(tid: String): String = "thread|v2|$tid"

    fun notice(): String = "notice"

    fun notice(category: String): String = "notice|$category"

    fun forum(fid: String): String = "forum|$fid"

    fun home(): String = "home"

    /** 导读列表按 view 分槽：各 tab 共用一套加载机制但缓存互不覆盖。 */
    fun guide(view: String): String = "guide|$view"

    fun search(keyword: String): String = "search|$keyword"
}
