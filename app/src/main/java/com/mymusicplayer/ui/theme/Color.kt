package com.mymusicplayer.ui.theme

import androidx.compose.ui.graphics.Color

// Default accent palette (Teal)
val Primary = Color(0xFF38D6B8)
val OnPrimary = Color(0xFF052E28)
val PrimaryContainer = Color(0xFF134B43)
val OnPrimaryContainer = Color(0xFFB8FFF0)

val Secondary = Color(0xFFFFC857)
val OnSecondary = Color(0xFF322200)
val SecondaryContainer = Color(0xFF58410B)
val OnSecondaryContainer = Color(0xFFFFE7A8)

val Tertiary = Color(0xFFB4C0FF)
val OnTertiary = Color(0xFF001B75)
val TertiaryContainer = Color(0xFF0027A6)
val OnTertiaryContainer = Color(0xFFDCE1FF)

val Background = Color(0xFF090B0F)
val OnBackground = Color(0xFFE9EDF2)
val Surface = Color(0xFF12161D)
val OnSurface = Color(0xFFE9EDF2)
val SurfaceVariant = Color(0xFF202734)
val OnSurfaceVariant = Color(0xFFB6C0CD)

val Error = Color(0xFFFFB4AB)
val OnError = Color(0xFF690005)

data class AccentPalette(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color
)

val AccentPalettes = listOf(
    // 0: Default Teal
    AccentPalette(
        primary = Color(0xFF38D6B8), onPrimary = Color(0xFF052E28), primaryContainer = Color(0xFF134B43), onPrimaryContainer = Color(0xFFB8FFF0),
        secondary = Color(0xFFFFC857), onSecondary = Color(0xFF322200), secondaryContainer = Color(0xFF58410B), onSecondaryContainer = Color(0xFFFFE7A8),
        tertiary = Color(0xFFB4C0FF), onTertiary = Color(0xFF001B75), tertiaryContainer = Color(0xFF0027A6), onTertiaryContainer = Color(0xFFDCE1FF)
    ),
    // 1: Amber
    AccentPalette(
        primary = Color(0xFFFFC857), onPrimary = Color(0xFF322200), primaryContainer = Color(0xFF58410B), onPrimaryContainer = Color(0xFFFFE7A8),
        secondary = Color(0xFF38D6B8), onSecondary = Color(0xFF052E28), secondaryContainer = Color(0xFF134B43), onSecondaryContainer = Color(0xFFB8FFF0),
        tertiary = Color(0xFFB4C0FF), onTertiary = Color(0xFF001B75), tertiaryContainer = Color(0xFF0027A6), onTertiaryContainer = Color(0xFFDCE1FF)
    ),
    // 2: Pink
    AccentPalette(
        primary = Color(0xFFFF80AB), onPrimary = Color(0xFF3E0020), primaryContainer = Color(0xFF5C1136), onPrimaryContainer = Color(0xFFFFD9E3),
        secondary = Color(0xFFCF94FF), onSecondary = Color(0xFF3A0068), secondaryContainer = Color(0xFF560091), onSecondaryContainer = Color(0xFFF0D9FF),
        tertiary = Color(0xFF38D6B8), onTertiary = Color(0xFF052E28), tertiaryContainer = Color(0xFF134B43), onTertiaryContainer = Color(0xFFB8FFF0)
    ),
    // 3: Purple
    AccentPalette(
        primary = Color(0xFFCF94FF), onPrimary = Color(0xFF3A0068), primaryContainer = Color(0xFF560091), onPrimaryContainer = Color(0xFFF0D9FF),
        secondary = Color(0xFFFF80AB), onSecondary = Color(0xFF3E0020), secondaryContainer = Color(0xFF5C1136), onSecondaryContainer = Color(0xFFFFD9E3),
        tertiary = Color(0xFF82B1FF), onTertiary = Color(0xFF003258), tertiaryContainer = Color(0xFF004A7C), onTertiaryContainer = Color(0xFFD6E8FF)
    ),
    // 4: Blue
    AccentPalette(
        primary = Color(0xFF82B1FF), onPrimary = Color(0xFF003258), primaryContainer = Color(0xFF004A7C), onPrimaryContainer = Color(0xFFD6E8FF),
        secondary = Color(0xFF69F0AE), onSecondary = Color(0xFF003D1F), secondaryContainer = Color(0xFF005A31), onSecondaryContainer = Color(0xFFA7FFC3),
        tertiary = Color(0xFFCF94FF), onTertiary = Color(0xFF3A0068), tertiaryContainer = Color(0xFF560091), onTertiaryContainer = Color(0xFFF0D9FF)
    ),
    // 5: Red
    AccentPalette(
        primary = Color(0xFFFF8A80), onPrimary = Color(0xFF4E001E), primaryContainer = Color(0xFF730033), onPrimaryContainer = Color(0xFFFFD9D6),
        secondary = Color(0xFF82B1FF), onSecondary = Color(0xFF003258), secondaryContainer = Color(0xFF004A7C), onSecondaryContainer = Color(0xFFD6E8FF),
        tertiary = Color(0xFF69F0AE), onTertiary = Color(0xFF003D1F), tertiaryContainer = Color(0xFF005A31), onTertiaryContainer = Color(0xFFA7FFC3)
    ),
    // 6: Green
    AccentPalette(
        primary = Color(0xFF69F0AE), onPrimary = Color(0xFF003D1F), primaryContainer = Color(0xFF005A31), onPrimaryContainer = Color(0xFFA7FFC3),
        secondary = Color(0xFFFF8A80), onSecondary = Color(0xFF4E001E), secondaryContainer = Color(0xFF730033), onSecondaryContainer = Color(0xFFFFD9D6),
        tertiary = Color(0xFFFFC857), onTertiary = Color(0xFF322200), tertiaryContainer = Color(0xFF58410B), onTertiaryContainer = Color(0xFFFFE7A8)
    ),
    // 7: Orange
    AccentPalette(
        primary = Color(0xFFFFAB40), onPrimary = Color(0xFF3E2000), primaryContainer = Color(0xFF5C3000), onPrimaryContainer = Color(0xFFFFDCC7),
        secondary = Color(0xFF38D6B8), onSecondary = Color(0xFF052E28), secondaryContainer = Color(0xFF134B43), onSecondaryContainer = Color(0xFFB8FFF0),
        tertiary = Color(0xFFCF94FF), onTertiary = Color(0xFF3A0068), tertiaryContainer = Color(0xFF560091), onTertiaryContainer = Color(0xFFF0D9FF)
    )
)
