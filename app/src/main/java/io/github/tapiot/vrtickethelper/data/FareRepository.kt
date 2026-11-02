package io.github.tapiot.vrtickethelper.data

import android.content.Context
import io.github.tapiot.vrtickethelper.BuildConfig
import io.github.tapiot.vrtickethelper.logic.FareSearch
import io.github.tapiot.vrtickethelper.logic.SearchOutcome
import io.github.tapiot.vrtickethelper.logic.SiteCheckNeeded
import io.github.tapiot.vrtickethelper.model.Station
import io.github.tapiot.vrtickethelper.site.VrSite
import io.github.tapiot.vrtickethelper.web.FareSession
import io.github.tapiot.vrtickethelper.web.FetchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

class FareRepository(
    private val context: Context,
    private val session: FareSession,
    private val settings: SettingsStore,
) : FareSearch {

    private data class Cached(val at: Long, val outcome: SearchOutcome)

    private val cache = HashMap<String, Cached>()
    private var stations: List<Station>? = null

    /** True when the last search used the backup way of reading trains. */
    @Volatile var usedPageFallback = false
        private set

    fun clearCache() {
        cache.clear()
    }

    override suspend fun search(from: String, to: String, date: LocalDate): SearchOutcome {
        val key = "$from|$to|$date"
        val now = System.currentTimeMillis()
        cache[key]?.takeIf { now - it.at < CACHE_MS }?.let { return it.outcome }
        val outcome = fetchSearch(from, to, date)
        if (outcome !is SearchOutcome.Failed) cache[key] = Cached(now, outcome)
        return outcome
    }

    private suspend fun fetchSearch(from: String, to: String, date: LocalDate): SearchOutcome {
        return when (val r = session.fetch(VrSite.searchPath(from, to, date))) {
            FetchResult.CheckNeeded -> throw SiteCheckNeeded()
            is FetchResult.Failed -> SearchOutcome.Failed(r.message)
            is FetchResult.Http -> fromPage(from, to, date)
            is FetchResult.Ok -> {
                saveSample("search-$from-$to-$date.json", r.body)
                when (val p = VrSite.parseSearch(r.body)) {
                    is VrSite.ParsedSearch.Options -> { usedPageFallback = false; SearchOutcome.Ok(p.journeys) }
                    VrSite.ParsedSearch.NotOnSale -> SearchOutcome.NotOnSale
                    is VrSite.ParsedSearch.Error ->
                        if (p.message.startsWith("Unexpected") || p.message.startsWith("Unreadable")) fromPage(from, to, date)
                        else SearchOutcome.Failed(p.message)
                }
            }
        }
    }

    private suspend fun fromPage(from: String, to: String, date: LocalDate): SearchOutcome {
        val raw = session.readResultsPage(from, to, date) ?: return SearchOutcome.Failed("vr.fi results could not be read")
        saveSample("cards-$from-$to-$date.json", raw)
        val journeys = VrSite.parseCards(raw, date)
        usedPageFallback = true
        return if (journeys.isEmpty()) SearchOutcome.Failed("No trains could be read from vr.fi") else SearchOutcome.Ok(journeys)
    }

    suspend fun stations(forceRefresh: Boolean = false): List<Station> {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            stations?.let { return it }
            settings.cachedStations()?.let { (at, body) ->
                if (now - at < STATIONS_MS) {
                    val parsed = VrSite.parseStations(body)
                    if (parsed.isNotEmpty()) return parsed.also { stations = it }
                }
            }
        }
        return when (val r = session.fetch(VrSite.stationsPath())) {
            FetchResult.CheckNeeded -> throw SiteCheckNeeded()
            is FetchResult.Ok -> {
                saveSample("stations.json", r.body)
                val parsed = VrSite.parseStations(r.body)
                if (parsed.isNotEmpty()) settings.saveStations(r.body, now)
                parsed.also { stations = it }
            }
            else -> settings.cachedStations()?.let { VrSite.parseStations(it.second) }.orEmpty()
        }
    }

    /** Debug builds keep the latest raw answers so they can be turned into test samples. */
    private suspend fun saveSample(name: String, body: String) {
        if (!BuildConfig.DEBUG) return
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.filesDir, "samples").apply { mkdirs() }
                File(dir, name).writeText(body)
            }
        }
    }

    companion object {
        const val CACHE_MS = 10 * 60 * 1000L
        const val STATIONS_MS = 24 * 60 * 60 * 1000L
    }
}
