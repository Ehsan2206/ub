package ir.hesabyar.finance.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BrandTeal = Color(0xFF08A6A6)
val BrandTealDark = Color(0xFF006B75)
val BrandNavy = Color(0xFF073B58)
val BrandBlue = Color(0xFF087FA5)
val BrandGreen = Color(0xFF0B9B63)
val BrandGold = Color(0xFFE6A817)

private val LightColors = lightColorScheme(
    primary = BrandTealDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8F5F3),
    onPrimaryContainer = Color(0xFF003B42),
    secondary = BrandGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDF7E9),
    onSecondaryContainer = Color(0xFF064B31),
    tertiary = BrandBlue,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD8EEF7),
    onTertiaryContainer = BrandNavy,
    background = Color(0xFFF4F8FA),
    onBackground = Color(0xFF15242C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF15242C),
    surfaceVariant = Color(0xFFEDF3F5),
    onSurfaceVariant = Color(0xFF5B6C75),
    outline = Color(0xFFB9C8CE),
    error = Color(0xFFC62828),
    errorContainer = Color(0xFFFFE1E1),
    onErrorContainer = Color(0xFF7A1010)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF61DDD8),
    onPrimary = Color(0xFF003A3D),
    primaryContainer = Color(0xFF07545D),
    onPrimaryContainer = Color(0xFFD8F5F3),
    secondary = Color(0xFF74DDAE),
    onSecondary = Color(0xFF053C28),
    secondaryContainer = Color(0xFF164D38),
    onSecondaryContainer = Color(0xFFDDF7E9),
    tertiary = Color(0xFF75C7E5),
    onTertiary = Color(0xFF08384D),
    background = Color(0xFF0B161C),
    onBackground = Color(0xFFE6F0F3),
    surface = Color(0xFF112129),
    onSurface = Color(0xFFE6F0F3),
    surfaceVariant = Color(0xFF1B3039),
    onSurfaceVariant = Color(0xFFB9CAD1),
    outline = Color(0xFF59727D),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF7B2924),
    onErrorContainer = Color(0xFFFFDAD5)
)

@Composable
fun HesabYarTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
