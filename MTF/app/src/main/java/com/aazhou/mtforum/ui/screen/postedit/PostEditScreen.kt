package com.aazhou.mtforum.ui.screen.postedit

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aazhou.mtforum.R
import com.aazhou.mtforum.data.model.ThreadType
import com.aazhou.mtforum.ui.common.AppToast
import com.aazhou.mtforum.ui.common.ImagePreview
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.common.awaitImeSettled
import com.aazhou.mtforum.ui.common.container
import com.aazhou.mtforum.ui.screen.threaddetail.AtSuggestionStrip
import com.aazhou.mtforum.ui.screen.threaddetail.AtUserPickerPage
import com.aazhou.mtforum.ui.screen.threaddetail.ComposerSubBarHeight
import com.aazhou.mtforum.ui.screen.threaddetail.ComposerToolButton
import com.aazhou.mtforum.ui.screen.threaddetail.DefaultSmileyCodes
import com.aazhou.mtforum.ui.screen.threaddetail.InlineInsertBar
import com.aazhou.mtforum.ui.screen.threaddetail.HideInsertBar
import com.aazhou.mtforum.ui.screen.threaddetail.buildHideTag
import com.aazhou.mtforum.ui.screen.threaddetail.InsertKind
import com.aazhou.mtforum.ui.screen.threaddetail.InsertPanel
import com.aazhou.mtforum.ui.screen.threaddetail.KeyboardTracker
import com.aazhou.mtforum.ui.screen.threaddetail.MAX_UPLOAD_IMAGES
import com.aazhou.mtforum.ui.screen.threaddetail.MinKeyboardHeight
import com.aazhou.mtforum.ui.screen.threaddetail.STABLE_KEYBOARD_FRAMES
import com.aazhou.mtforum.ui.screen.threaddetail.SmileyOverlay
import com.aazhou.mtforum.ui.screen.threaddetail.SmileyPanel
import com.aazhou.mtforum.ui.screen.threaddetail.composerDeletionResult
import com.aazhou.mtforum.ui.screen.threaddetail.snapSelectionOutOfSmiley
import com.aazhou.mtforum.ui.screen.threaddetail.wrapSelectionOrInsert
import com.aazhou.mtforum.ui.screen.threaddetail.HANDOFF_GIVEUP_FRAMES
import com.aazhou.mtforum.ui.screen.threaddetail.atTokenAt
import com.aazhou.mtforum.ui.screen.threaddetail.filterAtUsers
import com.aazhou.mtforum.ui.screen.threaddetail.formatRichComposerText
import com.aazhou.mtforum.ui.screen.threaddetail.insertAtSelection
import com.aazhou.mtforum.ui.screen.threaddetail.prepareUploadFile
import com.aazhou.mtforum.ui.theme.DarkBrightTextAlpha
import com.aazhou.mtforum.ui.theme.LocalIsDarkTheme
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.bodyTextColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class PostComposerPanel { None, Smiley, Insert }

