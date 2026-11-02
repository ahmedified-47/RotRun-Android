package com.ahmed.rotrun.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmed.rotrun.data.FocusSession
import com.ahmed.rotrun.data.RotRunRepository
import com.ahmed.rotrun.logic.Scoring
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

private val ActionBlue = Color(red = 0.13f, green = 0.48f, blue = 1f)
private val Ink = Color(red = 0.10f, green = 0.13f, blue = 0.20f)
private val ClockInk = Color(red = 0.10f, green = 0.13f, blue = 0.22f)

private data class FocusChoice(val label: String, val minutes: Int?)

private val focusChoices = listOf(
    FocusChoice("15m", 15),
    FocusChoice("30m", 30),
    FocusChoice("45m", 45),
    FocusChoice("60m", 60),
    FocusChoice("As long as you can!", null),
)

@Composable
fun FocusChooser(onClose: () -> Unit, repo: RotRunRepository) {
    var choice by remember { mutableStateOf(focusChoices[1]) }
    var scope by remember { mutableStateOf("all") }
    var legendary by remember { mutableStateOf(false) }
    var timeOpen by remember { mutableStateOf(false) }
    var appsOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        FocusGarden()
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.92f)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(red = 0.16f, green = 0.20f, blue = 0.28f), modifier = Modifier.size(16.dp))
                }
            }
            Text(
                "Heal your brain\nwith Focus.",
                color = Ink,
                fontFamily = Nunito,
                fontWeight = FontWeight.Black,
                fontSize = 34.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            HangingBrain(Modifier.padding(top = 4.dp).align(Alignment.CenterHorizontally), 0.82f)
            OutlinedClock(if (choice.minutes == null) "Open" else "${choice.minutes}:00", Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.weight(1f))
            Column(
                Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).clip(RoundedCornerShape(28.dp)).background(Color.White.copy(alpha = 0.94f)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FocusSetting("Target Time", choice.label, legendary = false, icon = {
                    Text("◎", color = Color(red = 0.95f, green = 0.28f, blue = 0.32f), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }) {
                    Box {
                        Chevron { timeOpen = true }
                        DropdownMenu(expanded = timeOpen, onDismissRequest = { timeOpen = false }) {
                            focusChoices.forEach { option ->
                                DropdownMenuItem(text = { Text(option.label, fontFamily = Nunito) }, onClick = { choice = option; timeOpen = false })
                            }
                        }
                    }
                }
                FocusSetting("Apps to block", if (scope == "all") "All Apps" else "Brainrot Apps", legendary = false, icon = {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = Color(red = 0.45f, green = 0.40f, blue = 0.92f), modifier = Modifier.size(18.dp))
                }) {
                    Box {
                        Chevron { appsOpen = true }
                        DropdownMenu(expanded = appsOpen, onDismissRequest = { appsOpen = false }) {
                            listOf("all" to "All Apps", "brainrot" to "Brainrot Apps").forEach { (id, label) ->
                                DropdownMenuItem(text = { Text(label, fontFamily = Nunito) }, onClick = { scope = id; appsOpen = false })
                            }
                        }
                    }
                }
                FocusSetting("Legendary Mode", if (legendary) "On" else "Off", legendary = legendary, icon = {
                    Text("🔥", fontSize = 20.sp)
                }) {
                    Switch(
                        checked = legendary,
                        onCheckedChange = { legendary = it },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Color.White,
                            checkedThumbColor = Color(red = 1f, green = 0.55f, blue = 0.18f),
                            uncheckedTrackColor = Color(red = 0.20f, green = 0.78f, blue = 0.45f),
                            uncheckedThumbColor = Color.White,
                            uncheckedBorderColor = Color.Transparent,
                        ),
                    )
                }
                Text(
                    "Start Focus",
                    color = Color.White,
                    fontFamily = Nunito,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp).fillMaxWidth().clip(CircleShape).background(ActionBlue).clickable {
                        repo.startFocus(if (legendary) null else choice.minutes, legendary, scope)
                        onClose()
                    }.padding(vertical = 16.dp),
                )
            }
        }
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
    val unlimited = session.targetMinutes == null || session.legendary
    val remaining = session.targetMinutes?.let { (it * 60 - elapsed).coerceAtLeast(0) }
    val clock = Scoring.formatClock(if (unlimited) elapsed else (remaining ?: elapsed))
    Box(Modifier.fillMaxSize()) {
        FocusGarden()
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            HangingBrain(Modifier.padding(top = 8.dp), 1f)
            Text(
                if (unlimited) "As long as you can" else "Time remaining",
                color = Color(red = 0.16f, green = 0.22f, blue = 0.32f).copy(alpha = 0.72f),
                fontFamily = Nunito,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 18.dp),
            )
            OutlinedClock(clock, Modifier.padding(top = 4.dp, start = 12.dp, end = 12.dp))
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.padding(bottom = 36.dp).size(78.dp).clip(CircleShape).background(Color.White).clickable { repo.endFocus() },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)).background(Color(red = 0.93f, green = 0.22f, blue = 0.24f)))
            }
        }
    }
}

@Composable
private fun HangingBrain(modifier: Modifier, scale: Float) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        CoilSpring(Modifier.size(width = 34.dp, height = 78.dp))
        CartoonBrain(BrainStyle.Meditating, Modifier.size((168 * scale).dp).offset(y = (-6).dp), animated = true)
    }
}

@Composable
private fun CoilSpring(modifier: Modifier) {
    Canvas(modifier) {
        val coils = 8
        val step = size.height / coils
        repeat(coils) { index ->
            val y = index * step
            drawOval(
                color = Color(red = 0.20f, green = 0.22f, blue = 0.26f),
                topLeft = Offset(1f, y),
                size = Size(size.width - 2f, step * 1.35f),
                style = Stroke(width = 3.5f),
            )
        }
    }
}

