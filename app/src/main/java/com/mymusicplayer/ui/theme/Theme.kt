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
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
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
        darkTheme -> {
            JustBlackColorScheme
        }
        else -> {
            darkColorScheme(
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
            background = Color.Black,
            onBackground = Color.White,
            surface = Color.Black,
            onSurface = Color.White,
            surfaceVariant = Color(0xFF121212),
            onSurfaceVariant = Color(0xFFE0E0E0)
        )
    } else {
        withAccent
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.decorView.setBackgroundColor(colorScheme.background.toArgb())
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MuseMetaTypography,
        content = content
    )
}
