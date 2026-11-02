package io.github.tapiot.vrtickethelper.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationPolicyTest {

    private val vr = "www.vr.fi"

    @Test fun allowsVrFi() {
        assertEquals(NavDecision.ALLOW, NavigationPolicy(true).decide("https://www.vr.fi/en/x", false, vr))
    }

    @Test fun blocksPlainHttpAlways() {
        val p = NavigationPolicy(true)
        assertEquals(NavDecision.BLOCK, p.decide("http://www.vr.fi/", false, vr))
        p.markPaymentStarted()
        assertEquals(NavDecision.BLOCK, p.decide("http://bank.example/", true, "bank.example"))
    }

    @Test fun blocksOtherHostsBeforePayment() {
        assertEquals(NavDecision.BLOCK, NavigationPolicy(true).decide("https://evil.example/", true, vr))
        assertEquals(NavDecision.BLOCK, NavigationPolicy(true).decide("https://vr.fi.evil.example/", true, vr))
    }

    @Test fun paymentStartsOnlyFromVrFi() {
        val p = NavigationPolicy(true)
        assertEquals(NavDecision.BLOCK, p.decide("https://pay.paytrail.com/x", false, "other.example"))
        assertEquals(NavDecision.ALLOW_START_PAYMENT, p.decide("https://pay.paytrail.com/x", false, vr))
    }

    @Test fun paymentNotAllowedInFareView() {
        assertEquals(NavDecision.BLOCK, NavigationPolicy(false).decide("https://pay.paytrail.com/x", false, vr))
    }

    @Test fun anyHttpsDuringPayment() {
        val p = NavigationPolicy(true)
        p.markPaymentStarted()
        assertEquals(NavDecision.ALLOW, p.decide("https://www.bank.example/login", false, "pay.paytrail.com"))
        assertTrue(p.paymentStarted)
    }

    @Test fun appLinksOnlyDuringPaymentWithGesture() {
        val p = NavigationPolicy(true)
        val link = "intent://pay#Intent;scheme=bankapp;package=com.example.bank;end"
        assertEquals(NavDecision.BLOCK, p.decide(link, true, vr))
        p.markPaymentStarted()
        assertEquals(NavDecision.BLOCK, p.decide(link, false, "pay.paytrail.com"))
        assertEquals(NavDecision.OPEN_EXTERNAL, p.decide(link, true, "pay.paytrail.com"))
        assertEquals(NavDecision.OPEN_EXTERNAL, p.decide("bankapp://pay?x=1", true, "pay.paytrail.com"))
    }

    @Test fun telAndMarketAsk() {
        val p = NavigationPolicy(true)
        assertEquals(NavDecision.ASK_EXTERNAL, p.decide("tel:+358000000", true, vr))
        assertEquals(NavDecision.ASK_EXTERNAL, p.decide("market://details?id=x", true, vr))
        assertEquals(NavDecision.BLOCK, p.decide("tel:+358000000", false, vr))
    }

    @Test fun blocksLocalSchemes() {
        val p = NavigationPolicy(true)
        p.markPaymentStarted()
        listOf("file:///sdcard/x", "content://x/y", "javascript:alert(1)", "data:text/html,x", "about:blank").forEach {
            assertEquals(it, NavDecision.BLOCK, p.decide(it, true, vr))
        }
    }

    @Test fun paymentHostMatching() {
        assertTrue(NavigationPolicy.isPaymentHost("paytrail.com"))
        assertTrue(NavigationPolicy.isPaymentHost("services.paytrail.com"))
        assertEquals(false, NavigationPolicy.isPaymentHost("paytrail.com.evil.example"))
        assertEquals(false, NavigationPolicy.isPaymentHost("evilpaytrail.com"))
    }
}
