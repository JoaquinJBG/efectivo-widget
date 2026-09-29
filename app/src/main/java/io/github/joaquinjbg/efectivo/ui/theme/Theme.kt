package io.github.joaquinjbg.efectivo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Bg = Color(0xFF0E0E11)
val Surface1 = Color(0xFF1A1A20)
val Surface2 = Color(0xFF26262E)
val Accent = Color(0xFF35D07F)
val Danger = Color(0xFFFF5C5C)
val OnDark = Color(0xFFF2F2F5)
val Muted = Color(0xFF9A9AA6)

private val Scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF00210F),
    secondary = Surface2,
    onSecondary = OnDark,
    background = Bg,
    onBackground = OnDark,
    surface = Surface1,
    onSurface = OnDark,
    surfaceVariant = Surface2,
    onSurfaceVariant = Muted,
    error = Danger,
    onError = Color(0xFF2A0000),
    outline = Color(0xFF3A3A44),
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 18.sp),
    bodyMedium = TextStyle(fontSize = 16.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun EfectivoTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, content = content)
}
