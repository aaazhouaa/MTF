package com.aazhou.mtforum.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aazhou.mtforum.R
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.accentTextColor
import com.aazhou.mtforum.ui.theme.bodyTextColor
import kotlinx.coroutines.delay

/** [HintToast] 的停留时长：够读完一句话，又不至于挡着内容太久。 */
const val HINT_DURATION_MS = 2600L

/**
 * 页面级一次性提示（ViewModel 的 actionMessage）与 [HintToast] 的桥接。
 *
 * 列表页与详情页的 actionMessage 都走这里：这类提示出现时页面里往往还有缓存内容，
 * 必须盖得住；而且应用的底部导航是浮层，贴在 Scaffold 底部的 Snackbar 会被它压住。
 */
@Composable
fun ActionHint(
    message: String?,
    onConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var hint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        hint = text
        // 熄灭由 HintToast 自己计时；这里立刻清掉来源，下一条消息才能重新触发
        onConsumed()
    }
    HintToast(message = hint, onDismiss = { hint = null }, modifier = modifier)
}

/** 统一的加载中 / 出错占位。 */
@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * 统一的出错占位：中偏上一枚圆角卡片（图标 + 文案 + 重试）。
 * 位置从中下挪到中偏上，是给列表首屏留出视觉重心；文案由调用方给，
 * 网络异常的英文原话不应直接透传，见各处 ViewModel 的友好化处理。
 */
@Composable
fun ErrorBox(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalMtfColors.current
    Box(
        modifier.fillMaxSize(),
        contentAlignment = BiasAlignment(horizontalBias = 0f, verticalBias = -0.22f),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .widthIn(max = 360.dp)
                .shadow(8.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(colors.quoteBackground)
                .padding(horizontal = 28.dp, vertical = 26.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_forbid),
                    contentDescription = null,
                    tint = accentTextColor(),
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = bodyTextColor(),
                textAlign = TextAlign.Center,
            )
            FilledTonalButton(onClick = onRetry) { Text("重试") }
        }
    }
}

/**
 * 中偏上的轻提示卡片：色条 + 文案，[durationMillis] 后自动消失。
 * 用于「加载失败但仍有内容可看」这类提示——内容还在屏幕上，提示必须盖得住，
 * 也不能像底部 Snackbar 那样被底栏和长列表挤没。
 */
@Composable
fun HintToast(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    durationMillis: Long = HINT_DURATION_MS,
) {
    if (message != null) {
        // 以文案为 key：同一条文案连发时不重启计时，新文案到了就换一条重新计时
        LaunchedEffect(message) {
            delay(durationMillis)
            onDismiss()
        }
    }
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
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
                .border(1.dp, colors.divider, RoundedCornerShape(14.dp))
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
                text = message.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = bodyTextColor(),
            )
        }
    }
}

@Composable
fun EmptyBox(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
