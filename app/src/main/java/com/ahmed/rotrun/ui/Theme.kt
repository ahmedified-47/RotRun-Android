package com.ahmed.rotrun.ui

import androidx.compose.ui.graphics.Color

object Melt {
    val Meadow = Color(0xFF3DB370)
    val MeadowDeep = Color(0xFF2E8C57)
    val Tab = Color(0xFF24754D)
    val Action = Color(0xFF217AFF)
    val Orange = Color(0xFFFF9E2E)
    val Sky = Color(0xFF8CD1ED)
    val BrainPink = Color(0xFFFAB999)
    val BrainShadow = Color(0xFFE67A99)
    val Outline = Color(0xFF472933)
    val Blush = Color(0xFFF58099)
    val House = Color(0xFFF25247)
    val Glow = Color(0xFFB8E6FF)
    val CardTop = Color(0xFF4DA86B)
    val CardBottom = Color(0xFF288052)
    val Lip = Color(0xFF124829)
    val Peach = Color(0xFFFDE6C7)
    val Grouped = Color(0xFFF2F2F7)
    val Ink = Color(0xFF1C1C1E)
    val Muted = Color(0xFF8E8E93)
    val AscendTop = Color(0xFF5775A3)
    val AscendBottom = Color(0xFF293D66)
}

enum class BrainStyle { Meadow, Sad, Pleading, Spa, Meditating, Reading, Flower, Robe, Mug, Watch }

data class HomeLook(
    val id: String,
    val title: String,
    val canvas: Color,
    val onCanvas: Color,
    val tab: Color,
    val light: Boolean,
    val brain: BrainStyle,
    val streak: Int?,
)

object Looks {
    val all = listOf(
        HomeLook("Default", "Default", Color(0xFFF2F2F7), Melt.Ink, Color(0xFFF2F2F7), true, BrainStyle.Meadow, null),
        HomeLook("Grassy", "Grassy", Melt.Meadow, Color.White, Melt.Tab, false, BrainStyle.Meadow, null),
        HomeLook("Sunrise", "Sunrise", Color(0xFF579E5C), Color.White, Color(0xFF9E5729), false, BrainStyle.Flower, 3),
        HomeLook("Zen", "Zen", Color(0xFF6BAD94), Color.White, Color(0xFF337570), false, BrainStyle.Meditating, 7),
        HomeLook("Twilight", "Twilight", Color(0xFF47336B), Color.White, Color(0xFF422966), false, BrainStyle.Reading, 14),
        HomeLook("Cosmic", "Cosmic", Color(0xFF1A1A42), Color.White, Color(0xFF141433), false, BrainStyle.Robe, 30),
    )

    fun byId(id: String): HomeLook = all.firstOrNull { it.id == id } ?: all[1]

    val icons = listOf(
        "Default" to null,
        "Grassy" to null,
        "Spark" to 3,
        "Zen" to 7,
        "Scholar" to 14,
        "Cosmic" to 30,
    )
}
