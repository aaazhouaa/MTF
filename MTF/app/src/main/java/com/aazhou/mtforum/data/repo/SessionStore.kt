package com.aazhou.mtforum.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "mtf_session")

/** 持久化登录会话（Cookie 头），使重启后仍保持登录。 */
class SessionStore(private val context: Context) {

    private val cookieKey = stringPreferencesKey("cookie_header")
    private val uidKey = stringPreferencesKey("uid")
    private val nameKey = stringPreferencesKey("username")
    private val avatarKey = stringPreferencesKey("avatar")

    val cookieHeader: Flow<String> =
        context.sessionDataStore.data.map { it[cookieKey] ?: "" }

    val uid: Flow<String> = context.sessionDataStore.data.map { it[uidKey] ?: "" }
    val username: Flow<String> = context.sessionDataStore.data.map { it[nameKey] ?: "" }
    val avatar: Flow<String> = context.sessionDataStore.data.map { it[avatarKey] ?: "" }

    fun checkInDate(uid: String): Flow<String> {
        val key = stringPreferencesKey("check_in_date_$uid")
        return context.sessionDataStore.data.map { it[key] ?: "" }
    }

    suspend fun saveCheckInDate(uid: String, date: String) {
        val key = stringPreferencesKey("check_in_date_$uid")
        context.sessionDataStore.edit {
            it[key] = date
        }
    }

    suspend fun saveSession(cookieHeader: String, uid: String, username: String, avatar: String?) {
        context.sessionDataStore.edit {
            it[cookieKey] = cookieHeader
            it[uidKey] = uid
            it[nameKey] = username
            it[avatarKey] = avatar ?: ""
        }
    }

    suspend fun clearSession() {
        context.sessionDataStore.edit { it.clear() }
    }
}
