package com.aazhou.mtforum.ui.screen.notice

import com.aazhou.mtforum.data.model.Notice
import com.aazhou.mtforum.data.model.PrivateMessage
import kotlinx.serialization.Serializable
import java.util.Calendar

/** 消息页支持的两种内容承载形式。 */
sealed interface NoticeContentState {
    data class Notices(
        val items: List<Notice>,
        /** 已加载到的页码；从 1 开始。 */
        val page: Int = 1,
        val hasNext: Boolean = false,
    ) : NoticeContentState

    data class PrivateMessages(val items: List<PrivateMessage>) : NoticeContentState

    fun isEmpty(): Boolean = when (this) {
        is Notices -> items.isEmpty()
        is PrivateMessages -> items.isEmpty()
    }
}

/** 该分类用于未读判据的 id：站点提醒 id，私信用会话 key（与列表 items 的 key 一致）。 */
fun NoticeContentState.noticeIds(): List<String> = when (this) {
    is NoticeContentState.Notices -> items.map { it.id }
    is NoticeContentState.PrivateMessages -> items.map { it.plid ?: it.touid ?: it.username }
}

/** 消息提醒的缓存内容。 */
@Serializable
data class CachedNotices(
    val notices: List<Notice> = emptyList(),
    val page: Int = 1,
    val hasNext: Boolean = false,
)

/** 站点提醒页实际出现的时间文本：相对时间、相对日词加钟点、绝对日期、当年月日。 */
private val NOTICE_RELATIVE_TIME = Regex("""(\d+)\s*(秒|分钟|小时|天)前""")
private val NOTICE_DAY_WORD_TIME = Regex("""(今天|昨天|前天)(?:\s+(\d{1,2}):(\d{2})(?::(\d{2}))?)?""")
private val NOTICE_ABSOLUTE_TIME =
    Regex("""(\d{4})-(\d{1,2})-(\d{1,2})(?:\s+(\d{1,2}):(\d{2})(?::(\d{2}))?)?""")
private val NOTICE_MONTH_DAY_TIME =
    Regex("""(\d{1,2})-(\d{1,2})\s+(\d{1,2}):(\d{2})(?::(\d{2}))?""")

/**
 * 站点的时间文本 -> 可比较的时间戳；解析不出为 [Long.MIN_VALUE]。
 *
 * 私人消息与三类提醒共用：站点给的时间文本形态一致（相对时间、日词、绝对日期），
 * 列表要按它倒序排。
 */
internal fun recencyOf(timeText: String?): Long {
    val raw = timeText?.replace('\u00A0', ' ')?.trim().orEmpty()
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
