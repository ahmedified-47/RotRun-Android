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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
    val chrome = rot()
    Box(Modifier.fillMaxWidth().padding(top = 70.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(chrome.card).padding(horizontal = 18.dp).padding(top = 78.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Focus Mode", color = chrome.ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Go into focus mode to heal your brain!", color = chrome.muted, fontSize = 15.sp, textAlign = TextAlign.Center)
            FocusRow("Target Time", if (legendary) "As long as you can!" else "${minutes}m", Color(0xFFFF375F), legendary = false) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Change target",
                    tint = chrome.muted,
                    modifier = Modifier.clickable {
                        legendary = false
                        minutes = targets[(targets.indexOf(minutes) + 1).mod(targets.size)]
                    },
                )
            }
            FocusRow("Apps to block", if (scope == "all") "All Apps" else "Brainrot Apps", Color(0xFF5E5CE6), legendary = false) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Change apps",
                    tint = chrome.muted,
                    modifier = Modifier.clickable { scope = if (scope == "all") "brainrot" else "all" },
                )
            }
            FocusRow("Legendary Mode", if (legendary) "On" else "Off", Melt.Orange, legendary = legendary) {
                Switch(
                    checked = legendary,
                    onCheckedChange = { legendary = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = chrome.green, checkedThumbColor = Color.White, uncheckedThumbColor = Color.White, uncheckedTrackColor = chrome.pill, uncheckedBorderColor = Color.Transparent),
                )
            }
            PrimaryButton("Start Focus") {
                repo.startFocus(if (legendary) null else minutes, legendary, scope)
                onClose()
            }
        }
        CartoonBrain(BrainStyle.Meadow, Modifier.align(Alignment.TopCenter).offset(y = (-64).dp).size(150.dp), animated = false)
    }
}

@Composable
private fun FocusRow(title: String, value: String, tint: Color, legendary: Boolean, trailing: @Composable () -> Unit) {
    val chrome = rot()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (legendary) Melt.Orange else chrome.inset).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(
                when (title) {
                    "Apps to block" -> Icons.Default.PhoneAndroid
                    "Legendary Mode" -> Icons.Default.LocalFireDepartment
                    else -> Icons.Default.KeyboardArrowDown
                },
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(title, color = if (legendary) Color.White.copy(alpha = 0.85f) else chrome.muted, fontSize = 13.sp)
            Text(value, color = if (legendary) Color.White else chrome.ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
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

