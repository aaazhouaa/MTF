package com.aazhou.mtforum.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.bodyTextColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 全局轻提示的停留时长：够读完一句话，又不至于挡着内容太久。 */
const val TOAST_DURATION_MS = 2600L

/**
 * 全局轻提示的发送入口：任意位置都能调（含非 Composable 上下文），
 * 由应用根部唯一的 [GlobalToastHost] 统一展示，位置与样式全局一致。
 *
 * @param durationMs 停留时长，默认走全局时长 [TOAST_DURATION_MS]。
 */
object AppToast {

    private val _current = MutableStateFlow<ToastMessage?>(null)
    val current: StateFlow<ToastMessage?> = _current.asStateFlow()

    /** 自增序号：同一条文案连发也能重新触发，不会被 StateFlow 的等值合吞掉。 */
    private var serial = 0

    fun show(text: String, durationMs: Long = TOAST_DURATION_MS) {
        if (text.isBlank()) return
        _current.value = ToastMessage(serial++, text, durationMs)
    }

    /** 到点熄灭，由宿主调用。 */
    fun dismiss() {
        _current.value = null
    }
}

data class ToastMessage(val serial: Int, val text: String, val durationMs: Long = TOAST_DURATION_MS)

/**
 * 全局轻提示宿主：贴在窗口顶部（状态栏下方）、盖在顶栏之上，带主题色细描边。
 *
 * 位置与样式只此一处：所有提示（含以后新增的）都走 [AppToast.show]，
 * 要调整观感改这里即可，各页面不再自行摆放提示卡片。
 */
@Composable
fun GlobalToastHost(modifier: Modifier = Modifier) {
    val message by AppToast.current.collectAsStateWithLifecycle()
    // 退场动画期间 message 已置空：用最后一条文案撑住内容，淡出时不会突然变空
    var lastText by remember { mutableStateOf("") }
    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        lastText = current.text
        delay(current.durationMs)
        AppToast.dismiss()
    }
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier.statusBarsPadding().padding(top = 26.dp),
        enter = fadeIn(tween(140)) + slideInVertically(tween(200)) { -it / 2 },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 4 },
    ) {
        val colors = LocalMtfColors.current
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .shadow(6.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                .border(0.1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = lastText,
                style = MaterialTheme.typography.bodyMedium,
                color = bodyTextColor(),
            )
        }
    }
}
