package com.aazhou.mtforum.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aazhou.mtforum.data.model.PostQuote
import com.aazhou.mtforum.ui.theme.LocalMtfColors

/** 行内表情的显示尺寸，与正文行高匹配。 */
private val SmileySize = 20.sp

/** 渲染帖子正文，图文按原顺序展示；图片点击回调交给上层做大图查看。 */
@Composable
fun PostContent(
    html: String,
    quote: PostQuote? = null,
    onImageClick: ((String) -> Unit)? = null,
    bodyStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(html) { parseContentBlocks(html) }
    val colors = LocalMtfColors.current
    Column(modifier) {
        if (quote != null && quote.text.isNotBlank()) QuoteCard(quote)
        for (block in blocks) {
            when (block) {
                is ContentBlock.Quote -> {
                    Text(
                        text = AnnotatedString(block.text),
                        style = bodyStyle.copy(fontSize = 13.sp),
                        color = colors.quoteContent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.quoteBackground)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
                is ContentBlock.Text -> {
                    val text = block.text.trim('\n')
                    if (text.isNotBlank()) {
                        Text(
                            text = remember(text, block.smiles) { text.toAnnotatedString(block.smiles) },
                            inlineContent = remember(block.smiles) { smileyInlineContent(block.smiles) },
                            style = bodyStyle,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
                is ContentBlock.Image -> SingleImage(
                    url = block.url,
                    onImageClick = onImageClick,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                is ContentBlock.ImageGroup -> ImageGrid(block.urls, onImageClick)
            }
        }
    }
}

/** 单张图片：限宽并限制最大高宽比，小图不放大；尺寸未知时先按 4:3 占位。 */
@Composable
private fun SingleImage(url: String, onImageClick: ((String) -> Unit)?, modifier: Modifier = Modifier) {
    val colors = LocalMtfColors.current
    // 宽高比在图片加载完成后才可知，先给 4:3 占位避免布局跳动
    var aspectRatio by remember(url) { mutableStateOf(4f / 3f) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val maxImageWidth = maxWidth * 0.92f
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onSuccess = { state ->
                val drawable = state.result.drawable
                if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                    // 限制最大高宽比为 1.25，竖长图不占满多屏
                    aspectRatio = (drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight)
                        .coerceAtLeast(0.8f)
                }
            },
            modifier = Modifier
                .align(Alignment.Center)
                .width(maxImageWidth)
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.quoteBackground)
                .then(
                    if (onImageClick != null) Modifier.clickable { onImageClick(url) } else Modifier
                ),
        )
    }
}

/** 连续图片网格：2/4 张两列、其余三列，最多 9 张，超出在第 9 格显示剩余张数。 */
@Composable
private fun ImageGrid(urls: List<String>, onImageClick: ((String) -> Unit)?) {
    val colors = LocalMtfColors.current
    val columns = if (urls.size == 2 || urls.size == 4) 2 else 3
    val cells = if (urls.size > 9) urls.take(9) else urls
    val overflow = urls.size - 9
    val cellRatio = if (columns == 2) 0.85f else 1f
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        cells.chunked(columns).forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEachIndexed { columnIndex, url ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f / cellRatio)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.quoteBackground)
                            .then(
                                if (onImageClick != null) Modifier.clickable { onImageClick(url) } else Modifier
                            ),
                    ) {
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (overflow > 0 && rowIndex * columns + columnIndex == 8) {
                            Box(
                                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.47f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "+$overflow",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    }
                }
                repeat(columns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** 引用卡：灰底，被引正文 13sp，右下角「被引者 发表于 时间」11sp 半透明。 */
@Composable
private fun QuoteCard(quote: PostQuote) {
    val colors = LocalMtfColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(colors.quoteBackground)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(
            text = quote.text,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = colors.quoteContent,
        )
        val title = buildString {
            if (!quote.author.isNullOrBlank()) append(quote.author)
            if (!quote.time.isNullOrBlank()) {
                if (isNotEmpty()) append(' ')
                append("发表于 ").append(quote.time)
            }
        }
        if (title.isNotBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = colors.textSecondary,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

/**
 * 表情占位字符标上注解，Text 会把该区间替换成 inlineContent 里的图片（区间内的字符不渲染）。
 *
 * 注解的值必须是 inlineContent 的 key（占位字符本身）：框架用注解值去查 map，
 * 填图片地址会查不到，表情不会被替换。
 */
internal fun String.toAnnotatedString(smiles: Map<Char, String>): AnnotatedString {
    if (smiles.isEmpty()) return AnnotatedString(this)
    return buildAnnotatedString {
        this@toAnnotatedString.forEachIndexed { index, char ->
            append(char)
            if (smiles.containsKey(char)) {
                addStringAnnotation(TAG_SMILEY, char.toString(), index, index + 1)
            }
        }
    }
}

private const val TAG_SMILEY = "androidx.compose.foundation.text.inlineContent"

/** 占位字符 -> 行内表情图片。 */
internal fun smileyInlineContent(smiles: Map<Char, String>): Map<String, InlineTextContent> =
    smiles.entries.associate { (placeholder, url) ->
        placeholder.toString() to InlineTextContent(
            Placeholder(
                width = SmileySize,
                height = SmileySize,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
            ),
        ) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
