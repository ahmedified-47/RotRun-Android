package com.ahmed.rotrun.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmed.rotrun.data.SavedState
import com.ahmed.rotrun.data.UsageSnapshot
import com.ahmed.rotrun.logic.BrainHealthLevel
import com.ahmed.rotrun.logic.Scoring
import java.time.LocalTime

@Composable
fun HomeScreen(state: SavedState, usage: UsageSnapshot, refreshing: Boolean, onRefresh: () -> Unit, onTalk: () -> Unit, onConnect: () -> Unit) {
    val look = Looks.byId(state.theme)
    val scenic = sceneFor(state.theme) != null
    val level = BrainHealthLevel.fromLabel(state.brainHealth)
    val scored = usage.authorized && usage.drainSeconds >= 60
    val health = Scoring.displayedHealth(Scoring.drainHealth(usage.drainSeconds, level), state.focusMinutesToday(), level.restoreMinutes)
    val style = when (health) {
        in 65..100 -> look.brain
        in 40..64 -> BrainStyle.Sad
        else -> BrainStyle.Pleading
    }
    val on = if (scenic) Color.White else Melt.Ink
    val brainSize = 136.dp
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(Modifier.fillMaxWidth().height(brainSize + 78.dp)) {
            if (scenic) HeroBackdrop(state.theme, Modifier.fillMaxWidth().fillMaxHeight())
            Column(Modifier.fillMaxWidth()) {
                HomeHeader(state.streak.coerceAtLeast(1), refreshing, scenic, on, onRefresh, onTalk)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    CartoonBrain(style, Modifier.size(brainSize), animated = false)
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                "BRAIN HEALTH",
                color = if (scenic) Color.White.copy(alpha = 0.82f) else Melt.Muted,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.3.sp,
            )
            Text(
                if (scored) "$health%" else "Not in yet",
                color = if (scenic) Color.Black else Melt.Ink,
                fontWeight = FontWeight.Black,
                fontSize = if (scored) 60.sp else 36.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (scored) Scoring.caption(health) else "Waiting on a real reading",
                color = if (scenic) Color.White.copy(alpha = 0.9f) else Melt.Muted,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
            Box(
                Modifier.padding(horizontal = 44.dp, vertical = 8.dp).fillMaxWidth().height(12.dp).clip(CircleShape)
                    .background(if (scenic) Color.Black.copy(alpha = 0.22f) else Color(0xFFE5E5EA)),
            ) {
                Box(
                    Modifier.fillMaxWidth((health / 100f).coerceIn(0.04f, 1f)).fillMaxHeight()
                        .clip(CircleShape)
                        .background(if (scenic) Color.White else Melt.Action),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Screen Time", Scoring.visibleDuration(usage.todaySeconds), MetricKind.Screen, Modifier.weight(1f))
            MetricCard("Pickups", Scoring.visibleCount(usage.pickups), MetricKind.Pickups, Modifier.weight(1f))
            MetricCard("Focus Time", Scoring.visibleDuration(state.focusMinutesToday() * 60L), MetricKind.Focus, Modifier.weight(1f))
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Top Offenders", scenic)
            RaisedCard {
                val offenders = usage.offenders.filter { it.seconds >= 60 }
                if (offenders.isEmpty()) {
                    Text("Not in yet", color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Medium, modifier = Modifier.padding(16.dp))
                } else {
                    offenders.forEachIndexed { index, app ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppDot(app.label)
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(app.label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 1)
                                Text("App", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                            }
                            Text(Scoring.formatDuration(app.seconds), color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                        if (index < offenders.lastIndex) {
                            Box(Modifier.padding(start = 64.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
                        }
                    }
                }
            }
            SectionTitle("Highlights", scenic)
            RaisedCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(Scoring.paceLine(usage.aheadOfPace), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Legend(Melt.Orange, "Today", Scoring.visibleDuration(usage.todaySeconds), Melt.Orange)
                        Spacer(Modifier.weight(1f))
                        Legend(Color.White, "Average", Scoring.visibleDuration(usage.avgSeconds), Color.White)
                    }
                    TrendChart(usage.hourlyToday, usage.hourlyAvg, Modifier.fillMaxWidth().height(120.dp))
                }
            }
            RaisedCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(Scoring.comparisonLine(usage.todaySeconds, usage.avgSeconds, usage.diffPercent, usage.belowAverage), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    CompareBar(Scoring.visibleDuration(usage.todaySeconds), "Today", usage.todaySeconds, usage.avgSeconds, Melt.Orange)
                    CompareBar(Scoring.visibleDuration(usage.avgSeconds), "7-day avg", usage.avgSeconds, usage.avgSeconds, Color.White.copy(alpha = 0.38f))
                }
            }
            RaisedCard {
                Column(Modifier.padding(18.dp)) {
                    Text(Scoring.weeklyLine(usage.avgSeconds), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Box(Modifier.padding(vertical = 12.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.15f)))
                    WeekBars(usage, Modifier.fillMaxWidth().height(148.dp))
                }
            }
        }
        if (!usage.authorized) {
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clickable(onClick = onConnect),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RaisedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                            Text("⌛", color = Color.White)
                        }
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text("Connect Screen Time", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Load today’s real usage", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                        }
                        Text("›", color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(streak: Int, refreshing: Boolean, scenic: Boolean, on: Color, onRefresh: () -> Unit, onTalk: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("ROTRUN", color = on, fontWeight = FontWeight.Black, fontSize = 30.sp, modifier = Modifier.align(Alignment.Center))
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            Text("Talk", color = on, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp).clickable(onClick = onTalk))
            Icon(
                Icons.Default.Refresh,
                contentDescription = "Refresh",
                tint = on.copy(alpha = if (refreshing) 0.45f else 1f),
                modifier = Modifier.size(22.dp).clickable(onClick = onRefresh),
            )
        }
        Column(Modifier.align(Alignment.CenterEnd), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(36.dp)) {
                Box(Modifier.size(36.dp).offset(y = 3.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.28f)))
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(Color(0xFFFFC740), Color(0xFFFF6B1F)))),
                    contentAlignment = Alignment.Center,
                ) { Text("🔥", fontSize = 16.sp) }
            }
            Text("$streak", color = on, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        }
    }
}

