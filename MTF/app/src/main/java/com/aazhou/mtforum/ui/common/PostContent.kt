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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.aazhou.mtforum.ui.common.container
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aazhou.mtforum.data.model.PostQuote
import com.aazhou.mtforum.image.GridImageSize
import com.aazhou.mtforum.image.ImageAspectCache
import com.aazhou.mtforum.image.imageAspectRatio
import com.aazhou.mtforum.image.postImageRequest
import com.aazhou.mtforum.image.smallImageRequest
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.bodyTextColor
import com.aazhou.mtforum.ui.theme.dimImageInDark
import com.aazhou.mtforum.ui.theme.accentTextColor
import com.aazhou.mtforum.ui.theme.MtfColors

/** 行内表情的显示尺寸，与正文行高匹配。 */
private val SmileySize = 20.sp

/** 渲染帖子正文，图文按原顺序展示；图片/视频/链接/附件点击回调交给上层处理。 */
@Composable
fun PostContent(
    html: String,
    quote: PostQuote? = null,
    onImageClick: ((urls: List<String>, index: Int) -> Unit)? = null,
    onVideoClick: ((url: String) -> Unit)? = null,
    /** 正文链接点击：站内帖子链接交由上层跳到原生详情页，其余走系统浏览器。 */
    onLinkClick: ((url: String) -> Unit)? = null,
    /** 附件下载框点击：交由上层处理（下载并落盘）。 */
    onAttachmentClick: ((fileName: String, url: String) -> Unit)? = null,
    pageImages: List<String> = emptyList(),
    bodyStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    modifier: Modifier = Modifier,
    /**
     * 正文是否可长按划选。
     *
     * 评论区楼层传 false：楼层自身挂了长按操作菜单（回复/举报），划选手势会抢在
     * 菜单之前弹出系统的剪切/复制工具条，长按菜单反而点不出来。
     */
    selectable: Boolean = true,
) {
    if (selectable) {
        SelectionContainer {
            PostContentBody(html, quote, onImageClick, onVideoClick, onLinkClick, onAttachmentClick, pageImages, bodyStyle, modifier)
        }
    } else {
        PostContentBody(html, quote, onImageClick, onVideoClick, onLinkClick, onAttachmentClick, pageImages, bodyStyle, modifier)
    }
}

