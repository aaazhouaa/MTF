package com.aazhou.mtforum.data.repo

import com.aazhou.mtforum.data.model.UserProfile
import com.aazhou.mtforum.data.network.HttpClient
import com.aazhou.mtforum.ui.common.AppToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 签到管理器：管理每日签到状态与签到请求。
 *
 * 严格防并发：采用 Mutex 互斥锁，严禁任何并行请求，避免触发论坛风控导致账号风险。
 * 本地记录当天签到状态（按 uid + 日期），已签到直接命中本地缓存，避免发起多余网络请求。
 */
class CheckInManager(
    private val repository: ForumRepository,
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
    private val currentUser: StateFlow<UserProfile?>,
    private val scope: CoroutineScope,
) {
    /** k_misign 签到抓取：签到页 formhash 的形态与通用页面不同，单独走这里。 */
    private val checkInRepo = CheckInRepo(httpClient)

    private val mutex = Mutex()

    private val _hasSigned = MutableStateFlow(false)
    val hasSigned: StateFlow<Boolean> = _hasSigned.asStateFlow()

    private val _isCheckingIn = MutableStateFlow(false)
    val isCheckingIn: StateFlow<Boolean> = _isCheckingIn.asStateFlow()

    init {
        scope.launch {
            currentUser.collect { user ->
                val uid = user?.uid
                if (uid.isNullOrEmpty() || uid == "0") {
                    _hasSigned.value = false
                } else {
                    val today = todayDateString()
                    val savedDate = sessionStore.checkInDate(uid).first()
                    if (savedDate == today) {
                        _hasSigned.value = true
                    } else {
                        checkStatusSilently(uid)
                    }
                }
            }
        }
    }

    /** 检查签到状态（只读查询，严格互斥防并发，若拿不到锁或已记录则跳过）。 */
    fun checkStatusSilently(targetUid: String? = currentUser.value?.uid) {
        val uid = targetUid ?: return
        if (uid.isEmpty() || uid == "0" || !httpClient.isLoggedIn()) return
        val today = todayDateString()

        scope.launch {
            val savedDate = sessionStore.checkInDate(uid).first()
            if (savedDate == today) {
                _hasSigned.value = true
                return@launch
            }
            if (!mutex.tryLock()) return@launch
            try {
                val status = checkInRepo.status()
                if (status.hasSigned) {
                    _hasSigned.value = true
                    sessionStore.saveCheckInDate(uid, today)
                }
            } catch (_: Exception) {
                // 静默失败
            } finally {
                mutex.unlock()
            }
        }
    }

    /** 用户点击签到：严格串行执行，防并发、防重复点击。 */
    fun performCheckIn() {
        if (!httpClient.isLoggedIn()) {
            AppToast.show("请先登录")
            return
        }
        val uid = currentUser.value?.uid
        if (uid.isNullOrEmpty() || uid == "0") {
            AppToast.show("请先登录")
            return
        }
        if (_hasSigned.value) {
            AppToast.show("今日已签到")
            return
        }
        if (_isCheckingIn.value) {
            return
        }
        if (!mutex.tryLock()) {
            return
        }

        scope.launch {
            _isCheckingIn.value = true
            try {
                val today = todayDateString()
                // 步骤 1：先拉取签到页面获取状态与 formhash
                val status = checkInRepo.status()
                if (status.hasSigned) {
                    _hasSigned.value = true
                    sessionStore.saveCheckInDate(uid, today)
                    AppToast.show("今日已签到")
                    return@launch
                }
                val formhash = status.formhash
                if (formhash.isNullOrBlank()) {
                    AppToast.show("获取签到令牌失败，请稍后重试")
                    return@launch
                }

                // 步骤 2：安全间隔，串行等待 500ms 避免快速连续请求
                delay(500)

                // 步骤 3：串行发送签到请求
                val result = checkInRepo.sign(formhash)
                if (result.success) {
                    _hasSigned.value = true
                    sessionStore.saveCheckInDate(uid, today)
                    AppToast.show(result.message)
                } else {
                    AppToast.show(result.message)
                }
            } catch (e: Exception) {
                AppToast.show("签到失败，请稍后重试")
            } finally {
                _isCheckingIn.value = false
                mutex.unlock()
            }
        }
    }

    companion object {
        fun todayDateString(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
            sdf.timeZone = TimeZone.getTimeZone("GMT+8")
            return sdf.format(Date())
        }
    }
}
