package io.github.tapiot.vrtickethelper.web

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

interface SiteListener {
    fun onPageStarted(url: String) {}
    fun onUrlChanged(url: String) {}
    fun onPageFinished(url: String) {}
    fun onPaymentStarted() {}
    fun onBlocked(url: String) {}
    fun onExternal(url: String, ask: Boolean) {}
    fun onRendererGone() {}
}

class SiteClient(
    private val policy: NavigationPolicy,
    private val listener: SiteListener,
) : WebViewClient() {

    private var currentHost: String? = null

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (!request.isForMainFrame) return false
        return handle(view, request.url.toString(), request.hasGesture(), fromPageStart = false)
    }

    /** Returns true when the load must not go ahead in this view. */
    private fun handle(view: WebView, url: String, gesture: Boolean, fromPageStart: Boolean): Boolean {
        return when (policy.decide(url, gesture, currentHost)) {
            NavDecision.ALLOW -> false
            NavDecision.ALLOW_START_PAYMENT -> {
                policy.markPaymentStarted()
                listener.onPaymentStarted()
                false
            }
            NavDecision.BLOCK -> {
                listener.onBlocked(url)
                true
            }
            NavDecision.OPEN_EXTERNAL -> {
                if (!fromPageStart) listener.onExternal(url, ask = false)
                true
            }
            NavDecision.ASK_EXTERNAL -> {
                if (!fromPageStart) listener.onExternal(url, ask = true)
                true
            }
        }
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        if (handle(view, url, gesture = false, fromPageStart = true)) {
            view.stopLoading()
            if (view.canGoBack()) view.goBack()
            return
        }
        currentHost = NavigationPolicy.hostOf(url)
        listener.onPageStarted(url)
        listener.onUrlChanged(url)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        currentHost = NavigationPolicy.hostOf(url)
        listener.onUrlChanged(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        listener.onPageFinished(url)
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        listener.onRendererGone()
        return true
    }
}

class SiteChrome : WebChromeClient() {
    override fun onPermissionRequest(request: PermissionRequest) {
        request.deny()
    }

    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
        callback.invoke(origin, false, false)
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
    ): Boolean = false

    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean = false
}
