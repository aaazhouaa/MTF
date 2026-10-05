package com.aazhou.mtforum.ui.screen.verify

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.aazhou.mtforum.data.network.HttpClient
import com.aazhou.mtforum.data.network.WafSolver

/**
 * WAF 真人验证兜底页：本地求解失配时打开站点，让站点的 JS 自身写入
 * acw_sc__v2 cookie，再把它回灌给原生会话。
 *
 * UA 必须与 [HttpClient.MOBILE_UA] 一致，否则站点的 clearance cookie 会与
 * 后续原生请求的 UA 指纹不匹配而失效。
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WafVerificationScreen(url: String, onFinished: (String?) -> Unit) {
    val reported = remember { mutableStateOf(false) }

    fun reportIfSolved() {
        if (reported.value) return
        val cookie = CookieManager.getInstance().getCookie(url)
        if (cookie != null && cookie.contains(WafSolver.COOKIE_NAME)) {
            reported.value = true
            onFinished(cookie)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("人机验证") },
                actions = {
                    TextButton(onClick = { onFinished(null) }) { Text("取消") }
                    TextButton(onClick = { onFinished(CookieManager.getInstance().getCookie(url)) }) {
                        Text("完成")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                "正在加载站点验证，通过后会自动继续。",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = HttpClient.MOBILE_UA
                        CookieManager.getInstance().setAcceptCookie(true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, pageUrl: String?) {
                                super.onPageFinished(view, pageUrl)
                                reportIfSolved()
                            }
                        }
                        loadUrl(url)
                    }
                },
            )
        }
    }
}
