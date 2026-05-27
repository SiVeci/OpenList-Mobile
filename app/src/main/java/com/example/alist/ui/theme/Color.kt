package com.example.alist.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val IndigoPrimary = Color(0xFF4F46E5)
private val IndigoOnPrimary = Color(0xFFFFFFFF)
private val IndigoPrimaryContainer = Color(0xFFE0E7FF)
private val IndigoOnPrimaryContainer = Color(0xFF1E1B4B)

private val TealSecondary = Color(0xFF0D9488)
private val TealOnSecondary = Color(0xFFFFFFFF)
private val TealSecondaryContainer = Color(0xFFCCFBF1)
private val TealOnSecondaryContainer = Color(0xFF134E4A)

private val RoseTertiary = Color(0xFFE11D48)
private val RoseOnTertiary = Color(0xFFFFFFFF)
private val RoseTertiaryContainer = Color(0xFFFFE4E6)
private val RoseOnTertiaryContainer = Color(0xFF4C0519)

private val LightBackground = Color(0xFFF8FAFC)
private val LightOnBackground = Color(0xFF0F172A)
private val LightSurface = Color(0xFFFFFFFF)
private val LightOnSurface = Color(0xFF0F172A)
private val LightSurfaceVariant = Color(0xFFEEF2FF)
private val LightOnSurfaceVariant = Color(0xFF475569)
private val LightOutline = Color(0xFFCBD5E1)
private val LightOutlineVariant = Color(0xFFE2E8F0)
private val LightInverseSurface = Color(0xFF1E293B)
private val LightInverseOnSurface = Color(0xFFF1F5F9)
private val LightInversePrimary = Color(0xFFA5B4FC)
private val LightError = Color(0xFFDC2626)
private val LightOnError = Color(0xFFFFFFFF)
private val LightErrorContainer = Color(0xFFFEE2E2)
private val LightOnErrorContainer = Color(0xFF7F1D1D)
private val LightSurfaceTint = Color(0xFF4F46E5)
private val LightScrim = Color(0xFF000000)

private val DarkBackground = Color(0xFF0F172A)
private val DarkOnBackground = Color(0xFFE2E8F0)
private val DarkSurface = Color(0xFF1E293B)
private val DarkOnSurface = Color(0xFFE2E8F0)
private val DarkSurfaceVariant = Color(0xFF1E1B4B)
private val DarkOnSurfaceVariant = Color(0xFF94A3B8)
private val DarkOutline = Color(0xFF475569)
private val DarkOutlineVariant = Color(0xFF334155)
private val DarkInverseSurface = Color(0xFFE2E8F0)
private val DarkInverseOnSurface = Color(0xFF1E293B)
private val DarkInversePrimary = Color(0xFF4F46E5)
private val DarkError = Color(0xFFF87171)
private val DarkOnError = Color(0xFF7F1D1D)
private val DarkErrorContainer = Color(0xFF7F1D1D)
private val DarkOnErrorContainer = Color(0xFFFEE2E2)
private val DarkSurfaceTint = Color(0xFF818CF8)
private val DarkScrim = Color(0xFF000000)

private val DarkPrimary = Color(0xFFA5B4FC)
private val DarkOnPrimary = Color(0xFF1E1B4B)
private val DarkPrimaryContainer = Color(0xFF3730A3)
private val DarkOnPrimaryContainer = Color(0xFFE0E7FF)

private val DarkSecondary = Color(0xFF5EEAD4)
private val DarkOnSecondary = Color(0xFF134E4A)
private val DarkSecondaryContainer = Color(0xFF115E59)
private val DarkOnSecondaryContainer = Color(0xFFCCFBF1)

private val DarkTertiary = Color(0xFFFDA4AF)
private val DarkOnTertiary = Color(0xFF4C0519)
private val DarkTertiaryContainer = Color(0xFF9F1239)
private val DarkOnTertiaryContainer = Color(0xFFFFE4E6)

val AppLightColorScheme = lightColorScheme(
    primary = IndigoPrimary,
    onPrimary = IndigoOnPrimary,
    primaryContainer = IndigoPrimaryContainer,
    onPrimaryContainer = IndigoOnPrimaryContainer,
    secondary = TealSecondary,
    onSecondary = TealOnSecondary,
    secondaryContainer = TealSecondaryContainer,
    onSecondaryContainer = TealOnSecondaryContainer,
    tertiary = RoseTertiary,
    onTertiary = RoseOnTertiary,
    tertiaryContainer = RoseTertiaryContainer,
    onTertiaryContainer = RoseOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    inversePrimary = LightInversePrimary,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    surfaceTint = LightSurfaceTint,
    scrim = LightScrim,
)

val AppDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    inversePrimary = DarkInversePrimary,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    surfaceTint = DarkSurfaceTint,
    scrim = DarkScrim,
)
