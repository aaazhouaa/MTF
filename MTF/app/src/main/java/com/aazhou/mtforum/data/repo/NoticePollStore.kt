package com.aazhou.mtforum.data.repo

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.noticePollDataStore by preferencesDataStore(name = "mtf_notice_poll")

/**
 * 后台消息轮询开关（消息提醒页底部那一项）。
 *
 * 缺省关闭：站点没有推送服务端，定时查是客户端自己发的请求，默认不开既省电省流量，
 * 也少给站点添限频风险。开启后由 [com.aazhou.mtforum.NoticePollWorker] 每 15 分钟查
 * 一次；关闭时只保留「打开 App 或消息页时查一次」。
 */
class NoticePollStore(private val context: Context) {

    private val enabledKey = booleanPreferencesKey("background_enabled")

    val enabled: Flow<Boolean> =
        context.noticePollDataStore.data.map { it[enabledKey] ?: false }

    suspend fun setEnabled(enabled: Boolean) {
        context.noticePollDataStore.edit { it[enabledKey] = enabled }
    }
}
