package com.aazhou.mtforum.data.repo

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.noticeUnreadDataStore by preferencesDataStore(name = "mtf_notice_unread")

/**
 * 消息未读状态：红点与推送都以它为准。
 *
 * 判定方式是「提醒 id 集合」而非最新一条的 id——站点把四个类别各拉一份合并，
 * 合并结果不按时间排序，取第一条当最新并不可靠；而提醒 id 全局唯一，
 * 只要这次列表里出现上次没有的 id，就是来了新消息。
 *
 * 第一次轮询时集合还是空的，只记基线不亮红点：否则刚装好应用就把历史未读
 * 全算成新消息，一进来就是一颗点。
 */
class NoticeUnreadStore(private val context: Context) {

    private val seenKey = stringSetPreferencesKey("seen_ids")
    private val unreadKey = booleanPreferencesKey("unread")

    val hasUnread: Flow<Boolean> =
        context.noticeUnreadDataStore.data.map { it[unreadKey] ?: false }

    /** 轮询结果：把 [ids] 记为新基线，返回其中新出现的 id（空表示没有新消息）。 */
    suspend fun onPolled(ids: List<String>): List<String> {
        if (ids.isEmpty()) return emptyList()
        var fresh = emptyList<String>()
        context.noticeUnreadDataStore.edit { prefs ->
            val seen = prefs[seenKey].orEmpty()
            // 首轮只建基线：见类注释
            fresh = if (seen.isEmpty()) emptyList() else ids.filterNot { it in seen }
            prefs[seenKey] = ids.toSet()
            if (fresh.isNotEmpty()) prefs[unreadKey] = true
        }
        return fresh
    }

    /** 看过消息页：以页面上这批提醒为已读，红点灭掉。 */
    suspend fun markRead(ids: List<String>) {
        context.noticeUnreadDataStore.edit { prefs ->
            // 页面没取到列表时不更新基线，否则会把这些 id 误判成下次的新消息
            if (ids.isNotEmpty()) prefs[seenKey] = ids.toSet()
            prefs[unreadKey] = false
        }
    }
}
