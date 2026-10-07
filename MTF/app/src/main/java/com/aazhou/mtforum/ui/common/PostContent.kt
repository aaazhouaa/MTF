package com.aazhou.mtforum.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aazhou.mtforum.data.model.PostQuote
import com.aazhou.mtforum.image.ImageAspectCache
import com.aazhou.mtforum.image.imageAspectRatio
import com.aazhou.mtforum.ui.theme.LocalIsDarkTheme
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.DarkBrightTextAlpha
import com.aazhou.mtforum.ui.theme.bodyTextColor
import com.aazhou.mtforum.ui.theme.dimImageInDark
import com.aazhou.mtforum.ui.theme.accentTextColor
import com.aazhou.mtforum.ui.theme.MtfColors

/** 行内表情的显示尺寸，与正文行高匹配。 */
private val SmileySize = 20.sp

/** 站点自带文字颜色与底色的对比度下限，低于该值即视为「看不清」。 */
private const val MinSiteTextContrast = 2.5f

/** WCAG 相对对比度：[a]/[b] 亮者在前，返回 >=1 的比值。 */
private fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return if (la >= lb) la / lb else lb / la
}

/**
 * 校正站点内联文字颜色的可读性。
 *
 * 站点把 UBB 编辑器选的颜色直接烘进 `<font color>` 里（按浅色网页选的），
 * 深色主题下深灰/近黑字会糊进深色底里看不见。对比度不足时保持色相、
 * 朝反色方向（深色主题趋白、浅色主题趋黑）逐级插值到达标为止。
 *
 * 深色主题下最终还要按 [DarkBrightTextAlpha] 压低，故这里按压暗后的合成色算对比度，
 * 否则会「先达标再压暗」反而跌破下限。
 */
private fun readableSiteColor(color: Color, background: Color, darkTheme: Boolean): Color {
    val target = if (darkTheme) color.copy(alpha = DarkBrightTextAlpha) else color
    if (contrastRatio(target, background) >= MinSiteTextContrast) return color
    val anchor = if (darkTheme) Color.White else Color.Black
    var step = 0.1f
    while (step < 1f) {
        val candidate = lerp(color, anchor, step)
        val rendered = if (darkTheme) candidate.copy(alpha = DarkBrightTextAlpha) else candidate
        if (contrastRatio(rendered, background) >= MinSiteTextContrast) return candidate
        step += 0.1f
    }
    return anchor
}

/** 渲染帖子正文，图文按原顺序展示；图片/视频/链接点击回调交给上层处理。 */
@Composable
fun PostContent(
    html: String,
    quote: PostQuote? = null,
    onImageClick: ((urls: List<String>, index: Int) -> Unit)? = null,
    onVideoClick: ((url: String) -> Unit)? = null,
    /** 正文链接点击：站内帖子链接交由上层跳到原生详情页，其余走系统浏览器。 */
    onLinkClick: ((url: String) -> Unit)? = null,
    pageImages: List<String> = emptyList(),
    bodyStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    modifier: Modifier = Modifier,
    /**
     * 行内前缀：拼在首段正文之前同一段起排，折行后所有行回到前缀的左边缘。
     *
     * 首块不是文字（例如正文以图片开头）时，前缀只能单独占一行，退化成两行排版。
     */
    inlinePrefix: AnnotatedString? = null,
) {
    val blocks = remember(html) { parseContentBlocks(html) }
    val colors = LocalMtfColors.current
    val darkTheme = LocalIsDarkTheme.current
    Column(modifier) {
        if (quote != null && quote.text.isNotBlank()) QuoteCard(quote)
        // 首块不是文字（正文以图片开头）时，行内前缀无从拼接，只能单独占一行
        if (inlinePrefix != null && blocks.firstOrNull() !is ContentBlock.Text) {
            Text(text = inlinePrefix, style = bodyStyle, modifier = Modifier.padding(vertical = 2.dp))
        }
        for ((index, block) in blocks.withIndex()) {
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
                        val linkColor = accentTextColor()
                        val codeBg = colors.quoteBackground
                        val style = remember(block, bodyStyle) { block.derivedStyle(bodyStyle) }
                        // 回调经 state 间接引用：链接注解串按正文缓存，重组不会重建，
                        // 而 onLinkClick 每次重组都是新实例，直接进 key 会让长文反复重建
                        val linkClick by rememberUpdatedState(onLinkClick)
                        val body = remember(
                            text,
                            block.smiles,
                            block.spans,
                            linkColor,
                            codeBg,
                            colors.background,
                            darkTheme,
                        ) {
                            buildBodyText(
                                text = text,
                                smiles = block.smiles,
                                spans = block.spans,
                                linkColor = linkColor,
                                codeBackground = codeBg,
                                background = colors.background,
                                darkTheme = darkTheme,
                                onLinkClick = linkClick,
                            )
                        }
                        Text(
                            text = if (index == 0 && inlinePrefix != null) inlinePrefix + body else body,
                            inlineContent = remember(block.smiles) { smileyInlineContent(block.smiles) },
                            style = style,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    } else if (index == 0 && inlinePrefix != null) {
                        // 首块是空文本块：前缀自己占一行，正文从下一块开始
                        Text(text = inlinePrefix, style = bodyStyle)
                    }
                }
                is ContentBlock.Image -> SingleImage(
                    url = block.url,
                    onImageClick = onImageClick?.let { click ->
                        // 单图也放进全帖图片序列，全屏查看时可左右翻到其它图
                        val urls = pageImages.ifEmpty { listOf(block.url) }
                        val index = urls.indexOf(block.url).coerceAtLeast(0)
                        return@let { click(urls, index) }
                    },
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                is ContentBlock.ImageGroup -> ImageGrid(
                    urls = block.urls,
                    onImageClick = onImageClick?.let { click -> { url -> click(block.urls, block.urls.indexOf(url)) } },
                )
                is ContentBlock.Video -> InlineVideo(
                    url = block.url,
                    poster = block.poster,
                    onPlay = onVideoClick?.let { click -> { click(block.url) } },
                )
                is ContentBlock.Table -> PostTable(block, bodyStyle)
            }
        }
    }
}

