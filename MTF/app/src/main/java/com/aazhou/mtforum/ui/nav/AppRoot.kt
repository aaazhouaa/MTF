package com.aazhou.mtforum.ui.nav

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aazhou.mtforum.ui.common.container
import com.aazhou.mtforum.ui.screen.forumlist.ForumListScreen
import com.aazhou.mtforum.ui.screen.guide.GuideScreen
import com.aazhou.mtforum.ui.screen.home.HomeScreen
import com.aazhou.mtforum.ui.screen.login.LoginScreen
import com.aazhou.mtforum.ui.screen.postedit.PostEditScreen
import com.aazhou.mtforum.ui.screen.profile.ProfileScreen
import com.aazhou.mtforum.ui.screen.search.SearchScreen
import com.aazhou.mtforum.ui.screen.settings.SettingsScreen
import com.aazhou.mtforum.ui.screen.threaddetail.ThreadDetailScreen
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
    const val THREAD = "thread/{tid}"
    const val USER = "user/{uid}"
    const val POST = "post/{fid}/{name}"

    fun forum(fid: String, name: String) = "forum/$fid/${Uri.encode(name)}"
    fun thread(tid: String) = "thread/$tid"
    fun user(uid: String) = "user/$uid"
    fun post(fid: String, name: String) = "post/$fid/${Uri.encode(name)}"
}

@Composable
fun AppRoot(onToggleTheme: () -> Unit) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // 本地 WAF 求解失配时，全局拉起人机验证页；完成后 cookie 回灌给原生会话
    val container = LocalContext.current.container()
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
        GlassTab("首页", Icons.Filled.Home),
        GlassTab("板块", Icons.Filled.List),
        GlassTab("导读", Icons.Filled.Star),
        GlassTab("我的", Icons.Filled.AccountCircle),
    )
    val tabRoutes = listOf(Routes.HOME, Routes.FORUMS, Routes.GUIDE, Routes.PROFILE)
    val selectedIndex = tabRoutes.indexOf(currentRoute)
    val showBottomBar = selectedIndex >= 0

    Box(Modifier.fillMaxSize()) {
        val background = MaterialTheme.colorScheme.background
        val backdrop = rememberLayerBackdrop {
            drawRect(background)
            drawContent()
        }

        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            // 返回过渡必须为 None：NavHost 默认 popEnter/popExit 是 700ms 交叉淡入淡出，
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
                    onForumClick = { fid, name -> navController.navigate(Routes.forum(fid, name)) },
                    onThreadClick = { tid -> navController.navigate(Routes.thread(tid)) },
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
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(onThreadClick = { tid -> navController.navigate(Routes.thread(tid)) })
            }
            composable(Routes.LOGIN) {
                LoginScreen(
                    onBack = { navController.popBackStack() },
                    onSuccess = { navController.popBackStack() },
                )
            }
            composable(Routes.SETTINGS) { SettingsScreen() }

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

            composable(Routes.THREAD, arguments = listOf(navArgument("tid") { type = NavType.StringType })) { entry ->
                ThreadDetailScreen(
                    tid = entry.arguments?.getString("tid").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onUserClick = { uid -> navController.navigate(Routes.user(uid)) },
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
