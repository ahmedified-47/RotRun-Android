package com.ahmed.rotrun.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Stand-in for SwiftUI `RoundedRectangle(style: .continuous)`.
 * Apple's continuous curve is not a public formula. Each corner is a superellipse with n = 5.
 */
class ContinuousCornerShape(
    private val topStart: Dp = 0.dp,
    private val topEnd: Dp = 0.dp,
    private val bottomEnd: Dp = 0.dp,
    private val bottomStart: Dp = 0.dp,
) : Shape {
    constructor(all: Dp) : this(all, all, all, all)

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val start = if (layoutDirection == LayoutDirection.Ltr) topStart else topEnd
        val end = if (layoutDirection == LayoutDirection.Ltr) topEnd else topStart
        val bottomEndSide = if (layoutDirection == LayoutDirection.Ltr) bottomEnd else bottomStart
        val bottomStartSide = if (layoutDirection == LayoutDirection.Ltr) bottomStart else bottomEnd
        return Outline.Generic(
            continuousRoundRect(
                size.width,
                size.height,
                with(density) { start.toPx() },
                with(density) { end.toPx() },
                with(density) { bottomEndSide.toPx() },
                with(density) { bottomStartSide.toPx() },
            ),
        )
    }
}

fun continuousRoundRect(
    width: Float,
    height: Float,
    topLeft: Float,
    topRight: Float,
    bottomRight: Float,
    bottomLeft: Float,
): Path {
    val limit = min(width, height) / 2f
    fun cap(radius: Float) = radius.coerceIn(0f, limit)
    val tl = cap(topLeft)
    val tr = cap(topRight)
    val br = cap(bottomRight)
    val bl = cap(bottomLeft)
    val path = Path()
    val steps = 12
    path.moveTo(tl, 0f)
    path.lineTo((width - tr).coerceAtLeast(tl), 0f)
    squircle(path, width - tr, tr, tr, steps, Quadrant.TopRight)
    path.lineTo(width, (height - br).coerceAtLeast(tr))
    squircle(path, width - br, height - br, br, steps, Quadrant.BottomRight)
    path.lineTo(bl.coerceAtMost(width - br), height)
    squircle(path, bl, height - bl, bl, steps, Quadrant.BottomLeft)
    path.lineTo(0f, tl.coerceAtMost(height - bl))
    squircle(path, tl, tl, tl, steps, Quadrant.TopLeft)
    path.close()
    return path
}

private enum class Quadrant { TopRight, BottomRight, BottomLeft, TopLeft }

private fun squircle(path: Path, cx: Float, cy: Float, radius: Float, steps: Int, quadrant: Quadrant) {
    if (radius <= 0f) return
    val n = 5.0
    for (step in 1..steps) {
        val t = (PI / 2.0) * step / steps
        val c = abs(cos(t)).pow(2.0 / n).toFloat()
        val s = abs(sin(t)).pow(2.0 / n).toFloat()
        val (x, y) = when (quadrant) {
            Quadrant.TopRight -> cx + radius * s to cy - radius * c
            Quadrant.BottomRight -> cx + radius * c to cy + radius * s
            Quadrant.BottomLeft -> cx - radius * s to cy + radius * c
            Quadrant.TopLeft -> cx - radius * c to cy - radius * s
        }
        path.lineTo(x, y)
    }
}
