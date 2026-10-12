package com.aazhou.mtforum

import android.app.Application
import android.content.Context
import com.aazhou.mtforum.data.cache.ContentCache
import com.aazhou.mtforum.data.download.ThreadArchiveStore
import com.aazhou.mtforum.data.log.AppLogger
import com.aazhou.mtforum.data.log.LogStore
import com.aazhou.mtforum.data.model.UserProfile
import com.aazhou.mtforum.data.network.HttpClient
import com.aazhou.mtforum.data.repo.ActionStore
import com.aazhou.mtforum.data.repo.DownloadStore
import com.aazhou.mtforum.data.repo.DraftStore
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.data.repo.LogSettingsStore
import com.aazhou.mtforum.data.repo.NoticeChecker
import com.aazhou.mtforum.data.repo.NoticeUnreadStore
import com.aazhou.mtforum.data.repo.CheckInManager
import com.aazhou.mtforum.data.repo.SessionStore
import com.aazhou.mtforum.data.repo.SmileyStore
import com.aazhou.mtforum.data.repo.ThemeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 应用级依赖容器：手动 DI，避免引入注解处理框架。 */
class AppContainer(application: Application) {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val appContext: Context = application

    val sessionStore = SessionStore(application)
    val themeStore = ThemeStore(application)
    val actionStore = ActionStore(application)
    /** 已下载帖子的登记表（列表、打开、手动删除）。 */
    val downloadStore = DownloadStore(application)
    /** 已下载帖子的应用内存档（快照 + 本地图片，供离线按原格式看帖）。 */
    val archiveStore = ThreadArchiveStore(application)
    val httpClient = HttpClient()
    /** 表情表与最近使用表情的本地缓存。 */
    val smileyStore = SmileyStore(application)
    val repository = ForumRepository(httpClient, smileyStore)
    val noticeUnreadStore = NoticeUnreadStore(application)
    /** 后台消息轮询（15 分钟一次）；进应用时也由 [checkNoticesNow] 顺手调一次。 */
    val noticeChecker = NoticeChecker(repository, noticeUnreadStore)

    /** 是否有未读新消息：首页消息图标上的小圆点以此为准。 */
    val unreadNotices: StateFlow<Boolean> =
        noticeUnreadStore.hasUnread.stateIn(appScope, SharingStarted.Eagerly, false)

    /**
     * 当前登录用户的资料；未登录为 null。
     *
     * 登录可能发生在三处：原生登录页、人机验证页里的 WebView（cookie 回灌后由
     * [HttpClient.onSessionChanged] 通知）、以及启动时从持久化会话恢复。三者都
     * 汇到 [refreshCurrentUser]，首页头像与各页资料才拿得到同一份数据。
     */
    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    val checkInManager = CheckInManager(repository, sessionStore, httpClient, currentUser, appScope)

    /** 本地内容缓存：一个 key 一个 JSON 文件，置于 cacheDir（临时性质，系统空间紧张可回收）。 */
    val contentCache = ContentCache(java.io.File(application.cacheDir, "mtf_content"))

    /** 发帖草稿箱：同在 cacheDir 下，「清除缓存」即清掉；不自动清理，只由用户手动删除。 */
    val draftStore = DraftStore(java.io.File(application.cacheDir, "mtf_drafts"))

    /** 本地日志库（filesDir/logs，内部存储，不占缓存）与记录等级的持久化。 */
    val logStore = LogStore(java.io.File(application.filesDir, "logs/mtf_log.jsonl"))
    val logSettings = LogSettingsStore(application)

    /** 应用日志门面：logcat 镜像 + 按等级入库；崩溃路径同步写盘。 */
    val logger = AppLogger(logStore, appScope)

