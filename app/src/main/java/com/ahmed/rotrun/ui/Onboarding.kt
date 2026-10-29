package com.ahmed.rotrun.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ahmed.rotrun.data.AppList
import com.ahmed.rotrun.data.OnboardingStep
import com.ahmed.rotrun.data.RotRunRepository
import com.ahmed.rotrun.data.SavedState
import com.ahmed.rotrun.data.TrackedApp
import com.ahmed.rotrun.data.UiPulse
import com.ahmed.rotrun.logic.BlockRules
import com.ahmed.rotrun.logic.Marketing
import com.ahmed.rotrun.logic.PaywallGate
import com.ahmed.rotrun.logic.Products
import com.ahmed.rotrun.logic.Scoring
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(repo: RotRunRepository) {
    val state by repo.state.collectAsState()
    val installed by repo.installed.collectAsState()
    val step = OnboardingStep.fromRaw(state.onboardingStep)
    var plan by remember { mutableStateOf(Products.YEARLY) }
    var legal by remember { mutableStateOf<String?>(null) }
    var upgrade by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        repo.pulses.collect { pulse ->
            if (pulse == UiPulse.OpenPaywall) upgrade = true
        }
    }
    val context = LocalContext.current
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { repo.advance() }
    val catalog by com.ahmed.rotrun.logic.PlayCatalog.status.collectAsState()
    androidx.compose.runtime.LaunchedEffect(catalog, state.onboardingStep, state.premium, state.yearly70Dismissed) {
        if (step.skipped(repo.state.value)) repo.advance()
    }
    if (legal != null) {
        LegalPage(if (legal == "terms") "terms" else "privacy", if (legal == "terms") "Terms & Conditions" else "Privacy Policy") {
            legal = null
        }
        return
    }
    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        OnboardingBackdrop(step)
        when (step) {
            OnboardingStep.Persona -> Choices(step, "Which best describes you?", state.persona, personaOptions, false, repo)
            OnboardingStep.ScreenTimeAffect -> Choices(step, "How does screen time affect you the most?", state.screenTimeAffect, affectOptions, true, repo)
            OnboardingStep.TriedReduce -> Choices(step, "Have you tried to reduce screen time before?", state.triedReduce, triedOptions, true, repo)
            OnboardingStep.RotMessage -> Story(step, "The more you rot, the more your brain rots.", mascot = BrainStyle.Meadow, repo = repo, ground = true)
            OnboardingStep.SeeForYourself -> Reveal(state, repo)
            OnboardingStep.LoseControl -> Choices(step, "When do you usually lose control? (rot the most)", state.loseControl, loseOptions, true, repo)
            OnboardingStep.ThisWeekHelp -> WeekHelp(repo)
            OnboardingStep.WelcomeBrand -> Story(step, "Welcome to RotRun!", "It's time to regain control of your screen time.", "Let's go!", BrainStyle.Meadow, repo, brainSize = 180)
            OnboardingStep.GoalWithApp -> Choices(step, "What is your goal with RotRun?", state.goal, goalOptions, true, repo)
            OnboardingStep.PersonalizeIntro -> Story(step, "Let's personalize RotRun for you.", "Your answers help us map your attention patterns.", "Let's do it!", BrainStyle.Mug, repo, brainSize = 170)
            OnboardingStep.Personalizing -> Personalizing(repo)
            OnboardingStep.MeetBrain -> Story(step, "Meet your brain", "He's doing OK.", mascot = BrainStyle.Meadow, repo = repo, ground = true, brainSize = 170)
            OnboardingStep.Age -> Choices(step, "How old are you?", state.ageRange, ageOptions, true, repo)
            OnboardingStep.AttentionProfile -> Attention(state, repo)
            OnboardingStep.DailyEstimate -> Hours(state, repo, goal = false)
            OnboardingStep.Committed -> Committed(repo)
            OnboardingStep.BeforeAfter -> BeforeAfter(repo)
            OnboardingStep.HoldToAscend -> Hold(repo)
            OnboardingStep.NotAddicted -> NotAddicted(repo)
            OnboardingStep.DidYouKnow -> DidYouKnow(repo)
            OnboardingStep.SocialProof -> SocialProof(repo)
            OnboardingStep.CarouselWelcome, OnboardingStep.CarouselThemes, OnboardingStep.CarouselFocus -> Carousel(step, { legal = it }, repo)
            OnboardingStep.PeakFocus -> Choices(step, "Pick your peak focus time!", state.peakFocus, peakOptions, false, repo, "This is the time where you want to block the most distracting apps.", chrome = false)
            OnboardingStep.Notifications -> Notifications {
                if (Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS) else repo.advance()
            }
            OnboardingStep.RulePreview -> RulePreview(state, repo)
            OnboardingStep.GetAccess -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                GetAccessPaywall(
                    state = state,
                    showClose = !state.skippedOneTimeOffer,
                    showDiscountLink = !state.skippedOneTimeOffer,
                    onClose = repo::declineAccess,
                    onDiscount = repo::declineAccess,
                    onPurchase = repo::purchase,
                    onRestore = repo::restore,
                    selected = plan,
                    onSelect = { plan = it },
                    onTerms = { legal = "terms" },
                    onPrivacy = { legal = "privacy" },
                )
            }
            OnboardingStep.OneTimeOffer -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                OneTimeOffer(
                    onRedeem = { repo.purchase(Products.YEARLY70) },
                    onOther = repo::leaveOffer,
                    showClose = true,
                    onClose = repo::leaveOffer,
                    onTerms = { legal = "terms" },
                    onPrivacy = { legal = "privacy" },
                )
            }
            OnboardingStep.SetGoal -> Hours(state, repo, goal = true)
            OnboardingStep.ConnectScreenTime -> ConnectEdu(repo)
            OnboardingStep.PurchaseComplete -> PurchaseComplete(state, repo)
            OnboardingStep.ThemeUnlocked -> ThemeUnlocked(repo)
            OnboardingStep.WidgetHowTo -> WidgetSteps(repo)
            OnboardingStep.StreakReward -> StreakReward(repo)
            OnboardingStep.SelectApps -> SelectApps(state, installed, repo)
            OnboardingStep.ScreenTimeDialog -> ScreenDialog(repo) {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            OnboardingStep.EnableFirstRule -> EnableRule(repo)
            OnboardingStep.LimitedOffer, OnboardingStep.ChoosePlan -> {
                LaunchedEffect(step) { repo.advance() }
            }
        }
        val plansAlreadyShowing = step == OnboardingStep.GetAccess || step == OnboardingStep.OneTimeOffer
        if (upgrade && PaywallGate.shows && !state.premiumActive() && !plansAlreadyShowing) {
            Column(Modifier.fillMaxSize().background(Color.White).verticalScroll(rememberScrollState())) {
                GetAccessPaywall(
                    state = state,
                    showClose = true,
                    showDiscountLink = false,
                    onClose = { upgrade = false },
                    onDiscount = { upgrade = false },
                    onPurchase = repo::purchase,
                    onRestore = repo::restore,
                    selected = plan,
                    onSelect = { plan = it },
                )
            }
        }
    }
}

