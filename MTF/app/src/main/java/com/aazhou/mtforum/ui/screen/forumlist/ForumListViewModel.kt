package com.aazhou.mtforum.ui.screen.forumlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.data.cache.CacheKeys
import com.aazhou.mtforum.data.cache.ContentCache
import com.aazhou.mtforum.data.model.ForumGroup
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class ForumListViewModel(
    private val repository: ForumRepository,
    private val contentCache: ContentCache,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<ForumGroup>>>(UiState.Loading)
    val state: StateFlow<UiState<List<ForumGroup>>> = _state.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** 一次性提示（如「新数据获取失败」）；界面展示后调 [consumeActionMessage] 清掉。 */
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun consumeActionMessage() {
        _actionMessage.value = null
    }

    private val cacheJson = Json { ignoreUnknownKeys = true }

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            // 有缓存先摆出来，不走全屏转圈；过期的也照摆，刷新失败保留旧内容
            val cached = contentCache.read(
                CacheKeys.forumList(),
                CacheKeys.LIST_TTL,
                allowExpired = true,
            ) {
                cacheJson.decodeFromString<List<ForumGroup>>(it)
            }
            if (cached != null) {
                _state.value = UiState.Success(cached)
            } else if (_state.value !is UiState.Success) {
                _state.value = UiState.Loading
            }
            _state.value = try {
                val groups = repository.forumGroups()
                saveToCache(groups)
                UiState.Success(groups)
            } catch (e: Exception) {
                if (_state.value is UiState.Success) {
                    _actionMessage.value = "新数据获取失败"
                    _state.value
                } else {
                    UiState.Error(e.message ?: "加载失败")
                }
            }
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                val groups = repository.forumGroups()
                _state.value = UiState.Success(groups)
                saveToCache(groups)
            } catch (e: Exception) {
                if (_state.value !is UiState.Success) {
                    _state.value = UiState.Error(e.message ?: "刷新失败")
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private suspend fun saveToCache(groups: List<ForumGroup>) {
        contentCache.write(
            key = CacheKeys.forumList(),
            ttlMillis = CacheKeys.LIST_TTL,
            value = groups,
            encode = { cacheJson.encodeToString(it) },
        )
    }
}
