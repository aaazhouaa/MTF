package com.aazhou.mtforum.data.download

import android.content.Context
import android.net.Uri
import com.aazhou.mtforum.data.export.sanitizeContentHtml
import com.aazhou.mtforum.data.model.ThreadSnapshot
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * 已下载帖子的应用内存档：`filesDir/thread_downloads/<tid>/`。
 *
 * 与网页内容缓存（有 TTL、会被后续访问覆盖）分开，下载副本永久保留、只由用户在
 * 「已下载的帖子」里手动删除，不设任何清理策略。正文里的图片下载到同目录 images/
 * 后，contentHtml 与头像地址改写为本地 file:// 引用，离线也能按正常格式看帖。
 * [archive] 返回「原图地址 -> 本地地址」映射，供保存侧登记宽高比缓存。
 */
class ThreadArchiveStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private fun archiveDir(tid: String): File = File(context.filesDir, "thread_downloads/$tid")

    suspend fun archive(
        tid: String,
        snapshot: ThreadSnapshot,
        images: Map<String, ByteArray>,
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val dir = archiveDir(tid)
        // 重复下载同一 tid：整个目录重写，旧副本不残留
        dir.deleteRecursively()
        val imagesDir = File(dir, "images").apply { mkdirs() }
        val localOf = HashMap<String, String>()
        images.forEach { (url, bytes) ->
            val file = File(imagesDir, imageFileName(url))
            file.writeBytes(bytes)
            localOf[url] = Uri.fromFile(file).toString()
        }
        val localSnapshot = snapshot.copy(
            posts = snapshot.posts.map { post ->
                post.copy(
                    avatarUrl = post.avatarUrl?.let { localOf[it] ?: it },
                    contentHtml = sanitizeContentHtml(post.contentHtml) { localOf[it] },
                )
            },
        )
        File(dir, "snapshot.json").writeText(
            json.encodeToString(ThreadSnapshot.serializer(), localSnapshot),
        )
        localOf
    }

    suspend fun read(tid: String): ThreadSnapshot? = withContext(Dispatchers.IO) {
        val file = File(archiveDir(tid), "snapshot.json")
        if (!file.isFile) return@withContext null
        runCatching {
            json.decodeFromString(ThreadSnapshot.serializer(), file.readText())
        }.getOrNull()
    }

    suspend fun exists(tid: String): Boolean = withContext(Dispatchers.IO) {
        File(archiveDir(tid), "snapshot.json").isFile
    }

    suspend fun delete(tid: String) {
        withContext(Dispatchers.IO) { archiveDir(tid).deleteRecursively() }
    }

    /** 图片文件名：URL 摘要 + 扩展名，同名不同图不会互相覆盖。 */
    private fun imageFileName(url: String): String {
        val ext = url.substringBefore('?').substringBefore('#').substringAfterLast('.', "jpg")
            .take(5).lowercase()
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(20)
        return "$digest.$ext"
    }
}
