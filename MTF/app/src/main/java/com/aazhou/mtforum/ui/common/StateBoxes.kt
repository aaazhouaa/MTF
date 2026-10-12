package com.aazhou.mtforum.ui.common

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aazhou.mtforum.R
import com.aazhou.mtforum.ui.theme.LocalIsDarkTheme
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.bodyTextColor

/** 统一的加载中占位。 */
@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * 统一的出错占位界面：
 * 采用现代通透的卡片与双层呼吸徽章，提供明确的状态主标题、人性化的中文辅助说明及带刷新图标的胶囊重试按钮。
 */
@Composable
fun ErrorBox(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current
    val primaryColor = MaterialTheme.colorScheme.primary

    // 智能人性化文案转译
    val isNetworkIssue = message.contains("connection", ignoreCase = true) ||
        message.contains("timeout", ignoreCase = true) ||
        message.contains("host", ignoreCase = true) ||
        message.contains("network", ignoreCase = true) ||
        message.contains("closed", ignoreCase = true) ||
        message.contains("reset", ignoreCase = true) ||
        message.contains("connect", ignoreCase = true)

    val titleText = if (isNetworkIssue) "网络连接异常" else "内容加载失败"
    val detailText = when {
        message.contains("connection closed", ignoreCase = true) -> "服务器连接已中断，请检查网络后重试"
        message.contains("timeout", ignoreCase = true) -> "网络请求超时，请检查网络连接"
        message.contains("Unable to resolve host", ignoreCase = true) ||
            message.contains("UnknownHost", ignoreCase = true) -> "网络无法连接，请检查是否已联网"
        message.isNotBlank() && !message.contains("Exception", ignoreCase = true) -> message
        else -> "加载遇到问题，请点击下方按钮重新加载"
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = BiasAlignment(horizontalBias = 0f, verticalBias = -0.15f),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(min = 220.dp, max = 320.dp)
                .shadow(
                    elevation = if (isDark) 0.dp else 4.dp,
                    shape = RoundedCornerShape(22.dp),
                    clip = false,
                )
                .clip(RoundedCornerShape(22.dp))
                .then(
                    if (isDark) Modifier.border(0.5.dp, colors.divider, RoundedCornerShape(22.dp))
                    else Modifier
                )
                .background(colors.surface)
                .padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            // 双层柔和呼吸徽章图标
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(primaryColor.copy(alpha = if (isDark) 0.16f else 0.10f)),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(primaryColor.copy(alpha = if (isDark) 0.22f else 0.14f)),
                ) {
                    Icon(
                        painter = painterResource(if (isNetworkIssue) R.drawable.ic_network_error else R.drawable.ic_forbid),
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = titleText,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                fontWeight = FontWeight.Bold,
                color = bodyTextColor(),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = detailText,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(18.dp))

            // 胶囊形重新加载按钮
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor,
                    contentColor = Color.White,
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 22.dp,
                    vertical = 9.dp,
                ),
                modifier = Modifier.height(38.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_refresh),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "重新加载",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
            }
        }
    }
}

/** 统一的空数据占位。 */
@Composable
fun EmptyBox(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = BiasAlignment(horizontalBias = 0f, verticalBias = -0.15f),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 28.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(colors.quoteBackground),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_list),
                    contentDescription = null,
                    tint = colors.textHint,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
