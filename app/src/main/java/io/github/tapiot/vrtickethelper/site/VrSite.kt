package io.github.tapiot.vrtickethelper.site

import io.github.tapiot.vrtickethelper.logic.FINLAND
import io.github.tapiot.vrtickethelper.model.Journey
import io.github.tapiot.vrtickethelper.model.Leg
import io.github.tapiot.vrtickethelper.model.Station
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.UUID

/** Everything specific to www.vr.fi, kept in one place. */
object VrSite {

    const val HOST = "www.vr.fi"
    const val ORIGIN = "https://www.vr.fi"
    const val START_URL = "https://www.vr.fi/en"
    val PAYMENT_HOSTS = listOf("paytrail.com")

    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    // ---------------------------------------------------------------- value formats

    val STATION_CODE = Regex("^[A-ZÅÄÖ]{1,6}$")
    private val STATION_NAME = Regex("^[\\p{L}\\p{M}0-9 .,'()/\\-]{1,60}$")
    private val TRAIN_NUMBER = Regex("^[0-9]{1,5}$")
    private val TRAIN_TYPE = Regex("^[A-Za-z0-9]{1,8}$")
    private val OPTION_ID = Regex("^[A-Za-z0-9_:.=+/\\-]{1,200}$")
    private val CLOCK = Regex("^([0-9]{1,2}):([0-9]{2})$")
    private val PRICE_TEXT = Regex("^€\\s?([0-9]{1,4})[.,]([0-9]{2})$")
    private val PATH = Regex("^/[A-Za-z0-9/_\\-]{0,200}$")
    private const val MAX_PRICE_CENTS = 1_000_000
    private const val MAX_STATIONS = 2_000
    private const val MAX_OPTIONS = 200

    // ---------------------------------------------------------------- API addresses

    private const val SEARCH_PATH = "/api/trpc/journey.searchJourney?batch=1&input="
    private const val STATIONS_PATH = "/api/trpc/station.getStations?input="
    private const val REFRESH_PATH = "/api/trpc/auth.refreshTokens"
    private const val TOKEN_EXPIRED = "AccessTokenExpired"

    val CHECK_STATUSES = setOf(202, 403, 405, 429)

    fun searchPath(from: String, to: String, date: LocalDate): String {
        require(STATION_CODE.matches(from) && STATION_CODE.matches(to))
        val input = buildJsonObject {
            putJsonObject("0") {
                put("locale", "en")
                put("departureStation", from)
                put("arrivalStation", to)
                put("departureTime", date.toString())
                putJsonArray("passengers") {
                    addJsonObject {
                        put("key", UUID.randomUUID().toString())
                        put("type", "ADULT")
                        put("wheelchair", false)
                        putJsonArray("vehicles") {}
                    }
                }
                putJsonArray("placeTypes") { add("SEAT"); add("CABIN_SEAT"); add("CABIN_BED") }
                putJsonArray("filters") {}
            }
        }
        return SEARCH_PATH + encode(input.toString())
    }

    fun stationsPath(): String = STATIONS_PATH + encode("""{"locale":"en"}""")

    private fun encode(s: String) = URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20")

    // ---------------------------------------------------------------- booking pages

    fun outboundResultsUrl(from: String, to: String, outDate: LocalDate, returnDate: LocalDate?): String {
        require(STATION_CODE.matches(from) && STATION_CODE.matches(to))
        val sb = StringBuilder("$ORIGIN/en/single-ticket-outbound-search-results")
        sb.append("?from=").append(encode(from)).append("&to=").append(encode(to))
        sb.append("&outboundDate=").append(outDate)
        if (returnDate != null) sb.append("&returnDate=").append(returnDate)
        sb.append("&passengers%5B0%5D%5Btype%5D=ADULT")
        return sb.toString()
    }

    enum class PageKind { OUTBOUND_RESULTS, OUTBOUND_DETAILS, RETURN_RESULTS, RETURN_DETAILS, SUMMARY, OTHER }

