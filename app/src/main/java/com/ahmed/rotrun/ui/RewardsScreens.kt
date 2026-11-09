package com.ahmed.rotrun.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmed.rotrun.data.RotRunRepository
import com.ahmed.rotrun.data.SavedState
import com.ahmed.rotrun.data.UsageSnapshot
import com.ahmed.rotrun.icon.LauncherIcons
import com.ahmed.rotrun.logic.BrainHealthLevel
import com.ahmed.rotrun.logic.RewardSceneTint
import com.ahmed.rotrun.logic.Scoring
import com.ahmed.rotrun.logic.WidgetGlyph
import com.ahmed.rotrun.logic.WidgetGlyphs
import com.ahmed.rotrun.R
import kotlin.math.roundToInt

private val SkyTop = Color(0xFFBDE6FA)
private val SkyBottom = Color(0xFFDBF2FC)
private val Ink = Color(0xFF1A2133)
private val StreakInk = Color(0xFF2E4761)
private val StatusInk = Color(0xFF38526B)
private val ActionBlue = Color(0xFF217AFF)
private val LockedLabel = Color(0x4D3C3C43)
private val Bezel = Color(0xFF1F2129)

private val sky = Brush.verticalGradient(listOf(SkyTop, SkyBottom))

@Composable
fun RewardsPage(state: SavedState, repo: RotRunRepository, onBack: () -> Unit) {
    LaunchedEffect(state.streak) { repo.recheckRewards() }
    val usage by repo.usage.collectAsState()
    val next = Looks.rewards.firstOrNull { it.days != null && state.streak < it.days }
    val line = if (next?.days == null) {
        "${state.streak}-day streak. Every world is unlocked."
    } else {
        "${state.streak}-day streak. Next world at ${next.days} days."
    }
    Column(Modifier.fillMaxWidth().background(sky).padding(top = 8.dp, bottom = 40.dp)) {
        RewardBack(onBack)
        Text(
            "Streaks unlock\nnew worlds.",
            color = Ink,
            fontSize = 36.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 40.sp,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp),
        )
        Text(
            line,
            color = StreakInk,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 8.dp),
        )
        Row(
            Modifier.padding(top = 18.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Looks.all.forEach { look ->
                WorldPhoneCard(
                    look = look,
                    streak = state.streak,
                    selected = state.theme == look.id,
                    usage = usage,
                    state = state,
                    onChoose = { repo.update { it.copy(theme = look.id) } },
                )
            }
        }
        Text(
            "App icons",
            color = Ink,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 26.dp),
        )
        Row(
            Modifier.padding(top = 18.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Looks.icons.forEach { (name, need) ->
                RewardIconCard(name, need, state.streak, state.appIcon == name, fill = false) {
                    repo.update { it.copy(appIcon = name) }
                }
            }
        }
    }
}

@Composable
fun IconPage(state: SavedState, repo: RotRunRepository, onBack: () -> Unit) {
    LaunchedEffect(state.streak) { repo.recheckRewards() }
    Column(Modifier.fillMaxWidth().background(sky).padding(top = 8.dp, bottom = 40.dp)) {
        RewardBack(onBack)
        Text(
            "App icons",
            color = Ink,
            fontSize = 36.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 16.dp),
        )
        Text(
            "Icon changes show on your home screen and may take a moment to appear.",
            color = StreakInk,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 16.dp),
        )
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Looks.icons.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { (name, need) ->
                        RewardIconCard(name, need, state.streak, state.appIcon == name, fill = true, modifier = Modifier.weight(1f)) {
                            repo.update { it.copy(appIcon = name) }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RewardBack(onBack: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 20.dp).size(36.dp).clip(CircleShape).background(Color.White).clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Text("‹", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
    }
}

@Composable
private fun WorldPhoneCard(
    look: HomeLook,
    streak: Int,
    selected: Boolean,
    usage: UsageSnapshot,
    state: SavedState,
    onChoose: () -> Unit,
) {
    val unlocked = look.streak == null || streak >= look.streak
    val reward = Looks.rewards.firstOrNull { it.themeId == look.id }
    val status = when {
        reward?.days != null && !unlocked -> "Unlocks at a ${reward.days}-day streak"
        selected -> "On your Home screen"
        else -> if (reward?.days != null) reward.summary else "Your starting world"
    }
    Column(Modifier.width(286.dp).clickable(enabled = unlocked, onClick = onChoose)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(520.dp)
                .shadow(16.dp, RoundedCornerShape(36.dp), clip = false),
        ) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(36.dp)).background(Bezel)) {
                Box(Modifier.padding(7.dp).fillMaxSize().clip(RoundedCornerShape(30.dp))) {
                    WorldPhoneScreen(look, usage, state)
                    if (!unlocked) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Lock, contentDescription = "Locked", tint = Color.White, modifier = Modifier.size(22.dp))
                                if (reward?.days != null) {
                                    Text("${reward.days} days", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                                }
                            }
                        }
                    }
                }
            }
            if (selected) {
                Box(Modifier.fillMaxSize().border(4.dp, ActionBlue, RoundedCornerShape(38.dp)))
            }
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(look.title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(status, color = StatusInk, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 2.dp))
            }
            if (unlocked) RewardRadio(selected)
        }
    }
}