@Composable
fun PostEditScreen(
    fid: String,
    forumName: String,
    onBack: () -> Unit,
    onPosted: () -> Unit,
    /** 编辑自己的帖子/评论时带上站点 tid/pid；为空表示发新帖。 */
    editTid: String? = null,
    editPid: String? = null,
) {
    val viewModel: PostEditViewModel = appViewModel {
        PostEditViewModel(fid, forumName, it.repository, it.smileyStore, editTid, editPid, it.draftStore)
    }

    val colors = LocalMtfColors.current
    val context = LocalContext.current
    val draftStore = context.container().draftStore
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    val initData by viewModel.initData.collectAsStateWithLifecycle()
    val editData by viewModel.editData.collectAsStateWithLifecycle()
    val loadingInit by viewModel.loadingInit.collectAsStateWithLifecycle()
    val selectedType by viewModel.selectedType.collectAsStateWithLifecycle()
    val subject by viewModel.subject.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val uploadedImages by viewModel.uploadedImages.collectAsStateWithLifecycle()
    val uploading by viewModel.uploading.collectAsStateWithLifecycle()
    val smileyPacks by viewModel.smileyPacks.collectAsStateWithLifecycle()
    val atUsers by viewModel.atUsers.collectAsStateWithLifecycle()
    val recentCodes by viewModel.recentCodes.collectAsStateWithLifecycle(initialValue = emptyList())
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 站点只在首帖表单里给标题输入框：编辑评论时一并收起标题行
    val editing = viewModel.editing
    val showSubject = !editing || editData?.subject != null
    val permissionDenied = initData?.permissionDenied ?: editData?.permissionDenied
    val threadTypes = initData?.types ?: editData?.types.orEmpty()
    // 发布成功的那一次不能再落草稿：离开发帖页时内容还在输入框里，不拦一道会把刚发出去的帖子又存成草稿
    var published by remember { mutableStateOf(false) }

    // 离开即存档：不管从返回键、系统返回还是发布成功退出，走的都是组合销毁这一条路
    DisposableEffect(viewModel) {
        onDispose {
            // 编辑态不碰草稿箱：改的是站上已有内容，更不能顺手删掉该版块的草稿
            if (editing) return@onDispose
            val draft = viewModel.currentDraft()
            when {
                published -> draftStore.remove(listOf(fid))
                draft != null -> draftStore.save(draft)
                // 内容被清空了：这一版块的旧草稿跟着作废，别留一份空箱占位
                else -> draftStore.remove(listOf(fid))
            }
        }
    }

    var panel by remember { mutableStateOf(PostComposerPanel.None) }
    var activeInsertKind by remember { mutableStateOf<InsertKind?>(null) }
    // 打开内联条那一刻的选区：内联条的输入框抢走焦点后，正文选区会被输入法压成光标，
    // 确认插入时再读 message.selection 就落到了末尾，选中的文字也换不掉
    var pendingSelection by remember { mutableStateOf<TextRange?>(null) }
    var inlineUrlInput by remember { mutableStateOf("") }
    var inlineTitleInput by remember { mutableStateOf("") }
    var hideContentInput by remember { mutableStateOf("") }
    var hideCreditsInput by remember { mutableStateOf("") }
    var hideDaysInput by remember { mutableStateOf("") }
    var showAtPicker by remember { mutableStateOf(false) }
    // 正在看大图的附件下标，-1 表示没打开
    var previewImageIndex by remember { mutableIntStateOf(-1) }
    // 点了缩略图但还没开预览的下标：预览是挂在窗口装饰层的 Popup，键盘还在时创建会按
    // 当时的 ime 高度把底部裁掉一块，且键盘随后收起也不补回来，故先收键盘再开
    var pendingPreviewIndex by remember { mutableIntStateOf(-1) }
    // 开预览前键盘是否弹着：看完大图按原样请回来，用户自己收起的键盘不硬弹
    var previewRestoreKeyboard by remember { mutableStateOf(false) }

    val subjectFocusRequester = remember { FocusRequester() }
    val messageFocusRequester = remember { FocusRequester() }
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    // 正文滚动交给外层容器承载：字段内部滚动拿不到真实滚动量，bringIntoView 也滚不动光标，
    // 表情贴图更是据此漂移。ScrollState 是唯一真源，光标滚动与表情定位都从它算
    val messageScrollState = rememberScrollState()
    // 正文盒子（滚动视口）的高度 px：滚到光标时用它做下界
    var messageBoxHeightPx by remember { mutableIntStateOf(0) }
    // 正文盒子视口高度的 dp 版本：让字段至少撑满视口，内容少时点空白处也能落光标
    var messageBoxHeightDp by remember { mutableStateOf(0.dp) }

    val imeInsets = WindowInsets.ime
    val imeHeight = with(density) { imeInsets.getBottom(density).toDp() }
    var keyboardHeight by remember {
        mutableStateOf(KeyboardTracker.getInitialHeight(context, density))
    }
    var smileySlot by remember {
        mutableStateOf(KeyboardTracker.getInitialHeight(context, density))
    }
    var keyboardHandoff by remember { mutableStateOf(false) }

    // 键盘与表情面板的下部容器：高度单一真源，键盘收起/弹出时工具栏纹丝不动。
    // 槽必须独立于上面的内容区：插入面板态同样要按键盘高度占位，否则键盘一升起
    // 就把插入条连工具栏一起盖住
    val bottomSlotHeight = when {
        panel == PostComposerPanel.Smiley || keyboardHandoff -> smileySlot
        imeHeight > 0.dp -> imeHeight
        else -> 0.dp
    }

    // 进发帖页就把光标落在正文里：不用先点一下输入框才唤得起键盘；草稿回填后
    // 光标停在正文末尾，接着写不用先拖到尾
    LaunchedEffect(Unit) {
        if (!editing) messageFocusRequester.requestFocus()
    }

    // 键盘完全升起后把光标滚进可视区：刚弹起时字段视口还没缩到位，等连续稳定帧确认
    // 键盘停稳后再滚。滚动由外层 ScrollState 承载，直接按光标矩形滚到可视区底部之上
    LaunchedEffect(imeHeight > 0.dp) {
        if (!(imeHeight > 0.dp)) return@LaunchedEffect
        var lastPx = -1
        var stableFrames = 0
        while (true) {
            withFrameNanos { }
            val px = imeInsets.getBottom(density)
            if (px > 0) {
                if (px == lastPx) stableFrames++ else { lastPx = px; stableFrames = 0 }
                if (stableFrames >= STABLE_KEYBOARD_FRAMES) break
            } else {
                return@LaunchedEffect
            }
        }
        // 滚动量 = 光标矩形底部 - 内容区高度（正文盒子自身高度）：保证光标落在底部之上
        val cursorRect = textLayout
            ?.takeIf { it.layoutInput.text.text == message.text }
            ?.getCursorRect(message.selection.max.coerceIn(0, message.text.length))
        if (cursorRect != null) {
            val target = (cursorRect.bottom - messageBoxHeightPx).coerceAtLeast(0f).toInt()
            messageScrollState.scrollTo(target)
        }
    }

    // 监控软键盘高度
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

    // 键盘收起动画走完（ime 归零）才开预览：Popup 不带焦点，创建那一刻的窗口框架按
    // 当时的 ime 高度裁掉底部一块、之后也不补回来。等不到就按原样开，别把预览卡死
    LaunchedEffect(pendingPreviewIndex) {
        if (pendingPreviewIndex < 0) return@LaunchedEffect
        awaitImeSettled(imeInsets, density)
        previewImageIndex = pendingPreviewIndex
        pendingPreviewIndex = -1
    }

    LaunchedEffect(state) {
        when (val s = state) {
            is PostEditViewModel.PostState.Success -> {
                published = true
                AppToast.show(if (editing) "已保存" else "发布成功")
                viewModel.consume()
                onPosted()
            }
            is PostEditViewModel.PostState.Error -> {
                AppToast.show(s.message)
                viewModel.consume()
            }
            else -> Unit
        }
    }

    fun switchToKeyboard(focusMessage: Boolean = true) {
        if (panel == PostComposerPanel.Smiley) {
            keyboardHandoff = true
        }
        if (focusMessage) messageFocusRequester.requestFocus()
        keyboard?.show()
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
        if (imeInsets.getBottom(density) > 0 && panel == PostComposerPanel.Smiley) {
            panel = PostComposerPanel.None
        }
    }

    BackHandler(enabled = panel != PostComposerPanel.None || activeInsertKind != null || showAtPicker) {
        when {
            showAtPicker -> showAtPicker = false
            activeInsertKind != null -> activeInsertKind = null
            panel != PostComposerPanel.None -> panel = PostComposerPanel.None
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_UPLOAD_IMAGES),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val aids = viewModel.uploadImages(uris) { uri -> prepareUploadFile(context, uri) }
            // 标记一次插齐：逐张插会都基于同一份文本，后一张把前一张覆盖掉
            if (aids.isNotEmpty()) {
                viewModel.insertTextAtSelection(aids.joinToString("") { "[attachimg]$it[/attachimg]" })
            }
            val failed = uris.size - aids.size
            when {
                failed == 0 -> AppToast.show(
                    if (aids.size == 1) "图片上传成功并已插入正文" else "${aids.size} 张图片已插入正文",
                )
                aids.isEmpty() -> AppToast.show("上传失败，可能超出大小限制或凭据已过期")
                else -> AppToast.show("${aids.size} 张已插入，$failed 张上传失败")
            }
        }
    }

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

    fun onComposerMessageChange(next: TextFieldValue) {
        viewModel.onMessageChange((message.composerDeletionResult(next) ?: next).snapSelectionOutOfSmiley(smileyByCode))
    }

    /** 按打开内联条时记下的选区写入标记：有选中就替换掉选中的文字，没选中就在光标处插入。 */
    fun insertAtRange(inserted: String) {
        val current = message
        val target = pendingSelection ?: current.selection
        val start = minOf(target.min, target.max).coerceIn(0, current.text.length)
        val end = maxOf(target.min, target.max).coerceIn(0, current.text.length)
        val merged = current.text.substring(0, start) + inserted + current.text.substring(end)
        viewModel.onMessageChange(
            current.copy(text = merged, selection = TextRange(start + inserted.length)),
        )
        pendingSelection = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Scaffold(
            containerColor = colors.background,
            topBar = {
                PostEditTopBar(
                    forumName = forumName,
                    editing = editing,
                    canSubmit = message.text.isNotBlank() && permissionDenied == null &&
                        (!showSubject || subject.isNotBlank()),
                    isSending = state is PostEditViewModel.PostState.Sending,
                    onBack = onBack,
                    onSubmit = { viewModel.submit() },
                )
            },
            bottomBar = {
                // 底部工具栏与展开插槽
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.topBar)
                        .navigationBarsPadding(),
                ) {
                    // @ 候选条（光标处含有 @keyword 时显示）
                    val atToken = if (message.selection.collapsed) {
                        atTokenAt(message.text, message.selection.start)
                    } else null

                    if (atToken != null && panel == PostComposerPanel.None && activeInsertKind == null) {
                        val atSuggestions = filterAtUsers(atUsers, atToken.second)
                        if (atSuggestions.isNotEmpty()) {
                            AtSuggestionStrip(
                                suggestions = atSuggestions,
                                onPick = { name ->
                                    val start = atToken.first
                                    val end = message.selection.start
                                    val replacement = "@$name "
                                    val newText = message.text.replaceRange(start, end, replacement)
                                    val newCursor = start + replacement.length
                                    viewModel.onMessageChange(
                                        message.copy(
                                            text = newText,
                                            selection = TextRange(newCursor),
                                        ),
                                    )
                                },
                            )
                        }
                    }

                    // 工具按钮行
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ComposerToolButton(R.drawable.ic_smiley, "表情") {
                            if (panel == PostComposerPanel.Smiley) {
                                switchToKeyboard()
                            } else {
                                keyboardHandoff = false
                                smileySlot = if (imeHeight > MinKeyboardHeight && imeHeight >= keyboardHeight) {
                                    imeHeight
                                } else {
                                    keyboardHeight
                                }
                                panel = PostComposerPanel.Smiley
                                focusManager.clearFocus()
                                keyboard?.hide()
                            }
                        }

                        ComposerToolButton(R.drawable.ic_image, "插入图片") {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }

                        ComposerToolButton(R.drawable.ic_at, "提到朋友") {
                            panel = PostComposerPanel.None
                            focusManager.clearFocus()
                            keyboard?.hide()
                            showAtPicker = true
                        }

                        ComposerToolButton(R.drawable.ic_add, "插入内容") {
                            // 内联插入条占着工具栏下方这块位置，先收起它插入面板才露得出来
                            activeInsertKind = null
                            if (panel == PostComposerPanel.Smiley) {
                                keyboardHandoff = true
                            }
                            panel = if (panel == PostComposerPanel.Insert) PostComposerPanel.None else PostComposerPanel.Insert
                            messageFocusRequester.requestFocus()
                            keyboard?.show()
                        }

                        Spacer(Modifier.weight(1f))

                        if (uploading) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = primaryColor,
                                )
                                Text(
                                    "上传中…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }

                    // 工具栏下方的内容区：就地内联插入条 / 插入面板
                    val currentInsert = activeInsertKind
                    if (currentInsert == InsertKind.Hide) {
                        HideInsertBar(
                            contentInput = hideContentInput,
                            creditsInput = hideCreditsInput,
                            daysInput = hideDaysInput,
                            onContentChange = { hideContentInput = it },
                            onCreditsChange = { hideCreditsInput = it },
                            onDaysChange = { hideDaysInput = it },
                            onDismiss = {
                                activeInsertKind = null
                                messageFocusRequester.requestFocus()
                                keyboard?.show()
                            },
                            onConfirm = {
                                val tag = buildHideTag(hideContentInput, hideCreditsInput, hideDaysInput)
                                insertAtRange(tag)
                                hideContentInput = ""
                                hideCreditsInput = ""
                                hideDaysInput = ""
                                activeInsertKind = null
                                messageFocusRequester.requestFocus()
                                keyboard?.show()
                            },
                        )
                    } else if (currentInsert != null) {
                        InlineInsertBar(
                            kind = currentInsert,
                            urlInput = inlineUrlInput,
                            titleInput = inlineTitleInput,
                            onUrlChange = { inlineUrlInput = it },
                            onTitleChange = { inlineTitleInput = it },
                            onDismiss = {
                                activeInsertKind = null
                                // 内联条的输入框此刻持有焦点，直接移除它会被当成失焦而收起键盘
                                messageFocusRequester.requestFocus()
                                keyboard?.show()
                            },
                            onConfirm = {
                                val tag = when (currentInsert) {
                                    InsertKind.Bold -> "[b]${inlineUrlInput.trim()}[/b]"
                                    InsertKind.Italic -> "[i]${inlineUrlInput.trim()}[/i]"
                                    InsertKind.Underline -> "[u]${inlineUrlInput.trim()}[/u]"
                                    InsertKind.Strikethrough -> "[s]${inlineUrlInput.trim()}[/s]"
                                    InsertKind.Url -> {
                                        val u = inlineUrlInput.trim()
                                        val t = inlineTitleInput.trim()
                                        if (t.isNotBlank()) "[url=$u]$t[/url]" else "[url]$u[/url]"
                                    }
                                    InsertKind.NetImage -> "[img]${inlineUrlInput.trim()}[/img]"
                                    InsertKind.Audio -> "[audio]${inlineUrlInput.trim()}[/audio]"
                                    InsertKind.Video -> "[media=x,500,375]${inlineUrlInput.trim()}[/media]"
                                    InsertKind.Flash -> "[flash]${inlineUrlInput.trim()}[/flash]"
                                    InsertKind.Quote -> "[quote]${inlineUrlInput.trim()}[/quote]"
                                    InsertKind.Code -> "[code]${inlineUrlInput.trim()}[/code]"
                                    InsertKind.Hide -> ""
                                }
                                insertAtRange(tag)
                                inlineUrlInput = ""
                                inlineTitleInput = ""
                                activeInsertKind = null
                                messageFocusRequester.requestFocus()
                                keyboard?.show()
                            },
                        )
                    } else if (panel == PostComposerPanel.Insert) {
                        InsertPanel(
                            onPick = { kind ->
                                val start = minOf(message.selection.start, message.selection.end)
                                val end = maxOf(message.selection.start, message.selection.end)
                                val selected = if (end > start) message.text.substring(start, end) else ""
                                val isTextFormat = kind in listOf(
                                    InsertKind.Bold,
                                    InsertKind.Italic,
                                    InsertKind.Underline,
                                    InsertKind.Strikethrough,
                                    InsertKind.Quote,
                                    InsertKind.Code,
                                )
                                if (selected.isNotEmpty() && isTextFormat) {
                                    // 有选中文本时直接套标签，跟评论面板一致，不再弹内联条问一遍
                                    val tag = when (kind) {
                                        InsertKind.Bold -> "b"
                                        InsertKind.Italic -> "i"
                                        InsertKind.Underline -> "u"
                                        InsertKind.Strikethrough -> "s"
                                        InsertKind.Quote -> "quote"
                                        InsertKind.Code -> "code"
                                        else -> ""
                                    }
                                    viewModel.onMessageChange(
                                        message.wrapSelectionOrInsert("[$tag]", "[/$tag]"),
                                    )
                                    panel = PostComposerPanel.None
                                    messageFocusRequester.requestFocus()
                                    keyboard?.show()
                                } else {
                                    // 选中的文字是「显示文字」，不是网址：与评论面板一致
                                    inlineUrlInput = ""
                                    inlineTitleInput = selected
                                    if (kind == InsertKind.Hide) {
                                        hideContentInput = selected
                                        hideCreditsInput = ""
                                        hideDaysInput = ""
                                    }
                                    pendingSelection = message.selection
                                    activeInsertKind = kind
                                    panel = PostComposerPanel.None
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ComposerSubBarHeight)
                                .padding(horizontal = 14.dp, vertical = 4.dp),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(bottomSlotHeight),
                    ) {
                        // 一旦开始切回键盘，表情页立刻让位变空白：底部槽只剩一块实色区域等键盘
                        // 升起来填充，不再让表情网格被键盘一点点盖上去
                        if (panel == PostComposerPanel.Smiley && !keyboardHandoff) {
                            SmileyPanel(
                                packs = smileyPacks,
                                recentCodes = recentCodes,
                                onPick = { code ->
                                    viewModel.insertTextAtSelection(code)
                                    if (code !in DefaultSmileyCodes) {
                                        viewModel.recordSmileyUsed(code)
                                    }
                                },
                                // 与回复输入区同一套内边距：表情网格与分类栏不贴死屏幕左右边缘
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                // 权限限制提示
                val permissionWarning = permissionDenied
                if (permissionWarning != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .background(
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                                RoundedCornerShape(10.dp),
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = permissionWarning,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }

                // 主题分类选择 Chip
                val types = threadTypes
                if (types.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 4.dp)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "分类:",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                        )
                        for (type in types) {
                            val isSelected = selectedType?.id == type.id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isSelected) primaryColor else colors.surface,
                                    )
                                    .border(
                                        width = if (isSelected) 0.dp else 0.8.dp,
                                        color = if (isSelected) Color.Transparent else colors.divider,
                                        shape = RoundedCornerShape(16.dp),
                                    )
                                    .clickable { viewModel.onSelectType(type) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = type.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) Color.White else colors.textPrimary,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }

                // 核心输入卡片
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // 标题输入框（编辑评论时站点不给标题，这一行一并收起）
                        if (showSubject) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                BasicTextField(
                                    value = subject,
                                    onValueChange = { viewModel.onSubjectChange(it) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    textStyle = TextStyle(
                                        color = bodyTextColor(),
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    cursorBrush = SolidColor(primaryColor),
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(subjectFocusRequester)
                                        .onFocusChanged { if (it.isFocused) switchToKeyboard(focusMessage = false) },
                                    decorationBox = { innerTextField ->
                                        if (subject.isEmpty()) {
                                            Text(
                                                "输入帖子标题…",
                                                style = TextStyle(
                                                    color = colors.textHint,
                                                    fontSize = 17.sp,
                                                    fontWeight = FontWeight.Bold,
                                                ),
                                            )
                                        }
                                        innerTextField()
                                    },
                                )
                                Text(
                                    text = "${subject.length}/80",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (subject.length > 70) MaterialTheme.colorScheme.error else colors.textHint,
                                )
                            }
                        }

                        // 分割细线
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.6.dp)
                                .background(colors.divider),
                        )
                        Spacer(Modifier.height(12.dp))

                        // 正文输入框与表情 Overlay：滚动交给外层容器（verticalScroll），输入框与
                        // 贴图同处一个滚动容器、随 ScrollState 一起滚，表情不再漂移；滚动量是
                        // ScrollState 真值，键盘弹起后能可靠滚到光标。字段仍撑满可视高度，
                        // 点卡片空白处也能落光标
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .heightIn(min = 80.dp)
                                // onSizeChanged 必须在 verticalScroll 之前（更外层）：否则它测量的是
                                // 可滚动内容的高度（文本总高），而不是视口高度，滚到光标时算出的
                                // 目标偏移恒为 0、等于没滚
                                .onSizeChanged { messageBoxHeightPx = it.height; messageBoxHeightDp = with(density) { it.height.toDp() } }
                                .verticalScroll(messageScrollState),
                        ) {
                            BasicTextField(
                                value = message,
                                onValueChange = { onComposerMessageChange(it) },
                                textStyle = TextStyle(
                                    color = bodyTextColor(),
                                    fontSize = 15.sp,
                                    lineHeight = 24.sp,
                                ),
                                cursorBrush = SolidColor(primaryColor),
                                visualTransformation = smileyVisualTransformation,
                                onTextLayout = { textLayout = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = messageBoxHeightDp)
                                    .focusRequester(messageFocusRequester)
                                    .onFocusChanged { if (it.isFocused) switchToKeyboard(focusMessage = false) },
                                decorationBox = { innerTextField ->
                                    Box(Modifier.fillMaxWidth()) {
                                        if (message.text.isEmpty()) {
                                            Text(
                                                "请在这里输入帖子正文，分享你的见解或交流提问…",
                                                style = TextStyle(
                                                    color = colors.textHint,
                                                    fontSize = 15.sp,
                                                    lineHeight = 24.sp,
                                                ),
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                            )
                            // 表情贴图与输入框同处滚动容器：随 ScrollState 一起滚，表情不再漂移。
                            // 两者在同一未滚动坐标系里，容器滚动整体平移，无需额外减 scrollOffset
                            val layout = textLayout
                            if (layout != null) {
                                SmileyOverlay(
                                    text = message.text,
                                    layout = layout,
                                    smileyByCode = smileyByCode,
                                )
                            }
                        }

                        // 已上传图片附件缩略列表
                        if (uploadedImages.isNotEmpty() || uploading) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "已上传图片附件 (${uploadedImages.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textSecondary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(8.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                items(uploadedImages, key = { it.aid }) { attachment ->
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(colors.background)
                                            .border(0.6.dp, colors.divider, RoundedCornerShape(10.dp)),
                                    ) {
                                        AsyncImage(
                                            model = attachment.imageModel,
                                            contentDescription = attachment.fileName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clickable {
                                                    previewRestoreKeyboard = imeHeight > 0.dp
                                                    focusManager.clearFocus()
                                                    keyboard?.hide()
                                                    pendingPreviewIndex = uploadedImages.indexOf(attachment)
                                                },
                                        )

                                        // 点击再次插入标记
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .fillMaxWidth()
                                                .background(Color.Black.copy(alpha = 0.55f))
                                                .clickable {
                                                    viewModel.insertTextAtSelection("[attachimg]${attachment.aid}[/attachimg]")
                                                    AppToast.show("已插入 [attachimg] 标记")
                                                }
                                                .padding(vertical = 2.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                "插入",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                fontSize = 10.sp,
                                            )
                                        }

                                        // 删除按钮
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.6f))
                                                .clickable { viewModel.removeAttachment(attachment) },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                "✕",
                                                style = TextStyle(color = Color.White, fontSize = 10.sp),
                                            )
                                        }
                                    }
                                }

                                if (uploading) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .size(80.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(colors.background)
                                                .border(0.6.dp, colors.divider, RoundedCornerShape(10.dp)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.dp,
                                                color = primaryColor,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // @朋友 全屏选择层覆盖在根布局之上
        var atPickerFocusRequest by remember { mutableStateOf(0) }
        // 选择页的搜索框抢过焦点，回来必须显式要一次，否则键盘不再弹出、光标也不知去向
        LaunchedEffect(atPickerFocusRequest) {
            if (atPickerFocusRequest > 0) {
                messageFocusRequester.requestFocus()
                keyboard?.show()
            }
        }
        if (showAtPicker) {
            AtUserPickerPage(
                onDismiss = {
                    showAtPicker = false
                    atPickerFocusRequest++
                },
                onPick = { username ->
                    showAtPicker = false
                    viewModel.insertTextAtSelection("@$username ")
                    viewModel.noteAtUsers(listOf(username))
                    atPickerFocusRequest++
                },
            )
        }

        // 点附件缩略图看大图：整组可左右翻页，点中的那张作首页；本地文件没有网络地址，只留缩放与翻页
        if (previewImageIndex >= 0 && uploadedImages.isNotEmpty()) {
            ImagePreview(
                urls = uploadedImages.map { it.imageModel },
                initialIndex = previewImageIndex.coerceAtMost(uploadedImages.lastIndex),
                onDismiss = {
                    previewImageIndex = -1
                    // 看大图前收起的键盘请回来：接着写不用再点一下输入框。用户自己收起
                    // 键盘再看图的（previewRestoreKeyboard 为 false）不硬弹
                    if (previewRestoreKeyboard) switchToKeyboard()
                },
            )
        }
    }
}

