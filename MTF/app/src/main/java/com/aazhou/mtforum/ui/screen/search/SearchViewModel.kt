package com.aazhou.mtforum.ui.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.data.cache.CacheKeys
import com.aazhou.mtforum.data.cache.ContentCache
import com.aazhou.mtforum.data.model.AtUser
import com.aazhou.mtforum.data.model.Forum
import com.aazhou.mtforum.data.model.SearchResult
import com.aazhou.mtforum.data.model.SearchTab
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class SearchViewModel(
    private val repository: ForumRepository,
    private val contentCache: ContentCache,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<SearchResult>?>(null)
    val state: StateFlow<UiState<SearchResult>?> = _state.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    /** 一次性提示（如「新数据获取失败」）；界面展示后调 [consumeActionMessage] 清掉。 */
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _selectedTab = MutableStateFlow(SearchTab.Thread)
    val selectedTab: StateFlow<SearchTab> = _selectedTab.asStateFlow()

    private val _forumState = MutableStateFlow<UiState<List<Forum>>?>(null)
    val forumState: StateFlow<UiState<List<Forum>>?> = _forumState.asStateFlow()

    private val _userState = MutableStateFlow<UiState<List<AtUser>>?>(null)
    val userState: StateFlow<UiState<List<AtUser>>?> = _userState.asStateFlow()

    fun consumeActionMessage() {
        _actionMessage.value = null
    }

    var keyword: String = ""
        private set
    var page: Int = 1
        private set

    private val cacheJson = Json { ignoreUnknownKeys = true }

    /** 切换分类：已有关键词时按新分类重搜。 */
    fun selectTab(tab: SearchTab) {
        if (_selectedTab.value == tab) return
        _selectedTab.value = tab
        if (keyword.isNotBlank()) submit(keyword)
    }

    /** 按当前分类搜索。 */
    fun submit(keyword: String) {
        when (_selectedTab.value) {
            SearchTab.Thread -> search(keyword)
            SearchTab.Forum -> searchForums(keyword)
            SearchTab.User -> searchUsers(keyword)
        }
    }

    fun search(keyword: String, page: Int = 1) {
        if (keyword.isBlank()) return
        val trimmed = keyword.trim()
        this.keyword = trimmed
        this.page = page
        val targetPage = page
        viewModelScope.launch {
            // 同一关键词的旧结果先摆出来，不走全屏转圈；过期的也照摆，刷新失败保留旧内容
            if (targetPage == 1) {
                val cached = contentCache.read(
                    CacheKeys.search(trimmed),
                    CacheKeys.LIST_TTL,
                    allowExpired = true,
                ) {
                    cacheJson.decodeFromString<SearchResult>(it)
                }
                if (cached != null) _state.value = UiState.Success(cached)
            }
            if (_state.value == null) _state.value = UiState.Loading
            _state.value = try {
                val result = repository.search(trimmed, targetPage)
                this@SearchViewModel.page = targetPage
                saveToCache(result)
                UiState.Success(result)
            } catch (e: Exception) {
                // 有旧结果就留着，只提示这次没拿到新的
                if (_state.value is UiState.Success) {
                    _actionMessage.value = "新数据获取失败"
                    _state.value
                } else {
                    UiState.Error(e.message ?: "搜索失败")
                }
            }
        }
    }

    /** 滑到底部时追加下一页；失败即停止继续请求。 */
    fun loadMore() {
        if (_loadingMore.value) return
        val current = (_state.value as? UiState.Success)?.data ?: return
        if (!current.hasNext) return
        _loadingMore.value = true
        viewModelScope.launch {
            runCatching { repository.search(keyword, page + 1) }
                .onSuccess { next ->
                    page += 1
                    val merged = SearchResult(current.threads + next.threads, next.hasNext)
                    _state.value = UiState.Success(merged)
                    saveToCache(merged)
                }
                .onFailure {
                    _state.value = UiState.Success(current.copy(hasNext = false))
                }
            _loadingMore.value = false
        }
    }

    /** 搜圈子：站点无版块搜索接口，拉全量版块表按名过滤，无分页。 */
    fun searchForums(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) return
        this.keyword = trimmed
        viewModelScope.launch {
            val cached = contentCache.read(
                CacheKeys.searchForum(trimmed),
                CacheKeys.LIST_TTL,
                allowExpired = true,
            ) {
                cacheJson.decodeFromString(ListSerializer(Forum.serializer()), it)
            }
            if (cached != null) _forumState.value = UiState.Success(cached)
            if (_forumState.value == null) _forumState.value = UiState.Loading
            _forumState.value = try {
                val forums = repository.forumGroups().flatMap { it.forums }
                    .filter { it.name.contains(trimmed, ignoreCase = true) }
                saveForumsToCache(trimmed, forums)
                UiState.Success(forums)
            } catch (e: Exception) {
                if (_forumState.value is UiState.Success) {
                    _actionMessage.value = "新数据获取失败"
                    _forumState.value
                } else {
                    UiState.Error(e.message ?: "搜索失败")
                }
            }
        }
    }

    /** 搜用户：站点按用户名包含匹配，无分页。 */
    fun searchUsers(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) return
        this.keyword = trimmed
        viewModelScope.launch {
            val cached = contentCache.read(
                CacheKeys.searchUser(trimmed),
                CacheKeys.LIST_TTL,
                allowExpired = true,
            ) {
                cacheJson.decodeFromString(ListSerializer(AtUser.serializer()), it)
            }
            if (cached != null) _userState.value = UiState.Success(cached)
            if (_userState.value == null) _userState.value = UiState.Loading
            _userState.value = try {
                val users = repository.searchUsers(trimmed)
                saveUsersToCache(trimmed, users)
                UiState.Success(users)
            } catch (e: Exception) {
                if (_userState.value is UiState.Success) {
                    _actionMessage.value = "新数据获取失败"
                    _userState.value
                } else {
                    UiState.Error(e.message ?: "搜索失败")
                }
            }
        }
    }

    private suspend fun saveToCache(result: SearchResult) {
        contentCache.write(
            key = CacheKeys.search(keyword),
            ttlMillis = CacheKeys.LIST_TTL,
            value = result,
            encode = { cacheJson.encodeToString(SearchResult.serializer(), it) },
        )
    }

    private suspend fun saveForumsToCache(keyword: String, forums: List<Forum>) {
        contentCache.write(
            key = CacheKeys.searchForum(keyword),
            ttlMillis = CacheKeys.LIST_TTL,
            value = forums,
            encode = { cacheJson.encodeToString(ListSerializer(Forum.serializer()), it) },
        )
    }

    private suspend fun saveUsersToCache(keyword: String, users: List<AtUser>) {
        contentCache.write(
            key = CacheKeys.searchUser(keyword),
            ttlMillis = CacheKeys.LIST_TTL,
            value = users,
            encode = { cacheJson.encodeToString(ListSerializer(AtUser.serializer()), it) },
        )
    }
}
