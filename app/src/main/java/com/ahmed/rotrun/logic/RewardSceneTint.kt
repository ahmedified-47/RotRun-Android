package com.ahmed.rotrun.logic

/**
 * Screen colours under a reward scene. Surface hexes start from iOS
 * `RewardSceneTint` at 3cb279e9. Cosmic is pushed toward blue so it does not
 * sit on Twilight, and two card bottoms are lightened just enough for black
 * text to stay WCAG AA on both ends of the gradient.
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
        val sky: Long,
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
        sky = 0xFFF2F2F7,
        lightInk = false,
        lightPercent = false,
    )

    val grassy = Palette(
        name = "Grassy",
        system = false,
        canvas = 0xFF3DB370,
        cardTop = 0xFF4DA86B,
        cardBottom = 0xFF2C8454,
        cardLip = 0xFF124729,
        tabBar = 0xFF24754D,
        sky = 0xFF8CD1ED,
        lightInk = true,
        lightPercent = false,
    )

    val sunrise = Palette(
        name = "Sunrise",
        system = false,
        canvas = 0xFF579E5C,
        cardTop = 0xFFDB7538,
        cardBottom = 0xFFB45C28,
        cardLip = 0xFF5C290F,
        tabBar = 0xFF7A381A,
        sky = 0xFFFFC76B,
        lightInk = true,
        lightPercent = false,
    )

    val zen = Palette(
        name = "Zen",
        system = false,
        canvas = 0xFF5C9E8A,
        cardTop = 0xFF3D8075,
        cardBottom = 0xFF295C57,
        cardLip = 0xFF143833,
        tabBar = 0xFF1F524D,
        sky = 0xFFBDE0DB,
        lightInk = true,
        lightPercent = false,
    )

    val twilight = Palette(
        name = "Twilight",
        system = false,
        canvas = 0xFF47336B,
        cardTop = 0xFF6B4DA3,
        cardBottom = 0xFF3D2966,
        cardLip = 0xFF1F1438,
        tabBar = 0xFF291A47,
        sky = 0xFF8C61C7,
        lightInk = true,
        lightPercent = true,
    )

    /** Bluer than the 3cb279e9 night sky, which was only a few degrees from Twilight. */
    val cosmic = Palette(
        name = "Cosmic",
        system = false,
        canvas = 0xFF102848,
        cardTop = 0xFF1A4478,
        cardBottom = 0xFF0E2C52,
        cardLip = 0xFF08162C,
        tabBar = 0xFF0A1C36,
        sky = 0xFF1A4478,
        lightInk = true,
        lightPercent = true,
    )

    private val byName = listOf(standard, grassy, sunrise, zen, twilight, cosmic).associateBy { it.name }

    fun resolve(raw: String?): Palette = byName[raw] ?: grassy

    fun explicit(raw: String?): Boolean = raw != null && raw in byName

    const val WHITE = 0xFFFFFFFF
    const val BLACK = 0xFF000000L

    fun contrast(foreground: Long, background: Long): Double {
        val lighter = maxOf(luminance(foreground), luminance(background))
        val darker = minOf(luminance(foreground), luminance(background))
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** Prefer the scene's ink, and switch when that colour misses WCAG AA on this surface. */
    fun ink(background: Long, preferLight: Boolean): Long {
        val preferred = if (preferLight) WHITE else BLACK
        val other = if (preferLight) BLACK else WHITE
        val preferredContrast = contrast(preferred, background)
        if (preferredContrast >= 4.5) return preferred
        return if (contrast(other, background) >= preferredContrast) other else preferred
    }

    fun hueDegrees(packed: Long): Double {
        val r = channel(packed, 16) / 255.0
        val g = channel(packed, 8) / 255.0
        val b = channel(packed, 0) / 255.0
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        if (delta == 0.0) return 0.0
        val hue = when (max) {
            r -> ((g - b) / delta) % 6.0
            g -> ((b - r) / delta) + 2.0
            else -> ((r - g) / delta) + 4.0
        }
        val degrees = hue * 60.0
        return if (degrees < 0) degrees + 360.0 else degrees
    }

    fun hueDistance(first: Long, second: Long): Double {
        val delta = kotlin.math.abs(hueDegrees(first) - hueDegrees(second))
        return minOf(delta, 360.0 - delta)
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
