package com.ahmed.rotrun.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmed.rotrun.BuildConfig
import com.ahmed.rotrun.R
import com.ahmed.rotrun.icon.LauncherIcons
import com.ahmed.rotrun.data.DeviceAccessReader
import com.ahmed.rotrun.data.RotRunRepository
import com.ahmed.rotrun.data.SavedState
import com.ahmed.rotrun.data.TrackedApp
import com.ahmed.rotrun.data.iconStreak
import com.ahmed.rotrun.data.themeStreak
import com.ahmed.rotrun.logic.BrainHealthLevel
import com.ahmed.rotrun.logic.Products
import com.ahmed.rotrun.logic.SupportCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(state: SavedState, installed: List<TrackedApp>, repo: RotRunRepository) {
    var page by remember { mutableStateOf("root") }
    var plan by remember { mutableStateOf(state.productId ?: Products.YEARLY) }
    var authError by remember { mutableStateOf(false) }
    var showDisclosure by remember { mutableStateOf(false) }
    val access by repo.access.collectAsState()
    val context = LocalContext.current
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        repo.refreshAccess()
    }
    when (page) {
        "privacy" -> LegalPage("privacy", "Privacy Policy") { page = "support" }
        "terms" -> LegalPage("terms", "Terms & Conditions") { page = "support" }
        "brain" -> BrainHealthPage(state, repo) { page = "root" }
        "excluded" -> ExcludedPage(state, installed, repo) { page = "root" }
        "styles" -> StylesPage(state, repo) { page = "root" }
        "rewards" -> RewardsPage(state, repo) { page = "root" }
        "icon" -> IconPage(state, repo) { page = "root" }
        "support" -> SupportPage { page = it }
        "help" -> HelpPage(repo) { page = "support" }
        "feedback" -> FeedbackPage { page = "support" }
        "contact" -> ContactPage { page = "support" }
        "widget" -> WidgetPage { page = "root" }
        "memory" -> MemoryPage(repo) { page = "root" }
        "subscription" -> Column(Modifier.padding(12.dp)) {
            Text("‹ Manage Subscription", color = Melt.Action, modifier = Modifier.clickable { page = "support" })
            GetAccessPaywall(
                state, showClose = false, showDiscountLink = false, onClose = {}, onDiscount = {},
                onPurchase = repo::purchase, onRestore = repo::restore, selected = plan, onSelect = { plan = it }, settingsStyle = true,
            )
        }
        else -> RootSettings(
            state = state,
            access = access,
            repo = repo,
            open = { page = it },
            onUsage = { DeviceAccessReader.open(context, DeviceAccessReader.usageIntent(context)) },
            onAccessibility = { showDisclosure = true },
            onNotifications = {
                if (Build.VERSION.SDK_INT >= 33 && !access.notifications) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
                else DeviceAccessReader.open(context, DeviceAccessReader.notificationIntent(context))
            },
            onOverlay = { DeviceAccessReader.open(context, DeviceAccessReader.overlayIntent(context)) },
            onBattery = { DeviceAccessReader.open(context, DeviceAccessReader.batteryIntent(context)) },
        )
    }
    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text("Accessibility") },
            text = { Text(stringResource(R.string.accessibility_disclosure)) },
            confirmButton = {
                TextButton(onClick = {
                    showDisclosure = false
                    runCatching { context.startActivity(DeviceAccessReader.accessibilityIntent()) }
                        .onFailure { authError = true }
                }) { Text("Open settings") }
            },
            dismissButton = { TextButton(onClick = { showDisclosure = false }) { Text("Not now") } },
        )
    }
    if (authError) {
        AlertDialog(
            onDismissRequest = { authError = false },
            title = { Text("Screen Time") },
            text = { Text("Couldn't connect to Screen Time.") },
            confirmButton = { TextButton(onClick = { authError = false }) { Text("OK") } },
        )
    }
}