    fun pageKind(path: String): PageKind = when {
        path.endsWith("single-ticket-outbound-search-results") -> PageKind.OUTBOUND_RESULTS
        path.endsWith("single-ticket-outbound-journey-details") -> PageKind.OUTBOUND_DETAILS
        path.endsWith("single-ticket-return-search-results") -> PageKind.RETURN_RESULTS
        path.endsWith("single-ticket-return-journey-details") -> PageKind.RETURN_DETAILS
        path.endsWith("single-ticket-purchase-summary") -> PageKind.SUMMARY
        else -> PageKind.OTHER
    }

    /** How a date can appear on ticket pages: "10.11." and "10.11.2026". */
    fun dateForms(date: LocalDate): List<String> = listOf(
        "${date.dayOfMonth}.${date.monthValue}.",
        "%02d.%02d.".format(date.dayOfMonth, date.monthValue),
    )

    /** Number of adult passengers mentioned on a summary page, or null when none is found. */
    fun adultCount(text: String): Int? {
        Regex("(?i)(?<![0-9])([0-9]{1,2})\\s*[x×]?\\s*adults?\\b").find(text)?.let {
            return it.groupValues[1].toIntOrNull()
        }
        return if (Regex("(?i)\\badult\\b").containsMatchIn(text)) 1 else null
    }

    // ---------------------------------------------------------------- scripts

    private const val GUARD = "if (location.origin !== \"$ORIGIN\") return null;"

    private fun js(s: String): String = JsonPrimitive(s).toString()

    fun fetchStartScript(slot: String, path: String): String {
        require(slot.matches(Regex("^[a-z0-9]{8,32}$")))
        require(path.startsWith("/api/"))
        return """
(function () {
  $GUARD
  var key = ${js("__vth_$slot")};
  var path = ${js(path)};
  var opts = { credentials: "same-origin", headers: { "accept": "application/json" } };
  Object.defineProperty(window, key, { value: { state: "pending" }, writable: true, configurable: true, enumerable: false });
  (async function () {
    try {
      var r = await fetch(path, opts);
      if (r.status === 401) {
        var t = await r.text();
        if (t.indexOf(${js(TOKEN_EXPIRED)}) >= 0 || t.indexOf("UNAUTHORIZED") >= 0) {
          await fetch(${js(REFRESH_PATH)}, { method: "POST", credentials: "same-origin", headers: { "content-type": "application/json" } });
          r = await fetch(path, opts);
        } else {
          window[key] = { state: "done", status: 401, ct: r.headers.get("content-type") || "", body: t };
          return;
        }
      }
      var body = await r.text();
      window[key] = { state: "done", status: r.status, ct: r.headers.get("content-type") || "", body: body };
    } catch (e) {
      window[key] = { state: "error", message: String(e).slice(0, 200) };
    }
  })();
  return "started";
})()
""".trimIndent()
    }

    fun fetchPollScript(slot: String): String {
        require(slot.matches(Regex("^[a-z0-9]{8,32}$")))
        return """
(function () {
  $GUARD
  var key = ${js("__vth_$slot")};
  var v = window[key];
  if (!v) return "gone";
  if (v.state === "pending") return null;
  delete window[key];
  return JSON.stringify(v);
})()
""".trimIndent()
    }

    private const val CARD = "button[data-testid=\"connection-list-item\"]"
    private const val CHANGE_SEAT = "[data-testid=\"journey-details__toggleWagonMap\"]"
    private const val CONFIRM_SEAT_TEXT = "Confirm seat selection"
    private const val SELECT_SEAT_TEXT = "Select a seat"
    private const val NIGHT_TRAFFIC_TEXT = "Night traffic"

    private val COACH_MAP_JS = """
  var coachMap = Array.prototype.some.call(document.querySelectorAll("button"), function (b) {
    return b.offsetParent !== null && (b.textContent || "").indexOf(${js(CONFIRM_SEAT_TEXT)}) >= 0;
  }) || Array.prototype.some.call(document.querySelectorAll("h1,h2,h3,[role=heading]"), function (h) {
    return h.offsetParent !== null && (h.textContent || "").indexOf(${js(SELECT_SEAT_TEXT)}) >= 0;
  });
""".trimIndent()

