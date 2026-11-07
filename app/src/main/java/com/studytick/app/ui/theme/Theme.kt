package com.studytick.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BrandBlue = Color(0xFF2F6BFF)
val BrandBlueSoft = Color(0xFFEDF2FF)
val BrandBlueDeep = Color(0xFF1B4FD8)

private val LightScheme = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = BrandBlueSoft,
    onPrimaryContainer = Color(0xFF12326F),
    secondary = Color(0xFF5B6B87),
    onSecondary = Color.White,
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF0F1620),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F1620),
    surfaceVariant = Color(0xFFEFF2F8),
    onSurfaceVariant = Color(0xFF7A8397),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    outline = Color(0xFFE4E8F0),
    outlineVariant = Color(0xFFEFF1F6),
    error = Color(0xFFE5484D),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7FA3FF),
    onPrimary = Color(0xFF06122C),
    primaryContainer = Color(0xFF1A2B4F),
    onPrimaryContainer = Color(0xFFD9E4FF),
    secondary = Color(0xFF9AA7C0),
    onSecondary = Color(0xFF10151D),
    background = Color(0xFF0B0E13),
    onBackground = Color(0xFFEDF0F7),
    surface = Color(0xFF141922),
    onSurface = Color(0xFFEDF0F7),
    surfaceVariant = Color(0xFF1C222D),
    onSurfaceVariant = Color(0xFF8C96A8),
    surfaceContainerHigh = Color(0xFF161C25),
    outline = Color(0xFF262E3B),
    outlineVariant = Color(0xFF1E2531),
    error = Color(0xFFFF6B6B),
)

private val StudyTypography = Typography()

@Composable
fun StudyTickTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = StudyTypography,
        content = content,
    )
}
