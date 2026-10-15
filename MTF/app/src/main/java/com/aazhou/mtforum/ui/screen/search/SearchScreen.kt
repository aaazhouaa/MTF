package com.aazhou.mtforum.ui.screen.search

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aazhou.mtforum.R
import com.aazhou.mtforum.data.model.AtUser
import com.aazhou.mtforum.data.model.Forum
import com.aazhou.mtforum.data.model.SearchTab
import com.aazhou.mtforum.image.smallImageRequest
import com.aazhou.mtforum.ui.common.AppToast
import com.aazhou.mtforum.ui.common.EmptyBox
import com.aazhou.mtforum.ui.common.ErrorBox
import com.aazhou.mtforum.ui.common.ImagePreview
import com.aazhou.mtforum.ui.common.LoadingBox
import com.aazhou.mtforum.ui.common.SwipeTabIndicator
import com.aazhou.mtforum.ui.common.ThreadCard
import com.aazhou.mtforum.ui.common.UiState
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.common.bottomBarContentPadding
import com.aazhou.mtforum.ui.common.onDoubleTapToTop
import com.aazhou.mtforum.ui.common.scrollToTop
import com.aazhou.mtforum.ui.theme.LocalIsDarkTheme
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.accentTextColor
import com.aazhou.mtforum.ui.theme.bodyTextColor
import com.aazhou.mtforum.ui.theme.dimImageInDark
import kotlinx.coroutines.launch

private const val PREFS_SEARCH = "mtf_search_prefs"
private const val KEY_HISTORY = "history_keywords"

