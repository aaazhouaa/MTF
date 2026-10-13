package com.aazhou.mtforum.ui.screen.notice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.aazhou.mtforum.ui.screen.login.LoginDialog
import com.aazhou.mtforum.ui.theme.LocalIsDarkTheme
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.accentTextColor
import com.aazhou.mtforum.ui.theme.bodyTextColor

/**
 * 消息提醒一级页：对应网页端「消息提醒」入口（home.php?mod=space&do=notice）。
 *
 * 网页那一页只是个分类菜单，点进去才是各子类的列表，这里照做：菜单 6 项 + 底部
 * 后台轮询开关。「我的粉丝」在站点里也挂在这个菜单下，但不是提醒数据，跳到关注/粉丝页。
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

    // 开开关时要通知权限：Android 13+ 没有 POST_NOTIFICATIONS 就发不出系统通知。
    // 默认关闭时不再一进页面就弹申请，改成用户主动开启时再问。
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
            // 关掉后别在通知栏留着一条数不对的「N 条新消息提醒」
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
        containerColor = colors.topBar,
        contentColor = bodyTextColor(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.topBar),
                title = { Text("消息提醒") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_back),
                            contentDescription = "返回",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                expandedHeight = MtfTopBarHeight,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = bottomBarContentPadding()),
        ) {
            items(NoticeGroup.entries.toList()) { group ->
                NoticeMenuRow(
                    group = group,
                    hasUnread = group in unreadTabs.map { it.group }.toSet(),
                    onClick = {
                        when (group) {
                            NoticeGroup.FANS -> onFansClick()
                            else -> if (container.isLoggedIn()) {
                                onGroupClick(group)
                            } else {
                                showLoginDialog = true
                            }
                        }
                    },
                )
                HorizontalDivider(color = colors.divider.copy(alpha = 0.6f), thickness = 0.5.dp)
            }
            item(key = "poll-switch") {
                Surface(color = colors.background) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        MtfSwitchRow(
                            title = "后台消息提醒",
                            subtitle = "开启后每 15 分钟在后台检查一次，有新消息发通知；" +
                                "关闭时只在打开 App 或消息页时检查",
                            checked = pollEnabled,
                            onCheckedChange = { togglePoll(it) },
                        )
                        Text(
                            text = "站点没有推送服务，后台检查是客户端自己定时发请求，" +
                                "默认关闭；开启后更费电与流量。",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
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

/** 一级菜单的一项：圆角图标 + 分类名，右上角圆点表示该分类下有未读子类。 */
@Composable
private fun NoticeMenuRow(
    group: NoticeGroup,
    hasUnread: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current
    val primaryColor = MaterialTheme.colorScheme.primary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.quoteBackground),
        ) {
            Icon(
                painter = painterResource(group.iconRes()),
                contentDescription = null,
                tint = accentTextColor(),
                modifier = Modifier.size(20.dp),
            )
            if (hasUnread) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(primaryColor),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = group.title,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = bodyTextColor(),
        )
    }
}

/** 一级菜单各项的图标；「应用提醒」与「我的粉丝」没有专属图标，用近义向量图顶位。 */
private fun NoticeGroup.iconRes(): Int = when (this) {
    NoticeGroup.PM -> R.drawable.ic_notice_pm
    NoticeGroup.FANS -> R.drawable.ic_people
    NoticeGroup.MY_POST -> R.drawable.ic_notice_mypost
    NoticeGroup.INTERACTIVE -> R.drawable.ic_notice_interactive
    NoticeGroup.SYSTEM -> R.drawable.ic_notice_system
    NoticeGroup.APP -> R.drawable.ic_card_view
}
