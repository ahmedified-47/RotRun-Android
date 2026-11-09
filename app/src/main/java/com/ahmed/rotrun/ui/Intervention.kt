package com.ahmed.rotrun.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmed.rotrun.data.RotRunRepository
import com.ahmed.rotrun.logic.BrainCallPolicy
import com.ahmed.rotrun.logic.InterventionCopy
import com.ahmed.rotrun.logic.ShieldPalette
import com.ahmed.rotrun.watch.AskBrainLaunch
import kotlin.math.sin
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InterventionFlow(
    appName: String,
    startMode: String,
    repo: RotRunRepository,
    onDismiss: () -> Unit,
    onUnlock: (Int) -> Unit,
    packageName: String = "",
) {
    val state by repo.state.collectAsState()
    var mode by remember { mutableStateOf(startMode) }
    val name = InterventionCopy.displayName(appName)
    val friend = state.cameoName.takeIf { state.cameoToday() }
    val shieldCopy = InterventionCopy.forPackage(packageName, name)
    val reduce = reduceMotion()
    var pendingLeave by remember { mutableStateOf(false) }
    var ransom by remember { mutableStateOf(false) }
    var coverMode by remember { mutableStateOf(startMode) }
    if (mode != "shield") coverMode = mode
    val shieldUnderneath = startMode == "shield" || mode == "shield"
    val retreat = {
        if (startMode == "shield") mode = "shield" else pendingLeave = true
    }
    LaunchedEffect(pendingLeave) {
        if (!pendingLeave) return@LaunchedEffect
        delay(if (reduce) 10 else 280)
        onDismiss()
    }
    val shield = shieldColors(shieldCopy.id)
    Box(Modifier.fillMaxSize()) {
        if (shieldUnderneath && !pendingLeave) {
            val context = LocalContext.current
            Column(
                Modifier.fillMaxSize().background(shield.second).padding(horizontal = 28.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Shield(
                    name,
                    packageName,
                    shield.first,
                    onAsk = {
                        AskBrainLaunch.open(context, packageName, name)
                        (context as? android.app.Activity)?.finish()
                    },
                    onBailOut = { ransom = true },
                )
            }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = mode != "shield" && !pendingLeave,
            modifier = Modifier.fillMaxSize(),
            enter = coverEnter(reduce),
            exit = coverExit(reduce),
        ) {
            val shown = coverMode
            if (BrainCallPolicy.isCall(shown)) {
                BrainCall(
                    friend = friend,
                    onDecline = {
                        if (BrainCallPolicy.afterDecline() == "dismiss") pendingLeave = true
                    },
                    onUnlock = onUnlock,
                )
            } else if (shown == "standard") {
                Standard(name, friend, state.cameoKind, onDismiss = retreat, onUnlock = onUnlock)
            } else if (shown == "breathing") {
                Breathing(friend, state.cameoKind, retreat, onUnlock)
            } else {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF8FDBFA), Color(0xFF66C785), Color(0xFF338C57))))) {
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        when (shown) {
                            "quick" -> Quick(name, friend, retreat, onUnlock)
                            "cameo" -> Cameo(name, friend ?: "a friend", state.cameoKind, retreat, onUnlock)
                            else -> Quick(name, friend, retreat, onUnlock)
                        }
                    }
                }
            }
        }
        if (ransom) {
            RansomSheet(
                reduce = reduce,
                onCancel = { ransom = false },
                onPaid = {
                    ransom = false
                    repo.payRansom()
                    onUnlock(10)
                },
            )
        }
    }
}

