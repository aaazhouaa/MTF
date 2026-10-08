package com.aazhou.mtforum.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.aazhou.mtforum.R
import com.aazhou.mtforum.ui.theme.LocalIsDarkTheme

/**
 * 登录/登出动图的资源地址。
 *
 * 图标的墨迹是黑色的，深色主题下会糊进底色看不见，故按主题换用浅色墨迹的变体
 * （青色角标两套一致，不随主题改）。整张染色会连角标一起变色，所以做两份资产。
 */
@Composable
internal fun accountIconModel(logout: Boolean): String {
    val context = LocalContext.current
    val res = if (LocalIsDarkTheme.current) {
        if (logout) R.raw.anim_logout_dark else R.raw.anim_login_dark
    } else {
        if (logout) R.raw.anim_logout else R.raw.anim_login
    }
    return "android.resource://${context.packageName}/$res"
}
