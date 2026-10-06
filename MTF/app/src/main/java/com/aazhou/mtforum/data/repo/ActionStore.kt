package com.aazhou.mtforum.data.repo

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.actionDataStore by preferencesDataStore(name = "mtf_actions")

/**
 * 持久化各主题的点赞/收藏状态。
 *
 * 站点只在登录态渲染点赞/收藏控件，未登录或控件缺失时页面无法告知状态，
 * 此时沿用本地记录，否则已操作过的帖子再次进入会被显示成未操作。
 */
class ActionStore(private val context: Context) {

    private fun likedKey(tid: String) = booleanPreferencesKey("liked_$tid")
    private fun favoritedKey(tid: String) = booleanPreferencesKey("favorited_$tid")

    /** 本地记录的点赞状态；从未记录过返回 null。 */
    suspend fun liked(tid: String): Boolean? =
        context.actionDataStore.data.map { it[likedKey(tid)] }.first()

    /** 本地记录的收藏状态；从未记录过返回 null。 */
    suspend fun favorited(tid: String): Boolean? =
        context.actionDataStore.data.map { it[favoritedKey(tid)] }.first()

    suspend fun setLiked(tid: String, liked: Boolean) {
        context.actionDataStore.edit { it[likedKey(tid)] = liked }
    }

    suspend fun setFavorited(tid: String, favorited: Boolean) {
        context.actionDataStore.edit { it[favoritedKey(tid)] = favorited }
    }
}
