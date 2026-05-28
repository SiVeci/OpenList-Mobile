package com.openlistmobile.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val BluePrimary = Color(0xFF3B82F6) // Soft Light Blue Primary
private val BlueOnPrimary = Color(0xFFFFFFFF)
private val BluePrimaryContainer = Color(0xFFDBEAFE)
private val BlueOnPrimaryContainer = Color(0xFF1E3A8A)

private val SkySecondary = Color(0xFF0EA5E9) // Soft Sky Blue Secondary
private val SkyOnSecondary = Color(0xFFFFFFFF)
private val SkySecondaryContainer = Color(0xFFE0F2FE)
private val SkyOnSecondaryContainer = Color(0xFF0369A1)

private val RoseTertiary = Color(0xFFEF5350) // Error/Logout Red
private val RoseOnTertiary = Color(0xFFFFFFFF)
private val RoseTertiaryContainer = Color(0xFFFFE4E6)
private val RoseOnTertiaryContainer = Color(0xFF4C0519)

private val LightBackground = Color(0xFFFFFFFF)
private val LightOnBackground = Color(0xFF1A1C1E)
private val LightSurface = Color(0xFFFFFFFF)
private val LightOnSurface = Color(0xFF1A1C1E)
private val LightSurfaceVariant = Color(0xFFF9F7FF) // Card background
private val LightOnSurfaceVariant = Color(0xFF474559)
private val LightOutline = Color(0xFFE2E1EC)
private val LightOutlineVariant = Color(0xFFEDECF4)
private val LightInverseSurface = Color(0xFF1A1C1E) // Selection bar background
private val LightInverseOnSurface = Color(0xFFF1F0F7)
private val LightInversePrimary = Color(0xFFBFDBFE)
private val LightError = Color(0xFFEF5350)
private val LightOnError = Color(0xFFFFFFFF)
private val LightErrorContainer = Color(0xFFFEE2E2)
private val LightOnErrorContainer = Color(0xFF7F1D1D)
private val LightSurfaceTint = Color(0xFF3B82F6)
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
private val DarkInversePrimary = Color(0xFF3B82F6)
private val DarkError = Color(0xFFF87171)
private val DarkOnError = Color(0xFF7F1D1D)
private val DarkErrorContainer = Color(0xFF7F1D1D)
private val DarkOnErrorContainer = Color(0xFFFEE2E2)
private val DarkSurfaceTint = Color(0xFF60A5FA)
private val DarkScrim = Color(0xFF000000)

private val DarkPrimary = Color(0xFF93C5FD) // Soft Light Blue Primary
private val DarkOnPrimary = Color(0xFF1E3A8A)
private val DarkPrimaryContainer = Color(0xFF1E40AF)
private val DarkOnPrimaryContainer = Color(0xFFDBEAFE)

private val DarkSecondary = Color(0xFF7DD3FC) // Soft Sky Blue Secondary
private val DarkOnSecondary = Color(0xFF0369A1)
private val DarkSecondaryContainer = Color(0xFF0C4A6E)
private val DarkOnSecondaryContainer = Color(0xFFE0F2FE)

private val DarkTertiary = Color(0xFFFDA4AF)
private val DarkOnTertiary = Color(0xFF4C0519)
private val DarkTertiaryContainer = Color(0xFF9F1239)
private val DarkOnTertiaryContainer = Color(0xFFFFE4E6)

val AppLightColorScheme = lightColorScheme(
    primary = BluePrimary,
    onPrimary = BlueOnPrimary,
    primaryContainer = BluePrimaryContainer,
    onPrimaryContainer = BlueOnPrimaryContainer,
    secondary = SkySecondary,
    onSecondary = SkyOnSecondary,
    secondaryContainer = SkySecondaryContainer,
    onSecondaryContainer = SkyOnSecondaryContainer,
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
