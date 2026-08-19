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
    primary = Color(0xFF176B5A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8F3E9),
    onPrimaryContainer = Color(0xFF073D33),
    secondary = Color(0xFFB96A3D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE8D9),
    onSecondaryContainer = Color(0xFF55240A),
    tertiary = Color(0xFF5267A5),
    tertiaryContainer = Color(0xFFDDE4FF),
    background = Color(0xFFF6F8F5),
    onBackground = Color(0xFF17201D),
    surface = Color(0xFFFCFDFC),
    onSurface = Color(0xFF17201D),
    surfaceVariant = Color(0xFFECF1EE),
    onSurfaceVariant = Color(0xFF596661),
    outline = Color(0xFFB8C7C1),
    outlineVariant = Color(0xFFDCE5E1),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF91D7C0),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF075141),
    onPrimaryContainer = Color(0xFFB3F2DA),
    secondary = Color(0xFFFFB68A),
    secondaryContainer = Color(0xFF743817),
    tertiary = Color(0xFFBAC6FF),
    background = Color(0xFF0F1513),
    onBackground = Color(0xFFDFE7E2),
    surface = Color(0xFF171E1B),
    onSurface = Color(0xFFDFE7E2),
    surfaceVariant = Color(0xFF252E2A),
    onSurfaceVariant = Color(0xFFBAC7C1),
    outline = Color(0xFF84928C),
    outlineVariant = Color(0xFF35423D),
)

private val LedgerTypography = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
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
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
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
