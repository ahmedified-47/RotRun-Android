package com.ahmed.rotrun.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmed.rotrun.data.FocusSession
import com.ahmed.rotrun.data.RotRunRepository
import com.ahmed.rotrun.logic.Scoring
import kotlinx.coroutines.delay

@Composable
fun FocusChooser(onClose: () -> Unit, onCouch: () -> Unit, repo: RotRunRepository) {
    var minutes by remember { mutableIntStateOf(30) }
    var scope by remember { mutableStateOf("all") }
    var legendary by remember { mutableStateOf(false) }
    val targets = listOf(15, 30, 45, 60)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color.White).padding(20.dp)) {
        Text("Focus Mode", fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text("Go into focus mode to heal your brain!", color = Melt.Muted)
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            CartoonBrain(BrainStyle.Spa, Modifier.size(120.dp))
        }
        Text("Target Time", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            targets.forEach { value ->
                Text("${value}m", color = if (!legendary && minutes == value) Color.White else Melt.Ink, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (!legendary && minutes == value) Melt.Action else Melt.Grouped).clickable { legendary = false; minutes = value }.padding(10.dp))
            }
        }
        Text(
            "As long as you can!",
            color = if (legendary) Color.White else Melt.Ink,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (legendary) Melt.Action else Melt.Grouped).clickable { legendary = true }.padding(10.dp),
        )
        Text("Apps to block", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to "All Apps", "brainrot" to "Brainrot Apps").forEach { (id, label) ->
                Text(label, color = if (scope == id) Color.White else Melt.Ink, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (scope == id) Melt.Action else Melt.Grouped).clickable { scope = id }.padding(10.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Legendary Mode", fontWeight = FontWeight.Bold)
                Text(if (legendary) "On" else "Off", color = Melt.Muted)
            }
            Switch(checked = legendary, onCheckedChange = { legendary = it })
        }
        PrimaryButton("Start Focus") {
            repo.startFocus(if (legendary) null else minutes, legendary, scope)
            onClose()
        }
        Text("Start Couch Reset", color = Melt.Action, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp).clickable(onClick = onCouch))
        Text("Close", color = Melt.Muted, modifier = Modifier.padding(top = 8.dp).clickable(onClick = onClose))
    }
}

@Composable
fun FocusOverlay(session: FocusSession, repo: RotRunRepository) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session.startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            repo.maybeExpireFocus()
            delay(500)
        }
    }
    val elapsed = ((now - session.startedAt) / 1000).coerceAtLeast(0)
    val remaining = session.targetMinutes?.let { (it * 60 - elapsed).coerceAtLeast(0) }
    Column(Modifier.fillMaxSize().background(Melt.AscendBottom).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Focus Mode", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text(if (session.targetMinutes == null) "As long as you can" else "Time remaining", color = Color.White.copy(alpha = 0.8f))
        CartoonBrain(BrainStyle.Spa, Modifier.size(140.dp).padding(top = 12.dp))
        Text(Scoring.formatClock(remaining ?: elapsed), color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(vertical = 12.dp))
        PrimaryButton("End Focus", light = true) { repo.endFocus() }
    }
}

@Composable
fun CouchReset(onClose: () -> Unit, repo: RotRunRepository) {
    var reps by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(Color(0xFF0A0A0F)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Couch Reset", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Do 10 Sit-ups to unlock 5 mins", color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
        Text("$reps / 10", color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(vertical = 20.dp))
        Text("Tap once per sit-up. Camera pose tracking isn’t on Android yet.", color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center)
        PrimaryButton(if (reps >= 10) "Unlock 5 mins" else "Count a sit-up") {
            if (reps >= 10) {
                repo.grantUnlock(5)
                onClose()
            } else {
                reps += 1
            }
        }
        Text("Log Run / Workout", color = Color.White, modifier = Modifier.padding(top = 16.dp).clickable { repo.logWorkout() })
        Text("Saved on this device", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
        Text("Close", color = Melt.Muted, modifier = Modifier.padding(top = 12.dp).clickable(onClick = onClose))
    }
}
