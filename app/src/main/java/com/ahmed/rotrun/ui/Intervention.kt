package com.ahmed.rotrun.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmed.rotrun.data.RotRunRepository
import com.ahmed.rotrun.logic.InterventionCopy
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
) {
    val state by repo.state.collectAsState()
    var mode by remember { mutableStateOf(startMode) }
    val name = InterventionCopy.displayName(appName)
    val friend = state.cameoName.takeIf { state.cameoToday() }
    val shield = if (mode == "shield") shieldColors(InterventionCopy.forApp(name).id) else null
    if (mode == "shield") {
        Column(
            Modifier.fillMaxSize().background(shield?.second ?: Color(0xFF3D1A28)).padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Shield(name, shield?.first ?: Melt.Action, onAsk = {
                val style = repo.pickStyle() ?: "standard"
                repo.chargeAskBrain(System.currentTimeMillis())
                mode = style
            })
        }
    } else if (mode == "standard") {
        Standard(name, friend, state.cameoKind, onDismiss, onUnlock)
    } else {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF8FDBFA), Color(0xFF66C785), Color(0xFF338C57))))) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (mode) {
                    "quick" -> Quick(name, friend, onDismiss, onUnlock)
                    "breathing" -> Breathing(friend, state.cameoKind, onDismiss, onUnlock)
                    "mirror" -> Mirror(friend, onDismiss, onUnlock)
                    "cameo" -> Cameo(name, friend ?: "a friend", state.cameoKind, onDismiss, onUnlock)
                    else -> Quick(name, friend, onDismiss, onUnlock)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.Shield(appName: String, accent: Color, onAsk: () -> Unit) {
    val copy = InterventionCopy.forApp(appName)
    Spacer(Modifier.weight(1f))
    CartoonBrain(BrainStyle.Meadow, Modifier.size(196.dp))
    Text(copy.headline, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 28.dp))
    Text(copy.body, color = Color(0xFF9E9EA3), fontSize = 17.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
    if (copy.footer != null) Text(copy.footer, color = Color(0xFF9E9EA3), fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
    Spacer(Modifier.weight(1f))
    Text(
        "Ask my brain",
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().clip(CircleShape).background(accent).clickable(onClick = onAsk).padding(vertical = 18.dp),
    )
}

private fun shieldColors(id: String): Pair<Color, Color> {
    val accent = when (id) {
        "stop-scrolling", "brain-first" -> Color(0xFFE6389E)
        "read-anything" -> Color(0xFF8C6147)
        "hydrate" -> Color(0xFF38B8D1)
        "cheap-dopamine", "lock-in" -> Color(0xFF617AB3)
        "how-many-hours" -> Color(0xFF8F5CC7)
        "future-self" -> Color(0xFF387A52)
        "touch-grass" -> Color(0xFF249E94)
        "brain-rest" -> Color(0xFFF2857A)
        "how-much-today", "melting" -> Color(0xFFF28538)
        "zoned-out" -> Color(0xFF8C8CE6)
        "not-again" -> Color(0xFFF26B6B)
        else -> Color(0xFFB84747)
    }
    return accent to Color(android.graphics.Color.HSVToColor(floatArrayOf(accent.toHsvHue(), 0.62f, 0.24f)))
}

private fun Color.toHsvHue(): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(), hsv)
    return hsv[0]
}

