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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aazhou.mtforum.R
import com.aazhou.mtforum.data.model.Post
import com.aazhou.mtforum.data.parser.Smiley
import com.aazhou.mtforum.data.parser.SmileyPack
import com.aazhou.mtforum.ui.common.AppToast
import com.aazhou.mtforum.ui.theme.LocalIsDarkTheme
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.bodyTextColor
import kotlinx.coroutines.Dispatchers
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
    recentCodes: MutableList<String>,
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
    var panel by remember { mutableStateOf(ComposerPanel.None) }
    var inserting by remember { mutableStateOf<InsertKind?>(null) }

    // 打开面板即聚焦并把键盘请出来
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val smileyByCode = remember(smileyPacks) {
        smileyPacks.flatMap { it.items }.associateBy { it.code }
    }

    fun pickSmiley(code: String) {
        onValueChange(value.insertAtSelection(code))
        recentCodes.remove(code)
        recentCodes.add(0, code)
        if (recentCodes.size > RecentSmileyLimit) recentCodes.removeAt(recentCodes.lastIndex)
    }

    // 默认那一行：先用最近用过的，不足 8 个用默认包的开头补满
    val recentRow = run {
        val used = recentCodes.mapNotNull { smileyByCode[it] }
        val filler = smileyPacks.firstOrNull()
            ?.items
            ?.filter { smiley -> recentCodes.none { it == smiley.code } }
            .orEmpty()
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
                .padding(top = 12.dp, bottom = 12.dp),
        ) {
            // 输入区：占满宽度，文字顶格；回复指定楼层时占位文案带上对方昵称
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ReplyComposerInputHeight)
                    // indication 置空：这层只是让整个输入区都能响应点击，
                    // 不需要按下水波纹——按一下灰一片很违和
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        // 表情面板开着就先收起它；键盘已弹出时 show 是空操作，不会把它收掉
                        if (panel == ComposerPanel.Smiley) panel = ComposerPanel.None
                        focusRequester.requestFocus()
                        keyboard?.show()
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
                    onValueChange = onValueChange,
                    textStyle = TextStyle(color = bodyTextColor(), fontSize = 15.sp),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .focusRequester(focusRequester)
                        // 聚焦状态回传上层：遮罩和返回键靠它区分「该收键盘」还是「该收回复框」
                        .onFocusChanged { onFocusedChange(it.isFocused) }
                        // 同上：点输入框任意位置都把键盘请回来
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                if (panel == ComposerPanel.Smiley) panel = ComposerPanel.None
                                focusRequester.requestFocus()
                                keyboard?.show()
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
                ComposerToolButton(R.drawable.ic_image, "图片") { inserting = InsertKind.Image }
                ComposerToolButton(R.drawable.ic_at, "提到朋友") { inserting = InsertKind.At }
                ComposerToolButton(R.drawable.ic_hash, "话题") {
                    onValueChange(value.insertAtSelection("#话题#", cursorOffsetInInserted = 1))
                }
                ComposerToolButton(R.drawable.ic_add, "插入") {
                    panel = if (panel == ComposerPanel.Insert) ComposerPanel.None else ComposerPanel.Insert
                }
                Spacer(Modifier.weight(1f))
                if (sending) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        "发布",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(ComposerPublishColor, AbsoluteRoundedCornerShape(50))
                            .clickable(enabled = value.text.isNotBlank(), onClick = onSend)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }

            when (panel) {
                ComposerPanel.Smiley -> SmileyPanel(
                    packs = smileyPacks,
                    recentCodes = recentCodes,
                    onPick = { code -> pickSmiley(code) },
                    modifier = Modifier.padding(top = 8.dp),
                )
                // 没展开面板时只摆一行最近用过的，够 8 个为止
                ComposerPanel.None -> RecentSmileyRow(recentRow) { code -> pickSmiley(code) }
                ComposerPanel.Insert -> InsertPanel(
                    onPick = { kind ->
                        inserting = kind
                        panel = ComposerPanel.None
                    },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

    when (inserting) {
        InsertKind.Image -> ImageInsertDialog(
            hasCredentials = uploadCredentials != null,
            onDismiss = { inserting = null },
            onUpload = { uri ->
                val file = prepareUploadFile(context, uri)
                val aid = onUploadImage(file)
                if (aid != null) {
                    onValueChange(value.insertAtSelection("[attachimg]$aid[/attachimg]"))
                } else {
                    AppToast.show("上传失败，可能超出大小限制或凭据已过期")
                }
            },
            onInsertUrl = { url ->
                // 网络图片站点是直接引用的，不用过上传：上传接口只认本机文件
                onValueChange(value.insertAtSelection("[img]$url[/img]"))
            },
        )
        InsertKind.At -> TextInsertDialog(
            title = "提到朋友",
            label = "用户名",
            onDismiss = { inserting = null },
            onConfirm = { name -> onValueChange(value.insertAtSelection("@$name ")) },
        )
        InsertKind.Url -> TextInsertDialog(
            title = "文字链接",
            label = "链接网址",
            onDismiss = { inserting = null },
            onConfirm = { url -> onValueChange(value.insertAtSelection("[url=$url]$url[/url]")) },
        )
        InsertKind.Audio -> TextInsertDialog(
            title = "音乐",
            label = "音频地址",
            onDismiss = { inserting = null },
            onConfirm = { url -> onValueChange(value.insertAtSelection("[audio]$url[/audio]")) },
        )
        InsertKind.Video -> TextInsertDialog(
            title = "视频",
            label = "视频地址",
            onDismiss = { inserting = null },
            onConfirm = { url -> onValueChange(value.insertAtSelection("[media=x,500,375]$url[/media]")) },
        )
        InsertKind.Quote -> TextInsertDialog(
            title = "引用",
            label = "引用内容",
            onDismiss = { inserting = null },
            onConfirm = { text -> onValueChange(value.insertAtSelection("[quote]$text[/quote]")) },
        )
        InsertKind.Code -> TextInsertDialog(
            title = "代码",
            label = "代码",
            onDismiss = { inserting = null },
            onConfirm = { text -> onValueChange(value.insertAtSelection("[code]$text[/code]")) },
        )
        null -> Unit
    }
}

private enum class ComposerPanel { None, Smiley, Insert }

/** 「插入」面板里的条目，也决定点开后弹哪种输入框。 */
private enum class InsertKind(val label: String) {
    Url("文字链接"),
    Image("网络图片"),
    Audio("音乐"),
    Video("视频"),
    Quote("引用"),
    Code("代码"),
    At("提到朋友"),
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
    // 0 = 最近使用，其余按下标对应各个包
    var packIndex by remember { mutableStateOf(0) }
    val smileyByCode = remember(packs) { packs.flatMap { it.items }.associateBy { it.code } }
    val recent = recentCodes.mapNotNull { smileyByCode[it] }
    val emojis = if (packIndex == 0) recent else packs[packIndex - 1].items
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
        // 固定高度：表情少的时候也占和满表情一样高，不会忽大忽小
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

/** 默认状态下工具栏下面那一行：最近用过的表情，不足 8 个用默认包补满。 */
@Composable
private fun RecentSmileyRow(smileys: List<Smiley>, onPick: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        smileys.forEach { smiley ->
            SmileyCell(smiley, onPick, Modifier.weight(1f))
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
            .padding(10.dp)
            .clickable { onPick(smiley.code) },
    )
}

/** 「插入」面板：列出可插入的标记类型（@朋友 在工具栏单独有入口）。 */
@Composable
private fun InsertPanel(onPick: (InsertKind) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(InsertKind.values().filter { it != InsertKind.At }) { kind ->
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

/**
 * 图片插入：相册选图后压到站点限制内再上传，插 `[attachimg]id[/attachimg]`；
 * 也可直接贴网络图片地址，插 `[img]url[/img]`。
 */
@Composable
private fun ImageInsertDialog(
    hasCredentials: Boolean,
    onDismiss: () -> Unit,
    onUpload: suspend (Uri) -> Unit,
    onInsertUrl: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var urlMode by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null || !hasCredentials) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            try {
                onUpload(uri)
                onDismiss()
            } catch (e: Exception) {
                AppToast.show(e.message ?: "上传失败")
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (urlMode) "网络图片" else "插入图片") },
        text = {
            when {
                urlMode -> BasicTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    textStyle = TextStyle(color = bodyTextColor(), fontSize = 15.sp),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
                busy -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("上传中…", style = MaterialTheme.typography.bodyMedium)
                }
                else -> Text(
                    if (hasCredentials) {
                        "从相册选图上传，超过 1MB 会自动压缩"
                    } else {
                        "上传凭据已失效，返回帖子重进试试"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            if (urlMode) {
                TextButton(
                    enabled = url.isNotBlank(),
                    onClick = {
                        onInsertUrl(url.trim())
                        urlMode = false
                        onDismiss()
                    },
                ) { Text("插入") }
            } else {
                TextButton(
                    enabled = hasCredentials && !busy,
                    onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                ) { Text("选择图片") }
            }
        },
        dismissButton = {
            Row {
                if (!urlMode && !busy) {
                    TextButton(onClick = { urlMode = true }) { Text("用网址") }
                }
                TextButton(onClick = { if (urlMode) urlMode = false else onDismiss() }) {
                    Text(if (urlMode) "返回" else "取消")
                }
            }
        },
    )
}

/** 单行文本输入弹窗（@朋友、链接、音频、视频、引用、代码共用）。 */
@Composable
private fun TextInsertDialog(
    title: String,
    label: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium, color = LocalMtfColors.current.textSecondary)
                Spacer(Modifier.height(6.dp))
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = bodyTextColor(), fontSize = 15.sp),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text.trim()) }) { Text("插入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
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

/** 表情面板展开时占住键盘原来的高度。 */
private val ComposerSmileyPanelHeight = 310.dp

/** 发布按钮底色。 */
private val ComposerPublishColor = Color(0xFF97CDAD)
