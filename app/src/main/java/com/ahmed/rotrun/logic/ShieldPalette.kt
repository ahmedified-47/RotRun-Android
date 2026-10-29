package com.ahmed.rotrun.logic

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Shield colours. Background uses the accent hue, saturation at least 0.55, brightness 0.24. */
object ShieldPalette {
    fun accent(id: String): Int = when (id) {
        "stop-scrolling", "brain-first" -> 0xFFE6389E.toInt()
        "read-anything" -> 0xFF8C6147.toInt()
        "hydrate" -> 0xFF38B8D1.toInt()
        "cheap-dopamine", "lock-in" -> 0xFF617AB3.toInt()
        "how-many-hours" -> 0xFF8F5CC7.toInt()
        "future-self" -> 0xFF387A52.toInt()
        "touch-grass" -> 0xFF249E94.toInt()
        "brain-rest" -> 0xFFF2857A.toInt()
        "how-much-today", "melting" -> 0xFFF28538.toInt()
        "zoned-out" -> 0xFF8C8CE6.toInt()
        "not-again" -> 0xFFF26B6B.toInt()
        else -> 0xFFB84747.toInt()
    }

    fun background(id: String): Int {
        val color = accent(id)
        return hsvToArgb(hueOf(color), max(satOf(color), 0.55f), 0.24f)
    }

    private fun hueOf(argb: Int): Float {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val maxChannel = maxOf(r, g, b)
        val minChannel = minOf(r, g, b)
        val delta = maxChannel - minChannel
        if (delta == 0f) return 0f
        val hue = when (maxChannel) {
            r -> 60f * (((g - b) / delta) % 6f)
            g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        return if (hue < 0f) hue + 360f else hue
    }

    private fun satOf(argb: Int): Float {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val maxChannel = maxOf(r, g, b)
        val minChannel = minOf(r, g, b)
        return if (maxChannel == 0f) 0f else (maxChannel - minChannel) / maxChannel
    }

    private fun hsvToArgb(hue: Float, saturation: Float, value: Float): Int {
        val h = ((hue % 360f) + 360f) % 360f
        val s = saturation.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)
        val c = v * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = v - c
        val (r1, g1, b1) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val r = ((r1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
