package com.aazhou.mtforum.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aazhou.mtforum.data.log.LogLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.logDataStore by preferencesDataStore(name = "mtf_log")

/**
 * 日志等级的持久化（设置页三选一）。
 *
 * 缺省 [LogLevel.INFO]：诊断功能默认全记，由用户自己决定收敛到 Warn/Error。
 */
class LogSettingsStore(private val context: Context) {

    private val levelKey = stringPreferencesKey("level")

    val level: Flow<LogLevel> =
        context.logDataStore.data.map { prefs ->
            prefs[levelKey]
                ?.let { name -> runCatching { LogLevel.valueOf(name) }.getOrNull() }
                ?: LogLevel.INFO
        }

    suspend fun setLevel(level: LogLevel) {
        context.logDataStore.edit { it[levelKey] = level.name }
    }
}