@Composable
private fun ColumnScope.Shield(appName: String, packageName: String, accent: Color, onAsk: () -> Unit, onBailOut: () -> Unit) {
    var opening by remember { mutableStateOf(false) }
    val copy = InterventionCopy.forPackage(packageName, appName)
    Spacer(Modifier.weight(1f))
    CartoonBrain(BrainStyle.Meadow, Modifier.size(196.dp))
    Text(copy.headline, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 28.dp))
    Text(copy.body, color = Color(0xFF9E9EA3), fontSize = 17.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
    if (copy.footer != null) Text(copy.footer, color = Color(0xFF9E9EA3), fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
    Spacer(Modifier.weight(1f))
    Text(
        if (opening) "Opening…" else "Ask my brain",
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().clip(CircleShape).background(accent).clickable(enabled = !opening) {
            if (opening) return@clickable
            opening = true
            onAsk()
        }.padding(vertical = 18.dp),
    )
    Text(
        "Hold to bail out",
        color = Color.White.copy(alpha = 0.85f),
        fontSize = 15.sp,
        modifier = Modifier.padding(top = 14.dp).clickable(enabled = !opening, onClick = onBailOut),
    )
}

private fun shieldColors(id: String): Pair<Color, Color> =
    Color(ShieldPalette.accent(id)) to Color(ShieldPalette.background(id))

@Composable
private fun Standard(appName: String, friend: String?, kind: String, onDismiss: () -> Unit, onUnlock: (Int) -> Unit) {
    var beat by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(10) }
    val stamp = remember { SimpleDateFormat("h:mm a", Locale.US).format(Date()).lowercase() }
    val blue = Color(0xFF007AFF)
    val question = if (friend != null) "$friend left a note." else "you really gotta open this?"
    val scroll = rememberScrollState()
    LaunchedEffect(beat) {
        when (beat) {
            0 -> { delay(380); beat = 1 }
            1 -> { delay(520); beat = 2 }
            2 -> if (friend != null) { delay(420); beat = 3 } else { delay(280); beat = 4 }
            3 -> { delay(280); beat = 4 }
            5 -> { delay(400); beat = 6 }
            6 -> { delay(850); onDismiss() }
            7 -> { delay(450); beat = 8 }
            8 -> { delay(480); beat = 9 }
            9 -> { delay(250); beat = 10 }
            11 -> { delay(420); beat = 12 }
            12 -> { delay(280); beat = 13 }
        }
        if (beat > 1) scroll.animateScrollTo(scroll.maxValue, androidx.compose.animation.core.tween(250, easing = androidx.compose.animation.core.EaseOut))
    }
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹ $appName", color = blue, fontSize = 17.sp, modifier = Modifier.clickable(onClick = onDismiss))
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(78.dp).clip(CircleShape).background(Color(0xFFB8E0FA)), contentAlignment = Alignment.Center) {
                CartoonBrain(BrainStyle.Sleeping, Modifier.size(72.dp).offset(y = 4.dp), animated = false)
            }
            Text(
                "Your Brain ›",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 8.dp).clip(CircleShape).border(1.dp, Color(0xFFC6C6C8), CircleShape).background(Color(0xFFF2F2F7)).padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Today $stamp", color = Color(0xFFC7C7CC), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp))
            if (beat >= 1) ChatBubble("yo it's me. brain", true, blue)
            if (beat >= 2) ChatBubble(question, true, blue)
            if (beat >= 3 && friend != null) ChatBubble(InterventionCopy.roast(kind, friend), true, blue)
            if (beat in 5..6) ChatBubble("nah, you're right", false, blue)
            if (beat >= 6 && beat < 7) ChatBubble("bet. go live.", true, blue)
            if (beat in 7..13) ChatBubble("yes i really do", false, blue)
            if (beat in 8..13) ChatBubble("alright more rot it is 🥱", true, blue)
            if (beat in 9..13) ChatBubble("so how long?", true, blue)
            if (beat >= 11) ChatBubble("$minutes mins", false, blue)
            if (beat >= 12) ChatBubble("ok lol go for it", true, blue)
            if (beat == 4) {
                SpringBeat {
                    Column(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalAlignment = Alignment.End) {
                        Text("Tap to reply ↓", color = Color(0xFFC7C7CC), fontSize = 13.sp)
                        ReplyPill("nah, you're right", blue) { beat = 5 }
                        ReplyPill("yes i really do", blue) { beat = 7 }
                    }
                }
            }
            if (beat == 10) {
                SpringBeat {
                    Column(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalAlignment = Alignment.End) {
                        Text("Tap to reply ↓", color = Color(0xFFC7C7CC), fontSize = 13.sp)
                        listOf(10, 20, 30).forEach { value -> ReplyPill("$value mins", blue) { minutes = value; beat = 11 } }
                        ReplyPill("actually nevermind", blue) { beat = 5 }
                    }
                }
            }
        }
        if (beat >= 13) {
            SpringBeat {
                Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 22.dp)) {
                    Text(
                        "Actually I don't need this",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clip(CircleShape).background(blue).clickable(onClick = onDismiss).padding(vertical = 16.dp),
                    )
                    Text(
                        "Open the app and rot",
                        color = Color(0xFFFF3B30),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp).clickable { onUnlock(minutes) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SpringBeat(content: @Composable () -> Unit) {
    val scale = remember { androidx.compose.animation.core.Animatable(0.86f) }
    val alpha = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, swiftSpring(0.38f, 0f))
    }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, swiftSpring(0.38f, 0f))
    }
    Box(Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value }) { content() }
}

