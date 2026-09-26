package com.example.circleplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

data class PlayerPalette(
    val background: Color,
    val surface: Color,
    val discPanel: Color,
    val discBody: Color,
    val discRim: Color,
    val discHole: Color,
    val discHoleInner: Color,
    val wheel: Color,
    val wheelEdge: Color,
    val wheelIcon: Color,
    val centerButton: Color,
    val centerIcon: Color,
    val sideButton: Color,
    val sideButtonIcon: Color,
    val chipBackground: Color,
    val chipText: Color,
    val text: Color,
    val textSecondary: Color,
    val progressTrack: Color,
    val progressActive: Color,
    val listBackground: Color,
    val listRowSelected: Color,
    val divider: Color
)

val LightPalette = PlayerPalette(
    background = Color(0xFFECEAE6),
    surface = Color(0xFFFFFFFF),
    discPanel = Color(0xFFFFFFFF),
    discBody = Color(0xFF5B5B5B),
    discRim = Color(0xFF424242),
    discHole = Color(0xFFE2E0DC),
    discHoleInner = Color(0xFF6B6B6B),
    wheel = Color(0xFFD6D3CE),
    wheelEdge = Color(0xFFA9A5A0),
    wheelIcon = Color(0xFF57534F),
    centerButton = Color(0xFF57534F),
    centerIcon = Color(0xFFF5F4F1),
    sideButton = Color(0xFFE4E1DC),
    sideButtonIcon = Color(0xFF47433F),
    chipBackground = Color(0xFF57534F),
    chipText = Color(0xFFE8E6E2),
    text = Color(0xFF37342F),
    textSecondary = Color(0xFF6E6B66),
    progressTrack = Color(0xFF57534F),
    progressActive = Color(0xFF6E6B66),
    listBackground = Color(0xFFECEAE6),
    listRowSelected = Color(0xFFF8F7F4),
    divider = Color(0xFFD5D2CC)
)

val DarkPalette = PlayerPalette(
    background = Color(0xFF3B3B3B),
    surface = Color(0xFF474747),
    discPanel = Color(0xFFA8A8A8),
    discBody = Color(0xFF474747),
    discRim = Color(0xFF383838),
    discHole = Color(0xFFBDBDBD),
    discHoleInner = Color(0xFF555555),
    wheel = Color(0xFF9A9A9A),
    wheelEdge = Color(0xFF707070),
    wheelIcon = Color(0xFF454545),
    centerButton = Color(0xFF454545),
    centerIcon = Color(0xFFDCDCDC),
    sideButton = Color(0xFFADADAD),
    sideButtonIcon = Color(0xFF3F3F3F),
    chipBackground = Color(0xFF474747),
    chipText = Color(0xFFCFCFCF),
    text = Color(0xFFDCDCDC),
    textSecondary = Color(0xFFB5B5B5),
    progressTrack = Color(0xFF5E5E5E),
    progressActive = Color(0xFFD2D2D2),
    listBackground = Color(0xFF3B3B3B),
    listRowSelected = Color(0xFF5D5D5D),
    divider = Color(0xFF565656)
)

val LocalPlayerPalette = staticCompositionLocalOf { LightPalette }

private fun readableForeground(background: Color, palette: PlayerPalette): Color =
    listOf(palette.background, palette.text, palette.textSecondary, palette.centerIcon, Color.Black, Color.White)
        .maxBy { candidate ->
            val backgroundLuminance = background.luminance()
            val foregroundLuminance = candidate.luminance()
            (maxOf(backgroundLuminance, foregroundLuminance) + 0.05f) /
                (minOf(backgroundLuminance, foregroundLuminance) + 0.05f)
        }

