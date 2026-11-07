package com.ahmed.rotrun.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ahmed.rotrun.R
import kotlin.math.sin
import kotlinx.coroutines.delay

@Composable
fun CartoonBrain(style: BrainStyle, modifier: Modifier = Modifier, animated: Boolean = true) {
    val label = style.name
    if (label.isEmpty()) return
    val minPx = with(LocalDensity.current) { 96.dp.roundToPx() }
    var tallEnough by remember { mutableIntStateOf(0) }
    val breath = rememberPulse(1.03f, 2400, animated && tallEnough >= minPx)
    Image(
        painter = painterResource(R.drawable.rot_mascot),
        contentDescription = "Rot",
        contentScale = ContentScale.Fit,
        modifier = modifier.onSizeChanged { tallEnough = it.height }.graphicsLayer {
            scaleX = breath
            scaleY = breath
        },
    )
}

/** Rank canvas. Frame at most every 0.08s. Reduce Motion is not checked, matching iOS. */
@Composable
fun PixelBrain(rankTitle: String, modifier: Modifier = Modifier) {
    var frame by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            delay(80)
            frame += 1
        }
    }
    val speed = when (rankTitle) {
        "Sloth" -> 0.6f
        "Active" -> 2.0f
        "Cosmic Mind" -> 3.2f
        else -> 1.2f
    }
    val t = frame * 0.08f
    val glow = 0.5f + 0.5f * sin(t * speed)
    val jitter = if (rankTitle == "Rotter" || rankTitle !in setOf("Sloth", "Active", "Cosmic Mind")) 0.15f else 0f
    Canvas(modifier) {
        val cell = size.minDimension / 5f
        val on = Color(0xFF7C5CFF).copy(alpha = glow.coerceIn(0.15f, 1f))
        val cells = listOf(1 to 1, 2 to 1, 3 to 1, 1 to 2, 3 to 2, 2 to 3)
        cells.forEachIndexed { index, (x, y) ->
            val drop = if (jitter > 0f && index % 2 == 0) cell * jitter * ((sin(t * 3f + index) + 1f) / 2f) else 0f
            drawRect(on, topLeft = Offset(x * cell, y * cell + drop), size = Size(cell * 0.8f, cell * 0.8f))
        }
    }
}

@Composable
fun HeroBackdrop(theme: String, modifier: Modifier = Modifier) {
    val palette = sceneFor(theme) ?: return
    Canvas(modifier) {
        drawRect(brush = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(palette.sky, palette.skyDeep, palette.ground)))
        if (palette.moon) {
            val moon = Rect(size.width * 0.72f, size.height * 0.10f, size.width * 0.72f + 34f, size.height * 0.10f + 34f)
            drawOval(Color(0xFFF2EDD1), topLeft = moon.topLeft, size = moon.size)
            drawOval(palette.skyDeep.copy(alpha = 0.55f), topLeft = moon.topLeft + Offset(8f, -4f), size = moon.size)
        }
        if (palette.stars) {
            listOf(0.12f to 0.10f, 0.28f to 0.18f, 0.72f to 0.12f, 0.88f to 0.20f, 0.50f to 0.08f).forEach { (x, y) ->
                drawStar(Offset(x * size.width, y * size.height), 7f, palette.star.copy(alpha = 0.95f))
            }
        }
        val hill = Path().apply {
            moveTo(0f, size.height * 0.70f)
            quadraticBezierTo(size.width * 0.52f, size.height * 0.50f, size.width, size.height * 0.66f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(hill, palette.ground)
        drawBush(Offset(size.width * 0.16f, size.height * 0.70f), 1f, palette.groundDeep)
        drawBush(Offset(size.width * 0.84f, size.height * 0.68f), 1.15f, palette.groundDeep)
        if (palette.flowers) {
            drawFlower(Offset(size.width * 0.27f, size.height * 0.78f), Color.White)
            drawFlower(Offset(size.width * 0.70f, size.height * 0.80f), Color.Yellow)
            drawFlower(Offset(size.width * 0.78f, size.height * 0.86f), Color.White)
        }
    }
}

private fun DrawScope.drawBush(at: Offset, scale: Float, color: Color) {
    val w = 46f * scale
    drawOval(color, topLeft = Offset(at.x - w / 2f, at.y - 16f * scale), size = Size(w, 28f * scale))
    drawOval(color, topLeft = Offset(at.x - 10f * scale, at.y - 26f * scale), size = Size(22f * scale, 22f * scale))
}

private fun DrawScope.drawFlower(at: Offset, color: Color) {
    drawOval(color, topLeft = Offset(at.x - 4f, at.y - 4f), size = Size(8f, 8f))
    drawOval(Color.Yellow.copy(alpha = 0.9f), topLeft = Offset(at.x - 2f, at.y - 2f), size = Size(4f, 4f))
}

private fun DrawScope.drawStar(center: Offset, span: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - span / 2f)
        lineTo(center.x + 1.4f, center.y - 1.4f)
        lineTo(center.x + span / 2f, center.y)
        lineTo(center.x + 1.4f, center.y + 1.4f)
        lineTo(center.x, center.y + span / 2f)
        lineTo(center.x - 1.4f, center.y + 1.4f)
        lineTo(center.x - span / 2f, center.y)
        lineTo(center.x - 1.4f, center.y - 1.4f)
        close()
    }
    drawPath(path, color)
}
