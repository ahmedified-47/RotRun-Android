package com.ahmed.rotrun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
fun FocusChooser(onClose: () -> Unit, repo: RotRunRepository) {
    var minutes by remember { mutableIntStateOf(30) }
    var scope by remember { mutableStateOf("all") }
    var legendary by remember { mutableStateOf(false) }
    val targets = listOf(15, 30, 45, 60)
    Box(Modifier.fillMaxWidth().padding(top = 78.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(36.dp)).background(Color.White).padding(horizontal = 22.dp).padding(top = 84.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Focus Mode", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Go into focus mode to heal your brain!", color = Melt.Muted, fontSize = 15.sp, textAlign = TextAlign.Center)
            FocusRow("Target Time", if (legendary) "As long as you can!" else "${minutes}m", legendary = false) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    targets.forEach { value ->
                        Text(
                            "${value}m",
                            color = if (!legendary && minutes == value) Melt.Action else Melt.Muted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable { legendary = false; minutes = value },
                        )
                    }
                    Text("∞", color = if (legendary) Melt.Action else Melt.Muted, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { legendary = true })
                }
            }
            FocusRow("Apps to block", if (scope == "all") "All Apps" else "Brainrot Apps", legendary = false) {
                Text(if (scope == "all") "Brainrot" else "All", color = Melt.Action, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { scope = if (scope == "all") "brainrot" else "all" })
            }
            FocusRow("Legendary Mode", if (legendary) "On" else "Off", legendary = legendary) {
                Switch(checked = legendary, onCheckedChange = { legendary = it })
            }
            PrimaryButton("Start Focus") {
                repo.startFocus(if (legendary) null else minutes, legendary, scope)
                onClose()
            }
        }
        CartoonBrain(BrainStyle.Meditating, Modifier.align(Alignment.TopCenter).offset(y = (-78).dp).size(168.dp), animated = false)
    }
}

@Composable
private fun FocusRow(title: String, value: String, legendary: Boolean, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(if (legendary) Color(0xFFFF8C2E) else Color(0xFFF2F2F7)).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (legendary) Color.White.copy(alpha = 0.85f) else Melt.Muted, fontSize = 13.sp)
            Text(value, color = if (legendary) Color.White else Melt.Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        trailing()
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
    Column(Modifier.fillMaxSize().background(Melt.Meadow.copy(alpha = 0.97f)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        CartoonBrain(BrainStyle.Meditating, Modifier.size(140.dp))
        Text("Focus Mode", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(if (session.targetMinutes == null || session.legendary) "As long as you can" else "Time remaining", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(Scoring.formatClock(if (session.targetMinutes == null || session.legendary) elapsed else (remaining ?: elapsed)), color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(vertical = 8.dp))
        Box(Modifier.padding(horizontal = 12.dp).padding(top = 8.dp)) {
            PrimaryButton("End Focus", light = true, textColor = Melt.Action) { repo.endFocus() }
        }
        Spacer(Modifier.weight(1f))
    }
}

