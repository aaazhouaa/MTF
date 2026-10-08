package com.aazhou.mtforum.data.log

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.Serializable

/**
 * 日志等级：按严重程度递增。
 *
 * 等级设置的含义是「只记录本等级及更严重的日志」：设为 WARN 时 INFO 不再入库，
 * WARN 与 ERROR 照记。比较一律走 [admits]，不要用 ordinal 裸比。
 */
enum class LogLevel(val label: String) {
    INFO("Info"),
    WARN("Warn"),
    ERROR("Error"),
    ;

    /** [entry] 是否达到本等级的严重程度（同级也算）。 */
    fun admits(entry: LogLevel): Boolean = entry.ordinal >= ordinal
}

/**
 * 一条本地日志。
 *
 * [id] 由 [LogStore] 分配（当前最大 id + 1），重启后继续递增，供列表与详情页定位；
 * [detail] 放异常堆栈等附加信息，可为空。
 */
@Serializable
data class LogEntry(
    val id: Long,
    val time: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val detail: String? = null,
)

private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

/** 单条日志的可读文本：导出与详情页共用同一格式。 */
fun LogEntry.toText(): String = buildString {
    append('[').append(timeFormat.format(Date(time))).append("] ")
    append('[').append(level.name).append("] ")
    append('[').append(tag).append("] ")
    append(message)
    detail?.takeIf { it.isNotBlank() }?.let {
        append('\n').append(it)
    }
}

/**
 * 一批日志的导出文本：按时间正序，头部注明版本与记录等级。
 *
 * 版本与等级由调用方传入：数据层不碰 BuildConfig，导出的文件才自解释。
 */
fun List<LogEntry>.toExportText(versionName: String, level: LogLevel): String = buildString {
    append("MTF v").append(versionName).append(" 日志导出\n")
    append("记录等级：").append(level.label)
        .append("（仅记录该等级及更严重的日志），共 ").append(size).append(" 条\n")
    append("--------------------------------------------------\n")
    this@toExportText.sortedBy { it.time }.forEach { entry ->
        append(entry.toText()).append('\n')
    }
}