@Composable
private fun PostContentBody(
    html: String,
    quote: PostQuote?,
    onImageClick: ((urls: List<String>, index: Int) -> Unit)?,
    onVideoClick: ((url: String) -> Unit)?,
    onLinkClick: ((url: String) -> Unit)?,
    onAttachmentClick: ((fileName: String, url: String) -> Unit)?,
    pageImages: List<String>,
    bodyStyle: TextStyle,
    modifier: Modifier,
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
                        ) {
                            buildBodyText(
                                text = text,
                                smiles = block.smiles,
                                spans = block.spans,
                                baseFontSize = style.fontSize,
                                linkColor = linkColor,
                                codeBackground = codeBg,
                                onLinkClick = linkClick,
                            )
                        }
                        Text(
                            text = body,
                            inlineContent = remember(block.smiles) { smileyInlineContent(block.smiles) },
                            style = style,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
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
                    onImageClick = onImageClick?.let { click ->
                        { url ->
                            // 图组也放进全帖图片序列：与单图一致，点击组内任意一张都能翻看全帖所有图
                            val urls = pageImages.ifEmpty { block.urls }
                            val index = urls.indexOf(url).coerceAtLeast(0)
                            click(urls, index)
                        }
                    },
                )
                is ContentBlock.Video -> InlineVideo(
                    url = block.url,
                    poster = block.poster,
                    onPlay = onVideoClick?.let { click -> { click(block.url) } },
                )
                is ContentBlock.Attachment -> PostAttachment(
                    block = block,
                    onClick = onAttachmentClick,
                )
                is ContentBlock.Table -> PostTable(block, bodyStyle)
                is ContentBlock.CodeBlock -> PostCodeBlock(block, bodyStyle)
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
internal fun buildBodyText(
    text: String,
    smiles: Map<Char, String>,
    spans: List<StyleSpan>,
    baseFontSize: TextUnit,
    linkColor: Color,
    codeBackground: Color,
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
            // 字号逐 span 施加：站点把标题包进 <font size> 里、普通行不包，
            // 整块取最大缩放会把段落里的普通行一起放大，与网页逐行各自生效不一致
            val scale = span.fontSizeScale * headingScale(span.headingLevel)
            val spanStyle = SpanStyle(
                fontSize = if (scale != 1f) baseFontSize * scale else TextUnit.Unspecified,
                fontWeight = if (span.bold || span.headingLevel > 0) FontWeight.Bold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null,
                textDecoration = decoration,
                fontFamily = if (span.code) FontFamily.Monospace else null,
                background = if (span.code) codeBackground else Color.Unspecified,
            )
            if (spanStyle != SpanStyle()) addStyle(spanStyle, start, end)
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

/** 由段内样式推出该段的基础排版：只推导段级对齐，字号与标题缩放按 span 在 buildBodyText 施加。 */
private fun ContentBlock.Text.derivedStyle(bodyStyle: TextStyle): TextStyle {
    if (spans.isEmpty()) return bodyStyle
    val align = spans.firstNotNullOfOrNull { it.align }
    return bodyStyle.copy(
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
 * 代码块：圆角浅灰底 + 左侧一条彩色竖线 + 行号前缀，等宽字体、长行自动换行。
 *
 * 行号与代码必须同处一段：拆成两个 Text 时代码折行会让行号列与代码行错位，
 * 故每行一个 Text、行号作前缀，续行用 TextIndent 缩进行号列宽度与代码左缘对齐。
 */
@Composable
private fun PostCodeBlock(block: ContentBlock.CodeBlock, bodyStyle: TextStyle) {
    val colors = LocalMtfColors.current
    val codeStyle = bodyStyle.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    )
    val numberStyle = codeStyle.copy(fontSize = 11.sp)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // 行号列宽度按最宽行号测量：折行后的续行缩进到它，代码左缘才与首行对齐
    val indent = remember(block.lines.size, numberStyle) {
        with(density) {
            (measurer.measure(
                AnnotatedString("%02d ".format(block.lines.size)),
                numberStyle,
            ).size.width.toDp() + 8.dp).toSp()
        }
    }
    val wrappedStyle = remember(codeStyle, indent) {
        codeStyle.merge(
            ParagraphStyle(textIndent = TextIndent(firstLine = 0.sp, restLine = indent)),
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.quoteBackground)
            .height(IntrinsicSize.Min),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accentTextColor()),
        )
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            block.lines.forEachIndexed { index, line ->
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = colors.textHint)) {
                            append("%02d ".format(index + 1))
                        }
                        append(line)
                    },
                    style = wrappedStyle,
                    color = bodyTextColor(),
                )
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

