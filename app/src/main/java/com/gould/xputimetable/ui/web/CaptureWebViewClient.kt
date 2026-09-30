/*
 * CaptureWebViewClient.kt —— 课表数据接口拦截器（策略 A：拦截 XHR/JSON，M2-B §4.4）
 *
 * 原理：教务课表页自己会发 GET print-data（带 Cookie 的 XHR）。我们在 WebView 的
 * shouldInterceptRequest 里认出这条 URL，用 HttpURLConnection **代发同样的 GET**，
 * 拿到响应体后做两份副本：
 *   【副本 1】包装成 WebResourceResponse 还给 WebView —— 页面照常渲染，用户无感知；
 *   【副本 2】经 [onCaptured] 回调交给封装层（ViewModel → 解析 → 预览确认，AC-10）。
 *
 * 【注意】 shouldInterceptRequest 在**后台线程**被调用：这里可以做同步网络请求（这正是
 * 本策略的价值），但**绝不触碰任何 View / 主线程 UI**；回调只更新 StateFlow（线程安全）。
 *
 * 隐私红线（Spec §8）：鉴权只靠 Cookie。我们只在代发请求头里带上 Cookie
 * （android.webkit.CookieManager 的内存副本），**不读取/不存储/不上传任何凭据**，
 * 不 dump Cookie 到日志、不写文件。用户在 WebView 里自己登录，App 对账号密码零接触。
 *
 * 失败兜底：代发失败时返回 null（交回 WebView 自己发，页面仍可用），错误经
 * [onError] 上报 → 触发「无法连接学校服务器…」提示（AC-12）。
 */
package com.gould.xputimetable.ui.web

import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.gould.xputimetable.parser.xpu.XpuEndpoints
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

class CaptureWebViewClient(
    /** 命中课表数据接口：响应体字符串 + 完整 URL（含 semesterId）。可能在后台线程调用。 */
    private val onCaptured: (body: String, url: String) -> Unit,
    /** 代发请求失败（网络不通/学校服务器异常）：AC-12 提示的数据源。 */
    private val onError: (reason: String) -> Unit = {},
) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val url = request.url.toString()
        if (XpuEndpoints.PRINT_DATA_REGEX.matches(url).not()) return null
        return runCatching { fetchAndDispatch(url) }
            .getOrElse { e ->
                onError(e.message ?: e::class.simpleName ?: "网络请求失败")
                null // 交回 WebView 自己发，页面仍然可用
            }
    }

    /** 代发 GET print-data：带上 WebView 的 Cookie 与 XHR 头（Spec §1.1 抓包结论）。 */
    private fun fetchAndDispatch(url: String): WebResourceResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            CookieManager.getInstance().getCookie(url)?.let { cookie ->
                setRequestProperty("Cookie", cookie)
            }
            setRequestProperty("X-Requested-With", "XMLHttpRequest")
            setRequestProperty("Referer", XpuEndpoints.COURSE_TABLE_PAGE)
        }
        try {
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            // 【副本 2】先交给封装层（StateFlow 线程安全；解析在 VM 的协程里做）
            onCaptured(body, url)
            // 【副本 1】再还给 WebView：页面正常渲染，用户无感知
            return WebResourceResponse(
                "application/json",
                "utf-8",
                ByteArrayInputStream(body.toByteArray(Charsets.UTF_8)),
            )
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000
    }
}
