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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.min

@Composable
fun CartoonBrain(style: BrainStyle, modifier: Modifier = Modifier, animated: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "brain")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (animated) 1.035f else 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
        label = "scale",
    )
    Canvas(modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
        val unit = min(size.width, size.height) / 128f
        val cx = size.width / 2f
        val cy = size.height / 2f
        fun px(value: Float) = cx + value * unit
        fun py(value: Float) = cy + value * unit
        fun s(value: Float) = value * unit

        drawOval(
            color = Color.Black.copy(alpha = 0.12f),
            topLeft = Offset(px(-30f), py(50f)),
            size = Size(s(60f), s(12f)),
        )
        if (style != BrainStyle.Sad && style != BrainStyle.Pleading && style != BrainStyle.Spa) {
            drawRoundRect(Melt.Outline, Offset(px(-18f), py(28f)), Size(s(8f), s(22f)), CornerRadius(s(4f)))
            drawRoundRect(Melt.Outline, Offset(px(10f), py(28f)), Size(s(8f), s(22f)), CornerRadius(s(4f)))
        }
        drawOval(Melt.BrainPink, Offset(px(-34f), py(-28f)), Size(s(68f), s(62f)))
        drawCircle(Melt.BrainPink, s(22f), Offset(px(-14f), py(-30f)))
        drawCircle(Melt.BrainPink, s(22f), Offset(px(14f), py(-30f)))
        drawOval(Melt.BrainShadow.copy(alpha = 0.35f), Offset(px(-28f), py(6f)), Size(s(56f), s(22f)))
        drawLine(Melt.Outline.copy(alpha = 0.35f), Offset(px(0f), py(-46f)), Offset(px(0f), py(8f)), strokeWidth = s(2f))
        drawOval(
            color = Melt.Outline,
            topLeft = Offset(px(-36f), py(-32f)),
            size = Size(s(72f), s(68f)),
            style = Stroke(width = s(3.2f)),
        )

        val closed = style == BrainStyle.Spa || style == BrainStyle.Meditating || style == BrainStyle.Robe
        val sad = style == BrainStyle.Sad || style == BrainStyle.Pleading || style == BrainStyle.Watch
        if (closed) {
            drawArcEye(px(-16f), py(-4f), s(10f))
            drawArcEye(px(16f), py(-4f), s(10f))
        } else {
            val eyeY = if (sad) py(0f) else py(-6f)
            drawCircle(Melt.Outline, s(4.2f), Offset(px(-16f), eyeY))
            drawCircle(Melt.Outline, s(4.2f), Offset(px(16f), eyeY))
            drawCircle(Color.White, s(1.4f), Offset(px(-15f), eyeY - s(1.2f)))
            drawCircle(Color.White, s(1.4f), Offset(px(17f), eyeY - s(1.2f)))
        }
        drawCircle(Melt.Blush.copy(alpha = 0.8f), s(5f), Offset(px(-24f), py(8f)))
        drawCircle(Melt.Blush.copy(alpha = 0.8f), s(5f), Offset(px(24f), py(8f)))
        if (sad) {
            drawLine(Melt.Outline, Offset(px(-8f), py(16f)), Offset(px(8f), py(16f)), strokeWidth = s(2.4f), cap = StrokeCap.Round)
        } else {
            drawArc(
                Melt.Outline,
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(px(-10f), py(8f)),
                size = Size(s(20f), s(14f)),
                style = Stroke(width = s(2.4f), cap = StrokeCap.Round),
            )
        }
        if (style == BrainStyle.Pleading) {
            drawOval(Color(0xFF73B8F2), Offset(px(-20f), py(6f)), Size(s(5f), s(8f)))
            drawOval(Color(0xFF73B8F2), Offset(px(16f), py(6f)), Size(s(5f), s(8f)))
        }
        if (style == BrainStyle.Spa || style == BrainStyle.Robe) {
            drawOval(Color(0xFF73C761), Offset(px(-24f), py(-10f)), Size(s(16f), s(7f)))
            drawOval(Color(0xFF73C761), Offset(px(8f), py(-10f)), Size(s(16f), s(7f)))
        }
        if (style == BrainStyle.Flower) {
            drawCircle(Color(0xFFFFD15C), s(7f), Offset(px(0f), py(-48f)))
            drawCircle(Melt.Orange, s(4f), Offset(px(0f), py(-48f)))
        }
        if (style == BrainStyle.Reading) {
            drawRoundRect(Color(0xFF6E5AA8), Offset(px(-16f), py(18f)), Size(s(32f), s(16f)), CornerRadius(s(3f)))
            drawLine(Color.White, Offset(px(0f), py(18f)), Offset(px(0f), py(34f)), strokeWidth = s(1.5f))
        }
        if (style == BrainStyle.Mug) {
            drawRoundRect(Color.White, Offset(px(22f), py(4f)), Size(s(16f), s(18f)), CornerRadius(s(3f)))
            drawArc(
                Melt.Outline,
                -90f,
                180f,
                false,
                Offset(px(34f), py(8f)),
                Size(s(8f), s(10f)),
                style = Stroke(width = s(1.6f)),
            )
        }
        if (style == BrainStyle.Watch) {
            drawCircle(Color.White, s(7f), Offset(px(28f), py(22f)))
            drawCircle(Melt.Outline, s(7f), Offset(px(28f), py(22f)), style = Stroke(width = s(1.4f)))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArcEye(x: Float, y: Float, width: Float) {
    drawArc(
        Melt.Outline,
        200f,
        140f,
        false,
        Offset(x - width / 2f, y - width / 4f),
        Size(width, width / 1.6f),
        style = Stroke(width = width / 6f, cap = StrokeCap.Round),
    )
}