/** 单张图片：横图完整显示、竖图按固定区域局部裁剪，均靠左；尺寸未知时先按 4:3 占位。 */
@Composable
private fun SingleImage(url: String, onImageClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val colors = LocalMtfColors.current
    val context = LocalContext.current
    // 宽高比预取命中时首次测量即终态：滚动途中图片加载完成不会再撑高所在 item，
    // LazyColumn 的滚动锚点失配导致的跳屏随之消失；未命中仍回落 4:3 占位
    val cachedAspect = ImageAspectCache.aspectOf(url)
    var aspectRatio by remember(url) { mutableStateOf(cachedAspect ?: 4f / 3f) }
    // 加载失败的图整块收起：站点附件失效时网页端同样不占位（懒加载失败后换成 0×0 的
    // img_err.gif），客户端留着灰框会凭空多出几个空块
    var loadFailed by remember(url) { mutableStateOf(false) }
    // 预取在本项组合之后才完成时，缓存写入会触发重组，此时按缓存校正一次
    LaunchedEffect(url, cachedAspect) {
        if (cachedAspect != null && aspectRatio != cachedAspect) aspectRatio = cachedAspect
    }
    if (loadFailed) return
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // 单图比多图网格单格略大即可：同时限宽与限高，横图按宽、竖图按高，避免长图把列表撑得过长
        val maxImageWidth = maxWidth * 0.6f
        val maxImageHeight = maxWidth * 0.9f
        if (aspectRatio >= 1f) {
            // 横图：整图完整显示，靠左
            val sizeModifier =
                if (aspectRatio >= maxImageWidth / maxImageHeight) {
                    Modifier.width(maxImageWidth).aspectRatio(aspectRatio)
                } else {
                    Modifier.height(maxImageHeight).aspectRatio(aspectRatio)
                }
            AsyncImage(
                model = postImageRequest(url),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onSuccess = { state ->
                    val drawable = state.result.drawable
                    imageAspectRatio(drawable.intrinsicWidth, drawable.intrinsicHeight)?.let {
                        aspectRatio = it
                    }
                },
                onError = { state ->
                    loadFailed = true
                    context.container().logger.error(
                        "Image",
                        "正文单图加载失败 url=$url",
                        state.result.throwable,
                    )
                },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .then(sizeModifier)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.quoteBackground)
                    .dimImageInDark()
                    .border(1.dp, colors.divider, RoundedCornerShape(6.dp))
                    .then(
                        if (onImageClick != null) Modifier.clickable { onImageClick() } else Modifier
                    ),
            )
        } else {
            // 竖图：整张按比例缩出来只有一小条，改成固定区域局部裁剪，靠左
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(maxImageWidth)
                    .height(maxImageWidth * 0.75f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.quoteBackground)
                    .dimImageInDark()
                    .border(1.dp, colors.divider, RoundedCornerShape(6.dp))
                    .then(
                        if (onImageClick != null) Modifier.clickable { onImageClick() } else Modifier
                    ),
            ) {
                AsyncImage(
                    model = postImageRequest(url),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onSuccess = { state ->
                        val drawable = state.result.drawable
                        imageAspectRatio(drawable.intrinsicWidth, drawable.intrinsicHeight)?.let {
                            aspectRatio = it
                        }
                    },
                    onError = { state ->
                        loadFailed = true
                        context.container().logger.error(
                            "Image",
                            "正文单图加载失败 url=$url",
                            state.result.throwable,
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** 连续图片网格：2/4 张两列、其余三列，最多 9 张，超出在第 9 格显示剩余张数。 */
@Composable
private fun ImageGrid(urls: List<String>, onImageClick: ((String) -> Unit)?) {
    val colors = LocalMtfColors.current
    val context = LocalContext.current
    // 失效图不进网格：站点删掉的附件/失联的图床加载失败后，网页端是 0×0 占位、不占地方，
    // 客户端照原样渲染只会得到一排灰框
    val failedUrls = remember(urls) { mutableStateMapOf<String, Boolean>() }
    val liveUrls = urls.filterNot { failedUrls[it] == true }
    if (liveUrls.isEmpty()) return
    val columns = if (liveUrls.size == 2 || liveUrls.size == 4) 2 else 3
    val cells = if (liveUrls.size > 9) liveUrls.take(9) else liveUrls
    val overflow = liveUrls.size - 9
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
                            .border(1.dp, colors.divider, RoundedCornerShape(6.dp))
                            .then(
                                if (onImageClick != null) Modifier.clickable { onImageClick(url) } else Modifier
                            ),
                    ) {
                        AsyncImage(
                            model = postImageRequest(url, maxSize = GridImageSize),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            onError = { state ->
                                failedUrls[url] = true
                                context.container().logger.error(
                                    "Image",
                                    "正文图组加载失败 url=$url",
                                    state.result.throwable,
                                )
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
 * 正文附件下载卡片：文件图标 + 文件名 + 大小/积分说明，点击后由上层下载。
 * 还原网页端 div.comiis_attach 的「文件」形态。
 */
@Composable
private fun PostAttachment(
    block: ContentBlock.Attachment,
    onClick: ((fileName: String, url: String) -> Unit)?,
) {
    val colors = LocalMtfColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.quoteBackground)
            .border(1.dp, colors.divider, RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable { onClick(block.fileName, block.url) } else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        ) {
            Icon(
                painter = painterResource(com.aazhou.mtforum.R.drawable.ic_download),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = block.fileName,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                fontWeight = FontWeight.Medium,
                color = bodyTextColor(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!block.sizeText.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = block.sizeText,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
    val context = LocalContext.current
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
                    model = postImageRequest(poster),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onError = { state ->
                        context.container().logger.error(
                            "Image",
                            "视频封面加载失败 url=$poster",
                            state.result.throwable,
                        )
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
                model = smallImageRequest(url),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                // 表情与配图/头像同套深色压暗：站点表情多为浅色线条画，深色底上很跳。
                // 膜色即页面底色，emo 四周的透明区不会被看出方块
                modifier = Modifier.fillMaxSize().dimImageInDark(),
            )
        }
    }