@Composable
private fun WorldPhoneScreen(look: HomeLook, usage: UsageSnapshot, state: SavedState) {
    val scene = RewardSceneTint.resolve(look.id)
    val level = BrainHealthLevel.fromLabel(state.brainHealth)
    val health = Scoring.shownHealth(usage.authorized, usage.drainSeconds, state.focusMinutesToday(), level)
    val known = if (usage.todaySeconds >= 60L) health else null
    val screen = if (known == null) "—" else Scoring.formatDuration(usage.todaySeconds)
    val pickups = if (usage.pickups > 0) usage.pickups.toString() else "—"
    val focus = Scoring.formatDuration(state.focusMinutesToday() * 60L)
    val offenders = usage.offenders.filter { it.seconds > 0 }.take(2)
    val header = if (scene.system) Ink else Color(RewardSceneTint.label(scene.lightInk))
    val percent = if (scene.system) Ink else Color(RewardSceneTint.label(scene.lightPercent))
    val onCard = if (scene.system) Ink else Color(RewardSceneTint.label(scene.lightInk))
    Box(Modifier.fillMaxSize()) {
        if (scene.system) {
            Box(Modifier.fillMaxSize().background(Color.White))
        } else {
            Box(Modifier.fillMaxSize().background(Color(scene.canvas)))
            HeroBackdrop(look.id, Modifier.fillMaxSize())
        }
        Column(Modifier.fillMaxSize().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("RotRun", color = header, fontSize = 16.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                Box(Modifier.size(24.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = Color(0xFFFF8C29), modifier = Modifier.size(14.dp))
                }
            }
            CartoonBrain(look.brain, Modifier.padding(top = 8.dp).size(118.dp), animated = false)
            Text(
                if (known == null) "Not in yet" else "$known%",
                color = if (known == null) header else percent,
                fontSize = if (known == null) 22.sp else 42.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(top = 4.dp),
            )
            Box(
                Modifier.padding(horizontal = 28.dp, vertical = 8.dp).fillMaxWidth().height(8.dp).clip(CircleShape).background(header.copy(alpha = 0.22f)),
            ) {
                if (known != null) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth((known / 100f).coerceIn(0.04f, 1f)).background(header))
                }
            }
            Row(Modifier.padding(horizontal = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WorldStat(screen, "Screen", onCard, Color(scene.cardTop), Modifier.weight(1f))
                WorldStat(pickups, "Pickups", onCard, Color(scene.cardTop), Modifier.weight(1f))
                WorldStat(focus, "Focus", onCard, Color(scene.cardTop), Modifier.weight(1f))
            }
            if (offenders.isNotEmpty()) {
                Column(
                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(scene.cardBottom)).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Top Offenders", color = onCard, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    offenders.forEach { app ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OffenderMark(app.label, app.packageName)
                            Text(
                                app.label,
                                color = onCard,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            )
                            Text(Scoring.formatDuration(app.seconds), color = onCard.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorldStat(value: String, title: String, ink: Color, fill: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(fill).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(title, color = ink.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OffenderMark(label: String, packageName: String) {
    val glyph = WidgetGlyphs.forApp(label, packageName)
    val background = when (glyph) {
        WidgetGlyph.Instagram -> R.drawable.glyph_instagram
        WidgetGlyph.WhatsApp -> R.drawable.glyph_whatsapp
        WidgetGlyph.Maps -> R.drawable.glyph_maps
        WidgetGlyph.AppStore -> R.drawable.glyph_appstore
        WidgetGlyph.Screenshots, WidgetGlyph.Other -> R.drawable.glyph_other
    }
    // These files are <shape> gradients. painterResource only accepts vectors and bitmaps,
    // and that throw is what closed Rewards as soon as an offender row was composed.
    val context = LocalContext.current
    val drawable = remember(background) { context.getDrawable(background)?.mutate() }
    Canvas(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))) {
        val mark = drawable ?: return@Canvas
        mark.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
        drawIntoCanvas { canvas -> mark.draw(canvas.nativeCanvas) }
    }
}

@Composable
private fun RewardIconCard(
    name: String,
    need: Int?,
    streak: Int,
    selected: Boolean,
    fill: Boolean,
    modifier: Modifier = Modifier,
    onChoose: () -> Unit,
) {
    val unlocked = need == null || streak >= need
    val status = when {
        !unlocked -> "Unlock at ${need ?: 0} days"
        selected -> "On your Home Screen"
        else -> "Unlocked"
    }
    Column(modifier.clickable(enabled = unlocked, onClick = onChoose)) {
        Box(
            Modifier
                .then(if (fill) Modifier.fillMaxWidth() else Modifier.width(148.dp))
                .height(148.dp)
                .clip(RoundedCornerShape(28.dp)),
        ) {
            Image(
                painterResource(LauncherIcons.preview(name)),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (!unlocked) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Lock, contentDescription = "Locked", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            if (selected) {
                Box(Modifier.fillMaxSize().border(3.dp, ActionBlue, RoundedCornerShape(28.dp)))
            }
        }
        Text(
            name,
            color = if (unlocked) Ink else LockedLabel,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(status, color = StatusInk, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Start)
    }
}

@Composable
private fun RewardRadio(selected: Boolean) {
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        if (selected) {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(ActionBlue), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        } else {
            Box(Modifier.fillMaxSize().border(2.dp, Color(0xFFC7C7CC), CircleShape))
        }
    }
}
