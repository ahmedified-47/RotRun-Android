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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ahmed.rotrun.logic.BoardSnapshot
import com.ahmed.rotrun.logic.DailyBoard
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
import com.ahmed.rotrun.logic.BrainHealthLevel
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
    onEditYou: () -> Unit = {},
) {
    var page by remember { mutableStateOf("focus") }
    Column(Modifier.background(if (page == "board") Color.Black else Color.Transparent).padding(bottom = 24.dp)) {
        Segment(
            "Focus",
            "Leaderboard",
            page == "board",
            { page = "focus" },
            { page = "board" },
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        if (page == "focus") FocusStats(state, health) else Leaderboard(state, screenMinutes, repo, onEditYou)
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

private enum class BoardScope { Daily, Friends }

@Composable
private fun Leaderboard(
    state: SavedState,
    screenMinutes: Int?,
    repo: RotRunRepository,
    onEditYou: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        LeaderboardBody(state, screenMinutes, repo, onEditYou)
    }
}

@Composable
private fun LeaderboardBody(
    state: SavedState,
    screenMinutes: Int?,
    repo: RotRunRepository,
    onEditYou: () -> Unit,
) {
    val context = LocalContext.current
    val level = BrainHealthLevel.fromLabel(state.brainHealth)
    val xp = screenMinutes?.let { Scoring.dailyXp(it, level.goalMinutes, state.askBrainCount, state.ransomDebit, level.xpMultiplier) }
    var board by remember { mutableStateOf(BoardSnapshot()) }
    var refreshing by remember { mutableStateOf(false) }
    var scope by remember { mutableStateOf(BoardScope.Daily) }
    val day = Scoring.dayKey()
    var graph by remember { mutableStateOf<com.ahmed.rotrun.logic.FriendGraph?>(null) }
    var friendNote by remember { mutableStateOf<String?>(null) }
    var inviteDraft by remember { mutableStateOf("") }
    var friendTarget by remember { mutableStateOf<RankedXp?>(null) }
    fun reloadFriends() {
        CoroutineScope(Dispatchers.IO).launch {
            val yours = repo.shownDailyXp()
            val loaded = repo.backend.loadFriendGraph()
            if (loaded != null) repo.backend.rememberFriendCameo(loaded, yours)
            withContext(Dispatchers.Main) {
                if (loaded != null) {
                    graph = loaded
                } else if (friendNote.isNullOrBlank()) {
                    friendNote = "Friends aren't available yet."
                }
            }
        }
    }
    fun reload() {
        refreshing = true
        CoroutineScope(Dispatchers.IO).launch {
            repo.syncDailyXp()
            val snapshot = repo.backend.loadDailyBoard(xp)
            withContext(Dispatchers.Main) {
                board = snapshot
                refreshing = false
            }
        }
    }
    LaunchedEffect(xp, repo.backend.signedIn, day, state.askBrainCount, state.ransomDebit) {
        reload()
        reloadFriends()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        reload()
        reloadFriends()
    }
    val reduce = reduceMotion()
    val trophy = remember { androidx.compose.animation.core.Animatable(if (reduce) 1f else 0.72f) }
    LaunchedEffect(Unit) {
        if (!reduce) trophy.animateTo(1f, swiftSpring(0.45f, 0.35f))
    }
    Appear(0f) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Melt.Orange, modifier = Modifier.size(28.dp).graphicsLayer { scaleX = trophy.value; scaleY = trophy.value })
            Text(" Leaderboard", color = BoardInk, fontFamily = Nunito, fontSize = 28.sp, fontWeight = FontWeight.Black)
        }
    }
    Appear(0.06f) {
        Text(
            if (scope == BoardScope.Friends) "Accepted friends, today only." else "See how your Daily XP stacks up.",
            color = BoardMuted,
            fontFamily = Nunito,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
    Appear(0.1f) {
        BoardTabs(scope) { scope = it }
    }
    if (scope == BoardScope.Friends) {
        FriendsBoard(
            graph = graph,
            note = friendNote,
            draft = inviteDraft,
            signedIn = repo.backend.signedIn,
            youName = repo.backend.profileName(),
            youXp = xp ?: repo.shownDailyXp(),
            youAvatar = repo.backend.profileAvatar(),
            onDraft = { inviteDraft = it },
            onAddCode = {
                val typed = inviteDraft
                inviteDraft = ""
                CoroutineScope(Dispatchers.IO).launch {
                    val message = repo.backend.addFriendByCode(typed)
                    withContext(Dispatchers.Main) { friendNote = message }
                    reloadFriends()
                }
            },
            onRespond = { id, accept ->
                CoroutineScope(Dispatchers.IO).launch {
                    val message = repo.backend.setFriendStatus(id, if (accept) "accepted" else "declined")
                    withContext(Dispatchers.Main) { friendNote = message }
                    reloadFriends()
                }
            },
            onOpen = { row -> if (row.you) onEditYou() else friendTarget = row },
            repo = repo,
            onEditYou = onEditYou,
            onSignIn = { reload(); reloadFriends() },
        )
    } else {
        val you = board.rows.firstOrNull { it.you } ?: board.pinned
        var youOnScreen by remember(you?.userId, you?.rank, board.rows.size) {
            mutableStateOf(board.rows.any { it.you })
        }
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { repo.refreshUsage(); reload(); reloadFriends() }) {
            Column(Modifier.fillMaxWidth()) {
                when {
                    !repo.backend.signedIn || board.signedOut -> Appear(0f) { SignInBoard(context, repo) { reload() } }
                    xp == null -> Appear(0.08f) { UsageBoard() }
                    board.rows.isEmpty() && board.pinned == null -> Appear(0.12f) { EmptyBoard() }
                    else -> {
                        if (board.rows.isNotEmpty()) {
                            Appear(0.04f) {
                                TopAvatars(board.rows, { row -> if (row.you) onEditYou() else friendTarget = row }) { youOnScreen = it }
                            }
                        }
                        board.rows.drop(3).forEachIndexed { index, row ->
                            Appear(0.12f + minOf(index, 7) * 0.05f) {
                                BoardLine(row, { tapped -> if (tapped.you) onEditYou() else friendTarget = tapped }) { youOnScreen = it }
                            }
                        }
                    }
                }
                board.message?.let {
                    Text(it, color = BoardMuted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center)
                }
            }
        }
        if (you != null && !youOnScreen && repo.backend.signedIn && xp != null && !board.signedOut) {
            YouPill(you, onEditYou)
        }
    }
    friendTarget?.let { row ->
        FriendActionDialog(
            name = com.ahmed.rotrun.logic.FriendCameoPick.displayName(row.name),
            state = com.ahmed.rotrun.logic.FriendCameoPick.actionState(
                repo.backend.userId.orEmpty(),
                row.userId,
                graph?.records.orEmpty(),
            ),
            onAdd = {
                val id = row.userId
                friendTarget = null
                CoroutineScope(Dispatchers.IO).launch {
                    val message = repo.backend.requestFriend(id, graph?.records.orEmpty())
                    withContext(Dispatchers.Main) { friendNote = message }
                    reloadFriends()
                }
            },
            onAccept = {
                val record = com.ahmed.rotrun.logic.FriendCameoPick.match(
                    repo.backend.userId.orEmpty(),
                    row.userId,
                    graph?.records.orEmpty(),
                )
                friendTarget = null
                if (record != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val message = repo.backend.setFriendStatus(record.id, "accepted")
                        withContext(Dispatchers.Main) { friendNote = message }
                        reloadFriends()
                    }
                }
            },
            onDecline = {
                val record = com.ahmed.rotrun.logic.FriendCameoPick.match(
                    repo.backend.userId.orEmpty(),
                    row.userId,
                    graph?.records.orEmpty(),
                )
                friendTarget = null
                if (record != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val message = repo.backend.setFriendStatus(record.id, "declined")
                        withContext(Dispatchers.Main) { friendNote = message }
                        reloadFriends()
                    }
                }
            },
            onDismiss = { friendTarget = null },
        )
    }
}

