package com.ahmed.rotrun.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
        "styles" -> StylesPage(state, repo) { page = "root" }
        "rewards" -> RewardsPage(state, repo) { page = "root" }
        "icon" -> IconPage(state, repo) { page = "root" }
        "support" -> SupportPage { page = it }
        "help" -> HelpPage(repo) { page = "support" }
        "feedback" -> FeedbackPage { page = "support" }
        "contact" -> ContactPage { page = "support" }
        "widget" -> WidgetPage { page = "root" }
        "memory" -> MemoryPage(repo) { page = "root" }
        "subscription" -> PremiumManage(state, repo, onBack = { page = "support" }, onChangePlan = { page = "plans" })
        "plans" -> Column(Modifier.padding(bottom = 12.dp)) {
            BackHeader("RotRun Premium") { page = "subscription" }
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
    if (page == "brain") BrainHealthPage(state, repo) { page = "root" }
    if (page == "excluded") ExcludedPage(state, installed, repo) { page = "root" }
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
    val chrome = rot()
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(84.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(Color(0xFF7DDE9A), Color(0xFF3CB371)))),
            contentAlignment = Alignment.Center,
        ) { CartoonBrain(BrainStyle.Meadow, Modifier.size(72.dp), animated = false) }
        Text("Settings", color = chrome.ink, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        CardGroup {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("Upgrade reminders", color = chrome.ink, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "One local note a day from Rot about RotRun Premium. Off until you turn this on. RotRun will not ask for notification permission just to send these.",
                        color = chrome.muted,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    GreenSwitch(state.upgradeReminders) { enabled ->
                        repo.update { it.copy(upgradeReminders = enabled) }
                        com.ahmed.rotrun.watch.UpgradeReminders.sync(context, repo.state.value)
                    }
                }
            }
        }
        CardGroup {
            RowLink("Brain Health", state.brainHealth, Icons.Default.Favorite, Color(0xFFFF3B30)) { open("brain") }
            Hairline()
            RowLink("Excluded Apps", icon = Icons.Default.Diamond, tint = Color(0xFF5AC8FA)) { open("excluded") }
        }
        CardGroup {
            RowLink("Intervention Style", icon = Icons.Default.Chat, tint = Color(0xFF64B5F6)) { open("styles") }
            Hairline()
            RowLink("Rewards", icon = Icons.Default.CardGiftcard, tint = Color(0xFFFF9F0A)) { open("rewards") }
            Hairline()
            RowLink("App Icon", icon = Icons.Default.GridView, tint = Color(0xFF5E5CE6)) { open("icon") }
        }
        CardGroup {
            RowLink("Support & Feedback", icon = Icons.Default.QuestionMark, tint = Color(0xFFFF375F)) { open("support") }
            Hairline()
            RowLink("Home Screen Widget", icon = Icons.Default.GridView, tint = Color(0xFF0A84FF)) { open("widget") }
        }
        Text("Permissions", color = chrome.ink, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(top = 18.dp).fillMaxWidth())
        Text("Request usage and blocking access", color = chrome.muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp).fillMaxWidth())
        CardGroup {
            PermissionRow("Usage Access", "Today’s screen time, pickups, and top apps.", access.usage, onUsage)
            Hairline()
            PermissionRow("Accessibility", stringResource(R.string.accessibility_disclosure), access.accessibility, onAccessibility)
            Hairline()
            PermissionRow("Notifications", "Rot’s notes, focus timer, and Ask my brain.", access.notifications, onNotifications)
            if (!access.accessibility) {
                Hairline()
                PermissionRow("Display over other apps", "Fallback shield when Accessibility is off.", access.overlay, onOverlay)
            }
            Hairline()
            PermissionRow("Battery", "Keep blocking after a restart and when RotRun is swiped away.", access.batteryUnrestricted, onBattery)
        }
        Text(
            "Rot's memory",
            color = chrome.blue,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 14.dp).clickable { open("memory") },
        )
        Text(
            "Sync Screen Time now",
            color = chrome.blue,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp).clickable { repo.refreshUsage() },
        )
    }
}

