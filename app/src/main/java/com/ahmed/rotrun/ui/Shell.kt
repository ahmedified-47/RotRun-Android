package com.ahmed.rotrun.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ahmed.rotrun.data.DeviceAccessReader
import com.ahmed.rotrun.data.RotRunRepository
import com.ahmed.rotrun.data.UiPulse
import com.ahmed.rotrun.logic.BrainHealthLevel
import com.ahmed.rotrun.logic.PaywallGate
import com.ahmed.rotrun.logic.PlayCatalog
import com.ahmed.rotrun.logic.Products
import com.ahmed.rotrun.logic.Scoring
import kotlinx.coroutines.delay

private enum class Tab { Home, Block, Stats, Settings }

@Composable
fun RotRunRoot(repo: RotRunRepository) {
    val state by repo.state.collectAsState()
    val toast by repo.toast.collectAsState()
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2400)
            repo.clearToast()
        }
    }
    Box(Modifier.fillMaxSize()) {
        when {
            !state.onboardingCompleted -> OnboardingScreen(repo)
            else -> MainShell(repo)
        }
        if (toast != null) {
            Text(
                toast.orEmpty(),
                color = Color.White,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.82f)).padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        if (state.bailoutFrom.isNotBlank()) {
            LaunchedEffect(state.bailoutFrom) { delay(4000); repo.clearBailout() }
            Text(
                "${state.bailoutFrom} bailed you out. You owe them a coffee.",
                color = Color.White,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 96.dp).clip(CircleShape).background(Melt.House).padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun Gate(repo: RotRunRepository) {
    val state by repo.state.collectAsState()
    var plan by remember { mutableStateOf(Products.YEARLY) }
    var offer by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(rot().page).verticalScroll(rememberScrollState())) {
        if (offer && !state.skippedOneTimeOffer) {
            OneTimeOffer(
                onRedeem = { repo.purchase(Products.YEARLY70) },
                onOther = { repo.leaveOffer(); offer = false },
                showClose = true,
                onClose = { repo.leaveOffer(); offer = false },
            )
        } else {
            GetAccessPaywall(
                state = state,
                showClose = !state.skippedOneTimeOffer,
                showDiscountLink = !state.skippedOneTimeOffer,
                onClose = { offer = true },
                onDiscount = { offer = true },
                onPurchase = repo::purchase,
                onRestore = repo::restore,
                selected = plan,
                onSelect = { plan = it },
            )
        }
    }
}

@Composable
private fun MainShell(repo: RotRunRepository) {
    val state by repo.state.collectAsState()
    val usage by repo.usage.collectAsState()
    val refreshing by repo.refreshing.collectAsState()
    val installed by repo.installed.collectAsState()
    val batteryAsk by repo.batteryAsk.collectAsState()
    var tab by remember { mutableStateOf(Tab.Home) }
    var focusSheet by remember { mutableStateOf(false) }
    var audio by remember { mutableStateOf(false) }
    var talk by remember { mutableStateOf(false) }
    var upgrade by remember { mutableStateOf(false) }
    var offer by remember { mutableStateOf(false) }
    var plan by remember { mutableStateOf(Products.YEARLY) }
    val offerNow by androidx.compose.runtime.rememberUpdatedState(offer)
    val context = LocalContext.current
    val look = Looks.byId(state.theme)
    val level = BrainHealthLevel.fromLabel(state.brainHealth)
    val health = Scoring.displayedHealth(Scoring.drainHealth(usage.drainSeconds, level), state.focusMinutesToday(), level.restoreMinutes)
    val screenMinutes = (usage.todaySeconds / 60).toInt()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        repo.onForeground()
        if (com.ahmed.rotrun.logic.PaywallGate.shows && !repo.state.value.premiumActive() && !offerNow) upgrade = true
    }
    LaunchedEffect(Unit) {
        repo.pulses.collect { pulse ->
            when (pulse) {
                UiPulse.OpenFocusChooser -> focusSheet = true
                UiPulse.ShowAudioSlap -> audio = true
                UiPulse.OpenPaywall -> upgrade = true
                UiPulse.OpenChat -> talk = true
            }
        }
    }
    val scenicHome = tab == Tab.Home && sceneFor(state.theme) != null
    val canvas = if (tab == Tab.Home) look.canvas else rot().page
    val barColor = if (scenicHome) look.tab else rot().tab
    Box(Modifier.fillMaxSize().background(canvas)) {
        CompositionLocalProvider(LocalContentColor provides if (scenicHome) Color.White else rot().ink) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 132.dp)) {
            when (tab) {
                Tab.Home -> HomeScreen(state, usage, refreshing, repo::refreshUsage, { talk = true }) {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                Tab.Block -> BlockScreen(state, installed, repo)
                Tab.Stats -> StatsScreen(state, health, screenMinutes, repo)
                Tab.Settings -> SettingsScreen(state, installed, repo)
            }
        }
        }
        TabBar(
            tab = tab,
            bar = barColor,
            light = !rot().dark && !scenicHome,
            onHome = { tab = Tab.Home },
            onBlock = { tab = Tab.Block },
            onStats = { tab = Tab.Stats },
            onSettings = { tab = Tab.Settings },
            onFocus = { if (state.focus == null) focusSheet = true },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        if (focusSheet) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable { focusSheet = false })
            Box(Modifier.align(Alignment.BottomCenter).padding(12.dp).navigationBarsPadding()) {
                FocusChooser(onClose = { focusSheet = false }, repo = repo)
            }
        }
        state.focus?.let { FocusOverlay(it, repo) }
        if (talk) TalkSheet(repo) { talk = false }
        if (offer && PaywallGate.shows && !state.yearly70Dismissed) {
            Box(Modifier.fillMaxSize().background(rot().page).statusBarsPadding().verticalScroll(rememberScrollState())) {
                OneTimeOffer(
                    onRedeem = { repo.purchase(Products.YEARLY70) },
                    onOther = { repo.dismissYearly70(); offer = false },
                    showClose = true,
                    onClose = { repo.dismissYearly70(); offer = false },
                )
            }
        } else if (upgrade && PaywallGate.shows && !state.premiumActive()) {
            Box(Modifier.fillMaxSize().background(rot().page).statusBarsPadding().verticalScroll(rememberScrollState())) {
                GetAccessPaywall(
                    state = state,
                    showClose = true,
                    showDiscountLink = false,
                    onClose = {
                        upgrade = false
                        if (!state.yearly70Dismissed && PlayCatalog.quote(Products.YEARLY70) != null) offer = true
                    },
                    onDiscount = {},
                    onPurchase = repo::purchase,
                    onRestore = repo::restore,
                    selected = plan,
                    onSelect = { plan = it },
                )
            }
        }
        if (batteryAsk) {
            AlertDialog(
                onDismissRequest = repo::dismissBatteryAsk,
                title = { Text("Keep blocking on") },
                text = { Text("Allow RotRun to ignore battery optimisation so blocks survive a restart and stay up after you swipe the app away.") },
                confirmButton = {
                    TextButton(onClick = {
                        repo.dismissBatteryAsk()
                        DeviceAccessReader.open(context, DeviceAccessReader.batteryIntent(context))
                    }) { Text("Allow") }
                },
                dismissButton = { TextButton(onClick = repo::dismissBatteryAsk) { Text("Not now") } },
            )
        }
        if (audio) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                AudioSlap(state.cameoName.ifBlank { "a friend" }) { audio = false }
            }
        }
    }
}

