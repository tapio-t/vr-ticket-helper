package io.github.tapiot.vrtickethelper

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.tapiot.vrtickethelper.data.FareRepository
import io.github.tapiot.vrtickethelper.data.RecentRoute
import io.github.tapiot.vrtickethelper.data.SettingsStore
import io.github.tapiot.vrtickethelper.logic.Comparison
import io.github.tapiot.vrtickethelper.logic.DateRange
import io.github.tapiot.vrtickethelper.logic.DayResult
import io.github.tapiot.vrtickethelper.logic.FINLAND
import io.github.tapiot.vrtickethelper.logic.Ranking
import io.github.tapiot.vrtickethelper.logic.SearchFailed
import io.github.tapiot.vrtickethelper.logic.SearchOutcome
import io.github.tapiot.vrtickethelper.logic.SiteCheckNeeded
import io.github.tapiot.vrtickethelper.logic.TripRequest
import io.github.tapiot.vrtickethelper.model.Candidate
import io.github.tapiot.vrtickethelper.model.Station
import io.github.tapiot.vrtickethelper.site.VrSite
import io.github.tapiot.vrtickethelper.web.BookingController
import io.github.tapiot.vrtickethelper.web.FareSession
import io.github.tapiot.vrtickethelper.web.WebViews
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class SearchForm(
    val from: Station? = null,
    val to: Station? = null,
    val departDate: LocalDate = today().plusDays(1),
    val arriveAround: LocalTime = LocalTime.of(12, 0),
    val returnDate: LocalDate = today().plusDays(3),
    val backAround: LocalTime = LocalTime.of(18, 0),
    val directOnly: Boolean = false,
    val rangeMode: Boolean = false,
    val rangeStart: LocalDate = today().plusDays(1),
    val rangeEnd: LocalDate = today().plusDays(14),
    val nights: Int = 2,
)

sealed interface Screen {
    data object Search : Screen
    data class Trains(val day: DayResult.Found, val outIndex: Int, val retIndex: Int) : Screen
    data object Range : Screen
    data object Booking : Screen
    data object SiteCheck : Screen
    data object Health : Screen
}

data class RangeBoard(
    val days: List<Pair<LocalDate, LocalDate>> = emptyList(),
    val results: Map<LocalDate, DayResult> = emptyMap(),
    val running: Boolean = false,
)

enum class CheckState { PENDING, RUNNING, OK, FAILED }

data class HealthItem(val label: String, val state: CheckState = CheckState.PENDING, val detail: String = "")

data class UiState(
    val stack: List<Screen> = listOf(Screen.Search),
    val form: SearchForm = SearchForm(),
    val stations: List<Station> = emptyList(),
    val stationsLoading: Boolean = false,
    val busy: Boolean = false,
    val progress: String? = null,
    val message: String? = null,
    val range: RangeBoard = RangeBoard(),
    val health: List<HealthItem> = emptyList(),
    val webViewVersion: String? = null,
    val bookingAllowed: Boolean = true,
) {
    val screen: Screen get() = stack.last()
}

