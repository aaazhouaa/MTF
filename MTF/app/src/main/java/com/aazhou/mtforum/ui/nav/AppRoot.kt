package com.aazhou.mtforum.ui.nav

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aazhou.mtforum.R
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aazhou.mtforum.ui.common.container
import com.aazhou.mtforum.ui.common.GlobalToastHost
import com.aazhou.mtforum.ui.common.maskAccessibility
import com.aazhou.mtforum.ui.screen.forumlist.ForumListScreen
import com.aazhou.mtforum.ui.screen.downloads.DownloadsScreen
import com.aazhou.mtforum.ui.screen.home.HomeScreen
import com.aazhou.mtforum.ui.screen.logs.LogDetailScreen
import com.aazhou.mtforum.ui.screen.logs.LogsScreen
import com.aazhou.mtforum.ui.screen.login.LoginScreen
import com.aazhou.mtforum.ui.screen.notice.NoticeScreen
import com.aazhou.mtforum.ui.screen.postedit.PostEditScreen
import com.aazhou.mtforum.ui.screen.profile.ProfileScreen
import com.aazhou.mtforum.ui.screen.search.SearchScreen
import com.aazhou.mtforum.ui.screen.settings.SettingsScreen
import com.aazhou.mtforum.ui.screen.threaddetail.ThreadDetailScreen
import com.aazhou.mtforum.ui.screen.threaddetail.DownloadedThreadScreen
import com.aazhou.mtforum.ui.screen.threadlist.ThreadListScreen
import com.aazhou.mtforum.ui.screen.user.UserProfileScreen
import com.aazhou.mtforum.ui.screen.chat.ChatScreen
import com.aazhou.mtforum.ui.screen.verify.WafVerificationScreen
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

object Routes {
    const val HOME = "home"
    const val FORUMS = "forums"
    const val PROFILE = "profile"
    const val SEARCH = "search"
    const val LOGIN = "login"
    const val SETTINGS = "settings"
    const val FORUM = "forum/{fid}/{name}"
    // pid 是可查询参数：消息提醒跳进来时带上要定位的楼层，普通进帖不带
    const val THREAD = "thread/{tid}?pid={pid}"
    const val USER = "user/{uid}"
    const val CHAT = "chat/{uid}?name={name}"
    const val POST = "post/{fid}/{name}"
    /** 编辑自己的帖子/评论：站点 tid/pid 决定改主题（带标题）还是改评论。 */
    const val EDIT_POST = "edit_post/{tid}/{pid}/{fid}/{name}"
    const val NOTICE = "notice"
    const val DOWNLOADS = "downloads"
    const val DOWNLOADED_THREAD = "downloaded/{tid}"
    const val LOGS = "logs"
    const val LOG_DETAIL = "log/{id}"
    const val MY_POSTS = "my_posts"
    const val DRAFTS = "drafts"
    const val MY_FAVORITES = "my_favorites"
    const val MY_FRIENDS = "my_friends"
    const val USER_POSTS = "user_posts/{uid}"
    const val FOLLOW_LIST = "follow_list?type={type}"

    fun followList(type: String = "following") = "follow_list?type=$type"

    fun forum(fid: String, name: String) = "forum/$fid/${Uri.encode(name)}"
    fun thread(tid: String) = "thread/$tid"

    fun user(uid: String) = "user/$uid"
    fun userPosts(uid: String) = "user_posts/$uid"
    fun chat(uid: String, name: String? = null) =
        if (name.isNullOrBlank()) "chat/$uid" else "chat/$uid?name=${Uri.encode(name)}"
    fun post(fid: String, name: String) =
        "post/$fid/${Uri.encode(name.ifBlank { "版块" })}"
    fun editPost(tid: String, pid: String, fid: String, name: String) =
        "edit_post/$tid/$pid/$fid/${Uri.encode(name.ifBlank { "版块" })}"
    fun downloadedThread(tid: String) = "downloaded/$tid"
    fun logDetail(id: Long) = "log/$id"
}