/**
 * 一段正文的注解串：先放笑脸占位，再叠加局部样式与链接。
 *
 * 笑脸用字符串注解 + inlineContent 替换（注解值必须是占位字符本身）；
 * 样式来自 [StyleSpan]，下标与 [text] 对齐，越界区间已在解析阶段裁掉。
 */
private fun buildBodyText(
    text: String,
    smiles: Map<Char, String>,
    spans: List<StyleSpan>,
    linkColor: Color,
    codeBackground: Color,
    background: Color,
    darkTheme: Boolean,
    onLinkClick: ((url: String) -> Unit)? = null,
): AnnotatedString {
    if (spans.isEmpty()) return text.toAnnotatedString(smiles)
    return buildAnnotatedString {
        text.forEachIndexed { index, char ->
            append(char)
            if (smiles.containsKey(char)) {
                addStringAnnotation(TAG_SMILEY, char.toString(), index, index + 1)
            }
        }
        for (span in spans) {
            val start = span.start.coerceIn(0, length)
            val end = span.end.coerceIn(start, length)
            if (end <= start) continue
            val decoration = when {
                span.strikethrough -> TextDecoration.LineThrough
                span.underline -> TextDecoration.Underline
                else -> null
            }
            val spanStyle = SpanStyle(
                fontWeight = if (span.bold || span.headingLevel > 0) FontWeight.Bold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null,
                textDecoration = decoration,
                fontFamily = if (span.code) FontFamily.Monospace else null,
                background = if (span.code) codeBackground else Color.Unspecified,
            )
            if (spanStyle != SpanStyle()) addStyle(spanStyle, start, end)
            span.colorArgb?.let { raw ->
                val siteColor = readableSiteColor(Color(raw), background, darkTheme)
                // 深色下与正文统一压低（readableSiteColor 已按压暗后的合成色算过对比度）
                val rendered = if (darkTheme) siteColor.copy(alpha = DarkBrightTextAlpha) else siteColor
                addStyle(SpanStyle(color = rendered), start, end)
            }
            span.linkUrl?.let { url ->
                addLink(
                    LinkAnnotation.Url(
                        url = url,
                        styles = TextLinkStyles(
                            style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                        ),
                        linkInteractionListener = onLinkClick?.let { click ->
                            LinkInteractionListener { click(url) }
                        },
                    ),
                    start,
                    end,
                )
            }
        }
    }
}

/** 由段内样式推出该段的基础排版（标题字号/加粗、字号缩放、对齐）。 */
private fun ContentBlock.Text.derivedStyle(bodyStyle: TextStyle): TextStyle {
    if (spans.isEmpty()) return bodyStyle
    val heading = spans.maxOfOrNull { it.headingLevel } ?: 0
    val scale = (spans.maxOfOrNull { it.fontSizeScale } ?: 1f) * headingScale(heading)
    val align = spans.firstNotNullOfOrNull { it.align }
    return bodyStyle.copy(
        fontSize = bodyStyle.fontSize * scale,
        lineHeight = bodyStyle.lineHeight * scale,
        fontWeight = if (heading > 0) FontWeight.Bold else null,
        textAlign = when (align) {
            CellAlign.Center -> TextAlign.Center
            CellAlign.End -> TextAlign.End
            CellAlign.Start -> TextAlign.Start
            null -> TextAlign.Unspecified
        },
    )
}

