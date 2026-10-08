package com.aazhou.mtforum.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.aazhou.mtforum.R
import android.view.View
import com.aazhou.mtforum.data.network.HttpClient
import com.aazhou.mtforum.image.forumMediaClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 全屏视频播放：黑底 + 内置控制条，顶部提供返回 / 保存 / 分享。
 *
 * 数据源走 [forumMediaClient]：附件 CDN 校验 UA，隐藏附件的视频还要求会话 Cookie，
 * OkHttp 拦截器每次请求实时取当前 cookie，登录态变化即时生效。
 */
@Composable
fun VideoPlayerScreen(url: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val player = remember(url) {
        val httpClient = context.container().httpClient
        val dataSourceFactory = OkHttpDataSource.Factory(
            forumMediaClient(
                userAgent = HttpClient.MOBILE_UA,
                cookieHeader = { httpClient.cookieHeader() },
            ),
        ).setUserAgent(HttpClient.MOBILE_UA)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(url))
                playWhenReady = true
                prepare()
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        failed = true
                    }
                })
            }
    }

    // 息屏/切后台时暂停，离开页面立即释放：播放器持有解码器与网络连接，泄漏会拖垮列表页
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.pause()
                Lifecycle.Event.ON_DESTROY -> player.release()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    // 客户端只做竖屏内嵌，控制条自带的全屏按钮点击无对应行为，直接隐藏
                    findViewById<View>(androidx.media3.ui.R.id.exo_fullscreen)?.visibility = View.GONE
                }
            },
            onRelease = { view -> view.player = null },
            modifier = Modifier.fillMaxSize(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0x66000000))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_back),
                    contentDescription = "返回",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = fileNameOf(url, "mp4"),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x66000000))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
            Spacer(Modifier.weight(1f))
            VideoActionIcon(R.drawable.ic_download, "保存到相册") {
                runBusy(scope, { busy = it }, "保存失败") {
                    withContext(Dispatchers.IO) { saveVideoToGallery(context, url) } != null
                }
            }
            Spacer(Modifier.width(8.dp))
            VideoActionIcon(R.drawable.ic_share, "分享") {
                runBusy(scope, { busy = it }, "分享失败") {
                    val file = withContext(Dispatchers.IO) { writeVideoCacheFile(context, url) }
                    if (file == null) {
                        false
                    } else {
                        withContext(Dispatchers.Main) { shareVideo(context, url, file) }
                        true
                    }
                }
            }
        }

        if (busy) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).size(32.dp),
            )
        }
        if (failed) {
            Text(
                "视频播放失败",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/** 顶部操作图标：圆形暗色底衬，与全屏图片查看器同规格。 */
@Composable
private fun VideoActionIcon(iconRes: Int, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(0x66000000))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            painterResource(iconRes),
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )    }
}
