package com.cinewords.nativeapp

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The same Material 3 seed and expressive corner radii as Flutter's AppTheme. */
object CineColors {
    val Violet = Color(0xFF7C6CFF)
    val Teal = Color(0xFF00BFA5)
    val Amber = Color(0xFFFFB74D)
    val Coral = Color(0xFFFF6B4A)
    val HeroDark = Color(0xFF1C1B22)
    val HeroDarkAlt = Color(0xFF141318)
    val CardDark = Color(0xFF16151C)
}

private val lightColors = lightColorScheme(
    primary = Color(0xFF6750A4), onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDFF), onPrimaryContainer = Color(0xFF220F47),
    secondary = Color(0xFF006B5D), onSecondary = Color.White,
    secondaryContainer = Color(0xFF9CF1DC), onSecondaryContainer = Color(0xFF002019),
    tertiary = Color(0xFF825500), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB5), onTertiaryContainer = Color(0xFF291800),
    background = Color(0xFFFFFBFE), surface = Color(0xFFFFFBFE),
    surfaceContainer = Color(0xFFF2EDF5), surfaceContainerLow = Color(0xFFF8F3FA),
    surfaceContainerHighest = Color(0xFFE7E0EC),
    onSurface = Color(0xFF1D1B20), onSurfaceVariant = Color(0xFF49454F),
    outlineVariant = Color(0xFFCAC4D0)
)

private val darkColors = darkColorScheme(
    primary = Color(0xFFD0BCFF), onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B), onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFF5DDCC4), onSecondary = Color(0xFF00382E),
    secondaryContainer = Color(0xFF005047), onSecondaryContainer = Color(0xFF9CF1DC),
    tertiary = Color(0xFFFFB74D), onTertiary = Color(0xFF452B00),
    tertiaryContainer = Color(0xFF634000), onTertiaryContainer = Color(0xFFFFDDB5),
    background = Color(0xFF121116), surface = Color(0xFF121116),
    surfaceContainerLow = Color(0xFF1C1B22), surfaceContainer = Color(0xFF242329),
    surfaceContainerHighest = Color(0xFF38363F),
    onSurface = Color(0xFFE9E1E9), onSurfaceVariant = Color(0xFFCBC4D0),
    outlineVariant = Color(0xFF49454F)
)

private val monoColors = darkColorScheme(
    primary = Color.White, onPrimary = Color.Black,
    primaryContainer = Color(0xFF2C2C2C), onPrimaryContainer = Color.White,
    secondary = Color(0xFFEEEEEE), onSecondary = Color.Black,
    secondaryContainer = Color(0xFF2C2C2C), onSecondaryContainer = Color.White,
    tertiary = Color(0xFFCCCCCC), onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF2C2C2C), onTertiaryContainer = Color.White,
    surface = Color.Black, background = Color.Black, onSurface = Color.White,
    surfaceContainerLow = Color(0xFF121212),
    surfaceContainer = Color(0xFF1E1E1E),
    surfaceContainerHighest = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFB0B0B0),
    outline = Color(0xFF404040), outlineVariant = Color(0xFF2A2A2A)
)

/** Plus Jakarta Sans/Inter proportions approximated with system sans until bundled fonts are available. */
private val typography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 57.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 45.sp),
    displaySmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 36.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.1.sp)
)

private val expressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun CineWordsTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        "light" -> false
        "dark", "monochrome" -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val scheme = when {
        mode == "monochrome" -> monoColors
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> darkColors
        else -> lightColors
    }
    MaterialTheme(colorScheme = scheme, typography = typography,
        shapes = expressiveShapes, content = content)
}