private fun headingScale(level: Int): Float = when (level) {
    1 -> 1.6f
    2 -> 1.4f
    3 -> 1.25f
    4 -> 1.15f
    5 -> 1.08f
    else -> 1f
}

/** 表格：列宽按内容测量分配 + 分隔线，表头行加粗并带淡底色。 */
@Composable
private fun PostTable(table: ContentBlock.Table, bodyStyle: TextStyle) {
    val colors = LocalMtfColors.current
    val cellStyle = bodyStyle.copy(fontSize = 13.sp, lineHeight = 18.sp)
    val shape = RoundedCornerShape(8.dp)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val cellHorizontalPadding = 10.dp
    val dividerThickness = 0.6.dp
    val minColumn = 36.dp

    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        val columns = maxOf(table.header?.size ?: 0, table.rows.maxOfOrNull { it.size } ?: 0)
        if (columns == 0) return@BoxWithConstraints
        // 每列取该列最宽单元格的单行自然宽度作为权重，序号列才不会和正文列平分到同样宽
        val natural = List(columns) { column ->
            val texts = buildList {
                table.header?.getOrNull(column)?.let(::add)
                table.rows.forEach { row -> row.getOrNull(column)?.let(::add) }
            }
            val maxPx = texts.maxOfOrNull { text ->
                measurer.measure(AnnotatedString(text), cellStyle).size.width
            } ?: 0
            with(density) { maxPx.toDp() }
        }
        val fixed = cellHorizontalPadding * 2 * columns + dividerThickness * (columns - 1)
        val budget = (maxWidth - fixed).coerceAtLeast(minColumn * columns)
        val widths = allocateColumnWidths(natural, budget, minColumn)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(dividerThickness, colors.divider, shape),
        ) {
            table.header?.let { header ->
                PostTableRow(header, widths, cellStyle, FontWeight.Bold, colors, headerTab = true)
                if (table.rows.isNotEmpty()) {
                    HorizontalDivider(color = colors.divider, thickness = dividerThickness)
                }
            }
            table.rows.forEachIndexed { rowIndex, row ->
                PostTableRow(row, widths, cellStyle, null, colors, headerTab = false)
                if (rowIndex < table.rows.lastIndex) {
                    HorizontalDivider(color = colors.divider, thickness = dividerThickness)
                }
            }
        }
    }
}

/**
 * 按内容宽度占比分配可用宽度，列间用竖线分隔。
 *
 * 内容很窄的列（如行号）只拿到与内容相当的宽度，剩余宽度给内容最长的列；
 * 总宽超出预算时从最宽的列开始回收，但不小于 [min]。
 */
private fun allocateColumnWidths(natural: List<Dp>, budget: Dp, min: Dp): List<Dp> {
    if (natural.isEmpty()) return emptyList()
    val total = natural.fold(0f) { acc, width -> acc + width.value }
    val raw = if (total <= 0f) {
        List(natural.size) { budget.value / natural.size }
    } else {
        natural.map { budget.value * it.value / total }
    }
    val widths = raw.map { maxOf(it, min.value) }.toMutableList()
    var overflow = widths.sum() - budget.value
    var guard = 0
    while (overflow > 0.5f && guard < 64) {
        val index = widths.indices.maxByOrNull { widths[it] } ?: break
        val reducible = (widths[index] - min.value).coerceAtMost(overflow)
        if (reducible <= 0f) break
        widths[index] -= reducible
        overflow -= reducible
        guard++
    }
    return widths.map { it.dp }
}

/** 一行单元格：按 [widths] 固定列宽，行高由内容最高的单元格决定，列间用竖线分隔。 */
@Composable
private fun PostTableRow(
    cells: List<String>,
    widths: List<Dp>,
    cellStyle: TextStyle,
    weight: FontWeight?,
    colors: MtfColors,
    headerTab: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (headerTab) Modifier.background(colors.quoteBackground) else Modifier)
            .height(IntrinsicSize.Min),
    ) {
        cells.forEachIndexed { index, cell ->
            Text(
                text = cell,
                style = cellStyle.copy(fontWeight = weight),
                color = bodyTextColor(),
                modifier = Modifier
                    .width(widths.getOrElse(index) { widths.last() })
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
            if (index < cells.size - 1) {
                VerticalDivider(color = colors.divider, thickness = 0.6.dp)
            }
        }
    }
}