@Composable
private fun ChatBubble(text: String, brain: Boolean, blue: Color) {
    val scale = remember { androidx.compose.animation.core.Animatable(0.86f) }
    val alpha = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(text) {
        val bounce = if (brain) 0.18f else 0.12f
        val duration = if (brain) 0.36f else 0.32f
        scale.animateTo(1f, swiftSpring(duration, bounce))
    }
    LaunchedEffect(text) {
        val bounce = if (brain) 0.18f else 0.12f
        val duration = if (brain) 0.36f else 0.32f
        alpha.animateTo(1f, swiftSpring(duration, bounce))
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp).graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        this.alpha = alpha.value
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (brain) 0f else 1f, 1f)
    }) {
        if (!brain) Spacer(Modifier.weight(1f))
        Text(
            text,
            color = if (brain) Color.White else Melt.Ink,
            fontSize = 17.sp,
            modifier = Modifier.weight(1f, fill = false).clip(RoundedCornerShape(18.dp)).background(if (brain) blue else Color(0xFFE5E5EA)).padding(horizontal = 14.dp, vertical = 9.dp),
        )
        if (brain) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ReplyPill(label: String, blue: Color, onClick: () -> Unit) {
    Text(
        label,
        color = blue,
        fontSize = 16.sp,
        modifier = Modifier.padding(top = 10.dp).clip(CircleShape).border(1.5.dp, blue, CircleShape).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
private fun Quick(appName: String, friend: String?, onDismiss: () -> Unit, onUnlock: (Int) -> Unit) {
    var step by remember { mutableIntStateOf(1) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val fromBelow = with(density) { 12.dp.roundToPx() }
    val leave = with(density) { 8.dp.roundToPx() }
    Appear(0f) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("QUICK CHECK", color = Color(0xFF1A5230), fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.1.sp, modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.92f)).padding(horizontal = 12.dp, vertical = 6.dp))
            Text(appName, color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
        }
        listOf(1, 2).forEach { pip ->
            val filled = pip <= step
            Box(
                Modifier.padding(start = 6.dp).size(32.dp).clip(CircleShape).background(if (filled) Color.White else Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("$pip", color = if (filled) Color(0xFF1A5230) else Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
        }
    } }
    Spacer(Modifier.height(20.dp))
    androidx.compose.animation.SharedTransitionLayout {
        val shared = this
        androidx.compose.animation.AnimatedContent(
            step,
            transitionSpec = {
                androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(550, easing = androidx.compose.animation.core.EaseInOut)) togetherWith
                    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(550, easing = androidx.compose.animation.core.EaseInOut))
            },
            label = "quick-body",
        ) { current ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val bubbleMod = with(shared) {
                    Modifier.sharedElement(
                        rememberSharedContentState("quick-bubble"),
                        animatedVisibilityScope = this@AnimatedContent,
                        boundsTransform = { _, _ -> swiftSpring(0.45f, 0.12f) },
                    )
                }
                val mascotMod = with(shared) {
                    Modifier.sharedElement(
                        rememberSharedContentState("quick-mascot"),
                        animatedVisibilityScope = this@AnimatedContent,
                        boundsTransform = { _, _ -> swiftSpring(0.45f, 0.12f) },
                    )
                }
                val bubble = @Composable {
                    Bubble(if (current == 1) {
                        if (friend != null) "Would $friend believe this is important?" else "Hey. Do you really need this right now?"
                    } else {
                        "Okay, you can scroll some more, just remember it hurts me."
                    })
                }
                val mascot = @Composable {
                    CartoonBrain(if (current == 1) BrainStyle.Watch else BrainStyle.Pleading, mascotMod.size(180.dp))
                }
                if (current == 1) {
                    Appear(0.06f) { Box(bubbleMod) { bubble() } }
                    Appear(0.10f) { mascot() }
                } else {
                    Box(bubbleMod) { bubble() }
                    mascot()
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    androidx.compose.animation.AnimatedContent(
        step,
        transitionSpec = {
            val up = targetState > initialState
            val fade = swiftSpring<Float>(0.45f, 0.12f)
            val move = swiftSpring<androidx.compose.ui.unit.IntOffset>(0.45f, 0.12f)
            (androidx.compose.animation.slideInVertically(move) { fromBelow } + androidx.compose.animation.fadeIn(fade)) togetherWith
                (androidx.compose.animation.slideOutVertically(move) { if (up) -leave else leave } + androidx.compose.animation.fadeOut(fade))
        },
        label = "quick-controls",
    ) { current ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (current == 1) {
                Pill("Nah, you're right") { onDismiss() }
                Text("Yes, I really do", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.padding(vertical = 8.dp).clickable { step = 2 })
            } else {
                DurationTiles(onUnlock)
                Text("Nah, never mind", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.padding(vertical = 8.dp).clickable(onClick = onDismiss))
            }
        }
    }
}

@Composable
private fun Breathing(friend: String?, kind: String, onDismiss: () -> Unit, onUnlock: (Int) -> Unit) {
    val context = LocalContext.current
    var phase by remember { mutableIntStateOf(0) }
    var left by remember { mutableIntStateOf(4) }
    val labels = listOf("Breathe in.", "Hold.", "Breathe out.")
    val lengths = listOf(4, 2, 4)
    val breath = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(phase) {
        if (phase > 2) return@LaunchedEffect
        val vibrator = vibrator(context)
        when (phase) {
            0 -> pulseHaptic(vibrator, (0.72f * 255).toInt(), 4000)
            1 -> runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(40, 255)) }
            2 -> pulseHaptic(vibrator, (0.55f * 255).toInt(), 4000)
        }
    }
    LaunchedEffect(phase) {
        if (phase > 2) return@LaunchedEffect
        when (phase) {
            0 -> breath.animateTo(1.18f, androidx.compose.animation.core.tween(4000, easing = androidx.compose.animation.core.EaseInOut))
            2 -> breath.animateTo(1f, androidx.compose.animation.core.tween(4000, easing = androidx.compose.animation.core.EaseInOut))
        }
    }
    LaunchedEffect(phase) {
        if (phase > 2) return@LaunchedEffect
        var remaining = lengths[phase]
        while (remaining > 0) {
            left = remaining
            delay(1000)
            remaining -= 1
        }
        phase += 1
    }
    var ask by remember { mutableStateOf(true) }
    val oneLiner = friend?.let { InterventionCopy.roast(kind, it) }?.takeIf { it.isNotBlank() }
    androidx.compose.animation.AnimatedContent(
        targetState = phase <= 2,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(450, easing = androidx.compose.animation.core.EaseInOut)) togetherWith
                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(450, easing = androidx.compose.animation.core.EaseInOut))
        },
        label = "breath-swap",
    ) { breathing ->
        if (breathing) {
            BreathingCycle(left = left, scale = breath.value, title = labels[phase.coerceAtMost(2)], oneLiner = oneLiner)
        } else {
            BreathingGarden(asking = ask, onNah = onDismiss, onYes = { ask = false }, onUnlock = onUnlock, onNevermind = onDismiss)
        }
    }
}

