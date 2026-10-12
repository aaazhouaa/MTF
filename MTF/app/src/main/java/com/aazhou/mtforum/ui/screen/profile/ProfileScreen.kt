package com.aazhou.mtforum.ui.screen.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.aazhou.mtforum.ui.common.TabSecondaryActionIcons
import com.aazhou.mtforum.ui.common.TopBarActionsEndPadding
import com.aazhou.mtforum.ui.common.TopBarVerticalPadding
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.common.bottomBarContentPadding
import com.aazhou.mtforum.ui.common.container
import com.aazhou.mtforum.ui.screen.login.LoginDialog
import com.aazhou.mtforum.ui.theme.LocalIsDarkTheme
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.bodyTextColor
import com.aazhou.mtforum.ui.theme.dimImageInDark

@Composable
fun ProfileScreen(
    onLogin: () -> Unit,
    onDownloads: () -> Unit,
    onDrafts: () -> Unit = {},
    onMyPosts: () -> Unit = {},
    onMyFavorites: () -> Unit = {},
    onMyFriends: () -> Unit = {},
    onFollowList: (String) -> Unit = {},
    onUserClick: (String) -> Unit = {},
    onSettings: () -> Unit = {},
    onMessages: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalMtfColors.current.background)
            .statusBarsPadding(),
    ) {
        ProfileContent(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 40.dp, bottom = bottomBarContentPadding()),
            onLogin = onLogin,
            onDownloads = onDownloads,
            onDrafts = onDrafts,
            onMyPosts = onMyPosts,
            onMyFavorites = onMyFavorites,
            onMyFriends = onMyFriends,
            onFollowList = onFollowList,
            onUserClick = onUserClick,
        )

        // 仅在右上角悬浮放置「设置」与「消息」图标，不增加任何顶栏
        TabSecondaryActionIcons(
            onSettings = onSettings,
            onMessages = onMessages,
            modifier = Modifier
                .align(Alignment.TopEnd)
                // 上留白取顶栏的垂直 padding：图标中心与首页/板块顶栏图标对齐（状态栏下方 28dp）
                .padding(top = TopBarVerticalPadding, end = TopBarActionsEndPadding),
        )
    }
}

/**
 * 「我的」页主体：底栏 tab 与「跳转到自己的个人主页」共用同一份内容。
 *
 * insets 由调用方给：tab 页自己避让状态栏并预留玻璃底栏高度；个人主页页由带返回键的
 * 顶栏 Scaffold 提供，避免出现双份状态栏留白或没有返回键。
 */
@Composable
fun ProfileContent(
    modifier: Modifier = Modifier,
    onLogin: () -> Unit,
    onDownloads: () -> Unit,
    onDrafts: () -> Unit = {},
    onMyPosts: () -> Unit = {},
    onMyFavorites: () -> Unit = {},
    onMyFriends: () -> Unit = {},
    onFollowList: (String) -> Unit = {},
    onUserClick: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val viewModel: ProfileViewModel = appViewModel { ProfileViewModel(it.repository, it.contentCache, it.sessionStore) }
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()

    // 登录页返回等场景下重新拉取会话状态
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }

    val loggedIn = profile?.isLoggedIn == true

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }

    val requireLogin = { showLoginDialog = true }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        if (loading && profile == null && context.container().repository.isLoggedIn()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        // 顶部个人信息卡片（头像左上突出、右上角放置精致的登录/退出微按钮、左下用户信息、右下统计数据）
        InfoCard(
            profile = profile,
            loggedIn = loggedIn,
            onAccountAction = {
                if (loggedIn) {
                    showLogoutDialog = true
                } else {
                    showLoginDialog = true
                }
            },
            onPostsClick = { if (loggedIn) onMyPosts() else requireLogin() },
            onFollowClick = { if (loggedIn) onFollowList("following") else requireLogin() },
            onFansClick = { if (loggedIn) onFollowList("follower") else requireLogin() },
            onAvatarClick = {
                val uid = profile?.uid
                if (loggedIn && !uid.isNullOrBlank()) onUserClick(uid) else requireLogin()
            },
        )

        Spacer(Modifier.height(6.dp))

        // 功能入口卡片：横向排布「我的帖子」、「我的收藏」、「我的好友」、「草稿箱」、「离线缓存」
        val downloadCount by context.container().downloadStore.items
            .collectAsStateWithLifecycle(initialValue = emptyList())
        val draftCount by context.container().draftStore.items
            .collectAsStateWithLifecycle(initialValue = emptyList())
        ActionsCard(
            onMyPosts = { if (loggedIn) onMyPosts() else requireLogin() },
            onMyFavorites = { if (loggedIn) onMyFavorites() else requireLogin() },
            onMyFriends = { if (loggedIn) onMyFriends() else requireLogin() },
            onDownloads = onDownloads,
            onDrafts = onDrafts,
            downloadsCount = downloadCount.size,
            draftsCount = draftCount.size,
        )
    }

    if (showLogoutDialog) {
        LogoutConfirmDialog(
            username = profile?.name,
            onDismiss = { showLogoutDialog = false },
            onConfirm = {
                showLogoutDialog = false
                context.container().logout()
                viewModel.load()
            },
        )
    }

    if (showLoginDialog) {
        LoginDialog(
            onDismiss = { showLoginDialog = false },
            onSuccess = {
                showLoginDialog = false
                viewModel.load()
            },
        )
    }
}