@Composable
private fun OutlinedClock(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        repeat(12) { index ->
            val angle = index / 12.0 * 2 * Math.PI
            Text(
                text,
                color = Color.White,
                fontFamily = Nunito,
                fontWeight = FontWeight.Black,
                fontSize = 84.sp,
                maxLines = 1,
                modifier = Modifier.offset(x = (cos(angle) * 5).dp, y = (sin(angle) * 5).dp),
            )
        }
        Text(text, color = ClockInk, fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 84.sp, maxLines = 1)
    }
}

@Composable
private fun FocusSetting(title: String, value: String, legendary: Boolean, icon: @Composable () -> Unit, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (legendary) Color(red = 1f, green = 0.55f, blue = 0.18f) else Color(0xFFF2F2F7)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { icon() }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, color = if (legendary) Color.White.copy(alpha = 0.85f) else Color(0xFF8E8E93), fontFamily = Nunito, fontWeight = FontWeight.Medium, fontSize = 12.sp)
            Text(value, color = if (legendary) Color.White else Color.Black, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        trailing()
    }
}

@Composable
private fun Chevron(onClick: () -> Unit) {
    Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0xFFE5E5EA)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color(0xFFC7C7CC), modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun FocusGarden() {
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(
                        Color(red = 0.72f, green = 0.90f, blue = 0.98f),
                        Color(red = 0.56f, green = 0.82f, blue = 0.95f),
                        Color(red = 0.62f, green = 0.84f, blue = 0.55f),
                    ),
                ),
            )
            fun cloud(cx: Float, cy: Float, scale: Float) {
                listOf(Triple(-28f, 6f, 22f), Triple(0f, -6f, 28f), Triple(26f, 4f, 20f)).forEach { (dx, dy, r) ->
                    val w = r * scale
                    drawOval(Color.White.copy(alpha = 0.92f), topLeft = Offset(cx + dx * scale - w / 2f, cy + dy * scale - w * 0.7f), size = Size(w, w * 0.72f))
                }
            }
            cloud(size.width * 0.22f, size.height * 0.10f, 1f)
            cloud(size.width * 0.78f, size.height * 0.16f, 0.85f)
            cloud(size.width * 0.55f, size.height * 0.06f, 0.55f)
            val hill = Path().apply {
                moveTo(0f, size.height * 0.58f)
                quadraticTo(size.width * 0.48f, size.height * 0.46f, size.width, size.height * 0.52f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(hill, Color(red = 0.49f, green = 0.78f, blue = 0.40f))
            val near = Path().apply {
                moveTo(0f, size.height * 0.72f)
                quadraticTo(size.width * 0.4f, size.height * 0.64f, size.width, size.height * 0.68f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(near, Color(red = 0.40f, green = 0.70f, blue = 0.34f))
            val sand = Offset(size.width * 0.50f, size.height * 0.70f)
            listOf(0.34f, 0.24f, 0.15f).forEach { ring ->
                val w = size.width * ring
                drawOval(
                    Color.White.copy(alpha = 0.45f),
                    topLeft = Offset(sand.x - w / 2f, sand.y - w * 0.18f),
                    size = Size(w, w * 0.36f),
                    style = Stroke(width = 2f),
                )
            }
            fun rock(at: Offset, scale: Float) {
                val w = 46f * scale
                val h = 28f * scale
                val rect = Offset(at.x - w / 2f, at.y - h / 2f)
                drawOval(Color(red = 0.62f, green = 0.66f, blue = 0.70f), rect, Size(w, h))
                drawOval(Color(red = 0.78f, green = 0.81f, blue = 0.84f), rect + Offset(6f * scale, 5f * scale), Size(w - 12f * scale, h - 10f * scale))
            }
            rock(Offset(size.width * 0.18f, size.height * 0.66f), 1.1f)
            rock(Offset(size.width * 0.82f, size.height * 0.64f), 0.9f)
            rock(Offset(size.width * 0.74f, size.height * 0.78f), 0.7f)
        }
        FocusTree(Modifier.align(Alignment.TopCenter).padding(top = 54.dp).padding(end = 36.dp).size(width = 210.dp, height = 250.dp))
    }
}

@Composable
private fun FocusTree(modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.offset(y = (-8).dp).size(width = 18.dp, height = 92.dp).clip(RoundedCornerShape(10.dp)).background(Color(red = 0.45f, green = 0.32f, blue = 0.22f)))
        Canopy(Color(red = 0.36f, green = 0.62f, blue = 0.28f), 150.dp, 78.dp, Modifier.offset(x = (-8).dp, y = (-70).dp))
        Canopy(Color(red = 0.48f, green = 0.74f, blue = 0.34f), 120.dp, 64.dp, Modifier.offset(x = 28.dp, y = (-96).dp))
        Canopy(Color(red = 0.30f, green = 0.55f, blue = 0.26f), 90.dp, 50.dp, Modifier.offset(x = (-36).dp, y = (-108).dp))
        Box(Modifier.offset(x = 18.dp, y = (-58).dp).size(width = 54.dp, height = 8.dp).clip(RoundedCornerShape(50)).background(Color(red = 0.40f, green = 0.28f, blue = 0.18f)))
    }
}

@Composable
private fun Canopy(color: Color, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp, modifier: Modifier) {
    Box(modifier.size(width, height)) {
        Canvas(Modifier.fillMaxSize()) {
            drawOval(color)
            drawOval(Color.White.copy(alpha = 0.18f), topLeft = Offset(size.width * 0.28f, 8f), size = Size(size.width * 0.45f, size.height * 0.28f))
        }
    }
}