private val BreathSerene = Color(0xFF8CC7EB)
private val BreathNavy = Color(0xFF1F2E47)
private val BreathSeconds = Color(0xFF386B9E)
private val BreathSky = Color(0xFF9EDBFA)
private val BreathGrass = Color(0xFF6BC761)
private val BreathGrassDeep = Color(0xFF479E47)
private val BreathBlue = Color(0xFF387AF5)

@Composable
private fun BreathingCycle(left: Int, scale: Float, title: String, oneLiner: String?) {
    var waveTime by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(16)
            waveTime += 0.016f
        }
    }
    val shift = sin(waveTime * (kotlin.math.PI.toFloat() / 6f)) * 18f
    Box(Modifier.fillMaxSize().background(BreathSerene)) {
        Column {
            Box(Modifier.fillMaxWidth().height(120.dp).background(Color.White))
            Canvas(Modifier.fillMaxWidth().height(72.dp).offset(y = (-8).dp)) {
                val t = waveTime * 28f
                breathWave(Color(0xFFD1EDFA), 14.dp.toPx(), t + shift, 0f)
                breathWave(Color(0xFFB3E0F5), 18.dp.toPx(), t - shift * 0.7f, 10.dp.toPx())
                breathWave(BreathSerene, 12.dp.toPx(), t + shift * 0.4f, 22.dp.toPx())
            }
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(160.dp))
            CartoonBrain(
                BrainStyle.Meditating,
                Modifier.size(200.dp).graphicsLayer { scaleX = scale; scaleY = scale },
                animated = false,
            )
            androidx.compose.animation.AnimatedContent(
                title,
                transitionSpec = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(350)) togetherWith androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(350)) },
                label = "phase",
            ) {
                Text(it, color = BreathNavy, fontFamily = FontFamily.Default, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
            }
            Text(
                "$left second${if (left == 1) "" else "s"}",
                color = BreathSeconds,
                fontFamily = FontFamily.Default,
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 18.dp),
            )
            if (oneLiner != null) {
                Text(
                    oneLiner,
                    color = BreathNavy.copy(alpha = 0.9f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 18.dp),
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.breathWave(color: Color, amplitude: Float, phase: Float, yOffset: Float) {
    val path = Path()
    path.moveTo(0f, amplitude + 8f + yOffset)
    var x = 0f
    while (x <= size.width) {
        path.lineTo(x, amplitude + sin((x + phase) / 42f) * amplitude + yOffset)
        x += 8f
    }
    path.lineTo(size.width, size.height)
    path.lineTo(0f, size.height)
    path.close()
    drawPath(path, color)
}

@Composable
private fun BreathingGarden(
    asking: Boolean,
    onNah: () -> Unit,
    onYes: () -> Unit,
    onUnlock: (Int) -> Unit,
    onNevermind: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) { zenGarden() }
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(48.dp))
            Text(
                if (asking) "Be honest. Do you actually want this right now?" else "How long do you need?",
                color = Color(0xFF262626),
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 28.dp).padding(bottom = 8.dp).shadow(8.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(Color.White).padding(horizontal = 22.dp, vertical = 16.dp),
            )
            CartoonBrain(BrainStyle.Meditating, Modifier.padding(top = 8.dp).size(168.dp))
            Spacer(Modifier.weight(1f))
            Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (asking) {
                    GardenCapsule("Nah, you're right.", BreathBlue, Color.White, onNah)
                    Spacer(Modifier.height(16.dp))
                    GardenCapsule("Yes I really do", Color.White, BreathNavy, onYes)
                } else {
                    listOf(10, 20, 30).forEach { minutes ->
                        Text(
                            "$minutes min",
                            color = Color.White,
                            fontFamily = FontFamily.Default,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 12.dp).fillMaxWidth().clip(CircleShape).background(BreathBlue).clickable { onUnlock(minutes) }.padding(vertical = 14.dp),
                        )
                    }
                    Text(
                        "Nah actually nevermind",
                        color = Color.White,
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 4.dp).clickable(onClick = onNevermind),
                    )
                }
            }
        }
    }
}

