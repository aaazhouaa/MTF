package com.aazhou.mtforum.ui.screen.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aazhou.mtforum.R
import com.aazhou.mtforum.data.model.UserProfile
import com.aazhou.mtforum.image.smallImageRequest
import com.aazhou.mtforum.ui.common.accountIconModel
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.common.bottomBarContentPadding
import com.aazhou.mtforum.ui.common.container
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.accentTextColor
import com.aazhou.mtforum.ui.theme.bodyTextColor
import com.aazhou.mtforum.ui.theme.dimImageInDark

@Composable
fun ProfileScreen(
    onLogin: () -> Unit,
    onDownloads: () -> Unit,
    onMyPosts: () -> Unit = {},
    onMyFavorites: () -> Unit = {},
    onMyFriends: () -> Unit = {},
    onFollowList: (String) -> Unit = {},
    onUserClick: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val colors = LocalMtfColors.current
    val viewModel: ProfileViewModel = appViewModel { ProfileViewModel(it.repository) }
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()

    // 登录页返回等场景下重新拉取会话状态
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }

    val loggedIn = profile?.isLoggedIn == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(top = 12.dp, bottom = bottomBarContentPadding()),
    ) {
        if (loading && profile == null) {
            Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        // 顶部个人信息卡片（头像、用户名、UID在左侧，帖子/关注/粉丝统计在右侧）
        InfoCard(
            profile = profile,
            loggedIn = loggedIn,
            onAccountAction = {
                if (loggedIn) {
                    context.container().logout()
                    viewModel.load()
                } else {
                    onLogin()
                }
            },
            onPostsClick = { if (loggedIn) onMyPosts() else onLogin() },
            onFollowClick = { if (loggedIn) onFollowList("following") else onLogin() },
            onFansClick = { if (loggedIn) onFollowList("follower") else onLogin() },
            onAvatarClick = {
                val uid = profile?.uid
                if (loggedIn && !uid.isNullOrBlank()) onUserClick(uid) else onLogin()
            },
        )

        Spacer(Modifier.height(4.dp))

        // 功能入口卡片：横向排布「我的帖子」、「我的收藏」、「我的好友」
        ActionsCard(
            onMyPosts = { if (loggedIn) onMyPosts() else onLogin() },
            onMyFavorites = { if (loggedIn) onMyFavorites() else onLogin() },
            onMyFriends = { if (loggedIn) onMyFriends() else onLogin() },
        )

        Spacer(Modifier.height(4.dp))

        // 已下载的帖子入口
        val downloadCount by context.container().downloadStore.items
            .collectAsStateWithLifecycle(initialValue = emptyList())
        DownloadsCard(onOpen = onDownloads, count = downloadCount.size)
    }
}

