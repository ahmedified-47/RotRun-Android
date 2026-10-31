package com.ahmed.rotrun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmed.rotrun.BuildConfig
import com.ahmed.rotrun.data.RotRunRepository
import com.ahmed.rotrun.data.SavedState
import com.ahmed.rotrun.logic.InterventionCopy
import com.ahmed.rotrun.logic.Scoring
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun StatsScreen(state: SavedState, health: Int, screenMinutes: Int, repo: RotRunRepository) {
    var page by remember { mutableStateOf("focus") }
    Column(Modifier.padding(bottom = 24.dp)) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().clip(CircleShape).background(rot().inset).padding(4.dp),
        ) {
            Toggle("Focus", page == "focus", Modifier.weight(1f)) { page = "focus" }
            Toggle("Leaderboard", page == "board", Modifier.weight(1f)) { page = "board" }
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
    val max = (state.focusMinutesByDay.values.maxOrNull() ?: 1).coerceAtLeast(1)
    val chrome = rot()
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            week.forEach { day ->
                val on = day == selected
                Column(Modifier.weight(1f).clickable { selected = day }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(day.dayOfWeek.name.take(3), color = chrome.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Box(
                        Modifier.padding(top = 8.dp).size(36.dp).clip(CircleShape).background(if (on) Color(0xFFFF8C2E) else chrome.inset),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(day.dayOfMonth.toString(), color = if (on) Color.White else chrome.ink, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        Text(Scoring.formatDuration(minutes * 60L), color = chrome.ink, fontSize = 44.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp).fillMaxWidth(), textAlign = TextAlign.Center)
        Text(
            if (selected == LocalDate.now()) "Today, ${selected.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))}" else selected.format(DateTimeFormatter.ofPattern("MMM d", Locale.US)),
            color = chrome.muted,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Row(Modifier.fillMaxWidth().height(92.dp).padding(top = 12.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            week.forEach { day ->
                val value = (state.focusMinutesByDay[Scoring.dayKey(day)] ?: 0).toFloat()
                Box(Modifier.weight(1f).height((72f * value / max).dp.coerceAtLeast(8.dp)).clip(RoundedCornerShape(8.dp)).background(if (day == selected) Color(0xFFFF8C2E) else chrome.inset))
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CartoonBrain(BrainStyle.Meadow, Modifier.padding(top = 8.dp).size(140.dp), animated = false)
        Text(
            "You have spent ${Scoring.formatDuration(weekMinutes * 60L)} in focus time this week, regaining $health% of your brain health. With this average, you're on track to spend ${Scoring.formatDuration(year * 60L)} focused this year instead of rotting.",
            color = chrome.ink,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Mini("Longest", longest?.let { Scoring.formatDuration(it.value * 60L) } ?: "-")
            Mini("Best day", longest?.key?.let { Scoring.parseDay(it)?.dayOfWeek?.name?.take(3) } ?: "-")
            Mini("Lifetime", if (lifetime == 0) "-" else Scoring.formatDuration(lifetime * 60L))
        }
    }
}

@Composable
private fun Leaderboard(state: SavedState, screenMinutes: Int, repo: RotRunRepository) {
    Column(Modifier.padding(horizontal = 20.dp)) {
    LeaderboardBody(state, screenMinutes, repo)
    }
}

@Composable
private fun LeaderboardBody(state: SavedState, screenMinutes: Int, repo: RotRunRepository) {
    var friends by remember { mutableStateOf(false) }
    val xp = Scoring.dailyXp(screenMinutes, state.goalHours * 60, state.askBrainCount, state.ransomDebit)
    var rows by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    LaunchedEffect(xp, repo.backend.signedIn) {
        rows = withContext(Dispatchers.IO) {
            repo.backend.syncXp(xp, "You")
            repo.backend.leaderboard()
        }
    }
    val board = dailyBoard(rows, xp)
    val chrome = rot()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Melt.Orange, modifier = Modifier.size(28.dp))
        Text(" Leaderboard", color = chrome.ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
    Text("See how your Daily XP stacks up.", color = chrome.muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    Row(Modifier.padding(vertical = 8.dp).fillMaxWidth().clip(CircleShape).background(chrome.inset).padding(4.dp)) {
        Toggle("Daily XP", !friends, Modifier.weight(1f)) { friends = false }
        Toggle("Friends", friends, Modifier.weight(1f)) { friends = true }
    }
    if (friends) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(chrome.card).padding(16.dp)) {
            Text("Friends", color = chrome.ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                if (BuildConfig.PLAY_GAMES_FRIENDS) "Play Games friends show up on this tab."
                else "Friends is off in this build. Daily XP stays on the RotRun board.",
                color = chrome.muted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    } else {
        board.forEachIndexed { index, (name, score) ->
            val title = Scoring.rank(score).title
            val you = name.equals("You", ignoreCase = true)
            Row(
                Modifier.padding(vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(chrome.card).border(if (you) 2.dp else 0.dp, if (you) Melt.Orange else Color.Transparent, RoundedCornerShape(22.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("#${index + 1}", fontWeight = FontWeight.Black, color = Melt.Orange)
                CartoonBrain(BrainStyle.Meadow, Modifier.padding(horizontal = 10.dp).size(40.dp), animated = false)
                Column(Modifier.weight(1f)) {
                    Text(name, color = chrome.ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(
                        title,
                        color = if (you) Color.White else chrome.muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(8.dp)).background(if (you) chrome.blue else Color.Transparent).padding(horizontal = if (you) 8.dp else 0.dp, vertical = if (you) 2.dp else 0.dp),
                    )
                }
                if (you) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Melt.Orange), contentAlignment = Alignment.Center) {
                        Text("$score", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                } else {
                    Text("$score XP", color = chrome.muted, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun dailyBoard(rows: List<Pair<String, Int>>, xp: Int): List<Pair<String, Int>> {
    val source = if (rows.isEmpty()) listOf("You" to xp) else rows
    val seen = mutableSetOf<String>()
    val board = mutableListOf<Pair<String, Int>>()
    source.forEach { (name, score) ->
        val label = name.ifBlank { "Someone" }
        val key = label.trim().lowercase()
        if (!seen.add(key)) return@forEach
        board.add(label to score)
    }
    if ("you" !in seen) board.add("You" to xp)
    return board
}

@Composable
private fun Toggle(label: String, on: Boolean, modifier: Modifier = Modifier, click: () -> Unit) {
    Text(
        label,
        color = if (on) Color.White else Melt.Muted,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = modifier.clip(CircleShape).background(if (on) rot().blue else Color.Transparent).clickable(onClick = click).padding(vertical = 9.dp),
    )
}

@Composable
private fun Mini(title: String, value: String) {
    Column(Modifier.clip(RoundedCornerShape(14.dp)).background(rot().card).padding(10.dp)) {
        Text(title, color = rot().muted, fontSize = 12.sp)
        Text(value, color = rot().ink, fontWeight = FontWeight.Bold)
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
