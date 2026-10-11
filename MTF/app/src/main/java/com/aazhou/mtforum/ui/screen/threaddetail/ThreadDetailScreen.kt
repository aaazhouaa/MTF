package com.aazhou.mtforum.ui.screen.threaddetail

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import com.aazhou.mtforum.ui.common.MtfConfirmDialog
import com.aazhou.mtforum.ui.common.MtfContentDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.imageLoader
import com.aazhou.mtforum.data.model.Post
import com.aazhou.mtforum.data.parser.SmileyPack
import com.aazhou.mtforum.image.smallImageRequest
import com.aazhou.mtforum.ui.common.AppToast
import com.aazhou.mtforum.ui.common.BottomBarBottomMargin
import com.aazhou.mtforum.ui.common.ReplyBarCapsuleHeight
import com.aazhou.mtforum.ui.common.ReplyBarTopPadding
import com.aazhou.mtforum.ui.common.replyBarContentPadding
import com.aazhou.mtforum.ui.common.EmptyBox
import com.aazhou.mtforum.ui.common.ErrorBox
import com.aazhou.mtforum.ui.common.ImagePreview
import com.aazhou.mtforum.image.ImageAspectCache
import com.aazhou.mtforum.ui.common.LoadingBox
import com.aazhou.mtforum.ui.common.PostContent
import com.aazhou.mtforum.ui.common.saveThreadDownload
import com.aazhou.mtforum.ui.common.UiState
import com.aazhou.mtforum.ui.common.VideoPlayerScreen
import com.aazhou.mtforum.ui.common.scrollToTop
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.common.collectImageUrls
import com.aazhou.mtforum.ui.common.container
import com.aazhou.mtforum.ui.common.openForumLink
import com.aazhou.mtforum.ui.common.plainTextOf
import com.aazhou.mtforum.ui.common.onDoubleTapToTop
import com.aazhou.mtforum.ui.common.TopBarVerticalPadding
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.theme.LocalIsDarkTheme
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.bodyTextColor
import com.aazhou.mtforum.ui.theme.accentTextColor
import com.aazhou.mtforum.ui.theme.dimImageInDark
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadDetailScreen(
    tid: String,
    onBack: () -> Unit,
    onUserClick: (String) -> Unit,
    onThreadClick: (String) -> Unit,
    /** 从消息提醒跳进来时要定位的楼层 pid（普通进帖为 null）。 */
    anchorPid: String? = null,
    /** 编辑自己的首帖：跳到整页编辑（带标题）。参数为 tid/pid/fid/版块名。 */
    onEditThread: (String, String, String, String) -> Unit = { _, _, _, _ -> },
) {
    val context = LocalContext.current
    val colors = LocalMtfColors.current
    val container = context.container()
    val viewModel: ThreadDetailViewModel = appViewModel {
        ThreadDetailViewModel(
            tid,
            it.repository,
            it.actionStore,
            it.sessionStore.uid,
            anchorPid = anchorPid,
        )
    }
    val myUid by viewModel.myUid.collectAsStateWithLifecycle()
    // 登录态与界面显示（「我的」页、首页顶栏）共用同一判据：AppContainer.currentUser。
    // 不能只查 cookie 里的 _auth——站点侧会话失效后 cookie 仍在，会出现界面显示未登录、
    // 互动却全部放行的分歧
    val currentUser by container.currentUser.collectAsStateWithLifecycle()
    val loggedIn = currentUser?.isLoggedIn == true
    val state by viewModel.state.collectAsStateWithLifecycle()
    val replyState by viewModel.replyState.collectAsStateWithLifecycle()
    val orderType by viewModel.orderType.collectAsStateWithLifecycle()
    val opOnly by viewModel.opOnly.collectAsStateWithLifecycle()
    val replyTarget by viewModel.replyTarget.collectAsStateWithLifecycle()
    val followState by viewModel.followState.collectAsStateWithLifecycle()
    val liked by viewModel.liked.collectAsStateWithLifecycle()
    val likeCount by viewModel.likeCount.collectAsStateWithLifecycle()
    val favorited by viewModel.favorited.collectAsStateWithLifecycle()
    val favoriteCount by viewModel.favoriteCount.collectAsStateWithLifecycle()
    val loadingMore by viewModel.loadingMore.collectAsStateWithLifecycle()
    val loadMoreState by viewModel.loadMoreState.collectAsStateWithLifecycle()
    val waterPidsState by viewModel.waterPids.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val likeUsers by viewModel.likeUsers.collectAsStateWithLifecycle()
    val hasNext by viewModel.hasNext.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val foldExpanded by viewModel.foldExpanded.collectAsStateWithLifecycle()
    // 从消息提醒跳进来时要定位的楼层
    val anchorPid by viewModel.anchor.collectAsStateWithLifecycle()
    // 定位完成后柔和闪烁两次的楼层（指出是哪一条评论）
    var highlightPid by remember { mutableStateOf<String?>(null) }

    var composerVisible by remember { mutableStateOf(false) }
    // 正在编辑的楼层（自己的帖子/评论）：编辑面板以此为准，提交成功后由 ViewModel 清空
    val editTarget by viewModel.editTarget.collectAsStateWithLifecycle()
    val editForm by viewModel.editForm.collectAsStateWithLifecycle()
    // 编辑面板里的正文：站点原文由 ViewModel 取回后填入，之后由用户改
    var editText by remember { mutableStateOf(TextFieldValue("")) }
    // 最近用过的表情码：落在本地，反复开关输入面板、重启应用都不丢
    val recentCodes by container.smileyStore.recent.collectAsStateWithLifecycle(initialValue = emptyList())
    var smileyPacks by remember { mutableStateOf<List<SmileyPack>>(emptyList()) }
    // 草稿按回复对象各存一份（key 为空即评论本帖）：关掉面板不丢、换个楼层再回来还在，
    // 离开本帖时随这屏的状态一起销毁。用 TextFieldValue 而不是 String：工具栏要在光标处
    // 插入标记，得知道光标在哪
    val drafts = remember { mutableStateMapOf<String?, TextFieldValue>() }
    // 发送成功后要按发送时那一份清草稿：reply() 会把回复目标复位，Success 回来时
    // 当前的回复对象已经变成本帖，照当时取 key 会清错
    var sendingDraftKey by remember { mutableStateOf<String?>(null) }
    // 键盘是否立在上面：只认「键盘确实立着」，不拿 ime 的 bottom 当开关——它是逐帧插值的
    // 动画中间值，收起动画里一路非零。系统返回键或输入法自带的收起键收键盘时，返回事件
    // 先过输入法那一关（输入分发链里 IME 阶段排在 App 之前），常被输入法自己吞掉：输入框
    // 不失焦、ime 也要等动画走完才归零，两条旧信号都会让第二下返回键误判成「键盘还在」
    // 而空转，非得等动画结束才恢复
    var keyboardShown by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // 组合期取一次 density 与 ime Insets：两者都是 @Composable 读取，帧循环里只能用值
    val imeDensity = LocalDensity.current
    val imeInsets = WindowInsets.ime
    // 逐帧盯 ime 起落：立稳了才置位；一开始往下掉就立刻清状态并断开输入连接——断开后返回
    // 事件不再过输入法那一关，第二下返回键当场就能走到返回键处理里，不必等收起动画播完
    LaunchedEffect(composerVisible) {
        if (!composerVisible) {
            keyboardShown = false
            return@LaunchedEffect
        }
        var lastPx = -1
        var hiding = false
        while (true) {
            withFrameNanos { }
            val px = imeInsets.getBottom(imeDensity)
            when {
                px <= 0 -> {
                    hiding = false
                    keyboardShown = false
                }
                hiding -> {
                    // 收起动画进行中：除非键盘又被重新唤起来（高度反涨），否则一直算已收起
                    if (px > lastPx) hiding = false else keyboardShown = false
                }
                lastPx > 0 && px < lastPx -> {
                    hiding = true
                    keyboardShown = false
                    focusManager.clearFocus()
                }
                else -> keyboardShown = true
            }
            lastPx = px
        }
    }
    // 收起输入面板：焦点、键盘、面板三件套一起收。光把 visible 置 false 不够——面板要播完
    // 退场动画才离开组合，键盘会悬在屏幕上陪着它滑下去
    val dismissComposer: () -> Unit = {
        keyboardShown = false
        focusManager.clearFocus()
        keyboardController?.hide()
        composerVisible = false
        // 编辑态下收起即放弃这次修改：站点侧一个字都不会改
        if (editTarget != null) viewModel.cancelEdit()
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 表情表只在第一次打开面板时拉，站点不常变；失败就空着，面板里提示加载失败
    LaunchedEffect(composerVisible) {
        if (composerVisible && smileyPacks.isEmpty()) {
            smileyPacks = runCatching { container.repository.smileyPacks() }.getOrDefault(emptyList())
        }
    }

    // 切换排序/只看楼主会整体替换楼层列表，屏幕不能跟着跳；
    // 切换前记下当前滚动锚点交给 ViewModel，新楼层上屏后再按它原地定位。
    val pendingScrollAnchor by viewModel.pendingScrollAnchor.collectAsStateWithLifecycle()
    val anchorNow: () -> ScrollAnchor = {
        ScrollAnchor(
            index = listState.firstVisibleItemIndex,
            offset = listState.firstVisibleItemScrollOffset,
        )
    }
    LaunchedEffect(replyState) {
        when (val r = replyState) {
            is ThreadDetailViewModel.ReplyState.Success -> {
                sendingDraftKey?.let { drafts.remove(it) }
                sendingDraftKey = null
                dismissComposer()
                AppToast.show("回复成功")
                viewModel.consumeReplyState()
                // 只重拉第 1 页并合并，保留已加载的后续页，不整页回退
                viewModel.refreshFirstPage()
            }
            is ThreadDetailViewModel.ReplyState.Error -> {
                AppToast.show(r.message)
                viewModel.consumeReplyState()
            }
            else -> Unit
        }
    }
    // 编辑面板：站点原文取回就打开面板并填入；失败只提示，面板不用开
    LaunchedEffect(editForm) {
        when (val f = editForm) {
            is ThreadDetailViewModel.EditFormState.Ready -> {
                editText = TextFieldValue(f.text)
                // 只消费一次：不复位的话面板每次重组都会又被这一态拉起
                viewModel.consumeEditForm()
                keyboardShown = false
                composerVisible = true
            }
            is ThreadDetailViewModel.EditFormState.Error -> {
                AppToast.show(f.message)
                viewModel.consumeEditForm()
            }
            else -> Unit
        }
    }
    // 从「编辑本帖」的整页回来：站点上的正文已经变了，重拉一次让新内容与「编辑」标记上屏。
    // 标志要用 rememberSaveable：编辑页压在上层时本页会离开组合，remember 的值会被重置，
    // 回来时再也看不出「刚刚改过」
    var threadEdited by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (threadEdited) {
            threadEdited = false
            viewModel.refreshFirstPage()
        }
    }
    LaunchedEffect(followState) {
        when (val f = followState) {
            // 进帖时同步来的初始态不弹提示，只有用户点了关注/取消关注才反馈
            is ThreadDetailViewModel.FollowState.Done ->
                if (f.fromUser) AppToast.show(if (f.following) "已关注" else "已取消关注")
            is ThreadDetailViewModel.FollowState.Error -> AppToast.show(f.message)
            else -> Unit
        }
    }
    LaunchedEffect(actionMessage) {
        val message = actionMessage ?: return@LaunchedEffect
        AppToast.show(message)
        viewModel.consumeActionMessage()
    }

    LaunchedEffect(pendingScrollAnchor) {
        val anchor = pendingScrollAnchor ?: return@LaunchedEffect
        // 等新楼层完成组合与测量再定位，否则目标下标可能还没布局出来
        withFrameNanos { }
        val lastIndex = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
        listState.scrollToItem(anchor.index.coerceAtMost(lastIndex), anchor.offset)
        viewModel.consumePendingScrollAnchor()
    }

    val page = (state as? UiState.Success)?.data

    // 贴图上传凭据随帖子页来，uid/hash 缺一个就不能上传
    val uploadCredentials = remember(page) {
        val uid = page?.uploadUid
        val hash = page?.uploadHash
        if (uid != null && hash != null) uid to hash else null
    }

    // 「@朋友」候选名单：与站点网页端编辑器同源。回复面板打开时拉一次，之后输入 @ 按
    // 关键字过滤；账号没好友时空名单，用户照样能手输用户名
    var atUsers by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(composerVisible) {
        if (composerVisible && atUsers == null) {
            atUsers = runCatching { container.repository.fetchAtUsers() }.getOrDefault(emptyList())
            atUsers?.let { viewModel.noteAtUsers(it) }
        }
    }

    // 返回键与点遮罩同一个路子：键盘在就先收键盘、面板留着，再按一次才收面板，
    // 不把用户直接扔出帖子。keyboardShown 由上面的帧循环维护，键盘一开始收起就算已收起，
    // 所以收起动画途中按返回键不会再走空
    BackHandler(enabled = composerVisible) {
        if (keyboardShown) {
            keyboardShown = false
            focusManager.clearFocus()
            keyboardController?.hide()
        } else {
            dismissComposer()
        }
    }
    val following = (followState as? ThreadDetailViewModel.FollowState.Done)?.following ?: false
    // 点击图片一律进内置全屏查看器；urls 为本组全部地址，便于左右翻页
    var preview by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    val openImage: (List<String>, Int) -> Unit = { urls, index ->
        if (urls.isNotEmpty()) preview = urls to index
    }
    // 点击正文视频一律进全屏播放页；正文滚动时不持播放器，避免多视频同时解码
    var videoUrl by remember { mutableStateOf<String?>(null) }
    val openVideo: (String) -> Unit = { url -> videoUrl = url }
    // 列表底部续拉：距离末尾还有约一屏（6 条）就开始加载下一页，不等到划到底。
    // 只在 Idle 时触发（加载失败也停在 Idle，用户继续滑动即可再次尝试）。
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = listState.layoutInfo.totalItemsCount
            hasNext && loadMoreState is ThreadDetailViewModel.LoadMoreState.Idle &&
                !loadingMore && total > 0 && last >= total - 6
        }
    }
    // key 用 shouldLoadMore 本身：布局完成（false→true）与每拉完一页（Loading→Idle）都会重启
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    val todo: (String) -> Unit = { msg -> AppToast.show(msg) }
    // 未登录时互动入口只弹提示：不发请求、不开弹窗、不改状态
    val requireLogin: (String) -> Boolean = { hint ->
        if (loggedIn) {
            true
        } else {
            AppToast.show(hint, LOGIN_HINT_DURATION_MS)
            false
        }
    }
    // 长按评论弹出的操作菜单目标
    var actionTarget by remember { mutableStateOf<Post?>(null) }
    // 是否显示「@朋友」选择页
    var atPickerVisible by remember { mutableStateOf(false) }
    // 从「@朋友」选择页回来时 +1，让回复框把焦点和键盘要回来
    var composerFocusRequest by remember { mutableIntStateOf(0) }
    // 是否显示打赏弹窗
    var rewardOpen by remember { mutableStateOf(false) }
    // 举报弹窗目标：pid 为 null 表示举报本帖
    var reportTarget by remember { mutableStateOf<ReportTarget?>(null) }
    // 删除确认弹窗目标（删除不可撤销，需二次确认）
    var deleteTarget by remember { mutableStateOf<Post?>(null) }
    // 举报/打赏入口已随作者栏移入「更多」菜单，回调须提到 Scaffold 之外供 topBar 使用
    val reportThread: () -> Unit = { if (requireLogin(LOGIN_HINT_REPORT)) reportTarget = ReportTarget(null) }
    val rewardThread: () -> Unit = { if (requireLogin(LOGIN_HINT_REWARD)) rewardOpen = true }
    // 「更多」菜单的下载：只保存点击这一刻已加载的内容（正文必存，评论不补拉），
    // 想收进后续评论就等加载完再点一次，按 tid 覆盖旧副本
    var downloadBusy by remember { mutableStateOf(false) }
    val onDownload: () -> Unit = {
        // 快照在点击这一刻同步冻结：之后自动续拉进来的评论不进这次下载
        val frame = viewModel.snapshotForDownload()
        if (frame == null) {
            AppToast.show("帖子还没加载出来，稍后再试")
        } else {
            scope.launch {
                downloadBusy = true
                val entry = try {
                    saveThreadDownload(
                        context = context,
                        page = frame,
                        downloadStore = container.downloadStore,
                        archiveStore = container.archiveStore,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                downloadBusy = false
                val savedReplies = frame.posts.count { !it.isFirstPost }
                AppToast.show(
                    when {
                        entry == null -> "保存失败，请稍后重试"
                        savedReplies == 0 -> "已保存正文到 下载/MTF（暂无评论）"
                        else -> "已保存到 下载/MTF（含已加载的 $savedReplies 楼评论）"
                    },
                )
            }
        }
    }

    // 底栏两颗毛玻璃胶囊采样本页内容：只记录内容区，底栏作为兄弟层再采样，
    // 避免与底栏的 drawBackdrop 形成回环
    // 主题色经 state 间接读取：rememberLayerBackdrop 以 onDraw lambda 为 remember key，
    // 直接捕获 colors.topBar 会在换主题时重建 LayerBackdrop，而新实例的 layerCoordinates
    // 只在布局变化时补，换主题不改布局，回复底栏的采样会一直为空
    val replyBarTopBar = rememberUpdatedState(colors.topBar)
    val replyBarBackdrop = rememberLayerBackdrop {
        drawRect(replyBarTopBar.value)
        drawContent()
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            // 整页背景纯白（top_bar），不是灰色页面底
            containerColor = colors.topBar,
            // containerColor 不是主题配色方案里的色（浅 #FFFFFF / 深 #161B22），M3 的
            // contentColorFor 取不到对应前景色就退回当前 LocalContentColor —— 即上个主题的
            // scaffold 内容色。深色切浅色时它会原样留下深色的正文色，正文就糊在深色底上；
            // 反之亦然。这里显式给出当前主题的正文色。
            contentColor = bodyTextColor(),
            topBar = {
                // 顶栏与首页同规格：状态栏避让 + 上下 10dp，自绘单行，不用 TopAppBar 的固定高
                // 作者信息整行搬进顶栏（头像/昵称/举报/赞赏/时间/地点/关注），标题区不再重复
                val op = page?.posts?.firstOrNull { it.isFirstPost }
                    ?: page?.posts?.firstOrNull()
                ThreadDetailTopBar(
                    post = op,
                    following = following,
                    // 胶囊只取决于「知道楼主是谁」：formhash 是一次性令牌，缓存上屏时它要等
                    // 补拉的第 1 页才回来，拿它当可见条件会让头像昵称都出来了、关注慢一拍
                    followEnabled = page?.opUid != null,
                    // 楼主是自己才给「编辑本帖」：站点只允许改自己的内容。
                    // 帖子带标题，编辑走整页（标题 + 正文 + 分类），与评论的底部面板不同
                    canEditThread = myUid.isNotBlank() && myUid == op?.authorUid,
                    showMore = true,
                    downloadBusy = downloadBusy,
                    onBack = onBack,
                    onFollow = { viewModel.toggleFollow() },
                    onReport = reportThread,
                    onReward = rewardThread,
                    onDownload = onDownload,
                    onEditThread = {
                        val fid = page?.fid
                        if (op != null && !fid.isNullOrBlank()) {
                            threadEdited = true
                            onEditThread(tid, op.pid, fid, page?.forumName.orEmpty())
                        } else {
                            AppToast.show("帖子信息还没加载好，稍后再试")
                        }
                    },
                    onUserClick = onUserClick,
                    onDoubleTap = { scope.launch { listState.scrollToTop() } },
                )
            },
            bottomBar = {
                // 面板弹出时底栏整条让位：面板挂在页面根上（见下方），根上的遮罩才盖得住顶栏
                if (page != null && !composerVisible) {
                    ReplyBar(
                        backdrop = replyBarBackdrop,
                        replyCount = page.totalReplies ?: 0,
                        liked = liked,
                        likeCount = likeCount,
                        favorited = favorited,
                        favoriteCount = favoriteCount,
                        onClick = {
                            if (requireLogin(LOGIN_HINT_REPLY)) {
                                viewModel.setReplyTarget(null)
                                composerVisible = true
                            }
                        },
                        onComments = {
                            if (requireLogin(LOGIN_HINT_REPLY)) scope.launch { listState.animateScrollToItem(1) }
                        },
                        onLike = { if (requireLogin(LOGIN_HINT_LIKE)) viewModel.toggleLike() },
                        onLikeLongPress = { viewModel.loadLikeUsers() },
                        onFavorite = { if (requireLogin(LOGIN_HINT_FAVORITE)) viewModel.toggleFavorite() },
                        onShare = { shareThread(context, page.subject, tid) },
                    )
                }
            },
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    // 与 ReplyBar 同一可见条件：回复面板弹出时底栏整条让位，采样层没有消费者，
                    // 不必再录全屏
                    .then(
                        if (page != null && !composerVisible) Modifier.layerBackdrop(replyBarBackdrop)
                        else Modifier
                    ),
            ) {
            when (val current = state) {
                is UiState.Loading -> LoadingBox(Modifier.fillMaxSize())
                is UiState.Error -> ErrorBox(current.message, { viewModel.load() }, Modifier.fillMaxSize())
                is UiState.Success -> {
                    val data = current.data
                    if (data.posts.isEmpty()) {
                        EmptyBox("没有内容", Modifier.fillMaxSize())
                    } else {
                        val op = data.posts.firstOrNull { it.isFirstPost } ?: data.posts.firstOrNull()
                        // 全帖图片序列：单图与图组共用，供全屏查看左右翻页。
                        // 楼层会在后台一批批变多，全量解析正文挪出主线程，否则每批
                        // 新增楼层都要在组合时重解析全部正文，长帖直接掉帧。
                        val pageImages by produceState(initialValue = emptyList<String>(), data.posts) {
                            val urls = withContext(Dispatchers.Default) {
                                data.posts.flatMap { collectImageUrls(it.contentHtml) }
                            }
                            value = urls
                            // 配图真实宽高比随楼层数据一并预取：正文单图首次组合即按终态
                            // 测量，快速滑动时不会因图片加载完成而撑高所在 item、列表跳屏
                            ImageAspectCache.prefetch(context, context.imageLoader, urls)
                        }
                        // 灌水集合在 ViewModel 后台算好，这里只消费，避免主线程逐楼层 Jsoup 判定
                        val waterPids = waterPidsState
                        val foldedCount = waterPids.size
                        // 楼层平铺展示：首帖由头部呈现，其余按当前排序一字排开
                        val visibleReplies = remember(data.posts, waterPids, foldExpanded) {
                            val replies = data.posts.filter { it !== op }
                            if (foldExpanded) replies else replies.filter { it.pid !in waterPids }
                        }
                        // 从消息提醒跳进来：目标楼层（可能在很后面的页）被拉回来后滚到它所在的位置
                        LaunchedEffect(anchorPid, visibleReplies, foldExpanded) {
                            val pid = anchorPid ?: return@LaunchedEffect
                            // 先把藏住它的灌水折叠展开（只看本次）
                            viewModel.revealAnchorReply(pid)
                            val index = visibleReplies.indexOfFirst { it.pid == pid }
                            if (index < 0) {
                                // 目标还没上屏：所在页没拉到，或刚展开的折叠还没重组完
                                return@LaunchedEffect
                            }
                            // 列表头部固定两项：帖子头部 + 评论标题栏（头部缺省时少一项）
                            val lazyIndex = index + (if (op != null) 1 else 0) + 1
                            // 必须等目标下标真的进入布局再滚：此刻组合虽已换上新的楼层列表，
                            // LazyColumn 可能还没测量它，直接滚会被夹到当前列表末尾——
                            // 表现为「总是跳到最后一楼」。滚完再确认一次，没到位就重试。
                            repeat(3) {
                                withTimeoutOrNull(2500) {
                                    snapshotFlow { listState.layoutInfo.totalItemsCount }
                                        .first { count -> count > lazyIndex }
                                }
                                withFrameNanos { }
                                listState.scrollToItem(lazyIndex)
                                withFrameNanos { }
                                if (listState.layoutInfo.visibleItemsInfo.any { it.key == pid }) {
                                    // 闪烁落在目标本身
                                    highlightPid = pid
                                    viewModel.consumeAnchor()
                                    // 闪烁只来一下：定位完就撤掉，楼层滚出屏幕再回来不会重复闪
                                    delay(FLASH_HIGHLIGHT_MS)
                                    highlightPid = null
                                    return@LaunchedEffect
                                }
                            }
                            viewModel.consumeAnchor()
                        }

                        PullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = { viewModel.refresh() },
                            // 内容铺到屏幕底：底栏是悬浮的半透明胶囊，帖子要从它下面滚过，
                            // 毛玻璃才有东西可采样；底部留白须覆盖底栏实际占高：输入胶囊 48dp
                            // + 上留白 6dp + 与首页底栏一致的底部间距，回复目标行出现时再多留一行
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = padding.calculateTopPadding()),
                        ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = listState,
                            contentPadding = PaddingValues(
                                bottom = replyBarContentPadding(),
                            ),
                        ) {
                            if (op != null) {
                                item(key = "header") {
                                    DetailHeader(
                                        post = op,
                                        subject = data.subject,
                                        forumName = data.forumName,
                                        onImageClick = openImage,
                                        onVideoClick = openVideo,
                                        pageImages = pageImages,
                                        onUnlock = {
                                            // 隐藏内容需回复后解锁：把回复目标复位到本帖并直接弹出输入面板，
                                            // 面板自己会聚焦把键盘请出来，不用再提示去底部找入口
                                            if (requireLogin(LOGIN_HINT_REPLY)) {
                                                viewModel.setReplyTarget(null)
                                                composerVisible = true
                                            }
                                        },
                                        onLinkClick = { url ->
                                            openForumLink(context, url, container.httpClient.host, onThreadClick)
                                        },
                                    )
                                }
                            }
                            item(key = "comment-header") {
                                CommentHeader(
                                    total = data.totalReplies,
                                    folded = foldedCount,
                                    foldExpanded = foldExpanded,
                                    opOnly = opOnly,
                                    orderType = orderType,
                                    onToggleFold = { viewModel.toggleFoldExpanded() },
                                    onOpOnly = {
                                        viewModel.setReplyFilter(true, orderType, anchorNow())
                                    },
                                    onOrder = {
                                        viewModel.setReplyFilter(false, it, anchorNow())
                                    },
                                )
                                HorizontalDivider(color = colors.divider)
                            }
                            if (visibleReplies.isEmpty()) {
                                item(key = "no-comment") {
                                    Box(
                                        Modifier.fillMaxWidth().padding(vertical = 28.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("还没有评论", color = colors.textHint, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                            items(visibleReplies, key = { it.pid }) { reply ->
                                // 定位闪烁：目标是本条
                                val highlighted = reply.pid == highlightPid
                                ReplyBlock(
                                    reply = reply,
                                    highlight = highlighted,
                                    onReply = {
                                        if (requireLogin(LOGIN_HINT_REPLY)) {
                                            viewModel.setReplyTarget(reply)
                                            composerVisible = true
                                        }
                                    },
                                    onImageClick = openImage,
                                    onVideoClick = openVideo,
                                    onUserClick = onUserClick,
                                    pageImages = pageImages,
                                    onLongPress = { actionTarget = it },
                                    onThreadClick = onThreadClick,
                                )
                            }
                            item(key = "pager") {
                                when (loadMoreState) {
                                    is ThreadDetailViewModel.LoadMoreState.Loading -> Box(
                                        Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(Modifier.size(22.dp))
                                    }
                                    is ThreadDetailViewModel.LoadMoreState.End -> Box(
                                        Modifier.fillMaxWidth().padding(20.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "已经到底了",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                            color = colors.textHint,
                                        )
                                    }
                                    else -> Unit
                                }
                            }
                        }
                        }
                    }
                }
            }
            }
        }
        // 输入面板弹出时：整页压一层遮罩（连顶栏和状态栏下方一起盖住），挡掉误点；
        // 面板挂在遮罩之上，所以遮罩能盖住顶栏而盖不住面板。
        // 遮罩的显隐要走 AnimatedVisibility：退出动画那一下必须还留在组合里播完，
        // 跟回复框一起被 if 摘掉的话就只剩硬闪
        AnimatedVisibility(
            visible = composerVisible,
            enter = fadeIn(animationSpec = tween(durationMillis = ScrimFadeMillis)),
            exit = fadeOut(animationSpec = tween(durationMillis = ScrimFadeMillis)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f))
                    // 遮罩只响应在回复框外部（空白区域）的点按，绝不误吞回复框内部按钮的操作。
                    // 键盘在不在都一律整体收起：两段式（先收键盘、再点一次才收面板）会在用户
                    // 拿返回键或输入法自带的收起键收掉键盘后吃掉第一下——那时输入框仍持焦，
                    // ime 要等收起动画走完才归零，遮罩据此以为键盘还在，执行的 clearFocus/hide
                    // 全是空操作，得再点一下才管用
                    .pointerInput(Unit) {
                        detectTapGestures { dismissComposer() }
                    },
            )
        }
        // 输入面板：显隐同样走 AnimatedVisibility，收起时往下滑出屏幕，不是当帧从组合里消失。
        // align 必须挂在 AnimatedVisibility 自己身上：它是 BoxScope 的 ParentDataModifier，
        // 只有直接子节点才会被 Box 的测量策略读取；写进内容 lambda 里会被 AnimatedVisibility
        // 内部的测量策略忽略，面板静默掉到屏幕左上角，底部槽空出一大片
        AnimatedVisibility(
            visible = composerVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(
                animationSpec = tween(durationMillis = ComposerSlideMillis, easing = FastOutSlowInEasing),
            ) { it } + fadeIn(animationSpec = tween(durationMillis = ComposerSlideMillis)),
            exit = slideOutVertically(
                animationSpec = tween(durationMillis = ComposerSlideMillis, easing = FastOutSlowInEasing),
            ) { it } + fadeOut(animationSpec = tween(durationMillis = ComposerSlideMillis)),
        ) {
            val editingPost = editTarget
            ReplyComposer(
                value = if (editingPost != null) editText else drafts[replyTarget?.pid] ?: TextFieldValue(""),
                onValueChange = { next ->
                    if (editingPost != null) editText = next else drafts[replyTarget?.pid] = next
                },
                target = if (editingPost != null) null else replyTarget,
                editing = editingPost != null,
                sending = replyState is ThreadDetailViewModel.ReplyState.Sending ||
                    editForm is ThreadDetailViewModel.EditFormState.Loading,
                smileyPacks = smileyPacks,
                recentCodes = recentCodes,
                onSmileyUsed = { code -> scope.launch { container.smileyStore.addRecent(code) } },
                uploadCredentials = uploadCredentials,
                onUploadImage = { file ->
                    val (uid, hash) = uploadCredentials ?: return@ReplyComposer null
                    container.repository.uploadReplyImage(file, uid, hash)
                },
                atUsers = atUsers.orEmpty(),
                onOpenAtPicker = { atPickerVisible = true },
                focusRequest = composerFocusRequest,
                onSend = {
                    val current = editTarget
                    if (current != null) {
                        if (editText.text.isNotBlank()) viewModel.submitEdit(editText.text)
                    } else {
                        val draft = drafts[replyTarget?.pid]
                        if (requireLogin(LOGIN_HINT_REPLY) && draft != null && draft.text.isNotBlank()) {
                            sendingDraftKey = replyTarget?.pid
                            viewModel.reply(draft.text)
                        }
                    }
                },
                onDismiss = dismissComposer,
            )
        }
    }

    // 「@朋友」选择页：整页盖在回复面板之上，返回键只收这一层，别把回复面板一起关掉
    BackHandler(enabled = atPickerVisible) { atPickerVisible = false }
    if (atPickerVisible) {
        AtUserPickerPage(
            onDismiss = {
                atPickerVisible = false
                // 取消也要把焦点还给正文：选择页的搜索框抢过焦点，不还的话键盘直接收起
                composerFocusRequest++
            },
            onPick = { name ->
                atPickerVisible = false
                val key = replyTarget?.pid
                drafts[key] = (drafts[key] ?: TextFieldValue("")).insertAtSelection("@$name ")
                viewModel.noteAtUsers(listOf(name))
                composerFocusRequest++
            },
        )
    }

    preview?.let { (urls, index) ->
        ImagePreview(urls = urls, initialIndex = index, onDismiss = { preview = null })
    }

    videoUrl?.let { url ->
        VideoPlayerScreen(url = url, onBack = { videoUrl = null })
    }

    if (rewardOpen) {
        RewardDialog(
            onDismiss = { rewardOpen = false },
            onSubmit = { amount, message ->
                rewardOpen = false
                viewModel.reward(null, amount, message)
            },
        )
    }

    reportTarget?.let { target ->
        ReportDialog(
            isThread = target.pid == null,
            onDismiss = { reportTarget = null },
            onSubmit = { reason, message ->
                reportTarget = null
                viewModel.report(target.pid, reason, message)
            },
        )
    }

    deleteTarget?.let { target ->
        MtfConfirmDialog(
            title = "删除回复",
            message = "确定要删除这条回复吗？删除后无法恢复。",
            confirmText = "确认删除",
            cancelText = "取消",
            onDismissRequest = { deleteTarget = null },
            onConfirm = {
                deleteTarget = null
                viewModel.deleteReply(target.pid)
            },
        )
    }

    likeUsers?.let { users ->
        MtfContentDialog(
            title = "点赞用户 (${users.size})",
            onDismissRequest = { viewModel.clearLikeUsers() },
        ) {
            if (users.isEmpty()) {
                Text(
                    "暂无点赞用户",
                    color = colors.textHint,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(users, key = { it.uid }) { user ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    viewModel.clearLikeUsers()
                                    onUserClick(user.uid)
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                        ) {
                            AsyncImage(
                                model = smallImageRequest(user.avatarUrl),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(colors.quoteBackground)
                                    .dimImageInDark(),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                user.name,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp),
                                color = bodyTextColor(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }

    actionTarget?.let { target ->
        val isOwn = myUid.isNotBlank() && myUid == target.authorUid
        MtfContentDialog(
            title = "${target.authorName.ifBlank { "匿名" }} · 评论操作",
            onDismissRequest = { actionTarget = null },
        ) {
            MenuAction("回复") {
                actionTarget = null
                if (requireLogin(LOGIN_HINT_REPLY)) {
                    viewModel.setReplyTarget(target)
                    composerVisible = true
                }
            }
            MenuAction("复制") {
                actionTarget = null
                val text = plainTextOf(target.contentHtml)
                if (text.isBlank()) {
                    todo("无可复制内容")
                } else {
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as? android.content.ClipboardManager
                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("回复内容", text))
                    todo("已复制回复内容")
                }
            }
            if (isOwn) {
                MenuAction("编辑") {
                    actionTarget = null
                    viewModel.beginEdit(target)
                }
                MenuAction("删除") {
                    actionTarget = null
                    deleteTarget = target
                }
            } else {
                MenuAction("举报") {
                    actionTarget = null
                    if (requireLogin(LOGIN_HINT_REPORT)) reportTarget = ReportTarget(target.pid)
                }
            }
        }
    }
}

/** 未登录点击互动入口时的提示文案。 */
private const val LOGIN_HINT_REPLY = "你不登录咋回复???"
private const val LOGIN_HINT_LIKE = "嗯?暂无点赞资格"
private const val LOGIN_HINT_FAVORITE = "我说收藏成功了你信吗?"
private const val LOGIN_HINT_REPORT = "666你是有操作的"
private const val LOGIN_HINT_REWARD = "tui~啥也不是!"

/** 未登录提示的停留时长。 */
private const val LOGIN_HINT_DURATION_MS = 2000L

/** 举报弹窗目标：pid 为 null 时举报本帖。 */
private data class ReportTarget(val pid: String?)

/** 举报理由选项。 */
private val REPORT_REASONS = listOf("广告垃圾", "恶意灌水", "违规内容", "重复发帖", "其他")

/**
 * 举报确认弹窗：先选理由（可补充说明）再提交，不做“点一下就直接举报”。
 */
@Composable
private fun ReportDialog(
    isThread: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (reason: String, message: String) -> Unit,
) {
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary
    var reason by remember { mutableStateOf("其他") }
    var message by remember { mutableStateOf("") }

    MtfContentDialog(
        title = if (isThread) "举报本帖" else "举报该评论",
        subtitle = "请选择举报原因并简要说明，共同维护良好社区氛围",
        onDismissRequest = onDismiss,
    ) {
        Column {
            REPORT_REASONS.forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { reason = option }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                ) {
                    RadioButton(selected = option == reason, onClick = { reason = option })
                    Spacer(Modifier.width(8.dp))
                    Text(
                        option,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp),
                        color = bodyTextColor(),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            BasicTextField(
                value = message,
                onValueChange = { message = it },
                textStyle = TextStyle(color = bodyTextColor(), fontSize = 14.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.quoteBackground)
                    .padding(12.dp),
                decorationBox = { inner ->
                    if (message.isEmpty()) {
                        Text("补充说明（可不填）", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = colors.textHint)
                    }
                    inner()
                },
            )
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.quoteBackground)
                        .clickable(onClick = onDismiss),
                ) {
                    Text(
                        "取消",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        fontWeight = FontWeight.Medium,
                        color = bodyTextColor(),
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .then(
                            if (isDark) {
                                Modifier
                                    .border(1.dp, primary.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                    .background(primary.copy(alpha = 0.22f))
                            } else {
                                Modifier.background(primary)
                            },
                        )
                        .clickable { onSubmit(reason, message) },
                ) {
                    Text(
                        "提交举报",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) primary else MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

/** 打赏弹窗：金额档位 + 可选留言。 */
@Composable
private fun RewardDialog(
    onDismiss: () -> Unit,
    onSubmit: (Int, String?) -> Unit,
) {
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary
    val amounts = listOf(1, 5, 10, 50, 100)
    var amount by remember { mutableStateOf(amounts.first()) }
    var message by remember { mutableStateOf("") }

    MtfContentDialog(
        title = "打赏作者",
        subtitle = "感谢作者的优质分享，支持创作",
        onDismissRequest = onDismiss,
    ) {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                amounts.forEach { value ->
                    val active = value == amount
                    Text(
                        value.toString(),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = if (active) (if (isDark) primary else MaterialTheme.colorScheme.onPrimary) else bodyTextColor(),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .then(
                                if (active && isDark) {
                                    Modifier
                                        .border(1.dp, primary.copy(alpha = 0.5f), RoundedCornerShape(50))
                                        .background(primary.copy(alpha = 0.22f))
                                } else {
                                    Modifier.background(if (active) primary else colors.quoteBackground)
                                },
                            )
                            .clickable { amount = value }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            BasicTextField(
                value = message,
                onValueChange = { message = it },
                textStyle = TextStyle(color = bodyTextColor(), fontSize = 14.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.quoteBackground)
                    .padding(12.dp),
                decorationBox = { inner ->
                    if (message.isEmpty()) {
                        Text("留言（可不填）", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = colors.textHint)
                    }
                    inner()
                },
            )
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.quoteBackground)
                        .clickable(onClick = onDismiss),
                ) {
                    Text(
                        "取消",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        fontWeight = FontWeight.Medium,
                        color = bodyTextColor(),
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .then(
                            if (isDark) {
                                Modifier
                                    .border(1.dp, primary.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                    .background(primary.copy(alpha = 0.22f))
                            } else {
                                Modifier.background(primary)
                            },
                        )
                        .clickable { onSubmit(amount, message.ifBlank { null }) },
                ) {
                    Text(
                        "确认打赏",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) primary else MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

/** 长按菜单里的一行操作。 */
@Composable
private fun MenuAction(label: String, onClick: () -> Unit) {
    val colors = LocalMtfColors.current
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
        color = bodyTextColor(),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

/**
 * 头像/昵称可点：有 uid 时进入该用户主页；无 uid（游客/匿名）不响应。
 */
private fun Modifier.userClick(
    uid: String?,
    onUserClick: (String) -> Unit,
): Modifier =
    if (uid.isNullOrBlank()) this else clickable { onUserClick(uid) }

private fun shareThread(context: Context, subject: String?, tid: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "$subject https://bbs.binmt.cc/thread-$tid-1-1.html")
    }
    context.startActivity(Intent.createChooser(intent, "分享"))
}

/**
 * 顶栏：与首页顶栏同规格（状态栏避让 + 上下 10dp）的单行作者栏。
 * 返回 → 头像/昵称（昵称下方左对齐显示时间/属地）→ 关注 → 更多；作者帖未就绪时占位
 * 显示，行高与就绪后一致，否则内容区会随 Scaffold 顶部内边距变化被推着下移。
 * 举报/赞赏/下载都收在「更多」菜单里（纯文字项），顶栏不再单独摆图标。
 */
@Composable
internal fun ThreadDetailTopBar(
    post: Post?,
    following: Boolean,
    followEnabled: Boolean,
    /** 楼主是当前登录用户：给「编辑本帖」入口。 */
    canEditThread: Boolean = false,
    showMore: Boolean,
    downloadBusy: Boolean,
    onBack: () -> Unit,
    onFollow: () -> Unit,
    onReport: () -> Unit,
    onReward: () -> Unit,
    onDownload: () -> Unit,
    onEditThread: () -> Unit = {},
    onUserClick: (String) -> Unit,
    onDoubleTap: () -> Unit,
) {
    val colors = LocalMtfColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.topBar)
            // 子控件（返回/头像/关注等）自己消费手势，故双击只会命中顶栏空白处
            .onDoubleTapToTop(onDoubleTap)
            .statusBarsPadding()
            .padding(
                start = 12.dp,
                end = 12.dp,
                top = TopBarVerticalPadding,
                bottom = TopBarVerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(com.aazhou.mtforum.R.drawable.ic_back),
            contentDescription = "返回",
            tint = bodyTextColor(),
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .padding(7.dp),
        )
        Spacer(Modifier.width(6.dp))
        AsyncImage(
            model = smallImageRequest(post?.avatarUrl),
            contentDescription = null,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.quoteBackground)
                .dimImageInDark()
                .userClick(post?.authorUid, onUserClick),
        )
        Spacer(Modifier.width(8.dp))
        // 昵称一行、时间/属地一行，均左对齐；昵称可被压缩，长昵称优先让位
        Column(Modifier.weight(1f)) {
            Text(
                post?.authorName?.ifBlank { "匿名" }.orEmpty(),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                fontWeight = FontWeight.Bold,
                color = bodyTextColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.userClick(post?.authorUid, onUserClick),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 1.dp)) {
                Text(
                    post?.timeText.orEmpty(),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = colors.textSecondary,
                    maxLines = 1,
                )
                if (!post?.location.isNullOrBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        post?.location.orEmpty(),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = colors.textHint,
                        maxLines = 1,
                    )
                }
            }
        }
        if (followEnabled) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (following) "已关注" else "关注",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                fontWeight = FontWeight.Bold,
                color = if (following) colors.textSecondary else accentTextColor(),
                modifier = Modifier
                    .height(26.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (following) colors.quoteBackground else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .clickable(onClick = onFollow)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
        // 举报/赞赏只针对楼主帖，与下载一同收进「更多」菜单；下载期间菜单保持展开，
        // 下载项显示「下载中…」并禁用，保存结束（downloadBusy 回落）自动收起
        if (showMore) {
            // 与左侧「关注」胶囊留出让位间距，免得太贴
            Spacer(Modifier.width(8.dp))
            MoreMenu(
                downloadBusy = downloadBusy,
                canEditThread = canEditThread,
                onReport = onReport,
                onReward = onReward,
                onDownload = onDownload,
                onEditThread = onEditThread,
            )
        }
    }
}

/** 「更多」竖展菜单：编辑 / 打赏 / 举报 / 下载，精致圆角与图标排版。 */
@Composable
private fun MoreMenu(
    downloadBusy: Boolean,
    canEditThread: Boolean = false,
    onReport: () -> Unit,
    onReward: () -> Unit,
    onDownload: () -> Unit,
    onEditThread: () -> Unit = {},
) {
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current
    var open by remember { mutableStateOf(false) }
    var wasBusy by remember { mutableStateOf(false) }
    LaunchedEffect(downloadBusy) {
        if (wasBusy && !downloadBusy) open = false
        wasBusy = downloadBusy
    }
    Box {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = { open = true }),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(com.aazhou.mtforum.R.drawable.ic_more),
                contentDescription = "更多",
                tint = bodyTextColor(),
                modifier = Modifier.size(22.dp),
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(14.dp),
            containerColor = colors.surface,
            shadowElevation = 6.dp,
            border = if (isDark) androidx.compose.foundation.BorderStroke(0.5.dp, colors.divider) else null,
            modifier = Modifier.width(118.dp),
        ) {
            val itemStyle = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            val itemPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
            if (canEditThread) {
                DropdownMenuItem(
                    text = { Text("编辑本帖", style = itemStyle, color = bodyTextColor()) },
                    contentPadding = itemPadding,
                    leadingIcon = {
                        Icon(
                            painter = painterResource(com.aazhou.mtforum.R.drawable.ic_post_edit),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = {
                        open = false
                        onEditThread()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("打赏", style = itemStyle, color = bodyTextColor()) },
                contentPadding = itemPadding,
                leadingIcon = {
                    Icon(
                        painter = painterResource(com.aazhou.mtforum.R.drawable.ic_reward_gift),
                        contentDescription = null,
                        tint = colors.gold,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = {
                    open = false
                    onReward()
                },
            )
            DropdownMenuItem(
                text = { Text("举报", style = itemStyle, color = bodyTextColor()) },
                contentPadding = itemPadding,
                leadingIcon = {
                    Icon(
                        painter = painterResource(com.aazhou.mtforum.R.drawable.ic_report_shield),
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = {
                    open = false
                    onReport()
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        if (downloadBusy) "下载中…" else "下载",
                        style = itemStyle,
                        color = bodyTextColor(),
                    )
                },
                contentPadding = itemPadding,
                leadingIcon = {
                    if (downloadBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Icon(
                            painter = painterResource(com.aazhou.mtforum.R.drawable.ic_download),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
                enabled = !downloadBusy,
                onClick = {
                    onDownload()
                },
            )
        }
    }
}

/**
 * 正文头：标题 → 细线上方一行（编辑标记靠左 / 版块 chip 靠右）→ 细线 → 正文 → 评论标题栏。
 * 作者信息栏已整体移入页面顶栏 ThreadDetailTopBar，此处不再重复。
 */
@Composable
internal fun DetailHeader(
    post: Post,
    subject: String?,
    forumName: String?,
    onImageClick: (List<String>, Int) -> Unit,
    onVideoClick: ((String) -> Unit)? = null,
    pageImages: List<String>,
    onUnlock: () -> Unit,
    onLinkClick: ((url: String) -> Unit)? = null,
) {
    val colors = LocalMtfColors.current
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(
            text = subject.orEmpty(),
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp, lineHeight = 29.sp),
            fontWeight = FontWeight.Bold,
            color = bodyTextColor(),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )
        // 编辑标记与版块 chip 同一行、同处细线上方：编辑标记靠左，chip 靠右；两者都无时不占行
        if (!post.editedText.isNullOrBlank() || !forumName.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!post.editedText.isNullOrBlank()) {
                    Text(
                        text = post.editedText,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        fontStyle = FontStyle.Italic,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (!forumName.isNullOrBlank()) {
                    Text(
                        text = forumName,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .border(
                                0.1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.69f),
                                RoundedCornerShape(50),
                            )
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }
        HorizontalDivider(
            color = colors.divider,
            thickness = 0.5.dp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(2.dp))
        PostContent(
            html = post.contentHtml,
            onImageClick = onImageClick,
            onVideoClick = onVideoClick,
            pageImages = pageImages,
            bodyStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 24.sp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            onLinkClick = onLinkClick,
        )
        if (post.hasHiddenContent) {
            HiddenContentCard(onUnlock = onUnlock)
        }
    }
}

/** 「回复可见」锁态卡片：锁图标圆底 + 标题/提示 + 「去回复」胶囊。 */
@Composable
private fun HiddenContentCard(onUnlock: () -> Unit) {
    val colors = LocalMtfColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.divider, RoundedCornerShape(12.dp))
            .clickable(onClick = onUnlock)
            .padding(12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        ) {
            Icon(
                painterResource(com.aazhou.mtforum.R.drawable.ic_lock_outline),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "包含隐藏内容",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                fontWeight = FontWeight.Bold,
                color = bodyTextColor(),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "回复本帖后自动刷新解锁",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                color = colors.textSecondary,
            )
        }
        Spacer(Modifier.width(8.dp))
        // 与标题同款文本：深色下原先的实心主色胶囊过亮，改成主题色描边、无底色
        Text(
            "去回复",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
            fontWeight = FontWeight.Bold,
            color = bodyTextColor(),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .border(
                    0.1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.69f),
                    RoundedCornerShape(50),
                )
                .clickable(onClick = onUnlock)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** 「全部评论 (N) [已折叠 N 条 / 收起已折叠]  楼主 | 正序 | 倒序」。 */
@Composable
internal fun CommentHeader(
    total: Int?,
    folded: Int,
    foldExpanded: Boolean,
    opOnly: Boolean,
    orderType: Int,
    sortable: Boolean = true,
    onToggleFold: () -> Unit,
    onOpOnly: (Boolean) -> Unit,
    onOrder: (Int) -> Unit,
) {
    val colors = LocalMtfColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧一组占满剩余空间：窄屏时标题先省略，计数、折叠胶囊与右侧控件不动
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "全部评论",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.5.sp),
                fontWeight = FontWeight.Bold,
                color = bodyTextColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (total != null) {
                Text(
                    "($total)",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = colors.textSecondary,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            if (folded > 0) {
                Text(
                    if (foldExpanded) "收起已折叠" else "已折叠 $folded 条",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = colors.textSecondary,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(colors.quoteBackground)
                        .clickable(onClick = onToggleFold)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }
        if (sortable) {
            SegmentedControl(
                selected = when {
                    opOnly -> 0
                    orderType == 1 -> 2
                    else -> 1
                },
                onSelect = { index ->
                    when (index) {
                        0 -> onOpOnly(true)
                        1 -> onOrder(0)
                        else -> onOrder(1)
                    }
                },
            )
        }
    }
}

/** 三档分段控件：浅灰底轨，选中项白底。 */
@Composable
private fun SegmentedControl(selected: Int, onSelect: (Int) -> Unit) {
    val colors = LocalMtfColors.current
    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(50))
            .background(colors.quoteBackground)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf("楼主", "正序", "倒序").forEachIndexed { index, label ->
            val active = index == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(if (active) colors.surface else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 9.dp),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) bodyTextColor() else colors.textSecondary,
                )
            }
        }
    }
}

/** 顶层回复：头像 30dp、楼层号 11sp、昵称 14sp、引用卡、正文 15sp、底栏时间与操作。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReplyBlock(
    reply: Post,
    onReply: () -> Unit,
    onImageClick: (List<String>, Int) -> Unit,
    onVideoClick: ((String) -> Unit)? = null,
    onUserClick: (String) -> Unit,
    pageImages: List<String>,
    onLongPress: (Post) -> Unit,
    onThreadClick: (String) -> Unit,
    /** 定位闪烁：从消息提醒跳到本条时柔和闪两下。 */
    highlight: Boolean = false,
) {
    val colors = LocalMtfColors.current
    val context = LocalContext.current
    val siteHost = context.container().httpClient.host
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = { onLongPress(reply) })
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            AsyncImage(
                model = smallImageRequest(reply.avatarUrl),
                contentDescription = null,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .dimImageInDark()
                    .userClick(reply.authorUid, onUserClick),
            )
            Spacer(Modifier.width(9.dp))
            // 闪烁只覆盖这条消息的内容（昵称 + 正文），不含头像与底栏
            Column(Modifier.weight(1f).flashHighlight(highlight)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!reply.floorLabel.isNullOrBlank()) {
                        Text(
                            reply.floorLabel,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = colors.textSecondary,
                            modifier = Modifier.padding(end = 2.dp),
                        )
                    }
                    Text(
                        reply.authorName.ifBlank { "匿名" },
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                        // 昵称比正文低一档，但深色下已由 bodyTextColor 统一压到 69%,
                        // 这里不再叠 alpha，否则会二次相乘到 55% 看不清
                        color = bodyTextColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.userClick(reply.authorUid, onUserClick),
                    )
                    if (reply.isOp) {
                        Spacer(Modifier.width(5.dp))
                        AuthorBadge("楼主")
                    }
                    if (!reply.editedText.isNullOrBlank()) {
                        Spacer(Modifier.width(1.dp))
                        Text(
                            reply.editedText,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(start = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                PostContent(
                    html = reply.contentHtml,
                    quote = reply.quote,
                    onImageClick = onImageClick,
                    onVideoClick = onVideoClick,
                    pageImages = pageImages,
                    bodyStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
                    modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { onLongPress(reply) }),
                    onLinkClick = { url -> openForumLink(context, url, siteHost, onThreadClick) },
                    selectable = false,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(45.dp))
            TimeLine(reply)
            Spacer(Modifier.weight(1f))
            Icon(
                painterResource(com.aazhou.mtforum.R.drawable.ic_reply),
                contentDescription = "回复",
                tint = colors.iconSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onReply),
            )
        }
    }
}

/** 「楼主」徽章：淡主色底、主色文字。 */
@Composable
private fun AuthorBadge(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
        fontWeight = FontWeight.Bold,
        color = accentTextColor(),
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

@Composable
private fun TimeLine(post: Post) {
    val colors = LocalMtfColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            post.timeText.orEmpty(),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = colors.textSecondary,
        )
        if (!post.location.isNullOrBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                post.location,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = colors.textHint,
            )
        }
    }
}

/** 底部栏：两颗毛玻璃胶囊——左侧「写评论」入口，右侧 评论 / 点赞 / 收藏 / 分享。 */
@Composable
private fun ReplyBar(
    backdrop: LayerBackdrop,
    replyCount: Int,
    liked: Boolean,
    likeCount: Int,
    favorited: Boolean,
    favoriteCount: Int,
    onClick: () -> Unit,
    onComments: () -> Unit,
    onLike: () -> Unit,
    onLikeLongPress: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
) {
    val colors = LocalMtfColors.current
    // 玻璃面色与全局玻璃底栏一致：浅色薄白、深色薄黑；深浅同样看 LocalIsDarkTheme，
    // 否则应用内切主题后会与全局底栏的膜色不一致
    val glassColor =
        if (LocalIsDarkTheme.current) Color(0xFF121212).copy(0.28f) else Color(0xFFFAFAFA).copy(0.28f)
    val glassShape = AbsoluteRoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp)
            .padding(top = ReplyBarTopPadding, bottom = BottomBarBottomMargin),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 左胶囊：只作入口，点它弹出输入面板
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(ReplyBarCapsuleHeight)
                    .clickable(onClick = onClick)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { glassShape },
                        effects = {
                            vibrancy()
                            blur(16.dp.toPx())
                            lens(12.dp.toPx(), 16.dp.toPx())
                        },
                        onDrawSurface = { drawRect(glassColor) },
                    )
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(com.aazhou.mtforum.R.drawable.ic_comment_pen),
                    contentDescription = null,
                    tint = bodyTextColor(),
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "写评论",
                    style = MaterialTheme.typography.bodyMedium,
                    color = bodyTextColor(),
                )
            }
            Spacer(Modifier.width(8.dp))
            // 右胶囊：评论 / 点赞 / 收藏 / 分享，图标在上、计数或文字在下；与左胶囊各占一半宽度
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(ReplyBarCapsuleHeight)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { glassShape },
                        effects = {
                            vibrancy()
                            blur(16.dp.toPx())
                            lens(12.dp.toPx(), 16.dp.toPx())
                        },
                        onDrawSurface = { drawRect(glassColor) },
                    )
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarAction(onClick = onComments, modifier = Modifier.weight(1f)) {
                    BarCount(
                        icon = {
                            Icon(
                                painterResource(com.aazhou.mtforum.R.drawable.ic_reply),
                                contentDescription = "查看评论",
                                tint = bodyTextColor(),
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        count = replyCount,
                        color = colors.textSecondary,
                    )
                }
                BarAction(onClick = onLike, onLongClick = onLikeLongPress, modifier = Modifier.weight(1f)) {
                    BarCount(
                        icon = {
                            Icon(
                                painterResource(com.aazhou.mtforum.R.drawable.ic_like_thumb),
                                contentDescription = "点赞",
                                tint = if (liked) MaterialTheme.colorScheme.primary else bodyTextColor(),
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        count = likeCount,
                        color = if (liked) MaterialTheme.colorScheme.primary else colors.textSecondary,
                    )
                }
                BarAction(onClick = onFavorite, modifier = Modifier.weight(1f)) {
                    BarCount(
                        icon = {
                            Icon(
                                painterResource(com.aazhou.mtforum.R.drawable.ic_favorite_outline),
                                contentDescription = "收藏",
                                tint = if (favorited) MaterialTheme.colorScheme.primary else bodyTextColor(),
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        count = favoriteCount,
                        color = if (favorited) MaterialTheme.colorScheme.primary else colors.textSecondary,
                    )
                }
                BarAction(onClick = onShare, modifier = Modifier.weight(1f)) {
                    BarCount(
                        icon = {
                            Icon(
                                painterResource(com.aazhou.mtforum.R.drawable.ic_share),
                                contentDescription = "分享",
                                tint = bodyTextColor(),
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        caption = "分享",
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

/** 胶囊内的单个操作：图标在上、数字在下；数字为 null 时不占位。 */
@Composable
private fun BarCount(
    icon: @Composable () -> Unit,
    count: Int?,
    color: Color,
) {
    BarCount(icon = icon, caption = count?.toString(), color = color)
}

/** 图标在上、固定文字在下（如「分享」）；字号与数字一致。 */
@Composable
private fun BarCount(
    icon: @Composable () -> Unit,
    caption: String?,
    color: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        icon()
        if (caption != null) {
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = color,
            )
        }
    }
}

/** 底部栏的单个图标操作；带长按时可触发（如长按点赞看点赞人）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BarAction(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        icon()
    }
}

/** 闪烁圆角：与消息卡片的圆角区分开，只看得出是这一条的内容区。 */
private val FLASH_CORNER = 8.dp

/**
 * 定位提醒楼层时的柔和闪烁：底色用最底层的页面灰，淡入淡出两次。
 *
 * 灰色与页面底色接近，透明度压低后会淡到看不出来，故峰值给足（淡入淡出本身
 * 已经提供了「柔和」）；画在内容之下、带圆角，只覆盖内容列。
 */
@Composable
private fun Modifier.flashHighlight(active: Boolean): Modifier {
    if (!active) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        repeat(2) {
            progress.animateTo(1f, tween(durationMillis = 240, easing = FastOutSlowInEasing))
            progress.animateTo(0f, tween(durationMillis = 480, easing = FastOutSlowInEasing))
        }
    }
    val shade = LocalMtfColors.current.background
    return drawBehind {
        // 圆角的 px 值在 DrawScope 里换算，才能拿到当前密度
        drawRoundRect(
            color = shade,
            cornerRadius = CornerRadius(FLASH_CORNER.toPx()),
            alpha = progress.value,
        )
    }
}

/** 输入面板遮罩淡入淡出的时长：再短就显得硬闪，再长会拖沓得挡住页面操作。 */
private val ScrimFadeMillis = 350

/**
 * 输入面板上滑进场、下滑退场的时长。收起走下滑（不是当帧消失）时不能拖太长，
 * 拖久了像页面卡住；比遮罩淡出短一截，视觉上先走面板、再散遮罩。
 */
private val ComposerSlideMillis = 250
