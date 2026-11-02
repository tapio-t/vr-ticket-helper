package io.github.tapiot.vrtickethelper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tapiot.vrtickethelper.ui.BookingScreen
import io.github.tapiot.vrtickethelper.ui.HealthScreen
import io.github.tapiot.vrtickethelper.ui.RangeScreen
import io.github.tapiot.vrtickethelper.ui.SearchActions
import io.github.tapiot.vrtickethelper.ui.SearchScreen
import io.github.tapiot.vrtickethelper.ui.SiteCheckScreen
import io.github.tapiot.vrtickethelper.ui.TrainsScreen
import io.github.tapiot.vrtickethelper.ui.WebViewHost
import io.github.tapiot.vrtickethelper.ui.theme.AppColors
import io.github.tapiot.vrtickethelper.ui.theme.VrTicketHelperTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm.session.attachTo(this)
        setContent {
            VrTicketHelperTheme {
                AppRoot(vm)
            }
        }
    }

    override fun onDestroy() {
        vm.session.detach()
        super.onDestroy()
    }
}

@Composable
fun AppRoot(vm: AppViewModel) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val recent by vm.recentRoutes.collectAsStateWithLifecycle()
    val historyOn by vm.historyEnabled.collectAsStateWithLifecycle()
    val fareUrl by vm.session.currentUrl.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.dismissMessage()
        }
    }

    val actions = remember(vm) {
        SearchActions(
            update = vm::updateForm,
            swap = vm::swap,
            find = vm::findTrains,
            useRecent = vm::useRecent,
            openHealth = vm::openHealth,
            clearSiteData = { vm.clearSiteData() },
            clearHistory = { vm.clearHistory() },
            setHistory = { vm.setHistoryEnabled(it) },
            reloadStations = { vm.loadStations(force = true) },
        )
    }

    BackHandler(enabled = state.stack.size > 1 && state.screen != Screen.Booking) { vm.back() }

    Box(Modifier.fillMaxSize().background(AppColors.Background).safeDrawingPadding()) {
        val screen = state.screen
        if (screen != Screen.SiteCheck) {
            WebViewHost(vm.session.webView, Modifier.size(1.dp).alpha(0f))
        }
        when (screen) {
            Screen.Search -> SearchScreen(state, recent, historyOn, actions)
            is Screen.Trains -> TrainsScreen(
                screen, state.form.from, state.form.to, state.bookingAllowed,
                onBack = vm::back, onOut = vm::selectOut, onRet = vm::selectRet, onBook = vm::startBooking,
            )
            Screen.Range -> RangeScreen(state.range, state.form.from, state.form.to, state.progress, state.form.nights, vm::back, vm::openDay)
            Screen.Booking -> vm.booking?.let { BookingScreen(it, onClose = vm::back) }
            Screen.SiteCheck -> SiteCheckScreen(vm.session.webView, fareUrl, onDone = vm::checkDone, onCancel = vm::back)
            Screen.Health -> HealthScreen(state.health, vm::back, vm::runHealth)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
