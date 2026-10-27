package com.ahmed.rotrun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmed.rotrun.data.SavedState
import com.ahmed.rotrun.data.UsageSnapshot
import com.ahmed.rotrun.logic.BrainHealthLevel
import com.ahmed.rotrun.logic.Scoring

@Composable
fun HomeScreen(state: SavedState, usage: UsageSnapshot, refreshing: Boolean, onRefresh: () -> Unit, onConnect: () -> Unit) {
    val look = Looks.byId(state.theme)
    val level = BrainHealthLevel.fromLabel(state.brainHealth)
    val health = Scoring.displayedHealth(Scoring.drainHealth(usage.drainSeconds, level), state.focusMinutesToday(), level.restoreMinutes)
    val style = when (health) {
        in 65..100 -> look.brain
        in 40..64 -> BrainStyle.Sad
        else -> BrainStyle.Pleading
    }
    val on = look.onCanvas
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("ROTRUN", color = on, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh", tint = on) }
            Text("🔥 ${state.streak.coerceAtLeast(1)}", color = on, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CartoonBrain(style, Modifier.size(150.dp))
        }
        Text("BRAIN HEALTH", color = on.copy(alpha = 0.8f), fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.2.sp, modifier = Modifier.alignCenterText())
        Text("$health%", color = on, fontWeight = FontWeight.Black, fontSize = 60.sp, modifier = Modifier.alignCenterText())
        Text(Scoring.caption(health), color = on.copy(alpha = 0.9f), fontWeight = FontWeight.SemiBold, modifier = Modifier.alignCenterText())
        Box(Modifier.padding(horizontal = 44.dp, vertical = 10.dp).fillMaxWidth().height(12.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.22f))) {
            Box(Modifier.fillMaxWidth(health / 100f).height(12.dp).clip(CircleShape).background(if (look.light) Melt.Action else Color.White))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric("Screen Time", Scoring.formatDuration(usage.todaySeconds), Modifier.weight(1f), on)
            Metric("Pickups", usage.pickups.toString(), Modifier.weight(1f), on)
            Metric("Focus Time", Scoring.formatDuration(state.focusMinutesToday() * 60L), Modifier.weight(1f), on)
        }
        SectionCard("Top Offenders", on) {
            if (usage.offenders.isEmpty()) Text("No app usage yet today", color = on.copy(alpha = 0.85f))
            usage.offenders.forEach { app ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(app.label, color = on, modifier = Modifier.weight(1f))
                    Text(Scoring.formatDuration(app.seconds), color = on, fontWeight = FontWeight.Bold)
                }
            }
        }
        SectionCard("Highlights", on) {
            Text(Scoring.paceLine(usage.aheadOfPace), color = on)
            HourChart(usage.hourlyToday, usage.hourlyAvg, on)
        }
        SectionCard("Comparison", on) {
            Text(Scoring.comparisonLine(usage.todaySeconds, usage.avgSeconds, usage.diffPercent, usage.belowAverage), color = on)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("Today", Scoring.formatDuration(usage.todaySeconds), Modifier.weight(1f), on)
                Metric("7-day avg", Scoring.formatDuration(usage.avgSeconds), Modifier.weight(1f), on)
            }
        }
        SectionCard("This week", on) {
            Text(Scoring.weeklyLine(usage.avgSeconds), color = on)
            WeekChart(usage.week.map { it.label to (it.seconds / 60f) }, on)
        }
        if (!usage.authorized) {
            Row(
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.18f))
                    .clickable(onClick = onConnect)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Connect Screen Time", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Load today’s real usage", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }
                Text(
                    if (refreshing) "…" else "Connect",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(CircleShape).background(Melt.Action).padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier, on: Color) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(Brush.verticalGradient(listOf(Melt.CardTop, Melt.CardBottom))).padding(10.dp),
    ) {
        Text(label, color = on.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(value, color = on, fontWeight = FontWeight.Black, fontSize = 16.sp)
    }
}

@Composable
private fun SectionCard(title: String, on: Color, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.Black.copy(alpha = 0.12f)).padding(16.dp)) {
        Text(title, color = on, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun HourChart(today: List<Float>, average: List<Float>, on: Color) {
    val max = (today + average).maxOrNull()?.coerceAtLeast(1f) ?: 1f
    Row(Modifier.fillMaxWidth().height(72.dp).padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
        today.forEachIndexed { index, value ->
            if (index % 3 == 0) {
                Box(Modifier.weight(1f).padding(horizontal = 1.dp).height((56f * (value / max)).dp).clip(RoundedCornerShape(3.dp)).background(on))
            }
        }
    }
    Text("12 AM          12 PM          11 PM", color = on.copy(alpha = 0.7f), fontSize = 10.sp)
}

@Composable
private fun WeekChart(days: List<Pair<String, Float>>, on: Color) {
    val max = days.maxOfOrNull { it.second }?.coerceAtLeast(1f) ?: 1f
    Row(Modifier.fillMaxWidth().height(80.dp).padding(top = 8.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEach { (label, minutes) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height((52f * (minutes / max)).dp).clip(RoundedCornerShape(4.dp)).background(on))
                Text(label, color = on, fontSize = 11.sp)
            }
        }
    }
}

private fun Modifier.alignCenterText(): Modifier = this.then(Modifier.fillMaxWidth()).padding(horizontal = 16.dp)
