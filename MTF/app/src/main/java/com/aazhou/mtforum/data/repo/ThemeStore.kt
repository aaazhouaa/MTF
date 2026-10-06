package com.aazhou.mtforum.data.repo

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aazhou.mtforum.ui.theme.DefaultThemeIndex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(name = "mtf_theme")

/** 持久化主题色索引与深浅模式覆盖（null 表示跟随系统）。 */
class ThemeStore(private val context: Context) {

    private val indexKey = intPreferencesKey("theme_index")
    private val darkOverrideKey = booleanPreferencesKey("dark_override")

    val themeIndex: Flow<Int> =
        context.themeDataStore.data.map { it[indexKey] ?: DefaultThemeIndex }

    val darkOverride: Flow<Boolean?> =
        context.themeDataStore.data.map { it[darkOverrideKey] }

    suspend fun setThemeIndex(index: Int) {
        context.themeDataStore.edit { it[indexKey] = index }
    }

    suspend fun setDarkOverride(dark: Boolean?) {
        context.themeDataStore.edit { prefs ->
            if (dark == null) prefs.remove(darkOverrideKey) else prefs[darkOverrideKey] = dark
        }
    }
}