private val personaOptions = listOf("Builder / Creator" to "⚙️", "Knowledge worker" to "🔍", "Founder / Entrepreneur" to "✈️", "Sports / Athlete" to "⭐", "Student" to "📚", "Just trying to live better" to "❤️")
private val affectOptions = listOf("No focus / procrastination" to "🌀", "Anxiety / overstimulation" to "👀", "Bad sleep" to "🌙", "Productivity loss" to "📉", "I feel mentally fried" to "🔥", "Less time with friends / family" to "👥")
private val triedOptions = listOf("Yes, didn't stick" to "↩", "Yes, briefly worked" to "✓", "No, first time" to "✦")
private val loseOptions = listOf("Mornings" to "🌅", "During the day" to "☀️", "Evenings" to "🌆", "All day" to "🔁", "Not sure" to "？")
private val goalOptions = listOf("Improve focus" to "🎯", "Reduce mindless scrolling" to "📱", "Sleep better" to "😴", "Be more present" to "🌿", "Be more productive" to "⚡", "Just curious" to "✨")
private val ageOptions = listOf("Under 18" to "", "18-24" to "", "25-34" to "", "35-44" to "", "45-54" to "", "55+" to "")
private val peakOptions = listOf("Mindful Morning" to "⏰", "Work Focus" to "⚙️", "Evening Wind Down" to "🌙")

@Composable
private fun OnboardingBackdrop(step: OnboardingStep) {
    when (step) {
        OnboardingStep.Committed -> Sunburst(Color(0xFF6BBDE6), 0.12f)
        OnboardingStep.ThemeUnlocked -> Sunburst(Melt.Sky, 0.16f)
        OnboardingStep.StreakReward -> Sunburst(Melt.Orange, 0.12f)
        OnboardingStep.HoldToAscend -> Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Melt.AscendTop, Melt.AscendBottom))))
        else -> Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Melt.Grouped))
            Box(
                Modifier.fillMaxWidth().height(460.dp).background(
                    Brush.verticalGradient(listOf(Melt.Sky.copy(alpha = 0.58f), Melt.Glow.copy(alpha = 0.32f), Color.Transparent)),
                ),
            )
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 70.dp, y = (-90).dp).size(260.dp).clip(CircleShape).background(Melt.Action.copy(alpha = 0.12f)),
            )
        }
    }
}

@Composable
private fun Sunburst(fill: Color, ray: Float) {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(fill)
        val center = Offset(size.width / 2f, size.height / 2f)
        val long = maxOf(size.width, size.height) * 1.6f
        val thick = 18.dp.toPx()
        repeat(18) { index ->
            rotate(index * 10f, center) {
                drawRect(
                    Color.White.copy(alpha = ray),
                    topLeft = Offset(center.x - thick / 2f, center.y - long / 2f),
                    size = Size(thick, long),
                )
            }
        }
    }
}

@Composable
private fun ScreenFrame(footer: @Composable () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
        Box(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)) { footer() }
    }
}

