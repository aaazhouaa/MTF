package com.aazhou.mtforum.ui.screen.threaddetail

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ripple
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.unit.Dp
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
import java.io.InputStream
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
    uploadCredentials: Pair<String, String>?,
    onUploadImage: suspend (File) -> String?,
    /** 「@朋友」候选名单：与站点网页端编辑器同源，输入 @ 时按关键字过滤，点候选插 `@用户名 `。 */
    atUsers: List<String>,
    /** 打开「@朋友」选择页：默认列出好友与关注，可全局搜索全站用户。 */
    onOpenAtPicker: () -> Unit,
    /** 外部请求聚焦正文的计数：从「@朋友」选择页回来时 +1，把光标和键盘请回正文。 */
    focusRequest: Int = 0,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
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
        keyboard?.show()
    }

    // 选择页的搜索框抢过焦点，回来必须显式要一次，否则键盘不再弹出、光标也不知去向
    LaunchedEffect(focusRequest) {
        if (focusRequest > 0) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    val density = LocalDensity.current
    var activeInsertKind by remember { mutableStateOf<InsertKind?>(null) }
    var inlineUrlInput by remember { mutableStateOf("") }
    var inlineTitleInput by remember { mutableStateOf("") }
    var pendingSelection by remember { mutableStateOf<TextRange?>(null) }

    val smileyByCode = remember(smileyPacks) {
        smileyPacks.flatMap { it.items }.associateBy { it.code }
    }

    val primaryColor = MaterialTheme.colorScheme.primary

    val smileyVisualTransformation = remember(smileyByCode, colors, primaryColor) {
        VisualTransformation { text ->
            TransformedText(
                formatRichComposerText(text.text, smileyByCode, colors, primaryColor),
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
        if (code !in DefaultSmileyCodes) {
            onSmileyUsed(code)
        }
    }

    // 快捷表情行：最近使用的排在最前（不含这 8 个默认表情）；不足 8 个按默认表情表顺延往前补齐替换
    val recentRow = run {
        val nonDefaultRecent = recentCodes.filter { it !in DefaultSmileyCodes }
        val used = nonDefaultRecent.mapNotNull { smileyByCode[it] }
        val wanted = DefaultSmileyCodes.mapNotNull { smileyByCode[it] }
        val filler = wanted.filter { smiley -> nonDefaultRecent.none { it == smiley.code } }
        (used + filler).take(RecentSmileyLimit)
    }

    val imeInsets = WindowInsets.ime
    val navInsets = WindowInsets.navigationBars

    val imeHeight = with(density) { imeInsets.getBottom(density).toDp() }
    val navHeight = with(density) { navInsets.getBottom(density).toDp() }

    // 表情面板占位高度：打开那一刻捕获到的真实键盘高度，之后恒定不变，直到下次再打开。
    // 表情面板与键盘共用这一个底部槽，二者严格等高，切换时面板既不上窜也不下塌
    var smileySlot by remember {
        mutableStateOf(KeyboardTracker.getInitialHeight(context, density))
    }

    var keyboardHeight by remember {
        mutableStateOf(KeyboardTracker.getInitialHeight(context, density))
    }

    // 只在键盘停稳之后记录它的高度。起落动画途中的 ime 值是中间值，键盘收起时高度一路
    // 递减，边收边记会把记录值越写越小，之后打开表情面板就按这个残值占位，面板明显比
    // 刚才的键盘矮一截。连续多帧同一高度才算停稳，双向更新，切换更矮的键盘也能跟上
    LaunchedEffect(Unit) {
        var lastPx = -1
        var stableFrames = 0
        while (true) {
            withFrameNanos { }
            val px = imeInsets.getBottom(density)
            if (px > 0) {
                if (px == lastPx) stableFrames++ else { lastPx = px; stableFrames = 0 }
                if (stableFrames >= STABLE_KEYBOARD_FRAMES) {
                    val height = with(density) { px.toDp() }
                    if (height > MinKeyboardHeight) {
                        keyboardHeight = height
                        KeyboardTracker.save(context, px)
                    }
                    lastPx = -1
                    stableFrames = 0
                }
            } else {
                lastPx = -1
                stableFrames = 0
            }
        }
    }

    // 从表情面板切回键盘的交接期：期间底部槽仍锁死在表情面板的高度，键盘从屏幕底部
    // 升起、把表情面板逐步盖住，升到位后才改由 ime 接管，全程高度不变
    var keyboardHandoff by remember { mutableStateOf(false) }

    fun switchToKeyboard() {
        if (panel == ComposerPanel.Smiley) {
            keyboardHandoff = true
        }
        focusRequester.requestFocus()
        keyboard?.show()
    }

    // 表情面板展开时按返回键：直接整体收起输入面板，与点遮罩收起面板同一路子，
    // 不再 intermediate 地先把键盘唤出来
    BackHandler(enabled = panel == ComposerPanel.Smiley) {
        panel = ComposerPanel.None
        onDismiss()
    }

    // 交接期只有两个出口：键盘确实升起来并停稳（改由 ime 接管、表情面板退场），或者
    // 半天没动静（放弃交接，表情面板原地留在原位，高度依旧不变）。不用固定时长的兜底：
    // 到点硬切会把面板从锁定高度直接掉到当时的 ime 高度，键盘起得慢一下就塌一截
    LaunchedEffect(keyboardHandoff) {
        if (!keyboardHandoff) return@LaunchedEffect
        var lastPx = -1
        var stableFrames = 0
        var idleFrames = 0
        while (keyboardHandoff) {
            withFrameNanos { }
            val px = imeInsets.getBottom(density)
            if (px > 0) {
                idleFrames = 0
                if (px == lastPx) stableFrames++ else { lastPx = px; stableFrames = 0 }
                if (stableFrames >= STABLE_KEYBOARD_FRAMES) break
            } else {
                lastPx = -1
                stableFrames = 0
                idleFrames++
                if (idleFrames >= HANDOFF_GIVEUP_FRAMES) break
            }
        }
        keyboardHandoff = false
        if (imeInsets.getBottom(density) > 0 && panel == ComposerPanel.Smiley) {
            panel = ComposerPanel.None
        }
    }

    val isSmileyOpen = panel == ComposerPanel.Smiley
    // 底部槽高度只有一个真源：表情面板展开时锁死在捕获到的键盘高度，交接期改由那 36dp
    // 子栏承载快捷表情行（键盘升起第一帧它就出现，底部槽相应少让 36dp），其余时候跟随
    // ime（键盘起落必须让位），两者都没有就只留导航栏
    val bottomSlotHeight = when {
        isSmileyOpen && !keyboardHandoff -> smileySlot + ComposerSubBarHeight
        keyboardHandoff -> smileySlot
        imeHeight > 0.dp -> imeHeight
        else -> navHeight
    }

    Box(
        modifier
            .fillMaxWidth()
            // 通栏实色底（浅色白、深色取 surface）、只圆上角、底部贴底栏/键盘：
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
                .padding(top = 12.dp),
        ) {
            // 输入区：占满宽度，文字顶格；下方留出空白点击区跳转光标，不破坏 BasicTextField 原生触摸/长按选词
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ReplyComposerInputHeight)
                    .padding(horizontal = 14.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
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
                        textStyle = TextStyle(color = bodyTextColor(), fontSize = 15.sp, lineHeight = 22.sp),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        visualTransformation = smileyVisualTransformation,
                        onTextLayout = { textLayout = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .focusRequester(focusRequester),
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
                    // 表情面板展开时，点击输入文字区域平滑切回键盘
                    if (panel == ComposerPanel.Smiley) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ) {
                                    switchToKeyboard()
                                },
                        )
                    }
                }
                // 文字下方的空白点击区：点击跳转到末尾，不与文字区域的选词/拖拽手势重叠
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) {
                            onValueChange(value.copy(selection = TextRange(value.text.length)))
                            switchToKeyboard()
                        },
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ComposerToolButton(R.drawable.ic_smiley, "表情") {
                        if (panel == ComposerPanel.Smiley) {
                            switchToKeyboard()
                        } else {
                            keyboardHandoff = false
                            // 捕获这一刻的真实键盘高度：键盘还开着就直接取现值，正在收起或
                            // 已经收起就退回停稳记录值。这样表情面板与刚才那个键盘严格等高
                            smileySlot = if (imeHeight > MinKeyboardHeight && imeHeight >= keyboardHeight) {
                                imeHeight
                            } else {
                                keyboardHeight
                            }
                            panel = ComposerPanel.Smiley
                            focusManager.clearFocus()
                            keyboard?.hide()
                        }
                    }
                    ComposerToolButton(R.drawable.ic_image, "图片") {
                        if (uploadCredentials != null) {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        } else {
                            AppToast.show("上传凭据已失效，返回帖子重进试试")
                        }
                    }
                    // @：打开「@朋友」选择页（好友/关注 + 全站搜索）
                    ComposerToolButton(R.drawable.ic_at, "提到朋友") { onOpenAtPicker() }
                    ComposerToolButton(R.drawable.ic_add, "插入") {
                        if (panel == ComposerPanel.Smiley) {
                            keyboardHandoff = true
                        }
                        panel = if (panel == ComposerPanel.Insert) ComposerPanel.None else ComposerPanel.Insert
                        focusRequester.requestFocus()
                        keyboard?.show()
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (sending || uploading) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    val publishReady = value.text.isNotBlank()
                    val primaryColor = MaterialTheme.colorScheme.primary
                    Text(
                        "发布",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (publishReady) Color.White else colors.textHint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(
                                if (publishReady) primaryColor.copy(alpha = 0.69f) else colors.textHint.copy(alpha = 0.2f),
                                AbsoluteRoundedCornerShape(50),
                            )
                            .clickable(enabled = publishReady, onClick = onSend)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }

            // @候选条：光标正落在 @关键字 里时才出现，贴在工具栏上方。表情/插入面板开着
            // 时说明没在打字，这时候浮一层候选只会挡住面板
            val atToken = if (value.selection.collapsed) {
                atTokenAt(value.text, value.selection.start)
            } else {
                null
            }
            if (atToken != null && panel == ComposerPanel.None && activeInsertKind == null) {
                val atSuggestions = filterAtUsers(atUsers, atToken.second)
                if (atSuggestions.isNotEmpty()) {
                    AtSuggestionStrip(
                        suggestions = atSuggestions,
                        onPick = { name ->
                            // 整段 @关键字 换成 `@用户名 `，尾空格与站点一致，免得紧贴后续文字
                            val end = value.selection.start
                            val merged = value.text.replaceRange(atToken.first, end, "@$name ")
                            val cursor = atToken.first + name.length + 2
                            onValueChange(value.copy(text = merged, selection = TextRange(cursor)))
                        },
                    )
                }
            }

            // 工具栏下方的内容区：
            // 1. 若正在输入链接/媒体：显示 InlineInsertBar
            // 2. 若点击了「+」号：显示 InsertPanel（软键盘保持升起）
            // 3. 正常打字（None）：显示 8 个快捷表情行
            // 4. 打开大表情面板（Smiley）：不显示快捷行，避免上下重复
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
                            InsertKind.Bold -> "[b]${inlineUrlInput.trim()}[/b]"
                            InsertKind.Italic -> "[i]${inlineUrlInput.trim()}[/i]"
                            InsertKind.Underline -> "[u]${inlineUrlInput.trim()}[/u]"
                            InsertKind.Strikethrough -> "[s]${inlineUrlInput.trim()}[/s]"
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
                        val targetRange = pendingSelection ?: value.selection
                        val start = min(targetRange.min, targetRange.max).coerceIn(0, value.text.length)
                        val end = max(targetRange.min, targetRange.max).coerceIn(0, value.text.length)
                        val merged = value.text.substring(0, start) + inserted + value.text.substring(end)
                        val cursor = start + inserted.length
                        onValueChange(value.copy(text = merged, selection = TextRange(cursor)))
                        pendingSelection = null
                        activeInsertKind = null
                        focusRequester.requestFocus()
                        keyboard?.show()
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else if (panel == ComposerPanel.Insert) {
                InsertPanel(
                    onPick = { kind ->
                        val selStart = min(value.selection.min, value.selection.max)
                        val selEnd = max(value.selection.min, value.selection.max)
                        val selected = if (selEnd > selStart) value.text.substring(selStart, selEnd) else ""
                        val isTextFormat = kind in listOf(
                            InsertKind.Bold,
                            InsertKind.Italic,
                            InsertKind.Underline,
                            InsertKind.Strikethrough,
                            InsertKind.Quote,
                            InsertKind.Code,
                        )
                        if (selected.isNotEmpty() && isTextFormat) {
                            val tag = when (kind) {
                                InsertKind.Bold -> "b"
                                InsertKind.Italic -> "i"
                                InsertKind.Underline -> "u"
                                InsertKind.Strikethrough -> "s"
                                InsertKind.Quote -> "quote"
                                InsertKind.Code -> "code"
                                else -> ""
                            }
                            onValueChange(value.wrapSelectionOrInsert("[$tag]", "[/$tag]"))
                            panel = ComposerPanel.None
                            focusRequester.requestFocus()
                            keyboard?.show()
                        } else {
                            activeInsertKind = kind
                            inlineUrlInput = ""
                            inlineTitleInput = selected
                            pendingSelection = value.selection
                            panel = ComposerPanel.None
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ComposerSubBarHeight)
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                )
            } else if (panel == ComposerPanel.None || keyboardHandoff) {
                RecentSmileyRow(
                    smileys = recentRow,
                    onPick = { code -> pickSmiley(code) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ComposerSubBarHeight)
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                )
            }

            // 键盘与表情面板的下部容器：高度锁定为 bottomSlotHeight，键盘收起/弹出时输入框纹丝不动
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bottomSlotHeight),
            ) {
                // 一旦开始切回键盘，表情页立刻让位变空白：底部槽只剩一块实色区域等键盘
                // 升起来填充，不再让表情网格被键盘一点点盖上去
                if (panel == ComposerPanel.Smiley && !keyboardHandoff) {
                    SmileyPanel(
                        packs = smileyPacks,
                        recentCodes = recentCodes,
                        onPick = { code -> pickSmiley(code) },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                    )
                }
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
internal fun smileyCodeStartBefore(text: String, cursor: Int): Int {
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
 * 把正文里的表情码（`[#呵呵]`）染成透明，同时把 BBCode 标记（链接、引用、代码、加粗、斜体等）施加富文本视觉样式。
 *
 * 针对连续引用的视觉粘连问题：为每个 BBCode 组打上统一的胶囊浅底色，开闭标签附带微徽标背景，
 * 使每组与相邻的普通文字或另一组代码之间拥有清晰分明的实体边界。
 *
 * 关键约束：字符总数与每个字符的下标必须 100% 保持不变，这样 OffsetMapping 用 Identity
 * 才能杜绝任何输入法 IME 光标抖动或越界崩溃。
 */
internal fun formatRichComposerText(
    source: String,
    smileyByCode: Map<String, Smiley>,
    colors: com.aazhou.mtforum.ui.theme.MtfColors,
    primaryColor: Color,
): AnnotatedString {
    return buildAnnotatedString {
        append(source)
        if (source.isEmpty()) return@buildAnnotatedString

        val capsuleBg = colors.textHint.copy(alpha = 0.08f)
        val tagBadgeBg = colors.textHint.copy(alpha = 0.16f)
        val tagTextColor = colors.textHint.copy(alpha = 0.65f)

        // 辅助：为成对 BBCode 整体加底色，并将开闭标签渲染为小徽标
        fun styleTagPair(
            entireRange: IntRange,
            openRange: IntRange,
            closeRange: IntRange,
            contentRange: IntRange,
            contentStyle: SpanStyle? = null,
        ) {
            // 1. 整组浅底色：把实体组从普通文本中区隔出来
            addStyle(SpanStyle(background = capsuleBg), entireRange.first, entireRange.last + 1)
            // 2. 开闭标签小徽标样式
            val tagStyle = SpanStyle(
                color = tagTextColor,
                background = tagBadgeBg,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
            )
            addStyle(tagStyle, openRange.first, openRange.last + 1)
            addStyle(tagStyle, closeRange.first, closeRange.last + 1)
            // 3. 内容富文本样式
            if (contentStyle != null && !contentRange.isEmpty()) {
                addStyle(contentStyle, contentRange.first, contentRange.last + 1)
            }
        }

        // 1. 表情码染成透明色（图片在 Overlay 层绘制）
        // 关键优化：整段表情码统一按 (40f / len).sp 缩放，使多字符表情码总测量宽度稳定在约 20~22dp，
        // 紧贴单表情尺寸，字符平滑横向展开，彻底消除多余空白与重叠。
        if (smileyByCode.isNotEmpty() && source.contains('[')) {
            val smileyRanges = smileyRangesOf(source, smileyByCode)
            for ((range, _) in smileyRanges) {
                val len = range.last - range.first + 1
                val emojiCharSize = (40f / len).coerceIn(6f, 10f).sp
                addStyle(SpanStyle(color = Color.Transparent, fontSize = emojiCharSize), range.first, range.last + 1)
            }
        }

        // 2. 文字链接富文本：[url=网址]链接文字[/url] 或 [url]网址[/url]
        val urlRegex = Regex("""(\[url(?:=[^\]]*)?\])([\s\S]*?)(\[/url\])""", RegexOption.IGNORE_CASE)
        for (match in urlRegex.findAll(source)) {
            val entireRange = match.range
            val openGroup = match.groups[1]!!
            val contentGroup = match.groups[2]!!
            val closeGroup = match.groups[3]!!

            styleTagPair(
                entireRange = entireRange,
                openRange = openGroup.range,
                closeRange = closeGroup.range,
                contentRange = contentGroup.range,
                contentStyle = SpanStyle(
                    color = primaryColor,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }

        // 3. 粗体：[b]...[/b]
        val boldRegex = Regex("""(\[b\])([\s\S]*?)(\[/b\])""", RegexOption.IGNORE_CASE)
        for (match in boldRegex.findAll(source)) {
            styleTagPair(
                entireRange = match.range,
                openRange = match.groups[1]!!.range,
                closeRange = match.groups[3]!!.range,
                contentRange = match.groups[2]!!.range,
                contentStyle = SpanStyle(fontWeight = FontWeight.Bold),
            )
        }

        // 4. 斜体：[i]...[/i]
        val italicRegex = Regex("""(\[i\])([\s\S]*?)(\[/i\])""", RegexOption.IGNORE_CASE)
        for (match in italicRegex.findAll(source)) {
            styleTagPair(
                entireRange = match.range,
                openRange = match.groups[1]!!.range,
                closeRange = match.groups[3]!!.range,
                contentRange = match.groups[2]!!.range,
                contentStyle = SpanStyle(fontStyle = FontStyle.Italic),
            )
        }

        // 5. 下划线：[u]...[/u]
        val underlineRegex = Regex("""(\[u\])([\s\S]*?)(\[/u\])""", RegexOption.IGNORE_CASE)
        for (match in underlineRegex.findAll(source)) {
            styleTagPair(
                entireRange = match.range,
                openRange = match.groups[1]!!.range,
                closeRange = match.groups[3]!!.range,
                contentRange = match.groups[2]!!.range,
                contentStyle = SpanStyle(textDecoration = TextDecoration.Underline),
            )
        }

        // 6. 删除线：[s]...[/s]
        val strikeRegex = Regex("""(\[s\])([\s\S]*?)(\[/s\])""", RegexOption.IGNORE_CASE)
        for (match in strikeRegex.findAll(source)) {
            styleTagPair(
                entireRange = match.range,
                openRange = match.groups[1]!!.range,
                closeRange = match.groups[3]!!.range,
                contentRange = match.groups[2]!!.range,
                contentStyle = SpanStyle(textDecoration = TextDecoration.LineThrough),
            )
        }

        // 7. 引用块富文本：[quote]...[/quote]
        val quoteRegex = Regex("""(\[quote\])([\s\S]*?)(\[/quote\])""", RegexOption.IGNORE_CASE)
        for (match in quoteRegex.findAll(source)) {
            styleTagPair(
                entireRange = match.range,
                openRange = match.groups[1]!!.range,
                closeRange = match.groups[3]!!.range,
                contentRange = match.groups[2]!!.range,
                contentStyle = SpanStyle(color = colors.textSecondary, fontStyle = FontStyle.Italic),
            )
        }

        // 8. 代码块富文本：[code]...[/code]
        val codeRegex = Regex("""(\[code\])([\s\S]*?)(\[/code\])""", RegexOption.IGNORE_CASE)
        for (match in codeRegex.findAll(source)) {
            styleTagPair(
                entireRange = match.range,
                openRange = match.groups[1]!!.range,
                closeRange = match.groups[3]!!.range,
                contentRange = match.groups[2]!!.range,
                contentStyle = SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = colors.textHint.copy(alpha = 0.14f),
                ),
            )
        }

        // 9. 多媒体标签弱化：[img]、[audio]、[media]、[flash]、[attachimg]
        val mediaRegex = Regex("""(\[(img|audio|media[^\\]]*|flash|attachimg)\])([\s\S]*?)(\[/(?:img|audio|media|flash|attachimg)\])""", RegexOption.IGNORE_CASE)
        for (match in mediaRegex.findAll(source)) {
            styleTagPair(
                entireRange = match.range,
                openRange = match.groups[1]!!.range,
                closeRange = match.groups[4]!!.range,
                contentRange = match.groups[3]!!.range,
                contentStyle = SpanStyle(color = colors.textSecondary, fontSize = 13.sp),
            )
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
internal fun SmileyOverlay(
    text: String,
    layout: TextLayoutResult?,
    smileyByCode: Map<String, Smiley>,
) {
    if (layout == null || smileyByCode.isEmpty()) return
    // 区间按布局自己的文本算，确保坐标计算绝对安全；不因 text 与 layout 存在单帧时差而直接 return，
    // 彻底消除插入新表情时已有表情整帧丢失导致的闪烁。
    val source = layout.layoutInput.text.text
    val ranges = remember(source, smileyByCode) { smileyRangesOf(source, smileyByCode) }
    if (ranges.isEmpty()) return
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        ranges.forEach { (range, code) ->
            val smiley = smileyByCode[code] ?: return@forEach
            if (range.first >= layout.layoutInput.text.length) return@forEach
            val first = layout.getBoundingBox(range.first)
            val last = if (range.last < layout.layoutInput.text.length && range.last > range.first) {
                layout.getBoundingBox(range.last)
            } else {
                first
            }
            // 表情码被行尾截断时首末字符不同行：只按首字符那一格画，画不全但不越界
            val box = if (last.top != first.top) first else Rect(first.left, first.top, last.right, last.bottom)
            with(density) {
                val side = 22.dp.toPx()
                val left = box.left + (box.width - side) / 2f
                val top = box.top + (box.height - side) / 2f
                key(range.first to code) {
                    AsyncImage(
                        model = smiley.url,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .absoluteOffset(left.toDp(), top.toDp())
                            .requiredSize(22.dp),
                    )
                }
            }
        }
    }
}

/** 「插入」面板里的条目：包含常用格式与网页端插入项。 */
internal enum class InsertKind(val label: String, val iconRes: Int? = null) {
    Bold("粗体", R.drawable.ic_format_bold),
    Italic("斜体", R.drawable.ic_format_italic),
    Underline("下划线", R.drawable.ic_format_underline),
    Strikethrough("删除线", R.drawable.ic_format_strikethrough),
    Url("文字链接"),
    NetImage("网络图片"),
    Audio("MP3 音乐"),
    Video("网络视频"),
    Flash("Flash"),
    Quote("引用"),
    Code("代码"),
}

/** 在光标处插入 [text]，插完光标落在插入内容里第 [cursorOffsetInInserted] 个字符处。 */
internal fun TextFieldValue.insertAtSelection(
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
 * @候选条：横向滑动的候选名单，贴在工具栏上方。
 * 刻意不用弹窗：用户视线不用离开输入框，键盘也不收起。
 */
@Composable
internal fun AtSuggestionStrip(
    suggestions: List<String>,
    onPick: (String) -> Unit,
) {
    val colors = LocalMtfColors.current
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = AtSuggestionStripHeight)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(suggestions, key = { it }) { name ->
            Text(
                "@$name",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = bodyTextColor(),
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(colors.quoteBackground)
                    .clickable { onPick(name) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

private val AtSuggestionStripHeight = 44.dp

/**
 * 光标处的 @提及 token：返回 [token 起始下标, 已输入的关键字]，光标不在 @xxx 里返回 null。
 *
 * 边界是空白符或行首——`@` 出现在词中间（如邮箱 `a@b.com`）不算提及。
 */
internal fun atTokenAt(text: String, cursor: Int): Pair<Int, String>? {
    if (cursor < 0 || cursor > text.length) return null
    var start = cursor
    while (start > 0 && !text[start - 1].isWhitespace()) start--
    val token = text.substring(start, cursor)
    if (!token.startsWith('@')) return null
    return start to token.drop(1)
}

/** 候选名单：前缀命中排在前，其余包含命中排在后；没有关键字就整份名单按原序。 */
internal fun filterAtUsers(users: List<String>, keyword: String): List<String> {
    if (users.isEmpty()) return emptyList()
    if (keyword.isEmpty()) return users
    val prefix = users.filter { it.startsWith(keyword, ignoreCase = true) }
    val rest = users.filter { it !in prefix && it.contains(keyword, ignoreCase = true) }
    return prefix + rest
}

/**
 * 站点侧 @ 的唯一契约：`@用户名` 后面必须紧跟一个 ASCII 空白（Discuz 用
 * `preg_match_all("/@([^\r\n]*?)\s/i", message.' ')` 取候选用户名），且候选按出现顺序
 * 截断到用户组的 @ 名额——第一个候选不是有效用户名时，整条消息的 @ 全部静默失效。
 *
 * 输入法常把 @ 后的尾随空格吞掉，用户也可能紧接中文标点，这里按已知名单把分隔空白补回来。
 * 名单之外的 @（邮箱、手输的陌生人名）不动：补了空格站点也解析不了，反而改坏用户正文。
 */
internal fun normalizeAtMentions(message: String, knownNames: Collection<String>): String {
    val names = knownNames.distinct().filter { it.isNotEmpty() }.sortedByDescending { it.length }
    if (names.isEmpty()) return message
    val sb = StringBuilder(message)
    var i = 0
    while (i < sb.length) {
        if (sb[i] == '@') {
            val name = names.firstOrNull { sb.startsWith(it, i + 1, ignoreCase = true) }
            if (name != null) {
                val after = i + 1 + name.length
                // 行尾不补：站点提交时自己会追加一个空格；只修「后面紧跟了非空白字符」的情况
                if (after < sb.length && !sb[after].isAtSeparator()) {
                    sb.insert(after, ' ')
                    i = after + 1
                } else {
                    i = after
                }
                continue
            }
        }
        i++
    }
    return sb.toString()
}

/** 站点正则里的 `\s` 只认 ASCII 空白；全角空格、零宽字符都不算分隔。 */
private fun Char.isAtSeparator(): Boolean =
    this == ' ' || this == '\t' || this == '\n' || this == '\r' || this == '\u000B' || this == '\u000C'

/**
 * 包装当前选区，或在光标处插入带前缀/后缀的模板；
 * 若无选区，光标自动停在 [prefix] 之后供用户立即输入。
 */
internal fun TextFieldValue.wrapSelectionOrInsert(
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
internal fun ComposerToolButton(iconRes: Int, label: String, onClick: () -> Unit) {
    Icon(
        painterResource(iconRes),
        contentDescription = label,
        tint = LocalMtfColors.current.textSecondary,
        modifier = Modifier
            .size(40.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(6.dp),
    )
}

/** 表情面板：展开时占住键盘原来的位置，左右滑动或点分类栏切包，网格点选插入。 */
@Composable
internal fun SmileyPanel(
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
    val pagerState = rememberPagerState(initialPage = 1) { packs.size + 1 }
    val scope = rememberCoroutineScope()
    val tabState = rememberLazyListState()
    val smileyByCode = remember(packs) { packs.flatMap { it.items }.associateBy { it.code } }
    val recent = recentCodes.filter { it !in DefaultSmileyCodes }.mapNotNull { smileyByCode[it] }
    // 最近使用为空时不能就此空格：按默认表情表补，站点里没有才退回第一个包。
    // 否则没用过表情的人一点开表情面板，看到的只有分类栏、网格全空
    fun emojisOf(page: Int): List<Smiley> = when (page) {
        0 -> recent.ifEmpty {
            DefaultSmileyCodes.mapNotNull { smileyByCode[it] }
                .ifEmpty { packs.firstOrNull()?.items.orEmpty() }
        }
        else -> packs[page - 1].items
    }
    // 滑动翻到哪页，分类栏就跟着把那个标签滚进来：包多的时候选中项本来就在屏幕外
    LaunchedEffect(pagerState.currentPage) {
        tabState.animateScrollToItem(pagerState.currentPage)
    }
    BoxWithConstraints(modifier) {
        // 网格区高度 = 面板高 - 分类栏（40dp 标签 + 6dp 底部内边距）- 4dp 间隔。按 7 行均分格子
        // 高度，任何键盘高度下都刚好铺满 7 行，不会出现矮键盘只露出半页的情况
        val gridHeight = maxHeight - SmileyPackBarHeight - 4.dp
        val cellHeight = ((gridHeight - 8.dp - 6.dp * (SmileyGridRows - 1)) / SmileyGridRows)
            .coerceAtLeast(24.dp)
        val iconSize = cellHeight.coerceAtMost(34.dp)
        Column(Modifier.fillMaxSize()) {
            LazyRow(
                state = tabState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 6.dp),
            ) {
                item {
                    SmileyPackTab(
                        selected = pagerState.currentPage == 0,
                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_recent),
                            contentDescription = "最近使用",
                            tint = LocalMtfColors.current.textSecondary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                itemsIndexed(packs) { index, pack ->
                    SmileyPackTab(
                        selected = pagerState.currentPage == index + 1,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index + 1) } },
                    ) {
                        AsyncImage(
                            model = pack.items.first().url,
                            contentDescription = pack.name,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                LazyVerticalGrid(
                    columns = GridCells.Fixed(SmileyGridColumns),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(
                        items = emojisOf(page),
                        key = { it.code },
                    ) { smiley ->
                        SmileyCell(smiley, onPick, Modifier.height(cellHeight), iconSize)
                    }
                }
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
private fun RecentSmileyRow(
    smileys: List<Smiley>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
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
private fun SmileyCell(
    smiley: Smiley,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                indication = ripple(bounded = true, radius = 16.dp),
                interactionSource = remember { MutableInteractionSource() },
                onClick = { onPick(smiley.code) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = smiley.url,
            contentDescription = smiley.code,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** 「插入」面板：列出可插入的标记类型（粗斜体/下划线/删除线排在文字链接前）。 */
@Composable
internal fun InsertPanel(onPick: (InsertKind) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(InsertKind.values()) { kind ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clickable { onPick(kind) }
                    .background(LocalMtfColors.current.background, AbsoluteRoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                if (kind.iconRes != null) {
                    Icon(
                        painter = painterResource(kind.iconRes),
                        contentDescription = null,
                        tint = LocalMtfColors.current.textSecondary,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Text(
                    kind.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalMtfColors.current.textSecondary,
                )
            }
        }
    }
}

/** 就地内联插入条：打开即自动聚焦输入框并保持软键盘升起，保留选中状态与内容。 */
@Composable
internal fun InlineInsertBar(
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
    val keyboard = LocalSoftwareKeyboardController.current
    val urlFocusRequester = remember { FocusRequester() }
    val isUrl = kind == InsertKind.Url
    val ready = if (isUrl) urlInput.isNotBlank() else urlInput.isNotBlank()

    LaunchedEffect(kind) {
        delay(60)
        urlFocusRequester.requestFocus()
        keyboard?.show()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = 14.dp, vertical = 8.dp),
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
                modifier = Modifier
                    .weight(1f)
                    .background(colors.surface, AbsoluteRoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .focusRequester(urlFocusRequester),
                decorationBox = { inner ->
                    if (urlInput.isEmpty()) {
                        Text(
                            text = when (kind) {
                                InsertKind.Bold -> "请输入加粗文本"
                                InsertKind.Italic -> "请输入斜体文本"
                                InsertKind.Underline -> "请输入下划线文本"
                                InsertKind.Strikethrough -> "请输入删除线文本"
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
            )
            val primaryColor = MaterialTheme.colorScheme.primary
            Text(
                text = "插入",
                style = MaterialTheme.typography.labelMedium,
                color = if (ready) Color.White else colors.textHint,
                modifier = Modifier
                    .background(
                        if (ready) primaryColor.copy(alpha = 0.69f) else colors.textHint.copy(alpha = 0.2f),
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
    """(\[(url(?:=[^\]]*)?|audio|img|media(?:=[^\]]*)?|flash|quote|code|attachimg|b|i|u|s)\])([\s\S]*?)(\[/(?:url|audio|img|media|flash|quote|code|attachimg|b|i|u|s)\])""",
    RegexOption.IGNORE_CASE,
)

/**
 * 智能成对标签退格：
 * 当用户按退格键（Backspace）删除了某个 BBCode 标签的一部分，或在标签边缘退格时，
 * 连同标签中的内容整体删除整对标签块（例如 [audio]哈哈[/audio] 一起全删，干净无残留）。
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
            // 连同标签中的内容整体删除：直接从原文本中移除整组 [tag]...[/tag]
            val resultText = oldText.removeRange(entireRange.first, entireRange.last + 1)
            return TextFieldValue(resultText, selection = TextRange(entireRange.first))
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
        resolver.openImageInput(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalStateException("图片解码失败")
        }
        val longEdge = max(bounds.outWidth, bounds.outHeight)
        val cache = File(context.cacheDir, "reply_upload.jpg")
        // 原图没超限就原样拷过去：GIF 动图重编码会丢帧
        val rawSize = try {
            resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
        } catch (_: Exception) {
            -1L
        }
        if (rawSize in 1..MAX_UPLOAD_BYTES && longEdge in 1 until MAX_UPLOAD_EDGE) {
            resolver.openImageInput(uri).use { input ->
                cache.outputStream().use { output -> input.copyTo(output) }
            }
            return@withContext cache
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = if (longEdge > MAX_UPLOAD_EDGE) longEdge / MAX_UPLOAD_EDGE else 1
        }
        val decoded = resolver.openImageInput(uri)
            .use { BitmapFactory.decodeStream(it, null, options) }
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

/**
 * 打开选中图的读取流，取不到就重试。
 *
 * openInputStream 在 provider 进程刚被系统回收等情况下返回 null 而不抛异常
 * （AOSP 文档：null if the provider recently crashed），单靠它会把本来读得出来的图误判成「读不到」。
 */
private suspend fun ContentResolver.openImageInput(uri: Uri): InputStream {
    repeat(OPEN_URI_ATTEMPTS) { attempt ->
        openImageStream(uri)?.let { return it }
        if (attempt != OPEN_URI_ATTEMPTS - 1) delay(OPEN_URI_RETRY_DELAY_MS)
    }
    throw IllegalStateException("读不到所选图片（${uri.scheme}://${uri.authority}）")
}

private fun ContentResolver.openImageStream(uri: Uri): InputStream? {
    val stream = try {
        openInputStream(uri)
    } catch (_: Exception) {
        null
    }
    if (stream != null) return stream
    // 部分机型的相册 provider（如 MIUI 的 com.miui.gallery.open）openAssetFile 直接返回 null，
    // 但 openFileDescriptor 是另一条实现，常常还能拿到 fd
    val fd = try {
        openFileDescriptor(uri, "r")
    } catch (_: Exception) {
        null
    }
    return fd?.let { ParcelFileDescriptor.AutoCloseInputStream(it) }
}

/** 站点限制：单张 1MB，网页端也会把图缩到 1500px 再传。 */
private const val MAX_UPLOAD_BYTES = 1024 * 1024L
private const val MAX_UPLOAD_EDGE = 1500
private const val MIN_UPLOAD_QUALITY = 15

/** 取选中图读取流的重试次数与间隔：provider 重启后再取通常就好了。 */
private const val OPEN_URI_ATTEMPTS = 3
private const val OPEN_URI_RETRY_DELAY_MS = 150L

/** 输入框高度：固定值，不随内容长高。 */
private val ReplyComposerInputHeight = 84.dp

/** 最近使用那一行摆几个。 */
internal const val RecentSmileyLimit = 8

/** 工具栏下方的子栏高度（快捷表情行、排版插入栏）。两者高度完全保持一致（36.dp）。 */
internal val ComposerSubBarHeight = 36.dp

/** 表情网格规格：6 列 7 行。格子高度按面板实际高度均分，键盘再矮也是整 7 行。 */
private const val SmileyGridColumns = 6
private const val SmileyGridRows = 7

/** 表情包分类栏高度：40dp 标签 + 6dp 底部内边距。 */
private val SmileyPackBarHeight = 46.dp

/** 低于这个高度的 ime 不算软键盘（分屏预览、悬浮窗边缘的残值）。 */
internal val MinKeyboardHeight = 100.dp

/** ime 高度连续这么多帧不变才认定为停稳，此时的高度才是键盘真实高度。 */
internal const val STABLE_KEYBOARD_FRAMES = 6

/** 切回键盘的交接最多等这么多帧（约 0.5s）：还没起来就放弃，表情面板留在原位。 */
internal const val HANDOFF_GIVEUP_FRAMES = 30

/** 键盘一次都没起来过时，表情面板按这个高度占位兜底（主流设备键盘常见高度）。 */
private val DefaultKeyboardHeight = 280.dp

/** 持久化记录软键盘真实高度，避免冷启动或跨回复弹框时高度不一致跳跃。 */
internal object KeyboardTracker {
    private const val PREF_NAME = "mtf_keyboard_pref"
    private const val KEY_HEIGHT_PX = "keyboard_height_px"
    @Volatile
    private var cachedHeightPx: Int = 0

    fun getInitialHeight(context: Context, density: androidx.compose.ui.unit.Density): Dp {
        if (cachedHeightPx > 0) {
            return with(density) { cachedHeightPx.toDp() }
        }
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val saved = sp.getInt(KEY_HEIGHT_PX, 0)
        if (saved > 0) {
            cachedHeightPx = saved
            return with(density) { saved.toDp() }
        }
        return DefaultKeyboardHeight
    }

    fun save(context: Context, heightPx: Int) {
        if (heightPx > 0 && heightPx != cachedHeightPx) {
            cachedHeightPx = heightPx
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_HEIGHT_PX, heightPx)
                .apply()
        }
    }
}

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
internal val DefaultSmileyCodes =
    listOf("[#呵呵]", "[#滑稽]", "[#哈哈]", "[#吐舌]", "[#啊]", "[#酷]", "[#怒]", "[#开心]")
