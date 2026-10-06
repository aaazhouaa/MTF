package com.aazhou.mtforum.ui.screen.threaddetail

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aazhou.mtforum.data.model.Post
import com.aazhou.mtforum.ui.common.EmptyBox
import com.aazhou.mtforum.ui.common.ErrorBox
import com.aazhou.mtforum.ui.common.ImagePreview
import com.aazhou.mtforum.ui.common.LoadingBox
import com.aazhou.mtforum.ui.common.PostContent
import com.aazhou.mtforum.ui.common.UiState
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.common.isWaterReply
import com.aazhou.mtforum.ui.common.bottomBarContentPadding
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadDetailScreen(
    tid: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalMtfColors.current
    val viewModel: ThreadDetailViewModel = appViewModel { ThreadDetailViewModel(tid, it.repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val replyState by viewModel.replyState.collectAsStateWithLifecycle()
    val collapsed by viewModel.collapsed.collectAsStateWithLifecycle()
    val orderType by viewModel.orderType.collectAsStateWithLifecycle()
    val opOnly by viewModel.opOnly.collectAsStateWithLifecycle()
    val replyTarget by viewModel.replyTarget.collectAsStateWithLifecycle()
    val followState by viewModel.followState.collectAsStateWithLifecycle()
    val liked by viewModel.liked.collectAsStateWithLifecycle()
    val likeCount by viewModel.likeCount.collectAsStateWithLifecycle()
    val favorited by viewModel.favorited.collectAsStateWithLifecycle()
    val favoriteCount by viewModel.favoriteCount.collectAsStateWithLifecycle()
    val loadingMore by viewModel.loadingMore.collectAsStateWithLifecycle()
    val hasNext by viewModel.hasNext.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val foldExpanded by viewModel.foldExpanded.collectAsStateWithLifecycle()

    var replyText by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(replyState) {
        when (val r = replyState) {
            is ThreadDetailViewModel.ReplyState.Success -> {
                replyText = ""
                snackbarHostState.showSnackbar("回复成功")
                viewModel.consumeReplyState()
                viewModel.load()
            }
            is ThreadDetailViewModel.ReplyState.Error -> {
                snackbarHostState.showSnackbar(r.message)
                viewModel.consumeReplyState()
            }
            else -> Unit
        }
    }
    LaunchedEffect(followState) {
        when (val f = followState) {
            is ThreadDetailViewModel.FollowState.Done ->
                snackbarHostState.showSnackbar(if (f.following) "已关注" else "已取消关注")
            is ThreadDetailViewModel.FollowState.Error -> snackbarHostState.showSnackbar(f.message)
            else -> Unit
        }
    }
    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeActionMessage()
        }
    }

    val page = (state as? UiState.Success)?.data
    val following = (followState as? ThreadDetailViewModel.FollowState.Done)?.following ?: false
    // 点击图片一律进内置全屏查看器；urls 为本组全部地址，便于左右翻页
    var preview by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    val openImage: (List<String>, Int) -> Unit = { urls, index ->
        if (urls.isNotEmpty()) preview = urls to index
    }
    val todo: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }

    Scaffold(
        // 整页背景纯白（top_bar），不是灰色页面底
        containerColor = colors.topBar,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.topBar),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(com.aazhou.mtforum.R.drawable.ic_back),
                            contentDescription = "返回",
                            tint = colors.textPrimary,
                        )
                    }
                },
                title = {
                    // 三个操作图标紧跟返回键左对齐
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { todo("举报需在网页端完成") }) {
                            Icon(
                                painterResource(com.aazhou.mtforum.R.drawable.ic_report_shield),
                                contentDescription = "举报",
                                tint = colors.iconSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        IconButton(onClick = { todo("赞赏请在网页端完成") }) {
                            Icon(
                                painterResource(com.aazhou.mtforum.R.drawable.ic_reward_packet_cny),
                                contentDescription = "赞赏",
                                tint = colors.iconSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        IconButton(onClick = { shareThread(context, page?.subject, tid) }) {
                            Icon(
                                painterResource(com.aazhou.mtforum.R.drawable.ic_share_outline),
                                contentDescription = "分享",
                                tint = colors.iconSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                },
                actions = {
                    if (!page?.forumName.isNullOrBlank()) {
                        Text(
                            text = page.forumName,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (page != null) {
                ReplyBar(
                    value = replyText,
                    onValueChange = { replyText = it },
                    target = replyTarget,
                    onCancelTarget = { viewModel.setReplyTarget(null) },
                    sending = replyState is ThreadDetailViewModel.ReplyState.Sending,
                    replyCount = page.totalReplies,
                    liked = liked,
                    likeCount = likeCount,
                    favorited = favorited,
                    favoriteCount = favoriteCount,
                    onSend = { if (replyText.isNotBlank()) viewModel.reply(replyText) },
                    onComments = { scope.launch { listState.animateScrollToItem(1) } },
                    onLike = { if (!viewModel.toggleLike()) todo("请先登录") },
                    onFavorite = { if (!viewModel.toggleFavorite()) todo("请先登录") },
                    onTodo = todo,
                )
            }
        },
    ) { padding ->
        when (val current = state) {
            is UiState.Loading -> LoadingBox(Modifier.padding(padding))
            is UiState.Error -> ErrorBox(current.message, { viewModel.load() }, Modifier.padding(padding))
            is UiState.Success -> {
                val data = current.data
                if (data.posts.isEmpty()) {
                    EmptyBox("没有内容", Modifier.padding(padding))
                } else {
                    val op = data.posts.firstOrNull { it.isFirstPost } ?: data.posts.firstOrNull()
                    // 树构建含 O(n²) 的引用匹配、灌水判定要扫全部楼层正文，都是纯计算；
                    // 缓存到 posts（或折叠状态）变化为止，否则滚动、点赞、输入引发的重组会在主线程重算。
                    val tree = remember(data.posts) {
                        buildReplyTree(data.posts.filter { it !== op })
                    }
                    // 灌水回复默认折叠；带子回复的不折，否则会连子回复一起藏掉
                    val waterPids = remember(tree) {
                        tree.topLevel
                            .filter { isWaterReply(it.contentHtml) && tree.children[it.pid].isNullOrEmpty() }
                            .map { it.pid }
                            .toSet()
                    }
                    val foldedCount = waterPids.size
                    val visibleReplies = remember(tree, waterPids, foldExpanded) {
                        if (foldExpanded) tree.topLevel else tree.topLevel.filter { it.pid !in waterPids }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        state = listState,
                        contentPadding = PaddingValues(bottom = bottomBarContentPadding()),
                    ) {
                        if (op != null) {
                            item(key = "header") {
                                DetailHeader(
                                    post = op,
                                    subject = data.subject,
                                    forumName = data.forumName,
                                    following = following,
                                    followEnabled = data.opUid != null && data.formhash != null,
                                    totalReplies = data.totalReplies,
                                    opOnly = opOnly,
                                    orderType = orderType,
                                    onFollow = { viewModel.toggleFollow() },
                                    onOpOnly = { viewModel.setOpOnly(it) },
                                    onOrder = { viewModel.setOrder(it) },
                                    onImageClick = openImage,
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
                                onOpOnly = { viewModel.setOpOnly(it) },
                                onOrder = { viewModel.setOrder(it) },
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
                            val children = tree.children[reply.pid].orEmpty()
                            ReplyBlock(
                                reply = reply,
                                children = children,
                                expanded = !collapsed.contains(reply.pid),
                                onToggle = { viewModel.toggleReplies(reply.pid) },
                                onReply = { viewModel.setReplyTarget(reply) },
                                onReplyTo = { viewModel.setReplyTarget(it) },
                                onImageClick = openImage,
                            )
                        }
                        item(key = "pager") {
                            // 滑到底自动续拉下一页；失败/加载中给出行内状态
                            LaunchedEffect(visibleReplies.size, hasNext) {
                                if (hasNext) viewModel.loadMore()
                            }
                            if (loadingMore) {
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

    preview?.let { (urls, index) ->
        ImagePreview(urls = urls, initialIndex = index, onDismiss = { preview = null })
    }
}

/** 归属匹配用：去掉 HTML 标签、URL 与全部空白（含不换行空格），便于比较正文快照。 */
private fun normalizeForMatch(s: String?): String = s.orEmpty()
    .replace(Regex("<[^>]*>"), "")
    .replace(Regex("https?://\\S+"), "")
    .replace(Regex("[\\s\\u00A0]+"), "")

/** 引用块短于该长度（纯表情/单个词）时区分不了楼层，宁可不匹配。 */
private const val MATCH_MIN_CHARS = 6

/** 楼层树：顶层楼层顺序列表 + 「父 pid -> 子楼层列表」。 */
private class ReplyTree(
    val topLevel: List<Post>,
    val children: Map<String, List<Post>>,
)

private class PostNode(val post: Post) {
    var isSubReply: Boolean = false
    var orderIndex: Int = -1
    val children: MutableList<PostNode> = mutableListOf()
}

/**
 * 把当前页平铺的楼层组织成楼中楼。
 *
 * 站点只有少数引用块带 pid，故按证据强度降级：pid -> 引用正文最长公共子串 -> 昵称。
 * 三者都要求被引楼层排在引用者之前，否则两条互相引用的楼层会因遍历顺序把父子方向弄反。
 */
private fun buildReplyTree(replies: List<Post>): ReplyTree {
    if (replies.isEmpty()) return ReplyTree(emptyList(), emptyMap())
    val nodes = replies.map { PostNode(it) }
    val byPid = HashMap<String, PostNode>()
    val byAuthor = HashMap<String, MutableList<PostNode>>()
    nodes.forEachIndexed { index, node ->
        node.orderIndex = index
        node.post.pid.takeIf { it.isNotBlank() }?.let { byPid[it] = node }
        node.post.authorName.takeIf { it.isNotBlank() }?.let {
            byAuthor.getOrPut(it) { mutableListOf() }.add(node)
        }
    }

    val childrenByParent = HashMap<String, MutableList<Post>>()
    for (node in nodes) {
        val quote = node.post.quote ?: continue
        val parent = resolveParent(node, nodes, byPid, byAuthor) ?: continue
        // 被引者本身是子回复时压平到它所属的顶层楼层，同一条子线程内的回复只平铺一层
        val root = if (parent.isSubReply) {
            nodes.firstOrNull { it !== parent && !it.isSubReply && it.children.any { c -> c === parent } }
        } else {
            parent
        } ?: continue
        if (root.orderIndex >= node.orderIndex) continue
        node.isSubReply = true
        root.children.add(node)
        childrenByParent.getOrPut(root.post.pid) { mutableListOf() }.add(node.post)
    }
    return ReplyTree(
        topLevel = nodes.filter { !it.isSubReply }.map { it.post },
        children = childrenByParent,
    )
}

private fun resolveParent(
    node: PostNode,
    nodes: List<PostNode>,
    byPid: Map<String, PostNode>,
    byAuthor: Map<String, MutableList<PostNode>>,
): PostNode? {
    val quote = node.post.quote ?: return null
    quote.pid?.takeIf { it.isNotBlank() }?.let { pid ->
        byPid[pid]?.takeIf { it !== node && it.orderIndex < node.orderIndex }?.let { return it }
    }
    matchByQuotedContent(node, nodes)?.let { return it }
    // 昵称是最弱的证据：同一作者的每条楼层都同名，只在无同名歧义时采用
    val name = quote.author?.takeIf { it.isNotBlank() } ?: return null
    val candidates = byAuthor[name].orEmpty().filter { it !== node && it.orderIndex < node.orderIndex }
    return candidates.singleOrNull()
}

/**
 * pid 缺失或指向未加载楼层时的降级匹配：拿引用块正文去比对各楼层正文。
 *
 * 引用块开头还挂着「回复 X 发表于 …」这段 meta，前缀比较必然对不齐，故用最长公共子串；
 * 只重合寥寥几字更像巧合同词，命中度不足时放弃，交给更弱的昵称证据。
 */
private fun matchByQuotedContent(node: PostNode, nodes: List<PostNode>): PostNode? {
    val quoted = normalizeForMatch(node.post.quote?.text ?: return null)
    if (quoted.length < MATCH_MIN_CHARS) return null
    var best: PostNode? = null
    var bestScore = 0
    for (candidate in nodes) {
        if (candidate === node || candidate.orderIndex >= node.orderIndex) continue
        val body = normalizeForMatch(candidate.post.contentHtml)
        if (body.isEmpty()) continue
        val score = longestCommonSubstring(quoted, body)
        if (score > bestScore) {
            bestScore = score
            best = candidate
        }
    }
    if (best == null) return null
    return if (bestScore >= MATCH_MIN_CHARS && bestScore >= quoted.length / 3) best else null
}

/** 两个字符串的最长公共子串长度（滚动数组）。 */
private fun longestCommonSubstring(a: String, b: String): Int {
    if (a.isEmpty() || b.isEmpty()) return 0
    val shorter = if (a.length <= b.length) a else b
    val longer = if (a.length <= b.length) b else a
    var prev = IntArray(shorter.length + 1)
    var cur = IntArray(shorter.length + 1)
    var best = 0
    for (i in 1..longer.length) {
        for (j in 1..shorter.length) {
            cur[j] = if (longer[i - 1] == shorter[j - 1]) prev[j - 1] + 1 else 0
            if (cur[j] > best) best = cur[j]
        }
        val swap = prev
        prev = cur
        cur = swap
        cur.fill(0)
    }
    return best
}

/** 头像可点：点击后用同一套全屏查看器打开；无头像地址时不响应。 */
private fun Modifier.avatarClick(
    url: String?,
    onImageClick: (List<String>, Int) -> Unit,
): Modifier =
    if (url.isNullOrBlank()) this else clickable { onImageClick(listOf(url), 0) }

private fun shareThread(context: Context, subject: String?, tid: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "$subject https://bbs.binmt.cc/thread-$tid-1-1.html")
    }
    context.startActivity(Intent.createChooser(intent, "分享"))
}

/**
 * 正文头：标题 → 隔离线 → 作者信息栏（头像/昵称/等级/时间/地点/关注）→
 * 编辑标记 → 正文 → 结束线 → 评论标题栏（计数/折叠徽章/分段控件）。
 */
@Composable
private fun DetailHeader(
    post: Post,
    subject: String,
    forumName: String?,
    following: Boolean,
    followEnabled: Boolean,
    totalReplies: Int?,
    opOnly: Boolean,
    orderType: Int,
    onFollow: () -> Unit,
    onOpOnly: (Boolean) -> Unit,
    onOrder: (Int) -> Unit,
    onImageClick: (List<String>, Int) -> Unit,
) {
    val colors = LocalMtfColors.current
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        // 标题
        Text(
            text = subject,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp, lineHeight = 29.sp),
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )
        HorizontalDivider(
            color = colors.divider,
            thickness = 0.5.dp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            AsyncImage(
                model = post.avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .avatarClick(post.avatarUrl, onImageClick),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        post.authorName.ifBlank { "匿名" },
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!post.level.isNullOrBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            post.level,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = colors.textSecondary,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
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
            if (followEnabled) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (following) "已关注" else "关注",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    fontWeight = FontWeight.Bold,
                    color = if (following) colors.textSecondary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .height(26.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (following) colors.quoteBackground else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .clickable(onClick = onFollow)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
        }
        if (!post.editedText.isNullOrBlank()) {
            Text(
                text = post.editedText,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                fontStyle = FontStyle.Italic,
                color = colors.textSecondary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            )
        }
        Spacer(Modifier.height(2.dp))
        PostContent(
            html = post.contentHtml,
            onImageClick = onImageClick,
            bodyStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 24.sp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        PostFooterLine()
    }
}

/** 正文与评论区的过渡底线。 */
@Composable
private fun PostFooterLine() {
    val colors = LocalMtfColors.current
    Spacer(Modifier.height(6.dp))
    HorizontalDivider(
        color = colors.divider,
        thickness = 0.6.dp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
    )
}

/** 「全部评论 (N) [已折叠 N 条 / 收起已折叠]  楼主 | 正序 | 倒序」。 */
@Composable
private fun CommentHeader(
    total: Int?,
    folded: Int,
    foldExpanded: Boolean,
    opOnly: Boolean,
    orderType: Int,
    onToggleFold: () -> Unit,
    onOpOnly: (Boolean) -> Unit,
    onOrder: (Int) -> Unit,
) {
    val colors = LocalMtfColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "全部评论",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.5.sp),
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
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
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.quoteBackground)
                    .clickable(onClick = onToggleFold)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        SegmentedControl(
            selected = when {
                opOnly -> 0
                orderType == 1 -> 2
                else -> 1
            },
            onSelect = { index ->
                when (index) {
                    0 -> onOpOnly(true)
                    1 -> {
                        onOpOnly(false)
                        onOrder(0)
                    }
                    else -> {
                        onOpOnly(false)
                        onOrder(1)
                    }
                }
            },
        )
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
                    color = if (active) colors.textPrimary else colors.textSecondary,
                )
            }
        }
    }
}

/** 顶层回复：头像 36dp、楼层号 11sp、昵称 14sp、引用卡、正文 15sp、底栏时间与操作。 */
@Composable
private fun ReplyBlock(
    reply: Post,
    children: List<Post>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onReply: () -> Unit,
    onReplyTo: (Post) -> Unit,
    onImageClick: (List<String>, Int) -> Unit,
) {
    val colors = LocalMtfColors.current
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            AsyncImage(
                model = reply.avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .avatarClick(reply.avatarUrl, onImageClick),
            )
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
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
                        color = colors.textPrimary.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                    bodyStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(45.dp))
            TimeLine(reply)
            Spacer(Modifier.weight(1f))
            if (children.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onToggle)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Icon(
                        if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        if (expanded) "收起回复" else "查看 ${children.size} 条回复",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.textSecondary,
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
            Icon(
                painterResource(com.aazhou.mtforum.R.drawable.ic_comment_lines),
                contentDescription = "回复",
                tint = colors.iconSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onReply),
            )
        }
        if (expanded) {
            // 楼中楼容器缩进 45dp，与正文文字对齐
            Column(Modifier.padding(start = 45.dp, top = 8.dp)) {
                children.forEach { child ->
                    NestedReply(child, onReply = { onReplyTo(child) }, onImageClick = onImageClick)
                }
            }
        }
    }
}

/** 楼中楼：头像 22dp、昵称 13sp、正文 14sp，缩进由父容器给出。 */
@Composable
private fun NestedReply(child: Post, onReply: () -> Unit, onImageClick: (List<String>, Int) -> Unit) {
    val colors = LocalMtfColors.current
    Column(Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = child.avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .avatarClick(child.avatarUrl, onImageClick),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                child.authorName.ifBlank { "匿名" },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = colors.textPrimary.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (child.isOp) {
                Spacer(Modifier.width(6.dp))
                AuthorBadge("楼主", small = true)
            }
            if (!child.editedText.isNullOrBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(
                    child.editedText,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        PostContent(
            html = child.contentHtml,
            onImageClick = onImageClick,
            bodyStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
            modifier = Modifier.padding(start = 30.dp, top = 3.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 30.dp, top = 4.dp)) {
            TimeLine(child)
            Spacer(Modifier.weight(1f))
            Icon(
                painterResource(com.aazhou.mtforum.R.drawable.ic_comment_lines),
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
private fun AuthorBadge(text: String, small: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = if (small) 9.5.sp else 10.sp),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .padding(horizontal = 5.dp, vertical = if (small) 1.dp else 2.dp),
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

/** 底部栏：灰底输入框 + 评论 / 点赞 / 收藏 / 图片。 */
@Composable
private fun ReplyBar(
    value: String,
    onValueChange: (String) -> Unit,
    target: Post?,
    onCancelTarget: () -> Unit,
    sending: Boolean,
    replyCount: Int?,
    liked: Boolean,
    likeCount: Int,
    favorited: Boolean,
    favoriteCount: Int,
    onSend: () -> Unit,
    onComments: () -> Unit,
    onLike: () -> Unit,
    onFavorite: () -> Unit,
    onTodo: (String) -> Unit,
) {
    val colors = LocalMtfColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.topBar)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        if (target != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(
                    "回复 @${target.authorName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "取消",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.clickable(onClick = onCancelTarget).padding(4.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.quoteBackground)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        "说点什么吧...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textHint,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(6.dp))
            if (sending) {
                CircularProgressIndicator(Modifier.size(22.dp))
            } else if (value.isNotBlank()) {
                IconButton(onClick = onSend) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "发送",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                BarAction(onClick = onComments) {
                    Icon(
                        painterResource(com.aazhou.mtforum.R.drawable.ic_comment_outline),
                        contentDescription = "查看评论",
                        tint = colors.iconSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (replyCount != null) {
                    Text(
                        replyCount.toString(),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.textSecondary,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
                BarAction(onClick = onLike) {
                    Icon(
                        painterResource(com.aazhou.mtforum.R.drawable.ic_like_outline),
                        contentDescription = "点赞",
                        tint = if (liked) MaterialTheme.colorScheme.primary else colors.iconSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (likeCount > 0) {
                    Text(
                        likeCount.toString(),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = if (liked) MaterialTheme.colorScheme.primary else colors.textSecondary,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
                BarAction(onClick = onFavorite) {
                    Icon(
                        painterResource(com.aazhou.mtforum.R.drawable.ic_favorite_outline),
                        contentDescription = "收藏",
                        tint = if (favorited) MaterialTheme.colorScheme.primary else colors.iconSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (favoriteCount > 0) {
                    Text(
                        favoriteCount.toString(),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = if (favorited) MaterialTheme.colorScheme.primary else colors.textSecondary,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
                BarAction(onClick = { onTodo("图片上传暂未开放") }) {
                    Icon(
                        painterResource(com.aazhou.mtforum.R.drawable.ic_gallery_outline),
                        contentDescription = "选择图片",
                        tint = colors.iconSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BarAction(onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        icon()
    }
}