@Composable
private fun RootSettings(
    state: SavedState,
    access: com.ahmed.rotrun.data.DeviceAccess,
    repo: RotRunRepository,
    open: (String) -> Unit,
    onUsage: () -> Unit,
    onAccessibility: () -> Unit,
    onNotifications: () -> Unit,
    onOverlay: () -> Unit,
    onBattery: () -> Unit,
) {
    val context = LocalContext.current
    val look = Looks.byId(state.theme)
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(92.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(look.canvas, look.tab))),
            contentAlignment = Alignment.Center,
        ) { CartoonBrain(look.brain, Modifier.size(78.dp), animated = false) }
        Text("Settings", fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        CardGroup {
            RowLink("Brain Health", state.brainHealth, Color(0xFFFF3B30)) { open("brain") }
            RowLink("Excluded Apps", tint = Color(0xFF59B8F2)) { open("excluded") }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Upgrade reminders", fontWeight = FontWeight.SemiBold)
                    Text("One local note a day from Rot about RotRun Premium. Off until you turn this on.", color = Melt.Muted, fontSize = 12.sp)
                }
                Switch(checked = state.upgradeReminders, onCheckedChange = { enabled ->
                    repo.update { it.copy(upgradeReminders = enabled) }
                    com.ahmed.rotrun.watch.UpgradeReminders.sync(context, repo.state.value)
                })
            }
        }
        CardGroup {
            RowLink("Intervention Style", tint = Color(0xFF598CF2)) { open("styles") }
            RowLink("Rewards", tint = Color(0xFFF28C33)) { open("rewards") }
            RowLink("App Icon", tint = Color(0xFF738CF2)) { open("icon") }
        }
        CardGroup {
            RowLink("Support & Feedback", tint = Color(0xFFF25973)) { open("support") }
            RowLink("Home Screen Widget", tint = Color(0xFF598CF2)) { open("widget") }
            RowLink("Rot's memory", tint = Color(0xFF3DB370)) { open("memory") }
        }
        Text("Request usage & blocking access", color = Melt.Muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        CardGroup {
            PermissionRow("Usage Access", "Today’s screen time, pickups, and top apps.", access.usage, onUsage)
            PermissionRow("Accessibility", stringResource(R.string.accessibility_disclosure), access.accessibility, onAccessibility)
            PermissionRow("Notifications", "Rot’s notes, focus timer, and Ask my brain.", access.notifications, onNotifications)
            if (!access.accessibility) {
                PermissionRow("Display over other apps", "Fallback shield when Accessibility is off.", access.overlay, onOverlay)
            }
            PermissionRow("Battery", "Keep blocking after a restart and when RotRun is swiped away.", access.batteryUnrestricted, onBattery)
        }
        Text(
            "Sync Screen Time now",
            color = Melt.Action,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp).clickable { repo.refreshUsage() },
        )
    }
}

@Composable
private fun PermissionRow(title: String, detail: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Melt.Muted, fontSize = 12.sp)
        }
        Text(if (granted) "On" else "Off", color = if (granted) Color(0xFF34C759) else Melt.House, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MemoryPage(repo: RotRunRepository, onBack: () -> Unit) {
    var note by remember { mutableStateOf<String?>(null) }
    BackHeader("Rot's memory", onBack)
    SettingsCard {
        Column(Modifier.padding(16.dp)) {
            Text("Rot can remember facts you share in chat. Forgetting clears those memories. Streak and XP stay.", color = Melt.Muted)
            Text(
                "Forget everything Rot remembers",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 14.dp).fillMaxWidth().clip(CircleShape).background(Color(0xFFFF3B30)).clickable {
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                        val error = repo.backend.forgetAll()
                        withContext(Dispatchers.Main) {
                            note = error ?: "Rot forgot what it had saved."
                        }
                    }
                }.padding(vertical = 14.dp),
            )
            note?.let { Text(it, modifier = Modifier.padding(top = 12.dp), color = Melt.Muted) }
        }
    }
}

@Composable
private fun BrainHealthPage(state: SavedState, repo: RotRunRepository, onBack: () -> Unit) {
    BackHeader("Brain Health", onBack)
    Text("Screen time drains brain health. Focus Mode restores it. Difficulty changes how quickly that happens.", color = Melt.Muted, modifier = Modifier.padding(horizontal = 20.dp))
    BrainHealthLevel.entries.forEach { level ->
        val on = state.brainHealth == level.label
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (on) Color(0xFFE8F1FF) else Color.White).clickable {
                repo.update { it.copy(brainHealth = level.label) }
            }.padding(14.dp),
        ) {
            Text(level.label, fontWeight = FontWeight.Bold)
            Text(level.headline)
            Text(level.goalLabel, color = Melt.Muted)
            Text(level.restoreCopy, color = Melt.Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ExcludedPage(state: SavedState, installed: List<TrackedApp>, repo: RotRunRepository, onBack: () -> Unit) {
    var picking by remember { mutableStateOf(false) }
    BackHeader("Excluded Apps", onBack)
    Text("Time spent in these apps won't lower your brain health (e.g. Maps, Slack, Spotify).", color = Melt.Muted, modifier = Modifier.padding(horizontal = 20.dp))
    SettingsCard {
        if (state.excluded.isEmpty()) Text("No apps yet", color = Melt.Muted, modifier = Modifier.padding(16.dp))
        state.excluded.forEach { app ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                AppGlyph(app.packageName, Modifier.size(36.dp))
                Text(app.label, modifier = Modifier.padding(start = 12.dp), fontWeight = FontWeight.SemiBold)
            }
        }
    }
    Text("Add More Apps", color = Melt.Action, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp).clickable { picking = true })
    if (picking) {
        AppPickerDialog(
            apps = installed,
            selected = state.excluded,
            onDismiss = { picking = false },
            onSave = { chosen ->
                repo.update { it.copy(excluded = chosen) }
                picking = false
            },
        )
    }
}

