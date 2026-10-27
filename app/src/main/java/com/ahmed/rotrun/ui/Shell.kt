package com.ahmed.rotrun.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ahmed.rotrun.data.RotRunRepository
import com.ahmed.rotrun.data.UiPulse
import com.ahmed.rotrun.logic.BrainHealthLevel
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
            !state.premiumActive() -> Gate(repo)
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
    Column(Modifier.fillMaxSize().background(Melt.Grouped).verticalScroll(rememberScrollState())) {
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
    var tab by remember { mutableStateOf(Tab.Home) }
    var focusSheet by remember { mutableStateOf(false) }
    var couch by remember { mutableStateOf(false) }
    var audio by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val look = Looks.byId(state.theme)
    val level = BrainHealthLevel.fromLabel(state.brainHealth)
    val health = Scoring.displayedHealth(Scoring.drainHealth(usage.drainSeconds, level), state.focusMinutesToday(), level.restoreMinutes)
    val screenMinutes = (usage.todaySeconds / 60).toInt()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { repo.refreshUsage() }
    LaunchedEffect(Unit) {
        repo.pulses.collect { pulse ->
            when (pulse) {
                UiPulse.OpenFocusChooser -> focusSheet = true
                UiPulse.ShowAudioSlap -> audio = true
            }
        }
    }
    val canvas = when (tab) {
        Tab.Home -> look.canvas
        Tab.Stats -> Color(0xFFFDE6C7)
        else -> Melt.Grouped
    }
    Box(Modifier.fillMaxSize().background(canvas)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 120.dp)) {
            when (tab) {
                Tab.Home -> HomeScreen(state, usage, refreshing, repo::refreshUsage) {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                Tab.Block -> BlockScreen(state, installed, repo)
                Tab.Stats -> StatsScreen(state, health, screenMinutes, repo)
                Tab.Settings -> SettingsScreen(state, installed, repo)
            }
        }
        TabBar(
            tab = tab,
            meadow = tab == Tab.Home && !look.light,
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
                FocusChooser(onClose = { focusSheet = false }, onCouch = { focusSheet = false; couch = true }, repo = repo)
            }
        }
        state.focus?.let { FocusOverlay(it, repo) }
        if (couch) CouchReset(onClose = { couch = false }, repo = repo)
        if (audio) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                AudioSlap(state.cameoName.ifBlank { "a friend" }) { audio = false }
            }
        }
    }
}

@Composable
private fun TabBar(
    tab: Tab,
    meadow: Boolean,
    onHome: () -> Unit,
    onBlock: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier,
) {
    val bar = if (meadow) Melt.Tab else Color.White
    val item = if (meadow) Color.White else Melt.Muted
    Box(modifier.fillMaxWidth().navigationBarsPadding()) {
        Box(
            Modifier.align(Alignment.TopCenter).padding(bottom = 8.dp).clip(CircleShape).background(Melt.Glow).padding(4.dp).clickable(onClick = onFocus),
        ) { CartoonBrain(BrainStyle.Spa, Modifier.size(58.dp), animated = false) }
        RowUnder(bar, item, tab, onHome, onBlock, onStats, onSettings)
    }
}

@Composable
private fun RowUnder(bar: Color, item: Color, tab: Tab, onHome: () -> Unit, onBlock: () -> Unit, onStats: () -> Unit, onSettings: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.padding(top = 28.dp).fillMaxWidth().height(78.dp).background(bar).padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabItem("Home", tab == Tab.Home, item, Modifier.weight(1f), onHome) { Icon(Icons.Default.Home, null, tint = Melt.House) }
        TabItem("Block", tab == Tab.Block, item, Modifier.weight(1f), onBlock) { Icon(Icons.Default.Lock, null, tint = Color(0xFF8E9399)) }
        Text("Focus", color = item, fontSize = 11.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(top = 28.dp))
        TabItem("Stats", tab == Tab.Stats, item, Modifier.weight(1f), onStats) { Icon(Icons.Default.DateRange, null, tint = Color(0xFF8E9399)) }
        TabItem("Settings", tab == Tab.Settings, item, Modifier.weight(1f), onSettings) { Icon(Icons.Default.Settings, null, tint = Color(0xFF8E9399)) }
    }
}

@Composable
private fun TabItem(label: String, selected: Boolean, color: Color, modifier: Modifier, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Column(modifier.clickable(onClick = onClick).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        icon()
        Text(label, color = color, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}
