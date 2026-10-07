package com.aazhou.mtforum.ui.screen.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.data.cache.CacheKeys
import com.aazhou.mtforum.data.cache.ContentCache
import com.aazhou.mtforum.data.model.ForumPage
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class GuideViewModel(
    private val repository: ForumRepository,
    private val contentCache: ContentCache,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<ForumPage>>(UiState.Loading)
    val state: StateFlow<UiState<ForumPage>> = _state.asStateFlow()

    var page: Int = 1
        private set

    /** 一次性提示（如「新数据获取失败」）；界面展示后调 [consumeActionMessage] 清掉。 */
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun consumeActionMessage() {
        _actionMessage.value = null
    }

    private val cacheJson = Json { ignoreUnknownKeys = true }

    init {
        load(1)
    }

    fun load(target: Int = page) {
        page = target
        viewModelScope.launch {
            // 命中缓存先上屏，不走全屏 Loading；请求回来原地替换
            if (target == 1) {
                val cached = contentCache.read(CacheKeys.guide(), CacheKeys.LIST_TTL) {
                    cacheJson.decodeFromString<ForumPage>(it)
                }
                if (cached != null) {
                    page = cached.page
                    _state.value = UiState.Success(cached)
                } else if (_state.value !is UiState.Success) {
                    _state.value = UiState.Loading
                }
            } else if (_state.value !is UiState.Success) {
                _state.value = UiState.Loading
            }
            _state.value = try {
                val data = repository.guide(target)
                page = target
                contentCache.write(
                    key = CacheKeys.guide(),
                    value = data,
                    encode = { cacheJson.encodeToString(ForumPage.serializer(), it) },
                )
                UiState.Success(data)
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
}