@Composable
private fun StylesPage(state: SavedState, repo: RotRunRepository, onBack: () -> Unit) {
    BackHeader("Intervention Style", onBack)
    Text("Turn on the styles you want. Ask my brain picks one at random.", color = Melt.Muted, modifier = Modifier.padding(horizontal = 20.dp))
    SettingsCard {
        StyleToggle("Standard Dialogue", "Chat with your brain", state.standardDialogue) { value -> repo.update { it.copy(standardDialogue = value) } }
        StyleToggle("Breathing Exercise", "4-2-4 then a question", state.breathingExercise) { value -> repo.update { it.copy(breathingExercise = value) } }
        StyleToggle("Mirror Mode", "FaceTime from your brain, requires camera", state.mirrorMode) { value -> repo.update { it.copy(mirrorMode = value) } }
        StyleToggle("Quick Check", "Need this? Then how long?", state.quickCheck) { value -> repo.update { it.copy(quickCheck = value) } }
        StyleToggle("Friend cameo", "A roast from a friend, when they sent you one today", state.friendCameo) { value -> repo.update { it.copy(friendCameo = value) } }
    }
}

@Composable
private fun StyleToggle(title: String, subtitle: String, on: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Melt.Muted, fontSize = 13.sp)
        }
        Switch(checked = on, onCheckedChange = change)
    }
}

@Composable
private fun RewardsPage(state: SavedState, repo: RotRunRepository, onBack: () -> Unit) {
    val next = Looks.rewards.firstOrNull { reward -> reward.days != null && state.streak < reward.days }
    val progress = if (next?.days == null) 1f else (state.streak.toFloat() / next.days).coerceIn(0f, 1f)
    BackHeader("Rewards", onBack)
    Text("Keep your streak alive to unlock Home looks, streak badges, and app icons.", color = Melt.Muted, modifier = Modifier.padding(horizontal = 20.dp))
    SettingsCard {
        Column(Modifier.padding(16.dp)) {
            Text("${state.streak}-day streak", fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text(
                if (next == null) "Every streak reward is unlocked." else "Next: ${next.title} at ${next.days} days",
                color = Melt.Muted,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
            )
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = Melt.Action, trackColor = Melt.Grouped)
        }
    }
    Looks.rewards.forEach { reward ->
        val unlocked = reward.days == null || state.streak >= reward.days
        val selected = state.theme == reward.themeId && state.appIcon == reward.iconId
        SettingsCard {
            Row(
                Modifier.fillMaxWidth().clickable(enabled = unlocked) {
                    repo.update { it.copy(theme = reward.themeId, appIcon = reward.iconId) }
                    repo.toast("Using ${reward.title}")
                }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RewardBadge(reward.title, unlocked)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(reward.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(reward.summary, color = Melt.Muted, fontSize = 13.sp)
                    Text(
                        when {
                            selected -> "Selected"
                            unlocked -> "Unlocked"
                            else -> "Unlock at ${reward.days}-day streak"
                        },
                        color = if (unlocked) Color(0xFF34C759) else Melt.Muted,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Image(painterResource(LauncherIcons.preview(reward.iconId)), contentDescription = reward.title, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)))
            }
        }
    }
}

@Composable
private fun RewardBadge(title: String, unlocked: Boolean) {
    val (icon, tint) = when (title) {
        "Spark" -> Icons.Default.Bolt to Color(0xFFFF8C3C)
        "Zen" -> Icons.Default.Eco to Color(0xFF6BAD94)
        "Scholar" -> Icons.Default.MenuBook to Color(0xFF5C4A8A)
        "Cosmic" -> Icons.Default.AutoAwesome to Color(0xFF7A6CFF)
        else -> null to Melt.Action
    }
    Box(Modifier.size(48.dp).clip(CircleShape).background(tint.copy(alpha = if (unlocked) 0.18f else 0.08f)), contentAlignment = Alignment.Center) {
        if (!unlocked) Icon(Icons.Default.Lock, contentDescription = "Locked", tint = Melt.Muted)
        else if (icon != null) Icon(icon, contentDescription = title, tint = tint)
        else CartoonBrain(BrainStyle.Meadow, Modifier.size(36.dp), animated = false)
    }
}

