package com.aazhou.mtforum.ui.screen.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.data.cache.CacheKeys
import com.aazhou.mtforum.data.cache.ContentCache
import com.aazhou.mtforum.data.model.UserProfile
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.data.repo.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ProfileViewModel(
    private val repository: ForumRepository,
    private val contentCache: ContentCache,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val cacheJson = Json { ignoreUnknownKeys = true }

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            // 有缓存先摆出来，切 tab / 重启不再转圈；过期的也照摆，刷新失败保留旧内容
            val cached = readCache()
            if (cached != null && _profile.value == null) {
                _profile.value = cached
            }
            if (_profile.value == null) {
                _loading.value = true
            }
            val fresh = try {
                repository.me()
            } catch (_: Exception) {
                null
            }
            if (fresh != null) {
                _profile.value = fresh
                saveToCache(fresh)
            } else if (_profile.value == null) {
                _profile.value = UserProfile()
            }
            _loading.value = false
        }
    }

    private suspend fun readCache(): UserProfile? {
        val uid = sessionStore.uid.first()
        if (uid.isBlank()) return null
        return contentCache.read(
            CacheKeys.profile(uid),
            CacheKeys.LIST_TTL,
            allowExpired = true,
        ) {
            cacheJson.decodeFromString<CachedProfile>(it)
        }?.toUserProfile()
    }

    /** 未登录不写缓存：避免把空资料落成下一段的旧数据。 */
    private suspend fun saveToCache(profile: UserProfile) {
        if (!profile.isLoggedIn) return
        contentCache.write(
            key = CacheKeys.profile(profile.uid.orEmpty()),
            ttlMillis = CacheKeys.LIST_TTL,
            value = CachedProfile.from(profile),
            encode = { cacheJson.encodeToString(CachedProfile.serializer(), it) },
        )
    }

    fun loggedIn(): Boolean = repository.isLoggedIn()
}

/** 个人中心缓存内容：只存页面资料字段，formhash / 关注态每次取新鲜的。 */
@Serializable
private data class CachedProfile(
    val uid: String? = null,
    val name: String? = null,
    val avatarUrl: String? = null,
    val level: String? = null,
    val groupName: String? = null,
    val threads: Int? = null,
    val posts: Int? = null,
    val friends: Int? = null,
    val following: Int? = null,
    val followers: Int? = null,
    val credits: Int? = null,
    val gold: Int? = null,
    val onlineTime: String? = null,
) {
    fun toUserProfile(): UserProfile = UserProfile(
        uid = uid,
        name = name,
        avatarUrl = avatarUrl,
        level = level,
        groupName = groupName,
        threads = threads,
        posts = posts,
        friends = friends,
        following = following,
        followers = followers,
        credits = credits,
        gold = gold,
        onlineTime = onlineTime,
    )

    companion object {
        fun from(profile: UserProfile): CachedProfile = CachedProfile(
            uid = profile.uid,
            name = profile.name,
            avatarUrl = profile.avatarUrl,
            level = profile.level,
            groupName = profile.groupName,
            threads = profile.threads,
            posts = profile.posts,
            friends = profile.friends,
            following = profile.following,
            followers = profile.followers,
            credits = profile.credits,
            gold = profile.gold,
            onlineTime = profile.onlineTime,
        )
    }
}
