package com.ahmed.rotrun.ui

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ahmed.rotrun.logic.BoardSnapshot
import com.ahmed.rotrun.logic.RankedXp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.DateRange
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmed.rotrun.R
import com.ahmed.rotrun.data.RotRunRepository
import com.ahmed.rotrun.data.SavedState
import com.ahmed.rotrun.logic.InterventionCopy
import com.ahmed.rotrun.logic.Scoring
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun StatsScreen(
    state: SavedState,
    health: Int?,
    screenMinutes: Int?,
    repo: RotRunRepository,
    onLeaderboard: () -> Unit = {},
    onEditYou: () -> Unit = {},
) {
    var page by remember { mutableStateOf("focus") }
    Column(Modifier.padding(bottom = 24.dp)) {
        Segment(
            "Focus",
            "Leaderboard",
            page == "board",
            { page = "focus" },
            { page = "board" },
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        if (page == "focus") FocusStats(state, health) else Leaderboard(state, screenMinutes, repo, onLeaderboard, onEditYou)
    }
}

@Composable
private fun FocusStats(state: SavedState, health: Int?) {
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
        Row(Modifier.fillMaxWidth().height(92.dp).padding(top = 12.dp, start = 8.dp, end = 8.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            week.forEach { day ->
                val value = (state.focusMinutesByDay[Scoring.dayKey(day)] ?: 0).toFloat()
                Box(
                    Modifier.weight(1f).height((72f * value / max).dp.coerceAtLeast(12.dp)).clip(RoundedCornerShape(8.dp))
                        .background(if (day == selected) Color(0xFFFF8C2E) else chrome.line),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, start = 8.dp, end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            week.forEach { day ->
                Text(
                    day.dayOfWeek.name.take(3),
                    color = chrome.muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    val storyBg = if (chrome.dark) chrome.page else Color(0xFFFCE6C7)
    Column(Modifier.fillMaxWidth().background(storyBg).padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CartoonBrain(BrainStyle.Meadow, Modifier.padding(top = 8.dp).size(150.dp), animated = false)
        FocusStory(
            weekLabel = Scoring.formatDuration(weekMinutes * 60L),
            healthLabel = health?.let { "$it%" } ?: "—",
            yearLabel = Scoring.formatDuration(year * 60L),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(22.dp)).background(if (chrome.dark) chrome.inset else Color(0xFFF2F2F7)).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Mini("Longest", longest?.let { Scoring.formatDuration(it.value * 60L) } ?: "-", Modifier.weight(1f))
            Mini("Best day", longest?.key?.let { Scoring.parseDay(it)?.dayOfWeek?.name?.take(3) } ?: "-", Modifier.weight(1f))
            Mini("Lifetime", if (lifetime == 0) "-" else Scoring.formatDuration(lifetime * 60L), Modifier.weight(1f))
        }
    }
}

@Composable
private fun FocusStory(weekLabel: String, healthLabel: String, yearLabel: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        StoryLine {
            StoryText("You have spent")
            StoryChip(weekLabel) { Icon(Icons.Default.Alarm, contentDescription = null, tint = Color(0xFFFF3B30), modifier = Modifier.size(14.dp)) }
            StoryText("in")
        }
        StoryText("focus time this week,", fill = true)
        StoryLine {
            StoryText("regaining")
            StoryChip(healthLabel) { Image(painterResource(R.drawable.rot_mascot), contentDescription = "Rot", modifier = Modifier.size(16.dp)) }
        }
        StoryText("of your brain health.", fill = true)
        StoryText("With this average, you're on", fill = true)
        StoryLine {
            StoryText("track to spend")
            StoryChip(yearLabel) { Icon(Icons.Default.DateRange, contentDescription = null, tint = Color(0xFF5AC8FA), modifier = Modifier.size(14.dp)) }
        }
        StoryText("focused this year instead of rotting.", fill = true)
    }
}

@Composable
private fun StoryLine(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        content()
    }
}

@Composable
private fun StoryText(text: String, fill: Boolean = false) {
    Text(
        text,
        color = rot().ink,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        textAlign = TextAlign.Center,
        modifier = if (fill) Modifier.fillMaxWidth().padding(top = 2.dp) else Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun StoryChip(text: String, icon: @Composable () -> Unit) {
    Row(
        Modifier.padding(horizontal = 3.dp).clip(RoundedCornerShape(10.dp)).background(rot().card).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        icon()
        Text(text, color = rot().ink, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
    }
}

@Composable
private fun Leaderboard(
    state: SavedState,
    screenMinutes: Int?,
    repo: RotRunRepository,
    onOpen: () -> Unit,
    onEditYou: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 20.dp)) {
    LeaderboardBody(state, screenMinutes, repo, onOpen, onEditYou)
    }
}

@Composable
private fun LeaderboardBody(
    state: SavedState,
    screenMinutes: Int?,
    repo: RotRunRepository,
    onOpen: () -> Unit,
    onEditYou: () -> Unit,
) {
    var friends by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val xp = screenMinutes?.let { Scoring.dailyXp(it, state.goalHours * 60, state.askBrainCount, state.ransomDebit) }
    var board by remember { mutableStateOf(BoardSnapshot()) }
    var refreshing by remember { mutableStateOf(false) }
    val day = Scoring.dayKey()
    fun reload() {
        refreshing = true
        CoroutineScope(Dispatchers.IO).launch {
            val snapshot = repo.backend.loadDailyBoard(xp)
            withContext(Dispatchers.Main) {
                board = snapshot
                refreshing = false
            }
        }
    }
    LaunchedEffect(Unit) { onOpen() }
    LaunchedEffect(xp, repo.backend.signedIn, day, state.askBrainCount, state.ransomDebit) { reload() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reload() }
    val chrome = rot()
    val reduce = reduceMotion()
    val trophy = remember { androidx.compose.animation.core.Animatable(if (reduce) 1f else 0.72f) }
    LaunchedEffect(Unit) {
        if (!reduce) trophy.animateTo(1f, swiftSpring(0.45f, 0.35f))
    }
    Appear(0f) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Melt.Orange, modifier = Modifier.size(28.dp).graphicsLayer { scaleX = trophy.value; scaleY = trophy.value })
            Text(" Leaderboard", color = chrome.ink, fontFamily = Nunito, fontSize = 28.sp, fontWeight = FontWeight.Black)
        }
    }
    Appear(0.06f) { Text("See how your Daily XP stacks up.", color = chrome.muted, fontFamily = Nunito, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
    Appear(0.10f) { ScopeToggle(friends, reduce) { friends = it } }
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { repo.refreshUsage(); reload() }) {
        Column(Modifier.fillMaxWidth()) {
            when {
                friends -> Appear(0f) { FriendsSoon() }
                !repo.backend.signedIn || board.signedOut -> Appear(0f) { SignInBoard(context, repo) { reload() } }
                xp == null -> Appear(0.08f) { UsageBoard() }
                board.rows.isEmpty() && board.pinned == null -> Appear(0.12f) { EmptyBoard() }
                else -> {
                    if (board.rows.size >= 3) {
                        Appear(0.04f) { Podium(board.rows.take(3), onEditYou) }
                    }
                    val list = if (board.rows.size >= 3) board.rows.drop(3) else board.rows
                    list.forEachIndexed { index, row -> Appear(0.12f + minOf(index, 7) * 0.05f) { BoardRow(row, onEditYou) } }
                    board.pinned?.let { PinnedYou(it, onEditYou) }
                }
            }
            board.message?.let {
                Text(it, color = chrome.muted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun FriendsSoon() {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(rot().card).padding(16.dp)) {
        Text("Friends", color = rot().ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Friends coming soon. Daily XP is ranked across all RotRun users.", color = rot().muted, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun UsageBoard() {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(rot().card).padding(16.dp)) {
        Text("Daily XP", color = rot().ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Connect usage access to score Daily XP. A blank day is not a perfect score.", color = rot().muted, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun SignInBoard(context: android.content.Context, repo: RotRunRepository, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(rot().card).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Sign in to see the leaderboard", color = rot().ink, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center)
        TextButton(onClick = {
            val activity = context as? Activity ?: return@TextButton
            repo.backend.signIn(activity) { onDone() }
        }) { Text("Sign in with Google") }
    }
}

@Composable
private fun EmptyBoard() {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CartoonBrain(BrainStyle.Meadow, Modifier.size(96.dp), animated = false)
        Text("No rankings yet", color = rot().ink, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
        Text("Play a bit, then pull to refresh your Daily XP rank.", color = rot().muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp))
    }
}

@Composable
private fun Podium(top: List<RankedXp>, onEditYou: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceEvenly) {
        PodiumPlace(top[1], 88.dp, Color(0xFFB8C2D1), onEditYou)
        PodiumPlace(top[0], 118.dp, Color(0xFFFFC747), onEditYou)
        PodiumPlace(top[2], 72.dp, Color(0xFFE68C52), onEditYou)
    }
}

@Composable
private fun PodiumPlace(row: RankedXp, bar: androidx.compose.ui.unit.Dp, medal: Color, onEditYou: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(96.dp).clickable(enabled = row.you, onClick = onEditYou),
    ) {
        PlayerAvatar(row.name, row.avatarUrl, 48.dp)
        Text(row.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = rot().ink)
        Text("${row.xp} XP", fontSize = 11.sp, color = rot().muted)
        Text("#${row.rank}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = rot().ink)
        Box(Modifier.padding(top = 4.dp).width(64.dp).height(bar).clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)).background(medal))
    }
}

@Composable
private fun BoardRow(row: RankedXp, onEditYou: () -> Unit) {
    val chrome = rot()
    val rankColor = when (row.rank) {
        1 -> Color(0xFFFFC747)
        2 -> chrome.muted
        else -> Color(0xFFC7C7CC)
    }
    Row(
        Modifier.padding(vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(chrome.card).border(if (row.you) 2.dp else 0.dp, if (row.you) Color(0xFFFF8C2E) else Color.Transparent, RoundedCornerShape(22.dp)).clickable(enabled = row.you, onClick = onEditYou).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("#${row.rank}", fontWeight = FontWeight.Black, color = rankColor, modifier = Modifier.width(36.dp))
        PlayerAvatar(row.name, row.avatarUrl, 36.dp)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(row.name, color = chrome.ink, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelBrain(Scoring.rank(row.xp).title, Modifier.size(18.dp))
                Text(Scoring.rank(row.xp).title, color = chrome.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
            }
        }
        ScoreCapsule(row.xp, row.you)
    }
}

@Composable
private fun PinnedYou(row: RankedXp, onEditYou: () -> Unit) {
    Row(
        Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0xFFFF8C2E)).clickable(onClick = onEditYou).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("#${row.rank}", color = Color.White, fontWeight = FontWeight.Black)
        Box(Modifier.padding(start = 10.dp)) { PlayerAvatar(row.name, row.avatarUrl, 36.dp) }
        Text(row.name, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${row.xp} XP", color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InitialAvatar(name: String) {
    val letter = name.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "R"
    Box(Modifier.size(36.dp).clip(CircleShape).background(rot().inset), contentAlignment = Alignment.Center) {
        Text(letter, fontWeight = FontWeight.Bold, color = rot().ink)
    }
}

@Composable
private fun ScoreCapsule(score: Int, you: Boolean) {
    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if (you) Color(0xFFFF8C2E) else rot().inset).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text("$score", color = if (you) Color.White else rot().muted, fontWeight = FontWeight.Bold, fontSize = if (score > 999) 11.sp else 13.sp, maxLines = 1)
    }
}

@Composable
private fun ScopeToggle(friends: Boolean, reduce: Boolean, onPick: (Boolean) -> Unit) {
    Segment(
        "Daily XP",
        "Friends",
        friends,
        { onPick(false) },
        { onPick(true) },
        Modifier.padding(vertical = 8.dp),
        reduce,
    )
}

@Composable
private fun Segment(
    left: String,
    right: String,
    rightOn: Boolean,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    modifier: Modifier = Modifier,
    reduce: Boolean = reduceMotion(),
) {
    val slide by androidx.compose.animation.core.animateFloatAsState(
        if (rightOn) 1f else 0f,
        if (reduce) androidx.compose.animation.core.tween(120, easing = androidx.compose.animation.core.EaseOut) else swiftSpring(0.32f, 0f),
        label = "segment",
    )
    BoxWithConstraints(modifier.fillMaxWidth().clip(CircleShape).background(rot().inset).padding(4.dp)) {
        Box(
            Modifier.fillMaxWidth(0.5f).offset(x = maxWidth * 0.5f * slide).height(36.dp).clip(CircleShape).background(rot().blue),
        )
        Row(Modifier.fillMaxWidth()) {
            Toggle(left, !rightOn, Modifier.weight(1f), onLeft)
            Toggle(right, rightOn, Modifier.weight(1f), onRight)
        }
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, modifier: Modifier = Modifier, click: () -> Unit) {
    Text(
        label,
        color = if (on) Color.White else Melt.Muted,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = modifier.clickable(onClick = click).padding(vertical = 9.dp),
    )
}

@Composable
private fun Mini(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(rot().card).padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = rot().ink, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center, maxLines = 1)
        Text(title, color = rot().muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp), textAlign = TextAlign.Center)
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
