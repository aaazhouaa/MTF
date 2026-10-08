package com.aazhou.mtforum.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aazhou.mtforum.R
import com.aazhou.mtforum.data.model.Thread
import com.aazhou.mtforum.ui.common.container
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.MtfColors
import com.aazhou.mtforum.ui.theme.bodyTextColor
import com.aazhou.mtforum.ui.theme.dimImageInDark

/**
 * 帖子卡片流：作者行（头像/昵称/等级/时间/版块）+ 标题 + 摘要 + 封面图 + 三项统计。
 *
 * 首页、版块内主题列表、搜索结果三处列表共用同一套卡片，保证不同入口的帖子观感一致。
 * [showForumName] 为 false 时不显示作者行右端的版块 chip——列表本身已限定在单个版块内时，
 * 每张卡都重复同一个版块名没有信息量。
 * 作者行内只有昵称/时间列吃 weight：chip 取自身宽度才落在右端，并与昵称同行（见下方注释）。
 */
@Composable
fun ThreadCard(
    thread: Thread,
    onClick: () -> Unit,
    onImageClick: (List<String>, Int) -> Unit,
    onUserClick: (String) -> Unit,
    showForumName: Boolean = true,
) {
    val colors = LocalMtfColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.5.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            // 内容左界必须落在 16dp（6dp 外边距 + 10dp 内边距）：与详情页标题/正文、
            // 评论列表、首页顶栏的 16dp 是同一条线；此前 14dp 内边距会把卡片内容
            // 整体推到 20dp，同一帖子在列表里比详情页标题多缩进 4dp
            .padding(horizontal = 10.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = thread.avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .dimImageInDark()
                    .userClick(thread.authorUid, onUserClick),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    thread.authorName ?: "匿名",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    fontWeight = FontWeight.Bold,
                    color = bodyTextColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.userClick(thread.authorUid, onUserClick),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.5.dp)) {
                    Text(
                        thread.timeText.orEmpty(),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = colors.textSecondary,
                    )
                }
            }
            // 版块 chip 贴作者行右端、与昵称同高：
            // 1) 该行只给昵称/时间列 weight，chip 取自身宽度，才能真的落到右端——
            //    给 chip 也加 weight(fill=false) 时，它让出的剩余空间不会回流给前面的列，
            //    胶囊会停在卡片中间，右侧空一片；
            // 2) align(Top) 覆盖行的 CenterVertically，否则 24dp 的胶囊会被压到
            //    昵称与时间两行之间；
            // 3) 96dp 上限兜住超长版名（省略号截断），昵称列始终留有空间。
            if (showForumName && !thread.forumName.isNullOrBlank()) {
                Spacer(Modifier.width(8.dp))
                ForumNameChip(
                    thread.forumName,
                    colors,
                    Modifier.align(Alignment.Top).widthIn(max = 96.dp),
                )
            }
        }
        Text(
            thread.subject,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
            fontWeight = FontWeight.Bold,
            color = bodyTextColor(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 9.dp),
        )
        if (!thread.excerpt.isNullOrBlank()) {
            Text(
                thread.excerpt,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
        CardImages(thread.imageUrls, onImageClick)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 11.dp),
        ) {
            StatItem(painterResource(R.drawable.ic_card_view), formatCount(thread.viewCount))
            Spacer(Modifier.width(24.dp))
            StatItem(painterResource(R.drawable.ic_reply), formatCount(thread.replyCount))
            Spacer(Modifier.width(24.dp))
            StatItem(painterResource(R.drawable.ic_like_thumb), formatCount(thread.likeCount))
        }
    }
}

/** 版块名 chip：挂在作者行右端，细描边 + 10sp 小字，长版名单行省略。 */
@Composable
private fun ForumNameChip(name: String, colors: MtfColors, modifier: Modifier = Modifier) {
    Text(
        name,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
        color = colors.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .border(
                0.1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.69f),
                RoundedCornerShape(50),
            )
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/**
 * 帖子卡片单张配图：满宽、固定 155dp 舒适高度，CenterCrop 裁切，圆角 + 细描边。
 */
@Composable
private fun CardSingleImage(url: String, onClick: () -> Unit) {
    val colors = LocalMtfColors.current
    val shape = RoundedCornerShape(10.dp)
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .height(155.dp)
            .clip(shape)
            .background(colors.quoteBackground)
            .dimImageInDark()
            .border(1.dp, colors.divider, shape)
            .clickable(onClick = onClick),
    )
}

