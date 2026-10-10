package com.aazhou.mtforum.ui.screen.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.data.model.UserProfile
import com.aazhou.mtforum.data.repo.ActionStore
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.data.repo.SessionStore
import com.aazhou.mtforum.ui.common.AppToast
import com.aazhou.mtforum.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UserProfileViewModel(
    val uid: String,
    private val repository: ForumRepository,
    private val sessionStore: SessionStore,
    private val actionStore: ActionStore,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<UserProfile>>(UiState.Loading)
    val state: StateFlow<UiState<UserProfile>> = _state.asStateFlow()

    private val _isOperating = MutableStateFlow(false)
    val isOperating: StateFlow<Boolean> = _isOperating.asStateFlow()

    /** 当前登录者的 UID。 */
    val myUid: StateFlow<String> = sessionStore.uid
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** 是否是自己本人的主页。 */
    val isSelf: StateFlow<Boolean> = combine(sessionStore.uid, MutableStateFlow(uid)) { myUid, targetUid ->
        myUid.isNotBlank() && myUid == targetUid
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 本地覆盖的关注状态（若有），与页面返回的状态融合。 */
    private val _followingOverride = MutableStateFlow<Boolean?>(null)
    val isFollowing: StateFlow<Boolean> = combine(_state, _followingOverride) { profileState, override ->
        if (override != null) return@combine override
        val profile = (profileState as? UiState.Success)?.data
        profile?.isFollowing ?: false
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        load()
    }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = try {
                val profile = repository.userProfile(uid)
                val localFollow = actionStore.following(uid)
                if (localFollow != null) {
                    _followingOverride.value = localFollow
                } else if (profile.isFollowing != null) {
                    _followingOverride.value = profile.isFollowing
                }
                UiState.Success(profile)
            } catch (e: Exception) {
                UiState.Error(e.message ?: "加载失败")
            }
        }
    }

    /** 切换关注状态。 */
    fun toggleFollow() {
        val currentProfile = (_state.value as? UiState.Success)?.data ?: return
        if (_isOperating.value) return
        val currentFollowing = isFollowing.value
        val target = !currentFollowing
        viewModelScope.launch {
            _isOperating.value = true
            try {
                val msg = repository.followUser(uid, currentProfile.formhash, target)
                _followingOverride.value = target
                actionStore.setFollowing(uid, target)
                AppToast.show(msg)
            } catch (e: Exception) {
                AppToast.show(e.message ?: "操作失败")
            } finally {
                _isOperating.value = false
            }
        }
    }

    /** 添加好友。 */
    fun addFriend(note: String, onDone: () -> Unit) {
        val currentProfile = (_state.value as? UiState.Success)?.data ?: return
        val formhash = currentProfile.formhash ?: ""
        if (_isOperating.value) return
        viewModelScope.launch {
            _isOperating.value = true
            try {
                val res = repository.addFriend(uid, formhash, note)
                res.onSuccess { msg ->
                    AppToast.show(msg)
                    onDone()
                }.onFailure { e ->
                    AppToast.show(e.message ?: "加好友失败")
                }
            } finally {
                _isOperating.value = false
            }
        }
    }

    /** 发送私信（聊聊天）。 */
    fun sendPm(message: String, onDone: () -> Unit) {
        val currentProfile = (_state.value as? UiState.Success)?.data ?: return
        val formhash = currentProfile.formhash ?: ""
        if (message.isBlank()) {
            AppToast.show("请输入聊天内容")
            return
        }
        if (_isOperating.value) return
        viewModelScope.launch {
            _isOperating.value = true
            try {
                val res = repository.sendPrivateMessage(uid, formhash, message)
                res.onSuccess { msg ->
                    AppToast.show(msg)
                    onDone()
                }.onFailure { e ->
                    AppToast.show(e.message ?: "发送失败")
                }
            } finally {
                _isOperating.value = false
            }
        }
    }

    /** 拉黑用户。 */
    fun blockUser(onDone: () -> Unit) {
        val currentProfile = (_state.value as? UiState.Success)?.data ?: return
        val username = currentProfile.name.orEmpty()
        val formhash = currentProfile.formhash ?: ""
        if (username.isBlank()) {
            AppToast.show("未能获取用户名，无法拉黑")
            return
        }
        if (_isOperating.value) return
        viewModelScope.launch {
            _isOperating.value = true
            try {
                val res = repository.addUserBlacklist(username, formhash)
                res.onSuccess { msg ->
                    AppToast.show(msg)
                    onDone()
                }.onFailure { e ->
                    AppToast.show(e.message ?: "拉黑失败")
                }
            } finally {
                _isOperating.value = false
            }
        }
    }
}
