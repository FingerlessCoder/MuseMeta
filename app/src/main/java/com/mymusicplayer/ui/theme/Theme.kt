package com.mymusicplayer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    error = Error,
    onError = OnError
)

private val JustBlackColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    background = Color(0xFF000000),
    onBackground = Color(0xFFE9EDF2),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFE9EDF2),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFB6C0CD),
    error = Error,
    onError = OnError
)

@Composable
fun MuseMetaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    amoledBlack: Boolean = false,
    accentPalette: AccentPalette = AccentPalettes[0],
    content: @Composable () -> Unit
) {
    val baseScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dynamicColor -> {
            dynamicDarkColorScheme(LocalContext.current)
        }
        else -> darkColorScheme(
            background = Background,
            onBackground = OnBackground,
            surface = Surface,
            onSurface = OnSurface,
            surfaceVariant = SurfaceVariant,
            onSurfaceVariant = OnSurfaceVariant,
            error = Error,
            onError = OnError
        )
    }
    val withAccent = baseScheme.copy(
        primary = accentPalette.primary,
        onPrimary = accentPalette.onPrimary,
        primaryContainer = accentPalette.primaryContainer,
        onPrimaryContainer = accentPalette.onPrimaryContainer,
        secondary = accentPalette.secondary,
        onSecondary = accentPalette.onSecondary,
        secondaryContainer = accentPalette.secondaryContainer,
        onSecondaryContainer = accentPalette.onSecondaryContainer,
        tertiary = accentPalette.tertiary,
        onTertiary = accentPalette.onTertiary,
        tertiaryContainer = accentPalette.tertiaryContainer,
        onTertiaryContainer = accentPalette.onTertiaryContainer
    )
    val colorScheme = if (amoledBlack) {
        withAccent.copy(
            background = Color(0xFF000000),
            surface = Color(0xFF000000),
            surfaceVariant = Color(0xFF1A1A1A)
        )
    } else {
        withAccent
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MuseMetaTypography,
        content = content
    )
}
