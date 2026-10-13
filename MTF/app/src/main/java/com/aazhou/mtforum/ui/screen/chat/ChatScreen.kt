package com.aazhou.mtforum.ui.screen.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aazhou.mtforum.R
import com.aazhou.mtforum.data.model.ChatMessage
import com.aazhou.mtforum.data.parser.SmileyPack
import com.aazhou.mtforum.image.smallImageRequest
import com.aazhou.mtforum.ui.common.ErrorBox
import com.aazhou.mtforum.ui.common.LoadingBox
import com.aazhou.mtforum.ui.common.MtfTopBarHeight
import com.aazhou.mtforum.ui.common.UiState
import com.aazhou.mtforum.ui.common.AppToast
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.common.container
import com.aazhou.mtforum.ui.screen.threaddetail.ComposerScrollTracker
import com.aazhou.mtforum.ui.screen.threaddetail.HANDOFF_GIVEUP_FRAMES
import com.aazhou.mtforum.ui.screen.threaddetail.KeyboardTracker
import com.aazhou.mtforum.ui.screen.threaddetail.MinKeyboardHeight
import com.aazhou.mtforum.ui.screen.threaddetail.STABLE_KEYBOARD_FRAMES
import com.aazhou.mtforum.ui.screen.threaddetail.SmileyOverlay
import com.aazhou.mtforum.ui.screen.threaddetail.SmileyPanel
import com.aazhou.mtforum.ui.screen.threaddetail.composerDeletionResult
import com.aazhou.mtforum.ui.screen.threaddetail.formatRichComposerText
import com.aazhou.mtforum.ui.screen.threaddetail.insertAtSelection
import com.aazhou.mtforum.ui.screen.threaddetail.prepareUploadFile
import com.aazhou.mtforum.ui.screen.threaddetail.snapSelectionOutOfSmiley
import com.aazhou.mtforum.ui.theme.LocalIsDarkTheme
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.bodyTextColor
import com.aazhou.mtforum.ui.theme.dimImageInDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uid: String,
    targetName: String? = null,
    onBack: () -> Unit,
    onUserClick: ((String) -> Unit)? = null,
) {
    val colors = LocalMtfColors.current
    val viewModel: ChatViewModel = appViewModel {
        ChatViewModel(uid, targetName, it.repository, it.sessionStore)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val displayName by viewModel.targetName.collectAsStateWithLifecycle()
    // 对方在线状态随会话数据来：站点只在标题行给「(在线)」时才知道，未登录/解析不到为 null
    val targetOnline = (state as? UiState.Success)?.data?.targetOnline
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val isUploading by viewModel.isUploading.collectAsStateWithLifecycle()
    val myAvatar by viewModel.myAvatarUrl.collectAsStateWithLifecycle()
    val targetAvatar by viewModel.targetAvatarUrl.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    var smileyPanelVisible by remember { mutableStateOf(false) }
    val inputFocusRequester = remember { FocusRequester() }
    var inputLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val inputScrollTracker = remember { ComposerScrollTracker() }
    var inputFieldHeight by remember { mutableIntStateOf(0) }
    // 贴图按未滚动的文本坐标画：输入框自己滚动时要减掉这份滚动量，表情才不会停在原位
    val inputScrollOffset = inputLayout
        ?.takeIf { it.layoutInput.text.text == inputText.text }
        ?.let {
            inputScrollTracker.update(it, inputText.selection, inputFieldHeight)
            inputScrollTracker.offset
        } ?: 0f
    var inputFocused by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val imeHeight = with(density) { imeInsets.getBottom(density).toDp() }
    // 表情面板占位高度：打开那一刻捕获到的真实键盘高度，之后恒定不变，直到下次再打开。
    // 表情面板与键盘共用底部这一个槽，二者严格等高，切换时输入栏既不上窜也不下塌
    var smileySlot by remember { mutableStateOf(KeyboardTracker.getInitialHeight(context, density)) }
    var keyboardHeight by remember { mutableStateOf(KeyboardTracker.getInitialHeight(context, density)) }
    // 从表情面板切回键盘的交接期：期间底部槽仍锁死在表情面板的高度，键盘从屏幕底部
    // 升起、把面板逐步盖住，升到位后才改由 ime 接管，全程高度不变
    var keyboardHandoff by remember { mutableStateOf(false) }
    var smileyPacks by remember { mutableStateOf<List<SmileyPack>>(emptyList()) }
    val recentCodes by context.container().smileyStore.recent
        .collectAsStateWithLifecycle(initialValue = emptyList())
    // 表情码要染成透明色由贴图层画图：没有这一层，插进来的 [闭嘴] 就只是段文字
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
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        smileyPacks = runCatching { context.container().repository.smileyPacks() }
            .getOrDefault(emptyList())
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
        val stableIme = with(density) { imeInsets.getBottom(density).toDp() }
        if (stableIme > 0.dp) {
            // 交接完成：键盘的真实高度可能与此前捕获的不同（冷启动第一次开面板时还没有
            // 历史高度），按它校正占位高度，收起面板那一帧底部槽才不会突变
            smileySlot = stableIme
            smileyPanelVisible = false
        }
    }

    // 底部槽高度只有一个真源：表情面板展开时锁死在捕获到的键盘高度，交接期保持不变，
    // 其余时候跟随 ime（键盘起落必须让位），键盘没起来就什么都不占。
    // 输入栏不挂 imePadding：那样键盘起落时底部整条逐帧增高，工具栏跟着上下跳
    val bottomSlotHeight = when {
        smileyPanelVisible || keyboardHandoff -> smileySlot
        imeHeight > 0.dp -> imeHeight
        else -> 0.dp
    }

    val scope = rememberCoroutineScope()

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val file = prepareUploadFile(context, uri)
                viewModel.uploadChatImage(file) { url ->
                    if (url != null) {
                        // 私聊没有附件：插 [img] 外链，站点 BBCode 会渲染成图
                        inputText = inputText.insertAtSelection("[img]$url[/img]")
                    }
                }
            } catch (e: Exception) {
                AppToast.show(e.message ?: "图片处理失败")
            }
        }
    }

    Scaffold(
        containerColor = colors.background,
        contentColor = bodyTextColor(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.topBar),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_back),
                            contentDescription = "返回",
                            tint = bodyTextColor(),
                        )
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            displayName,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                            fontWeight = FontWeight.Bold,
                            color = bodyTextColor(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // 站点在标题行给出「(在线)/(离线)」；未提及状态时不占位
                        if (targetOnline != null) {
                            Spacer(Modifier.width(5.dp))
                            Text(
                                if (targetOnline) "在线" else "离线",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = if (targetOnline) Color(0xFF22C55E) else colors.textHint,
                            )
                        }
                    }
                },
                actions = {
                    if (onUserClick != null) {
                        IconButton(onClick = { onUserClick(uid) }) {
                            if (!targetAvatar.isNullOrBlank()) {
                                AsyncImage(
                                    model = smallImageRequest(targetAvatar),
                                    contentDescription = "查看对方主页",
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(colors.quoteBackground)
                                        .dimImageInDark(),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(colors.quoteBackground),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_me),
                                        contentDescription = "查看对方主页",
                                        tint = colors.iconSecondary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                },
                expandedHeight = MtfTopBarHeight,
            )
        },
        bottomBar = {
            // 输入框窗口：增加顶部圆角与描边
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(colors.topBar)
                    .border(
                        width = 0.5.dp,
                        color = colors.divider,
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    )
                    .navigationBarsPadding(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    // 输入框改为一条 0.5dp 主题色细线
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 38.dp, max = 120.dp)
                                .padding(horizontal = 2.dp, vertical = 6.dp),
                        ) {
                            BasicTextField(
                                value = inputText,
                                onValueChange = { next ->
                                    // 退格删到表情码/成对标签边界时一次删掉整组，点表情中间
                                    // 则把光标挪到码两侧，别落进码里把码拆坏
                                    inputText = (inputText.composerDeletionResult(next) ?: next)
                                        .snapSelectionOutOfSmiley(smileyByCode)
                                },
                                textStyle = TextStyle(color = bodyTextColor(), fontSize = 15.sp, lineHeight = 21.sp),
                                cursorBrush = SolidColor(primaryColor),
                                visualTransformation = smileyVisualTransformation,
                                onTextLayout = { inputLayout = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(inputFocusRequester)
                                    // 用户直接点输入栏想打字而表情面板还开着：同样走交接，键盘升起来
                                    // 把面板接替掉，底部槽高度不变
                                    .onFocusChanged {
                                        inputFocused = it.isFocused
                                        if (it.isFocused && smileyPanelVisible) keyboardHandoff = true
                                    }
                                    .onSizeChanged { inputFieldHeight = it.height },
                                maxLines = 5,
                                decorationBox = { innerTextField ->
                                    Box(Modifier.fillMaxWidth()) {
                                        SmileyOverlay(
                                            text = inputText.text,
                                            layout = inputLayout,
                                            smileyByCode = smileyByCode,
                                            scrollOffset = inputScrollOffset,
                                            modifier = Modifier.matchParentSize().clipToBounds(),
                                        )
                                        if (inputText.text.isEmpty()) {
                                            Text(
                                                "发送私信消息...",
                                                color = colors.textHint,
                                                fontSize = 14.5.sp,
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                            )
                        }

                        // 一条 0.5dp 主题色细线
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = primaryColor,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // 下方独立操作栏：左侧表情与图片，右侧发送按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            ChatToolButton(
                                iconRes = R.drawable.ic_smiley,
                                contentDescription = "表情",
                                isActive = smileyPanelVisible,
                                onClick = {
                                    if (smileyPanelVisible) {
                                        keyboardHandoff = true
                                        inputFocusRequester.requestFocus()
                                        keyboard?.show()
                                    } else {
                                        keyboardHandoff = false
                                        smileySlot = if (imeHeight > MinKeyboardHeight && imeHeight >= keyboardHeight) {
                                            imeHeight
                                        } else {
                                            keyboardHeight
                                        }
                                        smileyPanelVisible = true
                                        focusManager.clearFocus()
                                        keyboard?.hide()
                                    }
                                },
                            )
                            ChatToolButton(
                                iconRes = R.drawable.ic_image,
                                contentDescription = "图片",
                                isActive = false,
                                onClick = {
                                    imagePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                },
                            )
                        }

                        Spacer(Modifier.weight(1f))

                        val canSend = inputText.text.isNotBlank() && !isSending

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (canSend) primaryColor else primaryColor.copy(alpha = 0.35f))
                                .clickable(enabled = canSend) {
                                    val msg = inputText.text
                                    viewModel.sendMessage(msg) {
                                        inputText = TextFieldValue("")
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White,
                                )
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.ic_send),
                                    contentDescription = "发送",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
                // 键盘与表情面板的下部容器：高度锁定为 bottomSlotHeight，键盘收起/弹出时输入栏纹丝不动
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(bottomSlotHeight),
                ) {
                    // 一开始切回键盘，表情页立刻让位变空白：底部槽只剩一块实色区域等键盘
                    // 升起来填充，不再让表情网格被键盘一点点盖上去
                    if (smileyPanelVisible && !keyboardHandoff) {
                        SmileyPanel(
                            packs = smileyPacks,
                            recentCodes = recentCodes,
                            onPick = { code ->
                                inputText = inputText.insertAtSelection(code)
                                scope.launch { context.container().smileyStore.addRecent(code) }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        },
    ) { padding ->
        when (val current = state) {
            is UiState.Loading -> LoadingBox(Modifier.padding(padding))
            is UiState.Error -> ErrorBox(current.message, { viewModel.load() }, Modifier.padding(padding))
            is UiState.Success -> {
                val messages = current.data.messages

                LaunchedEffect(messages.size) {
                    if (messages.isNotEmpty()) {
                        listState.animateScrollToItem(messages.size - 1)
                    }
                }

                if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(R.drawable.ic_notice_pm),
                                contentDescription = null,
                                tint = colors.textHint,
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "暂无聊天记录，发条消息打个招呼吧~",
                                color = colors.textSecondary,
                                fontSize = 14.sp,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(messages, key = { it.id }) { message ->
                            ChatBubbleItem(
                                message = message,
                                fallbackMyAvatar = myAvatar,
                                onAvatarClick = {
                                    if (!message.isSelf && onUserClick != null) {
                                        onUserClick(uid)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleItem(
    message: ChatMessage,
    fallbackMyAvatar: String?,
    onAvatarClick: () -> Unit,
) {
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current
    val primaryColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isSelf) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!message.isSelf) {
            // 对方头像
            AsyncImage(
                model = smallImageRequest(message.avatarUrl),
                contentDescription = null,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(colors.quoteBackground)
                    .clickable(onClick = onAvatarClick)
                    .dimImageInDark(),
            )
            Spacer(Modifier.width(8.dp))
        }

        // 气泡与时间同列：时间贴气泡下沿，靠气泡所在侧对齐（己方靠右、对方靠左）
        Column(horizontalAlignment = if (message.isSelf) Alignment.End else Alignment.Start) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(
                        if (message.isSelf) {
                            RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 4.dp,
                                bottomStart = 16.dp,
                                bottomEnd = 16.dp,
                            )
                        } else {
                            RoundedCornerShape(
                                topStart = 4.dp,
                                topEnd = 16.dp,
                                bottomStart = 16.dp,
                                bottomEnd = 16.dp,
                            )
                        },
                    )
                    .then(
                        if (message.isSelf) {
                            Modifier.background(primaryColor)
                        } else {
                            Modifier
                                .then(
                                    if (isDark) {
                                        Modifier.border(0.5.dp, colors.divider, RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                                    } else {
                                        Modifier
                                    },
                                )
                                .background(colors.surface)
                        },
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = message.text,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = if (message.isSelf) Color.White else bodyTextColor(),
                )
            }
            if (!message.time.isNullOrBlank()) {
                Text(
                    text = message.time,
                    fontSize = 11.sp,
                    color = colors.textHint,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        if (message.isSelf) {
            Spacer(Modifier.width(8.dp))
            // 己方头像
            AsyncImage(
                model = smallImageRequest(message.avatarUrl ?: fallbackMyAvatar),
                contentDescription = null,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(colors.quoteBackground)
                    .dimImageInDark(),
            )
        }
    }
}

/** 私聊输入栏的操作按钮（表情 / 图片）：精致 32dp 触控圆圈，支持激活态与点击反馈。 */
@Composable
private fun ChatToolButton(
    iconRes: Int,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalMtfColors.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val isDark = LocalIsDarkTheme.current
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                if (isActive) primaryColor.copy(alpha = if (isDark) 0.22f else 0.12f)
                else Color.Transparent,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = if (isActive) primaryColor else colors.iconSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}
