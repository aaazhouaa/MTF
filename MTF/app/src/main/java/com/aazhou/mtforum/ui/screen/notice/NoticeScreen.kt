package com.aazhou.mtforum.ui.screen.notice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aazhou.mtforum.NoticeNotifier
import com.aazhou.mtforum.NoticePollWorker
import com.aazhou.mtforum.R
import com.aazhou.mtforum.data.model.NoticeGroup
import com.aazhou.mtforum.data.repo.NoticeRepo
import com.aazhou.mtforum.ui.common.AppToast
import com.aazhou.mtforum.ui.common.MtfSwitchRow
import com.aazhou.mtforum.ui.common.MtfTopBarHeight
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.common.bottomBarContentPadding
import com.aazhou.mtforum.ui.common.container
import com.aazhou.mtforum.ui.common.onDoubleTapToTop
import com.aazhou.mtforum.ui.common.scrollToTop
import com.aazhou.mtforum.ui.screen.login.LoginDialog
import com.aazhou.mtforum.ui.theme.LocalIsDarkTheme
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.bodyTextColor
import kotlinx.coroutines.launch

/**
 * 消息提醒一级页：对应网页端「消息提醒」入口（home.php?mod=space&do=notice）。
 *
 * 采用现代大气的卡片分组式设计，包含六大类消息导航与后台轮询提醒控制。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticeScreen(
    onBack: () -> Unit,
    onGroupClick: (NoticeGroup) -> Unit,
    onFansClick: () -> Unit,
) {
    val colors = LocalMtfColors.current
    val context = LocalContext.current
    val container = context.container()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val viewModel: NoticeViewModel = appViewModel {
        NoticeViewModel(
            it.repository,
            NoticeRepo(it.httpClient),
            it.noticeUnreadStore,
            it.noticePollStore,
            onRead = { ids -> it.markNoticesRead(ids) },
        )
    }
    val unreadTabs by viewModel.unreadTabs.collectAsStateWithLifecycle()
    val pollEnabled by viewModel.pollEnabled.collectAsStateWithLifecycle()
    var showLoginDialog by remember { mutableStateOf(false) }

    var pendingEnable by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val want = pendingEnable
        pendingEnable = false
        if (granted && want) {
            viewModel.setPollEnabled(true)
            NoticePollWorker.enqueue(context)
        } else if (!granted && want) {
            AppToast.show("未授予通知权限，无法开启后台提醒")
        }
    }

    fun togglePoll(want: Boolean) {
        if (!want) {
            viewModel.setPollEnabled(false)
            NoticePollWorker.cancel(context)
            NoticeNotifier.cancel(context)
            return
        }
        if (!container.isLoggedIn()) {
            AppToast.show("请先登录后再开启后台提醒")
            return
        }
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.setPollEnabled(true)
            NoticePollWorker.enqueue(context)
        } else {
            pendingEnable = true
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        containerColor = colors.background,
        contentColor = bodyTextColor(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.topBar),
                title = {
                    Text(
                        text = "消息提醒",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                        ),
                        color = bodyTextColor(),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = "返回",
                            tint = bodyTextColor(),
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onDoubleTapToTop { scope.launch { listState.scrollToTop() } },
                expandedHeight = MtfTopBarHeight,
            )
        },
    ) { padding ->
        val isLoggedIn = container.isLoggedIn()

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = 14.dp,
                end = 14.dp,
                top = 12.dp,
                bottom = bottomBarContentPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 分组 1：消息与互动
            item(key = "notice-group-messages") {
                NoticeCardGroup(
                    title = "消息与互动",
                ) {
                    val messageGroups = listOf(
                        NoticeGroup.PM,
                        NoticeGroup.MY_POST,
                        NoticeGroup.INTERACTIVE,
                    )
                    messageGroups.forEachIndexed { index, group ->
                        val unreadCount = unreadTabs.count { it.group == group }
                        NoticeMenuRow(
                            group = group,
                            unreadCount = unreadCount,
                            onClick = {
                                if (isLoggedIn) {
                                    onGroupClick(group)
                                } else {
                                    showLoginDialog = true
                                }
                            },
                        )
                        if (index < messageGroups.lastIndex) {
                            HorizontalDivider(
                                color = colors.divider.copy(alpha = 0.5f),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 66.dp, end = 14.dp),
                            )
                        }
                    }
                }
            }

            // 分组 2：系统与动态
            item(key = "notice-group-system") {
                NoticeCardGroup(
                    title = "系统与动态",
                ) {
                    val systemGroups = listOf(
                        NoticeGroup.SYSTEM,
                        NoticeGroup.APP,
                        NoticeGroup.FANS,
                    )
                    systemGroups.forEachIndexed { index, group ->
                        val unreadCount = unreadTabs.count { it.group == group }
                        NoticeMenuRow(
                            group = group,
                            unreadCount = unreadCount,
                            onClick = {
                                when (group) {
                                    NoticeGroup.FANS -> onFansClick()
                                    else -> if (isLoggedIn) {
                                        onGroupClick(group)
                                    } else {
                                        showLoginDialog = true
                                    }
                                }
                            },
                        )
                        if (index < systemGroups.lastIndex) {
                            HorizontalDivider(
                                color = colors.divider.copy(alpha = 0.5f),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 66.dp, end = 14.dp),
                            )
                        }
                    }
                }
            }

            // 分组 3：后台消息提醒设置卡片
            item(key = "poll-switch-card") {
                NoticeCardGroup(
                    title = "设置",
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        MtfSwitchRow(
                            title = "后台消息提醒",
                            subtitle = "开启后每 15 分钟在后台检查一次，有新消息发系统通知；关闭时仅在打开应用或提醒页时检查",
                            checked = pollEnabled,
                            onCheckedChange = { togglePoll(it) },
                        )
                        Text(
                            text = "注：论坛未提供在线推送通道，后台检查需定时发起网络请求，默认关闭以节省电量与流量。",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                            ),
                            color = colors.textSecondary.copy(alpha = 0.85f),
                            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                        )
                    }
                }
            }
        }
    }

    if (showLoginDialog) {
        LoginDialog(
            onDismiss = { showLoginDialog = false },
            onSuccess = {
                showLoginDialog = false
                container.refreshCurrentUserAsync()
                viewModel.prefetch()
            },
        )
    }
}

/** 圆角卡片容器组件 */
@Composable
private fun NoticeCardGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface)
                .then(
                    if (isDark) {
                        Modifier.border(0.5.dp, colors.divider, RoundedCornerShape(16.dp))
                    } else {
                        Modifier
                    },
                ),
        ) {
            content()
        }
    }
}

