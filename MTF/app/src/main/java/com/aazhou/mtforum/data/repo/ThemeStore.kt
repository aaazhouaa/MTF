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

/** 持久化主题色索引、自定义主题色、深浅模式覆盖（null 表示跟随系统）与深色模式是否压暗图片。 */
class ThemeStore(private val context: Context) {

    private val indexKey = intPreferencesKey("theme_index")
    private val customColorKey = intPreferencesKey("theme_custom_color")
    private val darkOverrideKey = booleanPreferencesKey("dark_override")
    private val dimImagesKey = booleanPreferencesKey("dim_images_dark")

    val themeIndex: Flow<Int> =
        context.themeDataStore.data.map { it[indexKey] ?: DefaultThemeIndex }

    /** 自定义主题色（ARGB）；null 表示用 [themeIndex] 指向的预设。 */
    val customColor: Flow<Int?> =
        context.themeDataStore.data.map { it[customColorKey] }

    val darkOverride: Flow<Boolean?> =
        context.themeDataStore.data.map { it[darkOverrideKey] }

    /** 缺省压暗：深色模式下的浅色截图过于刺眼，允许用户在设置里关掉。 */
    val dimImagesInDark: Flow<Boolean> =
        context.themeDataStore.data.map { it[dimImagesKey] ?: true }

    /** 选预设与自定义色是二选一：留着自定义色会让预设点击看不出效果，故一并清掉。 */
    suspend fun setThemeIndex(index: Int) {
        context.themeDataStore.edit {
            it[indexKey] = index
            it.remove(customColorKey)
        }
    }

    suspend fun setCustomColor(argb: Int?) {
        context.themeDataStore.edit { prefs ->
            if (argb == null) prefs.remove(customColorKey) else prefs[customColorKey] = argb
        }
    }

    suspend fun setDarkOverride(dark: Boolean?) {
        context.themeDataStore.edit { prefs ->
            if (dark == null) prefs.remove(darkOverrideKey) else prefs[darkOverrideKey] = dark
        }
    }

    suspend fun setDimImagesInDark(dim: Boolean) {
        context.themeDataStore.edit { it[dimImagesKey] = dim }
    }
}
