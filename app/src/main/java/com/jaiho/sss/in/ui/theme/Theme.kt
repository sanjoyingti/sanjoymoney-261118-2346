package com.jaiho.sss.`in`.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightBg = Color(0xFFFDF8EA)
private val LightSurface = Color(0xFFFFFFFF)
private val LightVariant = Color(0xFFF2ECDC)
private val LightMuted = Color(0xFF6B6656)

private val DarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = Ink,
    primaryContainer = GoldDeep,
    onPrimaryContainer = Ink,
    secondary = Green,
    onSecondary = Ink,
    background = Ink,
    onBackground = TextMain,
    surface = CardBg,
    onSurface = TextMain,
    surfaceVariant = InkSoft,
    onSurfaceVariant = TextMuted,
    outline = CardLine,
    error = Danger,
    onError = Ink
)

private val LightColors = lightColorScheme(
    primary = GoldDeep,
    onPrimary = LightBg,
    secondary = Green,
    onSecondary = LightBg,
    background = LightBg,
    onBackground = Ink,
    surface = LightSurface,
    onSurface = Ink,
    surfaceVariant = LightVariant,
    onSurfaceVariant = LightMuted,
    outline = CardLine,
    error = Danger,
    onError = LightBg
)

@Composable
fun SanjoyTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
