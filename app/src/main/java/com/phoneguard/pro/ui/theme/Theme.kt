package com.phoneguard.pro.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val PgBg = Color(0xFF05070A)
val PgSurface = Color(0xFF0B1017)
val PgSurface2 = Color(0xFF111923)
val PgOutline = Color(0xFF1C2836)
val PgBlue = Color(0xFF2F80FF)
val PgCyan = Color(0xFF00E5FF)
val PgText = Color(0xFFE8F1FF)
val PgTextDim = Color(0xFF8A9BB0)
val PgSuccess = Color(0xFF2EE59D)
val PgWarning = Color(0xFFFFB020)
val PgDanger = Color(0xFFFF5370)

val PgAccentBrush: Brush get() = Brush.linearGradient(listOf(PgBlue, PgCyan))

fun scoreColor(score: Int?): Color = when {
    score == null -> PgTextDim
    score >= 85 -> PgSuccess
    score >= 70 -> PgCyan
    score >= 50 -> PgWarning
    else -> PgDanger
}

private val DarkScheme = darkColorScheme(
    primary = PgBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0E2A55),
    onPrimaryContainer = PgCyan,
    secondary = PgCyan,
    onSecondary = Color.Black,
    background = PgBg,
    onBackground = PgText,
    surface = PgSurface,
    onSurface = PgText,
    surfaceVariant = PgSurface2,
    onSurfaceVariant = PgTextDim,
    surfaceContainer = PgSurface,
    surfaceContainerHigh = PgSurface2,
    outline = PgOutline,
    error = PgDanger
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.5.sp)
)

@Composable
fun PhoneGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, typography = AppTypography, content = content)
}