@Composable
private fun BoardTabs(scope: BoardScope, onSelect: (BoardScope) -> Unit) {
    val reduce = reduceMotion()
    val slide by androidx.compose.animation.core.animateFloatAsState(
        if (scope == BoardScope.Friends) 1f else 0f,
        if (reduce) androidx.compose.animation.core.tween(120, easing = androidx.compose.animation.core.EaseOut) else swiftSpring(0.32f, 0f),
        label = "board-scope",
    )
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp).clip(CircleShape).background(Color(0xFF1C1C1E)).padding(4.dp)) {
        Box(
            Modifier.fillMaxWidth(0.5f).offset { IntOffset((maxWidth * 0.5f * slide).roundToPx(), 0) }.height(36.dp).clip(CircleShape).background(BoardOrange),
        )
        Row(Modifier.fillMaxWidth()) {
            BoardTab("Daily XP", scope == BoardScope.Daily, Modifier.weight(1f)) { onSelect(BoardScope.Daily) }
            BoardTab("Friends", scope == BoardScope.Friends, Modifier.weight(1f)) { onSelect(BoardScope.Friends) }
        }
    }
}

@Composable
private fun BoardTab(label: String, on: Boolean, modifier: Modifier, click: () -> Unit) {
    Text(
        label,
        color = if (on) Color.White else BoardMuted,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = modifier.clickable(onClick = click).padding(vertical = 9.dp),
    )
}

