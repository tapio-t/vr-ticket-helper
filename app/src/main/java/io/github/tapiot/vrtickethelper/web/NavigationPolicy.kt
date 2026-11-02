package io.github.tapiot.vrtickethelper.web

import io.github.tapiot.vrtickethelper.site.VrSite
import java.net.URI

enum class NavDecision {
    ALLOW,
    /** Allow, and treat the booking as being in payment from now on. */
    ALLOW_START_PAYMENT,
    BLOCK,
    /** Hand to another app (bank app) without asking. */
    OPEN_EXTERNAL,
    /** Hand to another app after asking the user. */
    ASK_EXTERNAL,
}

class NavigationPolicy(private val allowPayment: Boolean) {

    var paymentStarted: Boolean = false
        private set

    fun decide(url: String, hasGesture: Boolean, currentHost: String?): NavDecision {
        val uri = try { URI(url) } catch (_: Exception) { return NavDecision.BLOCK }
        val scheme = uri.scheme?.lowercase() ?: return NavDecision.BLOCK
        return when (scheme) {
            "https" -> https(uri.host?.lowercase(), currentHost?.lowercase())
            "http", "file", "content", "javascript", "data", "blob", "about", "ftp", "ws", "wss" -> NavDecision.BLOCK
            "tel", "market" -> if (hasGesture) NavDecision.ASK_EXTERNAL else NavDecision.BLOCK
            else -> if (paymentStarted && hasGesture) NavDecision.OPEN_EXTERNAL else NavDecision.BLOCK
        }
    }

    private fun https(host: String?, currentHost: String?): NavDecision = when {
        host == null -> NavDecision.BLOCK
        host == VrSite.HOST -> NavDecision.ALLOW
        paymentStarted -> NavDecision.ALLOW
        allowPayment && isPaymentHost(host) && currentHost == VrSite.HOST -> NavDecision.ALLOW_START_PAYMENT
        else -> NavDecision.BLOCK
    }

    fun markPaymentStarted() {
        paymentStarted = true
    }

    companion object {
        fun isPaymentHost(host: String?): Boolean {
            val h = host?.lowercase() ?: return false
            return VrSite.PAYMENT_HOSTS.any { h == it || h.endsWith(".$it") }
        }

        fun hostOf(url: String?): String? = try {
            url?.let { URI(it).host?.lowercase() }
        } catch (_: Exception) {
            null
        }
    }
}