@Composable
private fun GardenCapsule(label: String, fill: Color, ink: Color, onClick: () -> Unit) {
    Text(
        label,
        color = ink,
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().clip(CircleShape).background(fill).clickable(onClick = onClick).padding(vertical = 16.dp),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.zenGarden() {
    drawRect(BreathSky)
    val grassTop = size.height * 0.42f
    drawRect(BreathGrass, topLeft = Offset(0f, grassTop), size = Size(size.width, size.height - grassTop))
    gardenCloud(size.width * 0.18f, 72.dp.toPx(), 90.dp.toPx(), 36.dp.toPx(), 0.95f)
    gardenCloud(size.width * 0.78f, 96.dp.toPx(), 70.dp.toPx(), 28.dp.toPx(), 0.90f)
    gardenCloud(size.width * 0.52f, 58.dp.toPx(), 54.dp.toPx(), 22.dp.toPx(), 0.85f)
    val ring = BreathGrassDeep.copy(alpha = 0.35f)
    val ringCenter = Offset(size.width * 0.5f, size.height * 0.58f)
    listOf(70, 110, 150, 190).forEach { radius ->
        drawCircle(ring, radius = radius.dp.toPx(), center = ringCenter, style = Stroke(width = 3.dp.toPx()))
    }
    val treeX = size.width * 0.18f
    val treeY = size.height * 0.42f
    drawRoundRect(
        Color(0xFF38241A),
        topLeft = Offset(treeX - 9.dp.toPx(), treeY),
        size = Size(18.dp.toPx(), 90.dp.toPx()),
        cornerRadius = CornerRadius(9.dp.toPx()),
    )
    gardenFoliage(treeX, treeY - 78.dp.toPx(), 88.dp.toPx())
    gardenFoliage(treeX, treeY - 54.dp.toPx(), 108.dp.toPx())
    gardenFoliage(treeX, treeY - 28.dp.toPx(), 92.dp.toPx())
    gardenRock(size.width * 0.82f, size.height * 0.55f, 86.dp.toPx(), 54.dp.toPx())
    gardenRock(size.width * 0.72f, size.height * 0.62f, 52.dp.toPx(), 36.dp.toPx())
    gardenRock(size.width * 0.50f, size.height * 0.72f, 120.dp.toPx(), 28.dp.toPx())
    drawOval(Color(0xFF38943D), Offset(size.width * 0.38f - 18.dp.toPx(), size.height * 0.66f - 11.dp.toPx()), Size(36.dp.toPx(), 22.dp.toPx()))
    drawOval(Color(0xFF38943D), Offset(size.width * 0.58f - 18.dp.toPx(), size.height * 0.68f - 11.dp.toPx()), Size(36.dp.toPx(), 22.dp.toPx()))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.gardenCloud(cx: Float, cy: Float, width: Float, height: Float, alpha: Float) {
    val color = Color.White.copy(alpha = alpha)
    drawOval(color, Offset(cx - width / 2f, cy - height * 0.15f), Size(width * 0.55f, height * 0.7f))
    drawOval(color, Offset(cx - width * 0.22f, cy - height * 0.5f), Size(width * 0.5f, height * 0.85f))
    drawOval(color, Offset(cx, cy - height * 0.2f), Size(width * 0.48f, height * 0.65f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.gardenFoliage(cx: Float, cy: Float, width: Float) {
    val height = width * 0.72f
    val topLeft = Offset(cx - width / 2f, cy - height / 2f)
    val oval = Size(width, height)
    drawOval(Color(0xFF47B852), topLeft, oval)
    drawOval(Color.Black.copy(alpha = 0.25f), topLeft, oval, style = Stroke(width = 1.5.dp.toPx()))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.gardenRock(cx: Float, cy: Float, width: Float, height: Float) {
    rotate(degrees = -8f, pivot = Offset(cx, cy)) {
        val topLeft = Offset(cx - width / 2f, cy - height / 2f)
        val rock = Size(width, height)
        val radius = CornerRadius(8.dp.toPx())
        drawRoundRect(Color(0xFF8C8C8C), topLeft, rock, radius)
        drawRoundRect(Color.Black.copy(alpha = 0.35f), topLeft, rock, radius, style = Stroke(width = 1.5.dp.toPx()))
    }
}

@Composable
private fun BrainCall(friend: String?, onDecline: () -> Unit, onUnlock: (Int) -> Unit) {
    val context = LocalContext.current
    var accepted by remember { mutableStateOf(false) }
    var incomingHandled by remember { mutableStateOf(false) }
    var granted by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var step by remember { mutableIntStateOf(1) }
    IncomingRing(active = !accepted)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            MirrorPreview(Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("MIRROR MODE", color = Color.White, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            if (!accepted) {
                val ring = rememberPulse(1.08f, 1150, true)
                Appear(0f) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(Modifier.size(120.dp).graphicsLayer { scaleX = ring; scaleY = ring }.border(3.dp, Color.White.copy(alpha = 0.85f), CircleShape))
                        Text(friend ?: "Your Brain", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    }
                }
                Appear(0.08f) { Text("Wants a mirror match", color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp)) }
                if (!granted) {
                    Appear(0.16f) { Text("Camera access is off — the match still works.", color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp)) }
                    Appear(0.22f) { Text("Allow camera", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp).clickable { ask.launch(Manifest.permission.CAMERA) }) }
                }
            } else {
                Text("YOU", color = Color.White, fontWeight = FontWeight.Black)
                if (step == 1) {
                    Appear(0f) { MirrorQuestion("You really wanna keep scrolling?") }
                    Appear(0.08f) { Pill("Nah, you're right") { onDecline() } }
                    Appear(0.16f) { Pill("Yes, I really do") { step = 2 } }
                } else {
                    Appear(0f) { MirrorQuestion("How long do you need?") }
                    RiseIn(420) { DurationTiles(onUnlock, delays = listOf(0f, 0.05f, 0.10f)) }
                    Appear(0.16f) { Pill("Actually, never mind") { onDecline() } }
                }
            }
            Spacer(Modifier.weight(1f))
            if (!accepted) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) {
                        Pill("Decline") { if (!incomingHandled) { incomingHandled = true; onDecline() } }
                    }
                    Box(Modifier.weight(1f)) {
                        Pill("Accept") {
                            if (incomingHandled || BrainCallPolicy.answerStartsVoice()) return@Pill
                            incomingHandled = true
                            accepted = true
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IncomingRing(active: Boolean) {
    val context = LocalContext.current
    DisposableEffect(active) {
        if (!active) return@DisposableEffect onDispose { }
        val tone = ringtone(context)
        val vibrator = vibrator(context)
        tone?.play()
        val pattern = VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0)
        runCatching { vibrator?.vibrate(pattern) }
        onDispose {
            runCatching { tone?.stop() }
            runCatching { vibrator?.cancel() }
        }
    }
}

private fun ringtone(context: Context): Ringtone? = runCatching {
    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
    RingtoneManager.getRingtone(context, uri)
}.getOrNull()

private suspend fun pulseHaptic(vibrator: Vibrator?, amplitude: Int, totalMs: Int) {
    val ticks = totalMs / 500
    repeat(ticks) {
        runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(80, amplitude.coerceIn(1, 255))) }
        delay(500)
    }
}

private fun vibrator(context: Context): Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
    context.getSystemService(VibratorManager::class.java)?.defaultVibrator
} else {
    @Suppress("DEPRECATION")
    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
}

@Composable
private fun MirrorPreview(modifier: Modifier) {
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val view = androidx.camera.view.PreviewView(ctx)
            val future = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                val provider = future.get()
                val preview = androidx.camera.core.Preview.Builder().build()
                preview.surfaceProvider = view.surfaceProvider
                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        owner,
                        androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                    )
                }
            }, androidx.core.content.ContextCompat.getMainExecutor(ctx))
            view
        },
    )
}