/** 卡片 1：头像（突出一半在卡片上方）+ 名字 + UID（左侧），登录/退出按钮（右上角），帖子/关注/粉丝统计（右侧）。 */
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
    val isDark = LocalIsDarkTheme.current
    val avatarSize = 68.dp
    val avatarProtrude = 34.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            // 与访客主页资料卡同一套顶部几何：24dp 留白 + 头像突出 34dp，两页头部才等高
            .padding(top = 24.dp, bottom = 3.dp)
            .padding(top = avatarProtrude),
    ) {
        // 卡片主体
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (isDark) {
                        Modifier.border(0.5.dp, colors.divider, RoundedCornerShape(12.dp))
                    } else {
                        Modifier
                    },
                )
                .background(colors.surface)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            // 顶部一行：固定 38.dp 占位（与访客主页资料卡的顶部留空一致），左侧留空给突出头像，右上角放置典雅的登录/退出小胶囊
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(avatarSize))
                AccountActionButton(
                    loggedIn = loggedIn,
                    onClick = onAccountAction,
                )
            }

            // 主体行：左侧用户名与 UID，右侧三个统计指标
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 左侧：用户名与 UID
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onAvatarClick)
                        .padding(vertical = 2.dp),
                ) {
                    Text(
                        if (loggedIn) profile?.name.orEmpty().ifBlank { "未知用户" } else " ",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                        fontWeight = FontWeight.Bold,
                        color = bodyTextColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (loggedIn && !profile?.uid.isNullOrBlank()) "UID: ${profile?.uid}" else " ",
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
                            .height(30.dp)
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
                            .height(30.dp)
                            .padding(horizontal = 10.dp),
                        color = colors.divider,
                    )
                    StatItem(
                        label = "粉丝",
                        value = profile?.followers,
                        onClick = onFansClick,
                    )
                }
            }
        }

        // 悬浮在卡片左上方的头像（突出一半在卡片外部，未登录时为空白占位）
        Box(
            modifier = Modifier
                .padding(start = 16.dp)
                .offset(y = -avatarProtrude)
                .size(avatarSize)
                .clip(CircleShape)
                .background(colors.quoteBackground)
                .border(3.dp, colors.surface, CircleShape)
                .clickable(onClick = onAvatarClick),
        ) {
            if (loggedIn) {
                AsyncImage(
                    model = smallImageRequest(profile?.avatarUrl),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().dimImageInDark(),
                )
            }
        }
    }
}

/** 现代精致的登录/退出微型胶囊按钮。 */
@Composable
private fun AccountActionButton(
    loggedIn: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalMtfColors.current
    if (loggedIn) {
        // 已登录：低调优雅的小胶囊退出按钮
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(colors.quoteBackground)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_nav_logout),
                contentDescription = "退出登录",
                tint = colors.textSecondary,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "退出",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                color = colors.textSecondary,
                fontWeight = FontWeight.Medium,
            )
        }
    } else {
        // 未登录：醒目高亮的小胶囊登录按钮
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_nav_login),
                contentDescription = "登录",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "登录",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
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
            .padding(horizontal = 2.dp, vertical = 4.dp),
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

/** 卡片 2：功能入口横向排列（我的帖子、我的收藏、我的好友、草稿箱、离线缓存）。 */
@Composable
private fun ActionsCard(
    onMyPosts: () -> Unit,
    onMyFavorites: () -> Unit,
    onMyFriends: () -> Unit,
    onDownloads: () -> Unit,
    onDrafts: () -> Unit,
    downloadsCount: Int,
    draftsCount: Int,
) {
    ProfileCard(contentPadding = 14.dp) {
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
            ActionEntranceButton(
                icon = painterResource(R.drawable.ic_post_edit),
                title = "草稿箱",
                backgroundColor = Color(0xFF8B5CF6),
                badgeCount = draftsCount,
                onClick = onDrafts,
                modifier = Modifier.weight(1f),
            )
            ActionEntranceButton(
                icon = painterResource(R.drawable.ic_download),
                title = "离线缓存",
                backgroundColor = Color(0xFF10B981),
                badgeCount = downloadsCount,
                onClick = onDownloads,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 单个彩色大圆图标入口按钮（带可选小角标）。 */
@Composable
private fun ActionEntranceButton(
    icon: Painter,
    title: String,
    backgroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
) {
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current
    val actualBg = if (isDark) backgroundColor.copy(alpha = 0.22f) else backgroundColor
    val iconTint = if (isDark) backgroundColor else Color.White

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Box {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(actualBg),
            ) {
                Icon(
                    painter = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp),
                )
            }
            if (badgeCount > 0) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-2).dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp),
            fontWeight = FontWeight.Medium,
            color = bodyTextColor(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 统一的卡片外观：左右 6dp、间距 6dp、12dp 圆角。 */
@Composable
private fun ProfileCard(
    contentPadding: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isDark) {
                    Modifier.border(0.5.dp, colors.divider, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                },
            )
            .background(colors.surface)
            .padding(contentPadding),
    ) {
        content()
    }
}