private class CurvedTabShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val bump = with(density) { 36.dp.toPx() }
        val lift = bump - with(density) { 8.dp.toPx() }
        val corner = with(density) { 18.dp.toPx() }
        val inset = with(density) { 22.dp.toPx() }
        val shoulder = with(density) { 18.dp.toPx() }
        val w = size.width
        val h = size.height
        val mid = w / 2f
        val path = Path().apply {
            moveTo(0f, corner + lift)
            quadraticBezierTo(0f, lift, inset, lift)
            lineTo(mid - bump - shoulder, lift)
            cubicTo(mid - bump + with(density) { 2.dp.toPx() }, lift, mid - bump + with(density) { 4.dp.toPx() }, 0f, mid, 0f)
            cubicTo(mid + bump - with(density) { 4.dp.toPx() }, 0f, mid + bump - with(density) { 2.dp.toPx() }, lift, mid + bump + shoulder, lift)
            lineTo(w - inset, lift)
            quadraticBezierTo(w, lift, w, corner + lift)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
private fun TabBar(
    tab: Tab,
    bar: Color,
    light: Boolean,
    onHome: () -> Unit,
    onBlock: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier,
) {
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val item = if (light) Melt.Muted else Color.White.copy(alpha = 0.72f)
    val glyph = if (light) Color(0xFFF2F2F7) else Color.White.copy(alpha = 0.12f)
    Box(modifier.fillMaxWidth().height(36.dp + 88.dp + nav)) {
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(88.dp + nav).background(bar, CurvedTabShape()),
        )
        androidx.compose.foundation.layout.Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = nav, top = 28.dp).height(74.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabItem("Home", tab == Tab.Home, item, light, Modifier.weight(1f), onHome) {
                Glyph(if (tab == Tab.Home) Color.White else glyph) { Icon(Icons.Default.Home, null, tint = Color(0xFFF28C59)) }
            }
            TabItem("Block", tab == Tab.Block, item, light, Modifier.weight(1f), onBlock) {
                Glyph(if (tab == Tab.Block) Color(0xFFFFE8D2) else glyph) { Icon(Icons.Default.Lock, null, tint = if (tab == Tab.Block) Melt.Orange else Color(0xFF8C949E)) }
            }
            Text("Focus", color = item, fontSize = 11.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(top = 22.dp))
            TabItem("Stats", tab == Tab.Stats, item, light, Modifier.weight(1f), onStats) {
                Glyph(if (tab == Tab.Stats) Color.White else glyph) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.DateRange, null, tint = if (tab == Tab.Stats) Melt.Ink else Color(0xFF8C949E))
                        Text("31", color = if (tab == Tab.Stats) Melt.Ink else Color(0xFF8C949E), fontSize = 7.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(y = 2.dp))
                    }
                }
            }
            TabItem("Settings", tab == Tab.Settings, item, light, Modifier.weight(1f), onSettings) {
                Glyph(if (tab == Tab.Settings) Color.White else glyph) { Icon(Icons.Default.Settings, null, tint = if (tab == Tab.Settings) Melt.Ink else Color(0xFF8C949E)) }
            }
        }
        Box(
            Modifier.align(Alignment.TopCenter).size(74.dp).clip(CircleShape).background(Melt.Glow).clickable(onClick = onFocus),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(66.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                CartoonBrain(BrainStyle.Meadow, Modifier.size(58.dp), animated = false)
            }
        }
    }
}

@Composable
private fun Glyph(fill: Color, icon: @Composable () -> Unit) {
    Box(Modifier.size(34.dp).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) { icon() }
}

@Composable
private fun TabItem(
    label: String,
    selected: Boolean,
    color: Color,
    light: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val labelColor = if (selected && light) Melt.Ink else color
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
        Box(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), contentAlignment = Alignment.Center) { icon() }
        Text(label, color = labelColor, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}
