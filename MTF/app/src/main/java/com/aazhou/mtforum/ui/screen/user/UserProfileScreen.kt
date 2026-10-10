package com.aazhou.mtforum.ui.screen.user

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.aazhou.mtforum.R
import com.aazhou.mtforum.data.model.UserProfile
import com.aazhou.mtforum.image.smallImageRequest
import com.aazhou.mtforum.ui.common.ErrorBox
import com.aazhou.mtforum.ui.common.LoadingBox
import com.aazhou.mtforum.ui.common.MtfTopBarHeight
import com.aazhou.mtforum.ui.common.UiState
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.common.onDoubleTapToTop
import com.aazhou.mtforum.ui.common.scrollToTop
import com.aazhou.mtforum.ui.theme.LocalIsDarkTheme
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.bodyTextColor
import com.aazhou.mtforum.ui.theme.dimImageInDark
import kotlinx.coroutines.launch

/** 用户主页：图二风格卡片 + 关注、加好友、聊聊天、拉黑ta 操作项。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    uid: String,
    onBack: () -> Unit,
) {
    val colors = LocalMtfColors.current
    val viewModel: UserProfileViewModel = appViewModel {
        UserProfileViewModel(uid, it.repository, it.sessionStore, it.actionStore)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isSelf by viewModel.isSelf.collectAsStateWithLifecycle()
    val isFollowing by viewModel.isFollowing.collectAsStateWithLifecycle()
    val isOperating by viewModel.isOperating.collectAsStateWithLifecycle()

    var showAddFriendDialog by remember { mutableStateOf(false) }
    var showChatDialog by remember { mutableStateOf(false) }
    var showBlockDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = colors.background,
        contentColor = bodyTextColor(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.topBar),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_back),
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
                modifier = Modifier.onDoubleTapToTop {
                    scope.launch { scrollState.scrollToTop() }
                },
                expandedHeight = MtfTopBarHeight,
            )
        },
    ) { padding ->
        when (val current = state) {
            is UiState.Loading -> LoadingBox(Modifier.padding(padding))
            is UiState.Error -> ErrorBox(current.message, { viewModel.load() }, Modifier.padding(padding))
            is UiState.Success -> {
                val profile = current.data
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 14.dp, vertical = 24.dp),
                ) {
                    UserHeaderCard(profile = profile)

                    Spacer(Modifier.height(18.dp))

                    UserActionList(
                        isSelf = isSelf,
                        isFollowing = isFollowing,
                        isOperating = isOperating,
                        onToggleFollow = { viewModel.toggleFollow() },
                        onAddFriend = { showAddFriendDialog = true },
                        onChat = { showChatDialog = true },
                        onBlock = { showBlockDialog = true },
                    )
                }

                if (showAddFriendDialog) {
                    AddFriendDialog(
                        targetName = profile.name.orEmpty().ifBlank { "该用户" },
                        isSubmitting = isOperating,
                        onDismiss = { showAddFriendDialog = false },
                        onConfirm = { note ->
                            viewModel.addFriend(note) {
                                showAddFriendDialog = false
                            }
                        },
                    )
                }

                if (showChatDialog) {
                    ChatDialog(
                        targetName = profile.name.orEmpty().ifBlank { "该用户" },
                        isSubmitting = isOperating,
                        onDismiss = { showChatDialog = false },
                        onConfirm = { msg ->
                            viewModel.sendPm(msg) {
                                showChatDialog = false
                            }
                        },
                    )
                }

                if (showBlockDialog) {
                    AlertDialog(
                        onDismissRequest = { showBlockDialog = false },
                        title = { Text("加入黑名单") },
                        text = {
                            Text(
                                "确定将 ${profile.name.orEmpty().ifBlank { "该用户" }} 加入黑名单吗？\n加入后，对方将无法与您互动。",
                                color = colors.textSecondary,
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    viewModel.blockUser {
                                        showBlockDialog = false
                                    }
                                },
                            ) {
                                Text("确定拉黑", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showBlockDialog = false }) {
                                Text("取消", color = colors.textSecondary)
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * 样式对齐图二：
 * - 圆角白色卡片，左上方悬浮突出一半的圆形头像；
 * - 顶部留空（不带登录/退出按钮）；
 * - 主体行：左侧用户名与 UID，右侧三个统计指标（帖子、关注、粉丝），黄/金黄色，细竖线分割。
 */