@Composable
private fun Standard(appName: String, friend: String?, kind: String, onDismiss: () -> Unit, onUnlock: (Int) -> Unit) {
    var phase by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(10) }
    var line by remember { mutableStateOf("") }
    val stamp = remember { SimpleDateFormat("h:mm a", Locale.US).format(Date()).lowercase() }
    val blue = Color(0xFF007AFF)
    LaunchedEffect(phase) {
        if (phase == 0) {
            delay(380)
            line = if (friend != null) "$friend left a note.\nOpening $appName anyway?" else "you really gotta open this?"
            phase = 1
        }
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
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Today $stamp", color = Color(0xFFC7C7CC), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp))
            ChatBubble("yo it's me. brain", true, blue)
            if (line.isNotBlank()) line.split("\n").forEach { ChatBubble(it, true, blue) }
            if (phase >= 2) ChatBubble(if (phase == 2) "nah, you're right" else "yes i really do", false, blue)
            if (phase == 2) ChatBubble("bet. go live.", true, blue)
            if (phase >= 3 && phase != 2) {
                ChatBubble("alright more rot it is 🥱", true, blue)
                ChatBubble("so how long?", true, blue)
            }
            if (phase >= 4) {
                ChatBubble("$minutes mins", false, blue)
                ChatBubble("ok lol go for it", true, blue)
            }
            if (phase == 1 || phase == 3) {
                Column(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalAlignment = Alignment.End) {
                    Text("Tap to reply ↓", color = Color(0xFFC7C7CC), fontSize = 13.sp)
                    if (phase == 1) {
                        ReplyPill("nah, you're right", blue) { phase = 2 }
                        ReplyPill("yes i really do", blue) { phase = 3 }
                    } else {
                        listOf(10, 20, 30).forEach { value -> ReplyPill("$value mins", blue) { minutes = value; phase = 4 } }
                        ReplyPill("actually nevermind", blue) { phase = 2 }
                    }
                }
            }
            if (friend != null && kind.isNotBlank()) {
                Text(InterventionCopy.roast(kind, friend), color = Melt.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            }
        }
        if (phase == 2) {
            LaunchedEffect(Unit) { delay(850); onDismiss() }
        }
        if (phase == 4) {
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

@Composable
private fun ChatBubble(text: String, brain: Boolean, blue: Color) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
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

@Composable
private fun Quick(appName: String, friend: String?, onDismiss: () -> Unit, onUnlock: (Int) -> Unit) {
    var step by remember { mutableIntStateOf(1) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
    }
    Spacer(Modifier.height(20.dp))
    Bubble(if (step == 1) {
        if (friend != null) "Would $friend believe this is important?" else "Hey. Do you really need this right now?"
    } else {
        "Okay, you can scroll some more, just remember it hurts me."
    })
    CartoonBrain(if (step == 1) BrainStyle.Watch else BrainStyle.Pleading, Modifier.size(180.dp))
    Spacer(Modifier.height(12.dp))
    if (step == 1) {
        Pill("Nah, you're right") { onDismiss() }
        Text("Yes, I really do", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.padding(vertical = 8.dp).clickable { step = 2 })
    } else {
        DurationTiles(onUnlock)
        Text("Nah, never mind", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.padding(vertical = 8.dp).clickable(onClick = onDismiss))
    }
}

@Composable
private fun Breathing(friend: String?, kind: String, onDismiss: () -> Unit, onUnlock: (Int) -> Unit) {
    var phase by remember { mutableIntStateOf(0) }
    var left by remember { mutableIntStateOf(4) }
    val labels = listOf("Breathe in.", "Hold.", "Breathe out.")
    val lengths = listOf(4, 2, 4)
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
    CartoonBrain(BrainStyle.Spa, Modifier.size(140.dp))
    if (phase <= 2) {
        Text(labels[phase], fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("$left ${if (left == 1) "second" else "seconds"}", color = Melt.Muted)
        if (friend != null) Text(InterventionCopy.roast(kind, friend), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
    } else {
        var ask by remember { mutableStateOf(true) }
        if (ask) {
            Bubble("Be honest. Do you actually want this right now?")
            Pill("Nah, you're right.") { onDismiss() }
            Pill("Yes I really do") { ask = false }
        } else {
            Bubble("How long do you need?")
            listOf(10, 20, 30).forEach { Pill("$it min") { onUnlock(it) } }
            Pill("Nah actually nevermind") { onDismiss() }
        }
    }
}

@Composable
private fun Mirror(friend: String?, onDismiss: () -> Unit, onUnlock: (Int) -> Unit) {
    val context = LocalContext.current
    var accepted by remember { mutableStateOf(false) }
    var granted by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var step by remember { mutableIntStateOf(1) }
    Text("MIRROR MODE", color = Melt.Action, fontWeight = FontWeight.Black)
    Text(friend ?: "Your Brain", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    if (!accepted) {
        Text("Wants a mirror match")
        if (!granted) {
            Text("Camera access is off — the match still works.", color = Melt.Muted, textAlign = TextAlign.Center)
            Text("Open Settings", color = Melt.Action, modifier = Modifier.clickable {
                ask.launch(Manifest.permission.CAMERA)
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}")))
            })
        } else {
            MirrorPreview(Modifier.padding(8.dp).fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Pill("Decline") { onDismiss() }
            Pill("Accept") { accepted = true }
        }
    } else {
        if (granted) MirrorPreview(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)))
        else Text("YOU", color = Color.White, modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black).padding(20.dp))
        if (step == 1) {
            Bubble("You really wanna keep scrolling?")
            Pill("Nah, you're right") { onDismiss() }
            Pill("Yes, I really do") { step = 2 }
        } else {
            Bubble("How long do you need?")
            DurationTiles(onUnlock)
            Pill("Actually, never mind") { onDismiss() }
        }
    }
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
    Text("Note from $friend", fontWeight = FontWeight.Bold, fontSize = 22.sp)
    Text(InterventionCopy.roast(kind.ifBlank { "note" }, friend), textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
    Text("Opening $appName anyway?")
    if (!howLong) {
        Pill("Walk away") { onDismiss() }
        Pill("Continue") { howLong = true }
    } else {
        DurationTiles(onUnlock)
        Pill("Nah, never mind") { onDismiss() }
    }
}

@Composable
private fun DurationTiles(onUnlock: (Int) -> Unit) {
    data class Tile(val minutes: Int, val label: String)
    listOf(Tile(10, "Quick peek"), Tile(20, "A chapter"), Tile(30, "Long scroll")).forEach { tile ->
        Column(
            Modifier.padding(vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).clickable { onUnlock(tile.minutes) }.padding(12.dp),
        ) {
            Text("${tile.minutes}", fontWeight = FontWeight.Black, fontSize = 22.sp)
            Text("minutes · ${tile.label}", color = Melt.Muted)
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
private fun Pill(label: String, onClick: () -> Unit) {
    Box(Modifier.padding(vertical = 4.dp).fillMaxWidth()) {
        Box(Modifier.matchParentSize().offset(y = 5.dp).clip(CircleShape).background(Color(0xFF0F3D9E)))
        Text(
            label,
            fontWeight = FontWeight.Black,
            modifier = Modifier.fillMaxWidth().clip(CircleShape).background(Melt.Action).clickable(onClick = onClick).padding(vertical = 14.dp),
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}