private enum class MetricKind { Screen, Pickups, Focus }

@Composable
private fun MetricCard(title: String, value: String, kind: MetricKind, modifier: Modifier) {
    RaisedCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(48.dp)) {
                Box(Modifier.size(48.dp).offset(y = 4.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.28f)))
                Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    when (kind) {
                        MetricKind.Screen -> Text("☠", fontSize = 20.sp)
                        MetricKind.Pickups -> Box(Modifier.size(width = 16.dp, height = 26.dp).clip(RoundedCornerShape(5.dp)).background(Brush.verticalGradient(listOf(Color(0xFF8CD1FF), Color(0xFF408CF2)))))
                        MetricKind.Focus -> Text("🔒", fontSize = 18.sp)
                    }
                }
            }
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp, maxLines = 1)
            Text(title, color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
        }
    }
}

@Composable
private fun SectionTitle(title: String, scenic: Boolean) {
    Text(title, color = if (scenic) Color.White else Melt.Ink, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(start = 4.dp))
}

@Composable
fun RaisedCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.padding(bottom = 8.dp)) {
        Box(
            Modifier.matchParentSize().offset(y = 7.dp).clip(RoundedCornerShape(24.dp)).background(Melt.Lip),
        )
        Box(
            Modifier.clip(RoundedCornerShape(24.dp)).background(Brush.verticalGradient(listOf(Melt.CardTop, Melt.CardBottom))),
        ) { content() }
    }
}

@Composable
private fun AppDot(label: String) {
    val tint = when {
        label.contains("instagram", true) -> listOf(Color(0xFFFABF40), Color(0xFFE64073), Color(0xFF7338D9))
        label.contains("whatsapp", true) -> listOf(Color(0xFF2EBF61), Color(0xFF1A8C47))
        label.contains("map", true) -> listOf(Color(0xFF40B873), Color(0xFF3373E6))
        else -> listOf(Color(0xFF8E8E93), Color(0xFF636366))
    }
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(tint)))
}

@Composable
private fun Legend(dot: Color, label: String, value: String, valueColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Column(Modifier.padding(start = 8.dp)) {
            Text(label, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
            Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }
    }
}

@Composable
private fun CompareBar(value: String, label: String, amount: Long, maxAmount: Long, tint: Color) {
    val fraction = if (maxAmount <= 0L) 0.08f else (amount.toFloat() / maxAmount.toFloat()).coerceIn(0.06f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, modifier = Modifier.padding(start = 8.dp, bottom = 3.dp))
        }
        Box(Modifier.fillMaxWidth(fraction).height(10.dp).clip(CircleShape).background(tint))
    }
}

@Composable
private fun TrendChart(today: List<Float>, average: List<Float>, modifier: Modifier) {
    val hour = LocalTime.now().hour
    Canvas(modifier) {
        val width = size.width
        val height = (size.height - 16f).coerceAtLeast(1f)
        val peak = maxOf(average.maxOrNull() ?: 1f, today.take(hour + 1).maxOrNull() ?: 1f, 1f)
        fun pathOf(values: List<Float>, limit: Int): Path = Path().apply {
            values.take(limit).forEachIndexed { index, value ->
                val x = width * index / 23f
                val y = height - height * (value / peak)
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        drawPath(pathOf(average, average.size), Color.White.copy(alpha = 0.5f), style = Stroke(3f, cap = StrokeCap.Round))
        val todayPath = pathOf(today, hour + 1)
        drawPath(todayPath, Melt.Orange, style = Stroke(3.5f, cap = StrokeCap.Round))
        if (today.isNotEmpty()) {
            val last = hour.coerceIn(0, today.lastIndex)
            val x = width * last / 23f
            val y = height - height * (today[last] / peak)
            drawCircle(Melt.Orange, 4f, Offset(x, y))
        }
    }
    Row(Modifier.fillMaxWidth().offset(y = (-16).dp).padding(horizontal = 2.dp)) {
        Text("12 AM", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
        Spacer(Modifier.weight(1f))
        Text("11 PM", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
    }
}

@Composable
private fun WeekBars(usage: UsageSnapshot, modifier: Modifier) {
    val max = (usage.week.maxOfOrNull { it.seconds } ?: 1L).coerceAtLeast(usage.avgSeconds).coerceAtLeast(1L)
    Box(modifier) {
        Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            usage.week.forEach { day ->
                val fraction = (day.seconds.toFloat() / max.toFloat()).coerceIn(0f, 1f)
                Box(
                    Modifier.weight(1f).height((148f * fraction).dp.coerceAtLeast(8.dp)).clip(CircleShape).background(Color.White.copy(alpha = 0.42f)),
                )
            }
        }
        val avgFraction = (usage.avgSeconds.toFloat() / max.toFloat()).coerceIn(0f, 1f)
        Box(
            Modifier.align(Alignment.BottomStart).padding(bottom = (148f * avgFraction).dp).fillMaxWidth().height(3.dp).clip(CircleShape).background(Melt.Orange),
        )
    }
}