@Composable
private fun UsageBoard() {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Daily XP", color = BoardInk, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Connect usage access to score Daily XP. A blank day is not a perfect score.", color = BoardMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun SignInBoard(context: android.content.Context, repo: RotRunRepository, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Sign in to see the leaderboard", color = BoardInk, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center)
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
        Text("No rankings yet", color = BoardInk, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
        Text("Play a bit, then pull to refresh your Daily XP rank.", color = BoardMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp))
    }
}

@Composable
private fun FriendsBoard(
    graph: com.ahmed.rotrun.logic.FriendGraph?,
    note: String?,
    draft: String,
    signedIn: Boolean,
    youName: String,
    youXp: Int,
    youAvatar: String?,
    onDraft: (String) -> Unit,
    onAddCode: () -> Unit,
    onRespond: (String, Boolean) -> Unit,
    repo: RotRunRepository,
    onOpen: (RankedXp) -> Unit,
    onEditYou: () -> Unit,
    onSignIn: () -> Unit,
) {
    val context = LocalContext.current
    if (!signedIn) {
        SignInBoard(context, repo, onSignIn)
        return
    }
    FriendInviteBar(graph?.inviteCode, draft, note, graph, onDraft, onAddCode, onRespond)
    val rows = friendRanks(graph?.friendXp.orEmpty(), graph?.yourUserId.orEmpty(), youName, youXp, youAvatar)
    if (graph?.friendXp.isNullOrEmpty()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CartoonBrain(BrainStyle.Meadow, Modifier.size(96.dp), animated = false)
            Text("No friends yet", color = BoardInk, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Add someone with their invite code. Accepted friends show today's Daily XP here.",
                color = BoardMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp),
            )
        }
        return
    }
    var youOnScreen by remember(rows.size) { mutableStateOf(rows.any { it.you }) }
    if (rows.isNotEmpty()) {
        TopAvatars(rows, onOpen) { youOnScreen = it }
        rows.drop(3).forEach { row -> BoardLine(row, onOpen) { youOnScreen = it } }
    }
    val you = rows.firstOrNull { it.you }
    if (you != null && !youOnScreen) YouPill(you, onEditYou)
}

private fun friendRanks(
    friends: List<com.ahmed.rotrun.logic.FriendXPRow>,
    youId: String,
    youName: String,
    youXp: Int,
    youAvatar: String?,
): List<RankedXp> {
    if (friends.isEmpty()) return emptyList()
    val people = friends.map { row ->
        RankedXp(row.userId, row.name, row.xp, 0, youId.isNotBlank() && row.userId.equals(youId, ignoreCase = true), row.avatarUrl)
    }.toMutableList()
    if (youId.isNotBlank() && people.none { it.userId.equals(youId, ignoreCase = true) }) {
        people.add(
            RankedXp(
                youId,
                com.ahmed.rotrun.logic.FriendCameoPick.displayName(youName),
                youXp.coerceAtLeast(0),
                0,
                true,
                youAvatar,
            ),
        )
    }
    return people.sortedWith(compareByDescending<RankedXp> { it.xp }.thenBy { it.name })
        .mapIndexed { index, row -> row.copy(rank = index + 1) }
}

