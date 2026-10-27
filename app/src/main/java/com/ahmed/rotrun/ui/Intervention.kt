package com.ahmed.rotrun.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    Column(Modifier.fillMaxSize().background(Color(0xFFF4F1EC)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        when (mode) {
            "shield" -> Shield(name, onAsk = {
                val style = repo.pickStyle() ?: "standard"
                repo.chargeAskBrain(System.currentTimeMillis())
                mode = style
            })
            "standard" -> Standard(name, friend, state.cameoKind, onDismiss, onUnlock)
            "quick" -> Quick(name, friend, onDismiss, onUnlock)
            "breathing" -> Breathing(friend, state.cameoKind, onDismiss, onUnlock)
            "mirror" -> Mirror(friend, onDismiss, onUnlock)
            "cameo" -> Cameo(name, friend ?: "a friend", state.cameoKind, onDismiss, onUnlock)
            else -> Quick(name, friend, onDismiss, onUnlock)
        }
    }
}

@Composable
private fun Shield(appName: String, onAsk: () -> Unit) {
    val copy = InterventionCopy.forApp(appName)
    CartoonBrain(BrainStyle.Meadow, Modifier.size(140.dp))
    Text(copy.headline, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Text(copy.body, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
    if (copy.footer != null) Text(copy.footer, color = Melt.Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
    Box(Modifier.padding(top = 20.dp)) { PrimaryButton("Ask my brain", onClick = onAsk) }
}

@Composable
private fun Standard(appName: String, friend: String?, kind: String, onDismiss: () -> Unit, onUnlock: (Int) -> Unit) {
    var phase by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(10) }
    var line by remember { mutableStateOf("") }
    val stamp = remember { SimpleDateFormat("h:mm a", Locale.US).format(Date()) }
    LaunchedEffect(phase) {
        if (phase == 0) {
            delay(380)
            line = if (friend != null) "$friend left a note.\nOpening $appName anyway?" else "you really gotta open this?"
            phase = 1
        }
    }
    Text("‹ $appName", modifier = Modifier.fillMaxWidth())
    Text("Your Brain", fontWeight = FontWeight.Bold)
    Text("Today $stamp", color = Melt.Muted, fontSize = 12.sp)
    Bubble(if (line.isBlank()) "yo it's me. brain" else "yo it's me. brain\n$line")
    if (phase == 1) {
        Text("Tap to reply ↓", color = Melt.Muted)
        Pill("nah, you're right") { phase = 2 }
        Pill("yes i really do") { phase = 3 }
    }
    if (phase == 2) {
        Bubble("bet. go live.")
        LaunchedEffect(Unit) { delay(850); onDismiss() }
    }
    if (phase == 3) {
        Bubble("alright more rot it is 🥱\nso how long?")
        listOf(10, 20, 30).forEach { value -> Pill("$value mins") { minutes = value; phase = 4 } }
        Pill("actually nevermind") { phase = 2 }
    }
    if (phase == 4) {
        Bubble("ok lol go for it")
        Pill("Actually I don't need this") { onDismiss() }
        Pill("Open the app and rot") { onUnlock(minutes) }
    }
    if (friend != null && kind.isNotBlank()) Text(InterventionCopy.roast(kind, friend), color = Melt.Muted, fontSize = 12.sp)
}

@Composable
private fun Quick(appName: String, friend: String?, onDismiss: () -> Unit, onUnlock: (Int) -> Unit) {
    var step by remember { mutableIntStateOf(1) }
    Text("QUICK CHECK", color = Melt.Action, fontWeight = FontWeight.Black)
    Text(if (step == 1) "1" else "2", color = Melt.Muted)
    CartoonBrain(if (step == 1) BrainStyle.Watch else BrainStyle.Pleading, Modifier.size(120.dp))
    if (step == 1) {
        Bubble(if (friend != null) "Would $friend believe this is important?" else "Hey. Do you really need this right now?")
        Pill("Nah, you're right") { onDismiss() }
        Pill("Yes, I really do") { step = 2 }
    } else {
        Bubble("Okay, you can scroll some more, just remember it hurts me.")
        DurationTiles(onUnlock)
        Pill("Nah, never mind") { onDismiss() }
    }
    Text(appName, color = Melt.Muted, fontSize = 12.sp)
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
            Text("YOU", modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black).padding(24.dp), color = Color.White)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Pill("Decline") { onDismiss() }
            Pill("Accept") { accepted = true }
        }
    } else {
        Text("YOU", color = Color.White, modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black).padding(20.dp))
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
    Text(text, modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(14.dp))
}

@Composable
private fun Pill(label: String, onClick: () -> Unit) {
    Text(
        label,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth().clip(CircleShape).background(Melt.Action).clickable(onClick = onClick).padding(vertical = 12.dp),
        color = Color.White,
        textAlign = TextAlign.Center,
    )
}
