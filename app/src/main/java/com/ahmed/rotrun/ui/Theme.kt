package com.ahmed.rotrun.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.ahmed.rotrun.R

private fun nunito(weight: FontWeight, axis: Int) = Font(
    R.font.nunito,
    weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(axis)),
)

val Nunito = FontFamily(
    nunito(FontWeight.Normal, 400),
    nunito(FontWeight.Medium, 500),
    nunito(FontWeight.SemiBold, 600),
    nunito(FontWeight.Bold, 700),
    nunito(FontWeight.ExtraBold, 800),
    nunito(FontWeight.Black, 900),
)

private fun Typography.rounded(): Typography = copy(
    displayLarge = displayLarge.rounded(),
    displayMedium = displayMedium.rounded(),
    displaySmall = displaySmall.rounded(),
    headlineLarge = headlineLarge.rounded(),
    headlineMedium = headlineMedium.rounded(),
    headlineSmall = headlineSmall.rounded(),
    titleLarge = titleLarge.rounded(),
    titleMedium = titleMedium.rounded(),
    titleSmall = titleSmall.rounded(),
    bodyLarge = bodyLarge.rounded(),
    bodyMedium = bodyMedium.rounded(),
    bodySmall = bodySmall.rounded(),
    labelLarge = labelLarge.rounded(),
    labelMedium = labelMedium.rounded(),
    labelSmall = labelSmall.rounded(),
)

private fun TextStyle.rounded() = copy(fontFamily = Nunito)

data class RotChrome(
    val dark: Boolean,
    val page: Color,
    val card: Color,
    val inset: Color,
    val ink: Color,
    val muted: Color,
    val blue: Color,
    val line: Color,
    val tab: Color,
    val sheet: Color,
    val field: Color,
    val danger: Color,
    val green: Color,
    val pill: Color,
)

private val LightChrome = RotChrome(
    dark = false,
    page = Color(0xFFF2F2F7),
    card = Color.White,
    inset = Color(0xFFF2F2F7),
    ink = Color(0xFF1C1C1E),
    muted = Color(0xFF8E8E93),
    blue = Color(0xFF007AFF),
    line = Color(0xFFE5E5EA),
    tab = Color.White,
    sheet = Color(0xFFF2F2F7),
    field = Color.White,
    danger = Color(0xFFFF3B30),
    green = Color(0xFF34C759),
    pill = Color(0xFFE5E5EA),
)

private val DarkChrome = RotChrome(
    dark = true,
    page = Color(0xFF000000),
    card = Color(0xFF1C1C1E),
    inset = Color(0xFF2C2C2E),
    ink = Color.White,
    muted = Color(0xFF8E8E93),
    blue = Color(0xFF0A84FF),
    line = Color(0xFF38383A),
    tab = Color(0xFF1C1C1E),
    sheet = Color(0xFF1C1C1E),
    field = Color(0xFF2C2C2E),
    danger = Color(0xFFFF453A),
    green = Color(0xFF30D158),
    pill = Color(0xFF3A3A3C),
)

val LocalRot = staticCompositionLocalOf { LightChrome }

@Composable
fun rot(): RotChrome = LocalRot.current

@Composable
fun RotRunTheme(content: @Composable () -> Unit) {
    val chrome = if (isSystemInDarkTheme()) DarkChrome else LightChrome
    CompositionLocalProvider(LocalRot provides chrome) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = chrome.blue,
                background = chrome.page,
                surface = chrome.card,
            ),
            typography = Typography().rounded(),
            content = content,
        )
    }
}

object Melt {
    val Meadow = Color(0xFF3DB370)
    val MeadowDeep = Color(0xFF2E8C57)
    val Cucumber = Color(0xFF73C761)
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

enum class BrainStyle {
    Meadow, Sad, Pleading, Spa, Meditating, Reading, Flower, Robe, Mug, Watch, Sleeping, Newspaper,
}

data class ScenePalette(
    val sky: Color,
    val skyDeep: Color,
    val ground: Color,
    val groundDeep: Color,
    val star: Color,
    val stars: Boolean,
    val flowers: Boolean,
    val moon: Boolean,
)

fun sceneFor(theme: String): ScenePalette? = when (theme) {
    "Grassy" -> ScenePalette(Melt.Sky, Color(0xFF6BBDE6), Melt.Meadow, Melt.MeadowDeep, Color.Yellow, true, true, false)
    "Sunrise" -> ScenePalette(Color(0xFFFFC76B), Color(0xFFFF7A52), Color(0xFF579E5C), Color(0xFF387A47), Color(0xFFFFF2B8), true, true, false)
    "Zen" -> ScenePalette(Color(0xFFBDE0DB), Color(0xFF8CC7C2), Color(0xFF6BAD94), Color(0xFF478575), Color.White, false, true, false)
    "Twilight" -> ScenePalette(Color(0xFF8C61C7), Color(0xFF52388C), Color(0xFF47336B), Color(0xFF291F47), Color(0xFFFFEB8C), true, true, false)
    "Cosmic" -> ScenePalette(Color(0xFF1A1A47), Color(0xFF291A66), Color(0xFF1A1A42), Color(0xFF0F142E), Color.White, true, false, true)
    else -> null
}

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

    val rewards = listOf(
        StreakReward("Default", null, "Default", "Default", "The original RotRun look."),
        StreakReward("Grassy", null, "Grassy", "Grassy", "The meadow look. Always unlocked."),
        StreakReward("Spark", 3, "Sunrise", "Spark", "Sunrise Home look, Spark app icon, and a bolt streak badge."),
        StreakReward("Zen", 7, "Zen", "Zen", "Spa Home look, Zen app icon, and a meditating Home brain."),
        StreakReward("Scholar", 14, "Twilight", "Scholar", "Twilight Home look, Scholar app icon, and a reading Home brain."),
        StreakReward("Cosmic", 30, "Cosmic", "Cosmic", "Night-sky Home look, Cosmic app icon, and a robe Home brain."),
    )
}

data class StreakReward(
    val title: String,
    val days: Int?,
    val themeId: String,
    val iconId: String,
    val summary: String,
)
