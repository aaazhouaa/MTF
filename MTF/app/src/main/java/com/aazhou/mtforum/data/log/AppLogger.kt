package com.aazhou.mtforum.data.log

import android.util.Log as AndroidLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** 写 logcat 的镜像函数：可注入，便于单测（容器内 `android.util.Log` 未 mock）。 */
typealias LogcatMirror = (LogLevel, String, String, Throwable?) -> Unit

private val logcatMirror: LogcatMirror = { level, tag, message, throwable ->
    when (level) {
        LogLevel.INFO -> AndroidLog.i(tag, message, throwable)
        LogLevel.WARN -> AndroidLog.w(tag, message, throwable)
        LogLevel.ERROR -> AndroidLog.e(tag, message, throwable)
    }
}

/**
 * 应用日志门面：先镜像到 logcat，再按当前等级决定是否写入本地日志库。
 *
 * 写盘默认异步（[scope] 上 launch），调用方（含主线程）不被 IO 阻塞；
 * 崩溃路径必须用 [flush]，因为进程马上要被系统回收，异步写会随进程一起消失。
 */
class AppLogger(
    private val store: LogStore,
    private val scope: CoroutineScope,
    private val mirror: LogcatMirror = logcatMirror,
) {

    private val _level = MutableStateFlow(LogLevel.INFO)

    /** 当前记录等级：只记录本等级及更严重的日志。 */
    val level: StateFlow<LogLevel> = _level.asStateFlow()

    fun setLevel(level: LogLevel) {
        _level.value = level
    }

    fun log(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
        flush: Boolean = false,
    ) {
        mirror(level, tag, message, throwable)
        if (!_level.value.admits(level)) return
        val detail = throwable?.stackTraceToString()
        if (flush) {
            runBlocking {
                withTimeoutOrNull(FLUSH_TIMEOUT_MS) { store.append(level, tag, message, detail) }
            }
        } else {
            scope.launch { store.append(level, tag, message, detail) }
        }
    }

    fun info(tag: String, message: String) = log(LogLevel.INFO, tag, message)

    fun warn(tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.WARN, tag, message, throwable)

    fun error(tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.ERROR, tag, message, throwable)

    /**
     * 未捕获异常（崩溃/闪退）专用：同步落盘并落下崩溃标记，之后由调用方交回系统默认处理。
     * 写盘限时 [FLUSH_TIMEOUT_MS]：超时就放弃这一条，不拖住系统的崩溃流程。
     */
    fun logCrash(thread: Thread, throwable: Throwable) {
        val type = throwable.javaClass.name
        log(
            level = LogLevel.ERROR,
            tag = TAG_CRASH,
            message = "未捕获异常（线程 ${thread.name}）：$type：${throwable.message}",
            throwable = throwable,
            flush = true,
        )
        runBlocking {
            withTimeoutOrNull(FLUSH_TIMEOUT_MS) { store.markCrashed() }
        }
    }

    companion object {
        /** 崩溃日志的 tag；下次启动的指认提示与列表筛选都认这个值。 */
        const val TAG_CRASH = "Crash"

        /** 同步写盘的上限：崩溃路径专用，超时即放弃。 */
        private const val FLUSH_TIMEOUT_MS = 1000L
    }
}
