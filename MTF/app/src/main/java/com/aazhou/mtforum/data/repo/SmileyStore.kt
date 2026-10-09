package com.aazhou.mtforum.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aazhou.mtforum.data.parser.SmileyPack
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.smileyDataStore by preferencesDataStore(name = "mtf_smileys")

/** 最近使用列表的上限：再多也不会被翻到，只占存储。 */
private const val RECENT_LIMIT = 30

/**
 * 本地留存的站点笑脸表：[etag] 为 null 表示拉取时站点没给校验值，下次只能整份重拉。
 */
data class CachedSmileys(
    val packs: List<SmileyPack>,
    val checkedAt: Long,
    val etag: String?,
)

/**
 * 表情数据的本地缓存与最近使用记录。
 *
 * 站点把笑脸表放在 CDN 的一个 JS 文件里，内容不常变但站点确实会加表情：整份缓存到本地，
 * 由调用方每隔一段带 etag 回站确认一次——没变只记时间，变了才换新。最近用过的表情码只
 * 存在本地，与表情表同库，清理缓存时一并清掉。
 */
class SmileyStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val packsKey = stringPreferencesKey("packs")
    private val checkedAtKey = longPreferencesKey("checked_at")
    private val etagKey = stringPreferencesKey("etag")
    private val recentKey = stringPreferencesKey("recent")

    /** 最近用过的表情码，最新在前。 */
    val recent: Flow<List<String>> = context.smileyDataStore.data.map { prefs ->
        val raw = prefs[recentKey] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
    }

    /** 本地缓存的表；没有（首次安装或刚清理过）返回 null。内容损坏也按没有处理。 */
    suspend fun packs(): CachedSmileys? {
        val prefs = context.smileyDataStore.data.first()
        val raw = prefs[packsKey] ?: return null
        val packs = runCatching { json.decodeFromString<List<SmileyPack>>(raw) }.getOrNull() ?: return null
        return CachedSmileys(packs, prefs[checkedAtKey] ?: 0L, prefs[etagKey])
    }

    /** 存入新拉的整份表，同时记下这次拉取的时间。 */
    suspend fun savePacks(packs: List<SmileyPack>, etag: String?) {
        context.smileyDataStore.edit { prefs ->
            prefs[packsKey] = json.encodeToString(packs)
            prefs[checkedAtKey] = System.currentTimeMillis()
            if (etag.isNullOrEmpty()) prefs.remove(etagKey) else prefs[etagKey] = etag
        }
    }

    /** 站点回 304「内容没变」：只推进确认时间，表的内容原样留着。 */
    suspend fun markChecked() {
        context.smileyDataStore.edit { it[checkedAtKey] = System.currentTimeMillis() }
    }

    /** 记一次使用：去重置顶，超上限的从队尾挤掉。 */
    suspend fun addRecent(code: String) {
        if (code.isBlank()) return
        context.smileyDataStore.edit { prefs ->
            val list = prefs[recentKey]
                ?.let { raw -> runCatching { json.decodeFromString<List<String>>(raw) }.getOrNull() }
                .orEmpty()
            val updated = (listOf(code) + list.filter { it != code }).take(RECENT_LIMIT)
            prefs[recentKey] = json.encodeToString(updated)
        }
    }

    /** 清空表情表与最近使用记录（设置页「清理缓存」）。 */
    suspend fun clear() {
        context.smileyDataStore.edit { it.clear() }
    }

    /** 缓存占用的估算值：各键序列化后的长度相加，不去摸 DataStore 的内部文件。 */
    suspend fun approximateBytes(): Long = context.smileyDataStore.data.first().let { prefs ->
        var total = 0L
        prefs.asMap().forEach { (_, value) ->
            total += when (value) {
                is String -> value.length.toLong()
                is Long -> 8L
                else -> 0L
            }
        }
        total
    }
}