@Composable
private fun UserHeaderCard(profile: UserProfile) {
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current
    val avatarSize = 64.dp
    val avatarProtrude = 22.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = avatarProtrude),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .then(
                    if (isDark) {
                        Modifier.border(0.5.dp, colors.divider, RoundedCornerShape(14.dp))
                    } else {
                        Modifier
                    },
                )
                .background(colors.surface)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            // 顶部高度占位（保持图二中头像突出对应的间隙，无登录退出按钮）
            Spacer(Modifier.height(18.dp))

            // 主体行：左侧用户名与 UID，右侧三个统计指标
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 左侧：用户名与 UID
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 2.dp),
                ) {
                    Text(
                        profile.name.orEmpty().ifBlank { "未知用户" },
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                        fontWeight = FontWeight.Bold,
                        color = bodyTextColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (!profile.uid.isNullOrBlank()) "UID: ${profile.uid}" else "UID: -",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.textSecondary,
                    )
                }

                // 右侧：三个统计指标（帖子、关注、粉丝），黄/金黄色突出，竖线分割
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 12.dp),
                ) {
                    StatItem(
                        label = "帖子",
                        value = profile.threads,
                    )
                    VerticalDivider(
                        modifier = Modifier
                            .height(30.dp)
                            .padding(horizontal = 10.dp),
                        color = colors.divider,
                    )
                    StatItem(
                        label = "关注",
                        value = profile.following,
                    )
                    VerticalDivider(
                        modifier = Modifier
                            .height(30.dp)
                            .padding(horizontal = 10.dp),
                        color = colors.divider,
                    )
                    StatItem(
                        label = "粉丝",
                        value = profile.followers,
                    )
                }
            }
        }

        // 悬浮在卡片左上方的头像（突出一半在卡片外部）
        Box(
            modifier = Modifier
                .padding(start = 16.dp)
                .offset(y = -avatarProtrude)
                .size(avatarSize)
                .clip(CircleShape)
                .background(colors.quoteBackground)
                .border(3.dp, colors.surface, CircleShape),
        ) {
            AsyncImage(
                model = smallImageRequest(profile.avatarUrl),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().dimImageInDark(),
            )
        }
    }
}

/** 统计单元：上面黄色/金色大数字，下面灰色小字。 */
@Composable
private fun StatItem(
    label: String,
    value: Int?,
) {
    val colors = LocalMtfColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
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

/** 操作项列表（卡片内分组）：关注、加好友、聊聊天、拉黑ta。 */
@Composable
private fun UserActionList(
    isSelf: Boolean,
    isFollowing: Boolean,
    isOperating: Boolean,
    onToggleFollow: () -> Unit,
    onAddFriend: () -> Unit,
    onChat: () -> Unit,
    onBlock: () -> Unit,
) {
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (isDark) {
                    Modifier.border(0.5.dp, colors.divider, RoundedCornerShape(14.dp))
                } else {
                    Modifier
                },
            )
            .background(colors.surface),
    ) {
        // 自己的主页没有关注按钮
        if (!isSelf) {
            ActionRowItem(
                icon = if (isFollowing) R.drawable.ic_check else R.drawable.ic_add,
                iconTint = if (isFollowing) colors.textSecondary else MaterialTheme.colorScheme.primary,
                title = if (isFollowing) "已关注" else "关注",
                subtitle = if (isFollowing) "点击取消关注" else "关注该用户",
                onClick = onToggleFollow,
                enabled = !isOperating,
            )
            HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 16.dp))
        }

        ActionRowItem(
            icon = R.drawable.ic_friends,
            iconTint = Color(0xFF2196F3),
            title = "加好友",
            subtitle = "发送好友申请",
            onClick = onAddFriend,
            enabled = !isOperating,
        )

        HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 16.dp))

        ActionRowItem(
            icon = R.drawable.ic_notice_pm,
            iconTint = Color(0xFF4CAF50),
            title = "聊聊天",
            subtitle = "发送私信消息",
            onClick = onChat,
            enabled = !isOperating,
        )

        HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 16.dp))

        ActionRowItem(
            icon = R.drawable.ic_forbid,
            iconTint = MaterialTheme.colorScheme.error,
            title = "拉黑ta",
            subtitle = "将用户加入黑名单",
            onClick = onBlock,
            enabled = !isOperating,
        )
    }
}

@Composable
private fun ActionRowItem(
    icon: Int,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val colors = LocalMtfColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                fontWeight = FontWeight.Medium,
                color = bodyTextColor(),
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textHint,
            )
        }

        Icon(
            painter = painterResource(R.drawable.ic_arrow_right),
            contentDescription = null,
            tint = colors.textHint,
            modifier = Modifier.size(14.dp),
        )
    }
}

/** 加好友弹窗（支持输入附言）。 */
@Composable
private fun AddFriendDialog(
    targetName: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (note: String) -> Unit,
) {
    var note by remember { mutableStateOf("") }
    val colors = LocalMtfColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加好友") },
        text = {
            Column {
                Text(
                    "申请添加 $targetName 为好友：",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text("附言（选填，如：我是...）") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(note) },
                enabled = !isSubmitting,
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text("发送申请")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("取消", color = colors.textSecondary)
            }
        },
    )
}

/** 聊聊天快速私信弹窗。 */
@Composable
private fun ChatDialog(
    targetName: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (message: String) -> Unit,
) {
    var message by remember { mutableStateOf("") }
    val colors = LocalMtfColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发私信") },
        text = {
            Column {
                Text(
                    "给 $targetName 发送私信：",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    placeholder = { Text("说点什么吧...") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(message) },
                enabled = !isSubmitting && message.isNotBlank(),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text("发送")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("取消", color = colors.textSecondary)
            }
        },
    )
}
