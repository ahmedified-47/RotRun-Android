package com.ahmed.rotrun.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.min

@Composable
fun CartoonBrain(style: BrainStyle, modifier: Modifier = Modifier, animated: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "brain")
    val breath by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (animated) 1.026f else 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Reverse),
        label = "breath",
    )
    Canvas(
        modifier.graphicsLayer {
            scaleY = breath
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
        },
    ) {
        val unit = min(size.width, size.height) / 128f
        translate(size.width / 2f, size.height / 2f - 2f * unit) {
            scale(unit, unit, pivot = Offset.Zero) {
                drawMascot(style)
            }
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

private fun DrawScope.drawMascot(style: BrainStyle) {
    if (style != BrainStyle.Sleeping) {
        drawOval(Color.Black.copy(alpha = 0.12f), topLeft = Offset(-30f, 54f), size = Size(60f, 11f))
    }
    when (style) {
        BrainStyle.Meditating -> lotusLimbs()
        BrainStyle.Watch -> watchPose()
        BrainStyle.Pleading -> pleadingPose()
        BrainStyle.Sleeping -> Unit
        BrainStyle.Sad -> standingLimbs(includeLegs = false)
        else -> standingLimbs(includeLegs = true)
    }
    val head = brainSilhouette()
    drawPath(head, Melt.BrainPink)
    clipPath(head) { crease() }
    drawPath(head, Melt.Outline, style = Stroke(3.4f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    when (style) {
        BrainStyle.Spa, BrainStyle.Robe -> {
            cucumber(Offset(-17f, -6f))
            cucumber(Offset(17f, -6f))
        }
        BrainStyle.Sleeping -> {
            closedEye(Offset(-16f, -4f))
            closedEye(Offset(16f, -4f))
            nightcap()
            blanket()
        }
        BrainStyle.Meditating -> {
            closedEye(Offset(-16f, -4f))
            closedEye(Offset(16f, -4f))
            smile()
            zenStones()
        }
        BrainStyle.Watch -> openFace(frown = true, impatient = true)
        BrainStyle.Pleading -> {
            openFace(frown = true)
            drawOval(Color(0xFF73B8F2).copy(alpha = 0.85f), topLeft = Offset(-20f, 6f), size = Size(5f, 7f))
            drawOval(Color(0xFF73B8F2).copy(alpha = 0.85f), topLeft = Offset(16f, 6f), size = Size(5f, 7f))
        }
        BrainStyle.Sad -> {
            openFace(frown = true)
            phone()
        }
        else -> openFace(frown = false)
    }
    when (style) {
        BrainStyle.Robe -> robe()
        BrainStyle.Flower -> flower()
        BrainStyle.Reading -> book()
        BrainStyle.Mug -> mug()
        BrainStyle.Newspaper -> newspaper()
        else -> Unit
    }
}

private fun DrawScope.standingLimbs(includeLegs: Boolean) {
    limb(Offset(-40f, 4f), Offset(-54f, 26f), Offset(-56f, 8f))
    limb(Offset(40f, 4f), Offset(54f, 26f), Offset(56f, 8f))
    if (includeLegs) {
        limb(Offset(-12f, 30f), Offset(-16f, 52f), Offset(-14f, 42f))
        limb(Offset(12f, 30f), Offset(16f, 52f), Offset(14f, 42f))
    }
}

private fun DrawScope.watchPose() {
    limb(Offset(-12f, 30f), Offset(-16f, 52f), Offset(-14f, 42f))
    limb(Offset(12f, 30f), Offset(16f, 52f), Offset(14f, 42f))
    limb(Offset(40f, 4f), Offset(8f, 24f), Offset(30f, 22f))
    limb(Offset(-40f, 6f), Offset(-52f, 26f), Offset(-54f, 10f))
    drawRoundRect(Melt.Outline.copy(alpha = 0.92f), topLeft = Offset(-4f, 18f), size = Size(18f, 14f), cornerRadius = CornerRadius(3f, 3f))
    drawOval(Color(0xFFEBEBEB), topLeft = Offset(0f, 21f), size = Size(10f, 8f))
}

private fun DrawScope.pleadingPose() {
    limb(Offset(-12f, 30f), Offset(-16f, 52f), Offset(-14f, 42f))
    limb(Offset(12f, 30f), Offset(16f, 52f), Offset(14f, 42f))
    limb(Offset(-42f, 6f), Offset(-10f, 14f), Offset(-28f, 20f))
    limb(Offset(42f, 6f), Offset(10f, 14f), Offset(28f, 20f))
    drawOval(Melt.BrainPink, topLeft = Offset(-16f, 8f), size = Size(12f, 10f))
    drawOval(Melt.Outline.copy(alpha = 0.8f), topLeft = Offset(-16f, 8f), size = Size(12f, 10f), style = Stroke(1.5f))
    drawOval(Melt.BrainPink, topLeft = Offset(4f, 8f), size = Size(12f, 10f))
    drawOval(Melt.Outline.copy(alpha = 0.8f), topLeft = Offset(4f, 8f), size = Size(12f, 10f), style = Stroke(1.5f))
}

private fun DrawScope.lotusLimbs() {
    limb(Offset(-40f, 6f), Offset(-26f, 38f), Offset(-56f, 26f))
    limb(Offset(40f, 6f), Offset(26f, 38f), Offset(56f, 26f))
    limb(Offset(-12f, 30f), Offset(8f, 52f), Offset(-26f, 56f))
    limb(Offset(12f, 30f), Offset(-8f, 52f), Offset(26f, 56f))
}

private fun DrawScope.limb(from: Offset, to: Offset, control: Offset) {
    val path = Path().apply {
        moveTo(from.x, from.y)
        quadraticBezierTo(control.x, control.y, to.x, to.y)
    }
    drawPath(path, Melt.Outline, style = Stroke(8.4f, cap = StrokeCap.Round))
    drawPath(path, Melt.BrainPink, style = Stroke(6f, cap = StrokeCap.Round))
}

private fun brainSilhouette(): Path = Path().apply {
    moveTo(0f, -34f)
    quadraticBezierTo(4f, -46f, 16f, -48f)
    quadraticBezierTo(26f, -58f, 36f, -44f)
    quadraticBezierTo(50f, -40f, 48f, -24f)
    quadraticBezierTo(56f, -14f, 50f, -2f)
    quadraticBezierTo(52f, 10f, 44f, 18f)
    quadraticBezierTo(40f, 26f, 24f, 28f)
    quadraticBezierTo(12f, 34f, 0f, 32f)
    quadraticBezierTo(-12f, 34f, -24f, 28f)
    quadraticBezierTo(-40f, 26f, -44f, 18f)
    quadraticBezierTo(-52f, 10f, -50f, -2f)
    quadraticBezierTo(-56f, -14f, -48f, -24f)
    quadraticBezierTo(-50f, -40f, -36f, -44f)
    quadraticBezierTo(-26f, -58f, -16f, -48f)
    quadraticBezierTo(-4f, -46f, 0f, -34f)
    close()
}

private fun DrawScope.crease() {
    val path = Path().apply {
        moveTo(0f, -30f)
        cubicTo(-6f, -10f, 6f, 4f, 0f, 18f)
    }
    drawPath(path, Melt.BrainShadow.copy(alpha = 0.55f), style = Stroke(2f, cap = StrokeCap.Round))
}

private fun DrawScope.openFace(frown: Boolean, impatient: Boolean = false) {
    drawOval(Color.Black, topLeft = Offset(-22f, -14f), size = Size(12f, 17f))
    drawOval(Color.Black, topLeft = Offset(10f, -14f), size = Size(12f, 17f))
    drawOval(Color.White.copy(alpha = 0.95f), topLeft = Offset(-16f, -11f), size = Size(4f, 4f))
    drawOval(Color.White.copy(alpha = 0.95f), topLeft = Offset(16f, -11f), size = Size(4f, 4f))
    drawOval(Melt.Blush.copy(alpha = 0.42f), topLeft = Offset(-30f, 6f), size = Size(12f, 7f))
    drawOval(Melt.Blush.copy(alpha = 0.42f), topLeft = Offset(18f, 6f), size = Size(12f, 7f))
    if (impatient) {
        strokeLine(Offset(-24f, -18f), Offset(-8f, -12f))
        strokeLine(Offset(8f, -12f), Offset(24f, -18f))
    }
    if (frown) {
        drawArc(Melt.Outline, 200f, 140f, false, topLeft = Offset(-7f, 13f), size = Size(14f, 14f), style = Stroke(2.1f, cap = StrokeCap.Round))
    } else {
        smile()
    }
}

private fun DrawScope.smile() {
    drawArc(Melt.Outline, 22f, 136f, false, topLeft = Offset(-7.5f, 2.5f), size = Size(15f, 15f), style = Stroke(2.1f, cap = StrokeCap.Round))
}

private fun DrawScope.closedEye(at: Offset) {
    drawArc(Melt.Outline, 200f, 140f, false, topLeft = Offset(at.x - 6f, at.y - 6f), size = Size(12f, 12f), style = Stroke(2.2f, cap = StrokeCap.Round))
}

private fun DrawScope.cucumber(at: Offset) {
    drawOval(Melt.Cucumber, topLeft = Offset(at.x - 14f, at.y - 8f), size = Size(28f, 16f))
    drawOval(Color.White.copy(alpha = 0.55f), topLeft = Offset(at.x - 14f, at.y - 8f), size = Size(28f, 16f), style = Stroke(1f))
    drawOval(Color.White.copy(alpha = 0.45f), topLeft = Offset(at.x - 6f, at.y - 3f), size = Size(4f, 4f))
    drawOval(Color.White.copy(alpha = 0.35f), topLeft = Offset(at.x + 2f, at.y - 2f), size = Size(3f, 3f))
}

private val CapPurple = Color(0xFF8C6BD9)

private fun DrawScope.nightcap() {
    val cap = Path().apply {
        moveTo(-28f, -28f)
        quadraticBezierTo(-8f, -52f, 8f, -58f)
        quadraticBezierTo(32f, -48f, 36f, -22f)
        quadraticBezierTo(4f, -18f, -28f, -28f)
        close()
    }
    drawPath(cap, CapPurple)
    drawOval(CapPurple, topLeft = Offset(2f, -64f), size = Size(14f, 14f))
    drawOval(Color.White.copy(alpha = 0.9f), topLeft = Offset(-8f, -46f), size = Size(5f, 5f))
    drawOval(Color.White.copy(alpha = 0.85f), topLeft = Offset(10f, -40f), size = Size(4f, 4f))
    drawOval(Color.White.copy(alpha = 0.95f), topLeft = Offset(-18f, -50f), size = Size(11f, 11f))
    drawOval(CapPurple, topLeft = Offset(-15f, -51f), size = Size(9f, 9f))
}

private fun DrawScope.blanket() {
    val blanket = Path().apply {
        moveTo(-46f, 18f)
        quadraticBezierTo(0f, 8f, 46f, 18f)
        lineTo(50f, 52f)
        quadraticBezierTo(0f, 62f, -50f, 52f)
        close()
    }
    drawPath(blanket, CapPurple)
    listOf(Offset(-22f, 30f), Offset(4f, 28f), Offset(24f, 36f), Offset(-8f, 42f)).forEach {
        drawOval(Color.White.copy(alpha = 0.85f), topLeft = it, size = Size(6f, 6f))
    }
}

private fun DrawScope.zenStones() {
    listOf(Rect(-16f, -72f, 16f, -58f), Rect(-12f, -84f, 12f, -72f), Rect(-8f, -94f, 8f, -84f)).forEach { stone ->
        drawOval(Color(0xFF737880), topLeft = stone.topLeft, size = stone.size)
    }
}

private fun DrawScope.robe() {
    val robe = Path().apply {
        moveTo(-38f, 8f)
        quadraticBezierTo(-18f, -2f, 0f, 4f)
        quadraticBezierTo(18f, -2f, 38f, 8f)
        lineTo(46f, 52f)
        quadraticBezierTo(0f, 62f, -46f, 52f)
        close()
    }
    drawPath(robe, Color.White)
    drawPath(robe, Color.Black.copy(alpha = 0.12f), style = Stroke(1f))
    strokeLine(Offset(-22f, 28f), Offset(22f, 28f), 3.4f, Color.Black.copy(alpha = 0.85f))
    drawRoundRect(Color.Black.copy(alpha = 0.85f), topLeft = Offset(-6f, 24f), size = Size(12f, 8f), cornerRadius = CornerRadius(2f, 2f))
}

private fun DrawScope.phone() {
    drawRoundRect(Color.Black, topLeft = Offset(-16f, 28f), size = Size(32f, 42f), cornerRadius = CornerRadius(6f, 6f))
    drawRoundRect(Color(0xFF2E3847), topLeft = Offset(-12f, 32f), size = Size(24f, 30f), cornerRadius = CornerRadius(3f, 3f))
}

private fun DrawScope.flower() {
    val stem = Path().apply {
        moveTo(46f, 28f)
        quadraticBezierTo(62f, 16f, 58f, -8f)
    }
    drawPath(stem, Color(0xFF389E52), style = Stroke(2.6f, cap = StrokeCap.Round))
    val petal = Color(0xFFFAD12E)
    listOf(Offset(52f, -18f), Offset(64f, -18f), Offset(52f, -6f), Offset(64f, -6f)).forEach {
        drawOval(petal, topLeft = it, size = Size(12f, 12f))
    }
    drawOval(Color(0xFFFA8C2E), topLeft = Offset(56f, -12f), size = Size(10f, 10f))
}

private fun DrawScope.book() {
    drawRoundRect(Color(0xFF6B52C7), topLeft = Offset(-28f, 26f), size = Size(56f, 28f), cornerRadius = CornerRadius(4f, 4f))
    val page = Path().apply {
        moveTo(0f, 28f)
        lineTo(-24f, 30f)
        lineTo(-24f, 50f)
        lineTo(0f, 46f)
        lineTo(24f, 50f)
        lineTo(24f, 30f)
        close()
    }
    drawPath(page, Color.White)
    drawPath(page, Color.Black.copy(alpha = 0.12f), style = Stroke(1f))
    strokeLine(Offset(0f, 28f), Offset(0f, 46f), 1.4f, Color.Black.copy(alpha = 0.25f))
}

private fun DrawScope.mug() {
    drawRoundRect(Color.White, topLeft = Offset(-16f, 28f), size = Size(28f, 22f), cornerRadius = CornerRadius(5f, 5f))
    drawRoundRect(Color.Black.copy(alpha = 0.2f), topLeft = Offset(-16f, 28f), size = Size(28f, 22f), cornerRadius = CornerRadius(5f, 5f), style = Stroke(1.2f))
    drawArc(Color.Black.copy(alpha = 0.55f), -70f, 140f, false, topLeft = Offset(6f, 30f), size = Size(16f, 16f), style = Stroke(3f, cap = StrokeCap.Round))
    drawOval(Color(0xFF8C5C38), topLeft = Offset(-12f, 30f), size = Size(20f, 6f))
}

private fun DrawScope.newspaper() {
    drawRoundRect(Color.White, topLeft = Offset(-26f, 24f), size = Size(52f, 32f), cornerRadius = CornerRadius(3f, 3f))
    drawRoundRect(Color.Black.copy(alpha = 0.18f), topLeft = Offset(-26f, 24f), size = Size(52f, 32f), cornerRadius = CornerRadius(3f, 3f), style = Stroke(1f))
    drawRoundRect(Color(0xFFC7D1E0), topLeft = Offset(-20f, 28f), size = Size(18f, 12f), cornerRadius = CornerRadius(1.5f, 1.5f))
    listOf(30f, 36f, 42f, 48f).forEach { y -> strokeLine(Offset(2f, y), Offset(20f, y), 1.4f, Color.Black.copy(alpha = 0.22f)) }
}

private fun DrawScope.strokeLine(from: Offset, to: Offset, width: Float = 2.2f, color: Color = Melt.Outline) {
    drawLine(color, from, to, strokeWidth = width, cap = StrokeCap.Round)
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