    val PAGE_STATE_SCRIPT = """
(function () {
  $GUARD
  $COACH_MAP_JS
  return JSON.stringify({
    path: location.pathname,
    cards: document.querySelectorAll(${js(CARD)}).length,
    changeSeat: !!document.querySelector(${js(CHANGE_SEAT)}),
    coachMap: coachMap,
    saleId: new URLSearchParams(location.search).has("saleId")
  });
})()
""".trimIndent()

    private val CLOCK_JS = """
  var clock = function (el) {
    if (!el) return "";
    var m = (el.textContent || "").trim().match(/([0-9]{1,2}:[0-9]{2})\s*$/);
    return m ? m[1] : "";
  };
""".trimIndent()

    /** Clicks the nth matching train card. */
    fun clickTrainScript(departure: String, arrival: String, trainNumber: String, nth: Int): String {
        require(CLOCK.matches(departure) && CLOCK.matches(arrival) && TRAIN_NUMBER.matches(trainNumber) && nth in 0..20)
        return """
(function () {
  $GUARD
  $CLOCK_JS
  var cards = Array.prototype.slice.call(document.querySelectorAll(${js(CARD)}));
  if (!cards.length) return "nocards";
  var matches = cards.filter(function (c) {
    var nums = (c.getAttribute("data-train-numbers") || "").match(/[0-9]+/g) || [];
    return clock(c.querySelector('[data-testid="connection-list-item__time_departure"]')) === ${js(departure)}
      && clock(c.querySelector('[data-testid="connection-list-item__time_arrival"]')) === ${js(arrival)}
      && nums[0] === ${js(trainNumber)};
  });
  if (matches.length <= $nth) return "notfound";
  var card = matches[$nth];
  if (card.getAttribute("aria-disabled") === "true") return "disabled";
  card.scrollIntoView({ block: "center" });
  card.click();
  return "clicked";
})()
""".trimIndent()
    }

    /** Opens the coach map with "Change seat" unless it is already open. */
    val CLICK_CHANGE_SEAT_SCRIPT = """
(function () {
  $GUARD
  $COACH_MAP_JS
  if (coachMap) return "open";
  var b = document.querySelector(${js(CHANGE_SEAT)});
  if (!b) return "absent";
  b.scrollIntoView({ block: "center" });
  b.click();
  return "clicked";
})()
""".trimIndent()

    val PAGE_TEXT_SCRIPT = """
(function () {
  $GUARD
  return (document.body && document.body.innerText || "").slice(0, 30000);
})()
""".trimIndent()

    /** Reads the train cards of a results page. */
    val READ_CARDS_SCRIPT = """
(function () {
  $GUARD
  $CLOCK_JS
  var txt = function (el) { return el ? (el.textContent || "").trim().slice(0, 60) : ""; };
  var marker = null;
  var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  while (walker.nextNode()) {
    if ((walker.currentNode.nodeValue || "").trim().indexOf(${js(NIGHT_TRAFFIC_TEXT)}) === 0) { marker = walker.currentNode.parentElement; break; }
  }
  var cards = Array.prototype.slice.call(document.querySelectorAll(${js(CARD)}));
  return JSON.stringify(cards.slice(0, 200).map(function (c) {
    return {
      dep: clock(c.querySelector('[data-testid="connection-list-item__time_departure"]')),
      arr: clock(c.querySelector('[data-testid="connection-list-item__time_arrival"]')),
      numbers: (c.getAttribute("data-train-numbers") || "").slice(0, 80),
      types: (c.getAttribute("data-train-types") || "").slice(0, 80),
      transfers: txt(c.querySelector('[data-testid="connection-list-item__transferCount"]')),
      price: txt(c.querySelector('[data-testid="price__price-value"]')),
      disabled: c.getAttribute("aria-disabled") === "true",
      night: !!(marker && (marker.compareDocumentPosition(c) & Node.DOCUMENT_POSITION_FOLLOWING))
    };
  }));
})()
""".trimIndent()

    // ---------------------------------------------------------------- parsing

    data class FetchReply(val status: Int, val contentType: String, val body: String)

