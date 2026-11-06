@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
package app.propcare.homeos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.propcare.homeos.R

// Brand palette. The only place in the UI where raw colours appear.
private val Navy = Color(0xFF0E2F78)
private val NavyLight = Color(0xFFB4C5FF)
private val HiVis = Color(0xFF1E90E6)
private val Paper = Color.White
private val Ink = Color(0xFF1A1C22)
private val NightPaper = Color(0xFF0F1626)

private val Light = lightColorScheme(
    primary = Navy, onPrimary = Color.White,
    primaryContainer = Color(0xFFDAE2FF), onPrimaryContainer = Color(0xFF0F1A33),
    secondary = Navy, onSecondary = Color.White,
    secondaryContainer = Color(0xFFEAF3FC), onSecondaryContainer = Navy,
    tertiary = Color(0xFF3B5BA9), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDE1FF), onTertiaryContainer = Color(0xFF001550),
    error = Color(0xFFB3261E), onError = Color.White,
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
    background = Paper, onBackground = Ink,
    surface = Paper, onSurface = Ink,
    surfaceVariant = Color(0xFFE1E3EA), onSurfaceVariant = Color(0xFF44474F),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFAFBFD),
    surfaceContainer = Color(0xFFEEF0F5), surfaceContainerHigh = Color(0xFFE8EBF1), surfaceContainerHighest = Color(0xFFE2E5EC),
    outline = Color(0xFF74777F), outlineVariant = Color(0xFFC4C6D0),
    inverseSurface = Color(0xFF2F3036), inverseOnSurface = Color(0xFFF1F0F7), inversePrimary = NavyLight,
)

private val Dark = darkColorScheme(
    primary = NavyLight, onPrimary = Color(0xFF13234A),
    primaryContainer = Color(0xFF2C3B5E), onPrimaryContainer = Color(0xFFDAE2FF),
    secondary = NavyLight, onSecondary = Color(0xFF08254B),
    secondaryContainer = Color(0xFF213F63), onSecondaryContainer = Color(0xFFD4E8FF),
    tertiary = Color(0xFFB8C4FF), onTertiary = Color(0xFF002780),
    tertiaryContainer = Color(0xFF213F8F), onTertiaryContainer = Color(0xFFDDE1FF),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = NightPaper, onBackground = Color(0xFFE3E5EC),
    surface = NightPaper, onSurface = Color(0xFFE3E5EC),
    surfaceVariant = Color(0xFF2A3245), onSurfaceVariant = Color(0xFFC4C6D0),
    surfaceContainerLowest = Color(0xFF0A101D), surfaceContainerLow = Color(0xFF151D2E),
    surfaceContainer = Color(0xFF192133), surfaceContainerHigh = Color(0xFF232B3E), surfaceContainerHighest = Color(0xFF2E3649),
    outline = Color(0xFF8E9099), outlineVariant = Color(0xFF44474F),
    inverseSurface = Color(0xFFE3E5EC), inverseOnSurface = Color(0xFF2F3036), inversePrimary = Navy,
)

/** Status colours Material has no role for: emergency (full red screen), warning (due soon) and OK. */
@Immutable
data class StatusColors(
    val emergency: Color, val onEmergency: Color,
    val warning: Color, val onWarning: Color,
    val ok: Color, val onOk: Color,
    /** Outline drawn on photos where the AI found the issue. */
    val highlight: Color,
)

private val LightStatus = StatusColors(
    emergency = Color(0xFFB3261E), onEmergency = Color.White,
    warning = Color(0xFFFFDDA6), onWarning = Color(0xFF2B1700),
    ok = Color(0xFFC8EBCF), onOk = Color(0xFF00210B),
    highlight = HiVis,
)
private val DarkStatus = StatusColors(
    emergency = Color(0xFF8C1D18), onEmergency = Color.White,
    warning = Color(0xFF5E4100), onWarning = Color(0xFFFFDDA6),
    ok = Color(0xFF1E5130), onOk = Color(0xFFC8EBCF),
    highlight = HiVis,
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

object PropCareTheme {
    val status: StatusColors @Composable get() = LocalStatusColors.current
}

/** Brand theme, light and dark. No dynamic colour: PropCare's navy and hi-vis are the brand. */
@Composable
fun PropCareTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = if (darkTheme) Dark else Light, typography = BrandTypography,
            shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(22.dp)), content = content)
    }
}

/** Supplied company logo: white, royal blue, cyan. Quiet surfaces and one clear main action. */
object DemoColors {
    val photoText = Color.White
    val photoScrim = Color(0xFF061D4B)
    val photoGlass = Color.White.copy(alpha = .16f)
}

private val Inter = FontFamily(
    Font(R.font.inter, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.inter, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.inter, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.inter, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)
private val BaseType = Typography()
private val BrandTypography = Typography(
    displayLarge = BaseType.displayLarge.copy(fontFamily = Inter, fontWeight = FontWeight.Medium, letterSpacing = (-2).sp),
    displayMedium = BaseType.displayMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    displaySmall = BaseType.displaySmall.copy(fontFamily = Inter, fontWeight = FontWeight.Medium, letterSpacing = (-1).sp),
    headlineLarge = BaseType.headlineLarge.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp),
    headlineMedium = BaseType.headlineMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Medium, letterSpacing = (-.7).sp),
    headlineSmall = BaseType.headlineSmall.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    titleLarge = BaseType.titleLarge.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    titleMedium = BaseType.titleMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    titleSmall = BaseType.titleSmall.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    bodyLarge = BaseType.bodyLarge.copy(fontFamily = Inter), bodyMedium = BaseType.bodyMedium.copy(fontFamily = Inter),
    bodySmall = BaseType.bodySmall.copy(fontFamily = Inter),
    labelLarge = BaseType.labelLarge.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    labelMedium = BaseType.labelMedium.copy(fontFamily = Inter), labelSmall = BaseType.labelSmall.copy(fontFamily = Inter),
)
