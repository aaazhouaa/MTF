package com.aazhou.mtforum

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * 后台消息轮询：周期性拉一次提醒列表，来了新消息就发系统通知。
 *
 * 论坛没有推送服务端，客户端只能自己定时查，所以周期取 WorkManager 的下限 15 分钟
 * （再短系统也会合并）；进应用时另外会顺手查一次，红点不会只等这个周期。
 * 未登录直接返回，不白打请求；登出时由 [cancel] 停掉。
 */
class NoticePollWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? MtfApp)?.container ?: return Result.failure()
        if (!container.httpClient.isLoggedIn()) return Result.success()
        val fresh = try {
            container.noticeChecker.check()
        } catch (e: Exception) {
            // 网络/风控失败交给系统退避重试，不丢这一轮的检测
            return Result.retry()
        }
        if (fresh.isNotEmpty()) NoticeNotifier.show(applicationContext, fresh)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "notice-poll"

        /** KEEP：已排期的任务不受重复调用影响，周期改动要换名字才会生效。 */
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<NoticePollWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