@Composable
private fun IconPage(state: SavedState, repo: RotRunRepository, onBack: () -> Unit) {
    BackHeader("App Icon", onBack)
    Text("The same names as Rewards. An unlocked icon replaces the one on your home screen.", color = Melt.Muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
    Looks.icons.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { (name, need) ->
                val unlocked = need == null || state.streak >= need
                val selected = state.appIcon == name
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(Color.White).border(if (selected) 3.dp else 0.dp, if (selected) Melt.Action else Color.Transparent, RoundedCornerShape(22.dp)).clickable {
                        if (!unlocked) {
                            repo.toast("Unlock at $need-day streak")
                        } else {
                            repo.update { it.copy(appIcon = name) }
                            repo.toast("Launcher icon set to $name")
                        }
                    }.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painterResource(LauncherIcons.preview(name)),
                            contentDescription = name,
                            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)),
                            alpha = if (unlocked) 1f else 0.4f,
                        )
                        if (!unlocked) Icon(Icons.Default.Lock, contentDescription = "Locked", tint = Melt.Ink)
                    }
                    Text(name, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    Text(
                        when {
                            selected -> "Selected"
                            unlocked -> "Tap to use"
                            else -> "$need-day streak"
                        },
                        color = if (selected) Melt.Action else Melt.Muted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            repeat(3 - row.size) { Box(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun SupportPage(open: (String) -> Unit) {
    BackHeader("Support & Feedback") { open("root") }
    Text("Need help? Reach out or leave us feedback!", color = Melt.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
    CardGroup {
        RowLink("Help") { open("help") }
        RowLink("Feedback / Bug Report") { open("feedback") }
        RowLink("Contact Us") { open("contact") }
        RowLink("Privacy Policy") { open("privacy") }
        RowLink("Terms & Conditions") { open("terms") }
        RowLink("Manage Subscription") { open("subscription") }
    }
    Text("Follow us", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
    Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FollowGlyph(Icons.Default.Close, "X")
        FollowGlyph(Icons.Default.PhotoCamera, "Camera")
        FollowGlyph(Icons.Default.MusicNote, "Music")
    }
    Text(SupportCopy.EMAIL, color = Melt.Action, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(20.dp))
    Text(versionLine(), color = Melt.Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
}

@Composable
private fun FollowGlyph(icon: ImageVector, label: String) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label, tint = Melt.Ink)
    }
}

@Composable
private fun HelpPage(repo: RotRunRepository, onBack: () -> Unit) {
    var open by remember { mutableStateOf<String?>(null) }
    BackHeader("Need help?", onBack)
    SettingsCard {
        Column(Modifier.padding(16.dp)) {
            Text("We're working with your phone's usage access to provide you with the best experience possible. Tapping the refresh button below resolves most issues.", color = Melt.Muted)
        }
    }
    BoxButton("Refresh Blocking System") { repo.toast("Blocking system refreshed"); repo.refreshUsage() }
    MailLink("Email a question", "RotRun Help", helpBody())
    Text("Frequently Asked Questions", fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp))
    SupportCopy.faqs.forEach { faq ->
        Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).clickable {
            open = if (open == faq.question) null else faq.question
        }.padding(14.dp)) {
            Text(faq.question, fontWeight = FontWeight.SemiBold)
            if (open == faq.question) Text(faq.answer, color = Melt.Muted, modifier = Modifier.padding(top = 6.dp))
        }
    }
    SettingsCard {
        Column(Modifier.padding(16.dp)) {
            Text("From the founder", fontWeight = FontWeight.Bold)
            Text("I appreciate your patience as I work with Android usage access. Refreshing usually clears blocking glitches — if not, email ${SupportCopy.EMAIL} and we'll jump on it.", color = Melt.Muted, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun FeedbackPage(onBack: () -> Unit) {
    var bug by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val context = LocalContext.current
    BackHeader("Feedback / Bug Report", onBack)
    Text(
        if (bug) "Tell us what happened, what you expected, and how to reproduce it." else "Product feedback: what you like, and what you'd change.",
        color = Melt.Muted,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
    Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceChip("Feedback", !bug) { bug = false }
        ChoiceChip("Bug Report", bug) { bug = true }
    }
    OutlinedTextField(
        value = message,
        onValueChange = { message = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        minLines = 5,
        label = { Text(if (bug) "What happened" else "Your message") },
    )
    SettingsCard {
        Column(Modifier.padding(16.dp)) {
            Text("Sent with your note", fontWeight = FontWeight.Bold)
            Text(versionLine(), color = Melt.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            Text("Android ${Build.VERSION.RELEASE}", color = Melt.Muted, fontSize = 13.sp)
            Text("${Build.MANUFACTURER} ${Build.MODEL}", color = Melt.Muted, fontSize = 13.sp)
        }
    }
    BoxButton(if (bug) "Email bug report" else "Email feedback") {
        if (message.isBlank()) return@BoxButton
        val subject = if (bug) "RotRun Bug Report" else "RotRun Feedback"
        val lead = if (bug) "Bug report:" else "Product feedback:"
        sendMail(context, subject, "Hi RotRun team,\n\n$lead\n\n$message\n\n${diagnostics()}")
    }
}

@Composable
private fun ContactPage(onBack: () -> Unit) {
    val context = LocalContext.current
    BackHeader("Contact Us", onBack)
    SettingsCard {
        Column(Modifier.padding(16.dp)) {
            Text("Questions, press, or anything that isn't a bug. We read every message.", color = Melt.Muted)
            Text("We'll open a message to ${SupportCopy.EMAIL} with subject RotRun Contact.", modifier = Modifier.padding(top = 8.dp))
            Text(versionLine(), color = Melt.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
    BoxButton("Email RotRun") {
        sendMail(context, "RotRun Contact", "Hi RotRun team,\n\n\n\n${diagnostics()}")
    }
}

@Composable
private fun ChoiceChip(title: String, on: Boolean, click: () -> Unit) {
    Text(
        title,
        color = if (on) Color.White else Melt.Ink,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(CircleShape).background(if (on) Melt.Action else Color.White).clickable(onClick = click).padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun WidgetPage(onBack: () -> Unit) {
    BackHeader("Home Screen Widget", onBack)
    val steps = listOf(
        "Long-press your Home Screen" to "Tap and hold an empty area until the menu appears.",
        "Tap Widgets" to "Open the widget picker from the home screen.",
        "Search for RotRun" to "Use the search bar at the top.",
        "Choose a widget" to "Brain Health shows today’s score. Focus starts a 15, 30, 45, or 60 minute session.",
        "Tap Add" to "Confirm adding the widget to your home screen.",
    )
    steps.forEachIndexed { index, (title, body) ->
        SettingsCard {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Melt.Action), contentAlignment = Alignment.Center) {
                    Text("${index + 1}", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(title, fontWeight = FontWeight.Bold)
                    Text(body, color = Melt.Muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun BackHeader(title: String, onBack: () -> Unit) {
    Text("‹ $title", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp).clickable(onClick = onBack))
}

@Composable
private fun CardGroup(content: @Composable () -> Unit) {
    Column(Modifier.padding(vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White)) { content() }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White)) { content() }
}

@Composable
private fun RowLink(title: String, value: String? = null, tint: Color = Melt.Action, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(tint))
        }
        Text(title, modifier = Modifier.padding(start = 12.dp).weight(1f), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        if (value != null) Text(value, color = Melt.Muted, fontSize = 15.sp)
        Text("›", color = Color(0xFFC7C7CC), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BoxButton(title: String, onClick: () -> Unit) {
    Text(title, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(22.dp).fillMaxWidth().clip(CircleShape).background(Melt.Action).clickable(onClick = onClick).padding(vertical = 16.dp))
}

@Composable
private fun MailLink(title: String, subject: String, body: String) {
    val context = LocalContext.current
    Text(title, color = Melt.Action, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp).clickable {
        val uri = Uri.parse("mailto:${SupportCopy.EMAIL}?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}")
        context.startActivity(Intent(Intent.ACTION_SENDTO, uri))
    })
}

private fun versionLine() = "RotRun version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

private fun diagnostics(): String = "———\n${versionLine()}\nAndroid ${Build.VERSION.RELEASE}\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}"

private fun helpBody() = "Hi RotRun team,\n\nHere’s my question:\n\n\n\n${diagnostics()}"

private fun sendMail(context: android.content.Context, subject: String, body: String) {
    val uri = Uri.parse("mailto:${SupportCopy.EMAIL}?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}")
    runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, uri)) }
}
