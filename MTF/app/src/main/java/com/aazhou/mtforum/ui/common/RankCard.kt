package com.aazhou.mtforum.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aazhou.mtforum.R
import com.aazhou.mtforum.data.model.Thread
import com.aazhou.mtforum.ui.theme.LocalIsDarkTheme
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.bodyTextColor

/**
 * 排行榜卡片：14dp 圆角白卡 + 粗体标题 + 彩色序号 + 单行标题。
 *
 * 首页的「热帖排行」与版块页的「公告」共用：公告只解析出标题与 tid，
 * 正好只用到本卡片用到的两个字段。
 *
 * 条数超过 [collapsedLimit] 时默认只显示前几条，末尾给展开/收起入口；
 * 展开态由调用方持有，离开所在页面即回到默认的折叠态。
 */
internal const val RankCollapsedLimit = 3

@Composable
fun RankCard(
    title: String,
    threads: List<Thread>,
    onThreadClick: (String) -> Unit,
    /** 折叠时保留前几条。 */
    collapsedLimit: Int,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    val colors = LocalMtfColors.current
    // 条数不够时连入口都不给：点了也没有可展开的内容
    val collapsible = threads.size > collapsedLimit
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp),
            fontWeight = FontWeight.Bold,
            color = bodyTextColor(),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        )
        (if (expanded) threads else threads.take(collapsedLimit)).forEachIndexed { index, thread ->
            val rank = index + 1
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onThreadClick(thread.tid) }
                    .padding(vertical = 8.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(rankBadgeColor(rank)),
                ) {
                    Text(
                        rank.toString(),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = Color.White,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    thread.subject,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = bodyTextColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (collapsible) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(start = 6.dp, top = 2.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.quoteBackground)
                    .clickable { onToggleExpanded() }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    if (expanded) "收起" else "展开其余 ${threads.size - collapsedLimit} 条",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = colors.textSecondary,
                    maxLines = 1,
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    painterResource(if (expanded) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down),
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

/**
 * 序号底色：1 红 / 2 黄 / 3 绿 / 其余灰。
 *
 * 深色主题换用压暗的同色相实底：浅色那套是全饱和度亮色，直接铺在深色卡片上会发刺，
 * 且序号 4+ 的浅灰底配白字在深色底下几乎读不出来。压暗后白字对比度也一并解决。
 */
@Composable
private fun rankBadgeColor(rank: Int): Color {
    if (!LocalIsDarkTheme.current) return when (rank) {
        1 -> Color(0xFFFF705E)
        2 -> Color(0xFFFFB900)
        3 -> Color(0xFFA8C500)
        else -> Color(0xFFCCCCCC)
    }
    return when (rank) {
        1 -> Color(0xFFC2453A)
        2 -> Color(0xFFB07D0A)
        3 -> Color(0xFF6E8C0F)
        else -> Color(0xFF5A6472)
    }
}