/**
 * 卡片多图排布：按张数切换行数、行高、间距与圆角，前 3 张即用，其余张数进右上角角标。
 *
 * 手工 Row/Column 布局而非 Lazy 网格：卡片本身在 LazyColumn 里，嵌套懒布局会互相抢复用。
 */
@Composable
private fun CardImageGrid(urls: List<String>, onImageClick: (List<String>, Int) -> Unit) {
    val colors = LocalMtfColors.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (urls.size) {
            2 -> Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                urls.forEachIndexed { index, url ->
                    CardImage(
                        url,
                        cardCellModifier(colors, height = 130.dp, shape = RoundedCornerShape(6.dp)) {
                            onImageClick(urls, index)
                        },
                    )
                }
            }

            3 -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                urls.forEachIndexed { index, url ->
                    CardImage(
                        url,
                        cardCellModifier(colors, height = 100.dp, shape = RoundedCornerShape(6.dp)) {
                            onImageClick(urls, index)
                        },
                    )
                }
            }

            4 -> for (row in 0 until 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (column in 0 until 2) {
                        val index = row * 2 + column
                        CardImage(
                            urls[index],
                            cardCellModifier(colors, height = 98.dp, shape = RoundedCornerShape(6.dp)) {
                                onImageClick(urls, index)
                            },
                        )
                    }
                }
            }

            else -> Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                urls.take(3).forEachIndexed { position, url ->
                    val showBadge = position == 2 && urls.size > 3
                    val modifier = cardCellModifier(colors, height = 115.dp, shape = RoundedCornerShape(8.dp)) {
                        onImageClick(urls, position)
                    }
                    if (showBadge) {
                        Box(modifier = modifier) {
                            CardImage(url, Modifier.fillMaxSize())
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 4.dp, end = 4.dp)
                                    .background(Color(0x99000000), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    "${urls.size}图",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                        }
                    } else {
                        CardImage(url, modifier)
                    }
                }
            }
        }
    }
}

/** 网格单格外观：等宽、固定行高、圆角裁剪 + 占位底色 + 细描边 + 点击。 */
private fun RowScope.cardCellModifier(
    colors: MtfColors,
    height: Dp,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
): Modifier = Modifier
    .weight(1f)
    .height(height)
    .clip(shape)
    .background(colors.quoteBackground)
    .border(1.dp, colors.divider, shape)
    .clickable(onClick = onClick)

/** 单格配图：网格与角标格共用，圆角/底色/描边由传入的 modifier 负责。 */
@Composable
private fun CardImage(url: String, modifier: Modifier) {
    val context = LocalContext.current
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onError = { state ->
            context.container().logger.error(
                "Image",
                "帖子卡片配图加载失败 url=$url",
                state.result.throwable,
            )
        },
        modifier = modifier.dimImageInDark(),
    )
}

/**
 * 帖子卡片配图区：单图满宽大图，多图按张数排网格。
 */
@Composable
private fun CardImages(
    urls: List<String>,
    onImageClick: (List<String>, Int) -> Unit,
) {
    when (urls.size) {
        0 -> Unit
        1 -> CardSingleImage(urls.first()) { onImageClick(urls, 0) }
        else -> CardImageGrid(urls, onImageClick)
    }
}

/** 卡片底部统计项：图标 + 数值，数值为空时不占位。 */
@Composable
private fun StatItem(icon: Painter, count: String?) {
    val colors = LocalMtfColors.current
    if (count.isNullOrBlank()) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = colors.iconSecondary,
            modifier = Modifier.size(14.dp),
        )
        Text(
            count,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = colors.textSecondary,
        )
    }
}

/** 统计数字取整到千/万位：1200 -> "1k"、12345 -> "1w"；非数字原样返回。 */
private fun formatCount(raw: String?): String? {
    val count = raw?.trim()?.toIntOrNull() ?: return raw?.takeIf { it.isNotBlank() }
    return when {
        count >= 10_000 -> "${count / 10_000}w"
        count >= 1_000 -> "${count / 1_000}k"
        else -> count.toString()
    }
}

/**
 * 头像/昵称可点：有 uid 时进入该用户主页；无 uid（游客/匿名）不响应。
 *
 * 卡片本体可点（打开帖子），这里的内层 clickable 会优先消费手势，不会误触卡片。
 */
private fun Modifier.userClick(
    uid: String?,
    onUserClick: (String) -> Unit,
): Modifier =
    if (uid.isNullOrBlank()) this else clickable { onUserClick(uid) }
