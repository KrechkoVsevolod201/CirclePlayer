package com.example.circleplayer.ui.theme

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONObject

data class ThemePreset(
    val id: String,
    val name: String,
    val light: PlayerPalette,
    val dark: PlayerPalette
)

data class ThemeColors(
    val light: PlayerPalette,
    val dark: PlayerPalette
)

data class PaletteColorField(
    val key: String,
    val titleRu: String,
    val titleEn: String,
    val get: (PlayerPalette) -> Color,
    val set: (PlayerPalette, Color) -> PlayerPalette
)

val paletteColorFields = listOf(
    PaletteColorField("background", "Фон приложения", "App background", { it.background }, { p, c -> p.copy(background = c) }),
    PaletteColorField("surface", "Поверхности и карточки", "Surfaces and cards", { it.surface }, { p, c -> p.copy(surface = c) }),
    PaletteColorField("discPanel", "Панель винила", "Vinyl panel", { it.discPanel }, { p, c -> p.copy(discPanel = c) }),
    PaletteColorField("discBody", "Основа винила", "Vinyl body", { it.discBody }, { p, c -> p.copy(discBody = c) }),
    PaletteColorField("discRim", "Канавки и обод винила", "Vinyl grooves and rim", { it.discRim }, { p, c -> p.copy(discRim = c) }),
    PaletteColorField("discHole", "Центр винила", "Vinyl center", { it.discHole }, { p, c -> p.copy(discHole = c) }),
    PaletteColorField("discHoleInner", "Внутренняя точка винила", "Inner vinyl label", { it.discHoleInner }, { p, c -> p.copy(discHoleInner = c) }),
    PaletteColorField("wheel", "Click Wheel", "Click Wheel", { it.wheel }, { p, c -> p.copy(wheel = c) }),
    PaletteColorField("wheelEdge", "Метки колеса", "Wheel markers", { it.wheelEdge }, { p, c -> p.copy(wheelEdge = c) }),
    PaletteColorField("wheelIcon", "Значки колеса и акцент", "Wheel icons and accent", { it.wheelIcon }, { p, c -> p.copy(wheelIcon = c) }),
    PaletteColorField("centerButton", "Центральная кнопка", "Center button", { it.centerButton }, { p, c -> p.copy(centerButton = c) }),
    PaletteColorField("centerIcon", "Значок центральной кнопки", "Center button icon", { it.centerIcon }, { p, c -> p.copy(centerIcon = c) }),
    PaletteColorField("sideButton", "Боковые кнопки", "Side buttons", { it.sideButton }, { p, c -> p.copy(sideButton = c) }),
    PaletteColorField("sideButtonIcon", "Значки боковых кнопок", "Side button icons", { it.sideButtonIcon }, { p, c -> p.copy(sideButtonIcon = c) }),
    PaletteColorField("chipBackground", "Фон строки трека", "Track information background", { it.chipBackground }, { p, c -> p.copy(chipBackground = c) }),
    PaletteColorField("chipText", "Текст строки трека", "Track information text", { it.chipText }, { p, c -> p.copy(chipText = c) }),
    PaletteColorField("text", "Основной текст", "Primary text", { it.text }, { p, c -> p.copy(text = c) }),
    PaletteColorField("textSecondary", "Вторичный текст", "Secondary text", { it.textSecondary }, { p, c -> p.copy(textSecondary = c) }),
    PaletteColorField("progressTrack", "Фон шкалы воспроизведения", "Progress track", { it.progressTrack }, { p, c -> p.copy(progressTrack = c) }),
    PaletteColorField("progressActive", "Активная шкала", "Active progress", { it.progressActive }, { p, c -> p.copy(progressActive = c) }),
    PaletteColorField("listBackground", "Фон списка", "List background", { it.listBackground }, { p, c -> p.copy(listBackground = c) }),
    PaletteColorField("listRowSelected", "Выбранная строка списка", "Selected list row", { it.listRowSelected }, { p, c -> p.copy(listRowSelected = c) }),
    PaletteColorField("divider", "Разделители", "Dividers", { it.divider }, { p, c -> p.copy(divider = c) })
)

