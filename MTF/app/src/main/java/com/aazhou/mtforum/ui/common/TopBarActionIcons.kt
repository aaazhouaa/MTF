package com.aazhou.mtforum.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aazhou.mtforum.R
import com.aazhou.mtforum.ui.theme.LocalIsDarkTheme
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.accentTextColor

/**
 * 顶栏右侧动作图标的统一几何：首页、板块、我的三个 tab 共用同一组数值，
 * 切换 tab 时「设置」「消息」两枚图标才能停在同一位置不动（以首页为基准）。
 */
val TopBarActionsEndPadding = 12.dp
val TopBarActionsIconSpacing = 3.dp

/**
 * 首页专用四图标快捷栏：签到、主题、设置、消息。
 */
@Composable
fun TopBarActionIcons(
    onToggleTheme: () -> Unit,
    onSettings: () -> Unit,
    onMessages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMtfColors.current
    val context = LocalContext.current
    val container = context.container()
    val checkInManager = container.checkInManager
    val hasSigned by checkInManager.hasSigned.collectAsStateWithLifecycle()
    val isCheckingIn by checkInManager.isCheckingIn.collectAsStateWithLifecycle()
    val hasUnreadNotices by container.unreadNotices.collectAsStateWithLifecycle()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        // 1. 签到按钮
        val checkInTint = if (hasSigned) colors.textHint else accentTextColor()
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(
                    enabled = !isCheckingIn,
                    onClick = { checkInManager.performCheckIn() },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_checkin),
                    contentDescription = if (hasSigned) "已签到" else "签到",
                    tint = checkInTint,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Spacer(Modifier.width(TopBarActionsIconSpacing))

        // 2. 切换主题按钮
        val isDarkTheme = LocalIsDarkTheme.current
        val darkProgress by animateFloatAsState(
            targetValue = if (isDarkTheme) 1f else 0f,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
            label = "ThemeToggleProgress",
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onToggleTheme),
            contentAlignment = Alignment.Center,
        ) {
            val sunAlpha = (1f - darkProgress * 1.6f).coerceIn(0f, 1f)
            val moonAlpha = ((darkProgress - 0.375f) * 1.6f).coerceIn(0f, 1f)

            if (sunAlpha > 0f) {
                Icon(
                    painter = painterResource(R.drawable.ic_theme_sun),
                    contentDescription = "切换主题",
                    tint = colors.iconSecondary,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            alpha = sunAlpha
                            rotationZ = darkProgress * 90f
                            scaleX = 1f - darkProgress * 0.4f
                            scaleY = 1f - darkProgress * 0.4f
                        },
                )
            }
            if (moonAlpha > 0f) {
                Icon(
                    painter = painterResource(R.drawable.ic_theme_moon),
                    contentDescription = "切换主题",
                    tint = colors.iconSecondary,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            alpha = moonAlpha
                            rotationZ = (darkProgress - 1f) * 90f
                            scaleX = 0.6f + darkProgress * 0.4f
                            scaleY = 0.6f + darkProgress * 0.4f
                        },
                )
            }
        }

        Spacer(Modifier.width(TopBarActionsIconSpacing))

        // 3. 设置按钮
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(22.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = "设置",
                    tint = colors.iconSecondary,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Spacer(Modifier.width(TopBarActionsIconSpacing))

        // 4. 消息提醒按钮
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onMessages),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(22.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_mail_line),
                    contentDescription = "消息提醒",
                    tint = colors.iconSecondary,
                    modifier = Modifier.fillMaxSize(),
                )
                if (hasUnreadNotices) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

/**
 * 板块与我的页专用轻量快捷栏：只保留设置与消息提醒。
 */
@Composable
fun TabSecondaryActionIcons(
    onSettings: () -> Unit,
    onMessages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMtfColors.current
    val context = LocalContext.current
    val container = context.container()
    val hasUnreadNotices by container.unreadNotices.collectAsStateWithLifecycle()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        // 设置按钮
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(22.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = "设置",
                    tint = colors.iconSecondary,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Spacer(Modifier.width(TopBarActionsIconSpacing))

        // 消息提醒按钮
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onMessages),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(22.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_mail_line),
                    contentDescription = "消息提醒",
                    tint = colors.iconSecondary,
                    modifier = Modifier.fillMaxSize(),
                )
                if (hasUnreadNotices) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}