    /** Parses what [fetchPollScript] returns. Null for a script-side error. */
    fun parseFetchReply(raw: String): FetchReply? {
        val o = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
        if (o.str("state") != "done") return null
        val status = (o["status"] as? JsonPrimitive)?.intOrNull ?: return null
        return FetchReply(status, o.str("ct").orEmpty().take(100), o.str("body").orEmpty())
    }

    fun looksLikeJson(reply: FetchReply): Boolean =
        reply.contentType.contains("json", ignoreCase = true) &&
            reply.body.trimStart().let { it.startsWith("{") || it.startsWith("[") }

    sealed interface ParsedSearch {
        data class Options(val journeys: List<Journey>) : ParsedSearch
        data object NotOnSale : ParsedSearch
        data class Error(val message: String) : ParsedSearch
    }

    private val NOT_ON_SALE_HINTS = Regex("(?i)not.?on.?sale|sales?.?(period|window|range)|out.?of.?range|too.?far|future|in the past|date")

    fun parseSearch(body: String): ParsedSearch {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull()
            ?: return ParsedSearch.Error("Unreadable answer")
        val first = when (root) {
            is JsonArray -> root.firstOrNull() as? JsonObject
            is JsonObject -> root
            else -> null
        } ?: return ParsedSearch.Error("Unexpected answer")
        (first["error"] as? JsonObject)?.let { err ->
            val e = (err["json"] as? JsonObject) ?: err
            val message = e.str("message").orEmpty().take(200)
            val code = (e["data"] as? JsonObject)?.str("code").orEmpty().take(60)
            return if (NOT_ON_SALE_HINTS.containsMatchIn(message) || code.contains("SALE", true)) ParsedSearch.NotOnSale
            else ParsedSearch.Error(listOf(code, message).filter { it.isNotBlank() }.joinToString(": ").ifBlank { "Search error" })
        }
        val data = first.obj("result")?.obj("data")?.let { it.obj("json") ?: it }
            ?: return ParsedSearch.Error("Unexpected answer")
        val status = data.str("status").orEmpty()
        val options = (data["options"] as? JsonArray) ?: JsonArray(emptyList())
        if (options.isEmpty() && status.contains("SALE", ignoreCase = true)) return ParsedSearch.NotOnSale
        val journeys = options.take(MAX_OPTIONS).mapNotNull { (it as? JsonObject)?.let(::parseOption) }
        return ParsedSearch.Options(journeys)
    }

    private fun parseOption(o: JsonObject): Journey? {
        val id = o.str("id")?.takeIf { OPTION_ID.matches(it) } ?: return null
        val dep = o.time("departureTime") ?: return null
        val arr = o.time("arrivalTime") ?: return null
        val price = (o["totalPrice"] as? JsonPrimitive)?.intOrNull?.takeIf { it in 0..MAX_PRICE_CENTS } ?: return null
        val legsJson = o["legs"] as? JsonArray ?: return null
        val legs = legsJson.take(10).map { (it as? JsonObject)?.let(::parseLeg) ?: return null }
        if (legs.isEmpty() || !arr.isAfter(dep)) return null
        return Journey(id, dep, arr, price, legs)
    }

    private fun parseLeg(o: JsonObject): Leg? {
        val number = o.str("trainNumber")?.takeIf { TRAIN_NUMBER.matches(it) } ?: return null
        val type = o.str("trainType")?.takeIf { TRAIN_TYPE.matches(it) } ?: return null
        val from = o.str("departureStation")?.takeIf { STATION_CODE.matches(it) } ?: return null
        val to = o.str("arrivalStation")?.takeIf { STATION_CODE.matches(it) } ?: return null
        val dep = o.time("departureTime") ?: return null
        val arr = o.time("arrivalTime") ?: return null
        return Leg(type, number, from, to, dep, arr)
    }

    private val RUSSIA = setOf("20", "RU")

