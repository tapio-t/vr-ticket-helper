package io.github.tapiot.vrtickethelper.web

import android.content.Context
import android.content.MutableContextWrapper
import android.os.SystemClock
import android.webkit.WebView
import io.github.tapiot.vrtickethelper.site.VrSite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.util.UUID

sealed interface FetchResult {
    data class Ok(val body: String) : FetchResult
    data object CheckNeeded : FetchResult
    data class Http(val status: Int) : FetchResult
    data class Failed(val message: String) : FetchResult
}

class FareSession(appContext: Context) {

    private val wrapper = MutableContextWrapper(appContext.applicationContext)
    private val appContext = appContext.applicationContext
    private val mutex = Mutex()
    private var lastRequestAt = 0L

    private val finishedUrl = MutableStateFlow<String?>(null)
    private val _currentUrl = MutableStateFlow<String?>(null)
    val currentUrl: StateFlow<String?> = _currentUrl

    private var view: WebView? = null

    /** Created on the main thread on first use. */
    val webView: WebView
        get() = view ?: WebView(wrapper).also { wv ->
            WebViews.harden(wv)
            wv.webViewClient = SiteClient(NavigationPolicy(allowPayment = false), object : SiteListener {
                override fun onPageStarted(url: String) {
                    DebugLog.d("fare page started")
                    finishedUrl.value = null
                }
                override fun onUrlChanged(url: String) {
                    _currentUrl.value = url
                }
                override fun onPageFinished(url: String) {
                    DebugLog.d("fare page finished")
                    finishedUrl.value = url
                }
                override fun onRendererGone() {
                    view = null
                }
            })
            wv.webChromeClient = SiteChrome()
            view = wv
        }

    fun attachTo(activityContext: Context) {
        wrapper.baseContext = activityContext
    }

    fun detach() {
        wrapper.baseContext = appContext
    }

    private fun onVrPage(url: String?) = NavigationPolicy.hostOf(url) == VrSite.HOST

    /** Makes sure a vr.fi page is loaded; returns false if it did not load in time. */
    private var forceReload = false

    private suspend fun ensurePage(): Boolean {
        if (!forceReload && onVrPage(webView.url) && onVrPage(finishedUrl.value)) return true
        if (forceReload || !onVrPage(webView.url)) {
            forceReload = false
            finishedUrl.value = null
            webView.loadUrl(VrSite.START_URL)
        }
        val ok = withTimeoutOrNull(30_000) { finishedUrl.first { onVrPage(it) } } != null
        if (!ok) forceReload = true
        return ok
    }

    /** Loads a fresh vr.fi page before the next request, for example after the user's check. */
    fun reset() {
        forceReload = true
    }

    suspend fun fetch(path: String): FetchResult = withContext(Dispatchers.Main) {
        mutex.withLock {
            pace()
            val first = attempt(path)
            if (first != null) return@withLock first
            forceReload = true
            attempt(path) ?: FetchResult.Failed("vr.fi did not answer")
        }
    }

    /** One request; null when the page was not usable and a retry on a fresh page makes sense. */
    private suspend fun attempt(path: String): FetchResult? {
        if (!ensurePage()) {
            DebugLog.d("fare page not ready")
            return FetchResult.Failed("vr.fi did not load")
        }
        val slot = UUID.randomUUID().toString().replace("-", "").take(20)
        val started = webView.evalString(VrSite.fetchStartScript(slot, path))
        if (started != "started") {
            DebugLog.d("fetch not started: $started")
            return null
        }
        val raw = poll(slot)
        if (raw == null) {
            DebugLog.d("fetch gave no reply")
            return null
        }
        val reply = VrSite.parseFetchReply(raw) ?: return FetchResult.Failed("Network error")
        DebugLog.d("fetch status ${reply.status}")
        return when {
            reply.status in VrSite.CHECK_STATUSES -> FetchResult.CheckNeeded
            !VrSite.looksLikeJson(reply) -> if (reply.status == 200) FetchResult.CheckNeeded else FetchResult.Http(reply.status)
            reply.status == 404 -> FetchResult.Http(404)
            else -> FetchResult.Ok(reply.body)
        }
    }

    private suspend fun poll(slot: String): String? {
        val deadline = SystemClock.elapsedRealtime() + 25_000
        val script = VrSite.fetchPollScript(slot)
        while (SystemClock.elapsedRealtime() < deadline) {
            delay(120)
            when (val r = webView.evalString(script)) {
                null -> continue
                "gone" -> return null
                else -> return r
            }
        }
        return null
    }

    private suspend fun pace() {
        val wait = lastRequestAt + MIN_GAP_MS - SystemClock.elapsedRealtime()
        if (wait > 0) delay(wait)
        lastRequestAt = SystemClock.elapsedRealtime()
    }

    /** Reads train cards from vr.fi's own results page for a one-way trip. */
    suspend fun readResultsPage(from: String, to: String, date: LocalDate): String? = withContext(Dispatchers.Main) {
        mutex.withLock {
            pace()
            finishedUrl.value = null
            webView.loadUrl(VrSite.outboundResultsUrl(from, to, date, null))
            withTimeoutOrNull(30_000) { finishedUrl.first { onVrPage(it) } } ?: return@withLock null
            val deadline = SystemClock.elapsedRealtime() + 20_000
            var last: String? = null
            var stableRounds = 0
            while (SystemClock.elapsedRealtime() < deadline) {
                delay(700)
                val r = webView.evalString(VrSite.READ_CARDS_SCRIPT) ?: continue
                if (r != "[]" && r == last) stableRounds++ else stableRounds = 0
                last = r
                if (stableRounds >= 2) break
            }
            last
        }
    }

    companion object {
        const val MIN_GAP_MS = 500L
    }
}
