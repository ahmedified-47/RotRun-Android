package com.ahmed.rotrun.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ahmed.rotrun.data.ChatCard
import com.ahmed.rotrun.data.RotRunRepository
import com.ahmed.rotrun.logic.FreeTier
import com.ahmed.rotrun.logic.PaywallGate
import com.ahmed.rotrun.logic.Scoring
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val ActionBlue = Color(red = 0.13f, green = 0.48f, blue = 1f)
private val CallGreen = Color(red = 0.18f, green = 0.72f, blue = 0.38f)
private val CallRed = Color(red = 0.92f, green = 0.22f, blue = 0.24f)
private val BubbleGray = Color(0.38f, 0.38f, 0.38f)

@Composable
fun TalkSheet(repo: RotRunRepository, onClose: () -> Unit) {
    val context = LocalContext.current
    val usage by repo.usage.collectAsState()
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    var signedIn by remember { mutableStateOf(repo.backend.signedIn) }
    var needsConsent by remember { mutableStateOf(repo.backend.needsConsent()) }
    var declined by remember { mutableStateOf(false) }
    var consentBusy by remember { mutableStateOf(false) }
    var turns by remember { mutableStateOf(repo.backend.turns()) }
    var cards by remember { mutableStateOf(repo.backend.chatCards()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var calling by remember { mutableStateOf(false) }
    var callStatus by remember { mutableStateOf("Listening") }
    var transcript by remember { mutableStateOf("") }
    var focusTick by remember { mutableIntStateOf(0) }
    var micDenied by remember { mutableStateOf(false) }
    var seeded by remember { mutableStateOf(false) }
    val pulse = rememberPulse(1.16f, 750, calling)
    val dark = rot().dark
    val page = if (dark) Color.Black else Color(0xFFF2F2F7)
    val card = if (dark) Color(0xFF1C1C1E) else Color.White
    val ink = if (dark) Color.White else Color.Black
    val secondary = Color(0xFF8E8E93)
    val tertiary = if (dark) Color(0xFF636366) else Color(0xFFC7C7CC)
    fun leave() {
        calling = false
        repo.backend.stopVoice()
        scope.launch(Dispatchers.IO) { repo.backend.archiveChat() }
        onClose()
    }
    BackHandler(onBack = ::leave)
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            micDenied = true
            note = "Voice permissions required"
        } else {
            micDenied = false
            beginCall(repo, { calling = true; callStatus = "Listening"; transcript = "" }, { note = it }) { kind, value ->
                when (kind) {
                    "transcript" -> transcript = value
                    "status" -> {
                        if (value == "ended") {
                            calling = false
                            transcript = ""
                        } else {
                            callStatus = value
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(signedIn, needsConsent, declined) {
        if (signedIn && !needsConsent && !declined && !seeded) {
            withContext(Dispatchers.IO) { repo.backend.seedGreeting() }
            turns = repo.backend.turns()
            seeded = true
        }
    }
    Column(Modifier.fillMaxSize().background(page).statusBarsPadding().navigationBarsPadding()) {
        TalkBar(
            ink = ink,
            secondary = secondary,
            calling = calling,
            status = callStatus,
            pulse = pulse,
            showCall = signedIn && !needsConsent && !declined && !calling,
            onBack = ::leave,
            onEnd = ::leave,
            onCall = {
                if (!repo.state.value.premiumActive()) {
                    note = FreeTier.voiceMessage
                    if (PaywallGate.shows) repo.requestPaywall()
                    return@TalkBar
                }
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    micDenied = false
                    beginCall(repo, { calling = true; callStatus = "Listening"; transcript = "" }, { note = it }) { kind, value ->
                        when (kind) {
                            "transcript" -> transcript = value
                            "status" -> {
                                if (value == "ended") {
                                    calling = false
                                    transcript = ""
                                } else {
                                    callStatus = value
                                }
                            }
                        }
                    }
                } else {
                    mic.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
        )
        when {
            !signedIn -> Gate(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                title = "Sign in to chat",
                body = "Sign in with Google to talk to your brain. Your conversations help it learn about you and give better advice.",
                ink = ink,
                secondary = secondary,
                action = "Sign in with Google",
                busy = false,
                onAction = {
                    val activity = context as? Activity ?: return@Gate
                    repo.backend.signIn(activity) { signError ->
                        signedIn = repo.backend.signedIn
                        needsConsent = repo.backend.needsConsent()
                        note = when {
                            signError.isNullOrBlank() -> null
                            signError.contains("isn’t set up") || signError.contains("isn't set up") -> signError
                            else -> signError
                        }
                    }
                },
                secondaryAction = null,
                onSecondary = {},
                note = note,
            )
            declined -> Gate(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                title = "AI Chat Disabled",
                body = "You chose not to enable AI chat. Tap below to enable it when you're ready.",
                ink = ink,
                secondary = secondary,
                action = "Enable AI Chat",
                busy = false,
                onAction = { declined = false },
                secondaryAction = null,
                onSecondary = {},
                note = note,
                style = BrainStyle.Meditating,
            )
            needsConsent -> Gate(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                title = "Enable AI Chat",
                body = "Replies are generated by AI via xAI. Chats are stored to give you memory.",
                ink = ink,
                secondary = secondary,
                action = "Allow",
                busy = consentBusy,
                onAction = {
                    consentBusy = true
                    scope.launch {
                        val consentError = withContext(Dispatchers.IO) { repo.backend.grantConsent() }
                        needsConsent = repo.backend.needsConsent()
                        note = consentError
                        consentBusy = false
                    }
                },
                secondaryAction = "Not now",
                onSecondary = { declined = true },
                note = note,
            )
            calling -> ActiveCall(status = callStatus, ink = ink, secondary = secondary, modifier = Modifier.weight(1f).fillMaxWidth())
            else -> {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val scroll = rememberScrollState()
                    var scrolled by remember { mutableStateOf(false) }
                    LaunchedEffect(turns.size, error, transcript, cards.size, busy, focusTick) {
                        if (!scrolled) {
                            scrolled = true
                            scroll.scrollTo(scroll.maxValue)
                        } else {
                            scroll.animateScrollTo(scroll.maxValue, androidx.compose.animation.core.tween(250, easing = androidx.compose.animation.core.EaseOut))
                        }
                    }
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = maxHeight).verticalScroll(scroll).padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
                    ) {
                        BrainHeader(usage.offenders.take(3).filter { it.seconds > 0 }, ink, secondary, tertiary, callStatus)
                        if (turns.isEmpty()) {
                            TurnBubble("assistant", "Hey, how are you? How can I help you?", ink, dark)
                        }
                        turns.forEach { (role, content) -> TurnBubble(role, content, ink, dark) }
                        if (transcript.isNotBlank()) TranscriptBubble(transcript, ink)
                        if (busy) TypingBubble(card, secondary)
                        error?.let { ErrorBubble(it, retry != null && !busy, card, secondary) { retry?.let { message -> send(repo, message, { busy = it }, { turns = repo.backend.turns(); cards = repo.backend.chatCards() }, { error = it; retry = message }, { note = it }, { error = null; retry = null }) } } }
                        cards.forEach { cardItem ->
                            ChatCardView(cardItem, ink, secondary, card) { action ->
                                scope.launch {
                                    val problem = withContext(Dispatchers.IO) {
                                        when (action) {
                                            "forget" -> repo.backend.forgetAll()
                                            else -> null
                                        }
                                    }
                                    if (action == "focus") {
                                        repo.backend.clearChatCards()
                                        repo.backend.stopVoice()
                                        repo.startFocus(25, false, "all")
                                        leave()
                                        return@launch
                                    }
                                    if (action.startsWith("note:")) {
                                        note = withContext(Dispatchers.IO) { repo.backend.saveChatNote(action.removePrefix("note:")) }
                                    } else if (action.startsWith("fact:")) {
                                        val parts = action.removePrefix("fact:").split("\u0000", limit = 2)
                                        note = withContext(Dispatchers.IO) { repo.backend.confirmChatFact(parts.getOrElse(0) { "" }, parts.getOrElse(1) { "" }) }
                                    } else if (action.startsWith("prefs:")) {
                                        val parts = action.removePrefix("prefs:").split("\u0000")
                                        note = withContext(Dispatchers.IO) {
                                            repo.backend.updateChatPrefs(
                                                parts.getOrNull(0)?.ifBlank { null },
                                                parts.getOrNull(1)?.ifBlank { null },
                                                parts.getOrNull(2)?.toIntOrNull(),
                                            )
                                        }
                                    }
                                    if (action == "forget") note = problem
                                    repo.backend.clearChatCards()
                                    cards = emptyList()
                                    if (action == "forget") turns = repo.backend.turns()
                                }
                            }
                        }
                    }
                }
                note?.let {
                    Text(it, color = tertiary, fontFamily = Nunito, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
                }
                if (micDenied) {
                    Row(Modifier.fillMaxWidth().background(card).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MicOff, contentDescription = null, tint = Color(0xFFFF9F0A), modifier = Modifier.size(16.dp))
                        Text("Voice permissions required", color = ink, fontFamily = Nunito, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp).weight(1f))
                        Text("Settings", color = ActionBlue, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.clickable {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            })
                        })
                    }
                }
                InputBar(draft, { draft = it }, !busy && draft.isNotBlank(), ink, card, tertiary, page, onFocus = { focusTick += 1 }) {
                    val message = draft.trim()
                    if (message.isEmpty() || busy) return@InputBar
                    if (PaywallGate.shows && !repo.state.value.premiumActive() && !canStillChat(repo)) {
                        repo.requestPaywall()
                        note = FreeTier.limitMessage
                        return@InputBar
                    }
                    draft = ""
                    send(repo, message, { busy = it }, { turns = repo.backend.turns(); cards = repo.backend.chatCards() }, { error = it; retry = message }, { note = it }, { error = null; retry = null })
                }
            }
        }
    }
}

private fun canStillChat(repo: RotRunRepository): Boolean {
    val state = repo.state.value
    if (state.premiumActive()) return true
    val used = if (state.chatDay == Scoring.dayKey()) state.chatCount else 0
    return used < FreeTier.dailyMessageCap
}

private fun send(
    repo: RotRunRepository,
    message: String,
    onBusy: (Boolean) -> Unit,
    onTurns: () -> Unit,
    onError: (String) -> Unit,
    onNote: (String?) -> Unit,
    onClearError: () -> Unit,
) {
    onBusy(true)
    onClearError()
    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
        val before = repo.backend.turns().size
        val reply = repo.backend.sendChat(message, "home")
        val after = repo.backend.turns()
        withContext(Dispatchers.Main) {
            onTurns()
            if (after.size == before && reply.isNotBlank()) {
                if (reply == FreeTier.tryAgain) onError(reply) else {
                    onNote(reply)
                    onClearError()
                }
            } else {
                onClearError()
                onNote(null)
            }
            onBusy(false)
        }
    }
}