@Composable
private fun PermissionRow(title: String, detail: String, granted: Boolean, onClick: () -> Unit) {
    val chrome = rot()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = chrome.ink, fontWeight = FontWeight.SemiBold)
            Text(detail, color = chrome.muted, fontSize = 12.sp)
        }
        Text(if (granted) "On" else "Off", color = if (granted) chrome.green else chrome.danger, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GreenSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val chrome = rot()
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = chrome.green,
            checkedThumbColor = Color.White,
            uncheckedTrackColor = chrome.pill,
            uncheckedThumbColor = Color.White,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
private fun Hairline() {
    Box(Modifier.padding(start = 52.dp).fillMaxWidth().height(1.dp).background(rot().line))
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
    val chrome = rot()
    BottomSheet(onDismiss = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("Brain Health", color = chrome.ink, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text("Screen time drains brain health. Focus Mode restores it. Difficulty changes how quickly that happens.", color = chrome.muted, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
            BrainHealthLevel.entries.forEach { level ->
                val on = state.brainHealth == level.label
                Column(
                    Modifier.padding(vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (on) chrome.blue.copy(alpha = 0.16f) else chrome.card).clickable {
                        repo.update { it.copy(brainHealth = level.label) }
                    }.padding(14.dp),
                ) {
                    Text(level.label, color = chrome.ink, fontWeight = FontWeight.Bold)
                    Text(level.headline, color = chrome.ink)
                    Text(level.goalLabel, color = chrome.muted)
                    Text(level.restoreCopy, color = chrome.muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ExcludedPage(state: SavedState, installed: List<TrackedApp>, repo: RotRunRepository, onBack: () -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val chrome = rot()
    BottomSheet(onDismiss = onBack) {
        Column(Modifier.padding(20.dp)) {
            Text("Excluded Apps", color = chrome.ink, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text("Time spent in these apps won’t lower your brain health (e.g. Maps, Slack, Spotify).", color = chrome.muted, modifier = Modifier.padding(top = 6.dp))
            Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(chrome.card)) {
                if (state.excluded.isEmpty()) Text("No apps yet", color = chrome.muted, modifier = Modifier.padding(16.dp))
                state.excluded.forEach { app ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppGlyph(app.packageName, Modifier.size(36.dp))
                        Text(app.label, color = chrome.ink, modifier = Modifier.padding(start = 12.dp), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Text("Add More Apps", color = chrome.blue, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp).clickable { picking = true })
        }
    }
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
    val chrome = rot()
    Column(Modifier.padding(bottom = 12.dp)) {
        BackHeader("Intervention Style", onBack)
        Text("Turn on the styles you want. Let your brain pick one at random.", color = chrome.muted, modifier = Modifier.padding(horizontal = 20.dp))
        StyleToggle("Standard Dialogue", "Chat with your brain", state.standardDialogue) { value -> repo.update { it.copy(standardDialogue = value) } }
        StyleToggle("Breathing Exercise", "4-2-4 then a question", state.breathingExercise) { value -> repo.update { it.copy(breathingExercise = value) } }
        StyleToggle("Mirror Mode", "FaceTime from your brain, requires camera", state.mirrorMode) { value -> repo.update { it.copy(mirrorMode = value) } }
        StyleToggle("Quick Check", "Need this? Then how long?", state.quickCheck) { value -> repo.update { it.copy(quickCheck = value) } }
        StyleToggle("Friend cameo", "A roast from a Game Center friend, when they sent you one today", state.friendCameo) { value -> repo.update { it.copy(friendCameo = value) } }
    }
}

@Composable
private fun StyleToggle(title: String, subtitle: String, on: Boolean, change: (Boolean) -> Unit) {
    val chrome = rot()
    Row(
        Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(chrome.card).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = chrome.ink, fontWeight = FontWeight.Bold)
            Text(subtitle, color = chrome.muted, fontSize = 13.sp)
        }
        GreenSwitch(on, change)
    }
}

@Composable
private fun RewardsPage(state: SavedState, repo: RotRunRepository, onBack: () -> Unit) {
    val chrome = rot()
    val next = Looks.rewards.firstOrNull { reward -> reward.days != null && state.streak < reward.days }
    val progress = if (next?.days == null) 1f else (state.streak.toFloat() / next.days).coerceIn(0f, 1f)
    BackHeader("Rewards", onBack)
    Text("Keep your streak alive to unlock Home looks, streak badges, and app icons.", color = chrome.muted, modifier = Modifier.padding(horizontal = 20.dp))
    SettingsCard {
        Column(Modifier.padding(16.dp)) {
            Text("${state.streak.coerceAtLeast(1)}-day streak", color = chrome.ink, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text(
                if (next == null) "Every streak reward is unlocked." else "Next: ${next.title} at ${next.days} days",
                color = chrome.muted,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
            )
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = chrome.blue, trackColor = chrome.inset)
        }
    }
    Text("Themes", color = chrome.ink, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth())
    Text("Set a look for Home. Streak themes also change your Home brain and streak badge.", color = chrome.muted, modifier = Modifier.padding(horizontal = 20.dp))
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(chrome.card)) {
        Looks.rewards.forEachIndexed { index, reward ->
            val unlocked = reward.days == null || state.streak >= reward.days
            val look = Looks.byId(reward.themeId)
            val selected = state.theme == reward.themeId
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(chrome.line))
            Row(
                Modifier.fillMaxWidth().clickable(enabled = unlocked) {
                    repo.update { it.copy(theme = reward.themeId) }
                    repo.toast("Using ${look.title}")
                }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(look.canvas), contentAlignment = Alignment.Center) {
                    if (unlocked) CartoonBrain(look.brain, Modifier.size(40.dp), animated = false)
                    else Icon(Icons.Default.Lock, contentDescription = "Locked", tint = Color.White)
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(look.title, color = if (unlocked) chrome.ink else chrome.muted, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    if (!unlocked) Text("Unlock at ${reward.days}-day streak", color = chrome.muted, fontSize = 13.sp)
                }
                if (!unlocked) {
                    Icon(Icons.Default.Lock, contentDescription = "Locked", tint = chrome.muted)
                } else if (selected) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(chrome.blue), contentAlignment = Alignment.Center) {
                        Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Box(Modifier.size(26.dp).clip(CircleShape).border(1.5.dp, chrome.muted, CircleShape))
                }
            }
        }
    }
}

@Composable
private fun IconPage(state: SavedState, repo: RotRunRepository, onBack: () -> Unit) {
    val chrome = rot()
    BackHeader("App Icon", onBack)
    Text(
        "Use any unlocked icon on your Home Screen.",
        color = chrome.muted,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(chrome.card)) {
        Looks.icons.forEachIndexed { index, (name, need) ->
            val unlocked = need == null || state.streak >= need
            val selected = state.appIcon == name
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(chrome.line))
            Row(
                Modifier.fillMaxWidth().clickable {
                    if (!unlocked) repo.toast("Unlock at $need-day streak")
                    else {
                        repo.update { it.copy(appIcon = name) }
                        repo.toast("Launcher icon set to $name")
                    }
                }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painterResource(LauncherIcons.preview(name)),
                    contentDescription = name,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
                    alpha = if (unlocked) 1f else 0.45f,
                )
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(name, color = if (unlocked) chrome.ink else chrome.muted, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    if (!unlocked) Text("Unlock at $need-day streak", color = chrome.muted, fontSize = 13.sp)
                }
                if (!unlocked) {
                    Icon(Icons.Default.Lock, contentDescription = "Locked", tint = chrome.muted)
                } else if (selected) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(chrome.blue), contentAlignment = Alignment.Center) {
                        Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Box(Modifier.size(26.dp).clip(CircleShape).border(1.5.dp, chrome.muted, CircleShape))
                }
            }
        }
    }
}

@Composable
private fun SupportPage(open: (String) -> Unit) {
    val context = LocalContext.current
    BackHeader("Support & Feedback") { open("root") }
    Text("Need help? Reach out or leave us feedback!", color = Melt.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
    CardGroup {
        RowLink("Help") { open("help") }
        RowLink("Feedback / Bug Report") { open("feedback") }
        RowLink("Contact Us") { open("contact") }
        RowLink("Privacy Policy") { HostedPolicy.open(context, HostedPolicy.PRIVACY) }
        RowLink("Terms & Conditions") { HostedPolicy.open(context, HostedPolicy.TERMS) }
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
    Box(Modifier.size(44.dp).clip(CircleShape).background(rot().inset), contentAlignment = Alignment.Center) {
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
        Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(rot().card).clickable {
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
    var sendNote by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val chrome = rot()
    BackHeader("Feedback / Bug Report", onBack)
    Text(
        "Tell us what to improve, or walk us through a bug. Mail goes to ${SupportCopy.EMAIL}.",
        color = chrome.muted,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
    Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth().clip(CircleShape).background(chrome.inset).padding(4.dp)) {
        ChoiceChip("Feedback", !bug, Modifier.weight(1f)) { bug = false }
        ChoiceChip("Bug Report", bug, Modifier.weight(1f)) { bug = true }
    }
    SettingsCard {
        Column(Modifier.padding(16.dp)) {
            Text(if (bug) "Bug report" else "Product feedback", color = chrome.ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(
                if (bug) "Tell us what happened and how to reproduce it. We’ll pre-fill a short template."
                else "Share what you like, what feels off, or a feature you’d want next. We’ll pre-fill a short template.",
                color = chrome.muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
    BoxButton(if (bug) "Email bug report" else "Email feedback") {
        val subject = if (bug) "RotRun Bug Report" else "RotRun Feedback"
        val body = if (bug) {
            "Hi RotRun team,\n\nBug report:\n\nWhat happened:\n\nWhat I expected:\n\nSteps to reproduce:\n\n${diagnostics()}"
        } else {
            "Hi RotRun team,\n\nProduct feedback:\n\nWhat I like:\n\nWhat feels off:\n\n${diagnostics()}"
        }
        sendNote = if (sendMail(context, subject, body)) "Opening your email app." else "Couldn't open an email app."
    }
    sendNote?.let { Text(it, color = chrome.muted, modifier = Modifier.padding(horizontal = 22.dp)) }
}

@Composable
private fun ContactPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var sendNote by remember { mutableStateOf<String?>(null) }
    val chrome = rot()
    BackHeader("Contact Us", onBack)
    Text("Questions, press, or anything that isn’t a bug.", color = chrome.muted, modifier = Modifier.padding(horizontal = 20.dp))
    SettingsCard {
        Column(Modifier.padding(16.dp)) {
            Text("Email", color = chrome.muted, fontSize = 13.sp)
            Text(SupportCopy.EMAIL, color = chrome.ink, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(top = 4.dp))
            Text("Subject", color = chrome.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
            Text("RotRun Contact", color = chrome.ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        }
    }
    BoxButton("Email RotRun") {
        val body = "Hi RotRun team,\n\n\n\n${diagnostics()}"
        sendNote = if (sendMail(context, "RotRun Contact", body)) "Opening your email app." else "Couldn't open an email app."
    }
    sendNote?.let { Text(it, color = chrome.muted, modifier = Modifier.padding(horizontal = 22.dp)) }
}

@Composable
private fun ChoiceChip(title: String, on: Boolean, modifier: Modifier = Modifier, click: () -> Unit) {
    Text(
        title,
        color = if (on) Color.White else rot().muted,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = modifier.clip(CircleShape).background(if (on) rot().blue else Color.Transparent).clickable(onClick = click).padding(vertical = 10.dp),
    )
}

@Composable
private fun WidgetPage(onBack: () -> Unit) {
    BackHeader("Home Screen Widget", onBack)
    Text("Brain Health on your home screen, and a Focus widget that starts a session.", color = Melt.Muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0xFF24754D)).padding(16.dp)) {
        Text("ROTRUN", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("Brain Health", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("100%", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                Text("Looking sharp today", color = Color.White.copy(alpha = 0.9f))
                Text("Streak 1", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            }
            CartoonBrain(BrainStyle.Meadow, Modifier.size(96.dp), animated = false)
        }
    }
    Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(rot().card).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        CartoonBrain(BrainStyle.Meadow, Modifier.size(64.dp), animated = false)
        Column(Modifier.padding(start = 12.dp)) {
            Text("Focus", color = rot().ink, fontWeight = FontWeight.Bold)
            Text("15 · 30 · 45 · 60 min", color = rot().muted, fontSize = 13.sp)
        }
    }
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
                Box(Modifier.size(36.dp).clip(CircleShape).background(rot().blue), contentAlignment = Alignment.Center) {
                    Text("${index + 1}", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(title, color = rot().ink, fontWeight = FontWeight.Bold)
                    Text(body, color = rot().muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun BackHeader(title: String, onBack: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(rot().card).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Text("‹", color = rot().ink, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
        Text(title, color = rot().ink, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
    }
}

@Composable
private fun CardGroup(content: @Composable () -> Unit) {
    Column(Modifier.padding(vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(rot().card)) { content() }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(rot().card)) { content() }
}

@Composable
private fun RowLink(
    title: String,
    value: String? = null,
    icon: ImageVector? = null,
    tint: Color = Melt.Action,
    onClick: () -> Unit,
) {
    val chrome = rot()
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Text(title, color = chrome.ink, modifier = Modifier.padding(start = if (icon != null) 12.dp else 0.dp).weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        if (value != null) Text(value, color = chrome.muted, fontSize = 15.sp)
        Text("›", color = chrome.muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun BoxButton(title: String, onClick: () -> Unit) {
    Text(title, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth().clip(CircleShape).background(rot().blue).clickable(onClick = onClick).padding(vertical = 16.dp))
}

@Composable
private fun PremiumManage(state: SavedState, repo: RotRunRepository, onBack: () -> Unit, onChangePlan: () -> Unit) {
    val context = LocalContext.current
    val chrome = rot()
    val plan = Products.planName(state.productId)
    val renews = if (state.premiumActive() && !state.lifetime && state.premiumUntil > System.currentTimeMillis()) {
        "Renews ${java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(state.premiumUntil))}"
    } else if (state.lifetime) {
        "Does not renew"
    } else {
        "Prices come from Google Play"
    }
    BackHeader("RotRun Premium", onBack)
    Text(
        "Buy, restore, or manage your plan. Blocking, Focus, Brick, and Ask-my-brain stay available without a subscription.",
        color = chrome.muted,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(chrome.card).padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CartoonBrain(BrainStyle.Meadow, Modifier.size(120.dp), animated = false)
        Text(if (state.premiumActive()) plan else "No plan yet", color = chrome.ink, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.padding(top = 8.dp))
        Text(renews, color = chrome.muted, modifier = Modifier.padding(top = 4.dp))
    }
    Column(Modifier.padding(horizontal = 16.dp)) {
    CardGroup {
        RowLink("Change Plan", icon = Icons.Default.CardGiftcard, tint = Melt.Orange, onClick = onChangePlan)
        Hairline()
        RowLink("Manage Subscription", if (state.premiumActive()) plan else null, icon = Icons.Default.GridView, tint = chrome.blue) {
            val uri = android.net.Uri.parse("https://play.google.com/store/account/subscriptions?package=${context.packageName}")
            runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri)) }
        }
        Hairline()
        RowLink("Restore Purchases", icon = Icons.Default.Refresh, tint = chrome.blue, onClick = repo::restore)
    }
    }
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

private fun sendMail(context: android.content.Context, subject: String, body: String): Boolean {
    val uri = Uri.parse("mailto:${SupportCopy.EMAIL}?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}")
    return runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, uri)) }.isSuccess
}
