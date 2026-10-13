package com.aazhou.mtforum.ui.screen.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.data.repo.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 站点登录失败次数锁定的提示词（实测原话：密码错误次数过多，请 15 分钟后重新登录）。 */
private val LOCK_HINT = Regex("次数过多|分钟后|锁定|禁止登录")

/**
 * 站点的安全提问选项，与登录页 select[name=questionid] 的 value/文案一一对应。
 * 第 0 项表示未设置，提交时等价于不带该字段，其答案输入框无需展示。
 */
val SECURITY_QUESTIONS: List<Pair<Int, String>> = listOf(
    0 to "安全提问(未设置请忽略)",
    1 to "母亲的名字",
    2 to "爷爷的名字",
    3 to "父亲出生的城市",
    4 to "您其中一位老师的名字",
    5 to "您个人计算机的型号",
    6 to "您最喜欢的餐馆名称",
    7 to "驾驶执照最后四位数字",
)

class LoginViewModel(
    private val repository: ForumRepository,
    private val sessionStore: SessionStore,
    /** 会话落盘后通知容器重取当前用户：各页登录态都读容器里那一份，不刷新就还是未登录态。 */
    private val onSessionSaved: () -> Unit = {},
) : ViewModel() {

    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun login(username: String, password: String, questionId: Int = 0, answer: String = "") {
        if (username.isBlank() || password.isBlank()) {
            _state.value = LoginUiState.Error("请输入用户名和密码")
            return
        }
        if (questionId > 0 && answer.isBlank()) {
            _state.value = LoginUiState.Error("请输入安全提问的答案")
            return
        }
        _state.value = LoginUiState.Loading
        viewModelScope.launch {
            try {
                val result = repository.login(username.trim(), password, questionId, answer.trim())
                if (!result.success) {
                    _state.value = LoginUiState.Error(loginErrorMessage(result.message))
                    return@launch
                }
                val profile = repository.me()
                if (!profile.isLoggedIn) {
                    _state.value = LoginUiState.Error("登录未生效，请检查账号密码后重试")
                    return@launch
                }
                sessionStore.saveSession(
                    cookieHeader = repository.sessionCookieHeader(),
                    uid = profile.uid.orEmpty(),
                    username = profile.name.orEmpty(),
                    avatar = profile.avatarUrl,
                )
                onSessionSaved()
                _state.value = LoginUiState.Success
            } catch (e: Exception) {
                _state.value = LoginUiState.Error(e.message ?: "网络错误")
            }
        }
    }

    /**
     * 登录失败文案：站点原话优先，遇到失败次数锁定再附一句成因说明。
     *
     * 站点的 15 分钟锁定是按 IP 记的，客户端换账号、等待都无法立即绕过；这时只把
     * 原话丢给用户容易被当成「密码错」，补一句说明免得反复重试、把锁定时间越拉越长。
     */
    private fun loginErrorMessage(siteMessage: String?): String {
        val raw = siteMessage?.takeIf { it.isNotBlank() } ?: return "登录失败，请重试"
        return if (LOCK_HINT.containsMatchIn(raw)) {
            "$raw（该限制由站点按出口 IP 记录，与账号无关，请稍后再试）"
        } else {
            raw
        }
    }

    fun reset() {
        _state.value = LoginUiState.Idle
    }

    sealed interface LoginUiState {
        data object Idle : LoginUiState
        data object Loading : LoginUiState
        data object Success : LoginUiState
        data class Error(val message: String) : LoginUiState
    }
}
