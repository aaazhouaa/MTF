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
import androidx.compose.runtime.rememberUpdatedState
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
import com.aazhou.mtforum.ui.screen.forumlist.ForumListScreen
import com.aazhou.mtforum.ui.screen.downloads.DownloadsScreen
import com.aazhou.mtforum.ui.screen.guide.GuideScreen
import com.aazhou.mtforum.ui.screen.home.HomeScreen
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
import com.aazhou.mtforum.ui.screen.verify.WafVerificationScreen
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

object Routes {
    const val HOME = "home"
    const val FORUMS = "forums"
    const val GUIDE = "guide"
    const val PROFILE = "profile"
    const val SEARCH = "search"
    const val LOGIN = "login"
    const val SETTINGS = "settings"
    const val FORUM = "forum/{fid}/{name}"
    // pid 是可查询参数：消息提醒跳进来时带上要定位的楼层，普通进帖不带
    const val THREAD = "thread/{tid}?pid={pid}"
    const val USER = "user/{uid}"
    const val POST = "post/{fid}/{name}"
    const val NOTICE = "notice"
    const val DOWNLOADS = "downloads"
    const val DOWNLOADED_THREAD = "downloaded/{tid}"

    fun forum(fid: String, name: String) = "forum/$fid/${Uri.encode(name)}"
    fun thread(tid: String) = "thread/$tid"

    /** 从消息提醒跳进来：[pid] 为要定位的楼层，空表示只打开帖子。 */
    fun threadAnchor(tid: String, pid: String?) =
        if (pid.isNullOrBlank()) "thread/$tid" else "thread/$tid?pid=$pid"
    fun user(uid: String) = "user/$uid"
    fun post(fid: String, name: String) = "post/$fid/${Uri.encode(name)}"
    fun downloadedThread(tid: String) = "downloaded/$tid"
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
        GlassTab("导读", painterResource(R.drawable.ic_find)),
        GlassTab("我的", painterResource(R.drawable.ic_me)),
    )
    val tabRoutes = listOf(Routes.HOME, Routes.FORUMS, Routes.GUIDE, Routes.PROFILE)
    val selectedIndex = tabRoutes.indexOf(currentRoute)
    val showBottomBar = selectedIndex >= 0

    Box(Modifier.fillMaxSize()) {
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
                .layerBackdrop(backdrop),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onSearch = { navController.navigate(Routes.SEARCH) },
                    onToggleTheme = onToggleTheme,
                    // 消息提醒需登录态，未登录时先走登录页（与「我的」一致）
                    onMessages = {
                        if (container.httpClient.isLoggedIn()) {
                            navController.navigate(Routes.NOTICE)
                        } else {
                            navController.navigate(Routes.LOGIN)
                        }
                    },
                    onForumClick = { fid, name -> navController.navigate(Routes.forum(fid, name)) },
                    onThreadClick = { tid -> navController.navigate(Routes.thread(tid)) },
                    // 顶栏头像进个人主页（自己的空间页）
                    onProfileClick = { uid -> navController.navigate(Routes.user(uid)) },
                )
            }
            composable(Routes.FORUMS) {
                ForumListScreen(onForumClick = { fid, name ->
                    navController.navigate(Routes.forum(fid, name))
                })
            }
            composable(Routes.GUIDE) {
                GuideScreen(onThreadClick = { tid -> navController.navigate(Routes.thread(tid)) })
            }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    onLogin = { navController.navigate(Routes.LOGIN) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                    onDownloads = { navController.navigate(Routes.DOWNLOADS) },
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(onThreadClick = { tid -> navController.navigate(Routes.thread(tid)) })
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
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(Routes.NOTICE) {
                NoticeScreen(
                    onBack = { navController.popBackStack() },
                    onThreadClick = { tid, pid ->
                        navController.navigate(Routes.threadAnchor(tid, pid))
                    },
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
                    onNewPost = { navController.navigate(Routes.post(fid, name)) },
                )
            }

            composable(
                Routes.THREAD,
                arguments = listOf(
                    navArgument("tid") { type = NavType.StringType },
                    navArgument("pid") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                ThreadDetailScreen(
                    tid = entry.arguments?.getString("tid").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onUserClick = { uid -> navController.navigate(Routes.user(uid)) },
                    // 正文里的站内帖子链接在 App 内继续看帖，不进浏览器
                    onThreadClick = { tid -> navController.navigate(Routes.thread(tid)) },
                    // 消息提醒跳进来时带上要定位的楼层
                    anchorPid = entry.arguments?.getString("pid")?.takeIf { it.isNotBlank() },
                )
            }

            composable(Routes.USER, arguments = listOf(navArgument("uid") { type = NavType.StringType })) { entry ->
                UserProfileScreen(
                    uid = entry.arguments?.getString("uid").orEmpty(),
                    onBack = { navController.popBackStack() },
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
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
