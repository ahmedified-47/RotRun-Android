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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmed.rotrun.R
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
        "feedback" -> MailPage("Feedback / Bug Report", "RotRun Feedback", feedbackBody(context)) { page = "support" }
        "contact" -> MailPage("Contact Us", "RotRun Contact", contactBody(context)) { page = "support" }
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
    Text("Rot can remember facts you share in chat. Forgetting clears those memories. Streak and XP stay.", color = Melt.Muted, modifier = Modifier.padding(horizontal = 20.dp))
    Text(
        "Forget everything Rot remembers",
        color = Color(0xFFFF3B30),
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(20.dp).clickable {
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                val error = repo.backend.forgetAll()
                withContext(Dispatchers.Main) {
                    note = error ?: "Rot forgot what it had saved."
                }
            }
        },
    )
    note?.let { Text(it, modifier = Modifier.padding(horizontal = 20.dp), color = Melt.Muted) }
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
    if (state.excluded.isEmpty()) Text("No apps yet", modifier = Modifier.padding(20.dp))
    state.excluded.forEach { app -> Text(app.label, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
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
    StyleToggle("Standard Dialogue", "Chat with your brain", state.standardDialogue) { value -> repo.update { it.copy(standardDialogue = value) } }
    StyleToggle("Breathing Exercise", "4-2-4 then a question", state.breathingExercise) { value -> repo.update { it.copy(breathingExercise = value) } }
    StyleToggle("Mirror Mode", "FaceTime from your brain, requires camera", state.mirrorMode) { value -> repo.update { it.copy(mirrorMode = value) } }
    StyleToggle("Quick Check", "Need this? Then how long?", state.quickCheck) { value -> repo.update { it.copy(quickCheck = value) } }
    StyleToggle("Friend cameo", "A roast from a friend, when they sent you one today", state.friendCameo) { value -> repo.update { it.copy(friendCameo = value) } }
}

@Composable
private fun StyleToggle(title: String, subtitle: String, on: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Melt.Muted, fontSize = 13.sp)
        }
        Switch(checked = on, onCheckedChange = change)
    }
}

@Composable
private fun RewardsPage(state: SavedState, repo: RotRunRepository, onBack: () -> Unit) {
    BackHeader("Rewards", onBack)
    Text("Keep your streak alive to unlock Home looks, streak badges, and app icons.", color = Melt.Muted, modifier = Modifier.padding(horizontal = 20.dp))
    Looks.all.forEach { look ->
        val need = look.streak
        val unlocked = need == null || state.streak >= need
        Text(
            "${look.title}  ${if (unlocked) "Unlocked" else "Unlock at $need-day streak"}",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).clickable {
                if (unlocked) repo.update { it.copy(theme = look.id) }
            },
        )
    }
    listOf(
        3 to "Spark" to "Sunrise Home look, Spark app icon, and a bolt streak badge.",
        7 to "Zen" to "Spa Home look, Zen app icon, and a meditating Home brain.",
        14 to "Scholar" to "Twilight Home look, Scholar app icon, and a reading Home brain.",
        30 to "Cosmic" to "Night-sky Home look, Cosmic app icon, and a robe Home brain.",
    ).forEach { (pair, summary) ->
        val (days, title) = pair
        Text("$title · $days days", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        Text(summary, color = Melt.Muted, modifier = Modifier.padding(horizontal = 20.dp))
    }
}

@Composable
private fun IconPage(state: SavedState, repo: RotRunRepository, onBack: () -> Unit) {
    BackHeader("App Icon", onBack)
    Looks.icons.forEach { (name, need) ->
        val unlocked = need == null || state.streak >= (need)
        Text(
            "$name  ${if (unlocked) if (state.appIcon == name) "Selected" else "Tap to save" else "Unlock at $need-day streak"}",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).clickable(enabled = unlocked) {
                repo.update { it.copy(appIcon = name) }
                repo.toast("Saved. This Android build keeps one launcher icon.")
            },
        )
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
    Text("Follow us", fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp))
    Text("✕    camera    music", modifier = Modifier.padding(horizontal = 20.dp))
    Text(SupportCopy.EMAIL, color = Melt.Action, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(20.dp))
    Text("RotRun version 1.0.0 (1)", color = Melt.Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
}

@Composable
private fun HelpPage(repo: RotRunRepository, onBack: () -> Unit) {
    var open by remember { mutableStateOf<String?>(null) }
    BackHeader("Need help?", onBack)
    Text("We're working with your phone's usage access to provide you with the best experience possible. Tapping the refresh button below resolves most issues.", color = Melt.Muted, modifier = Modifier.padding(horizontal = 22.dp))
    BoxButton("Refresh Blocking System") { repo.toast("Blocking system refreshed"); repo.refreshUsage() }
    MailLink("Email a question", "RotRun Help", helpBody())
    Text("Frequently Asked Questions", fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp))
    SupportCopy.faqs.forEach { faq ->
        Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).clickable {
            open = if (open == faq.question) null else faq.question
        }.padding(12.dp)) {
            Text(faq.question, fontWeight = FontWeight.Medium)
            if (open == faq.question) Text(faq.answer, color = Melt.Muted, modifier = Modifier.padding(top = 6.dp))
        }
    }
    Text("From the founder", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
    Text("I appreciate your patience as I work with Android usage access. Refreshing usually clears blocking glitches — if not, email ${SupportCopy.EMAIL} and we'll jump on it.", modifier = Modifier.padding(horizontal = 20.dp))
}

@Composable
private fun MailPage(title: String, subject: String, body: String, onBack: () -> Unit) {
    BackHeader(title, onBack)
    Text(body, modifier = Modifier.padding(20.dp))
    MailLink("Send email", subject, body)
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
        Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
            Text("${index + 1}. $title", fontWeight = FontWeight.Bold)
            Text(body, color = Melt.Muted)
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

private fun diagnostics(): String = "———\nRotRun version 1.0.0 (1)\nAndroid ${Build.VERSION.RELEASE}\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}"

private fun helpBody() = "Hi RotRun team,\n\nHere’s my question:\n\n\n\n${diagnostics()}"
private fun feedbackBody(@Suppress("UNUSED_PARAMETER") context: android.content.Context) = "Hi RotRun team,\n\nProduct feedback:\n\nWhat I like / what I’d change:\n\n\n\n${diagnostics()}"
private fun contactBody(@Suppress("UNUSED_PARAMETER") context: android.content.Context) = "Hi RotRun team,\n\n\n\n${diagnostics()}"