private val HOT_SEARCH_WORDS = listOf(
    "MT管理器", "逆向破解", "Magisk", "Xposed", "Dex修复",
    "汉化教程", "签名校验", "自动化脚本", "Arsc", "Activity",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit = {},
    onThreadClick: (tid: String) -> Unit,
    onUserClick: (uid: String) -> Unit,
    onForumClick: (fid: String, name: String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current
    val viewModel: SearchViewModel = appViewModel { SearchViewModel(it.repository, it.contentCache) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadingMore by viewModel.loadingMore.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val forumState by viewModel.forumState.collectAsStateWithLifecycle()
    val userState by viewModel.userState.collectAsStateWithLifecycle()

    // 从结果页返回时本页会重组、输入框状态重建：不指定选区的话光标回到开头，
    // 光标位置与键盘聚焦看起来就「跑到前面」了，这里把光标落在已有文字末尾
    var input by remember {
        mutableStateOf(
            TextFieldValue(viewModel.keyword, TextRange(viewModel.keyword.length)),
        )
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // 进入页面即聚焦搜索框并弹起输入法
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    // 搜索历史保存
    var historyList by remember {
        mutableStateOf(loadSearchHistory(context))
    }

    val performSearch: (String) -> Unit = { query ->
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) {
            input = TextFieldValue(trimmed, selection = TextRange(trimmed.length))
            keyboardController?.hide()
            viewModel.submit(trimmed)
            val updated = (listOf(trimmed) + historyList.filterNot { it.equals(trimmed, ignoreCase = true) }).take(12)
            historyList = updated
            saveSearchHistory(context, updated)
        }
    }

    // 配图预览
    var preview by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    val openImage: (List<String>, Int) -> Unit = { urls, index ->
        if (urls.isNotEmpty()) preview = urls to index
    }

    val listStates = SearchTab.values().map { rememberLazyListState() }
    val searchTabs = SearchTab.values()
    val pagerState = rememberPagerState(initialPage = 0) { searchTabs.size }
    val scope = rememberCoroutineScope()

    // 滑动落定即切换分类：与点 tab 走同一条路，避免两处各提交一次搜索
    LaunchedEffect(pagerState.currentPage) {
        viewModel.selectTab(searchTabs[pagerState.currentPage])
    }

    LaunchedEffect(actionMessage) {
        val message = actionMessage ?: return@LaunchedEffect
        AppToast.show(message)
        viewModel.consumeActionMessage()
    }

    Scaffold(
        containerColor = colors.background,
        contentColor = bodyTextColor(),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.topBar)
                    .statusBarsPadding(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp, end = 14.dp, top = 8.dp, bottom = 10.dp),
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(38.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = "返回",
                            tint = bodyTextColor(),
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    // 搜索胶囊栏
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.quoteBackground)
                            .then(
                                if (isDark) Modifier.border(0.5.dp, colors.divider, RoundedCornerShape(20.dp))
                                else Modifier
                            )
                            .padding(horizontal = 12.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_search_new),
                            contentDescription = null,
                            tint = colors.textHint,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        BasicTextField(
                            value = input,
                            onValueChange = { input = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = bodyTextColor(),
                                fontSize = 14.sp,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { performSearch(input.text) }),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (input.text.isEmpty()) {
                                        Text(
                                            "输入关键词搜索帖子...",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                                            color = colors.textHint,
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                        )

                        AnimatedVisibility(
                            visible = input.text.isNotEmpty(),
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .clickable { input = TextFieldValue("") },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close),
                                    contentDescription = "清空",
                                    tint = colors.textHint,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    // 搜索按钮
                    val primary = MaterialTheme.colorScheme.primary
                    val dark = LocalIsDarkTheme.current
                    // 主题预设的夜间色偏亮（薄荷青/珊瑚橙等），白字对比不足；
                    // 按背景亮度取前景色，浅色模式仍是白字，外观不变
                    val foreground =
                        if (primary.luminance() > 0.5f) Color(0xFF111827) else Color.White
                    // 深色下按钮整体压到 50% 不透明度，与深色背景更融；浅色下背景压到 69%
                    val buttonForeground = if (dark) foreground.copy(alpha = 0.5f) else foreground
                    val buttonBackground =
                        if (dark) primary.copy(alpha = 0.5f) else primary.copy(alpha = 0.69f)
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(buttonBackground)
                            .clickable { performSearch(input.text) }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "搜索",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = buttonForeground,
                        )
                    }
                }

                val accent = accentTextColor()
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = colors.topBar,
                    contentColor = accent,
                    indicator = { tabPositions ->
                        SwipeTabIndicator(pagerState, tabPositions, accent)
                    },
                ) {
                    searchTabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = {
                                Text(
                                    tab.label,
                                    fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp,
                                    color = if (pagerState.currentPage == index) accent else colors.textSecondary,
                                )
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .onDoubleTapToTop {
                    scope.launch { listStates[pagerState.currentPage].scrollToTop() }
                },
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                // 两侧各少预组一页：默认会把左右邻居也建出来，等于切一次 tab 多打两个请求
                beyondViewportPageCount = 0,
            ) { page ->
                when (searchTabs[page]) {
                    SearchTab.Thread -> {
                        when (val current = state) {
                    null -> {
                        // 未搜索时的精美发现视图：搜索历史 + 推荐搜索
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            if (historyList.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_recent),
                                            contentDescription = null,
                                            tint = colors.textSecondary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "搜索历史",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = bodyTextColor(),
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                historyList = emptyList()
                                                clearSearchHistory(context)
                                            }
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            "清空",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                            color = colors.textHint,
                                        )
                                    }
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 20.dp),
                                ) {
                                    historyList.forEach { tag ->
                                        SearchChip(
                                            text = tag,
                                            onClick = { performSearch(tag) },
                                        )
                                    }
                                }
                            }

                            // 热门推荐
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_posts),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "热门搜索",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = bodyTextColor(),
                                )
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                            ) {
                                HOT_SEARCH_WORDS.forEach { tag ->
                                    SearchChip(
                                        text = tag,
                                        isHighlight = tag == "MT管理器" || tag == "逆向破解",
                                        onClick = { performSearch(tag) },
                                    )
                                }
                            }
                        }
                    }
                    is UiState.Loading -> LoadingBox()
                    is UiState.Error -> ErrorBox(current.message, { viewModel.submit(input.text) })
                    is UiState.Success -> {
                        val result = current.data
                        if (result.threads.isEmpty()) {
                            EmptyBox("没有找到相关主题，换个词试试")
                        } else {
                            val shouldLoadMore by remember {
                                derivedStateOf {
                                    val last = listStates[page].layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                    val total = listStates[page].layoutInfo.totalItemsCount
                                    total > 0 && last >= total - 3
                                }
                            }
                            LaunchedEffect(shouldLoadMore) {
                                if (shouldLoadMore) viewModel.loadMore()
                            }
                            LazyColumn(
                                state = listStates[page],
                                contentPadding = PaddingValues(top = 2.5.dp, bottom = bottomBarContentPadding()),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(result.threads, key = { it.tid }) { thread ->
                                    ThreadCard(
                                        thread = thread,
                                        onClick = { onThreadClick(thread.tid) },
                                        onImageClick = openImage,
                                        onUserClick = onUserClick,
                                    )
                                }
                                if (loadingMore) {
                                    item(key = "loading-more") {
                                        Box(
                                            Modifier.fillMaxWidth().padding(16.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CircularProgressIndicator(Modifier.size(22.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    }
                    }
                    SearchTab.Forum -> {
                        when (val current = forumState) {
                            null -> EmptyBox("输入关键词搜索圈子")
                            is UiState.Loading -> LoadingBox()
                            is UiState.Error -> ErrorBox(current.message, { viewModel.submit(input.text) })
                            is UiState.Success -> {
                                val forums = current.data
                                if (forums.isEmpty()) {
                                    EmptyBox("没有找到相关圈子")
                                } else {
                                    LazyColumn(
                                        state = listStates[page],
                                        contentPadding = PaddingValues(top = 2.5.dp, bottom = bottomBarContentPadding()),
                                        modifier = Modifier.fillMaxSize(),
                                    ) {
                                        items(forums, key = { it.fid }) { forum ->
                                            ForumResultRow(
                                                forum = forum,
                                                onClick = { onForumClick(forum.fid, forum.name) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    SearchTab.User -> {
                        when (val current = userState) {
                            null -> EmptyBox("输入关键词搜索用户")
                            is UiState.Loading -> LoadingBox()
                            is UiState.Error -> ErrorBox(current.message, { viewModel.submit(input.text) })
                            is UiState.Success -> {
                                val users = current.data
                                if (users.isEmpty()) {
                                    EmptyBox("没有找到相关用户")
                                } else {
                                    LazyColumn(
                                        state = listStates[page],
                                        contentPadding = PaddingValues(top = 2.5.dp, bottom = bottomBarContentPadding()),
                                        modifier = Modifier.fillMaxSize(),
                                    ) {
                                        items(users, key = { it.uid }) { user ->
                                            UserResultRow(
                                                user = user,
                                                onClick = { onUserClick(user.uid) },
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
    }

    preview?.let { (urls, index) ->
        ImagePreview(urls = urls, initialIndex = index, onDismiss = { preview = null })
    }
}

@Composable
private fun SearchChip(
    text: String,
    isHighlight: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isHighlight) primary.copy(alpha = if (isDark) 0.18f else 0.10f)
                else colors.surface,
            )
            .then(
                if (isDark && !isHighlight) Modifier.border(0.5.dp, colors.divider, RoundedCornerShape(16.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = if (isHighlight) primary else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun loadSearchHistory(context: Context): List<String> {
    val sp = context.getSharedPreferences(PREFS_SEARCH, Context.MODE_PRIVATE)
    val raw = sp.getString(KEY_HISTORY, "") ?: ""
    if (raw.isBlank()) return emptyList()
    return raw.split("###|||###").filter { it.isNotBlank() }
}

private fun saveSearchHistory(context: Context, list: List<String>) {
    val sp = context.getSharedPreferences(PREFS_SEARCH, Context.MODE_PRIVATE)
    sp.edit().putString(KEY_HISTORY, list.joinToString("###|||###")).apply()
}

private fun clearSearchHistory(context: Context) {
    val sp = context.getSharedPreferences(PREFS_SEARCH, Context.MODE_PRIVATE)
    sp.edit().remove(KEY_HISTORY).apply()
}

/** 圈子搜索结果卡片：与帖子卡片同一套外壳（surface 底 + 12dp 圆角 + 6dp 外边距 + 10dp 内边距）。 */
@Composable
private fun ForumResultRow(
    forum: Forum,
    onClick: () -> Unit,
) {
    val colors = LocalMtfColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.5.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.quoteBackground),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_home),
                    contentDescription = null,
                    tint = colors.iconSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                forum.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = bodyTextColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 用户搜索结果卡片：与帖子卡片同一套外壳（surface 底 + 12dp 圆角 + 6dp 外边距 + 10dp 内边距）。 */
@Composable
private fun UserResultRow(
    user: AtUser,
    onClick: () -> Unit,
) {
    val colors = LocalMtfColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.5.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = smallImageRequest(user.avatarUrl),
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.quoteBackground)
                    .dimImageInDark(),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    user.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    color = bodyTextColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (user.subtitle.isNotBlank()) {
                    Text(
                        user.subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