@Composable
private fun Cameo(appName: String, friend: String, kind: String, onDismiss: () -> Unit, onUnlock: (Int) -> Unit) {
    var howLong by remember { mutableStateOf(false) }
    val stepSpring = swiftSpring<Float>(0.4f, 0.1f)
    val reveal by androidx.compose.animation.core.animateFloatAsState(if (howLong) 1f else 0f, stepSpring, label = "cameo")
    val roast = InterventionCopy.roast(kind.ifBlank { "note" }, friend)
    val pieces = roast.split(". ").map { it.trim().trimEnd('.') }.filter { it.isNotEmpty() }
    Appear(0f) { Text("Note from $friend", fontWeight = FontWeight.Bold, fontSize = 22.sp) }
    Appear(0.06f) { Text(pieces.first(), textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp)) }
    Appear(0.10f) { Text("Opening $appName anyway?") }
    Appear(0.14f) { Text(pieces.getOrElse(1) { "This does not look important" }, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp)) }
    Appear(0.16f) { CartoonBrain(BrainStyle.Sleeping, Modifier.padding(top = 8.dp).size(96.dp), animated = false) }
    if (!howLong) {
        Appear(0.20f) { Pill("Walk away") { onDismiss() } }
        Appear(0.26f) { Pill("Continue") { howLong = true } }
    } else {
        Box(Modifier.graphicsLayer { alpha = reveal }) { DurationTiles(onUnlock) }
        Pill("Nah, never mind") { onDismiss() }
    }
}

