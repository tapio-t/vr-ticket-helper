package io.github.tapiot.vrtickethelper.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.tapiot.vrtickethelper.R

object AppColors {
    val Background = Color(0xFF0E1217)
    val Surface = Color(0xFF171C23)
    val SurfaceHigh = Color(0xFF202731)
    val Outline = Color(0xFF34404D)
    val Text = Color(0xFFE6EAEF)
    val Muted = Color(0xFF9AA6B2)
    val SignalBlue = Color(0xFF1D5BD8)
    val BlueText = Color(0xFF86A9FF)
    val BlueContainer = Color(0xFF14295A)
    val AmberBg = Color(0xFF3A2E0E)
    val AmberText = Color(0xFFFFD27A)
    val Warning = Color(0xFFFFB454)
    val WarningBg = Color(0xFF3D2A0C)
    val Error = Color(0xFFFF8A80)
    val Ok = Color(0xFF7FD69B)
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private val variableSans = { w: Int ->
    Font(R.font.ibm_plex_sans, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
}

val PlexSans = FontFamily(variableSans(400), variableSans(500), variableSans(600))
val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
)
val Barlow = FontFamily(
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
)

val MonoStyle = TextStyle(fontFamily = PlexMono, fontSize = 15.sp)

private val base = Typography()

private val AppTypography = Typography(
    displaySmall = base.displaySmall.copy(fontFamily = Barlow, fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Barlow, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
    headlineSmall = base.headlineSmall.copy(fontFamily = Barlow, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
    titleLarge = base.titleLarge.copy(fontFamily = Barlow, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
    titleMedium = base.titleMedium.copy(fontFamily = Barlow, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, letterSpacing = 0.6.sp),
    titleSmall = base.titleSmall.copy(fontFamily = Barlow, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.8.sp),
    bodyLarge = base.bodyLarge.copy(fontFamily = PlexSans),
    bodyMedium = base.bodyMedium.copy(fontFamily = PlexSans),
    bodySmall = base.bodySmall.copy(fontFamily = PlexSans),
    labelLarge = base.labelLarge.copy(fontFamily = PlexSans, fontWeight = FontWeight.Medium),
    labelMedium = base.labelMedium.copy(fontFamily = PlexSans),
    labelSmall = base.labelSmall.copy(fontFamily = PlexSans),
)

private val DarkColors = darkColorScheme(
    primary = AppColors.SignalBlue,
    onPrimary = Color.White,
    primaryContainer = AppColors.BlueContainer,
    onPrimaryContainer = AppColors.BlueText,
    secondary = AppColors.BlueText,
    onSecondary = Color(0xFF0A1D47),
    background = AppColors.Background,
    onBackground = AppColors.Text,
    surface = AppColors.Surface,
    onSurface = AppColors.Text,
    surfaceVariant = AppColors.SurfaceHigh,
    onSurfaceVariant = AppColors.Muted,
    surfaceContainer = AppColors.Surface,
    surfaceContainerHigh = AppColors.SurfaceHigh,
    surfaceContainerHighest = AppColors.SurfaceHigh,
    surfaceContainerLow = AppColors.Surface,
    outline = AppColors.Outline,
    outlineVariant = AppColors.Outline,
    error = AppColors.Error,
)

/** The app is always dark. */
@Composable
fun VrTicketHelperTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, typography = AppTypography, content = content)
}
