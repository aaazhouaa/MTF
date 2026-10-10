package com.aazhou.mtforum.ui.screen.notice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.data.cache.CacheKeys
import com.aazhou.mtforum.data.cache.ContentCache
import com.aazhou.mtforum.data.model.Notice
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Calendar

/** 只保留最新 10 条：页面按可见范围逐条拉楼层摘要，条数越少请求越少。 */
private const val NOTICE_LIMIT = 10

class NoticeViewModel(
    private val repository: ForumRepository,
    private val contentCache: ContentCache,
    /** 列表刷 Success 后回调：把这一批提醒记为已读，首页红点随之灭掉。 */
    private val onRead: (List<String>) -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<Notice>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Notice>>> = _state.asStateFlow()

    /** 下拉刷新中：与 [state] 独立，刷新期间保留旧列表不清屏。 */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val cacheJson = Json { ignoreUnknownKeys = true }

    /** 已问过内容的楼层 pid：滚动时重复可见不重复请求。 */
    private val contentAsked = mutableSetOf<String>()

    /** 列表一上来就为其取摘要的条数（约一屏）；其余由滚动可见时再取。 */
    private val eagerContentCount = 6

    /** 已取到的楼层正文摘要：pid -> 纯文本。 */
    private val contentText = mutableMapOf<String, String>()

    init {
        load()
    }

    /**
     * 进页面与失败重取的公共入口：有缓存先整份上屏，网络回来原地替换。
     *
     * 与首页/详情同一套做法——断网或风控时也看得见上次的提醒，不必对着一张失败页。
     */
    fun load() {
        viewModelScope.launch {
            val cached = contentCache.read(
                CacheKeys.notice(),
                CacheKeys.LIST_TTL,
                allowExpired = true,
            ) {
                cacheJson.decodeFromString<CachedNotices>(it).notices
            }
            if (cached != null) {
                _state.value = UiState.Success(cached)
            } else if (_state.value !is UiState.Success) {
                _state.value = UiState.Loading
            }
            reload()
        }
    }

    /**
     * 下拉刷新：不动 [state]，成功就原地替换列表，失败保留旧内容。
     *
     * 不复用 [load]：那会在没有缓存时把 state 置回 Loading，整页变成全屏转圈，
     * 下拉指示器也会随列表一起被撤掉。
     */
    fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            reload()
            _refreshing.value = false
        }
    }

    private suspend fun reload() {
        val page = try {
            repository.notices()
        } catch (e: Exception) {
            // 有缓存就留着旧内容（只当这次没刷新成功），没有缓存才整页转失败
            if (_state.value !is UiState.Success) {
                _state.value = UiState.Error(e.message ?: "加载失败")
            }
            return
        }
        // 四类提醒合并的结果不按时间排序，取最新几条上屏；未读基线仍用全量 id，
        // 否则没上屏的那部分下次轮询会被当成新消息、红点反复亮
        val latest = page.notices.sortedByDescending { noticeRecency(it) }.take(NOTICE_LIMIT)
        _state.value = when {
            page.loggedOut -> UiState.Error("请先登录后查看消息提醒")
            // 空列表不落盘：解析失配返回空时不能把上一份好数据覆盖掉
            page.notices.isEmpty() -> UiState.Success(emptyList())
            else -> UiState.Success(latest)
        }
        val loaded = (_state.value as? UiState.Success)?.data.orEmpty()
        // 新列表没有摘要：先把本次会话已知的摘要并回去，再连缓存一起存——
        // 否则刷新一次（或重进页面）内容就整块消失，问过的 pid 也不会再问
        republishWithContents()
        saveToCache((_state.value as? UiState.Success)?.data ?: loaded)
        // 打开/刷新消息页就算看过：以这一批提醒为已读基线
        onRead(page.notices.map { it.id })
        // 首屏几条的摘要立刻问：不依赖列表布局回调，进页面就能看见
        ensureContents(loaded.take(eagerContentCount))
    }

    private suspend fun saveToCache(notices: List<Notice>) {
        contentCache.write(
            key = CacheKeys.notice(),
            ttlMillis = CacheKeys.LIST_TTL,
            value = CachedNotices(notices),
            encode = { cacheJson.encodeToString(CachedNotices.serializer(), it) },
        )
    }

    /**
     * 给可见的提醒补上楼层正文摘要。
     *
     * 站点提醒页只给「谁 回复了您的帖子 标题」，没有回复内容；要显示内容得按
     * 楼层链接找到所在页再摘出该楼层。故按需加载：只问当前可见的几条，同一页
     * 只取一次（同页的多条提醒共用），问过的 pid 不再重复问。
     */
    fun ensureContents(visible: List<Notice>) {
        val pending = visible.filter { !it.pid.isNullOrBlank() && contentAsked.add(it.pid!!) }
        if (pending.isEmpty()) return
        viewModelScope.launch {
            // 一次问一批：仓库里按 (tid, page) 归并，同页的多条提醒只取一次页面
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

    /** 把已取到的摘要并回列表，逐条到达逐条上屏；并顺带落盘，下次进页面直接带摘要。 */
    private suspend fun republishWithContents() {
        val current = _state.value as? UiState.Success ?: return
        if (contentText.isEmpty()) return
        val merged = current.data.map { notice ->
            val text = notice.pid?.let { contentText[it] } ?: return@map notice
            if (notice.content == text) notice else notice.copy(content = text)
        }
        if (merged == current.data) return
        _state.value = UiState.Success(merged)
        saveToCache(merged)
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

/**
 * 提醒的时间文本 -> 可比较的时间戳，用于按新到旧排序。
 *
 * 解析不出时返回 [Long.MIN_VALUE] 排到最后；[sortedByDescending] 是稳定排序，
 * 同戳条目保持站点原序。
 */
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
        // 站点只写月日：年份按当年算
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

/** 把时、分、秒写进日历，缺项与解析不出的一律按 0 计。 */
private fun Calendar.atTimeOfDay(hour: String, minute: String, second: String): Calendar {
    set(Calendar.HOUR_OF_DAY, hour.toIntOrNull() ?: 0)
    set(Calendar.MINUTE, minute.toIntOrNull() ?: 0)
    set(Calendar.SECOND, second.toIntOrNull() ?: 0)
    set(Calendar.MILLISECOND, 0)
    return this
}
