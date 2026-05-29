package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// Dark scheme matching an Industrial Metal theme
private val DarkColorScheme = darkColorScheme(
    primary = GoldAmber,
    secondary = WarmCopper,
    tertiary = StatusBlue,
    background = SlateDarkBg,
    surface = SurfaceCard,
    surfaceVariant = SurfaceAlt,
    onPrimary = SlateDarkBg,
    onSecondary = TextPrimary,
    onTertiary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = BorderAccent
)

// Clean and crisp Light scheme for industrial tracking
private val LightColorScheme = lightColorScheme(
    primary = GoldAmber,
    secondary = WarmCopper,
    tertiary = StatusBlue,
    background = androidx.compose.ui.graphics.Color(0xFFF8F9FA),
    surface = androidx.compose.ui.graphics.Color.White,
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFEFF1F3),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF1A1D24),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF1A1D24),
    onTertiary = androidx.compose.ui.graphics.Color.White,
    onBackground = androidx.compose.ui.graphics.Color(0xFF1A1D24),
    onSurface = androidx.compose.ui.graphics.Color(0xFF1A1D24),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF495057),
    outline = androidx.compose.ui.graphics.Color(0xFFCED4DA)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Keep standard dark branding unless user explicitly wants dynamic material colors
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
