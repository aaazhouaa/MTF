package com.aazhou.mtforum.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aazhou.mtforum.data.model.NoticeGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.noticeUnreadDataStore by preferencesDataStore(name = "mtf_notice_unread")

/**
 * 消息未读状态：红点与分类圆点都以它为准。
 *
 * 未读的权威来源是站点「消息提醒」总览页（`do=notice`）各分类入口的 `notice_r` 角标——
 * 站点服务端渲染，有未读才出现。这里按分类（[NoticeGroup]）记一套账：
 * 每次拉总览页，把「有未读的分类集合」写进来；全局红点 = 任一分类有未读。
 *
 * 已读交给站点：进某个分类页后站点会清掉该分类角标，客户端只需在返回一级页时
 * 重新拉总览页刷新即可，不需要本地 id 差集猜「哪些是新消息」。
 */
class NoticeUnreadStore(private val context: Context) {

    private val unreadGroupsKey = stringSetPreferencesKey("unread_groups")

    val hasUnread: Flow<Boolean> =
        context.noticeUnreadDataStore.data.map { prefs ->
            prefs[unreadGroupsKey].orEmpty().isNotEmpty()
        }

    /** 有未读的一级分类集合：一级页据此给各入口亮圆点。 */
    val unreadGroups: Flow<Set<NoticeGroup>> =
        context.noticeUnreadDataStore.data.map { prefs ->
            prefs[unreadGroupsKey].orEmpty()
                .mapNotNull { name -> NoticeGroup.entries.firstOrNull { it.name == name } }
                .toSet()
        }

    /**
     * 拉过总览页后写入当前有未读的分类集合，返回与上次相比新出现的分类（供推送用）。
     * 首次只建基线不返回新消息，避免刚装好就把历史未读全算成新提醒。
     */
    suspend fun onOverview(unread: Set<NoticeGroup>): Set<NoticeGroup> {
        var fresh = emptySet<NoticeGroup>()
        context.noticeUnreadDataStore.edit { prefs ->
            val old = prefs[unreadGroupsKey].orEmpty()
            val current = unread.map { it.name }.toSet()
            // 首次只建基线：见类注释
            if (old.isNotEmpty()) {
                fresh = current.filterNot { it in old }
                    .mapNotNull { name -> NoticeGroup.entries.firstOrNull { it.name == name } }
                    .toSet()
            }
            prefs[unreadGroupsKey] = current
        }
        return fresh
    }

    /** 清空未读（进消息提醒页后由站点触发已读，客户端同步归零）。 */
    suspend fun clear() {
        context.noticeUnreadDataStore.edit { prefs ->
            prefs[unreadGroupsKey] = emptySet()
        }
    }
}
