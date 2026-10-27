package com.ahmed.rotrun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.ahmed.rotrun.data.RotRunRepository
import com.ahmed.rotrun.data.SavedState
import com.ahmed.rotrun.logic.InterventionCopy
import com.ahmed.rotrun.logic.Scoring
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun StatsScreen(state: SavedState, health: Int, screenMinutes: Int, repo: RotRunRepository) {
    var page by remember { mutableStateOf("focus") }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Toggle("Focus Time", page == "focus") { page = "focus" }
            Toggle("Leaderboard", page == "board") { page = "board" }
        }
        if (page == "focus") FocusStats(state, health) else Leaderboard(state, screenMinutes, repo)
    }
}

@Composable
private fun FocusStats(state: SavedState, health: Int) {
    val week = Scoring.thursdayWeek()
    var selected by remember { mutableStateOf(LocalDate.now()) }
    val minutes = state.focusMinutesByDay[Scoring.dayKey(selected)] ?: 0
    val weekMinutes = week.sumOf { state.focusMinutesByDay[Scoring.dayKey(it)] ?: 0 }
    val longest = state.focusMinutesByDay.maxByOrNull { it.value }
    val lifetime = state.focusMinutesByDay.values.sum()
    val year = if (weekMinutes == 0) 0 else (weekMinutes / 7.0 * 365).toInt()
    Text(Scoring.formatDuration(minutes * 60L), fontSize = 48.sp, fontWeight = FontWeight.Black, color = Melt.Accent)
    Text(
        if (selected == LocalDate.now()) "Today, ${selected.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))}" else selected.format(DateTimeFormatter.ofPattern("MMM d", Locale.US)),
        color = Melt.Muted,
    )
    Row(Modifier.fillMaxWidth().height(90.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        week.forEach { day ->
            val value = (state.focusMinutesByDay[Scoring.dayKey(day)] ?: 0).toFloat()
            val max = (state.focusMinutesByDay.values.maxOrNull() ?: 1).coerceAtLeast(1)
            Column(Modifier.weight(1f).clickable { selected = day }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height((60f * value / max).dp.coerceAtLeast(4.dp)).clip(RoundedCornerShape(4.dp)).background(if (day == selected) Melt.Orange else Color.White))
                Text(day.dayOfWeek.name.take(1), fontSize = 11.sp)
            }
        }
    }
    Text("You have spent ${Scoring.formatDuration(weekMinutes * 60L)} in", modifier = Modifier.padding(top = 8.dp))
    Text("focus time this week,", fontWeight = FontWeight.Bold)
    Text("regaining $health% of your brain health.")
    Text("With this average, you're on track to spend ${Scoring.formatDuration(year * 60L)} focused this year instead of rotting.")
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Mini("Longest", longest?.let { Scoring.formatDuration(it.value * 60L) } ?: "-")
        Mini("Best day", longest?.key?.let { Scoring.parseDay(it)?.dayOfWeek?.name?.take(3) } ?: "-")
        Mini("Lifetime", if (lifetime == 0) "-" else Scoring.formatDuration(lifetime * 60L))
    }
}

@Composable
private fun Leaderboard(state: SavedState, screenMinutes: Int, repo: RotRunRepository) {
    var friends by remember { mutableStateOf(false) }
    var receipt by remember { mutableStateOf(false) }
    var ransom by remember { mutableStateOf(false) }
    val xp = Scoring.dailyXp(screenMinutes, state.goalHours * 60, state.askBrainCount, state.ransomDebit)
    val rank = Scoring.rank(xp)
    Text("Leaderboard", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Text("See how your Daily XP stacks up.", color = Melt.Muted)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        Toggle("Daily XP", !friends) { friends = false }
        Toggle("Friends", friends) { friends = true }
    }
    if (!state.playGamesConnected) {
        Text("Sign in to Google Play Games", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text("Connect Play Games so RotRun can rank your Daily XP with friends and the world. Game Center isn’t available on Android, so this stays on your device.", color = Melt.Muted)
        PrimaryButton("Connect Play Games") { repo.connectPlayGames() }
        return
    }
    if (state.duelName.isNotBlank() && state.duelUntil > System.currentTimeMillis()) {
        val left = ((state.duelUntil - System.currentTimeMillis()) / 60000).toInt()
        val label = if (left >= 60) "${left / 60}h ${left % 60}m" else "${left}m"
        Text("Duel · Until midnight · $label", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
        Text("You ${xp} XP    ${state.duelName} ${state.duelScore} XP")
    }
    if (friends) {
        Text("No friends on the board yet", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        Text("Refresh after a friend joins RotRun on this device’s local board.", color = Melt.Muted)
    } else {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(14.dp)) {
            Text("1", fontWeight = FontWeight.Black, color = Melt.Orange)
            Text("You", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("$xp XP · ${rank.title}", color = Melt.Muted)
            Text(rank.flavor, modifier = Modifier.padding(top = 4.dp))
        }
        Text("Generate Receipt", color = Melt.Action, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp).clickable { receipt = true })
        Text("Pay Ransom", color = Melt.Action, modifier = Modifier.padding(top = 8.dp).clickable { ransom = true })
        Text("Audio Slap", color = Melt.Muted, modifier = Modifier.padding(top = 8.dp))
        Text("Challenge to Duel", color = Melt.Muted)
        Text("No friends on the board yet — slap and duel need a friend.", color = Melt.Muted, fontSize = 13.sp)
    }
    if (receipt) {
        Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF1C1C1E)).padding(16.dp)) {
            Text("OFFICIAL ROT RECEIPT", color = Melt.Orange, fontWeight = FontWeight.Black)
            Text("CERTIFIED ${rank.badge}", color = Color.White, fontWeight = FontWeight.Bold)
            Text("You", color = Color.White)
            Text("$xp XP", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text(rank.receipt, color = Color.White)
            Text("Public Daily XP · on this device\nNot Screen Time · Expires midnight", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            Text("Close", color = Melt.Orange, modifier = Modifier.padding(top = 8.dp).clickable { receipt = false })
        }
    }
    if (ransom) {
        Column(Modifier.padding(top = 12.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(14.dp)) {
            Text("Pay the ransom", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("Strike a silly hero pose and hold this button for 8 seconds. No sit-ups. No camera. Then a friend gets a thank-you — not a workout.")
            Text("Minus 100 of my Daily XP", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            Text("Floors at 0. You have $xp XP today.", color = Melt.Muted)
            PrimaryButton("Hold to be a hero") { repo.payRansom(); ransom = false }
            Text("Close", modifier = Modifier.clickable { ransom = false })
        }
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, click: () -> Unit) {
    Text(
        label,
        color = if (on) Color.White else Melt.Ink,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) Melt.Orange else Color.White).clickable(onClick = click).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun Mini(title: String, value: String) {
    Column(Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).padding(10.dp)) {
        Text(title, color = Melt.Muted, fontSize = 12.sp)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

private val Melt.Accent get() = Color(0xFFFF8C2E)

@Composable
fun AudioSlap(name: String, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF102218)).padding(20.dp)) {
        Text("Nature notes", color = Color.White, fontWeight = FontWeight.Bold)
        Text("A brief documentary from $name. One take. No siren.", color = Color.White.copy(alpha = 0.8f))
        Text(InterventionCopy.natureScript(name), color = Color.White, modifier = Modifier.padding(vertical = 12.dp))
        Text("OK cool", color = Melt.Orange, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onClose))
    }
}