    fun parseStations(body: String): List<Station> {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
        val first = when (root) {
            is JsonArray -> root.firstOrNull() as? JsonObject
            is JsonObject -> root
            else -> null
        } ?: return emptyList()
        val data = first.obj("result")?.obj("data")?.let { it.obj("json") ?: it } ?: return emptyList()
        val stations = data["stations"] ?: return emptyList()
        val items: List<JsonElement> = when (stations) {
            is JsonObject -> stations.values.toList()
            is JsonArray -> stations.toList()
            else -> emptyList()
        }
        return items.take(MAX_STATIONS).mapNotNull { e ->
            val s = e as? JsonObject ?: return@mapNotNull null
            val code = s.str("abbreviation")?.takeIf { STATION_CODE.matches(it) } ?: return@mapNotNull null
            val name = s.str("name")?.trim()?.takeIf { STATION_NAME.matches(it) } ?: return@mapNotNull null
            val type = s.str("type")
            if (type != null && type != "station") return@mapNotNull null
            val country = (s["countryCodeUic"] as? JsonPrimitive)?.contentOrNull
            if (country != null && country.uppercase() in RUSSIA) return@mapNotNull null
            val names = s.obj("names")
            val alt = listOfNotNull(names?.str("fi"), names?.str("sv"))
                .map { it.trim() }.filter { STATION_NAME.matches(it) && it != name }.distinct()
            Station(code, name, alt)
        }.distinctBy { it.code }.sortedBy { it.name }
    }

    /** Builds journeys from the cards of a results page for [date]. Values failing the formats are dropped. */
    fun parseCards(raw: String, date: LocalDate): List<Journey> {
        val arr = runCatching { json.parseToJsonElement(raw).jsonArray }.getOrNull() ?: return emptyList()
        return arr.take(MAX_OPTIONS).mapIndexedNotNull { i, e ->
            val c = e as? JsonObject ?: return@mapIndexedNotNull null
            if ((c["disabled"] as? JsonPrimitive)?.contentOrNull == "true") return@mapIndexedNotNull null
            val dep = clock(c.str("dep")) ?: return@mapIndexedNotNull null
            val arrival = clock(c.str("arr")) ?: return@mapIndexedNotNull null
            val price = priceCents(c.str("price")) ?: return@mapIndexedNotNull null
            val numbers = c.str("numbers").orEmpty().split(Regex("[^0-9]+")).filter { TRAIN_NUMBER.matches(it) }
            val types = c.str("types").orEmpty().split(Regex("[^A-Za-z0-9]+")).filter { TRAIN_TYPE.matches(it) }
            if (numbers.isEmpty()) return@mapIndexedNotNull null
            val night = (c["night"] as? JsonPrimitive)?.contentOrNull == "true"
            val depDate = if (night) date.plusDays(1) else date
            val depTime = depDate.atTime(dep).atZone(FINLAND).toOffsetDateTime()
            var arrTime = depDate.atTime(arrival).atZone(FINLAND).toOffsetDateTime()
            if (!arrTime.isAfter(depTime)) arrTime = arrTime.plusDays(1)
            val legs = numbers.mapIndexed { n, num ->
                Leg(types.getOrElse(n) { types.firstOrNull().orEmpty() }, num, "", "", depTime, arrTime)
            }
            Journey("card-$i", depTime, arrTime, price, legs)
        }
    }

    private fun clock(s: String?): LocalTime? {
        val m = s?.let { CLOCK.matchEntire(it) } ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        return if (h in 0..23 && min in 0..59) LocalTime.of(h, min) else null
    }

    fun priceCents(s: String?): Int? {
        val m = s?.replace(' ', ' ')?.trim()?.let { PRICE_TEXT.matchEntire(it) } ?: return null
        return (m.groupValues[1].toInt() * 100 + m.groupValues[2].toInt()).takeIf { it <= MAX_PRICE_CENTS }
    }

    fun isSafePath(p: String) = PATH.matches(p)

    // ---------------------------------------------------------------- JSON helpers

    private fun JsonObject.obj(k: String) = this[k] as? JsonObject
    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun JsonObject.time(k: String): OffsetDateTime? {
        val s = str(k)?.takeIf { it.length in 16..40 } ?: return null
        return try {
            OffsetDateTime.parse(s).takeIf { it.year in 2020..2100 }
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