@Composable
fun AppRoot(onToggleTheme: () -> Unit, openNotice: Boolean = false) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // 本地 WAF 求解失配时，全局拉起人机验证页；完成后 cookie 回灌给原生会话
    val container = LocalContext.current.container()

    // 点了新消息通知：直达消息页（未登录仍留在首页，消息页要登录态才能看）
    LaunchedEffect(openNotice) {
        if (openNotice && container.httpClient.isLoggedIn()) {
            navController.navigate(Routes.NOTICE)
        }
    }
    val needsVerification by container.httpClient.pendingVerificationFlow.collectAsStateWithLifecycle()
    if (needsVerification) {
        Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            WafVerificationScreen(
                url = container.httpClient.verificationUrl,
                onFinished = { cookie -> container.httpClient.completeVerification(cookie) },
            )
        }
    }

    val tabs = listOf(
        GlassTab("首页", painterResource(R.drawable.ic_home)),
        GlassTab("板块", painterResource(R.drawable.ic_list)),
        GlassTab("我的", painterResource(R.drawable.ic_me)),
    )
    val tabRoutes = listOf(Routes.HOME, Routes.FORUMS, Routes.PROFILE)
    val selectedIndex = tabRoutes.indexOf(currentRoute)
    val showBottomBar = selectedIndex >= 0
    // 底栏重复点击「首页」的计数：每点一次 +1，首页据此把当前分页的列表拉回顶部
    var homeScrollTopTick by remember { mutableIntStateOf(0) }

    Box(
        Modifier
            .fillMaxSize()
            // 屏蔽本应用的无障碍内容：开着 GKD 等无障碍服务时快滑必卡，详见 AccessibilityMask.kt
            .maskAccessibility(),
    ) {
        val background = MaterialTheme.colorScheme.background
        // 主题色必须经 state 间接读取再画进 backdrop：rememberLayerBackdrop 把 onDraw
        // 这个 lambda 当 remember key，直接捕获背景色的话，一换主题 lambda 就变、
        // LayerBackdrop 实例随之重建；新实例的 layerCoordinates 要等下一次布局才补上，
        // 而换主题只改颜色不改布局，于是底栏采样一直拿到空、毛玻璃整个消失。
        val backgroundState = rememberUpdatedState(background)
        val backdrop = rememberLayerBackdrop {
            drawRect(backgroundState.value)
            drawContent()
        }

        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            // 进出过渡也必须为 None：NavHost 默认 enter/exit 是 700ms 交叉淡入淡出，
            // 切 tab 时新旧两页同时在场淡入淡出，表现为整屏闪一下；玻璃底栏采样的是
            // 这个 NavHost 的图层，双页同场时采样内容还会跳变，故一律即时切换。
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            // 返回过渡同样必须为 None：NavHost 默认 popEnter/popExit 是 700ms 交叉淡入淡出，
            // 期间被弹出的页面仍留在 composition 里淡出且 zIndex 高于下层页面，会先接到并
            // 消耗用户的手势，表现为「退出帖子后第一次滑动不生效」。淡出用 alpha 实现，
            // 命中测试不看 alpha，所以只能让旧页面在返回时立即移除。
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
            modifier = Modifier
                .fillMaxSize()
            // 采样层只在 3 个 tab 页挂：非 tab 路由（搜索/登录/帖子详情/通知/发帖）没有玻璃底栏
            // 消费这个 backdrop，挂了也只是逐帧把全屏内容录进一个没人读取的图层
            .then(if (showBottomBar) Modifier.layerBackdrop(backdrop) else Modifier),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onSearch = { navController.navigate(Routes.SEARCH) },
                    onToggleTheme = onToggleTheme,
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                    // 未登录时顶栏左侧是登录图标，点击进登录页
                    onLogin = { navController.navigate(Routes.LOGIN) },
                    onUserClick = { uid -> navController.navigate(Routes.user(uid)) },
                    // 消息提醒：未登录时进入也会跳出登录弹窗
                    onMessages = {
                        navController.navigate(Routes.NOTICE)
                    },
                    onForumClick = { fid, name -> navController.navigate(Routes.forum(fid, name)) },
                    onThreadClick = { tid -> navController.navigate(Routes.thread(tid)) },
                    // 顶栏头像进个人主页（自己的空间页）
                    onProfileClick = { uid -> navController.navigate(Routes.user(uid)) },
                    scrollToTopTick = homeScrollTopTick,
                )
            }
            composable(Routes.FORUMS) {
                ForumListScreen(
                    onForumClick = { fid, name ->
                        navController.navigate(Routes.forum(fid, name))
                    },
                    onSearch = {
                        navController.navigate(Routes.SEARCH)
                    },
                )
            }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    onLogin = { navController.navigate(Routes.LOGIN) },
                    onDownloads = { navController.navigate(Routes.DOWNLOADS) },
                    onDrafts = { navController.navigate(Routes.DRAFTS) },
                    onMyPosts = {
                        if (container.httpClient.isLoggedIn()) navController.navigate(Routes.MY_POSTS)
                        else navController.navigate(Routes.LOGIN)
                    },
                    onMyFavorites = {
                        if (container.httpClient.isLoggedIn()) navController.navigate(Routes.MY_FAVORITES)
                        else navController.navigate(Routes.LOGIN)
                    },
                    onMyFriends = {
                        if (container.httpClient.isLoggedIn()) navController.navigate(Routes.MY_FRIENDS)
                        else navController.navigate(Routes.LOGIN)
                    },
                    onFollowList = { type ->
                        if (container.httpClient.isLoggedIn()) navController.navigate(Routes.followList(type))
                        else navController.navigate(Routes.LOGIN)
                    },
                    onUserClick = { uid -> navController.navigate(Routes.user(uid)) },
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onThreadClick = { tid -> navController.navigate(Routes.thread(tid)) },
                    onUserClick = { uid -> navController.navigate(Routes.user(uid)) },
                    onForumClick = { fid, name -> navController.navigate(Routes.forum(fid, name)) },
                )
            }
            composable(Routes.LOGIN) {
                LoginScreen(
                    onBack = { navController.popBackStack() },
                    onSuccess = {
                        // 登录成功后立刻刷新当前用户资料，首页头像随之出现
                        container.refreshCurrentUserAsync()
                        navController.popBackStack()
                    },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenLogs = { navController.navigate(Routes.LOGS) },
                )
            }
            composable(Routes.LOGS) {
                LogsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenLog = { id -> navController.navigate(Routes.logDetail(id)) },
                )
            }
            composable(
                Routes.LOG_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                LogDetailScreen(
                    id = entry.arguments?.getLong("id") ?: 0L,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.NOTICE) {
                NoticeScreen(
                    onBack = { navController.popBackStack() },
                    onThreadClick = { tid -> navController.navigate(Routes.thread(tid)) },
                    // 消息里的头像进对应用户的个人主页
                    onUserClick = { uid -> navController.navigate(Routes.user(uid)) },
                    onChatClick = { uid, name -> navController.navigate(Routes.chat(uid, name)) },
                )
            }
            composable(Routes.DOWNLOADS) {
                DownloadsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenThread = { tid -> navController.navigate(Routes.downloadedThread(tid)) },
                )
            }
            composable(
                Routes.DOWNLOADED_THREAD,
                arguments = listOf(navArgument("tid") { type = NavType.StringType }),
            ) { entry ->
                DownloadedThreadScreen(
                    tid = entry.arguments?.getString("tid").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onUserClick = { uid -> navController.navigate(Routes.user(uid)) },
                    onThreadClick = { tid -> navController.navigate(Routes.thread(tid)) },
                )
            }

            composable(
                Routes.FORUM,
                arguments = listOf(
                    navArgument("fid") { type = NavType.StringType },
                    navArgument("name") { type = NavType.StringType },
                ),
            ) { entry ->
                val fid = entry.arguments?.getString("fid").orEmpty()
                val name = entry.arguments?.getString("name").orEmpty()
                ThreadListScreen(
                    fid = fid,
                    title = name,
                    onBack = { navController.popBackStack() },
                    onThreadClick = { tid -> navController.navigate(Routes.thread(tid)) },
                    onUserClick = { uid -> navController.navigate(Routes.user(uid)) },
                    onNewPost = { navController.navigate(Routes.post(fid, name)) },
                )
            }

            composable(
                Routes.THREAD,
                arguments = listOf(
                    navArgument("tid") { type = NavType.StringType },
                ),
            ) { entry ->
                ThreadDetailScreen(
                    tid = entry.arguments?.getString("tid").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onUserClick = { uid -> navController.navigate(Routes.user(uid)) },
                    // 正文里的站内帖子链接在 App 内继续看帖，不进浏览器
                    onThreadClick = { tid -> navController.navigate(Routes.thread(tid)) },
                    // 楼主是自己：进整页编辑（标题 + 正文）
                    onEditThread = { tid, pid, fid, name ->
                        navController.navigate(Routes.editPost(tid, pid, fid, name))
                    },
                )
            }

            composable(
                Routes.EDIT_POST,
                arguments = listOf(
                    navArgument("tid") { type = NavType.StringType },
                    navArgument("pid") { type = NavType.StringType },
                    navArgument("fid") { type = NavType.StringType },
                    navArgument("name") { type = NavType.StringType },
                ),
            ) { entry ->
                PostEditScreen(
                    fid = entry.arguments?.getString("fid").orEmpty(),
                    forumName = entry.arguments?.getString("name").orEmpty(),
                    editTid = entry.arguments?.getString("tid").orEmpty(),
                    editPid = entry.arguments?.getString("pid").orEmpty(),
                    onBack = { navController.popBackStack() },
                    // 保存后回到帖子页：内容已在提交时更新，重进即最新
                    onPosted = { navController.popBackStack() },
                )
            }

            composable(Routes.USER, arguments = listOf(navArgument("uid") { type = NavType.StringType })) { entry ->
                UserProfileScreen(
                    uid = entry.arguments?.getString("uid").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onChat = { targetUid, targetName ->
                        navController.navigate(Routes.chat(targetUid, targetName))
                    },
                    onPostsClick = { targetUid ->
                        navController.navigate(Routes.userPosts(targetUid))
                    },
                    // 看自己的主页时内容即「我的」页，入口导航与底栏 tab 保持一致
                    onLogin = { navController.navigate(Routes.LOGIN) },
                    onDownloads = { navController.navigate(Routes.DOWNLOADS) },
                    onDrafts = { navController.navigate(Routes.DRAFTS) },
                    onMyPosts = {
                        if (container.httpClient.isLoggedIn()) navController.navigate(Routes.MY_POSTS)
                        else navController.navigate(Routes.LOGIN)
                    },
                    onMyFavorites = {
                        if (container.httpClient.isLoggedIn()) navController.navigate(Routes.MY_FAVORITES)
                        else navController.navigate(Routes.LOGIN)
                    },
                    onMyFriends = {
                        if (container.httpClient.isLoggedIn()) navController.navigate(Routes.MY_FRIENDS)
                        else navController.navigate(Routes.LOGIN)
                    },
                    onFollowList = { type ->
                        if (container.httpClient.isLoggedIn()) navController.navigate(Routes.followList(type))
                        else navController.navigate(Routes.LOGIN)
                    },
                )
            }

            composable(
                Routes.CHAT,
                arguments = listOf(
                    navArgument("uid") { type = NavType.StringType },
                    navArgument("name") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                val chatUid = entry.arguments?.getString("uid").orEmpty()
                val chatName = entry.arguments?.getString("name")
                ChatScreen(
                    uid = chatUid,
                    targetName = chatName,
                    onBack = { navController.popBackStack() },
                    onUserClick = { u -> navController.navigate(Routes.user(u)) },
                )
            }

            composable(
                Routes.POST,
                arguments = listOf(
                    navArgument("fid") { type = NavType.StringType },
                    navArgument("name") { type = NavType.StringType },
                ),
            ) { entry ->
                PostEditScreen(
                    fid = entry.arguments?.getString("fid").orEmpty(),
                    forumName = entry.arguments?.getString("name").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onPosted = { navController.popBackStack() },
                )
            }

            composable(Routes.MY_POSTS) {
                com.aazhou.mtforum.ui.screen.myposts.MyPostsScreen(
                    onBack = { navController.popBackStack() },
                    onThreadClick = { tid -> navController.navigate(Routes.thread(tid)) },
                    onUserClick = { uid -> navController.navigate(Routes.user(uid)) },
                )
            }
            composable(
                Routes.USER_POSTS,
                arguments = listOf(navArgument("uid") { type = NavType.StringType }),
            ) { entry ->
                com.aazhou.mtforum.ui.screen.user.UserPostsScreen(
                    uid = entry.arguments?.getString("uid").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onThreadClick = { tid -> navController.navigate(Routes.thread(tid)) },
                    onUserClick = { uid -> navController.navigate(Routes.user(uid)) },
                )
            }

            composable(Routes.DRAFTS) {
                com.aazhou.mtforum.ui.screen.drafts.DraftsScreen(
                    onBack = { navController.popBackStack() },
                    // 继续写这一篇：进该版块发帖页，草稿由发帖页自行回填
                    onOpenDraft = { fid, name -> navController.navigate(Routes.post(fid, name)) },
                )
            }

            composable(Routes.MY_FAVORITES) {
                com.aazhou.mtforum.ui.screen.myfavorites.MyFavoritesScreen(
                    onBack = { navController.popBackStack() },
                    onThreadClick = { tid -> navController.navigate(Routes.thread(tid)) },
                    onForumClick = { fid, name -> navController.navigate(Routes.forum(fid, name)) },
                )
            }

            composable(Routes.MY_FRIENDS) {
                com.aazhou.mtforum.ui.screen.myfriends.MyFriendsScreen(
                    onBack = { navController.popBackStack() },
                    onUserClick = { uid -> navController.navigate(Routes.user(uid)) },
                )
            }

            composable(
                Routes.FOLLOW_LIST,
                arguments = listOf(
                    navArgument("type") {
                        type = NavType.StringType
                        defaultValue = "following"
                    },
                ),
            ) { entry ->
                val type = entry.arguments?.getString("type") ?: "following"
                com.aazhou.mtforum.ui.screen.follow.FollowListScreen(
                    initialType = type,
                    onBack = { navController.popBackStack() },
                    onUserClick = { uid -> navController.navigate(Routes.user(uid)) },
                )
            }
        }

        if (showBottomBar) {
            GlassBottomBar(
                backdrop = backdrop,
                tabs = tabs,
                selectedIndex = selectedIndex,
                onSelect = { index ->
                    navController.navigate(tabRoutes[index]) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onReselect = { index ->
                    if (tabRoutes[index] == Routes.HOME) homeScrollTopTick++
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        // 全局轻提示：统一贴在窗口顶部，盖在一切页面之上；发送一律走 AppToast.show
        GlobalToastHost(Modifier.align(Alignment.TopCenter))
    }
}
