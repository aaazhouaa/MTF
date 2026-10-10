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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
fun ProfileScreen(onLogin: () -> Unit, onDownloads: () -> Unit) {
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
        )
        // 已下载的帖子入口
        val downloadCount by context.container().downloadStore.items
            .collectAsStateWithLifecycle(initialValue = emptyList())
        DownloadsCard(onOpen = onDownloads, count = downloadCount.size)
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
                    painterResource(com.aazhou.mtforum.R.drawable.ic_download),
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
                painterResource(com.aazhou.mtforum.R.drawable.ic_arrow_right),
                contentDescription = null,
                tint = colors.textHint,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** 卡片 1：头像 + 名字 + UID + 登录/登出。 */
@Composable
private fun InfoCard(
    profile: UserProfile?,
    loggedIn: Boolean,
    onAccountAction: () -> Unit,
) {
    val colors = LocalMtfColors.current
    ProfileCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 未登录也占头像位：底色取卡片同色，未登录时与卡片浑然一体；已登录仍是
            // 头像加载中的浅灰底
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(if (loggedIn) colors.quoteBackground else colors.surface),
            ) {
                if (loggedIn) {
                    AsyncImage(
                        model = smallImageRequest(profile?.avatarUrl),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().dimImageInDark(),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (loggedIn) profile?.name.orEmpty().ifBlank { "未知用户" } else "未登录",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                    fontWeight = FontWeight.Bold,
                    color = bodyTextColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        if (loggedIn) "UID: ${profile?.uid.orEmpty()}" else "登录后查看个人资料",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.textSecondary,
                    )
                }
            }
            // 登录/登出动图：WebP 已把白底抠透明，直接落在卡片上，
            // 深色主题下也是图标自身的颜色（GIF 只有 1 位透明，会锯齿，故用 WebP 承载 alpha）
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .clickable(onClick = onAccountAction),
            ) {
                AsyncImage(
                    model = accountIconModel(logout = loggedIn),
                    contentDescription = if (loggedIn) "退出登录" else "登录",
                    modifier = Modifier.size(42.dp),
                )
            }
        }
    }
}

/** 统一的白色卡片外观：左右 6dp、间距 6dp、12dp 圆角。 */
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