@Composable
private fun FriendInviteBar(
    code: String?,
    draft: String,
    note: String?,
    graph: com.ahmed.rotrun.logic.FriendGraph?,
    onDraft: (String) -> Unit,
    onAdd: () -> Unit,
    onRespond: (String, Boolean) -> Unit,
) {
    val me = graph?.yourUserId.orEmpty()
    val incoming = graph?.records.orEmpty().filter { it.status == "pending" && it.addresseeId.equals(me, ignoreCase = true) }
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Your code", color = BoardMuted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        Text(code ?: "——", color = BoardInk, fontWeight = FontWeight.Black, fontSize = 22.sp)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.text.BasicTextField(
                value = draft,
                onValueChange = { onDraft(it.uppercase()) },
                textStyle = androidx.compose.ui.text.TextStyle(color = BoardInk, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                singleLine = true,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 14.dp, vertical = 10.dp),
                decorationBox = { inner ->
                    if (draft.isEmpty()) Text("Invite code", color = BoardMuted, fontWeight = FontWeight.Bold)
                    inner()
                },
            )
            Text(
                "Add",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp).clip(CircleShape).background(BoardOrange).clickable(onClick = onAdd).padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        if (!note.isNullOrBlank()) {
            Text(note, color = BoardInk.copy(alpha = 0.8f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        }
        incoming.forEach { request ->
            val name = graph?.names[request.requesterId.lowercase()] ?: "RotRun user"
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = BoardInk, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
                Text("Accept", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(CircleShape).background(Color.White).clickable { onRespond(request.id, true) }.padding(horizontal = 10.dp, vertical = 6.dp))
                Text("Decline", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).clickable { onRespond(request.id, false) }.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun FriendActionDialog(
    name: String,
    state: com.ahmed.rotrun.logic.FriendActionState,
    onAdd: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(24.dp)).background(Color(0xFF1C1C1E)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(name, color = BoardInk, fontWeight = FontWeight.Black, fontSize = 22.sp)
            val line = when (state) {
                com.ahmed.rotrun.logic.FriendActionState.Add -> "Send a friend request"
                com.ahmed.rotrun.logic.FriendActionState.Pending -> "Waiting for them to answer"
                com.ahmed.rotrun.logic.FriendActionState.Incoming -> "They asked to be friends"
                com.ahmed.rotrun.logic.FriendActionState.Friends -> "You're friends"
            }
            Text(line, color = BoardMuted, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
            when (state) {
                com.ahmed.rotrun.logic.FriendActionState.Add -> FriendActionButton("Add friend", onAdd)
                com.ahmed.rotrun.logic.FriendActionState.Pending -> Text("Request pending", color = BoardInk, fontWeight = FontWeight.Bold)
                com.ahmed.rotrun.logic.FriendActionState.Incoming -> {
                    FriendActionButton("Accept", onAccept)
                    Text("Decline", color = BoardInk, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp).clickable(onClick = onDecline))
                }
                com.ahmed.rotrun.logic.FriendActionState.Friends -> Text("Friends", color = BoardInk, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FriendActionButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().clip(CircleShape).background(BoardOrange).clickable(onClick = onClick).padding(vertical = 12.dp),
    )
}

@Composable
private fun TopAvatars(rows: List<RankedXp>, onOpen: (RankedXp) -> Unit, onYouPlacement: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom,
    ) {
        DailyBoard.topRow(rows).forEach { row ->
            TopPlace(row, onOpen, onYouPlacement)
        }
    }
}

@Composable
private fun TopPlace(row: RankedXp, onOpen: (RankedXp) -> Unit, onYouPlacement: (Boolean) -> Unit) {
    val first = row.rank == 1
    val medal = medalColor(row.rank)
    val avatar = if (first) 104.dp else 76.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(if (first) 124.dp else 100.dp)
            .offset(y = if (first) (-14).dp else 0.dp)
            .padding(horizontal = 4.dp)
            .then(if (row.you) Modifier.border(2.dp, BoardOrange, RoundedCornerShape(22.dp)) else Modifier)
            .clickable { onOpen(row) }
            .youWatch(row.you, onYouPlacement)
            .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        Text(
            row.name,
            color = BoardInk,
            fontWeight = FontWeight.Bold,
            fontSize = if (first) 15.sp else 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Box(Modifier.padding(top = 8.dp)) {
            Box(Modifier.border(3.dp, medal, CircleShape).padding(3.dp)) {
                PlayerAvatar(row.name, row.avatarUrl, avatar, fill = avatarFill(row.userId), ink = Color.White)
            }
            RankBadge(row.rank, medal, Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-6).dp))
        }
        Text(
            "${row.xp} XP",
            color = BoardInk,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = if (first) 16.sp else 14.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun RankBadge(rank: Int, fill: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(50)).background(fill).padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "#$rank",
            color = if (rank == 3) Color.White else Color.Black,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun BoardLine(row: RankedXp, onOpen: (RankedXp) -> Unit, onYouPlacement: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .then(if (row.you) Modifier.border(2.dp, BoardOrange, RoundedCornerShape(18.dp)) else Modifier)
            .clickable { onOpen(row) }
            .youWatch(row.you, onYouPlacement)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("#${row.rank}", color = BoardInk, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.width(40.dp))
        PlayerAvatar(row.name, row.avatarUrl, 36.dp, fill = avatarFill(row.userId), ink = Color.White)
        Text(
            row.name,
            color = BoardInk,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp).weight(1f),
        )
        Text("${row.xp} XP", color = BoardInk, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun YouPill(row: RankedXp, onEditYou: () -> Unit) {
    val density = LocalDensity.current
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val lift = with(density) { (168.dp + nav).roundToPx() }
    val width = with(density) { LocalWindowInfo.current.containerSize.width.toDp() } - 32.dp
    Popup(
        alignment = Alignment.BottomCenter,
        offset = IntOffset(0, -lift),
        properties = PopupProperties(focusable = false, clippingEnabled = false),
    ) {
        Row(
            Modifier.width(width).clip(RoundedCornerShape(28.dp)).background(BoardOrange).clickable(onClick = onEditYou).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("#${row.rank}", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 15.sp)
            Box(Modifier.padding(start = 10.dp)) {
                PlayerAvatar(row.name, row.avatarUrl, 36.dp, fill = avatarFill(row.userId), ink = Color.White)
            }
            Text(
                row.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp).weight(1f),
            )
            Text("${row.xp} XP", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Modifier.youWatch(you: Boolean, onPlacement: (Boolean) -> Unit): Modifier {
    if (!you) return this
    val view = LocalView.current
    val cover = with(LocalDensity.current) {
        (148.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()).toPx()
    }
    return onGloballyPositioned { coords ->
        val bounds = coords.boundsInWindow()
        onPlacement(bounds.height > 0f && bounds.bottom > 0f && bounds.top < view.height - cover)
    }
}

private fun medalColor(rank: Int): Color = when (rank) {
    1 -> Color(0xFFFFC747)
    2 -> Color(0xFFC0C0C0)
    else -> Color(0xFFCD7F32)
}

private val AvatarFills = listOf(
    Color(0xFF3B82F6),
    Color(0xFFEF4444),
    Color(0xFF22C55E),
    Color(0xFFA855F7),
    Color(0xFFF59E0B),
    Color(0xFF14B8A6),
    Color(0xFFEC4899),
    Color(0xFF6366F1),
)

private fun avatarFill(userId: String): Color = AvatarFills[userId.hashCode().mod(AvatarFills.size)]

private val BoardInk = Color.White
private val BoardMuted = Color(0xFF8E8E93)
private val BoardOrange = Color(0xFFFF8C2E)

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
            Modifier.fillMaxWidth(0.5f).offset { IntOffset((maxWidth * 0.5f * slide).roundToPx(), 0) }.height(36.dp).clip(CircleShape).background(rot().blue),
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