@Composable
private fun DurationTiles(onUnlock: (Int) -> Unit, delays: List<Float> = emptyList()) {
    data class Tile(val minutes: Int, val label: String)
    Column(Modifier.fillMaxWidth()) {
    listOf(Tile(10, "Quick peek"), Tile(20, "A chapter"), Tile(30, "Long scroll")).forEachIndexed { index, tile ->
        val body = @Composable {
            Column(
                Modifier.padding(vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).clickable { onUnlock(tile.minutes) }.padding(12.dp),
            ) {
                Text("${tile.minutes}", fontWeight = FontWeight.Black, fontSize = 22.sp)
                Text("minutes · ${tile.label}", color = Melt.Muted)
            }
        }
        val delaySec = delays.getOrNull(index)
        if (delaySec == null) body() else Appear(delaySec) { body() }
    }
    }
}

@Composable
private fun MirrorQuestion(question: String) {
    Box(Modifier.padding(vertical = 8.dp).fillMaxWidth()) {
        Box(Modifier.matchParentSize().offset(y = 5.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF295C38)))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🧠", fontFamily = FontFamily.Default, fontSize = 28.sp, textAlign = TextAlign.Center)
            Text(
                question,
                color = Color(0xFF1F3329),
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun Bubble(text: String) {
    Box(Modifier.padding(vertical = 8.dp).fillMaxWidth()) {
        Box(Modifier.matchParentSize().offset(y = 5.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF295C38)))
        Text(
            text,
            color = Color(0xFF1F3329),
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).padding(horizontal = 18.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun RansomSheet(reduce: Boolean, onCancel: () -> Unit, onPaid: () -> Unit) {
    val context = LocalContext.current
    var holding by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var done by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (holding && !reduce) 1.08f else 1f,
        androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.EaseInOut),
        label = "robe",
    )
    LaunchedEffect(holding) {
        if (!holding || done) {
            if (!done) progress = 0f
            return@LaunchedEffect
        }
        while (progress < 1f) {
            delay(50)
            progress = (progress + 50f / 8000f).coerceAtMost(1f)
        }
        if (!done) {
            done = true
            runCatching { vibrator(context)?.vibrate(VibrationEffect.createOneShot(40, 180)) }
            onPaid()
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF1A1030)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text("Hold to bail out", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 28.sp, textAlign = TextAlign.Center)
            Text("8 seconds. It costs 100 XP.", color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(220.dp).pointerInput(done) {
                    detectTapGestures(onPress = {
                        if (done) return@detectTapGestures
                        holding = true
                        tryAwaitRelease()
                        holding = false
                    })
                },
            ) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 10.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    drawArc(Color.White.copy(alpha = 0.25f), 0f, 360f, false, style = stroke)
                    drawArc(Color.White, -90f, 360f * progress, false, style = stroke)
                }
                CartoonBrain(BrainStyle.Robe, Modifier.size(140.dp).graphicsLayer { scaleX = scale; scaleY = scale }, animated = false)
            }
            Text("Cancel", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 28.dp).clickable(onClick = onCancel))
        }
    }
}

@Composable
private fun Pill(label: String, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Box(Modifier.padding(vertical = 4.dp).fillMaxWidth().chunkyPress(press)) {
        Box(Modifier.matchParentSize().offset(y = 5.dp).clip(CircleShape).background(Color(0xFF0F3D9E)))
        Text(
            label,
            fontWeight = FontWeight.Black,
            modifier = Modifier.fillMaxWidth().clip(CircleShape).background(Melt.Action).clickable(interactionSource = press, indication = null, onClick = onClick).padding(vertical = 14.dp),
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}
