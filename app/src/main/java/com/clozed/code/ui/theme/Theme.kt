package com.clozed.code.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkScheme = darkColorScheme(
    primary = Brand,
    onPrimary = OnBrand,
    primaryContainer = BrandDim,
    onPrimaryContainer = TextHi,
    secondary = InfoBlue,
    onSecondary = Ink,
    background = Ink,
    onBackground = TextHi,
    surface = Surface1,
    onSurface = TextHi,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextLo,
    outline = Outline,
    error = ErrorRed,
    onError = Ink,
)

@Composable
fun ClozedTheme(
    @Suppress("UNUSED_PARAMETER") dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = ClozedTypography,
        content = content,
    )
}
