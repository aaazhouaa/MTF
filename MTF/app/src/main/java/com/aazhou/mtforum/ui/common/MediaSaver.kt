package com.aazhou.mtforum.ui.common

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.aazhou.mtforum.data.network.HttpClient
import com.aazhou.mtforum.image.forumMediaClient
import okhttp3.Request
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 媒体（图片与视频）的保存、分享公共实现，全屏图片查看器与视频播放器共用。
 *
 * 保存走 MediaStore：API 29+ 无需存储权限，图片落 Pictures/MTF、视频落 Movies/MTF。
 * 视频一律流式落盘，避免整段读进内存触顶 OOM。
 */

private fun imagesDir(): String = "${Environment.DIRECTORY_PICTURES}/MTF"
private fun videosDir(): String = "${Environment.DIRECTORY_MOVIES}/MTF"/** 经会话 Cookie 拉取原图字节（图片体积小，直接进内存）。 */
internal fun downloadBytes(context: Context, url: String): ByteArray =
    context.container().httpClient.getBytes(url, referer = "https://bbs.binmt.cc/")

internal fun mimeOf(url: String): String = when {
    url.contains(".png", true) -> "image/png"
    url.contains(".gif", true) -> "image/gif"
    url.contains(".webp", true) -> "image/webp"
    else -> "image/jpeg"
}

/** 视频统一按 mp4 声明：站点附件以 mp4 为主，容器由扩展名决定而非 mime 头。 */
internal fun videoMimeOf(url: String): String = when {
    url.contains(".webm", true) -> "video/webm"
    url.contains(".mkv", true) -> "video/x-matroska"
    url.contains(".m3u8", true) -> "application/vnd.apple.mpegurl"
    else -> "video/mp4"
}

internal fun fileNameOf(url: String, fallbackExtension: String = "jpg"): String {
    val name = url.substringBefore('?').substringAfterLast('/')
    return if (name.contains('.')) name else "mtf_${System.currentTimeMillis()}.$fallbackExtension"
}

/** 写入系统相册（API 29+ 走 MediaStore，无需存储权限）；失败返回 null。 */
internal fun saveToGallery(context: Context, url: String, bytes: ByteArray): Uri? =
    saveBytes(context, url, bytes, imagesDir(), mimeOf(url))

/**
 * 保存导出的帖子 HTML 到「下载/MTF」目录（API 29+ 无需存储权限）；失败返回 null。
 * 媒体库的 RELATIVE_PATH 会自动创建目录，无需应用自己建文件夹。
 */
internal fun saveDocument(context: Context, fileName: String, bytes: ByteArray): Uri? =
    saveBytes(context, fileName, bytes, "${Environment.DIRECTORY_DOWNLOADS}/MTF", "text/html")

/**
 * 保存导出的文本（日志等）到「下载/MTF」目录（API 29+ 无需存储权限）；失败返回 null。
 *
 * 与 [saveDocument] 同一套写入路径，只是 mime 换成 text/plain：日志文件要能被
 * 文本编辑器直接打开，而不是被当成网页。
 */
internal fun saveTextDocument(context: Context, fileName: String, text: String): Uri? =
    saveBytes(context, fileName, text.toByteArray(), "${Environment.DIRECTORY_DOWNLOADS}/MTF", "text/plain")

/** 删除一个已写入的媒体库文件（用户手动删除已下载帖子时调用）。 */
internal fun deleteDocument(context: Context, uri: Uri?) {
    if (uri == null) return
    runCatching { context.contentResolver.delete(uri, null, null) }
}

private fun saveBytes(
    context: Context,
    url: String,
    bytes: ByteArray,
    relativePath: String,
    mime: String,
): Uri? {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileNameOf(url, mime.substringAfter('/')))
        put(MediaStore.MediaColumns.MIME_TYPE, mime)
        put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Files.getContentUri("external"), values) ?: return null
    return try {
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return null
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        uri
    } catch (_: Exception) {
        resolver.delete(uri, null, null)
        null
    }
}

/** 流式保存视频到相册；返回 null 表示失败（网络或写入异常）。 */
internal fun saveVideoToGallery(context: Context, url: String): Uri? {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileNameOf(url, "mp4"))
        put(MediaStore.MediaColumns.MIME_TYPE, videoMimeOf(url))
        put(MediaStore.MediaColumns.RELATIVE_PATH, videosDir())
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
    val ok = try {
        copyToUri(context, url, uri)
    } catch (_: Exception) {
        false
    }
    if (!ok) {
        resolver.delete(uri, null, null)
        return null
    }
    values.clear()
    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
    resolver.update(uri, values, null, null)
    return uri
}

/** 把媒体字节流拷进已插入的相册项；带 UA / 会话 Cookie / Referer，附件 CDN 才放行。 */
private fun copyToUri(context: Context, url: String, uri: Uri): Boolean {
    val httpClient = context.container().httpClient
    val client = forumMediaClient(
        userAgent = HttpClient.MOBILE_UA,
        cookieHeader = { httpClient.cookieHeader() },
    )
    val request = Request.Builder().url(url).build()
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return false
        val body = response.body ?: return false
        context.contentResolver.openOutputStream(uri)?.use { out ->
            body.byteStream().copyTo(out)
            out.flush()
        } ?: return false
    }
    return true
}

/** 把图片落到 cacheDir，供 FileProvider 分享。 */
internal fun writeCacheFile(context: Context, url: String, bytes: ByteArray): File {
    val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
    return File(dir, fileNameOf(url)).apply { writeBytes(bytes) }
}

/** 把视频流式落到 cacheDir，供 FileProvider 分享；失败返回 null。 */
internal fun writeVideoCacheFile(context: Context, url: String): File? {
    val dir = File(context.cacheDir, "shared_videos").apply { mkdirs() }
    val target = File(dir, fileNameOf(url, "mp4"))
    if (target.exists()) return target
    return try {
        val httpClient = context.container().httpClient
        val client = forumMediaClient(
            userAgent = HttpClient.MOBILE_UA,
            cookieHeader = { httpClient.cookieHeader() },
        )
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            target.outputStream().use { body.byteStream().copyTo(it) }
        }
        target
    } catch (_: Exception) {
        target.delete()
        null
    }
}

internal fun shareImage(context: Context, url: String, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeOf(url)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享"))
}

internal fun shareVideo(context: Context, url: String, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = videoMimeOf(url)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享"))
}

/** 执行一次耗时保存/分享动作，期间显示进度；返回 false 或抛异常时统一提示。 */
internal fun runBusy(
    scope: CoroutineScope,
    setBusy: (Boolean) -> Unit,
    errorMessage: String,
    action: suspend () -> Boolean,
) {
    scope.launch {
        setBusy(true)
        val ok = runCatching { action() }.getOrDefault(false)
        setBusy(false)
        if (!ok) AppToast.show(errorMessage)
    }
}
