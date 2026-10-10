package com.aazhou.mtforum.ui.screen.notice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.data.cache.CacheKeys
import com.aazhou.mtforum.data.cache.ContentCache
import com.aazhou.mtforum.data.model.FollowerUser
import com.aazhou.mtforum.data.model.Notice
import com.aazhou.mtforum.data.model.NoticeCategory
import com.aazhou.mtforum.data.model.PrivateMessage
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.ui.common.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Calendar

/** 提醒类列表只保留最新 20 条。 */
private const val NOTICE_LIMIT = 20

/** 页面支持的三种内容承载形式。 */
sealed interface NoticeContentState {
    data class Notices(val items: List<Notice>) : NoticeContentState
    data class PrivateMessages(val items: List<PrivateMessage>) : NoticeContentState
    data class Followers(val items: List<FollowerUser>) : NoticeContentState

    fun isEmpty(): Boolean = when (this) {
        is Notices -> items.isEmpty()
        is PrivateMessages -> items.isEmpty()
        is Followers -> items.isEmpty()
    }
}

class NoticeViewModel(
    private val repository: ForumRepository,
    private val contentCache: ContentCache,
    /** 把已加载的提醒记为已读，首页红点随之灭掉。 */
    private val onRead: (List<String>) -> Unit,
) : ViewModel() {

    private val _category = MutableStateFlow(NoticeCategory.MY_POST)
    val category: StateFlow<NoticeCategory> = _category.asStateFlow()

    private val _state = MutableStateFlow<UiState<NoticeContentState>>(UiState.Loading)
    val state: StateFlow<UiState<NoticeContentState>> = _state.asStateFlow()

    /** 下拉刷新中：与 [state] 独立，刷新期间保留旧列表不清屏。 */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val cacheJson = Json { ignoreUnknownKeys = true }

    /** 内存快照缓存：切回已浏览的 Tab 时秒开。 */
    private val memoryStateCache = mutableMapOf<NoticeCategory, NoticeContentState>()

    /** 当前加载 Job，切换分类时取消前一个。 */
    private var loadJob: Job? = null

    /** 已问过内容的楼层 pid：滚动时重复可见不重复请求。 */
    private val contentAsked = mutableSetOf<String>()

    /** 列表一上来就为其取摘要的条数（约一屏）；其余由滚动可见时再取。 */
    private val eagerContentCount = 6

    /** 已取到的楼层正文摘要：pid -> 纯文本。 */
    private val contentText = mutableMapOf<String, String>()

    init {
        // 初次进入清除未读红点基线
        viewModelScope.launch {
            runCatching { repository.notices() }.getOrNull()?.let { page ->
                onRead(page.notices.map { it.id })
            }
        }
        loadCategory(_category.value)
    }

    /** 切换分类。 */
    fun selectCategory(newCategory: NoticeCategory) {
        if (_category.value == newCategory && _state.value is UiState.Success) return
        _category.value = newCategory
        loadCategory(newCategory)
    }

    /** 加载指定分类数据。 */
    fun loadCategory(cat: NoticeCategory) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // 内存缓存优先
            val mem = memoryStateCache[cat]
            if (mem != null) {
                _state.value = UiState.Success(mem)
            } else {
                // 尝试读磁盘缓存
                val diskCached = readDiskCache(cat)
                if (diskCached != null) {
                    _state.value = UiState.Success(diskCached)
                } else if (_state.value !is UiState.Success) {
                    _state.value = UiState.Loading
                }
            }
            reloadNetwork(cat)
        }
    }

    /** 下拉刷新当前分类。 */
    fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            reloadNetwork(_category.value)
            _refreshing.value = false
        }
    }

    private suspend fun reloadNetwork(cat: NoticeCategory) {
        try {
            when (cat) {
                NoticeCategory.PM -> {
                    val pms = repository.privateMessages()
                    val content = NoticeContentState.PrivateMessages(pms)
                    updateContent(cat, content)
                }
                NoticeCategory.FOLLOWER -> {
                    val followers = repository.followers()
                    val content = NoticeContentState.Followers(followers)
                    updateContent(cat, content)
                }
                NoticeCategory.MY_POST,
                NoticeCategory.INTERACTIVE,
                NoticeCategory.SYSTEM,
                NoticeCategory.APP -> {
                    val view = cat.viewKey ?: "mypost"
                    val page = repository.noticesByView(view)
                    if (page.loggedOut) {
                        _state.value = UiState.Error("请先登录后查看消息提醒")
                        return
                    }
                    val sorted = page.notices.sortedByDescending { noticeRecency(it) }.take(NOTICE_LIMIT)
                    val content = NoticeContentState.Notices(sorted)
                    updateContent(cat, content)
                    republishWithContents()
                    ensureContents(sorted.take(eagerContentCount))
                }
            }
        } catch (e: Exception) {
            if (_state.value !is UiState.Success) {
                _state.value = UiState.Error(e.message ?: "加载失败")
            }
        }
    }

    private suspend fun updateContent(cat: NoticeCategory, content: NoticeContentState) {
        memoryStateCache[cat] = content
        if (_category.value == cat) {
            _state.value = UiState.Success(content)
        }
        writeDiskCache(cat, content)
    }

    private suspend fun readDiskCache(cat: NoticeCategory): NoticeContentState? {
        return when (cat) {
            NoticeCategory.PM -> {
                contentCache.read(CacheKeys.notice(cat.name), CacheKeys.LIST_TTL, allowExpired = true) {
                    NoticeContentState.PrivateMessages(cacheJson.decodeFromString(it))
                }
            }
            NoticeCategory.FOLLOWER -> {
                contentCache.read(CacheKeys.notice(cat.name), CacheKeys.LIST_TTL, allowExpired = true) {
                    NoticeContentState.Followers(cacheJson.decodeFromString(it))
                }
            }
            NoticeCategory.MY_POST,
            NoticeCategory.INTERACTIVE,
            NoticeCategory.SYSTEM,
            NoticeCategory.APP -> {
                contentCache.read(CacheKeys.notice(cat.name), CacheKeys.LIST_TTL, allowExpired = true) {
                    NoticeContentState.Notices(cacheJson.decodeFromString<CachedNotices>(it).notices)
                }
            }
        }
    }

    private suspend fun writeDiskCache(cat: NoticeCategory, content: NoticeContentState) {
        when (content) {
            is NoticeContentState.Notices -> {
                contentCache.write(
                    key = CacheKeys.notice(cat.name),
                    ttlMillis = CacheKeys.LIST_TTL,
                    value = CachedNotices(content.items),
                    encode = { cacheJson.encodeToString(CachedNotices.serializer(), it) },
                )
            }
            is NoticeContentState.PrivateMessages -> {
                contentCache.write(
                    key = CacheKeys.notice(cat.name),
                    ttlMillis = CacheKeys.LIST_TTL,
                    value = content.items,
                    encode = { cacheJson.encodeToString(it) },
                )
            }
            is NoticeContentState.Followers -> {
                contentCache.write(
                    key = CacheKeys.notice(cat.name),
                    ttlMillis = CacheKeys.LIST_TTL,
                    value = content.items,
                    encode = { cacheJson.encodeToString(it) },
                )
            }
        }
    }

    /** 给可见的帖子提醒补上楼层正文摘要。 */
    fun ensureContents(visible: List<Notice>) {
        val pending = visible.filter { !it.pid.isNullOrBlank() && contentAsked.add(it.pid!!) }
        if (pending.isEmpty()) return
        viewModelScope.launch {
            val texts = runCatching {
                repository.noticeReplyTexts(
                    pending.mapNotNull { notice ->
                        val tid = notice.tid ?: return@mapNotNull null
                        val pid = notice.pid ?: return@mapNotNull null
                        tid to pid
                    },
                )
            }.getOrNull().orEmpty()
            if (texts.isEmpty()) return@launch
            contentText.putAll(texts)
            republishWithContents()
        }
    }

    private suspend fun republishWithContents() {
        val current = _state.value as? UiState.Success ?: return
        val noticesContent = current.data as? NoticeContentState.Notices ?: return
        if (contentText.isEmpty()) return
        val merged = noticesContent.items.map { notice ->
            val text = notice.pid?.let { contentText[it] } ?: return@map notice
            if (notice.content == text) notice else notice.copy(content = text)
        }
        if (merged == noticesContent.items) return
        val updated = NoticeContentState.Notices(merged)
        memoryStateCache[_category.value] = updated
        _state.value = UiState.Success(updated)
        writeDiskCache(_category.value, updated)
    }
}

