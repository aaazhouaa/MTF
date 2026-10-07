package com.aazhou.mtforum.ui.common

import android.content.Context
import android.graphics.BitmapFactory
import com.aazhou.mtforum.data.download.ThreadArchiveStore
import com.aazhou.mtforum.data.export.buildThreadHtml
import com.aazhou.mtforum.data.export.collectContentImageUrls
import com.aazhou.mtforum.data.export.dataUriOf
import com.aazhou.mtforum.data.model.DownloadedThread
import com.aazhou.mtforum.data.model.ThreadPage
import com.aazhou.mtforum.data.model.ThreadSnapshot
import com.aazhou.mtforum.data.network.HttpClient
import com.aazhou.mtforum.data.repo.DownloadStore
import com.aazhou.mtforum.image.forumMediaClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** 下载并发上限：站点媒体走 CDN，少量并发即可，避免抢占正常浏览的图片请求。 */
private const val DOWNLOAD_PARALLELISM = 4

/** 外置文件名的标题段最长长度（文件系统与文件管理器对长名都不友好）。 */
private const val FILE_NAME_SUBJECT_LIMIT = 40

/**
 * 把一页帖子完整保存下来：图片字节全部取回后，内置存档与「下载/MTF」的 HTML 同时产出。
 *
 * - 内置存档（`ThreadArchiveStore`）：正文与头像改写为本地 file:// 引用，供应用内按
 *   原格式离线看帖；永久保留，只由用户手动删除。
 * - 外置 HTML（`MediaStore` Download/MTF）：图片以 base64 内嵌，单文件可离线浏览、
 *   可另发给别人。
 *
 * 任一图片取不到都不阻断保存：该图保留原地址（联网时能看，离线显示占位底）。
 * 返回 null 表示整体失败（网络全断或媒体库写入失败）。
 */
internal suspend fun saveThreadDownload(
    context: Context,
    page: ThreadPage,
    downloadStore: DownloadStore,
    archiveStore: ThreadArchiveStore,
): DownloadedThread? {
    val images = downloadImageBytes(context, collectImageUrls(page))
    val localOf = archiveStore.archive(
        tid = page.tid,
        snapshot = page.toSnapshot(),
        images = images,
    )
    // 本地图的宽高比直接写进缓存：离线页首次组合即按真实比例测量
    val html = buildThreadHtml(page) { url -> images[url]?.let { dataUriOf(url, it) } }
    val fileName = "MTF-${page.tid}-${safeFileName(page.subject)}.html"
    val uri = saveDocument(context, fileName, html.toByteArray()) ?: return null
    val entry = DownloadedThread(
        tid = page.tid,
        subject = page.subject?.takeIf { it.isNotBlank() } ?: "MTF 帖子",
        forumName = page.forumName,
        fileName = fileName,
        uri = uri.toString(),
        savedAt = System.currentTimeMillis(),
        replyCount = page.posts.count { !it.isFirstPost },
        sizeBytes = html.length.toLong(),
    )
    downloadStore.upsert(entry)
    return entry
}

/** 全帖图片与头像地址（去重、保序）。 */
private fun collectImageUrls(page: ThreadPage): List<String> {
    val urls = LinkedHashSet<String>()
    page.posts.forEach { post ->
        post.avatarUrl?.takeIf { it.startsWith("http") }?.let(urls::add)
        collectContentImageUrls(post.contentHtml).forEach(urls::add)
    }
    return urls.toList()
}

/** 带 UA 与会话 Cookie 并发取图；单个失败只跳过该图。 */
private suspend fun downloadImageBytes(
    context: Context,
    urls: List<String>,
): Map<String, ByteArray> {
    if (urls.isEmpty()) return emptyMap()
    val client = mediaClient(context)
    val result = LinkedHashMap<String, ByteArray>()
    for (batch in urls.chunked(DOWNLOAD_PARALLELISM)) {
        val fetched = coroutineScope {
            batch.map { url -> async(Dispatchers.IO) { url to fetch(client, url) } }.awaitAll()
        }
        fetched.forEach { (url, bytes) -> if (bytes != null) result[url] = bytes }
    }
    return result
}

private fun mediaClient(context: Context): OkHttpClient {
    val httpClient = context.container().httpClient
    return forumMediaClient(
        userAgent = HttpClient.MOBILE_UA,
        cookieHeader = { httpClient.cookieHeader() },
    )
}

private fun fetch(client: OkHttpClient, url: String): ByteArray? =
    runCatching {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return@use null
            response.body?.bytes()?.takeIf { it.isNotEmpty() }
        }
    }.getOrNull()

/** 文件名安全化：去掉文件系统保留字符与首尾空白，超长截断。 */
private fun safeFileName(subject: String?): String {
    val cleaned = subject.orEmpty()
        .replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "")
        .trim()
        .take(FILE_NAME_SUBJECT_LIMIT)
    return cleaned.ifBlank { "thread" }
}

/** 存档用的快照：帖子级字段 + 全量楼层；会话态不进存档（下载副本只读，不需要令牌）。 */
private fun ThreadPage.toSnapshot(): ThreadSnapshot = ThreadSnapshot(
    subject = subject,
    forumName = forumName,
    opUid = opUid,
    fid = fid,
    totalReplies = totalReplies,
    posts = posts,
    progress = emptyList(),
)

/** 删除一条已下载帖子：外置文件由注册表清理，应用存档一并删掉。 */
internal suspend fun deleteThreadDownload(
    tid: String,
    downloadStore: DownloadStore,
    archiveStore: ThreadArchiveStore,
) {
    downloadStore.remove(tid)
    archiveStore.delete(tid)
}