/** 卡片 1：头像 + 名字 + UID（左侧），帖子/关注/粉丝统计（右侧）。 */
@Composable
private fun InfoCard(
    profile: UserProfile?,
    loggedIn: Boolean,
    onAccountAction: () -> Unit,
    onPostsClick: () -> Unit,
    onFollowClick: () -> Unit,
    onFansClick: () -> Unit,
    onAvatarClick: () -> Unit,
) {
    val colors = LocalMtfColors.current
    ProfileCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧：头像 + 名字 + UID 垂直排布
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onAvatarClick)
                    .padding(vertical = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(if (loggedIn) colors.quoteBackground else colors.surface),
                ) {
                    if (loggedIn) {
                        AsyncImage(
                            model = smallImageRequest(profile?.avatarUrl),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().dimImageInDark(),
                        )
                    } else {
                        Box(
                            Modifier.fillMaxSize().background(colors.quoteBackground),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_me),
                                contentDescription = null,
                                tint = colors.textHint,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (loggedIn) profile?.name.orEmpty().ifBlank { "未知用户" } else "点击登录",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.Bold,
                    color = bodyTextColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (loggedIn) "UID: ${profile?.uid.orEmpty()}" else "登录后查看更多",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textSecondary,
                )
            }

            // 右侧：三个统计指标（帖子、关注、粉丝），黄/金色突出，竖线分割
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 12.dp),
            ) {
                StatItem(
                    label = "帖子",
                    value = profile?.threads,
                    onClick = onPostsClick,
                )
                VerticalDivider(
                    modifier = Modifier
                        .height(32.dp)
                        .padding(horizontal = 10.dp),
                    color = colors.divider,
                )
                StatItem(
                    label = "关注",
                    value = profile?.following,
                    onClick = onFollowClick,
                )
                VerticalDivider(
                    modifier = Modifier
                        .height(32.dp)
                        .padding(horizontal = 10.dp),
                    color = colors.divider,
                )
                StatItem(
                    label = "粉丝",
                    value = profile?.followers,
                    onClick = onFansClick,
                )
            }

            Spacer(Modifier.width(8.dp))

            // 登录/登出操作按钮
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onAccountAction),
            ) {
                AsyncImage(
                    model = accountIconModel(logout = loggedIn),
                    contentDescription = if (loggedIn) "退出登录" else "登录",
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

/** 统计单元：上面黄色/金色大数字，下面灰色小字。 */
@Composable
private fun StatItem(
    label: String,
    value: Int?,
    onClick: () -> Unit,
) {
    val colors = LocalMtfColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 6.dp),
    ) {
        Text(
            text = value?.toString() ?: "0",
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp),
            fontWeight = FontWeight.Bold,
            color = colors.gold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = colors.textHint,
        )
    }
}

/** 卡片 2：功能入口横向排列（我的帖子、我的收藏、我的好友）。 */
@Composable
private fun ActionsCard(
    onMyPosts: () -> Unit,
    onMyFavorites: () -> Unit,
    onMyFriends: () -> Unit,
) {
    ProfileCard(contentPadding = 16.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionEntranceButton(
                icon = painterResource(R.drawable.ic_posts),
                title = "我的帖子",
                backgroundColor = Color(0xFFF59E0B),
                onClick = onMyPosts,
                modifier = Modifier.weight(1f),
            )
            ActionEntranceButton(
                icon = painterResource(R.drawable.ic_favorite_outline),
                title = "我的收藏",
                backgroundColor = Color(0xFF38BDF8),
                onClick = onMyFavorites,
                modifier = Modifier.weight(1f),
            )
            ActionEntranceButton(
                icon = painterResource(R.drawable.ic_friends),
                title = "我的好友",
                backgroundColor = Color(0xFFFB7185),
                onClick = onMyFriends,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 单个彩色大圆图标入口按钮。 */
@Composable
private fun ActionEntranceButton(
    icon: Painter,
    title: String,
    backgroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(backgroundColor),
        ) {
            Icon(
                painter = icon,
                contentDescription = title,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            fontWeight = FontWeight.Medium,
            color = bodyTextColor(),
        )
    }
}

/** 底部卡片：「已下载的帖子」入口。 */
@Composable
private fun DownloadsCard(onOpen: () -> Unit, count: Int) {
    val colors = LocalMtfColors.current
    ProfileCard(contentPadding = 0.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clickable(onClick = onOpen)
                .padding(horizontal = 16.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.quoteBackground),
            ) {
                Icon(
                    painterResource(R.drawable.ic_download),
                    contentDescription = null,
                    tint = accentTextColor(),
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                "已下载的帖子",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = bodyTextColor(),
                modifier = Modifier.weight(1f),
            )
            if (count > 0) {
                Text(
                    "$count 个",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = colors.textSecondary,
                )
                Spacer(Modifier.width(6.dp))
            }
            Icon(
                painterResource(R.drawable.ic_arrow_right),
                contentDescription = null,
                tint = colors.textHint,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** 统一的卡片外观：左右 6dp、间距 6dp、12dp 圆角。 */
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