@Composable
private fun animatedPalette(target: PlayerPalette): PlayerPalette {
    val background by animateColorAsState(target.background, tween(280), label = "theme-background")
    val surface by animateColorAsState(target.surface, tween(280), label = "theme-surface")
    val discPanel by animateColorAsState(target.discPanel, tween(280), label = "theme-disc-panel")
    val discBody by animateColorAsState(target.discBody, tween(280), label = "theme-disc-body")
    val discRim by animateColorAsState(target.discRim, tween(280), label = "theme-disc-rim")
    val discHole by animateColorAsState(target.discHole, tween(280), label = "theme-disc-hole")
    val discHoleInner by animateColorAsState(target.discHoleInner, tween(280), label = "theme-disc-hole-inner")
    val wheel by animateColorAsState(target.wheel, tween(280), label = "theme-wheel")
    val wheelEdge by animateColorAsState(target.wheelEdge, tween(280), label = "theme-wheel-edge")
    val wheelIcon by animateColorAsState(target.wheelIcon, tween(280), label = "theme-wheel-icon")
    val centerButton by animateColorAsState(target.centerButton, tween(280), label = "theme-center-button")
    val centerIcon by animateColorAsState(target.centerIcon, tween(280), label = "theme-center-icon")
    val sideButton by animateColorAsState(target.sideButton, tween(280), label = "theme-side-button")
    val sideButtonIcon by animateColorAsState(target.sideButtonIcon, tween(280), label = "theme-side-button-icon")
    val chipBackground by animateColorAsState(target.chipBackground, tween(280), label = "theme-chip-background")
    val chipText by animateColorAsState(target.chipText, tween(280), label = "theme-chip-text")
    val text by animateColorAsState(target.text, tween(280), label = "theme-text")
    val textSecondary by animateColorAsState(target.textSecondary, tween(280), label = "theme-text-secondary")
    val progressTrack by animateColorAsState(target.progressTrack, tween(280), label = "theme-progress-track")
    val progressActive by animateColorAsState(target.progressActive, tween(280), label = "theme-progress-active")
    val listBackground by animateColorAsState(target.listBackground, tween(280), label = "theme-list-background")
    val listRowSelected by animateColorAsState(target.listRowSelected, tween(280), label = "theme-list-selected")
    val divider by animateColorAsState(target.divider, tween(280), label = "theme-divider")
    return target.copy(
        background = background,
        surface = surface,
        discPanel = discPanel,
        discBody = discBody,
        discRim = discRim,
        discHole = discHole,
        discHoleInner = discHoleInner,
        wheel = wheel,
        wheelEdge = wheelEdge,
        wheelIcon = wheelIcon,
        centerButton = centerButton,
        centerIcon = centerIcon,
        sideButton = sideButton,
        sideButtonIcon = sideButtonIcon,
        chipBackground = chipBackground,
        chipText = chipText,
        text = text,
        textSecondary = textSecondary,
        progressTrack = progressTrack,
        progressActive = progressActive,
        listBackground = listBackground,
        listRowSelected = listRowSelected,
        divider = divider
    )
}

@Composable
fun CirclePlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    lightPalette: PlayerPalette = LightPalette,
    darkPalette: PlayerPalette = DarkPalette,
    content: @Composable () -> Unit
) {
    val palette = animatedPalette(if (darkTheme) darkPalette else lightPalette)
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.progressActive,
            onPrimary = readableForeground(palette.progressActive, palette),
            secondary = palette.wheelIcon,
            onSecondary = palette.text,
            background = palette.background,
            surface = palette.surface,
            onBackground = palette.text,
            onSurface = palette.text,
            error = Color(0xFFEF9A9A)
        )
    } else {
        lightColorScheme(
            primary = palette.progressActive,
            onPrimary = readableForeground(palette.progressActive, palette),
            secondary = palette.wheelIcon,
            onSecondary = palette.text,
            background = palette.background,
            surface = palette.surface,
            onBackground = palette.text,
            onSurface = palette.text,
            error = Color(0xFFB00020)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = MaterialTheme.shapes,
        content = {
            CompositionLocalProvider(
                LocalPlayerPalette provides palette,
                content = content
            )
        }
    )
}
