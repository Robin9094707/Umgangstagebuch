package app.umgangstagebuch.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.umgangstagebuch.data.ThemeMode

private val Light = lightColorScheme(
    primary = Color(0xFF7B4FA0), onPrimary = Color.White,
    primaryContainer = Color(0xFFF2DAFF), onPrimaryContainer = Color(0xFF2E004E),
    secondary = Color(0xFF9C4069), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E4), onSecondaryContainer = Color(0xFF3E0021),
    tertiary = Color(0xFF00687A), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFACEDFF), onTertiaryContainer = Color(0xFF001F26),
    background = Color(0xFFFFF7FD), onBackground = Color(0xFF1E1A20),
    surface = Color(0xFFFFF7FD), onSurface = Color(0xFF1E1A20),
    surfaceVariant = Color(0xFFEADFEA), onSurfaceVariant = Color(0xFF4B444D),
    outline = Color(0xFF7C747E), outlineVariant = Color(0xFFCDC3CE),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFAF1F9),
    surfaceContainer = Color(0xFFF4EBF3), surfaceContainerHigh = Color(0xFFEEE5ED),
    surfaceContainerHighest = Color(0xFFE9E0E8), surfaceDim = Color(0xFFE0D7DF), surfaceBright = Color(0xFFFFF7FD),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF332F35), inverseOnSurface = Color(0xFFF7EEF6), inversePrimary = Color(0xFFE0B6FF),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFE0B6FF), onPrimary = Color(0xFF4A1A6E),
    primaryContainer = Color(0xFF623687), onPrimaryContainer = Color(0xFFF2DAFF),
    secondary = Color(0xFFFFB0CC), onSecondary = Color(0xFF5E1138),
    secondaryContainer = Color(0xFF7D2950), onSecondaryContainer = Color(0xFFFFD9E4),
    tertiary = Color(0xFF84D2E6), onTertiary = Color(0xFF003641),
    tertiaryContainer = Color(0xFF004E5C), onTertiaryContainer = Color(0xFFACEDFF),
    background = Color(0xFF161218), onBackground = Color(0xFFE9E0E8),
    surface = Color(0xFF161218), onSurface = Color(0xFFE9E0E8),
    surfaceVariant = Color(0xFF4B444D), onSurfaceVariant = Color(0xFFCDC3CE),
    outline = Color(0xFF968E98), outlineVariant = Color(0xFF4B444D),
    surfaceContainerLowest = Color(0xFF110D13), surfaceContainerLow = Color(0xFF1E1A20),
    surfaceContainer = Color(0xFF231E24), surfaceContainerHigh = Color(0xFF2D282F),
    surfaceContainerHighest = Color(0xFF38333A), surfaceDim = Color(0xFF161218), surfaceBright = Color(0xFF3D373E),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE9E0E8), inverseOnSurface = Color(0xFF332F35), inversePrimary = Color(0xFF7B4FA0),
)

@Immutable
data class StatusColors(
    val good: Color, val onGood: Color, val goodContainer: Color, val onGoodContainer: Color,
    val warn: Color, val warnContainer: Color, val onWarnContainer: Color,
    val bad: Color, val badContainer: Color, val onBadContainer: Color,
    val neutralContainer: Color, val onNeutralContainer: Color,
)

private val LightStatus = StatusColors(
    good = Color(0xFF2E7D4F), onGood = Color.White, goodContainer = Color(0xFFC9F0D3), onGoodContainer = Color(0xFF00210E),
    warn = Color(0xFF8A5A00), warnContainer = Color(0xFFFFDDB0), onWarnContainer = Color(0xFF2B1700),
    bad = Color(0xFFBA1A1A), badContainer = Color(0xFFFFDAD6), onBadContainer = Color(0xFF410002),
    neutralContainer = Color(0xFFE9E0E8), onNeutralContainer = Color(0xFF4B444D),
)
private val DarkStatus = StatusColors(
    good = Color(0xFF8DD9A4), onGood = Color(0xFF00391C), goodContainer = Color(0xFF0E5130), onGoodContainer = Color(0xFFC9F0D3),
    warn = Color(0xFFFFB951), warnContainer = Color(0xFF633F00), onWarnContainer = Color(0xFFFFDDB0),
    bad = Color(0xFFFFB4AB), badContainer = Color(0xFF93000A), onBadContainer = Color(0xFFFFDAD6),
    neutralContainer = Color(0xFF38333A), onNeutralContainer = Color(0xFFCDC3CE),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private val Base = Typography()
private val AppTypography = Base.copy(
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.copy(lineHeight = 26.sp),
)

val Typography.overline: TextStyle
    get() = labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)

@Composable
fun UmgangTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, dynamicColor: Boolean = true, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatus else LightStatus) {
        MaterialExpressiveTheme(
            colorScheme = colors,
            motionScheme = MotionScheme.expressive(),
            shapes = AppShapes,
            typography = AppTypography,
            content = content,
        )
    }
}

val statusColors: StatusColors @Composable get() = LocalStatusColors.current