@Composable
private fun Chrome(step: OnboardingStep, showBack: Boolean, repo: RotRunRepository, mascot: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showBack && step != OnboardingStep.Persona) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFE5E5EA)).clickable(onClick = repo::back),
                contentAlignment = Alignment.Center,
            ) { Text("‹", color = Melt.Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold) }
        } else {
            Spacer(Modifier.size(36.dp))
        }
        Box(
            Modifier.padding(horizontal = 12.dp).weight(1f).height(6.dp).clip(CircleShape).background(Color(0xFFE5E5EA)),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(step.progress().coerceIn(0.04f, 1f)).background(Melt.Action))
        }
        if (mascot) CartoonBrain(BrainStyle.Mug, Modifier.size(44.dp), animated = false) else Spacer(Modifier.size(36.dp))
    }
}

@Composable
private fun Choices(
    step: OnboardingStep,
    title: String,
    selected: String?,
    options: List<Pair<String, String>>,
    showBack: Boolean,
    repo: RotRunRepository,
    subtitle: String? = null,
    chrome: Boolean = true,
) {
    ScreenFrame(footer = { PrimaryButton("Continue", enabled = selected != null) { repo.advance() } }) {
        if (chrome) Chrome(step, showBack, repo) else Spacer(Modifier.height(24.dp))
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp))
        if (subtitle != null) {
            Text(subtitle, color = Melt.Muted, textAlign = TextAlign.Center, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp))
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { (label, emoji) ->
                ChoiceRow(label, selected == label, emoji.ifBlank { null }) {
                    repo.update { current ->
                        when (step) {
                            OnboardingStep.Persona -> current.copy(persona = label)
                            OnboardingStep.ScreenTimeAffect -> current.copy(screenTimeAffect = label)
                            OnboardingStep.TriedReduce -> current.copy(triedReduce = label)
                            OnboardingStep.LoseControl -> current.copy(loseControl = label)
                            OnboardingStep.GoalWithApp -> current.copy(goal = label)
                            OnboardingStep.Age -> current.copy(ageRange = label)
                            OnboardingStep.PeakFocus -> current.copy(peakFocus = label)
                            else -> current
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Story(
    step: OnboardingStep,
    title: String,
    subtitle: String? = null,
    button: String = "Continue",
    mascot: BrainStyle,
    repo: RotRunRepository,
    ground: Boolean = false,
    brainSize: Int = 160,
) {
    ScreenFrame(footer = { PrimaryButton(button) { repo.advance() } }) {
        Chrome(step, true, repo, mascot = false)
        Spacer(Modifier.height(28.dp))
        CartoonBrain(mascot, Modifier.size(brainSize.dp))
        if (ground) {
            Box(Modifier.offset(y = (-6).dp).size(width = 180.dp, height = 22.dp).clip(RoundedCornerShape(50)).background(Color(0xFFE5E5EA)))
        }
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp).padding(top = 8.dp))
        if (subtitle != null) {
            Text(subtitle, textAlign = TextAlign.Center, color = Melt.Muted, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Reveal(state: SavedState, repo: RotRunRepository) {
    ScreenFrame(footer = { PrimaryButton("Continue") { repo.advance() } }) {
        Chrome(OnboardingStep.SeeForYourself, true, repo, mascot = false)
        Spacer(Modifier.height(24.dp))
        CartoonBrain(if (state.rotReveal < 0.45f) BrainStyle.Sad else BrainStyle.Meadow, Modifier.size(180.dp))
        Text("See for yourself!", fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        Slider(
            value = state.rotReveal,
            onValueChange = { value -> repo.update { it.copy(rotReveal = value) } },
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
            colors = SliderDefaults.colors(thumbColor = Melt.Orange, activeTrackColor = Melt.Orange),
        )
    }
}

@Composable
private fun WeekHelp(repo: RotRunRepository) {
    val rows = listOf(
        Icons.Filled.Phone to Color.Gray to "Regain control of your phone",
        Icons.Filled.Alarm to Melt.House to "Save over 3 hours every day",
        Icons.Filled.TrackChanges to Color(0xFFE23B3B) to "Improve your focus and mental clarity",
        Icons.Filled.Send to Melt.Orange to "Build healthier digital habits",
    )
    ScreenFrame(footer = { PrimaryButton("Continue") { repo.advance() } }) {
        Chrome(OnboardingStep.ThisWeekHelp, true, repo, mascot = false)
        Text("This week, RotRun will help you:", fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp))
        rows.forEach { (icon, line) ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(Melt.Action.copy(alpha = 0.10f)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon.first, null, tint = icon.second, modifier = Modifier.size(22.dp).width(36.dp))
                Text(line, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
            }
        }
        CartoonBrain(BrainStyle.Meadow, Modifier.padding(top = 8.dp).size(120.dp))
    }
}

@Composable
private fun Personalizing(repo: RotRunRepository) {
    var progress by remember { mutableFloatStateOf(0.12f) }
    LaunchedEffect(Unit) {
        delay(550); progress = 0.35f
        delay(550); progress = 0.62f
        delay(550); progress = 0.9f
        delay(350); progress = 1f
        delay(350); repo.advance()
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        CartoonBrain(BrainStyle.Meadow, Modifier.size(140.dp))
        Text("Personalizing your experience!", fontWeight = FontWeight.Bold, fontSize = 24.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
        Box(Modifier.padding(horizontal = 40.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(Color(0xFFE5E5EA))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(Melt.Action))
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            StatusLine("Analyzing your habits...", progress >= 0.12f)
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE5E5EA)))
            StatusLine("Calculating your profile...", progress >= 0.35f)
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE5E5EA)))
            StatusLine("Generating insights...", progress >= 0.62f)
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun StatusLine(text: String, revealed: Boolean) {
    val tint = if (revealed) Melt.Action else Color(0xFFC7C7CC)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (revealed) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null, tint = tint, modifier = Modifier.size(18.dp))
        Text(text, color = tint, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun Attention(state: SavedState, repo: RotRunRepository) {
    val interrupt = when (state.screenTimeAffect) {
        "No focus / procrastination", "Productivity loss" -> 0.78f
        "Anxiety / overstimulation", "I feel mentally fried" -> 0.7f
        else -> 0.62f
    }
    val daytime = when (state.loseControl) {
        "Mornings" -> 0.22f
        "Evenings" -> 0.82f
        "All day" -> 0.55f
        else -> 0.4f
    }
    ScreenFrame(footer = { PrimaryButton("Makes sense") { repo.advance() } }) {
        Chrome(OnboardingStep.AttentionProfile, true, repo, mascot = false)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(220.dp).clip(RoundedCornerShape(24.dp))) {
            Sunburst(Melt.Sky.copy(alpha = 0.35f), 0.2f)
            CartoonBrain(BrainStyle.Watch, Modifier.align(Alignment.Center).size(150.dp))
        }
        Text("Your attention profile is", color = Melt.Action, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 16.dp))
        Text("The Distracted Achiever", fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp))
        Text(
            "You value focus and productivity, but constant interruptions keep pulling you off track.",
            textAlign = TextAlign.Center,
            color = Melt.Muted,
            fontSize = 16.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
        BarLine("Focus interruptions", "Low", "High", interrupt)
        BarLine("Daytime vulnerability", "Mornings", "Evenings", daytime)
    }
}

@Composable
private fun BarLine(title: String, start: String, end: String, amount: Float) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(10.dp).clip(CircleShape).background(Color(0xFFE5E5EA))) {
            Box(Modifier.fillMaxWidth(amount).fillMaxHeight().background(Melt.Action))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(start, color = Color(0xFFC7C7CC), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(end, color = Color(0xFFC7C7CC), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun Hours(state: SavedState, repo: RotRunRepository, goal: Boolean) {
    val step = if (goal) OnboardingStep.SetGoal else OnboardingStep.DailyEstimate
    val value = if (goal) state.goalHours else state.estimatedDailyHours
    val tint = if (goal) Melt.Meadow else Melt.Orange
    ScreenFrame(footer = { PrimaryButton("Continue") { repo.advance() } }) {
        Chrome(step, true, repo, mascot = false)
        Text(
            if (goal) "Set your goal" else "How much time do you spend on screens daily?",
            fontSize = if (goal) 28.sp else 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        )
        Text(
            if (goal) "What's a healthy daily screen time target for you?" else "We don't judge, you can tell us the truth!",
            color = Melt.Muted,
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
        )
        CartoonBrain(if (goal) BrainStyle.Mug else BrainStyle.Watch, Modifier.padding(top = 8.dp).size(if (goal) 140.dp else 150.dp))
        Text("$value Hours", fontSize = if (goal) 32.sp else 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { next ->
                val hours = next.toInt()
                repo.update {
                    if (goal) {
                        val chosen = hours.coerceIn(1, 8)
                        it.copy(goalHours = chosen, brainHealth = com.ahmed.rotrun.logic.BrainHealthLevel.matching(chosen).label)
                    } else {
                        it.copy(estimatedDailyHours = hours.coerceIn(0, 12))
                    }
                }
            },
            valueRange = if (goal) 1f..8f else 0f..12f,
            modifier = Modifier.padding(horizontal = 28.dp),
            colors = SliderDefaults.colors(thumbColor = tint, activeTrackColor = tint),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (goal) "1h" else "0h", color = Melt.Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(if (goal) "8h" else "12h+", color = Melt.Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Text(
            if (goal) Scoring.goalCopy(state.estimatedDailyHours, state.goalHours) else Scoring.estimateCopy(state.estimatedDailyHours),
            color = Melt.Action,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFE9E9EB)).padding(14.dp),
        )
    }
}

@Composable
private fun Committed(repo: RotRunRepository) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        CartoonBrain(BrainStyle.Meditating, Modifier.size(180.dp))
        Text("Let's go!", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        Text(
            "You're committed and ready to go! The first step is often the hardest.",
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp),
        )
        Spacer(Modifier.weight(1f))
        Box(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            PrimaryButton("Continue", light = true) { repo.advance() }
        }
    }
}

@Composable
private fun BeforeAfter(repo: RotRunRepository) {
    ScreenFrame(footer = { PrimaryButton("Continue") { repo.advance() } }) {
        CartoonBrain(BrainStyle.Meadow, Modifier.padding(top = 16.dp).size(72.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
            CompareColumn("Before RotRun", Marketing.beforeAverage, true, Modifier.weight(1f))
            Box(Modifier.padding(horizontal = 8.dp).width(1.dp).height(120.dp).background(Color(0xFFC6C6C8)))
            CompareColumn("After RotRun", Marketing.afterAverage, false, Modifier.weight(1f))
        }
        Text(
            "Use RotRun and gain ${Marketing.hoursBack} back",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            color = Melt.Ink,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
        )
        Benefit(BrainStyle.Watch, "Focus, uninterrupted.", "Finally do your best work yet.")
        Benefit(BrainStyle.Reading, "Own your time.", "And fill your days with more intention.")
        Benefit(BrainStyle.Spa, "Be fully present.", "Because life isn't happening on your screen.")
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(5) { Icon(Icons.Filled.Star, null, tint = Melt.Orange, modifier = Modifier.size(16.dp)) }
        }
        Text(Marketing.reviews, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(Marketing.joinUsers, color = Melt.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun CompareColumn(title: String, time: String, tall: Boolean, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, textAlign = TextAlign.Center)
        Text("Daily Average", color = Melt.Muted, fontSize = 12.sp)
        Text(time, fontWeight = FontWeight.Black, fontSize = 22.sp)
        Row(Modifier.height(90.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            val count = if (tall) 5 else 3
            repeat(count) { index ->
                val height = if (tall) 36 + index * 10 else 16 + index * 6
                Box(
                    Modifier.width(12.dp).height(height.dp).clip(RoundedCornerShape(4.dp)).background(if (index % 2 == 0) Melt.Orange else Melt.Action),
                )
            }
        }
    }
}

@Composable
private fun Benefit(style: BrainStyle, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        CartoonBrain(style, Modifier.size(52.dp), animated = false)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(body, color = Melt.Muted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun Hold(repo: RotRunRepository) {
    var progress by remember { mutableFloatStateOf(0f) }
    var done by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Ready to take control?", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
        Text("Hold the button to help the brain ascend!", color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp))
        Spacer(Modifier.weight(1f))
        CartoonBrain(
            BrainStyle.Meditating,
            Modifier.size(200.dp).offset(y = (-48f * progress).dp).graphicsLayer {
                val scale = 1f + 0.1f * progress
                scaleX = scale
                scaleY = scale
            },
        )
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.padding(bottom = 36.dp).size(120.dp).pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val start = System.currentTimeMillis()
                    coroutineScope {
                        val ticker = launch {
                            while (isActive && !done) {
                                progress = ((System.currentTimeMillis() - start) / 1100f).coerceIn(0f, 1f)
                                if (progress >= 1f) {
                                    done = true
                                    repo.advance()
                                    break
                                }
                                delay(16)
                            }
                        }
                        tryAwaitRelease()
                        ticker.cancel()
                    }
                    if (!done) progress = 0f
                })
            },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(92.dp)) {
                val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                drawArc(Color.White.copy(alpha = 0.25f), 0f, 360f, false, style = stroke)
                drawArc(Melt.Action, -90f, 360f * progress, false, style = stroke)
            }
            Box(
                Modifier.size(72.dp).shadow(12.dp, CircleShape, ambientColor = Color.White, spotColor = Color.White).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(18.dp).rotate(45f).clip(RoundedCornerShape(3.dp)).background(Melt.Action))
            }
        }
    }
}

