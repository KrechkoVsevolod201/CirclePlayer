package com.example.circleplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

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
    progressTrack = Color(0xFFC9C6C1),
    progressActive = Color(0xFF57534F),
    listBackground = Color(0xFFECEAE6),
    listRowSelected = Color(0xFFD5D2CC),
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
    listRowSelected = Color(0xFF565656),
    divider = Color(0xFF565656)
)

val LocalPlayerPalette = staticCompositionLocalOf { LightPalette }

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFC9C6C1),
    secondary = Color(0xFFA9A5A0),
    background = DarkPalette.background,
    surface = DarkPalette.surface,
    onBackground = DarkPalette.text,
    onSurface = DarkPalette.text,
    error = Color(0xFFEF9A9A)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF57534F),
    secondary = Color(0xFF6E6B66),
    background = LightPalette.background,
    surface = LightPalette.surface,
    onBackground = LightPalette.text,
    onSurface = LightPalette.text,
    error = Color(0xFFB00020)
)

@Composable
fun CirclePlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkPalette else LightPalette

    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
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