val BuiltInThemePresets = listOf(
    ThemePreset("classic", "Классический", LightPalette, DarkPalette),
    ThemePreset(
        "hacker_green",
        "Зелёный хакерский",
        palette(
            background = "#E2FFE8", surface = "#F7FFFA", discPanel = "#E9FFF0", discBody = "#124A23",
            discRim = "#00A83A", discHole = "#A8FFC0", discHoleInner = "#08752F", wheel = "#A6FFBA",
            wheelEdge = "#37C45D", wheelIcon = "#008F32", centerButton = "#008F32", centerIcon = "#F4FFF6",
            sideButton = "#9CEFAE", sideButtonIcon = "#034D1B", chipBackground = "#06471D", chipText = "#E0FFE8",
            text = "#052B12", textSecondary = "#24743D", progressTrack = "#168338", progressActive = "#42D86A",
            listBackground = "#E2FFE8", listRowSelected = "#A8F3B9", divider = "#77D88E"
        ),
        palette(
            background = "#04190A", surface = "#0A2A13", discPanel = "#10371B", discBody = "#031108",
            discRim = "#39FF69", discHole = "#1C5A2B", discHoleInner = "#A2FFB4", wheel = "#286A37",
            wheelEdge = "#48C866", wheelIcon = "#00FF4C", centerButton = "#0AA83B", centerIcon = "#E4FFEA",
            sideButton = "#267A3A", sideButtonIcon = "#B0FFC0", chipBackground = "#082512", chipText = "#BDFFCA",
            text = "#E0FFE6", textSecondary = "#A4EFB2", progressTrack = "#28663A", progressActive = "#39FF69",
            listBackground = "#04190A", listRowSelected = "#255530", divider = "#388D4B"
        )
    ),
    ThemePreset(
        "cyberpunk_violet",
        "Неоновый фиолетовый киберпанк",
        palette(
            background = "#F4E8FF", surface = "#FFFFFF", discPanel = "#F8ECFF", discBody = "#653090",
            discRim = "#A500E8", discHole = "#E9C8FF", discHoleInner = "#7A00C7", wheel = "#E0B6FF",
            wheelEdge = "#B448F2", wheelIcon = "#9200D8", centerButton = "#9200D8", centerIcon = "#FFFFFF",
            sideButton = "#D9B0FF", sideButtonIcon = "#4A087A", chipBackground = "#4A087A", chipText = "#FFF0FF",
            text = "#26083D", textSecondary = "#71359B", progressTrack = "#7F18AF", progressActive = "#D24AFF",
            listBackground = "#F4E8FF", listRowSelected = "#E3C2FF", divider = "#C78AF0"
        ),
        palette(
            background = "#100320", surface = "#1D0638", discPanel = "#28094A", discBody = "#10021C",
            discRim = "#4CFBFF", discHole = "#48166B", discHoleInner = "#FFABFF", wheel = "#522078",
            wheelEdge = "#A945D8", wheelIcon = "#FF43F9", centerButton = "#C000F5", centerIcon = "#FFF4FF",
            sideButton = "#64218A", sideButtonIcon = "#FFD0FF", chipBackground = "#210432", chipText = "#FFC6FF",
            text = "#FFF0FF", textSecondary = "#D399FF", progressTrack = "#5B267A", progressActive = "#FF43F9",
            listBackground = "#100320", listRowSelected = "#42145E", divider = "#7936A0"
        )
    ),
    ThemePreset(
        "yellow_black",
        "Контрастный жёлто-чёрный",
        palette(
            background = "#FFF7A8", surface = "#FFFFF0", discPanel = "#FFF078", discBody = "#554800",
            discRim = "#8C7600", discHole = "#FFE52E", discHoleInner = "#3C3300", wheel = "#FFE52E",
            wheelEdge = "#D2B500", wheelIcon = "#302900", centerButton = "#302900", centerIcon = "#FFFF8A",
            sideButton = "#FFE52E", sideButtonIcon = "#211D00", chipBackground = "#302900", chipText = "#FFFF8A",
            text = "#211D00", textSecondary = "#625800", progressTrack = "#8A7600", progressActive = "#FFF000",
            listBackground = "#FFF7A8", listRowSelected = "#FFE846", divider = "#D8BE00"
        ),
        palette(
            background = "#171500", surface = "#2C2800", discPanel = "#403900", discBody = "#0C0A00",
            discRim = "#FFF000", discHole = "#756700", discHoleInner = "#FFFF80", wheel = "#655A00",
            wheelEdge = "#D7C000", wheelIcon = "#FFFF00", centerButton = "#716400", centerIcon = "#FFFFB8",
            sideButton = "#796A00", sideButtonIcon = "#FFFF80", chipBackground = "#201C00", chipText = "#FFFF80",
            text = "#FFFFD1", textSecondary = "#E3D56C", progressTrack = "#625600", progressActive = "#FFFF00",
            listBackground = "#171500", listRowSelected = "#4B4200", divider = "#857600"
        )
    )
)

