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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aazhou.mtforum.data.model.Thread
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.bodyTextColor

/**
 * 排行榜卡片：14dp 圆角白卡 + 粗体标题 + 彩色序号 + 单行标题。
 *
 * 首页的「热帖排行」与版块页的「公告」共用：公告只解析出标题与 tid，
 * 正好只用到本卡片用到的两个字段。
 */
@Composable
fun RankCard(
    title: String,
    threads: List<Thread>,
    onThreadClick: (String) -> Unit,
) {
    val colors = LocalMtfColors.current
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
        threads.forEachIndexed { index, thread ->
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
                        .background(rankColor(rank)),
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
    }
}

/** 序号底色：1 红 / 2 黄 / 3 绿 / 其余灰。 */
private fun rankColor(rank: Int): Color = when (rank) {
    1 -> Color(0xFFFF705E)
    2 -> Color(0xFFFFB900)
    3 -> Color(0xFFA8C500)
    else -> Color(0xFFCCCCCC)
}
