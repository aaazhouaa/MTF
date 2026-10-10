package com.aazhou.mtforum.ui.screen.postedit

import android.net.Uri
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
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
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
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.screen.threaddetail.AtSuggestionStrip
import com.aazhou.mtforum.ui.screen.threaddetail.AtUserPickerPage
import com.aazhou.mtforum.ui.screen.threaddetail.ComposerToolButton
import com.aazhou.mtforum.ui.screen.threaddetail.DefaultSmileyCodes
import com.aazhou.mtforum.ui.screen.threaddetail.InlineInsertBar
import com.aazhou.mtforum.ui.screen.threaddetail.InsertKind
import com.aazhou.mtforum.ui.screen.threaddetail.InsertPanel
import com.aazhou.mtforum.ui.screen.threaddetail.KeyboardTracker
import com.aazhou.mtforum.ui.screen.threaddetail.MinKeyboardHeight
import com.aazhou.mtforum.ui.screen.threaddetail.STABLE_KEYBOARD_FRAMES
import com.aazhou.mtforum.ui.screen.threaddetail.SmileyOverlay
import com.aazhou.mtforum.ui.screen.threaddetail.SmileyPanel
import com.aazhou.mtforum.ui.screen.threaddetail.atTokenAt
import com.aazhou.mtforum.ui.screen.threaddetail.filterAtUsers
import com.aazhou.mtforum.ui.screen.threaddetail.formatRichComposerText
import com.aazhou.mtforum.ui.screen.threaddetail.handleTagPairDeletion
import com.aazhou.mtforum.ui.screen.threaddetail.insertAtSelection
import com.aazhou.mtforum.ui.screen.threaddetail.prepareUploadFile
import com.aazhou.mtforum.ui.screen.threaddetail.smileyCodeStartBefore
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
) {
    val viewModel: PostEditViewModel = appViewModel {
        PostEditViewModel(fid, forumName, it.repository, it.smileyStore)
    }

    val colors = LocalMtfColors.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    val initData by viewModel.initData.collectAsStateWithLifecycle()
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

    var panel by remember { mutableStateOf(PostComposerPanel.None) }
    var activeInsertKind by remember { mutableStateOf<InsertKind?>(null) }
    var inlineUrlInput by remember { mutableStateOf("") }
    var inlineTitleInput by remember { mutableStateOf("") }
    var showAtPicker by remember { mutableStateOf(false) }

    val subjectFocusRequester = remember { FocusRequester() }
    val messageFocusRequester = remember { FocusRequester() }
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    val imeInsets = WindowInsets.ime
    val imeHeight = with(density) { imeInsets.getBottom(density).toDp() }
    var keyboardHeight by remember {
        mutableStateOf(KeyboardTracker.getInitialHeight(context, density))
    }
    var smileySlot by remember {
        mutableStateOf(KeyboardTracker.getInitialHeight(context, density))
    }
    var keyboardHandoff by remember { mutableStateOf(false) }

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

    LaunchedEffect(state) {
        when (val s = state) {
            is PostEditViewModel.PostState.Success -> {
                AppToast.show("发布成功")
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

    fun switchToKeyboard() {
        keyboardHandoff = true
        panel = PostComposerPanel.None
        messageFocusRequester.requestFocus()
        keyboard?.show()
        scope.launch {
            var stableFrames = 0
            while (true) {
                withFrameNanos { }
                val px = imeInsets.getBottom(density)
                if (px > 0) stableFrames++ else stableFrames = 0
                if (stableFrames >= STABLE_KEYBOARD_FRAMES) break
            }
            keyboardHandoff = false
        }
    }

    // 软键盘升起时自动收起表情面板
    LaunchedEffect(imeHeight) {
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

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val file = prepareUploadFile(context, uri)
                val aid = viewModel.uploadImageFile(file)
                if (aid != null) {
                    viewModel.insertTextAtSelection("[attachimg]$aid[/attachimg]")
                    AppToast.show("图片上传成功并已插入正文")
                } else {
                    AppToast.show("图片上传失败")
                }
            } catch (e: Exception) {
                AppToast.show(e.message ?: "图片处理失败")
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
        val shrunkByOne = next.text.length == message.text.length - 1 &&
            next.selection.collapsed && message.selection.collapsed
        if (shrunkByOne) {
            val tagPairResult = handleTagPairDeletion(message, next)
            if (tagPairResult != null) {
                viewModel.onMessageChange(tagPairResult)
                return
            }
            val codeStart = smileyCodeStartBefore(message.text, message.selection.start)
            if (codeStart >= 0) {
                val codeEnd = message.text.indexOf(']', codeStart) + 1
                val text = message.text.removeRange(codeStart, codeEnd)
                viewModel.onMessageChange(message.copy(text = text, selection = TextRange(codeStart)))
                return
            }
        }
        viewModel.onMessageChange(next)
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
                    canSubmit = subject.isNotBlank() && message.text.isNotBlank() && initData?.permissionDenied == null,
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
                            showAtPicker = true
                        }

                        ComposerToolButton(R.drawable.ic_add, "插入内容") {
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

                    // 扩展面板槽位
                    val currentInsert = activeInsertKind
                    if (currentInsert != null) {
                        InlineInsertBar(
                            kind = currentInsert,
                            urlInput = inlineUrlInput,
                            titleInput = inlineTitleInput,
                            onUrlChange = { inlineUrlInput = it },
                            onTitleChange = { inlineTitleInput = it },
                            onDismiss = { activeInsertKind = null },
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
                                }
                                viewModel.insertTextAtSelection(tag)
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
                                val selectedText = if (!message.selection.collapsed) {
                                    val min = minOf(message.selection.start, message.selection.end)
                                    val max = maxOf(message.selection.start, message.selection.end)
                                    message.text.substring(min, max)
                                } else ""
                                inlineUrlInput = selectedText
                                inlineTitleInput = ""
                                activeInsertKind = kind
                                panel = PostComposerPanel.None
                            },
                        )
                    } else if (panel == PostComposerPanel.None || keyboardHandoff) {
                        Spacer(Modifier.height(imeHeight.coerceAtLeast(0.dp)))
                    } else if (panel == PostComposerPanel.Smiley) {
                        SmileyPanel(
                            packs = smileyPacks,
                            recentCodes = recentCodes,
                            onPick = { code ->
                                viewModel.insertTextAtSelection(code)
                                if (code !in DefaultSmileyCodes) {
                                    viewModel.recordSmileyUsed(code)
                                }
                            },
                            modifier = Modifier.height(smileySlot),
                        )
                    }
                }
            },
        ) { paddingValues ->
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState),
            ) {
                // 权限限制提示
                val permissionWarning = initData?.permissionDenied
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
                val types = initData?.types.orEmpty()
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
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // 标题输入框
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
                                    color = colors.textPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                cursorBrush = SolidColor(primaryColor),
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(subjectFocusRequester),
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

                        // 分割细线
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.6.dp)
                                .background(colors.divider),
                        )
                        Spacer(Modifier.height(12.dp))

                        // 正文输入框与表情 Overlay
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
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
                                    .fillMaxSize()
                                    .focusRequester(messageFocusRequester),
                                decorationBox = { innerTextField ->
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
                                },
                            )

                            // 贴合表情图层
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
                                            model = attachment.localFile,
                                            contentDescription = attachment.fileName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
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

                Spacer(Modifier.height(16.dp))
            }
        }

        // @朋友 全屏选择层覆盖在根布局之上
        if (showAtPicker) {
            AtUserPickerPage(
                onDismiss = { showAtPicker = false },
                onPick = { username ->
                    showAtPicker = false
                    viewModel.insertTextAtSelection("@$username ")
                    messageFocusRequester.requestFocus()
                    keyboard?.show()
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
    onBack: () -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = LocalMtfColors.current
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
                text = "发布新帖",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
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
        Box(
            modifier = Modifier
                .clip(AbsoluteRoundedCornerShape(50))
                .background(
                    if (canSubmit && !isSending) primaryColor else colors.textHint.copy(alpha = 0.25f),
                )
                .clickable(enabled = canSubmit && !isSending, onClick = onSubmit)
                .padding(horizontal = 16.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isSending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = "发布",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (canSubmit) Color.White else colors.textHint,
                )
            }
        }
    }
}
