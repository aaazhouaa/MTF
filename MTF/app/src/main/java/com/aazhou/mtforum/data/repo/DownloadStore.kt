package com.aazhou.mtforum.data.repo

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aazhou.mtforum.data.model.DownloadedThread
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.downloadDataStore by preferencesDataStore(name = "mtf_downloads")

/**
 * 已下载帖子的登记表（tid -> 保存产物）。
 *
 * 只登记、不设上限、不自动清理：条目随用户在「已下载的帖子」里手动删除而移除，
 * 删除时连同媒体库里的外置 HTML 文件一起删。重复下载同一 tid 覆盖旧登记。
 */
class DownloadStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("threads")

    val items: Flow<List<DownloadedThread>> =
        context.downloadDataStore.data.map { prefs ->
            val raw = prefs[key] ?: return@map emptyList()
            runCatching { json.decodeFromString<List<DownloadedThread>>(raw) }
                .getOrDefault(emptyList())
                .sortedByDescending { it.savedAt }
        }

    /** 登记一次下载；同一 tid 重复下载时先删掉旧条目指向的外置文件，避免残留两份。 */
    suspend fun upsert(entry: DownloadedThread) {
        val previous = currentItems().firstOrNull { it.tid == entry.tid }
        previous?.let { deleteDocumentFile(it.uri) }
        context.downloadDataStore.edit { prefs ->
            val list = prefs[key]
                ?.let { raw -> runCatching { json.decodeFromString<List<DownloadedThread>>(raw) }.getOrNull() }
                .orEmpty()
                .filter { it.tid != entry.tid } + entry
            prefs[key] = json.encodeToString(list)
        }
    }

    /** 手动删除：删登记条目及其外置 HTML 文件（应用存档由调用方一并清理）。 */
    suspend fun remove(tid: String, onEntryGone: suspend () -> Unit = {}) {
        val entry = currentItems().firstOrNull { it.tid == tid }
        if (entry != null) deleteDocumentFile(entry.uri)
        context.downloadDataStore.edit { prefs ->
            val list = prefs[key]
                ?.let { raw -> runCatching { json.decodeFromString<List<DownloadedThread>>(raw) }.getOrNull() }
                .orEmpty()
                .filter { it.tid != tid }
            prefs[key] = json.encodeToString(list)
        }
        onEntryGone()
    }

    private suspend fun currentItems(): List<DownloadedThread> {
        val raw = context.downloadDataStore.data.map { it[key] }.first()
        return raw?.let { runCatching { json.decodeFromString<List<DownloadedThread>>(it) }.getOrNull() }
            .orEmpty()
    }

    private fun deleteDocumentFile(uriString: String) {
        runCatching {
            context.contentResolver.delete(Uri.parse(uriString), null, null)
        }
    }
}