/** 单张图片：限宽并限制最大高宽比，小图不放大；尺寸未知时先按 4:3 占位。 */
@Composable
private fun SingleImage(url: String, onImageClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val colors = LocalMtfColors.current
    // 宽高比预取命中时首次测量即终态：滚动途中图片加载完成不会再撑高所在 item，
    // LazyColumn 的滚动锚点失配导致的跳屏随之消失；未命中仍回落 4:3 占位
    val cachedAspect = ImageAspectCache.aspectOf(url)
    var aspectRatio by remember(url) { mutableStateOf(cachedAspect ?: 4f / 3f) }
    // 预取在本项组合之后才完成时，缓存写入会触发重组，此时按缓存校正一次
    LaunchedEffect(url, cachedAspect) {
        if (cachedAspect != null && aspectRatio != cachedAspect) aspectRatio = cachedAspect
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val maxImageWidth = maxWidth * 0.92f
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onSuccess = { state ->
                val drawable = state.result.drawable
                imageAspectRatio(drawable.intrinsicWidth, drawable.intrinsicHeight)?.let {
                    aspectRatio = it
                }
            },
            onError = { state ->
                android.util.Log.e("MTF-IMG", "single fail url=$url", state.result.throwable)
            },
            modifier = Modifier
                .align(Alignment.Center)
                .width(maxImageWidth)
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.quoteBackground)
                .dimImageInDark()
                .then(
                    if (onImageClick != null) Modifier.clickable { onImageClick() } else Modifier
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
                            onError = { state ->
                                android.util.Log.e("MTF-IMG", "grid fail url=$url", state.result.throwable)
                            },
                            modifier = Modifier.fillMaxSize().dimImageInDark(),
                        )
                        // 「+N」遮罩压在压暗膜之上：角标与计数不被图片亮度影响
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

/**
 * 正文内嵌视频卡：16:9 黑底，封面用附件懒加载图，无封面时纯黑；中间一颗播放键。
 * 卡片本身不持播放器，点击后由上层拉起全屏播放页，正文滚动时不会带着视频一直播。
 */
@Composable
private fun InlineVideo(url: String, poster: String?, onPlay: (() -> Unit)?) {
    val colors = LocalMtfColors.current
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        val maxVideoWidth = maxWidth * 0.92f
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(maxVideoWidth)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.quoteBackground)
                .then(if (onPlay != null) Modifier.clickable { onPlay() } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (poster != null) {
                AsyncImage(
                    model = poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onError = { state ->
                        android.util.Log.e("MTF-IMG", "video poster fail url=$poster", state.result.throwable)
                    },
                    modifier = Modifier.fillMaxSize().dimImageInDark(),
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "播放视频",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

/** 引用卡：上方「回复 {被引作者}：」斜体小字；正文带「原文」前缀，下方一条与正文等长的细灰线；右下角仅时间。 */
@Composable
private fun QuoteCard(quote: PostQuote) {
    val colors = LocalMtfColors.current
    val accentColor = accentTextColor()    // 正文最后一行的文字宽度：细线长度跟着它走，多行也只在最后一行下面画一条
    var lastLineRight by remember(quote.text) { mutableStateOf(0f) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        if (!quote.author.isNullOrBlank()) {
            Text(
                text = "回复 ${quote.author}：",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                fontStyle = FontStyle.Italic,
                color = colors.textHint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = accentColor, fontWeight = FontWeight.Bold)) {
                        append("原文 ")
                    }
                    append(quote.text)
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = colors.quoteContent,
                onTextLayout = { layout ->
                    lastLineRight = layout.getLineRight(layout.lineCount - 1)
                },
            )
            Spacer(Modifier.height(6.dp))
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp),
            ) {
                drawLine(
                    color = colors.divider,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(lastLineRight, size.height / 2f),
                    strokeWidth = size.height,
                )
            }
            if (!quote.time.isNullOrBlank()) {
                Text(
                    text = quote.time,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = colors.textSecondary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
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
                // 表情与配图/头像同套深色压暗：站点表情多为浅色线条画，深色底上很跳。
                // 膜色即页面底色，emo 四周的透明区不会被看出方块
                modifier = Modifier.fillMaxSize().dimImageInDark(),
            )
        }
    }
