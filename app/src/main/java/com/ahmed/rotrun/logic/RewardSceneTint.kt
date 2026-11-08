package com.ahmed.rotrun.logic

/**
 * Home colours under a reward scene. Hexes are iOS `RewardSceneTint` at
 * 691880d, converted from unit RGB with half-up rounding.
 * Unknown saved themes fall back to Grassy, same as iOS `resolve`.
 */
object RewardSceneTint {
    data class Palette(
        val name: String,
        val system: Boolean,
        val canvas: Long,
        val cardTop: Long,
        val cardBottom: Long,
        val cardLip: Long,
        val tabBar: Long,
        val lightInk: Boolean,
        val lightPercent: Boolean,
    )

    val offeredIds = listOf("Default", "Grassy", "Sunrise", "Zen", "Twilight", "Cosmic")

    val standard = Palette(
        name = "Default",
        system = true,
        canvas = 0xFFFFFFFF,
        cardTop = 0xFFFFFFFF,
        cardBottom = 0xFFFFFFFF,
        cardLip = 0xFFB3B3B3,
        tabBar = 0xFFFFFFFF,
        lightInk = false,
        lightPercent = false,
    )

    /** Meadow `#3DB370`. Cards and the tab are the darkened green. */
    val grassy = Palette(
        name = "Grassy",
        system = false,
        canvas = 0xFF3DB370,
        cardTop = 0xFF0E5A28,
        cardBottom = 0xFF0A3E1C,
        cardLip = 0xFF062814,
        tabBar = 0xFF0A3E1C,
        lightInk = true,
        lightPercent = true,
    )

    /** Sunrise sky `#FFC76B`. Page and cards stay gold; the tab is the deep ember. */
    val sunrise = Palette(
        name = "Sunrise",
        system = false,
        canvas = 0xFFFFC76B,
        cardTop = 0xFFFFD18A,
        cardBottom = 0xFFF0B15A,
        cardLip = 0xFFC4842E,
        tabBar = 0xFF7A2808,
        lightInk = false,
        lightPercent = false,
    )

    /** Spa water `#0E6E78`, not the mint hill. */
    val zen = Palette(
        name = "Zen",
        system = false,
        canvas = 0xFF0E6E78,
        cardTop = 0xFF0C5560,
        cardBottom = 0xFF083E46,
        cardLip = 0xFF062E34,
        tabBar = 0xFF08343C,
        lightInk = true,
        lightPercent = true,
    )

    /** Orchid `#F0B4F4`. The tab keeps the deep magenta. */
    val twilight = Palette(
        name = "Twilight",
        system = false,
        canvas = 0xFFF0B4F4,
        cardTop = 0xFFF6D0F8,
        cardBottom = 0xFFE7A0EE,
        cardLip = 0xFFC46CD0,
        tabBar = 0xFF6A1878,
        lightInk = false,
        lightPercent = false,
    )

    /** Night sky shifted blue (`#070B24`). */
    val cosmic = Palette(
        name = "Cosmic",
        system = false,
        canvas = 0xFF070B24,
        cardTop = 0xFF1A2A72,
        cardBottom = 0xFF101848,
        cardLip = 0xFF0A102E,
        tabBar = 0xFF050814,
        lightInk = true,
        lightPercent = true,
    )

    private val byName = listOf(standard, grassy, sunrise, zen, twilight, cosmic).associateBy { it.name }

    fun resolve(raw: String?): Palette = byName[raw] ?: grassy

    fun explicit(raw: String?): Boolean = raw != null && raw in byName

    const val WHITE = 0xFFFFFFFF
    /** iOS `RewardSceneTint.darkInk` (0.06, 0.07, 0.09). Fixed so Dark Mode cannot flip it. */
    const val DARK_INK = 0xFF0F1217

    /** White when [light] is set, otherwise the fixed dark ink. No contrast swap. */
    fun label(light: Boolean): Long = if (light) WHITE else DARK_INK

    fun contrast(foreground: Long, background: Long): Double {
        val lighter = maxOf(luminance(foreground), luminance(background))
        val darker = minOf(luminance(foreground), luminance(background))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun luminance(packed: Long): Double {
        val r = linear(channel(packed, 16) / 255.0)
        val g = linear(channel(packed, 8) / 255.0)
        val b = linear(channel(packed, 0) / 255.0)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun linear(channel: Double): Double =
        if (channel <= 0.04045) channel / 12.92 else Math.pow((channel + 0.055) / 1.055, 2.4)

    private fun channel(packed: Long, shift: Int): Int = ((packed shr shift) and 0xFF).toInt()
}