/** 统一发帖顶栏 */
@Composable
private fun PostEditTopBar(
    forumName: String,
    canSubmit: Boolean,
    isSending: Boolean,
    editing: Boolean = false,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current
    val primaryColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.topBar)
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_back),
                contentDescription = "返回",
                tint = colors.textPrimary,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(Modifier.width(4.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (editing) "编辑" else "发布新帖",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = bodyTextColor(),
            )
            Text(
                text = forumName,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                fontSize = 12.sp,
            )
        }

        Spacer(Modifier.width(8.dp))

        // 发布胶囊按钮
        // 深色下主题色被提亮，白字对比不足：按背景亮度取前景并压到 DarkBrightTextAlpha
        val submitForeground = if (primaryColor.luminance() > 0.5f) {
            Color(0xFF111827)
        } else {
            if (isDark) Color.White.copy(alpha = DarkBrightTextAlpha) else Color.White
        }
        val submitBackground =
            if (canSubmit && !isSending) {
                if (isDark) primaryColor.copy(alpha = 0.5f) else primaryColor
            } else {
                colors.textHint.copy(alpha = 0.25f)
            }
        Box(
            modifier = Modifier
                .clip(AbsoluteRoundedCornerShape(50))
                .background(submitBackground)
                .clickable(enabled = canSubmit && !isSending, onClick = onSubmit)
                .padding(horizontal = 16.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isSending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = submitForeground,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = if (editing) "保存" else "发布",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (canSubmit) submitForeground else colors.textHint,
                )
            }
        }
    }
}
