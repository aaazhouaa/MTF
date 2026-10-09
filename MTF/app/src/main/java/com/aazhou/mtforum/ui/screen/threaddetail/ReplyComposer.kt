package com.aazhou.mtforum.ui.screen.threaddetail

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.aazhou.mtforum.R
import com.aazhou.mtforum.data.model.Post
import com.aazhou.mtforum.data.parser.Smiley
import com.aazhou.mtforum.data.parser.SmileyPack
import com.aazhou.mtforum.ui.common.AppToast
import com.aazhou.mtforum.ui.theme.LocalIsDarkTheme
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.bodyTextColor
import com.aazhou.mtforum.ui.theme.dimImageInDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * 底部回复输入面板：点底栏「写评论」或楼层的「回复」弹出，键盘随之升起。
 *
 * 面板占住底栏的位置（Scaffold 的 bottomBar 槽在面板打开时改渲染它），玻璃采样与底栏
 * 同一套 backdrop，帖子内容仍从面板下透出。工具栏插入的都是站点认识的标记，与网页端编辑器
 * 一致：表情 `[呵呵]`、贴图 `[attachimg]id[/attachimg]`、朋友 `@用户名 `、话题 `#话题#`。
 */
@Composable
internal fun ReplyComposer(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    target: Post?,
    sending: Boolean,
    smileyPacks: List<SmileyPack>,
    recentCodes: List<String>,
    onSmileyUsed: (String) -> Unit,
    onFocusedChange: (Boolean) -> Unit,
    uploadCredentials: Pair<String, String>?,
    onUploadImage: suspend (File) -> String?,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMtfColors.current
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var panel by remember { mutableStateOf(ComposerPanel.None) }
    var uploading by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null || uploadCredentials == null) return@rememberLauncherForActivityResult
        uploading = true
        scope.launch {
            try {
                val file = prepareUploadFile(context, uri)
                val aid = onUploadImage(file)
                if (aid != null) {
                    onValueChange(value.insertAtSelection("[attachimg]$aid[/attachimg]"))
                } else {
                    AppToast.show("上传失败，可能超出大小限制或凭据已过期")
                }
            } catch (e: Exception) {
                AppToast.show(e.message ?: "上传失败")
            } finally {
                uploading = false
            }
        }
    }

    // 打开面板即聚焦并把键盘请出来
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val density = LocalDensity.current
    var activeInsertKind by remember { mutableStateOf<InsertKind?>(null) }
    var inlineUrlInput by remember { mutableStateOf("") }
    var inlineTitleInput by remember { mutableStateOf("") }

    val smileyByCode = remember(smileyPacks) {
        smileyPacks.flatMap { it.items }.associateBy { it.code }
    }

    val smileyVisualTransformation = remember(smileyByCode, colors) {
        VisualTransformation { text ->
            TransformedText(
                formatRichComposerText(text.text, smileyByCode, colors),
                OffsetMapping.Identity,
            )
        }
    }
    // 文本布局结果：没有它就不知道每个表情码画在哪
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    // 退格时智能联动删除成对标签（如 [audio]...[/audio]）或整个表情码
    fun onComposerValueChange(next: TextFieldValue) {
        val shrunkByOne = next.text.length == value.text.length - 1 &&
            next.selection.collapsed && value.selection.collapsed
        if (shrunkByOne) {
            // 1. 优先检查成对 BBCode 标签：按删除键一体化全删两边的代码
            val tagPairResult = handleTagPairDeletion(value, next)
            if (tagPairResult != null) {
                onValueChange(tagPairResult)
                return
            }

            // 2. 检查表情码：退格一次删掉整个表情
            val codeStart = smileyCodeStartBefore(value.text, value.selection.start)
            if (codeStart >= 0) {
                val codeEnd = value.text.indexOf(']', codeStart) + 1
                val text = value.text.removeRange(codeStart, codeEnd)
                onValueChange(value.copy(text = text, selection = TextRange(codeStart)))
                return
            }
        }
        onValueChange(next)
    }

    fun pickSmiley(code: String) {
        onValueChange(value.insertAtSelection(code))
        onSmileyUsed(code)
    }

    // 默认那一行：先用最近用过的，不足 8 个按默认表情表补满
    val recentRow = run {
        val used = recentCodes.mapNotNull { smileyByCode[it] }
        val wanted = DefaultSmileyCodes.mapNotNull { smileyByCode[it] }
        // 默认表一个都取不到（站点改了 code 写法或表情被下架）时退回第一个包的开头：
        // 宁可显示别的，也不能让这一行整行消失
        val filler = wanted.ifEmpty {
            smileyPacks.firstOrNull()?.items?.filter { smiley -> recentCodes.none { it == smiley.code } }
                .orEmpty()
        }.filter { smiley -> recentCodes.none { it == smiley.code } }
        (used + filler).take(RecentSmileyLimit)
    }

    Box(
        modifier
            .fillMaxWidth()
            // 键盘升起按 ime 避让、收起按导航栏避让：取两者较大值，不能相加（否则多留一条导航栏）
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            // 通栏实色底（浅色白、深色取 surface）、只圆上角、底部贴键盘：
            // 输入框以下整块被盖住，帖子内容不从边角和底缘透出来
            .background(colors.surface, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)),
    ) {
        // 底色本身不吃触摸：面板空白处（按钮两侧、四周留白）的点按和滑动会漏到下层遮罩，
        // 把回复框连着键盘一起关掉。垫一层把整个面板区域的指针事件吃掉。它先放置所以后命中，
        // 按钮自己消费掉的事件不受影响，漏过来的才被这里拦下。
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    }
                },
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(top = 12.dp),
        ) {
            // 输入区：占满宽度，文字顶格；回复指定楼层时占位文案带上对方昵称
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ReplyComposerInputHeight)
                    .pointerInput(value.text) {
                        detectTapGestures { offset ->
                            val layout = textLayout
                            val len = value.text.length
                            if (layout == null || len == 0) {
                                onValueChange(value.copy(selection = TextRange(len)))
                            } else {
                                val topPadPx = with(density) { 8.dp.toPx() }
                                val lastLine = layout.lineCount - 1
                                val lastLineBottom = layout.getLineBottom(lastLine) + topPadPx
                                val lastLineTop = layout.getLineTop(lastLine) + topPadPx
                                val lastLineRight = layout.getLineRight(lastLine)
                                // 点在所有文字行下方，或者点在末行文字右侧的空白区域 -> 瞬移到文本末尾
                                if (offset.y > lastLineBottom || (offset.y >= lastLineTop && offset.x > lastLineRight)) {
                                    onValueChange(value.copy(selection = TextRange(len)))
                                } else {
                                    val relY = (offset.y - topPadPx).coerceAtLeast(0f)
                                    val pos = layout.getOffsetForPosition(Offset(offset.x, relY))
                                    onValueChange(value.copy(selection = TextRange(pos)))
                                }
                            }
                            if (panel == ComposerPanel.Smiley) panel = ComposerPanel.None
                            focusRequester.requestFocus()
                            keyboard?.show()
                        }
                    },
                contentAlignment = Alignment.TopStart,
            ) {
                if (value.text.isEmpty()) {
                    Text(
                        if (target != null) "回复@${target.authorName}" else "写评论",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textHint,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = ::onComposerValueChange,
                    textStyle = TextStyle(color = bodyTextColor(), fontSize = 15.sp),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    visualTransformation = smileyVisualTransformation,
                    onTextLayout = { textLayout = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .focusRequester(focusRequester)
                        // 聚焦状态回传上层：遮罩和返回键靠它区分「该收键盘」还是「该收回复框」
                        .onFocusChanged { onFocusedChange(it.isFocused) },
                    // 表情贴图层：BasicTextField 没有 inlineContent 参数，只能自己按布局坐标贴图。
                    // 文字已被 visualTransformation 染成透明色，这一层垫在输入框下面，光标与
                    // 选区仍由输入框自己画，不会被图片盖住
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth()) {
                            SmileyOverlay(
                                text = value.text,
                                layout = textLayout,
                                smileyByCode = smileyByCode,
                            )
                            inner()
                        }
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ComposerToolButton(R.drawable.ic_smiley, "表情") {
                    val opening = panel != ComposerPanel.Smiley
                    panel = if (opening) ComposerPanel.Smiley else ComposerPanel.None
                    if (opening) {
                        // 必须先失焦再收键盘：只 hide 的话输入框仍持焦，输入法会在下次布局或
                        // 改字时立刻弹回来，表情面板和键盘就同时叠在屏幕上
                        focusManager.clearFocus()
                        keyboard?.hide()
                    } else {
                        focusRequester.requestFocus()
                        keyboard?.show()
                    }
                }
                ComposerToolButton(R.drawable.ic_image, "图片") {
                    if (uploadCredentials != null) {
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    } else {
                        AppToast.show("上传凭据已失效，返回帖子重进试试")
                    }
                }
                ComposerToolButton(R.drawable.ic_at, "提到朋友") {
                    onValueChange(value.wrapSelectionOrInsert("@", " "))
                    focusRequester.requestFocus()
                    keyboard?.show()
                }
                ComposerToolButton(R.drawable.ic_add, "插入") {
                    panel = if (panel == ComposerPanel.Insert) ComposerPanel.None else ComposerPanel.Insert
                }
                Spacer(Modifier.weight(1f))
                if (sending || uploading) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    val publishReady = value.text.isNotBlank()
                    Text(
                        "发布",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (publishReady) Color.White else colors.textHint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(
                                if (publishReady) ComposerPublishColor else colors.textHint.copy(alpha = 0.2f),
                                AbsoluteRoundedCornerShape(50),
                            )
                            .clickable(enabled = publishReady, onClick = onSend)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }

            // 展开哪个面板就排哪个：表情面板是内容列的一部分，网格高度由自己写死，
            // 不依赖键盘高度或剩余空间——那两种依赖都让网格塌成过一条 tab 栏
            when (panel) {
                // 没展开面板时只摆一行最近用过的，够 8 个为止
                ComposerPanel.None -> {
                    val currentInsert = activeInsertKind
                    if (currentInsert != null) {
                        InlineInsertBar(
                            kind = currentInsert,
                            urlInput = inlineUrlInput,
                            titleInput = inlineTitleInput,
                            onUrlChange = { inlineUrlInput = it },
                            onTitleChange = { inlineTitleInput = it },
                            onDismiss = {
                                activeInsertKind = null
                                focusRequester.requestFocus()
                                keyboard?.show()
                            },
                            onConfirm = {
                                val inserted = when (currentInsert) {
                                    InsertKind.Url -> {
                                        val u = inlineUrlInput.trim()
                                        val t = inlineTitleInput.trim().ifEmpty { u }
                                        "[url=$u]$t[/url]"
                                    }
                                    InsertKind.NetImage -> "[img]${inlineUrlInput.trim()}[/img]"
                                    InsertKind.Audio -> "[audio]${inlineUrlInput.trim()}[/audio]"
                                    InsertKind.Video -> "[media=x,500,375]${inlineUrlInput.trim()}[/media]"
                                    InsertKind.Flash -> "[flash]${inlineUrlInput.trim()}[/flash]"
                                    InsertKind.Quote -> "[quote]${inlineUrlInput.trim()}[/quote]"
                                    InsertKind.Code -> "[code]${inlineUrlInput.trim()}[/code]"
                                }
                                onValueChange(value.insertAtSelection(inserted))
                                activeInsertKind = null
                                focusRequester.requestFocus()
                                keyboard?.show()
                            },
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    } else {
                        RecentSmileyRow(recentRow) { code -> pickSmiley(code) }
                    }
                }
                ComposerPanel.Insert -> InsertPanel(
                    onPick = { kind ->
                        // 如果有选中文本且为引用/代码，可直接包裹
                        val selStart = min(value.selection.min, value.selection.max)
                        val selEnd = max(value.selection.min, value.selection.max)
                        val selected = if (selEnd > selStart) value.text.substring(selStart, selEnd) else ""
                        if (selected.isNotEmpty() && (kind == InsertKind.Quote || kind == InsertKind.Code)) {
                            val tag = if (kind == InsertKind.Quote) "quote" else "code"
                            onValueChange(value.wrapSelectionOrInsert("[$tag]", "[/$tag]"))
                            panel = ComposerPanel.None
                            focusRequester.requestFocus()
                            keyboard?.show()
                        } else {
                            // 打开就地内联条，键盘完全不收起，0 弹窗 0 闪烁
                            activeInsertKind = kind
                            inlineUrlInput = ""
                            inlineTitleInput = selected
                            panel = ComposerPanel.None
                        }
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )
                ComposerPanel.Smiley -> SmileyPanel(
                    packs = smileyPacks,
                    recentCodes = recentCodes,
                    onPick = { code -> pickSmiley(code) },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private enum class ComposerPanel { None, Smiley, Insert }

/**
 * 把正文里的表情码（`[#呵呵]`）换成行内表情占位。
 *
 * alternateText 必须与表情码等长：VisualTransformation 用 Identity 映射，两者一旦不等长，
 * 光标位置与选区就会整体偏移。
 */
/** 光标所在位置之前是否有一个表情码：是则返回它的起始下标，否则 -1。 */
private fun smileyCodeStartBefore(text: String, cursor: Int): Int {
    if (cursor <= 0 || cursor > text.length) return -1
    val open = text.lastIndexOf('[', (cursor - 1).coerceAtLeast(0))
    if (open < 0) return -1
    val close = text.indexOf(']', open + 1)
    if (close < 0 || close + 1 > text.length) return -1
    // 光标落在表情码内部或紧接其尾，才算「正在删这个表情」
    return if (cursor <= close + 1) open else -1
}

/** 正文里的表情码：区间（起、止下标）与码本身，顺序即出现顺序。 */
private fun smileyRangesOf(text: String, smileyByCode: Map<String, Smiley>): List<Pair<IntRange, String>> {
    val found = mutableListOf<Pair<IntRange, String>>()
    var index = 0
    while (index < text.length) {
        val close = if (text[index] == '[') text.indexOf(']', index + 1) else -1
        if (close > index + 1) {
            val code = text.substring(index, close + 1)
            if (smileyByCode.containsKey(code)) {
                found += (index until close + 1) to code
                index = close + 1
                continue
            }
        }
        index++
    }
    return found
}

/**
 * 把正文里的表情码（`[#呵呵]`）染成透明，同时把 BBCode 标记（链接、引用、代码等）施加富文本视觉样式。
 *
 * 关键约束：字符总数与每个字符的下标必须 100% 保持不变，这样 OffsetMapping 用 Identity
 * 才能杜绝任何输入法 IME 光标抖动或越界崩溃。
 */
private fun formatRichComposerText(
    source: String,
    smileyByCode: Map<String, Smiley>,
    colors: com.aazhou.mtforum.ui.theme.MtfColors,
): AnnotatedString {
    return buildAnnotatedString {
        append(source)
        if (source.isEmpty()) return@buildAnnotatedString

        // 1. 表情码染成透明色（图片在 Overlay 层绘制）
        if (smileyByCode.isNotEmpty() && source.contains('[')) {
            val smileyRanges = smileyRangesOf(source, smileyByCode)
            for ((range, _) in smileyRanges) {
                addStyle(SpanStyle(color = Color.Transparent), range.first, range.last + 1)
            }
        }

        // 2. 文字链接富文本：[url=网址]链接文字[/url]
        val urlRegex = Regex("""\[url=([^\]]+)\]([\s\S]*?)\[/url\]""", RegexOption.IGNORE_CASE)
        for (match in urlRegex.findAll(source)) {
            val entireRange = match.range
            val openTagLen = match.groups[1]!!.range.first - entireRange.first // "[url="
            val openTagEnd = match.groups[1]!!.range.last + 2 // 包含结尾 "]"
            val textGroup = match.groups[2]!!

            // 弱化 [url=网址] 前缀标签
            addStyle(
                SpanStyle(color = colors.textHint.copy(alpha = 0.45f), fontSize = 11.sp),
                entireRange.first,
                openTagEnd,
            )
            // 高亮链接文字：蓝色 + 下划线 + 加粗，呈现真实的链接属性
            addStyle(
                SpanStyle(
                    color = Color(0xFF2563EB),
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.SemiBold,
                ),
                textGroup.range.first,
                textGroup.range.last + 1,
            )
            // 弱化 [/url] 闭合标签
            addStyle(
                SpanStyle(color = colors.textHint.copy(alpha = 0.45f), fontSize = 11.sp),
                textGroup.range.last + 1,
                entireRange.last + 1,
            )
        }

        // 3. 引用块富文本：[quote]...[/quote]
        val quoteRegex = Regex("""\[quote\]([\s\S]*?)\[/quote\]""", RegexOption.IGNORE_CASE)
        for (match in quoteRegex.findAll(source)) {
            val entireRange = match.range
            val textGroup = match.groups[1]!!
            // 弱化标签
            addStyle(SpanStyle(color = colors.textHint.copy(alpha = 0.45f), fontSize = 11.sp), entireRange.first, textGroup.range.first)
            // 引用内容：斜体 + 次级文本颜色
            addStyle(SpanStyle(color = colors.textSecondary, fontStyle = FontStyle.Italic), textGroup.range.first, textGroup.range.last + 1)
            addStyle(SpanStyle(color = colors.textHint.copy(alpha = 0.45f), fontSize = 11.sp), textGroup.range.last + 1, entireRange.last + 1)
        }

        // 4. 代码块富文本：[code]...[/code]
        val codeRegex = Regex("""\[code\]([\s\S]*?)\[/code\]""", RegexOption.IGNORE_CASE)
        for (match in codeRegex.findAll(source)) {
            val entireRange = match.range
            val textGroup = match.groups[1]!!
            // 弱化标签
            addStyle(SpanStyle(color = colors.textHint.copy(alpha = 0.45f), fontSize = 11.sp), entireRange.first, textGroup.range.first)
            // 代码内容：等宽字体 + 浅底色
            addStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = colors.textHint.copy(alpha = 0.12f)),
                textGroup.range.first,
                textGroup.range.last + 1,
            )
            addStyle(SpanStyle(color = colors.textHint.copy(alpha = 0.45f), fontSize = 11.sp), textGroup.range.last + 1, entireRange.last + 1)
        }

        // 5. 多媒体标签弱化：[img]、[audio]、[media]、[flash]、[attachimg]
        val mediaRegex = Regex("""\[(img|audio|media[^\]]*|flash|attachimg)\]([\s\S]*?)\[/\1\]""", RegexOption.IGNORE_CASE)
        for (match in mediaRegex.findAll(source)) {
            val entireRange = match.range
            val textGroup = match.groups[2]!!
            addStyle(SpanStyle(color = colors.textHint.copy(alpha = 0.45f), fontSize = 11.sp), entireRange.first, textGroup.range.first)
            addStyle(SpanStyle(color = colors.textSecondary, fontSize = 13.sp), textGroup.range.first, textGroup.range.last + 1)
            addStyle(SpanStyle(color = colors.textHint.copy(alpha = 0.45f), fontSize = 11.sp), textGroup.range.last + 1, entireRange.last + 1)
        }
    }
}

/**
 * 输入框里的表情贴图层：按文本布局坐标把表情码的位置替换成图片。
 *
 * BasicTextField 各版本都没有 inlineContent 参数（已解包 foundation 的 classes.jar 用 javap
 * 核实），行内图片只能自己贴。这一层垫在输入框下面：文字已被染成透明色，光标与选区仍由
 * 输入框画在最上层，不会被图片盖住。
 */
@Composable
private fun SmileyOverlay(
    text: String,
    layout: TextLayoutResult?,
    smileyByCode: Map<String, Smiley>,
) {
    if (layout == null) return
    // 区间只能按布局自己的文本算：插入表情时文本先变、布局回调后到，用旧的布局配新的区间
    // 会 getBoundingBox 越界直接崩（offset 超出 [0, 0)）。对不上就等下一帧，不画这一帧
    val source = layout.layoutInput.text.text
    if (source != text || smileyByCode.isEmpty()) return
    val ranges = remember(source, smileyByCode) { smileyRangesOf(source, smileyByCode) }
    if (ranges.isEmpty()) return
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        ranges.forEach { (range, code) ->
            val smiley = smileyByCode[code] ?: return@forEach
            val first = layout.getBoundingBox(range.first)
            val last = if (range.last > range.first) layout.getBoundingBox(range.last) else first
            // 表情码被行尾截断时首末字符不同行：只按首字符那一格画，画不全但不越界
            val box = if (last.top != first.top) first else Rect(first.left, first.top, last.right, last.bottom)
            // 取行高做边长、水平居中：字符区间是按 `[#呵呵]` 五个方括号字符算的，直接铺会拉宽变形
            val side = box.height
            val left = box.left + (box.width - side) / 2f
            with(density) {
                AsyncImage(
                    model = smiley.url,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .absoluteOffset(left.toDp(), box.top.toDp())
                        .requiredSize(side.toDp()),
                )
            }
        }
    }
}

/** 「插入」面板里的条目：与网页端 7 项完全一致。 */
private enum class InsertKind(val label: String) {
    Url("文字链接"),
    NetImage("网络图片"),
    Audio("MP3 音乐"),
    Video("网络视频"),
    Flash("Flash"),
    Quote("引用"),
    Code("代码"),
}

/** 在光标处插入 [text]，插完光标落在插入内容里第 [cursorOffsetInInserted] 个字符处。 */
private fun TextFieldValue.insertAtSelection(
    text: String,
    cursorOffsetInInserted: Int = text.length,
): TextFieldValue {
    val start = min(selection.min, selection.max).coerceIn(0, this.text.length)
    val end = max(selection.min, selection.max).coerceIn(0, this.text.length)
    val merged = this.text.substring(0, start) + text + this.text.substring(end)
    val cursor = start + cursorOffsetInInserted.coerceIn(0, text.length)
    return copy(text = merged, selection = TextRange(cursor))
}

/**
 * 包装当前选区，或在光标处插入带前缀/后缀的模板；
 * 若无选区，光标自动停在 [prefix] 之后供用户立即输入。
 */
private fun TextFieldValue.wrapSelectionOrInsert(
    prefix: String,
    suffix: String,
): TextFieldValue {
    val start = min(selection.min, selection.max).coerceIn(0, text.length)
    val end = max(selection.min, selection.max).coerceIn(0, text.length)
    val selected = text.substring(start, end)
    return if (selected.isNotEmpty()) {
        val merged = text.substring(0, start) + prefix + selected + suffix + text.substring(end)
        copy(text = merged, selection = TextRange(start + prefix.length + selected.length + suffix.length))
    } else {
        val merged = text.substring(0, start) + prefix + suffix + text.substring(end)
        copy(text = merged, selection = TextRange(start + prefix.length))
    }
}

/** 包装或插入链接 BBCode，光标停在网址位置。 */
private fun TextFieldValue.wrapUrl(): TextFieldValue {
    val start = min(selection.min, selection.max).coerceIn(0, text.length)
    val end = max(selection.min, selection.max).coerceIn(0, text.length)
    val selected = text.substring(start, end)
    return if (selected.isNotEmpty()) {
        val merged = text.substring(0, start) + "[url=]" + selected + "[/url]" + text.substring(end)
        copy(text = merged, selection = TextRange(start + 5))
    } else {
        val merged = text.substring(0, start) + "[url=][/url]" + text.substring(end)
        copy(text = merged, selection = TextRange(start + 5))
    }
}

@Composable
private fun ComposerToolButton(iconRes: Int, label: String, onClick: () -> Unit) {
    Icon(
        painterResource(iconRes),
        contentDescription = label,
        tint = LocalMtfColors.current.textSecondary,
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick)
            .padding(12.dp),
    )
}

/** 表情面板：展开时占住键盘原来的位置，横向切包，网格点选插入。 */
@Composable
private fun SmileyPanel(
    packs: List<SmileyPack>,
    recentCodes: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (packs.isEmpty()) {
        Text(
            "表情加载失败",
            style = MaterialTheme.typography.bodySmall,
            color = LocalMtfColors.current.textHint,
            modifier = modifier.padding(vertical = 12.dp),
        )
        return
    }
    // 0 = 最近使用，其余按下标对应各个包。默认停在第一个包：最近使用是给用过的人翻历史的，
    // 不该拿它当默认页——用过几个表情的人一开面板只看到那几个，整页就像空了一样
    var packIndex by remember(packs.isEmpty()) { mutableStateOf(if (packs.isEmpty()) 0 else 1) }
    val smileyByCode = remember(packs) { packs.flatMap { it.items }.associateBy { it.code } }
    val recent = recentCodes.mapNotNull { smileyByCode[it] }
    // 最近使用为空时不能就此空格：按默认表情表补，站点里没有才退回第一个包。
    // 否则没用过表情的人一点开表情面板，看到的只有分类栏、网格全空
    val emojis = when (packIndex) {
        0 -> recent.ifEmpty {
            DefaultSmileyCodes.mapNotNull { smileyByCode[it] }
                .ifEmpty { packs.firstOrNull()?.items.orEmpty() }
        }
        else -> packs[packIndex - 1].items
    }
    Column(modifier) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                SmileyPackTab(selected = packIndex == 0, onClick = { packIndex = 0 }) {
                    Icon(
                        painterResource(R.drawable.ic_recent),
                        contentDescription = "最近使用",
                        tint = LocalMtfColors.current.textSecondary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            itemsIndexed(packs) { index, pack ->
                SmileyPackTab(selected = packIndex == index + 1, onClick = { packIndex = index + 1 }) {
                    AsyncImage(
                        model = pack.items.first().url,
                        contentDescription = pack.name,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // 固定高度：表情少的时候也占和满表情一样高，不会忽大忽小。不拿 Column 的剩余空间——
        // 外部高度一旦随键盘变化，网格就会被压成一条 tab 栏
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier
                .fillMaxWidth()
                .height(ComposerSmileyPanelHeight),
        ) {
            emojis.forEach { smiley ->
                item(key = smiley.code) { SmileyCell(smiley, onPick) }
            }
        }
    }
}

/** 表情包切换标签：选中态垫一层浅底。 */
@Composable
private fun SmileyPackTab(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(
                if (selected) {
                    LocalMtfColors.current.textHint.copy(alpha = 0.15f)
                } else {
                    Color.Transparent
                },
                AbsoluteRoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** 默认状态下工具栏下面那一行：最近用过的表情，不足 8 个按默认表情表补满。 */
@Composable
private fun RecentSmileyRow(smileys: List<Smiley>, onPick: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            // 不占满剩余高度：这一行现在是内容列的直接子节点，fillMaxHeight 会把它一路撑到
            // 键盘正上方，中间空出几百 dp
            .padding(top = EmojiRowToolbarGap),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 按 code 作 key：点一个表情后它在最近列表里前移，按位置渲染会让每个格子换成另一张图，
        // AsyncImage 换 model 重新加载就是一下闪白
        smileys.forEach { smiley ->
            key(smiley.code) {
                SmileyCell(smiley, onPick, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SmileyCell(smiley: Smiley, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    AsyncImage(
        model = smiley.url,
        contentDescription = smiley.code,
        modifier = modifier
            .aspectRatio(1f)
            .padding(8.dp)
            .clickable { onPick(smiley.code) },
    )
}

/** 「插入」面板：列出可插入的标记类型，与网页端 7 项完全一致。 */
@Composable
private fun InsertPanel(onPick: (InsertKind) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(InsertKind.values()) { kind ->
            Text(
                kind.label,
                style = MaterialTheme.typography.labelMedium,
                color = LocalMtfColors.current.textSecondary,
                modifier = Modifier
                    .clickable { onPick(kind) }
                    .background(LocalMtfColors.current.background, AbsoluteRoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** 就地内联插入条：在键盘保持升起状态下直接输入网址/标题并插入，0 弹窗 0 闪烁。 */
@Composable
private fun InlineInsertBar(
    kind: InsertKind,
    urlInput: String,
    titleInput: String,
    onUrlChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMtfColors.current
    val isUrl = kind == InsertKind.Url
    val ready = if (isUrl) urlInput.isNotBlank() else urlInput.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background, AbsoluteRoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = kind.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            BasicTextField(
                value = urlInput,
                onValueChange = onUrlChange,
                singleLine = true,
                textStyle = TextStyle(color = bodyTextColor(), fontSize = 14.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    if (urlInput.isEmpty()) {
                        Text(
                            text = when (kind) {
                                InsertKind.Url -> "请输入链接网址 (https://...)"
                                InsertKind.NetImage -> "请输入图片网址"
                                InsertKind.Audio -> "请输入音频网址"
                                InsertKind.Video -> "请输入视频网址"
                                InsertKind.Flash -> "请输入 Flash 网址"
                                InsertKind.Quote -> "请输入引用内容"
                                InsertKind.Code -> "请输入代码"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textHint,
                        )
                    }
                    inner()
                },
                modifier = Modifier
                    .weight(1f)
                    .background(colors.surface, AbsoluteRoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
            Text(
                text = "插入",
                style = MaterialTheme.typography.labelMedium,
                color = if (ready) Color.White else colors.textHint,
                modifier = Modifier
                    .background(
                        if (ready) ComposerPublishColor else colors.textHint.copy(alpha = 0.2f),
                        AbsoluteRoundedCornerShape(50),
                    )
                    .clickable(enabled = ready, onClick = onConfirm)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Text(
                text = "✕",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textHint,
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }
        if (isUrl) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "文字",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                )
                BasicTextField(
                    value = titleInput,
                    onValueChange = onTitleChange,
                    singleLine = true,
                    textStyle = TextStyle(color = bodyTextColor(), fontSize = 14.sp),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        if (titleInput.isEmpty()) {
                            Text("显示文字（选填，留空则同网址）", style = MaterialTheme.typography.bodySmall, color = colors.textHint)
                        }
                        inner()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface, AbsoluteRoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/**
 * 成对 BBCode 标签匹配正则（audio、url、img、media、flash、quote、code、attachimg）。
 * Group 1: 开始标签（如 `[audio]` 或 `[url=...]`）
 * Group 2: 标签名
 * Group 3: 中间包含的内容（可为空）
 * Group 4: 闭合标签（如 `[/audio]` 或 `[/url]`）
 */
private val COMPOSER_TAG_PAIR_REGEX = Regex(
    """(\[(url(?:=[^\]]*)?|audio|img|media(?:=[^\]]*)?|flash|quote|code|attachimg)\])([\s\S]*?)(\[/(?:url|audio|img|media|flash|quote|code|attachimg)\])""",
    RegexOption.IGNORE_CASE,
)

/**
 * 智能成对标签退格：
 * 当用户按退格键（Backspace）删除了某个 BBCode 标签的一部分，或在标签边缘退格时，
 * 将两边的配对代码（如 `[audio]` 与 `[/audio]`）一体化删除：
 * - 若内容为空（如 `[audio][/audio]`），整对标签彻底消失；
 * - 若内容非空（如 `[audio]哈哈[/audio]`），两边的代码整体移除，保留正文内容 `哈哈`。
 */
internal fun handleTagPairDeletion(oldValue: TextFieldValue, newValue: TextFieldValue): TextFieldValue? {
    val oldText = oldValue.text
    val deletedIndex = newValue.selection.start
    if (deletedIndex < 0 || deletedIndex >= oldText.length) return null

    val matches = COMPOSER_TAG_PAIR_REGEX.findAll(oldText)
    for (match in matches) {
        val openGroup = match.groups[1] ?: continue
        val contentGroup = match.groups[3] ?: continue
        val closeGroup = match.groups[4] ?: continue
        val entireRange = match.range

        val openRange = openGroup.range
        val contentRange = contentGroup.range
        val closeRange = closeGroup.range

        // 判定是否在删除该标签对：
        // 1. 被删字符落在闭合标签内（例如在 [/audio] 后按退格）
        // 2. 被删字符落在开始标签内（例如在 [audio] 处按退格）
        // 3. 内容为空且在两对标签交界处退格
        val isDeletingCloseTag = deletedIndex in closeRange
        val isDeletingOpenTag = deletedIndex in openRange
        val isDeletingEmptyPair = contentRange.isEmpty() && (deletedIndex == openRange.last || deletedIndex == closeRange.first)

        if (isDeletingCloseTag || isDeletingOpenTag || isDeletingEmptyPair) {
            val content = if (contentRange.isEmpty()) "" else oldText.substring(contentRange.first, contentRange.last + 1)
            return if (content.isEmpty()) {
                // 内容为空：彻底把整个 [tag][/tag] 删除
                val resultText = oldText.removeRange(entireRange.first, entireRange.last + 1)
                TextFieldValue(resultText, selection = TextRange(entireRange.first))
            } else {
                // 内容非空：将两边的配对标签代码一体化删除，保留中间的内容文本
                val resultText = oldText.substring(0, entireRange.first) + content + oldText.substring(entireRange.last + 1)
                val newCursor = if (isDeletingCloseTag) {
                    entireRange.first + content.length
                } else {
                    entireRange.first
                }
                TextFieldValue(resultText, selection = TextRange(newCursor))
            }
        }
    }
    return null
}

/** 把相册选中的图压到站点限制内（长边 1500px、≤1MB），返回待上传的缓存文件。 */
internal suspend fun prepareUploadFile(context: android.content.Context, uri: Uri): File =
    withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        // 全程流式解码，不把原图整个读进内存：相机直出的照片几十 MB，readBytes 会 OOM
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw IllegalStateException("读不到所选图片")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalStateException("图片解码失败")
        }
        val longEdge = max(bounds.outWidth, bounds.outHeight)
        val cache = File(context.cacheDir, "reply_upload.jpg")
        // 原图没超限就原样拷过去：GIF 动图重编码会丢帧
        val rawSize = resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
        if (rawSize in 1..MAX_UPLOAD_BYTES && longEdge in 1 until MAX_UPLOAD_EDGE) {
            resolver.openInputStream(uri)?.use { input ->
                cache.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("读不到所选图片")
            return@withContext cache
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = if (longEdge > MAX_UPLOAD_EDGE) longEdge / MAX_UPLOAD_EDGE else 1
        }
        val decoded = resolver.openInputStream(uri)
            ?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IllegalStateException("图片解码失败")
        val scale = min(
            MAX_UPLOAD_EDGE.toFloat() / decoded.width,
            MAX_UPLOAD_EDGE.toFloat() / decoded.height,
        ).coerceAtMost(1f)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            decoded
        }
        // 压不到站点限制内就明说，别把超限的文件传上去让站点含糊地拒掉
        var quality = 90
        while (true) {
            cache.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            if (cache.length() <= MAX_UPLOAD_BYTES) break
            if (quality <= MIN_UPLOAD_QUALITY) {
                throw IllegalStateException("这张图压不到 1MB 以内，换一张小一点的试试")
            }
            quality -= 15
        }
        cache
    }

/** 站点限制：单张 1MB，网页端也会把图缩到 1500px 再传。 */
private const val MAX_UPLOAD_BYTES = 1024 * 1024L
private const val MAX_UPLOAD_EDGE = 1500
private const val MIN_UPLOAD_QUALITY = 15

/** 输入框高度：固定值，不随内容长高。 */
private val ReplyComposerInputHeight = 84.dp

/** 最近使用那一行摆几个。 */
private const val RecentSmileyLimit = 8

/** 键盘一次都没起来过时，表情面板按这个高度占位。 */
private val ComposerSmileyPanelHeight = 310.dp

/** 工具栏与表情行之间的间距。 */
private val EmojiRowToolbarGap = 2.dp

/** 发布按钮底色。 */
private val ComposerPublishColor = Color(0xFF97CDAD)

/**
 * 默认表情行：最近用过的补不满时按这些补。
 *
 * code 必须带站点笑脸表里的方括号（SmileyParser 原样产出 `[#呵呵]`，插入正文的也正是这个
 * 标记）；写成分组匹配的 `#呵呵` 会一个都取不到，整行表情直接消失。
 */
private val DefaultSmileyCodes =
    listOf("[#呵呵]", "[#滑稽]", "[#哈哈]", "[#吐舌]", "[#啊]", "[#酷]", "[#怒]", "[#开心]")
