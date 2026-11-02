package io.github.tapiot.vrtickethelper.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SignalBlue = Color(0xFF1D5BD8)
private val CoolGrey = Color(0xFFEEF1F4)

private val LightColors = lightColorScheme(
    primary = SignalBlue,
    onPrimary = Color.White,
    background = CoolGrey,
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FB0FF),
    onPrimary = Color(0xFF00296B),
)

@Composable
fun VrTicketHelperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
