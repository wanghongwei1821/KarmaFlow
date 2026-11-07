package com.example.sizhang.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF22559D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E8FF),
    onPrimaryContainer = Color(0xFF102D57),
    secondary = Color(0xFF995022),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE8D9),
    onSecondaryContainer = Color(0xFF55240A),
    tertiary = Color(0xFF5267A5),
    tertiaryContainer = Color(0xFFDDE4FF),
    background = Color(0xFFEDF2F8),
    onBackground = Color(0xFF18263B),
    surface = Color(0xFFF9FBFF),
    onSurface = Color(0xFF18263B),
    surfaceVariant = Color(0xFFE4ECF7),
    onSurfaceVariant = Color(0xFF485A73),
    outline = Color(0xFF647891),
    outlineVariant = Color(0xFFCFDBEB),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C9FF),
    onPrimary = Color(0xFF102A51),
    primaryContainer = Color(0xFF234980),
    onPrimaryContainer = Color(0xFFE2EDFF),
    secondary = Color(0xFFFFB68A),
    secondaryContainer = Color(0xFF743817),
    onSecondaryContainer = Color(0xFFFFDCC8),
    tertiary = Color(0xFFBAC6FF),
    background = Color(0xFF111A29),
    onBackground = Color(0xFFEDF2F8),
    surface = Color(0xFF182438),
    onSurface = Color(0xFFEDF2F8),
    surfaceVariant = Color(0xFF24344D),
    onSurfaceVariant = Color(0xFFBDCCE1),
    outline = Color(0xFF8C9DB5),
    outlineVariant = Color(0xFF3A4B65),
)

private val LedgerTypography = Typography(
    headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Medium),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold),
)

private val LedgerShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
)

@Composable
fun PrivateLedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = LedgerTypography,
        shapes = LedgerShapes,
        content = content,
    )
}
