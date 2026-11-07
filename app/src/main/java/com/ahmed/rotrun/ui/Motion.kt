package com.ahmed.rotrun.ui

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import com.ahmed.rotrun.logic.SwiftSpring
import kotlinx.coroutines.delay

fun <T> swiftSpring(durationSec: Float, bounce: Float = 0f): FiniteAnimationSpec<T> = spring(
    dampingRatio = SwiftSpring.dampingRatio(bounce),
    stiffness = SwiftSpring.stiffness(durationSec),
    visibilityThreshold = null,
)

@Composable
fun reduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val animator = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    val transition = Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
    return animator == 0f || transition == 0f
}

/** New screen from the trailing edge. Back comes from the leading edge. Reduce Motion fades in 0.01s. */
fun screenSlide(forward: Boolean, reduce: Boolean): ContentTransform {
    if (reduce) {
        val spec = tween<Float>(10, easing = EaseOut)
        return fadeIn(spec) togetherWith fadeOut(spec)
    }
    val move = tween<IntOffset>(280, easing = EaseInOut)
    val fade = tween<Float>(280, easing = EaseInOut)
    return if (forward) {
        (slideInHorizontally(move) { it } + fadeIn(fade)) togetherWith (slideOutHorizontally(move) { -it } + fadeOut(fade))
    } else {
        (slideInHorizontally(move) { -it } + fadeIn(fade)) togetherWith (slideOutHorizontally(move) { it } + fadeOut(fade))
    }
}

/** Opacity 0→1 and a 14pt rise, 0.48s ease-out, once. Reduce Motion snaps visible. */
@Composable
fun Appear(delaySec: Float, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduce = reduceMotion()
    var shown by remember { mutableStateOf(reduce) }
    LaunchedEffect(delaySec) {
        if (reduce) {
            shown = true
        } else {
            delay((delaySec * 1000f).toLong())
            shown = true
        }
    }
    val spec: AnimationSpec<Float> = if (reduce) tween(0) else tween(480, easing = EaseOut)
    val alpha by animateFloatAsState(if (shown) 1f else 0f, spec, label = "appear")
    val rise by animateFloatAsState(if (shown) 0f else 14f, spec, label = "rise")
    Box(modifier.graphicsLayer { this.alpha = alpha; translationY = rise * density }) { content() }
}

/** GamePressButtonStyle: scale 0.97, +3pt, spring 0.16 bounce 0.25. */
@Composable
fun Modifier.chunkyPress(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val spec = swiftSpring<Float>(0.16f, 0.25f)
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spec, label = "press")
    val drop by animateFloatAsState(if (pressed) 3f else 0f, spec, label = "drop")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
        translationY = drop * density
    }
}

/** Scale 1→peak, ease-in-out, autoreverse forever. Still under Reduce Motion. */
@Composable
fun rememberPulse(peak: Float, durationMs: Int, enabled: Boolean): Float {
    val reduce = reduceMotion()
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (enabled && !reduce) peak else 1f,
        animationSpec = infiniteRepeatable(tween(durationMs, easing = EaseInOut), RepeatMode.Reverse),
        label = "pulse-scale",
    )
    return if (enabled && !reduce) scale else 1f
}

/** Snappy 0.2s (extraBounce 0) for a changing number. */
@Composable
fun SnappyText(value: String, content: @Composable (String) -> Unit) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            ContentTransform(
                fadeIn(swiftSpring(0.2f, 0f)),
                fadeOut(swiftSpring(0.2f, 0f)),
            )
        },
        label = "snappy",
    ) { content(it) }
}

/** Focus overlays: opacity only, spring 0.35s bounce 0. */
fun focusFadeIn() = fadeIn(swiftSpring<Float>(0.35f, 0f))

fun focusFadeOut() = fadeOut(swiftSpring<Float>(0.35f, 0f))

@Composable
fun easeWidth(target: Float, durationMs: Int, reduce: Boolean): Float {
    val spec: AnimationSpec<Float> = if (reduce) tween(0) else tween(durationMs, easing = EaseInOut)
    val value by animateFloatAsState(target, spec, label = "width")
    return value
}

/** Hold-to-ascend display: follow the finger linearly, ease back to 0 on release. */
@Composable
fun rememberHoldProgress(target: Float, holding: Boolean, reduce: Boolean): Float {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target, holding, reduce) {
        if (holding) anim.snapTo(target) else anim.animateTo(target, tween(if (reduce) 10 else 200, easing = EaseOut))
    }
    return anim.value
}