/** 消息提醒的缓存内容。 */
@Serializable
private data class CachedNotices(
    val notices: List<Notice> = emptyList(),
)

/** 站点提醒页实际出现的时间文本：相对时间、相对日词加钟点、绝对日期、当年月日。 */
private val NOTICE_RELATIVE_TIME = Regex("""(\d+)\s*(秒|分钟|小时|天)前""")
private val NOTICE_DAY_WORD_TIME = Regex("""(今天|昨天|前天)(?:\s+(\d{1,2}):(\d{2})(?::(\d{2}))?)?""")
private val NOTICE_ABSOLUTE_TIME = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})(?:\s+(\d{1,2}):(\d{2})(?::(\d{2}))?)?""")
private val NOTICE_MONTH_DAY_TIME = Regex("""(\d{1,2})-(\d{1,2})\s+(\d{1,2}):(\d{2})(?::(\d{2}))?""")

/** 提醒的时间文本 -> 可比较的时间戳。 */
private fun noticeRecency(notice: Notice): Long {
    val raw = notice.timeText?.replace('\u00A0', ' ')?.trim().orEmpty()
    if (raw.isEmpty()) return Long.MIN_VALUE
    NOTICE_RELATIVE_TIME.find(raw)?.let { match ->
        val amount = match.groupValues[1].toLongOrNull() ?: return@let
        val unit = when (match.groupValues[2]) {
            "秒" -> 1_000L
            "分钟" -> 60_000L
            "小时" -> 3_600_000L
            else -> 86_400_000L
        }
        return System.currentTimeMillis() - amount * unit
    }
    val today = Calendar.getInstance()
    NOTICE_DAY_WORD_TIME.find(raw)?.let { match ->
        val calendar = today.clone() as Calendar
        when (match.groupValues[1]) {
            "昨天" -> calendar.add(Calendar.DAY_OF_YEAR, -1)
            "前天" -> calendar.add(Calendar.DAY_OF_YEAR, -2)
        }
        return calendar.atTimeOfDay(match.groupValues[2], match.groupValues[3], match.groupValues[4])
            .timeInMillis
    }
    NOTICE_ABSOLUTE_TIME.find(raw)?.let { match ->
        val calendar = Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, match.groupValues[1].toIntOrNull() ?: return@let)
            set(Calendar.MONTH, (match.groupValues[2].toIntOrNull() ?: return@let) - 1)
            set(Calendar.DAY_OF_MONTH, match.groupValues[3].toIntOrNull() ?: return@let)
        }
        return calendar.atTimeOfDay(
            match.groupValues[4],
            match.groupValues[5],
            match.groupValues.getOrElse(6) { "" },
        ).timeInMillis
    }
    NOTICE_MONTH_DAY_TIME.find(raw)?.let { match ->
        val calendar = Calendar.getInstance().apply {
            clear()
            set(Calendar.MONTH, (match.groupValues[1].toIntOrNull() ?: return@let) - 1)
            set(Calendar.DAY_OF_MONTH, match.groupValues[2].toIntOrNull() ?: return@let)
        }
        return calendar.atTimeOfDay(
            match.groupValues[3],
            match.groupValues[4],
            match.groupValues.getOrElse(5) { "" },
        ).timeInMillis
    }
    return Long.MIN_VALUE
}

private fun Calendar.atTimeOfDay(hour: String, minute: String, second: String): Calendar {
    set(Calendar.HOUR_OF_DAY, hour.toIntOrNull() ?: 0)
    set(Calendar.MINUTE, minute.toIntOrNull() ?: 0)
    set(Calendar.SECOND, second.toIntOrNull() ?: 0)
    set(Calendar.MILLISECOND, 0)
    return this
}