@Composable
private fun NotAddicted(repo: RotRunRepository) {
    ScreenFrame(footer = { PrimaryButton("Good to know") { repo.advance() } }) {
        Chrome(OnboardingStep.NotAddicted, true, repo, mascot = false)
        Text("You're not addicted.", fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
        Text("Your brain is being exploited.", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Melt.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
            CartoonBrain(BrainStyle.Sad, Modifier.size(140.dp))
            Notice("Trending now", Melt.Orange, Modifier.offset(x = 70.dp, y = (-50).dp))
            Notice("Someone commented", Melt.Orange, Modifier.offset(x = (-78).dp, y = (-16).dp))
            Notice("Your friend posted", Color(0xFF8F5CC7), Modifier.offset(x = 86.dp, y = 18.dp))
            Notice("Check this out", Melt.House, Modifier.offset(x = (-64).dp, y = 48.dp))
        }
        Text("Modern apps are engineered to hijack attention.", color = Melt.Muted, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
        Text("RotRun helps you take control, without deleting your apps.", color = Melt.Muted, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp))
    }
}

@Composable
private fun Notice(text: String, tint: Color, modifier: Modifier) {
    Row(
        modifier.shadow(6.dp, CircleShape).clip(CircleShape).background(Color.White).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(tint))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun DidYouKnow(repo: RotRunRepository) {
    val facts = listOf(
        Icons.Filled.Alarm to Melt.House to "The average person spends over 4 hours per day on their phone",
        Icons.Filled.Phone to Color(0xFF8F5CC7) to "Most of us check our phone 58 times per day without realizing",
        Icons.Filled.Bedtime to Color(0xFF8F5CC7) to "Too much screen time messes with your memory, focus and sleep",
        Icons.Filled.Visibility to Melt.Ink to "7 out of 10 people get tired, dry eyes from staring at screens",
    )
    ScreenFrame(footer = { PrimaryButton("Continue") { repo.advance() } }) {
        Chrome(OnboardingStep.DidYouKnow, true, repo, mascot = false)
        Text("Did you know?", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        CartoonBrain(BrainStyle.Meadow, Modifier.size(120.dp))
        facts.forEach { (icon, line) ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFE9E9EB)).padding(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(icon.first, null, tint = icon.second, modifier = Modifier.size(18.dp).width(28.dp))
                Text(line, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun SocialProof(repo: RotRunRepository) {
    ScreenFrame(footer = { PrimaryButton("Continue") { repo.advance() } }) {
        Chrome(OnboardingStep.SocialProof, true, repo, mascot = false)
        Spacer(Modifier.height(12.dp))
        CartoonBrain(BrainStyle.Meadow, Modifier.size(150.dp))
        Text(Marketing.madeFor, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp))
        Text(Marketing.rating, color = Melt.Orange, fontSize = 42.sp, fontWeight = FontWeight.Black)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(5) { Icon(Icons.Filled.Star, null, tint = Melt.Orange, modifier = Modifier.size(18.dp)) }
        }
        Text(Marketing.users, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

private fun Modifier.borderCircle(): Modifier = this.then(Modifier.clip(CircleShape))

@Composable
private fun Carousel(step: OnboardingStep, onLegal: (String) -> Unit, repo: RotRunRepository) {
    val index = when (step) {
        OnboardingStep.CarouselThemes -> 1
        OnboardingStep.CarouselFocus -> 2
        else -> 0
    }
    val (eyebrow, title) = when (step) {
        OnboardingStep.CarouselThemes -> "Make it yours" to "Unlock themes and customize."
        OnboardingStep.CarouselFocus -> "Focus Mode" to "Heal your brain with Focus Mode."
        else -> "Welcome to RotRun" to "Stop scrolling. Save your Brain."
    }
    ScreenFrame(footer = {
        PrimaryButton("Get started") { repo.advance() }
        Text("By continuing, you agree to our", color = Melt.Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text("Terms of Use", color = Melt.Action, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.clickable { onLegal("terms") }.padding(8.dp))
            Text("and", color = Melt.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Text("Privacy Policy", color = Melt.Action, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.clickable { onLegal("privacy") }.padding(8.dp))
        }
    }) {
        PhoneMock(step)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { dot ->
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (dot == index) Melt.Action else Color(0xFFE5E5EA)))
            }
        }
        Text(eyebrow, color = Melt.Action, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
    }
}

@Composable
private fun PhoneMock(step: OnboardingStep) {
    Column(
        Modifier.padding(top = 12.dp).width(230.dp).height(360.dp).clip(RoundedCornerShape(36.dp)).background(Color.Black).padding(8.dp),
    ) {
        Column(
            Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(
                when (step) {
                    OnboardingStep.CarouselThemes -> Melt.Meadow.copy(alpha = 0.35f)
                    OnboardingStep.CarouselFocus -> Melt.Meadow.copy(alpha = 0.45f)
                    else -> Color.White
                },
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (step == OnboardingStep.CarouselFocus) {
                Spacer(Modifier.height(48.dp))
                CartoonBrain(BrainStyle.Meditating, Modifier.size(90.dp), animated = false)
                Text("0:00", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Box(Modifier.padding(top = 12.dp).size(44.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(18.dp).clip(CircleShape).background(Melt.House))
                }
            } else {
                Text("RotRun", fontWeight = FontWeight.Black, fontSize = 13.sp, modifier = Modifier.padding(top = 28.dp))
                CartoonBrain(if (step == OnboardingStep.CarouselThemes) BrainStyle.Sad else BrainStyle.Meadow, Modifier.size(72.dp), animated = false)
                Text(if (step == OnboardingStep.CarouselThemes) "79%" else "90%", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Box(
                    Modifier.padding(horizontal = 24.dp, vertical = 8.dp).fillMaxWidth().height(8.dp).clip(CircleShape)
                        .background(if (step == OnboardingStep.CarouselThemes) Melt.Orange else Melt.Meadow),
                )
                if (step == OnboardingStep.CarouselWelcome) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("1h 24m", "32", "4m").forEach { stat ->
                            Text(
                                stat,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFF2F2F7)).padding(horizontal = 8.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Notifications(onContinue: () -> Unit) {
    ScreenFrame(footer = { PrimaryButton("Continue", onClick = onContinue) }) {
        Text("Enable Screen Time Notifications!", fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp))
        Text("RotRun needs to send you notifications to help you stay on track.", color = Melt.Muted, textAlign = TextAlign.Center, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp))
        NotifyCard(title = "Brain Health Insights", body = "Get updates about your brain health and screen usage.") {
            CartoonBrain(BrainStyle.Spa, Modifier.size(44.dp), animated = false)
        }
        NotifyCard(title = "Daily Stats", body = "See how you're doing with your Screen Time goals.") {
            Icon(Icons.Filled.TrackChanges, null, tint = Melt.House, modifier = Modifier.size(22.dp))
        }
        NotifyCard(title = "Excessive Use Alerts", body = "Be notified when you're spending too much time on certain apps.") {
            Icon(Icons.Filled.Warning, null, tint = Melt.Orange, modifier = Modifier.size(22.dp))
        }
        NotifyCard(title = "Intervention Reminders", body = "Gentle reminders to take breaks and stay mindful.") {
            Icon(Icons.Filled.Alarm, null, tint = Melt.House, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun NotifyCard(title: String, body: String, icon: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(Melt.Action.copy(alpha = 0.10f)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { icon() }
        Column(Modifier.padding(start = 8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(body, color = Melt.Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun RulePreview(state: SavedState, repo: RotRunRepository) {
    ScreenFrame(footer = { PrimaryButton("Get Started") { repo.advance() } }) {
        Text("Check out your new rule!", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp))
        Text("This is what your rule will look like on the Rules tab.", color = Melt.Muted, textAlign = TextAlign.Center, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 6.dp))
        RuleCard(state.peakFocus.ifBlank { "Work Focus" }, peakWindow(state.peakFocus), "Today")
        CartoonBrain(BrainStyle.Meadow, Modifier.size(140.dp))
    }
}

@Composable
private fun RuleCard(title: String, subtitle: String, badge: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).clip(RoundedCornerShape(26.dp)).background(Color.White).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFE5E5EA)), contentAlignment = Alignment.Center) {
            Text("⚙️", fontSize = 22.sp)
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(subtitle, color = Melt.Muted, fontSize = 14.sp)
        }
        Text(badge, color = Melt.Orange, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

private fun peakWindow(name: String): String = when (name) {
    "Mindful Morning" -> "6 am - 9 am"
    "Evening Wind Down" -> "8 pm - 11 pm"
    else -> "9 am - 5 pm"
}

@Composable
private fun ConnectEdu(repo: RotRunRepository) {
    ScreenFrame(footer = { PrimaryButton("Continue") { repo.advance() } }) {
        Spacer(Modifier.height(48.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.zIndex(1f).size(110.dp).offset(x = 12.dp).clip(RoundedCornerShape(28.dp)).background(Melt.Sky),
                contentAlignment = Alignment.Center,
            ) { CartoonBrain(BrainStyle.Meadow, Modifier.size(78.dp), animated = false) }
            Box(
                Modifier.size(110.dp).offset(x = (-12).dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFF7361D9)),
                contentAlignment = Alignment.Center,
            ) { Text("⌛", color = Color.White, fontSize = 42.sp) }
        }
        Text("Connect RotRun to Screen Time", fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp))
        Text("Your data is completely private and never leaves your device.", color = Melt.Muted, textAlign = TextAlign.Center, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp))
    }
}

@Composable
private fun PurchaseComplete(state: SavedState, repo: RotRunRepository) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(Melt.Meadow))
        Spacer(Modifier.weight(1f))
        CartoonBrain(BrainStyle.Meadow, Modifier.size(160.dp))
        Text("Purchase complete!", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        Text("RotRun Premium is on. You now have access to every feature.", color = Melt.Muted, textAlign = TextAlign.Center, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp))
        Spacer(Modifier.weight(1f))
        Box(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            PrimaryButton(if (state.onboardingCompleted) "Continue to app" else "Continue") { repo.advance() }
        }
    }
}

@Composable
private fun ThemeUnlocked(repo: RotRunRepository) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.padding(top = 48.dp).size(120.dp).clip(RoundedCornerShape(28.dp)).background(Brush.verticalGradient(listOf(Melt.Sky, Melt.Meadow))),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("☁", color = Color.White, fontSize = 28.sp)
                Text("🍃", fontSize = 22.sp)
            }
        }
        CartoonBrain(BrainStyle.Meadow, Modifier.size(140.dp))
        Text("New Theme Unlocked!", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2430))
        Text(
            "You unlocked the Grassy theme! Check it out in your profile under customization!",
            textAlign = TextAlign.Center,
            color = Color(0xFF2E3847),
            fontSize = 16.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp),
        )
        Spacer(Modifier.weight(1f))
        Box(Modifier.padding(horizontal = 20.dp)) { PrimaryButton("Go Try it out!", light = true) { repo.advance() } }
        Text("Maybe Later", color = Color(0xFF1F477A), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(vertical = 16.dp).clickable { repo.advance() })
    }
}

@Composable
private fun StreakReward(repo: RotRunRepository) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        CartoonBrain(BrainStyle.Mug, Modifier.padding(top = 36.dp).size(150.dp))
        Text("1", color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Black)
        Text("day streak", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Reward Unlocked!", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Text("You hit a 1-day streak and unlocked a new reward.", color = Color.White.copy(alpha = 0.92f), textAlign = TextAlign.Center, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp))
        Spacer(Modifier.weight(1f))
        Box(Modifier.padding(horizontal = 20.dp)) { PrimaryButton("Collect Reward", light = true) { repo.advance() } }
        Text("Maybe Later", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(vertical = 16.dp).clickable { repo.advance() })
    }
}

@Composable
private fun WidgetSteps(repo: RotRunRepository) {
    val steps = listOf(
        "Long Press your Home Screen" to "Tap and hold an empty area of your Home Screen until the menu appears.",
        "Tap Widgets" to "On Android, open the widget picker from the home screen.",
        "Search for “RotRun”" to "Use the search bar at the top to find RotRun.",
        "Select the RotRun widget" to "Choose Brain Health or Focus from the RotRun widgets.",
        "Tap “Add Widget”" to "Confirm adding the widget to your home screen.",
    )
    ScreenFrame(footer = { PrimaryButton("Done") { repo.advance() } }) {
        Column(
            Modifier.padding(top = 20.dp).clip(RoundedCornerShape(22.dp)).background(Color.White).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CartoonBrain(BrainStyle.Meditating, Modifier.size(72.dp), animated = false)
            Text("Start Focus", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp).clip(CircleShape).background(Melt.Action).padding(horizontal = 14.dp, vertical = 6.dp))
        }
        steps.forEachIndexed { index, (title, body) ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(Melt.Action.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Text("${index + 1}", color = Melt.Action, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(body, color = Melt.Muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SelectApps(state: SavedState, installed: List<TrackedApp>, repo: RotRunRepository) {
    val selected = state.appLists.firstOrNull { it.id == "list.brainmelt" }?.apps.orEmpty()
    var picking by remember { mutableStateOf(false) }
    ScreenFrame(footer = { PrimaryButton("Continue") { repo.advance() } }) {
        Text("Select your RotRun Apps", fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 24.dp, start = 24.dp, end = 24.dp))
        Text("Select the apps that distract you the most, the ones you open on autopilot.", color = Melt.Muted, textAlign = TextAlign.Center, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 6.dp))
        CartoonBrain(BrainStyle.Meadow, Modifier.size(130.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFFE9E9EB)).padding(16.dp)) {
            Text("RotRun Apps", fontWeight = FontWeight.Bold)
            if (selected.isEmpty()) Text("No apps yet", color = Melt.Muted, fontSize = 13.sp)
            selected.forEach { app ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    AppGlyph(app.packageName, Modifier.size(32.dp))
                    Text(app.label, modifier = Modifier.padding(start = 10.dp), fontWeight = FontWeight.Medium)
                }
            }
            if (installed.isEmpty()) {
                Text("Open this screen again after RotRun loads your apps.", modifier = Modifier.padding(top = 8.dp))
                LaunchedEffect(Unit) { repo.loadInstalled() }
            }
            Text("Add Apps", color = Melt.Action, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp).clickable { picking = true })
        }
        Text("You can update this later in Block.", color = Melt.Action, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
    }
    if (picking) {
        AppPickerDialog(
            apps = installed,
            selected = selected,
            onDismiss = { picking = false },
            onSave = { chosen ->
                repo.update { current ->
                    val lists = current.appLists.ifEmpty { listOf(AppList("list.brainmelt", "RotRun Apps")) }.map { list ->
                        if (list.id == "list.brainmelt") list.copy(apps = chosen) else list
                    }
                    current.copy(appLists = lists, blockers = current.blockers.map { if (it.id == "brick") it.copy(apps = chosen) else it })
                }
                picking = false
            },
            exceedsCap = { proposed -> repo.wouldExceedFreeCap(proposed, exceptBlocker = "brick", exceptList = "list.brainmelt") },
            onUpgrade = {
                picking = false
                repo.toast(BlockRules.FREE_LIMIT_MESSAGE)
                repo.requestPaywall()
            },
        )
    }
}

@Composable
private fun ScreenDialog(repo: RotRunRepository, onAllow: () -> Unit) {
    ScreenFrame(footer = { PrimaryButton("Continue") { onAllow(); repo.advance() } }) {
        Text("Connect RotRun to Screen Time", fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp))
        Text("Your data is completely private and never leaves your device.", color = Melt.Muted, textAlign = TextAlign.Center, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp))
        Column(
            Modifier.padding(horizontal = 28.dp, vertical = 16.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("“RotRun” Would Like to Access Screen Time", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, textAlign = TextAlign.Center)
            Text("Providing “RotRun” access to Screen Time may allow it to see your activity data, restrict content, and limit the usage of apps and websites.", color = Melt.Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("Continue", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Text("Don't Allow", color = Melt.Action, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).clickable { repo.advance() })
            }
        }
        CartoonBrain(BrainStyle.Meadow, Modifier.size(120.dp))
    }
}

@Composable
private fun EnableRule(repo: RotRunRepository) {
    val context = LocalContext.current
    var ask by remember { mutableStateOf(false) }
    ScreenFrame(footer = { PrimaryButton("Activate Rule") { ask = true } }) {
        Text("Enable Your First Rule", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp))
        Text("Block your RotRun apps every day!", color = Melt.Muted, fontSize = 16.sp)
        CartoonBrain(BrainStyle.Mug, Modifier.size(140.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(26.dp)).background(Color.White).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFDBD1FA)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Phone, null, tint = Color(0xFF738CF2))
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text("Brick Mode", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("All Day", color = Melt.Muted)
            }
            Text("Active", color = Color(0xFF34C759), fontWeight = FontWeight.Bold)
        }
    }
    if (ask) {
        AlertDialog(
            onDismissRequest = { ask = false },
            title = { Text("Blocking access") },
            text = { Text(stringResource(com.ahmed.rotrun.R.string.accessibility_disclosure)) },
            confirmButton = {
                TextButton(onClick = {
                    ask = false
                    runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    repo.advance()
                }) { Text("Open settings") }
            },
            dismissButton = {
                TextButton(onClick = { ask = false; repo.advance() }) { Text("Not now") }
            },
        )
    }
}