private fun beginCall(repo: RotRunRepository, onLive: () -> Unit, onFail: (String) -> Unit, onUpdate: (String, String) -> Unit) {
    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
        val error = repo.backend.startVoice(onUpdate)
        withContext(Dispatchers.Main) {
            if (error == null) onLive() else onFail(error)
        }
    }
}

@Composable
private fun TalkBar(
    ink: Color,
    secondary: Color,
    calling: Boolean,
    status: String,
    pulse: Float,
    showCall: Boolean,
    onBack: () -> Unit,
    onEnd: () -> Unit,
    onCall: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        if (calling) {
            Box(Modifier.align(Alignment.CenterStart).size(32.dp).clip(CircleShape).background(CallRed).clickable(onClick = onEnd), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.CallEnd, contentDescription = "End call", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        } else {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Back", tint = ActionBlue, modifier = Modifier.align(Alignment.CenterStart).size(28.dp).clickable(onClick = onBack))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            CartoonBrain(BrainStyle.Meadow, Modifier.scale(if (calling) pulse else 1f).size(28.dp), animated = false)
            Column(Modifier.padding(start = 8.dp)) {
                Text("Rot", color = ink, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                val wrap = status == FreeTier.voiceWrapUp
                if (calling || wrap) {
                    Text(
                        if (wrap) status else if (status == "Talking") "Talking" else "Listening",
                        color = if (wrap) Color(0xFFFF9F0A) else secondary,
                        fontFamily = Nunito,
                        fontSize = 11.sp,
                    )
                }
            }
        }
        if (showCall) {
            Box(Modifier.align(Alignment.CenterEnd).size(32.dp).clip(CircleShape).background(CallGreen).clickable(onClick = onCall), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Call, contentDescription = "Call Rot", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun BrainHeader(offenders: List<com.ahmed.rotrun.data.Offender>, ink: Color, secondary: Color, tertiary: Color, status: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CartoonBrain(BrainStyle.Meadow, Modifier.size(72.dp), animated = true)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text("Rot", color = ink, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text(if (status == FreeTier.voiceWrapUp) status else "Your brain", color = if (status == FreeTier.voiceWrapUp) Color(0xFFFF9F0A) else secondary, fontFamily = Nunito, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            if (offenders.isNotEmpty()) {
                val line = offenders.joinToString(" · ") { "${it.label} ${(it.seconds / 60.0).roundToInt().coerceAtLeast(0)}m" }
                Text("Today's biggest apps: $line", color = tertiary, fontFamily = Nunito, fontSize = 12.sp, maxLines = 2)
            }
        }
    }
}

@Composable
private fun TurnBubble(role: String, content: String, ink: Color, dark: Boolean) {
    val user = role == "user"
    Row(verticalAlignment = Alignment.Bottom) {
        if (user) Spacer(Modifier.weight(1f).widthIn(min = 48.dp))
        else CartoonBrain(BrainStyle.Meadow, Modifier.padding(end = 8.dp).size(34.dp), animated = false)
        Text(
            content,
            color = if (user) Color.White else ink,
            fontFamily = Nunito,
            fontSize = 16.sp,
            textAlign = if (user) TextAlign.End else TextAlign.Start,
            modifier = Modifier.weight(1f, fill = false).clip(RoundedCornerShape(20.dp)).background(if (user) ActionBlue else BubbleGray).then(
                if (!user) Modifier.border(1.dp, Color.Black.copy(alpha = if (dark) 0.2f else 0.06f), RoundedCornerShape(20.dp)) else Modifier,
            ).padding(horizontal = 14.dp, vertical = 10.dp),
        )
        if (!user) Spacer(Modifier.widthIn(min = 36.dp))
    }
}

@Composable
private fun TranscriptBubble(text: String, ink: Color) {
    Row {
        Spacer(Modifier.weight(1f).widthIn(min = 60.dp))
        Row(Modifier.clip(RoundedCornerShape(18.dp)).background(ActionBlue.copy(alpha = 0.3f)).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = ink, fontFamily = Nunito, fontSize = 16.sp)
            Box(Modifier.padding(start = 8.dp).size(8.dp).clip(CircleShape).background(ActionBlue.copy(alpha = 0.7f)))
        }
    }
}

@Composable
private fun TypingBubble(card: Color, secondary: Color) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1600)
            tick += 1
        }
    }
    val lines = listOf("…chipping away", "…working on it", "…thinking", "…looking it up", "…sorting it out")
    Row {
        Row(Modifier.clip(RoundedCornerShape(18.dp)).background(card).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(3) {
                Box(Modifier.padding(end = 4.dp).size(8.dp).clip(CircleShape).background(secondary.copy(alpha = 0.5f)))
            }
            Text(lines[tick % lines.size], color = secondary, fontFamily = Nunito, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ErrorBubble(error: String, canRetry: Boolean, card: Color, secondary: Color, onRetry: () -> Unit) {
    Row {
        Column(Modifier.clip(RoundedCornerShape(18.dp)).background(card).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF9F0A), modifier = Modifier.size(16.dp))
                Text(error, color = secondary, fontFamily = Nunito, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
            }
            if (canRetry) {
                Text("Try again", color = ActionBlue, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp).clickable(onClick = onRetry))
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ChatCardView(card: ChatCard, ink: Color, secondary: Color, surface: Color, onAction: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(surface).padding(12.dp)) {
        when (card.type) {
            "remember" -> {
                Text("Remember this?", color = ink, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(card.data["body"].orEmpty(), color = secondary, fontFamily = Nunito, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                CardActions("Remember" to "note:${card.data["body"].orEmpty()}", "Don't" to "clear", secondary, onAction)
            }
            "confirm_fact" -> {
                Text("Remember this about you?", color = ink, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("${card.data["key"].orEmpty()}: ${card.data["value"].orEmpty()}", color = secondary, fontFamily = Nunito, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                CardActions("Confirm" to "fact:${card.data["key"].orEmpty()}\u0000${card.data["value"].orEmpty()}", "Not now" to "clear", secondary, onAction)
            }
            "confirm_forget" -> {
                Text("Forget everything?", color = ink, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("This clears all your notes and preferences.", color = secondary, fontFamily = Nunito, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Forget", color = CallRed, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.clickable { onAction("forget") })
                    Text("Cancel", color = secondary, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.clickable { onAction("clear") })
                }
            }
            "confirm_prefs" -> {
                Text("Update preferences?", color = ink, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                card.data["quiet_start"]?.let { Text("Quiet hours start: $it", color = secondary, fontFamily = Nunito, fontSize = 13.sp) }
                card.data["quiet_end"]?.let { Text("Quiet hours end: $it", color = secondary, fontFamily = Nunito, fontSize = 13.sp) }
                card.data["max_per_day"]?.let { Text("Max reminders: $it/day", color = secondary, fontFamily = Nunito, fontSize = 13.sp) }
                CardActions(
                    "Confirm" to "prefs:${card.data["quiet_start"].orEmpty()}\u0000${card.data["quiet_end"].orEmpty()}\u0000${card.data["max_per_day"].orEmpty()}",
                    "Cancel" to "clear",
                    secondary,
                    onAction,
                )
            }
            "start_focus" -> {
                Text("Start a Focus session?", color = ink, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                CardActions("Start Focus" to "focus", "Not now" to "clear", secondary, onAction)
            }
        }
    }
}

@Composable
private fun CardActions(primary: Pair<String, String>, dismiss: Pair<String, String>, secondary: Color, onAction: (String) -> Unit) {
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(primary.first, color = ActionBlue, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.clickable { onAction(primary.second) })
        Text(dismiss.first, color = secondary, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.clickable { onAction("clear") })
    }
}

@Composable
private fun InputBar(value: String, onValue: (String) -> Unit, canSend: Boolean, ink: Color, card: Color, tertiary: Color, page: Color, onFocus: () -> Unit = {}, onSend: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(page).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = value,
            onValueChange = onValue,
            textStyle = TextStyle(color = ink, fontFamily = Nunito, fontSize = 16.sp),
            cursorBrush = SolidColor(ActionBlue),
            modifier = Modifier.weight(1f).clip(CircleShape).background(card).padding(horizontal = 14.dp, vertical = 10.dp).onFocusChanged { if (it.isFocused) onFocus() },
            decorationBox = { inner ->
                if (value.isEmpty()) Text("Message Rot", color = tertiary, fontFamily = Nunito, fontSize = 16.sp)
                inner()
            },
        )
        Icon(
            Icons.Default.ArrowCircleUp,
            contentDescription = "Send",
            tint = if (canSend) ActionBlue else tertiary,
            modifier = Modifier.padding(start = 12.dp).size(32.dp).clickable(enabled = canSend, onClick = onSend),
        )
    }
}

@Composable
private fun ActiveCall(status: String, ink: Color, secondary: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CartoonBrain(BrainStyle.Meadow, Modifier.size(120.dp), animated = false)
        Text("Rot", color = ink, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 28.sp, modifier = Modifier.padding(top = 14.dp))
        Text(
            if (status == FreeTier.voiceWrapUp) status else if (status == "Talking") "Talking" else "Listening",
            color = if (status == FreeTier.voiceWrapUp) Color(0xFFFF9F0A) else secondary,
            fontFamily = Nunito,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun Gate(
    modifier: Modifier,
    title: String,
    body: String,
    ink: Color,
    secondary: Color,
    action: String,
    busy: Boolean,
    onAction: () -> Unit,
    secondaryAction: String?,
    onSecondary: () -> Unit,
    note: String?,
    style: BrainStyle = BrainStyle.Meadow,
) {
    Column(modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CartoonBrain(style, Modifier.size(if (title == "Sign in to chat") 120.dp else 100.dp))
        Text(title, color = ink, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 24.sp, modifier = Modifier.padding(top = 24.dp))
        Text(body, color = secondary, fontFamily = Nunito, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp, start = 8.dp, end = 8.dp))
        Box(Modifier.padding(top = 24.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ActionBlue).clickable(enabled = !busy, onClick = onAction).padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
            if (busy) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text(action, color = Color.White, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
        if (secondaryAction != null) {
            Text(secondaryAction, color = secondary, fontFamily = Nunito, fontWeight = FontWeight.Medium, fontSize = 17.sp, modifier = Modifier.padding(top = 12.dp).clickable(enabled = !busy, onClick = onSecondary))
        }
        note?.let { Text(it, color = CallRed, fontFamily = Nunito, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp)) }
    }
}