    init {
        httpClient.onSessionChanged = { refreshCurrentUserAsync() }
        httpClient.onLog = { level, tag, message, throwable ->
            logger.log(level, tag, message, throwable)
        }
        // 等级一改立即生效；设置页改完后新日志就按新等级入库
        appScope.launch { logSettings.level.collect { logger.setLevel(it) } }
        appScope.launch {
            // 上一次是崩溃退出的：先把指认放在本次运行日志的最前面，再看下面的崩溃条目
            if (logStore.consumeCrashMarker()) {
                logger.info(AppLogger.TAG_CRASH, "检测到上次运行异常退出，上一条 ERROR 即当时的崩溃日志")
            }
            val saved = sessionStore.cookieHeader.first()
            if (saved.isNotEmpty()) {
                restoreCookies(saved)
                logger.info("Session", "已从磁盘恢复登录会话")
            }
            // 先用上次登录保存的资料上屏（免一次请求的白屏），网络回来再覆盖
            val savedUid = sessionStore.uid.first()
            if (savedUid.isNotEmpty()) {
                _currentUser.value = UserProfile(
                    uid = savedUid,
                    name = sessionStore.username.first().ifEmpty { null },
                    avatarUrl = sessionStore.avatar.first().ifEmpty { null },
                )
            }
            if (httpClient.isLoggedIn()) {
                refreshCurrentUser()
                // 进应用先查一次消息：红点要反映真实未读，不能只等 15 分钟的后台轮询。
                // 失败无所谓，下一轮轮询还会再查；不发通知（用户已经在应用里）。
                appScope.launch {
                    runCatching { noticeChecker.check() }.onFailure { e ->
                        logger.warn("Notice", "进应用时查询消息失败", e)
                    }
                }
            }
            NoticePollWorker.enqueue(appContext)
        }
    }

    /** 看过消息页：把 [ids] 记为已读，红点随之灭掉。 */
    fun markNoticesRead(ids: List<String>) {
        appScope.launch { noticeUnreadStore.markRead(ids) }
        // 看过消息页即没有待看的新消息，通知栏那条提醒随之撤掉（autoCancel 只在点通知时生效）
        NoticeNotifier.cancel(appContext)
    }

    /**
     * 登录态判据：全 App 只认这一份。
     *
     * 不能改回查 cookie 里的 _auth：站点侧会话失效、冷启动时持久化 cookie 已恢复而资料
     * 尚未就绪，cookie 都还在，界面却已按未登录渲染，于是出现「界面显示未登录、操作却放行」。
     * 需要随登录态实时重绘的页面（如他人主页）请直接 collect [currentUser]。
     */
    fun isLoggedIn(): Boolean = currentUser.value?.isLoggedIn == true

    /** 拉取并缓存当前登录用户资料；未登录时清空。失败则保留旧值，不清空已有资料。 */
    suspend fun refreshCurrentUser() {
        if (!httpClient.isLoggedIn()) {
            _currentUser.value = null
            return
        }
        val profile = runCatching { repository.me() }.getOrElse { e ->
            logger.warn("Session", "拉取当前用户资料失败", e)
            // 资料页拉取失败不清登录态：cookie 判据仍是已登录，退回上次落盘的资料先上屏，
            // 否则刚登录成功的一瞬间界面仍按未登录渲染（点赞/评论全被拦）
            cachedProfile()?.let { _currentUser.value = it }
            return
        }
        if (!profile.isLoggedIn) {
            cachedProfile()?.let { _currentUser.value = it } ?: run { _currentUser.value = null }
            return
        }
        _currentUser.value = profile
        // 顺手落盘：WebView 里登录的会话此前只存在于内存，重启即丢
        persistSession(profile.uid.orEmpty(), profile.name.orEmpty(), profile.avatarUrl)
    }

    /** 上次登录落盘的资料；没有（从未登录过）返回 null。 */
    private suspend fun cachedProfile(): UserProfile? {
        val uid = sessionStore.uid.first()
        if (uid.isEmpty()) return null
        return UserProfile(
            uid = uid,
            name = sessionStore.username.first().ifEmpty { null },
            avatarUrl = sessionStore.avatar.first().ifEmpty { null },
        )
    }

    /** 界面/回调用的非挂起版本。 */
    fun refreshCurrentUserAsync() {
        appScope.launch { refreshCurrentUser() }
    }

    /** 将持久化的 Cookie 头写回内存 CookieJar。 */
    private fun restoreCookies(header: String) {
        for (pair in header.split(";")) {
            val eq = pair.indexOf('=')
            if (eq <= 0) continue
            val name = pair.substring(0, eq).trim()
            val value = pair.substring(eq + 1).trim()
            if (name.isEmpty() || value.isEmpty()) continue
            httpClient.cookieStore.setCookie(httpClient.host, name, value)
        }
    }

    /** 登录成功后调用：把当前会话落盘。 */
    fun persistSession(uid: String, username: String, avatar: String?) {
        appScope.launch {
            sessionStore.saveSession(httpClient.cookieHeader(), uid, username, avatar)
        }
    }

    fun logout() {
        httpClient.cookieStore.clear()
        _currentUser.value = null
        logger.info("Session", "已退出登录")
        NoticePollWorker.cancel(appContext)
        appScope.launch { sessionStore.clearSession() }
    }
}
