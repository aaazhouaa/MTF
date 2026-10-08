package com.aazhou.mtforum.data.log

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AppLogger] 的门禁与崩溃路径。
 *
 * logcat 镜像注入空实现：容器内 `android.util.Log` 未 mock，走默认实现会直接抛异常。
 */
class AppLoggerTest {

    private val mirrored = mutableListOf<Triple<LogLevel, String, String>>()

    private val noLogcat: LogcatMirror = { level, tag, message, _ ->
        mirrored.add(Triple(level, tag, message))
    }

    private lateinit var job: Job
    private lateinit var store: LogStore

    private fun tempLogger(): AppLogger {
        val dir = Files.createTempDirectory("mtf_logger_test").toFile()
        store = LogStore(File(dir, "mtf_log.jsonl"))
        job = SupervisorJob()
        return AppLogger(store, CoroutineScope(job + Dispatchers.Default), noLogcat)
    }

    /** 等所有异步写盘的任务结束，再断言落库结果。 */
    private suspend fun settle() {
        job.children.forEach { it.join() }
    }

    @Test
    fun `INFO 等级下各级都入库`() = runBlocking {
        val logger = tempLogger()
        logger.setLevel(LogLevel.INFO)

        logger.error("HttpClient", "GET 失败")
        logger.info("Session", "已恢复会话")
        settle()

        // 落库顺序为旧 → 新，列表接口再倒序
        assertEquals(listOf("GET 失败", "已恢复会话"), store.entries().reversed().map { it.message })
        assertEquals(2, mirrored.size)
    }

    @Test
    fun `ERROR 等级下低等级不入库但镜像照旧`() = runBlocking {
        val logger = tempLogger()
        logger.setLevel(LogLevel.ERROR)

        logger.info("Session", "不该入库")
        logger.warn("HttpClient", "也不该入库")
        logger.error("Crash", "该入库")
        settle()

        // 门禁只拦本地库：logcat 镜像与等级设置无关，否则降级后 logcat 也没得看
        assertEquals(3, mirrored.size)
        assertEquals(listOf("该入库"), store.entries().map { it.message })
    }

    @Test
    fun `崩溃日志同步落盘并落下崩溃标记`() = runBlocking {
        val logger = tempLogger()

        logger.logCrash(Thread("main"), IllegalStateException("boom"))

        // flush = true：方法返回时必然已落盘，不必等调度
        val entries = store.entries()
        assertEquals(1, entries.size)
        assertEquals(LogLevel.ERROR, entries[0].level)
        assertEquals(AppLogger.TAG_CRASH, entries[0].tag)
        assertTrue(entries[0].message.contains("boom"))
        assertTrue(entries[0].detail?.contains("IllegalStateException") == true)
        assertTrue(store.consumeCrashMarker())
        assertFalse(store.consumeCrashMarker())
    }
}