/** 分类菜单项：圆角微渐变图标 + 标题与副标题 + 主题色未读小圆点 + 箭头指示 */
@Composable
private fun NoticeMenuRow(
    group: NoticeGroup,
    unreadCount: Int,
    onClick: () -> Unit,
) {
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val style = group.visualStyle(isDark)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        // 图标容器：40.dp 柔和雅致微彩底 + 现代矢量图标
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(style.bgColor),
        ) {
            Icon(
                painter = painterResource(group.iconRes()),
                contentDescription = null,
                tint = style.iconTint,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.width(12.dp))

        // 文字区域：标题与副标题
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = bodyTextColor(),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = group.description(),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                ),
                color = colors.textSecondary,
            )
        }

        // 右侧未读指示：主题色小圆点
        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(primaryColor),
            )
        }

        // 浅灰色右向箭头指示
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = colors.textHint.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 各分类的副标题说明 */
private fun NoticeGroup.description(): String = when (this) {
    NoticeGroup.PM -> "私聊会话与公共消息"
    NoticeGroup.MY_POST -> "回复、点评与提到我的"
    NoticeGroup.INTERACTIVE -> "打招呼、留言与好友互动"
    NoticeGroup.SYSTEM -> "系统公告、管理与违规提醒"
    NoticeGroup.APP -> "论坛道具、插件与活动动态"
    NoticeGroup.FANS -> "关注我的坛友与粉丝列表"
}

/** 一级菜单各项的现代专属矢量图标 */
private fun NoticeGroup.iconRes(): Int = when (this) {
    NoticeGroup.PM -> R.drawable.ic_notice_pm
    NoticeGroup.MY_POST -> R.drawable.ic_notice_mypost
    NoticeGroup.INTERACTIVE -> R.drawable.ic_notice_interactive
    NoticeGroup.SYSTEM -> R.drawable.ic_notice_system
    NoticeGroup.APP -> R.drawable.ic_notice_app
    NoticeGroup.FANS -> R.drawable.ic_notice_fans
}

/** 视觉风格配置 */
private data class GroupVisualStyle(
    val bgColor: Color,
    val iconTint: Color,
)

/** 各分类根据深浅色自适应的专属轻色彩体系 */
private fun NoticeGroup.visualStyle(isDark: Boolean): GroupVisualStyle = if (isDark) {
    when (this) {
        NoticeGroup.PM -> GroupVisualStyle(Color(0xFF16253B), Color(0xFF60A5FA))
        NoticeGroup.MY_POST -> GroupVisualStyle(Color(0xFF332617), Color(0xFFFBBF24))
        NoticeGroup.INTERACTIVE -> GroupVisualStyle(Color(0xFF361C24), Color(0xFFFB7185))
        NoticeGroup.SYSTEM -> GroupVisualStyle(Color(0xFF142C20), Color(0xFF34D399))
        NoticeGroup.APP -> GroupVisualStyle(Color(0xFF2B183E), Color(0xFFA78BFA))
        NoticeGroup.FANS -> GroupVisualStyle(Color(0xFF352216), Color(0xFFFB923C))
    }
} else {
    when (this) {
        NoticeGroup.PM -> GroupVisualStyle(Color(0xFFEBF3FF), Color(0xFF2563EB))
        NoticeGroup.MY_POST -> GroupVisualStyle(Color(0xFFFFF7E6), Color(0xFFD97706))
        NoticeGroup.INTERACTIVE -> GroupVisualStyle(Color(0xFFFFEEF3), Color(0xFFE11D48))
        NoticeGroup.SYSTEM -> GroupVisualStyle(Color(0xFFE8F8F0), Color(0xFF059669))
        NoticeGroup.APP -> GroupVisualStyle(Color(0xFFF3E8FF), Color(0xFF7C3AED))
        NoticeGroup.FANS -> GroupVisualStyle(Color(0xFFFFF0E6), Color(0xFFEA580C))
    }
}
