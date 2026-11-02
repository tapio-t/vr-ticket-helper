package io.github.tapiot.vrtickethelper.ui

import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tapiot.vrtickethelper.CheckState
import io.github.tapiot.vrtickethelper.HealthItem
import io.github.tapiot.vrtickethelper.site.VrSite
import io.github.tapiot.vrtickethelper.ui.theme.AppColors
import io.github.tapiot.vrtickethelper.web.BookingController
import io.github.tapiot.vrtickethelper.web.BookingStep
import io.github.tapiot.vrtickethelper.web.NavigationPolicy

/** Shows [webView] in place, taking it from wherever it was shown before. */
@Composable
fun WebViewHost(webView: WebView, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { webView.also { (it.parent as? ViewGroup)?.removeView(it) } },
        modifier = modifier,
        onRelease = { (it.parent as? ViewGroup)?.removeView(it) },
    )
}

@Composable
fun SecureWindow() {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        activity.window.setHideOverlayWindows(true)
        onDispose {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            activity.window.setHideOverlayWindows(false)
        }
    }
}

private const val YOUR_TURN_PREFIX = "Your turn: "

private fun trustedHost(host: String?) = host == VrSite.HOST || NavigationPolicy.isPaymentHost(host)

@Composable
fun BookingScreen(controller: BookingController, onClose: () -> Unit) {
    val ui by controller.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmClose by remember { mutableStateOf(false) }
    SecureWindow()
    DisposableEffect(controller) {
        controller.attachTo(context)
        controller.start()
        onDispose { }
    }
    BackHandler {
        if (controller.webView.canGoBack()) controller.webView.goBack() else confirmClose = true
    }

    Column(Modifier.fillMaxSize()) {
        SiteBar(ui.host, trustedHost(ui.host), onClose = { confirmClose = true })
        ProgressStrip(ui.step)
        YourTurnCard(ui.message, ui.helping, ui.paymentStarted, ui.warnings, ui.notice, controller::dismissNotice)
        WebViewHost(controller.webView, Modifier.weight(1f).fillMaxWidth())
        if (!ui.paymentStarted) {
            Row(Modifier.fillMaxWidth().background(AppColors.Surface).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { controller.stopHelping() }, enabled = ui.helping, modifier = Modifier.weight(1f)) { Text("Stop helping") }
                Button(onClick = { controller.resume() }, modifier = Modifier.weight(1f)) { Text("Continue") }
            }
        }
    }

    ui.askExternal?.let { url ->
        val scheme = url.substringBefore(':').lowercase()
        AlertDialog(
            onDismissRequest = { controller.answerExternal(false) },
            title = { Text(if (scheme == "tel") "Call this number?" else "Open another app?") },
            text = { Text(if (scheme == "tel") url.removePrefix("tel:").take(30) else "vr.fi wants to open the Play Store.") },
            confirmButton = { TextButton(onClick = { controller.answerExternal(true) }) { Text("Open") } },
            dismissButton = { TextButton(onClick = { controller.answerExternal(false) }) { Text("Cancel") } },
        )
    }
    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("Close vr.fi?") },
            text = {
                Text(
                    if (ui.paymentStarted) "vr.fi data will be cleared from the app. Your tickets are on vr.fi and in your email."
                    else "Your seat choices on vr.fi may be lost.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmClose = false; onClose() }) { Text("Close") } },
            dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Stay") } },
        )
    }
}

@Composable
private fun ProgressStrip(step: BookingStep) {
    Row(
        Modifier.fillMaxWidth().background(AppColors.Surface).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookingStep.entries.forEach { s ->
            val done = s.ordinal < step.ordinal
            val active = s == step
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(18.dp).background(
                        when {
                            active -> AppColors.SignalBlue
                            done -> AppColors.BlueContainer
                            else -> AppColors.SurfaceHigh
                        },
                        CircleShape,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (done) "✓" else "${s.ordinal + 1}", fontSize = 10.sp, color = AppColors.Text)
                }
                Text(
                    " ${s.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) AppColors.Text else AppColors.Muted,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun YourTurnCard(
    message: String,
    helping: Boolean,
    payment: Boolean,
    warnings: List<String>,
    notice: String?,
    onDismissNotice: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(AppColors.BlueContainer).padding(horizontal = 14.dp, vertical = 10.dp)) {
        val yourTurn = message.startsWith(YOUR_TURN_PREFIX)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (helping && !yourTurn) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = AppColors.BlueText)
                Text("  ", fontSize = 4.sp)
            }
            Text(
                when {
                    payment -> "PAYMENT"
                    helping && !yourTurn -> "HELPING"
                    else -> "YOUR TURN"
                },
                style = MaterialTheme.typography.titleSmall,
                color = AppColors.BlueText,
            )
        }
        Text(message.removePrefix(YOUR_TURN_PREFIX), style = MaterialTheme.typography.bodyMedium, color = AppColors.Text)
        if (warnings.isNotEmpty()) {
            Column(Modifier.padding(top = 6.dp).fillMaxWidth().background(AppColors.WarningBg).padding(8.dp)) {
                Text("CHECK BEFORE PAYING", style = MaterialTheme.typography.titleSmall, color = AppColors.Warning)
                warnings.distinct().take(6).forEach { Bullet(it, AppColors.Warning) }
            }
        }
        if (notice != null) {
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(notice, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = AppColors.Warning)
                TextButton(onClick = onDismissNotice) { Text("OK") }
            }
        }
    }
}

@Composable
fun SiteCheckScreen(webView: WebView, url: String?, onDone: () -> Unit, onCancel: () -> Unit) {
    SecureWindow()
    val host = NavigationPolicy.hostOf(url)
    Column(Modifier.fillMaxSize()) {
        SiteBar(host, host == VrSite.HOST, onClose = onCancel)
        Column(Modifier.fillMaxWidth().background(AppColors.BlueContainer).padding(14.dp)) {
            Text("VR.FI WANTS A CHECK", style = MaterialTheme.typography.titleSmall, color = AppColors.BlueText)
            Text(
                "vr.fi asked to confirm you're a person. Complete anything it shows below, then press Done to continue searching.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        WebViewHost(webView, Modifier.weight(1f).fillMaxWidth())
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(12.dp).height(48.dp)) { Text("Done") }
    }
}

@Composable
fun HealthScreen(items: List<HealthItem>, onBack: () -> Unit, onRun: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Check vr.fi", "Tests what the app needs from vr.fi", onBack)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            items.forEach { item ->
                Panel(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        when (item.state) {
                            CheckState.RUNNING -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            CheckState.OK -> Text("✓", color = AppColors.Ok, fontSize = 18.sp)
                            CheckState.FAILED -> Text("✕", color = AppColors.Error, fontSize = 18.sp)
                            CheckState.PENDING -> Text("•", color = AppColors.Muted, fontSize = 18.sp)
                        }
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(item.label, style = MaterialTheme.typography.bodyLarge)
                            if (item.detail.isNotEmpty()) Text(item.detail, style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                        }
                    }
                }
            }
        }
        val running = items.any { it.state == CheckState.RUNNING }
        Button(onClick = onRun, enabled = !running, modifier = Modifier.fillMaxWidth().padding(16.dp).height(48.dp)) { Text("Run again") }
    }
}