private fun palette(
    background: String,
    surface: String,
    discPanel: String,
    discBody: String,
    discRim: String,
    discHole: String,
    discHoleInner: String,
    wheel: String,
    wheelEdge: String,
    wheelIcon: String,
    centerButton: String,
    centerIcon: String,
    sideButton: String,
    sideButtonIcon: String,
    chipBackground: String,
    chipText: String,
    text: String,
    textSecondary: String,
    progressTrack: String,
    progressActive: String,
    listBackground: String,
    listRowSelected: String,
    divider: String
) = PlayerPalette(
    background.toColor(), surface.toColor(), discPanel.toColor(), discBody.toColor(), discRim.toColor(),
    discHole.toColor(), discHoleInner.toColor(), wheel.toColor(), wheelEdge.toColor(), wheelIcon.toColor(),
    centerButton.toColor(), centerIcon.toColor(), sideButton.toColor(), sideButtonIcon.toColor(),
    chipBackground.toColor(), chipText.toColor(), text.toColor(), textSecondary.toColor(), progressTrack.toColor(),
    progressActive.toColor(), listBackground.toColor(), listRowSelected.toColor(), divider.toColor()
)

private fun String.toColor() = Color(AndroidColor.parseColor(this))

fun encodeThemePreset(preset: ThemePreset): String = JSONObject()
    .put("version", 1)
    .put("id", preset.id)
    .put("name", preset.name)
    .put("light", preset.light.toJson())
    .put("dark", preset.dark.toJson())
    .toString(2)

fun decodeThemePreset(json: String): ThemePreset? = runCatching {
    val obj = JSONObject(json)
    ThemePreset(
        id = obj.optString("id").ifBlank { "imported-${System.currentTimeMillis()}" },
        name = obj.getString("name"),
        light = obj.getJSONObject("light").toPalette(LightPalette),
        dark = obj.getJSONObject("dark").toPalette(DarkPalette)
    )
}.getOrNull()

fun encodeThemePresetList(presets: List<ThemePreset>): String = JSONArray().apply {
    presets.forEach { put(JSONObject(encodeThemePreset(it))) }
}.toString()

fun decodeThemePresetList(json: String?): List<ThemePreset> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { decodeThemePreset(array.getJSONObject(it).toString()) }
    }.getOrDefault(emptyList())
}

private fun PlayerPalette.toJson() = JSONObject().apply {
    paletteColorFields.forEach { field -> put(field.key, field.get(this@toJson).toArgb().toUInt().toString(16).padStart(8, '0').uppercase()) }
}

private fun JSONObject.toPalette(fallback: PlayerPalette): PlayerPalette =
    paletteColorFields.fold(fallback) { palette, field ->
        val color = optString(field.key).takeIf { it.isNotBlank() }?.let { value ->
            runCatching { Color(AndroidColor.parseColor(if (value.startsWith('#')) value else "#$value")) }
                .getOrNull()
        }
        if (color == null) palette else field.set(palette, color)
    }
