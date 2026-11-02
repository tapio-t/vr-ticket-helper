package io.github.tapiot.vrtickethelper.web

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import androidx.core.net.toUri
import android.os.SystemClock
import android.webkit.WebView
import io.github.tapiot.vrtickethelper.logic.TimeMath
import io.github.tapiot.vrtickethelper.model.Candidate
import io.github.tapiot.vrtickethelper.site.VrSite
import io.github.tapiot.vrtickethelper.site.VrSite.PageKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

enum class BookingStep(val label: String) {
    OUTWARD_SEAT("Outward seat"),
    CONFIRM("Confirm"),
    RETURN_SEAT("Return seat"),
    PAY("Pay"),
}

data class BookingUi(
    val host: String? = null,
    val step: BookingStep = BookingStep.OUTWARD_SEAT,
    val message: String = "Opening vr.fi…",
    val helping: Boolean = true,
    val paymentStarted: Boolean = false,
    val warnings: List<String> = emptyList(),
    val notice: String? = null,
    val askExternal: String? = null,
    val outwardChecked: Boolean = false,
    val returnChecked: Boolean = false,
)

/** State and steps of the booking screen. */
class BookingController(
    appContext: Context,
    private val scope: CoroutineScope,
    val outward: Candidate,
    val inbound: Candidate,
    private val from: String,
    private val to: String,
) {
    private val wrapper = MutableContextWrapper(appContext.applicationContext)
    private val appContext = appContext.applicationContext
    private val policy = NavigationPolicy(allowPayment = true)
    private val json = Json { ignoreUnknownKeys = true }

    private val _ui = MutableStateFlow(BookingUi())
    val ui: StateFlow<BookingUi> = _ui

    private var job: Job? = null

    // Per-page progress, so automation never repeats a click the user may have undone.
    private var outClicked = false
    private var retClicked = false
    private var outSeatClicked = false
    private var retSeatClicked = false
    private var outMapSeen = false
    private var retMapSeen = false
    private var outVerified = false
    private var retVerified = false
    private var summaryChecked = false
    private var cardsSeenAt = 0L

    val webView: WebView by lazy {
        WebView(wrapper).also { wv ->
            WebViews.harden(wv)
            wv.webViewClient = SiteClient(policy, listener)
            wv.webChromeClient = SiteChrome()
            wv.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                WebViews.download(appContext, url, contentDisposition, mimeType)
            }
        }
    }

    private val listener = object : SiteListener {
        override fun onUrlChanged(url: String) {
            _ui.update { it.copy(host = NavigationPolicy.hostOf(url)) }
        }

        override fun onPaymentStarted() {
            stopHelping(auto = true)
            _ui.update {
                it.copy(
                    paymentStarted = true,
                    step = BookingStep.PAY,
                    message = "Payment is open. Pay with your bank; the app no longer touches the page.",
                )
            }
        }

        override fun onBlocked(url: String) {
            val host = NavigationPolicy.hostOf(url)
            _ui.update { it.copy(notice = if (host != null) "Blocked a page outside vr.fi ($host)." else "Blocked a link.") }
        }

        override fun onExternal(url: String, ask: Boolean) {
            if (ask) _ui.update { it.copy(askExternal = url) } else openExternal(url)
        }

        override fun onRendererGone() {
            stopHelping(auto = true)
            _ui.update { it.copy(notice = "The vr.fi view stopped. Close it and start the booking again.") }
        }
    }

    fun attachTo(activityContext: Context) {
        wrapper.baseContext = activityContext
    }

    private var started = false

    fun start() {
        if (started) return
        started = true
        webView.loadUrl(VrSite.outboundResultsUrl(from, to, outward.bookingDate, inbound.bookingDate))
        runLoop()
    }

    private fun runLoop() {
        job?.cancel()
        job = scope.launch {
            while (isActive && _ui.value.helping) {
                delay(POLL_MS)
                tick()
            }
        }
    }

    fun stopHelping(auto: Boolean = false) {
        job?.cancel()
        job = null
        _ui.update {
            it.copy(
                helping = false,
                message = if (auto) it.message else "You're in control. Use vr.fi as usual; press Continue to get help again.",
            )
        }
    }

    /** Resumes from whatever page vr.fi is showing. */
    fun resume() {
        if (policy.paymentStarted) return
        when (currentKind) {
            PageKind.OUTBOUND_RESULTS -> outClicked = false
            PageKind.OUTBOUND_DETAILS -> outSeatClicked = false
            PageKind.RETURN_RESULTS -> retClicked = false
            PageKind.RETURN_DETAILS -> retSeatClicked = false
            else -> Unit
        }
        cardsSeenAt = 0L
        _ui.update { it.copy(helping = true, message = "Helping…") }
        runLoop()
    }

    fun dismissNotice() = _ui.update { it.copy(notice = null) }

    fun answerExternal(open: Boolean) {
        val url = _ui.value.askExternal
        _ui.update { it.copy(askExternal = null) }
        if (open && url != null) openExternal(url)
    }

    private fun openExternal(url: String) {
        val intent = externalIntent(url) ?: return
        try {
            wrapper.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            val fallback = intent.getStringExtra("browser_fallback_url")
            if (fallback != null && fallback.startsWith("https://") && policy.paymentStarted) webView.loadUrl(fallback)
            else _ui.update { it.copy(notice = "No app on this phone can open that link.") }
        } catch (_: SecurityException) {
            _ui.update { it.copy(notice = "That link could not be opened.") }
        }
    }

    fun destroy() {
        job?.cancel()
        webView.stopLoading()
        webView.destroy()
        wrapper.baseContext = appContext
    }

    // ---------------------------------------------------------------- automation

    private var currentKind: PageKind = PageKind.OTHER

    private data class PageState(val kind: PageKind, val cards: Int, val changeSeat: Boolean, val coachMap: Boolean)

    private suspend fun readState(): PageState? {
        val raw = webView.evalString(VrSite.PAGE_STATE_SCRIPT) ?: return null
        val o = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
        val path = (o["path"] as? JsonPrimitive)?.contentOrNull?.takeIf { VrSite.isSafePath(it) } ?: return null
        val cards = (o["cards"] as? JsonPrimitive)?.intOrNull?.coerceIn(0, 1000) ?: 0
        val change = (o["changeSeat"] as? JsonPrimitive)?.booleanOrNull ?: false
        val map = (o["coachMap"] as? JsonPrimitive)?.booleanOrNull ?: false
        return PageState(VrSite.pageKind(path), cards, change, map)
    }

    private suspend fun tick() {
        if (policy.paymentStarted) { stopHelping(auto = true); return }
        val s = readState() ?: return
        if (s.kind != currentKind) cardsSeenAt = 0L
        currentKind = s.kind
        when (s.kind) {
            PageKind.OUTBOUND_RESULTS -> {
                setStep(BookingStep.OUTWARD_SEAT)
                if (!outClicked) outClicked = selectTrain(s, outward, "outward")
            }
            PageKind.OUTBOUND_DETAILS -> details(s, outward, isReturn = false)
            PageKind.RETURN_RESULTS -> {
                setStep(BookingStep.RETURN_SEAT)
                if (!retClicked) retClicked = selectTrain(s, inbound, "return")
            }
            PageKind.RETURN_DETAILS -> details(s, inbound, isReturn = true)
            PageKind.SUMMARY -> summary()
            PageKind.OTHER -> Unit
        }
    }

    private fun setStep(step: BookingStep) = _ui.update { it.copy(step = step) }

    private fun describe(c: Candidate) =
        "${TimeMath.clock(c.journey.departure)} → ${TimeMath.clock(c.journey.arrival)} (${c.journey.trainNames})"

    /** Returns true once nothing more should be tried on this results page. */
    private suspend fun selectTrain(s: PageState, c: Candidate, which: String): Boolean {
        if (s.cards == 0) {
            _ui.update { it.copy(message = "Waiting for vr.fi's train list…") }
            return false
        }
        if (cardsSeenAt == 0L) cardsSeenAt = SystemClock.elapsedRealtime()
        val script = VrSite.clickTrainScript(
            TimeMath.clock(c.journey.departure), TimeMath.clock(c.journey.arrival), c.journey.firstTrainNumber, c.duplicateIndex,
        )
        return when (webView.evalString(script)) {
            "clicked" -> {
                _ui.update { it.copy(message = "Selected the $which train ${describe(c)}. Opening the ticket…") }
                true
            }
            "disabled" -> {
                _ui.update { it.copy(message = "The $which train ${describe(c)} is no longer available. Pick another train on vr.fi.") }
                true
            }
            "notfound" -> if (SystemClock.elapsedRealtime() - cardsSeenAt > NOT_FOUND_AFTER_MS) {
                _ui.update { it.copy(message = "Couldn't find the $which train ${describe(c)}. Select it yourself in the list.") }
                true
            } else false
            else -> false
        }
    }

    private suspend fun details(s: PageState, c: Candidate, isReturn: Boolean) {
        val verified = if (isReturn) retVerified else outVerified
        if (!verified) {
            val problems = checkPage { PageCheck.ticketProblems(it, c) }
            if (isReturn) retVerified = true else outVerified = true
            _ui.update {
                it.copy(
                    warnings = it.warnings + problems.map { p -> (if (isReturn) "Return: " else "Outward: ") + p },
                    outwardChecked = it.outwardChecked || (!isReturn && problems.isEmpty()),
                    returnChecked = it.returnChecked || (isReturn && problems.isEmpty()),
                )
            }
        }
        val leg = if (isReturn) "return" else "outward"
        if (s.coachMap) {
            if (isReturn) retMapSeen = true else outMapSeen = true
            _ui.update {
                it.copy(
                    step = if (isReturn) BookingStep.RETURN_SEAT else BookingStep.OUTWARD_SEAT,
                    message = "Your turn: pick a seat for the $leg train, then press “Confirm seat selection”.",
                )
            }
            return
        }
        val seatClicked = if (isReturn) retSeatClicked else outSeatClicked
        val mapSeen = if (isReturn) retMapSeen else outMapSeen
        when {
            mapSeen -> _ui.update {
                it.copy(
                    step = if (isReturn) BookingStep.RETURN_SEAT else BookingStep.CONFIRM,
                    message = "Your turn: check the $leg ticket and press “Confirm journey”.",
                )
            }
            !seatClicked && s.changeSeat -> {
                when (webView.evalString(VrSite.CLICK_CHANGE_SEAT_SCRIPT)) {
                    "clicked", "open" -> if (isReturn) retSeatClicked = true else outSeatClicked = true
                }
                _ui.update { it.copy(message = "Opening the coach map…") }
            }
            !seatClicked -> _ui.update { it.copy(message = "Waiting for “Change seat”…") }
            else -> Unit
        }
    }

    private suspend fun summary() {
        setStep(BookingStep.PAY)
        if (!summaryChecked) {
            summaryChecked = true
            val problems = checkPage { PageCheck.summaryProblems(it, outward, inbound) }
            _ui.update { it.copy(warnings = it.warnings + problems.map { p -> "Summary: $p" }) }
        }
        _ui.update {
            it.copy(
                message = if (it.warnings.isEmpty()) "Your turn: check the summary and pay on vr.fi. Trains and dates match your choice."
                else "Your turn: check the summary carefully before paying — see the warnings above.",
            )
        }
    }

    /** Reads the page text a few times while it settles; returns the last problems found. */
    private suspend fun checkPage(problemsOf: (String) -> List<String>): List<String> {
        var problems = listOf("page could not be read")
        val deadline = SystemClock.elapsedRealtime() + CHECK_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            val text = webView.evalString(VrSite.PAGE_TEXT_SCRIPT)
            if (text != null) {
                problems = problemsOf(text)
                if (problems.isEmpty()) return problems
            }
            delay(700)
        }
        return problems
    }

    companion object {
        const val POLL_MS = 600L
        const val NOT_FOUND_AFTER_MS = 8_000L
        const val CHECK_MS = 8_000L

        private const val URI_GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION

        fun externalIntent(url: String): Intent? = try {
            val intent = if (url.startsWith("intent:", ignoreCase = true)) Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            else Intent(Intent.ACTION_VIEW, url.toUri())
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            intent.component = null
            intent.selector = null
            intent.flags = intent.flags and URI_GRANT_FLAGS.inv()
            intent
        } catch (_: Exception) {
            null
        }
    }
}
