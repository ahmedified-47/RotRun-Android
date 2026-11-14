package com.ahmed.rotrun.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmed.rotrun.BuildConfig
import com.ahmed.rotrun.R
import com.ahmed.rotrun.data.DeviceAccessReader
import com.ahmed.rotrun.logic.NotificationPrompt
import com.ahmed.rotrun.data.RotRunRepository
import com.ahmed.rotrun.data.SavedState
import com.ahmed.rotrun.data.TrackedApp
import com.ahmed.rotrun.logic.BillingQuotes
import com.ahmed.rotrun.logic.BrainHealthLevel
import com.ahmed.rotrun.logic.Products
import com.ahmed.rotrun.logic.SupportCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(state: SavedState, installed: List<TrackedApp>, repo: RotRunRepository) {
    var page by rememberSaveable { mutableStateOf("root") }
    var showPlans by rememberSaveable { mutableStateOf(false) }
    var plan by remember { mutableStateOf(state.productId ?: Products.YEARLY) }
    var authError by remember { mutableStateOf(false) }
    var showDisclosure by remember { mutableStateOf(false) }
    val access by repo.access.collectAsState()
    val context = LocalContext.current
    var askedNotifications by rememberSaveable { mutableStateOf(false) }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        askedNotifications = true
        repo.refreshAccess()
        val rationale = (context as? Activity)?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true
        if (!granted && NotificationPrompt.afterDenial(rationale) == NotificationPrompt.SETTINGS) {
            DeviceAccessReader.open(context, DeviceAccessReader.notificationIntent(context))
        }
    }
    BackHandler(enabled = !showPlans && (page != "root" || showDisclosure || authError)) {
        when {
            authError -> authError = false
            showDisclosure -> showDisclosure = false
            page == "help" || page == "feedback" || page == "contact" || page == "subscription" -> page = "support"
            else -> page = "root"
        }
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
        "subscription" -> PremiumManage(state, repo, onBack = { page = "support" }, onChangePlan = { showPlans = true })
        else -> RootSettings(
            state = state,
            access = access,
            repo = repo,
            open = { page = it },
            onUsage = { DeviceAccessReader.open(context, DeviceAccessReader.usageIntent(context)) },
            onAccessibility = { showDisclosure = true },
            onNotifications = {
                val runtimeGranted = Build.VERSION.SDK_INT < 33 ||
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                val rationale = (context as? Activity)?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true
                val next = NotificationPrompt.next(runtimeGranted, rationale, askedNotifications)
                if (runtimeGranted || next == NotificationPrompt.SETTINGS) {
                    DeviceAccessReader.open(context, DeviceAccessReader.notificationIntent(context))
                } else {
                    askedNotifications = true
                    askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onOverlay = { DeviceAccessReader.open(context, DeviceAccessReader.overlayIntent(context)) },
            onBattery = { DeviceAccessReader.open(context, DeviceAccessReader.batteryIntent(context)) },
        )
    }
    if (showPlans) {
        val notice by repo.purchaseError.collectAsState()
        PaywallPopup(onDismiss = { showPlans = false }) {
            GetAccessPaywall(
                state, showClose = true, onClose = { showPlans = false },
                onPurchase = repo::purchase, onRestore = repo::restore, onRetry = repo::refreshBilling,
                selected = plan, onSelect = { plan = it }, notice = notice,
                signedInUserId = repo.backend.userId,
            )
        }
    }
    if (page == "brain") BrainHealthPage(state, repo) { page = "root" }
    if (page == "excluded") ExcludedPage(state, installed, repo) { page = "root" }
    if (showDisclosure) {
        AccessibilityDisclosureDialog(
            onDecline = { showDisclosure = false },
            onAccept = {
                showDisclosure = false
                runCatching { context.startActivity(DeviceAccessReader.accessibilityIntent()) }
                    .onFailure { authError = true }
            },
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
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    Column(
        Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        SettingsHeader(state.theme)
        SettingsGroup {
            RowLink("Brain Health", state.brainHealth, Icons.Default.Favorite, Color(0xFFFF3B30)) { open("brain") }
            Hairline()
            RowLink("Excluded Apps", icon = Icons.Default.Diamond, tint = Color(0xFF59B8F2)) { open("excluded") }
        }
        SettingsGroup {
            RowLink("Intervention Style", icon = Icons.AutoMirrored.Filled.Chat, tint = Color(0xFF598CF2)) { open("styles") }
            Hairline()
            RowLink("Rewards", icon = Icons.Default.CardGiftcard, tint = Color(0xFFF28C33)) { open("rewards") }
            Hairline()
            RowLink("App Icon", icon = Icons.Default.Apps, tint = Color(0xFF738CF2)) { open("icon") }
        }
        SettingsGroup {
            RowLink("Support & Feedback", icon = Icons.AutoMirrored.Filled.Help, tint = Color(0xFFF25973)) { open("support") }
            Hairline()
            RowLink("Home Screen Widget", icon = Icons.Default.GridView, tint = Color(0xFF598CF2)) { open("widget") }
        }
        PermissionColumn(access, onUsage, onAccessibility, onNotifications, onOverlay, onBattery)
        SettingsGroup {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("Upgrade reminders", color = chrome.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "One local note a day from Rot about RotRun Premium. Off until you turn this on. RotRun will not ask for notification permission just to send these.",
                        color = chrome.muted,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    GreenSwitch(state.upgradeReminders) { enabled ->
                        repo.update { it.copy(upgradeReminders = enabled) }
                        com.ahmed.rotrun.watch.UpgradeReminders.sync(context, repo.state.value, repo.backend.userId)
                    }
                }
            }
        }
        SettingsGroup {
            RowLink("Rot's memory", icon = Icons.Default.Refresh, tint = chrome.blue) { open("memory") }
        }
        if (repo.backend.signedIn) {
            SettingsGroup {
                Text(
                    "Delete account",
                    color = Color(0xFFFF3B30),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !deleting) { confirmDelete = true }.padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
            if (confirmDelete) {
                AlertDialog(
                    onDismissRequest = { if (!deleting) confirmDelete = false },
                    title = { Text("Delete account") },
                    text = {
                        Text("This permanently deletes your account, chat history, memories, stats, and leaderboard entry. Subscriptions must be cancelled in Google Play separately.")
                    },
                    confirmButton = {
                        TextButton(
                            enabled = !deleting,
                            onClick = {
                                deleting = true
                                scope.launch {
                                    val error = withContext(Dispatchers.IO) { repo.backend.deleteAccount() }
                                    if (error != null) {
                                        deleting = false
                                        confirmDelete = false
                                        repo.toast(error)
                                    } else {
                                        repo.backend.signOut()
                                        confirmDelete = false
                                        repo.update { it.copy(onboardingCompleted = false, onboardingStep = 0) }
                                    }
                                }
                            },
                        ) { Text("Delete account", color = Color(0xFFFF3B30)) }
                    },
                    dismissButton = {
                        TextButton(enabled = !deleting, onClick = { confirmDelete = false }) { Text("Cancel") }
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsHeader(theme: String) {
    val chrome = rot()
    val look = Looks.byId(theme)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 8.dp)) {
        Box(
            Modifier
                .size(92.dp)
                .shadow(8.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
                .clip(CircleShape)
                .background(Brush.verticalGradient(themePreview(theme, chrome)))
                .border(4.dp, chrome.page, CircleShape),
            contentAlignment = Alignment.Center,
        ) { CartoonBrain(look.brain, Modifier.size(78.dp), animated = false) }
        Text("Settings", color = chrome.ink, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
    }
}

/** Same stops as iOS AppThemeOption.previewGradient. */
private fun themePreview(theme: String, chrome: RotChrome): List<Color> = when (theme) {
    "Default" -> listOf(chrome.inset, chrome.card)
    "Sunrise" -> listOf(Color(0xFFFFB861), Color(0xFFFA6B47))
    "Zen" -> listOf(Color(0xFFB8E0DB), Color(0xFF6BAD94))
    "Twilight" -> listOf(Color(0xFF9E7AE0), Color(0xFF472E7A))
    "Cosmic" -> listOf(Color(0xFF382E85), Color(0xFF0F1438))
    else -> listOf(Color(0xFF8CD1ED), Color(0xFF3DB370))
}

@Composable
private fun PermissionColumn(
    access: com.ahmed.rotrun.data.DeviceAccess,
    onUsage: () -> Unit,
    onAccessibility: () -> Unit,
    onNotifications: () -> Unit,
    onOverlay: () -> Unit,
    onBattery: () -> Unit,
) {
    SettingsGroup {
        PermissionRow("Usage Access", "Which app is open, today’s time, and daily limits.", access.usage, onUsage)
        PermissionRule()
        PermissionRow("Accessibility", "Shows the Rot shield over apps you chose. RotRun does not read what you type.", access.accessibility, onAccessibility)
        PermissionRule()
        PermissionRow("Notifications", "The shield can still open if the overlay is off.", access.notifications, onNotifications)
        PermissionRule()
        PermissionRow("Display over other apps", "Fallback shield when Accessibility is off.", access.overlay, onOverlay)
        PermissionRule()
        PermissionRow("Battery", "Keep blocking after a restart and when RotRun is swiped away.", access.batteryUnrestricted, onBattery)
    }
}

@Composable
private fun PermissionRule() {
    Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp).background(rot().line))
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
    Box(Modifier.padding(start = 56.dp).fillMaxWidth().height(1.dp).background(rot().line))
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
                        repo.publishDailyXp()
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
private fun SupportPage(open: (String) -> Unit) {
    var legal by remember { mutableStateOf<String?>(null) }
    LegalDialog(legal) { legal = null }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        BackHeader(
            "Support & Feedback",
            { open("root") },
            "Need help? Reach out or leave us feedback!",
        )
        Column(Modifier.padding(horizontal = 20.dp)) {
            SettingsGroup {
                RowLink("Help", icon = Icons.Default.QuestionMark, tint = Color(0xFFF25980)) { open("help") }
                Hairline()
                RowLink("Feedback / Bug Report", icon = Icons.Default.Warning, tint = Color(0xFFFF9F0A)) { open("feedback") }
                Hairline()
                RowLink("Contact Us", icon = Icons.AutoMirrored.Filled.Chat, tint = Color(0xFF73B3FA)) { open("contact") }
                Hairline()
                RowLink("Privacy Policy", icon = Icons.Default.Lock, tint = Color(0xFFFF9F0A)) { legal = "privacy" }
                Hairline()
                RowLink("Terms & Conditions", icon = Icons.Default.Search, tint = Color(0xFF8C8CF2)) { legal = "terms" }
                Hairline()
                RowLink("Manage Subscription", icon = Icons.Default.Paid, tint = Color(0xFFF2BF33)) { open("subscription") }
            }
        }
        Text("Follow us", color = rot().ink, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally)) {
            Icon(Icons.Default.Close, contentDescription = "X", tint = rot().ink, modifier = Modifier.size(22.dp))
            Icon(Icons.Default.PhotoCamera, contentDescription = "Camera", tint = rot().ink, modifier = Modifier.size(22.dp))
            Icon(Icons.Default.MusicNote, contentDescription = "Music", tint = rot().ink, modifier = Modifier.size(22.dp))
        }
        Text(versionLine(), color = rot().muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center)
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
        Text("RotRun", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
private fun BackHeader(title: String, onBack: () -> Unit, subtitle: String? = null) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(rot().card).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Text("‹", color = rot().ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
        Text(title, color = rot().ink, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        if (subtitle != null) {
            Text(subtitle, color = rot().muted, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun CardGroup(content: @Composable () -> Unit) {
    Column(Modifier.padding(vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(rot().card)) { content() }
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(rot().card)) { content() }
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
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
        }
        Text(title, color = chrome.ink, modifier = Modifier.padding(start = if (icon != null) 12.dp else 0.dp).weight(1f), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        if (value != null) Text(value, color = chrome.muted, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text("›", color = chrome.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun BoxButton(title: String, onClick: () -> Unit) {
    Text(title, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth().clip(CircleShape).background(rot().blue).clickable(onClick = onClick).padding(vertical = 16.dp))
}

@Composable
private fun PremiumManage(state: SavedState, repo: RotRunRepository, onBack: () -> Unit, onChangePlan: () -> Unit) {
    BackHeader("RotRun Premium", onBack)
    Column(Modifier.padding(horizontal = 16.dp)) {
        SubscriptionSection(state, repo, onChangePlan)
    }
}

@Composable
private fun SubscriptionSection(state: SavedState, repo: RotRunRepository, onChangePlan: () -> Unit) {
    val context = LocalContext.current
    val chrome = rot()
    val now = System.currentTimeMillis()
    val shows = BillingQuotes.showsPlayPlan(repo.backend.userId, state.grantUserId, state.premiumActive())
    val plan = BillingQuotes.planTitle(shows, state.productId)
    val renewalText = "Renews ${java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(state.premiumUntil))}"
    val renews = BillingQuotes.statusLine(shows, state.lifetime, state.premiumUntil, now, state.productId, renewalText)
    Text(
        "Buy, restore, or manage your plan. RotRun stays open while your subscription is active.",
        color = chrome.muted,
        fontSize = 15.sp,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
    Column(Modifier.padding(vertical = 12.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(chrome.card).padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CartoonBrain(BrainStyle.Meadow, Modifier.size(88.dp), animated = false)
        Text(plan, color = chrome.ink, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.padding(top = 12.dp))
        Text(renews, color = chrome.muted, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp))
    }
    CardGroup {
        RowLink(if (shows) "Change Plan" else "Get Premium", icon = Icons.Default.Star, tint = Color(0xFFF2BF33), onClick = onChangePlan)
        Hairline()
        RowLink("Manage Subscription", plan, icon = Icons.Default.Paid, tint = Color(0xFFF2BF33)) {
            val product = state.productId
            val uri = android.net.Uri.parse(
                if (product.isNullOrBlank()) "https://play.google.com/store/account/subscriptions?package=${context.packageName}"
                else "https://play.google.com/store/account/subscriptions?sku=$product&package=${context.packageName}",
            )
            runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri)) }
        }
        Hairline()
        RowLink("Restore Purchases", icon = Icons.Default.Refresh, tint = Color(0xFF598CF2), onClick = repo::restore)
    }
    if (!shows) {
        Text(
            "You're not subscribed. Get Premium to start a plan, or Restore Purchases if you already bought one on this Google account.",
            color = chrome.muted,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
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