fun today(): LocalDate = LocalDate.now(FINLAND)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val settings = SettingsStore(app)
    val session = FareSession(app)
    private val repo = FareRepository(app, session, settings)

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    val recentRoutes = settings.recentRoutes.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val historyEnabled = settings.historyEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    var booking: BookingController? = null
        private set

    private var job: Job? = null
    private var retryAfterCheck: (() -> Unit)? = null

    init {
        val v = WebViews.versionStatus(app)
        _ui.update { it.copy(webViewVersion = v.versionName, bookingAllowed = v.bookingAllowed) }
        loadStations()
    }

    // ---------------------------------------------------------------- navigation

    private fun push(s: Screen) = _ui.update { it.copy(stack = it.stack + s) }

    fun back() {
        val current = _ui.value.screen
        if (current == Screen.Booking) endBooking()
        if (current == Screen.SiteCheck) retryAfterCheck = null
        if (current == Screen.Range || current == Screen.Search) job?.cancel()
        _ui.update { if (it.stack.size > 1) it.copy(stack = it.stack.dropLast(1), busy = false, progress = null) else it }
    }

    fun dismissMessage() = _ui.update { it.copy(message = null) }

    private fun say(msg: String) = _ui.update { it.copy(message = msg) }

    // ---------------------------------------------------------------- form

    fun updateForm(f: (SearchForm) -> SearchForm) = _ui.update { it.copy(form = f(it.form)) }

    fun swap() = updateForm { it.copy(from = it.to, to = it.from) }

    fun useRecent(r: RecentRoute) {
        val st = _ui.value.stations
        val from = st.firstOrNull { it.code == r.fromCode } ?: Station(r.fromCode, r.fromName)
        val to = st.firstOrNull { it.code == r.toCode } ?: Station(r.toCode, r.toName)
        updateForm { it.copy(from = from, to = to) }
    }

    fun loadStations(force: Boolean = false) {
        viewModelScope.launch {
            _ui.update { it.copy(stationsLoading = true) }
            try {
                val list = repo.stations(force)
                _ui.update { it.copy(stations = list) }
                if (list.isEmpty()) say("Couldn't load the station list from vr.fi. Check your connection.")
            } catch (_: SiteCheckNeeded) {
                needCheck { loadStations(force) }
            } finally {
                _ui.update { it.copy(stationsLoading = false) }
            }
        }
    }

    // ---------------------------------------------------------------- searching

    fun findTrains() {
        val f = _ui.value.form
        val from = f.from
        val to = f.to
        if (from == null || to == null) return say("Choose both stations.")
        if (from.code == to.code) return say("Choose two different stations.")
        val t = today()
        val req = TripRequest(from.code, to.code, f.arriveAround.toMinutes(), f.backAround.toMinutes(), f.directOnly)
        if (f.rangeMode) {
            when (val c = DateRange.check(f.rangeStart, f.rangeEnd, t)) {
                is DateRange.Check.Invalid -> return say(c.message)
                DateRange.Check.Ok -> Unit
            }
            runRange(req, f, from, to)
        } else {
            if (f.departDate.isBefore(t)) return say("The departure date is in the past.")
            if (f.returnDate.isBefore(f.departDate)) return say("The return date is before the departure date.")
            runSingle(req, f.departDate, f.returnDate, from, to)
        }
    }

    private fun remember(from: Station, to: Station) {
        viewModelScope.launch { settings.addRecent(RecentRoute(from.code, from.name, to.code, to.name)) }
    }

    private fun runSingle(req: TripRequest, out: LocalDate, ret: LocalDate, from: Station, to: Station) {
        job?.cancel()
        job = viewModelScope.launch {
            _ui.update { it.copy(busy = true, progress = "Searching vr.fi…") }
            try {
                when (val r = Comparison(repo, today()).day(req, out, ret)) {
                    is DayResult.Found -> {
                        remember(from, to)
                        openDay(r)
                    }
                    is DayResult.NotOnSale -> say("Those dates are not on sale yet.")
                    is DayResult.NoTrains -> say("No suitable trains on those dates.")
                }
            } catch (_: SiteCheckNeeded) {
                needCheck { runSingle(req, out, ret, from, to) }
            } catch (e: SearchFailed) {
                say("Search failed: ${e.message}")
            } finally {
                _ui.update { it.copy(busy = false, progress = null) }
            }
        }
    }

    private fun runRange(req: TripRequest, f: SearchForm, from: Station, to: Station) {
        job?.cancel()
        val days = DateRange.expand(f.rangeStart, f.rangeEnd, f.nights)
        val existing = _ui.value.range
        val keep = if (existing.days == days) existing.results else emptyMap()
        _ui.update { s ->
            s.copy(
                range = RangeBoard(days, keep, running = true),
                stack = if (s.screen == Screen.Range) s.stack else s.stack + Screen.Range,
            )
        }
        job = viewModelScope.launch {
            val comparison = Comparison(repo, today())
            try {
                remember(from, to)
                for ((i, pair) in days.withIndex()) {
                    if (_ui.value.range.results.containsKey(pair.first)) continue
                    _ui.update { it.copy(progress = "Day ${i + 1} of ${days.size}") }
                    val r = try {
                        comparison.day(req, pair.first, pair.second)
                    } catch (e: SearchFailed) {
                        say("Search failed for ${pair.first}: ${e.message}")
                        continue
                    }
                    _ui.update { it.copy(range = it.range.copy(results = it.range.results + (pair.first to r))) }
                }
            } catch (_: SiteCheckNeeded) {
                needCheck { runRange(req, f, from, to) }
            } finally {
                _ui.update { it.copy(range = it.range.copy(running = false), progress = null) }
            }
        }
    }

    fun openDay(day: DayResult.Found) {
        val best = day.best ?: return say("No pair of trains fits on that day.")
        push(Screen.Trains(day, day.outward.indexOf(best.first), day.returns.indexOf(best.second)))
    }

    fun selectOut(i: Int) = updateTrains { s ->
        val out = s.day.outward[i]
        val ret = s.day.returns[s.retIndex]
        val retIndex = if (Ranking.validReturn(out, ret)) s.retIndex
        else s.day.returns.indexOfFirst { Ranking.validReturn(out, it) }.let { if (it < 0) s.retIndex else it }
        s.copy(outIndex = i, retIndex = retIndex)
    }

    fun selectRet(i: Int) = updateTrains { it.copy(retIndex = i) }

    private fun updateTrains(f: (Screen.Trains) -> Screen.Trains) = _ui.update { s ->
        val t = s.screen as? Screen.Trains ?: return@update s
        s.copy(stack = s.stack.dropLast(1) + f(t))
    }

    // ---------------------------------------------------------------- vr.fi check

    private fun needCheck(retry: () -> Unit) {
        retryAfterCheck = retry
        if (_ui.value.screen != Screen.SiteCheck) push(Screen.SiteCheck)
    }

    fun checkDone() {
        val retry = retryAfterCheck
        retryAfterCheck = null
        _ui.update { if (it.screen == Screen.SiteCheck) it.copy(stack = it.stack.dropLast(1)) else it }
        session.reset()
        retry?.invoke()
    }

    // ---------------------------------------------------------------- booking

    fun startBooking(out: Candidate, ret: Candidate) {
        if (!_ui.value.bookingAllowed) return say("Update Android System WebView from the Play Store to book in the app.")
        val f = _ui.value.form
        val from = f.from ?: return
        val to = f.to ?: return
        booking?.destroy()
        booking = BookingController(getApplication(), viewModelScope, out, ret, from.code, to.code)
        push(Screen.Booking)
    }

    private fun endBooking() {
        val b = booking ?: return
        val paid = b.ui.value.paymentStarted
        b.destroy()
        booking = null
        if (paid) clearSiteData(quiet = true)
    }

    // ---------------------------------------------------------------- settings and checks

    fun clearSiteData(quiet: Boolean = false) {
        repo.clearCache()
        WebViews.clearSiteData(getApplication()) {
            session.reset()
            if (!quiet) say("vr.fi data cleared.")
        }
    }

    fun clearHistory() = viewModelScope.launch { settings.clearHistory(); say("History cleared.") }

    fun setHistoryEnabled(on: Boolean) = viewModelScope.launch { settings.setHistoryEnabled(on) }

    fun openHealth() {
        push(Screen.Health)
        runHealth()
    }

    fun runHealth() {
        val labels = listOf("Android System WebView", "Station list", "Fare search", "vr.fi results page")
        _ui.update { it.copy(health = labels.map { l -> HealthItem(l) }) }
        fun set(i: Int, state: CheckState, detail: String = "") =
            _ui.update { s -> s.copy(health = s.health.toMutableList().also { it[i] = it[i].copy(state = state, detail = detail) }) }
        job?.cancel()
        job = viewModelScope.launch {
            val v = WebViews.versionStatus(getApplication())
            set(0, if (v.bookingAllowed) CheckState.OK else CheckState.FAILED,
                (v.versionName ?: "unknown") + if (v.bookingAllowed) "" else " — update needed for booking (minimum ${WebViews.MIN_WEBVIEW_MAJOR})")
            try {
                set(1, CheckState.RUNNING)
                val st = repo.stations(forceRefresh = true)
                if (st.isNotEmpty()) {
                    _ui.update { it.copy(stations = st) }
                    set(1, CheckState.OK, "${st.size} stations")
                } else set(1, CheckState.FAILED, "No stations")
                set(2, CheckState.RUNNING)
                val date = today().plusDays(7)
                repo.clearCache()
                when (val r = repo.search(HEALTH_FROM, HEALTH_TO, date)) {
                    is SearchOutcome.Ok -> set(2,
                        if (r.journeys.isNotEmpty() && !repo.usedPageFallback) CheckState.OK else CheckState.FAILED,
                        "${r.journeys.size} trains $HEALTH_FROM–$HEALTH_TO on $date" + if (repo.usedPageFallback) " (read from the results page)" else "")
                    SearchOutcome.NotOnSale -> set(2, CheckState.FAILED, "Reported as not on sale")
                    is SearchOutcome.Failed -> set(2, CheckState.FAILED, r.message)
                }
                set(3, CheckState.RUNNING)
                val raw = session.readResultsPage(HEALTH_FROM, HEALTH_TO, date)
                val cards = raw?.let { VrSite.parseCards(it, date) }.orEmpty()
                set(3, if (cards.isNotEmpty()) CheckState.OK else CheckState.FAILED, "${cards.size} train cards read")
                session.reset()
            } catch (_: SiteCheckNeeded) {
                needCheck { runHealth() }
            }
        }
    }

    override fun onCleared() {
        booking?.destroy()
        super.onCleared()
    }

    companion object {
        const val HEALTH_FROM = "HKI"
        const val HEALTH_TO = "TPE"
    }
}

private fun LocalTime.toMinutes() = hour * 60 + minute
