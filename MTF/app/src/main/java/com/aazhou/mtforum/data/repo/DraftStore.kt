package com.aazhou.mtforum.data.repo

import com.aazhou.mtforum.data.model.PostDraft
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * 发帖草稿箱：一个版块一份草稿，落在 cacheDir 下的单个 JSON 文件里。
 *
 * 只依赖 [File]（不碰 Android Context），故可在 JVM 单测里直接验证。
 * 存放位置是缓存目录而非内部存储：草稿属于可弃数据，「清除缓存」即连同内容缓存一起
 * 清掉，此外不设过期、不参与任何容量淘汰——用户没删就一直在了。
 * 写入走「临时文件 + rename」原子替换，与内容缓存同一套办法：写一半被杀进程也留不下
 * 能解析出半截内容的文件。
 */
class DraftStore(private val dir: File) {

    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(dir, FILE_NAME)

    /** 独立作用域：离开发帖页时才落盘，那一刻组合已解散、viewModelScope 正在取消。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 读-改-写的串行锁。
     * 落盘在 IO 线程池上跑，两次保存（连续离开两个版块的发帖页）若不串行，后一次
     * 会照前一次落盘前的旧列表覆写，前一份草稿就丢了。
     */
    private val writeMutex = Mutex()

    private val _items = MutableStateFlow(readFromDisk())
    val items: StateFlow<List<PostDraft>> = _items.asStateFlow()

    /** 保存（同一 fid 覆盖旧的）；内容为空时由调用方先删，这里不判空。 */
    fun save(draft: PostDraft) {
        scope.launch {
            writeMutex.withLock {
                val merged = _items.value.filter { it.fid != draft.fid } + draft
                _items.value = merged.sortedByDescending { it.savedAt }
                writeToDisk(_items.value)
            }
        }
    }

    /** 按版块删除（用户清空输入后离开即走这条）。 */
    fun remove(fids: List<String>) {
        if (fids.isEmpty()) return
        scope.launch {
            writeMutex.withLock {
                val left = _items.value.filter { it.fid !in fids }
                if (left.size == _items.value.size) return@withLock
                _items.value = left
                writeToDisk(left)
            }
        }
    }

    /** 清空（设置页「清理缓存」与草稿箱里的「全部删除」共用）。 */
    fun clear() {
        scope.launch {
            writeMutex.withLock {
                _items.value = emptyList()
                writeToDisk(emptyList())
            }
        }
    }

    /** 当前占用字节数（设置页的缓存大小统计用）。 */
    fun sizeBytes(): Long = if (file.isFile) file.length() else 0L

    /** 磁盘读一份；文件不存在、内容损坏都按空箱处理，坏文件当场清掉。 */
    private fun readFromDisk(): List<PostDraft> {
        if (!file.isFile) return emptyList()
        val parsed = runCatching { json.decodeFromString<List<PostDraft>>(file.readText()) }.getOrNull()
        if (parsed == null) {
            file.delete()
            return emptyList()
        }
        return parsed.sortedByDescending { it.savedAt }
    }

    private fun writeToDisk(items: List<PostDraft>) {
        runCatching {
            dir.mkdirs()
            val tmp = File(dir, "$FILE_NAME$TMP_SUFFIX")
            tmp.writeText(json.encodeToString(items))
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }
    }

    private companion object {
        const val FILE_NAME = "post_drafts.json"
        const val TMP_SUFFIX = ".tmp"
    }
}
