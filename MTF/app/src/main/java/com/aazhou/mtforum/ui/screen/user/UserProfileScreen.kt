package com.aazhou.mtforum.ui.screen.user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aazhou.mtforum.data.model.UserProfile
import com.aazhou.mtforum.ui.common.ErrorBox
import com.aazhou.mtforum.ui.common.LoadingBox
import com.aazhou.mtforum.ui.common.MtfTopBarHeight
import com.aazhou.mtforum.ui.common.UiState
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.bodyTextColor
import com.aazhou.mtforum.ui.theme.accentTextColor

/** 他人个人空间：头像/昵称/等级/用户组 + 五列统计 + 积分金币在线。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(uid: String, onBack: () -> Unit) {
    val colors = LocalMtfColors.current
    val viewModel: UserProfileViewModel = appViewModel { UserProfileViewModel(uid, it.repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = colors.background,
        // 语义色不是 M3 配色方案里的色，contentColorFor 会退回当前 LocalContentColor
        // （即上一次更新的值），切主题时会比容器慢一步，正文色糊在浅色底上
        contentColor = bodyTextColor(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.topBar),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(com.aazhou.mtforum.R.drawable.ic_back),
                            contentDescription = "返回",
                            tint = bodyTextColor(),
                        )
                    }
                },
                title = {
                    Text(
                        (state as? UiState.Success)?.data?.name?.takeIf { it.isNotBlank() } ?: "用户资料",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                        fontWeight = FontWeight.Bold,
                        color = bodyTextColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                expandedHeight = MtfTopBarHeight,
            )
        },
    ) { padding ->
        when (val current = state) {
            is UiState.Loading -> LoadingBox(Modifier.padding(padding))
            is UiState.Error -> ErrorBox(current.message, { viewModel.load() }, Modifier.padding(padding))
            is UiState.Success -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 12.dp),
            ) {
                InfoCard(current.data)
                CreditsCard(current.data)
                Spacer(Modifier.size(12.dp))
            }
        }
    }
}

@Composable
private fun InfoCard(profile: UserProfile) {
    val colors = LocalMtfColors.current
    ProfileCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = profile.avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(colors.quoteBackground),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    profile.name.orEmpty().ifBlank { "未知用户" },
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                    fontWeight = FontWeight.Bold,
                    color = bodyTextColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        "UID: ${profile.uid.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.textSecondary,
                    )
                    if (!profile.level.isNullOrBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            profile.level,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = accentTextColor(),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                if (!profile.groupName.isNullOrBlank()) {
                    Text(
                        profile.groupName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        fontWeight = FontWeight.Bold,
                        color = accentTextColor(),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 16.dp))
        Row(Modifier.fillMaxWidth()) {
            StatColumn("帖子", profile.threads, Modifier.weight(1f))
            StatColumn("回复", profile.posts, Modifier.weight(1f))
            StatColumn("好友", profile.friends, Modifier.weight(1f))
            StatColumn("关注", profile.following, Modifier.weight(1f))
            StatColumn("粉丝", profile.followers, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CreditsCard(profile: UserProfile) {
    val colors = LocalMtfColors.current
    ProfileCard {
        Row(Modifier.fillMaxWidth()) {
            MetricColumn("积分", profile.credits?.toString(), accentTextColor(), Modifier.weight(1f))
            MetricColumn("金币", profile.gold?.toString(), colors.gold, Modifier.weight(1f))
            MetricColumn("在线", profile.onlineTime, bodyTextColor(), Modifier.weight(1f))
        }
    }
}

/** 白色卡片外观：左右 6dp、间距 6dp、12dp 圆角。 */
@Composable
private fun ProfileCard(
    contentPadding: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    val colors = LocalMtfColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .padding(contentPadding),
    ) {
        content()
    }
}

@Composable
private fun StatColumn(label: String, value: Int?, modifier: Modifier = Modifier) {
    val colors = LocalMtfColors.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value?.toString() ?: "—",
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
            fontWeight = FontWeight.Bold,
            color = bodyTextColor(),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun MetricColumn(
    label: String,
    value: String?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMtfColors.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value?.takeIf { it.isNotBlank() } ?: "—",
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = colors.textSecondary,
        )
    }
}
