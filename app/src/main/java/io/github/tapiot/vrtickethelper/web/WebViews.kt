package io.github.tapiot.vrtickethelper.web

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import androidx.core.net.toUri
import android.os.Environment
import android.util.Log
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import io.github.tapiot.vrtickethelper.BuildConfig
import io.github.tapiot.vrtickethelper.site.VrSite
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.coroutines.resume

object WebViews {

    /** Oldest Android System WebView major version the booking view is offered on. */
    const val MIN_WEBVIEW_MAJOR = 130

    private var debuggingSet = false

    @SuppressLint("SetJavaScriptEnabled")
    fun harden(webView: WebView) {
        if (!debuggingSet) {
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
            debuggingSet = true
        }
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            safeBrowsingEnabled = true
            setGeolocationEnabled(false)
            setSupportZoom(false)
        }
        webView.filterTouchesWhenObscured = true
        CookieManager.getInstance().setAcceptCookie(true)
    }

    data class VersionStatus(val versionName: String?, val major: Int?, val bookingAllowed: Boolean)

    fun versionStatus(context: Context): VersionStatus {
        val pkg = WebViewCompat.getCurrentWebViewPackage(context)
        val name = pkg?.versionName
        val major = name?.substringBefore('.')?.toIntOrNull()
        return VersionStatus(name, major, major != null && major >= MIN_WEBVIEW_MAJOR)
    }

    /** Removes vr.fi cookies, storage and the WebView cache. */
    fun clearSiteData(context: Context, onDone: () -> Unit = {}) {
        WebStorage.getInstance().deleteAllData()
        WebView(context.applicationContext).apply {
            clearCache(true)
            destroy()
        }
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
            onDone()
        }
    }

    fun download(context: Context, url: String, contentDisposition: String?, mimeType: String?) {
        if (!url.startsWith(VrSite.ORIGIN + "/")) return
        val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(url.toUri())
            .setMimeType(mimeType)
            .addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url).orEmpty())
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        context.getSystemService(DownloadManager::class.java)?.enqueue(request)
    }
}

private val evalJson = Json { isLenient = false }

/** Runs [script] and returns its string result, or null for null/undefined/non-string results. */
suspend fun WebView.evalString(script: String): String? = suspendCancellableCoroutine { cont ->
    evaluateJavascript(script) { raw ->
        val value = runCatching { (evalJson.parseToJsonElement(raw) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull }.getOrNull()
        if (cont.isActive) cont.resume(value)
    }
}

/** Logging for debug builds only. */
object DebugLog {
    fun d(message: String) {
        if (BuildConfig.DEBUG) Log.d("VrTicketHelper", message)
    }
}
