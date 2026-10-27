package com.ahmed.rotrun.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmed.rotrun.data.AppList
import com.ahmed.rotrun.data.OnboardingStep
import com.ahmed.rotrun.data.RotRunRepository
import com.ahmed.rotrun.data.SavedState
import com.ahmed.rotrun.data.TrackedApp
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
    val context = LocalContext.current
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { repo.advance() }
    if (legal != null) {
        LegalPage(if (legal == "terms") "terms" else "privacy", if (legal == "terms") "Terms & Conditions" else "Privacy Policy") {
            legal = null
        }
        return
    }
    val canvas = when (step) {
        OnboardingStep.Committed -> Brush.verticalGradient(listOf(Melt.Orange, Color(0xFFFFC46B)))
        OnboardingStep.HoldToAscend -> Brush.verticalGradient(listOf(Melt.AscendTop, Melt.AscendBottom))
        OnboardingStep.RotMessage -> Brush.verticalGradient(listOf(Melt.Sky, Melt.Meadow))
        else -> Brush.verticalGradient(listOf(Color(0xFFF8F9FC), Melt.Grouped))
    }
    Column(
        Modifier.fillMaxSize().background(canvas).verticalScroll(rememberScrollState()).padding(bottom = 28.dp),
    ) {
        when (step) {
            OnboardingStep.Persona -> Choices(step, "Which best describes you?", state.persona, personaOptions, false, repo) { it.copy(persona = it.persona) }
            OnboardingStep.ScreenTimeAffect -> Choices(step, "How does screen time affect you the most?", state.screenTimeAffect, affectOptions, true, repo) { it.copy(screenTimeAffect = it.screenTimeAffect) }
            OnboardingStep.TriedReduce -> Choices(step, "Have you tried to reduce screen time before?", state.triedReduce, triedOptions, true, repo) { it.copy(triedReduce = it.triedReduce) }
            OnboardingStep.RotMessage -> Story(step, "The more you rot, the more your brain rots.", mascot = BrainStyle.Meadow, repo = repo)
            OnboardingStep.SeeForYourself -> Reveal(state, repo)
            OnboardingStep.LoseControl -> Choices(step, "When do you usually lose control? (rot the most)", state.loseControl, loseOptions, true, repo) { it.copy(loseControl = it.loseControl) }
            OnboardingStep.ThisWeekHelp -> Bullets(step, "This week, RotRun will help you:", weekHelp, repo)
            OnboardingStep.WelcomeBrand -> Story(step, "Welcome to RotRun!", "It's time to regain control of your screen time.", "Let's go!", BrainStyle.Meadow, repo)
            OnboardingStep.GoalWithApp -> Choices(step, "What is your goal with RotRun?", state.goal, goalOptions, true, repo) { it.copy(goal = it.goal) }
            OnboardingStep.PersonalizeIntro -> Story(step, "Let's personalize RotRun for you.", "Your answers help us map your attention patterns.", "Let's do it!", BrainStyle.Mug, repo)
            OnboardingStep.Personalizing -> Personalizing(repo)
            OnboardingStep.MeetBrain -> Story(step, "Meet your brain", "He's doing OK.", mascot = BrainStyle.Meadow, repo = repo)
            OnboardingStep.Age -> Choices(step, "How old are you?", state.ageRange, ageOptions, true, repo, mascot = false) { it.copy(ageRange = it.ageRange) }
            OnboardingStep.AttentionProfile -> Attention(repo)
            OnboardingStep.DailyEstimate -> Hours(state, repo, goal = false)
            OnboardingStep.Committed -> Story(step, "Let's go!", "You're committed and ready to go! The first step is often the hardest.", mascot = BrainStyle.Meadow, repo = repo, lightButton = true)
            OnboardingStep.BeforeAfter -> BeforeAfter(repo)
            OnboardingStep.HoldToAscend -> Hold(repo)
            OnboardingStep.NotAddicted -> NotAddicted(repo)
            OnboardingStep.DidYouKnow -> Bullets(step, "Did you know?", facts, repo, BrainStyle.Reading)
            OnboardingStep.SocialProof -> SocialProof(repo)
            OnboardingStep.CarouselWelcome, OnboardingStep.CarouselThemes, OnboardingStep.CarouselFocus -> Carousel(step, { legal = it }, repo)
            OnboardingStep.PeakFocus -> Choices(step, "Pick your peak focus time!", state.peakFocus, peakOptions, true, repo, "This is the time where you want to block the most distracting apps.") { it.copy(peakFocus = it.peakFocus) }
            OnboardingStep.Notifications -> Notifications { 
                if (Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS) else repo.advance()
            }
            OnboardingStep.RulePreview -> RulePreview(repo)
            OnboardingStep.GetAccess -> GetAccessPaywall(
                state = state,
                showClose = !state.skippedOneTimeOffer,
                showDiscountLink = !state.skippedOneTimeOffer,
                onClose = repo::declineAccess,
                onDiscount = repo::declineAccess,
                onPurchase = repo::purchase,
                onRestore = repo::restore,
                selected = plan,
                onSelect = { plan = it },
            )
            OnboardingStep.OneTimeOffer -> OneTimeOffer(
                onRedeem = { repo.purchase(Products.YEARLY70) },
                onOther = repo::leaveOffer,
                showClose = true,
                onClose = repo::leaveOffer,
            )
            OnboardingStep.SetGoal -> Hours(state, repo, goal = true)
            OnboardingStep.ConnectScreenTime -> Story(step, "Connect RotRun to Screen Time", "Your data is completely private and never leaves your device.", mascot = BrainStyle.Meadow, repo = repo)
            OnboardingStep.PurchaseComplete -> Story(step, "Purchase complete!", "RotRun Premium is on. You now have access to every feature.", if (state.onboardingCompleted) "Continue to app" else "Continue", BrainStyle.Meadow, repo)
            OnboardingStep.ThemeUnlocked -> TwoActions(step, "New Theme Unlocked!", "You unlocked the Grassy theme! Check it out in your profile under customization!", "Go Try it out!", "Maybe Later", repo)
            OnboardingStep.WidgetHowTo -> WidgetSteps(repo)
            OnboardingStep.StreakReward -> TwoActions(step, "Reward Unlocked!", "You hit a 1-day streak and unlocked a new reward.", "Collect Reward", "Maybe Later", repo, headline = "1\nday streak")
            OnboardingStep.SelectApps -> SelectApps(state, installed, repo)
            OnboardingStep.ScreenTimeDialog -> ScreenDialog(repo) {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            OnboardingStep.EnableFirstRule -> EnableRule(repo)
            OnboardingStep.LimitedOffer, OnboardingStep.ChoosePlan -> {
                LaunchedEffect(step) { repo.advance() }
            }
        }
    }
}

private val personaOptions = listOf("Builder / Creator" to "⚙️", "Knowledge worker" to "🔍", "Founder / Entrepreneur" to "✈️", "Sports / Athlete" to "🏃", "Student" to "📚", "Just trying to live better" to "❤️")
private val affectOptions = listOf("No focus / procrastination" to "", "Anxiety / overstimulation" to "👀", "Bad sleep" to "", "Productivity loss" to "", "I feel mentally fried" to "🔥", "Less time with friends / family" to "")
private val triedOptions = listOf("Yes, didn't stick" to "", "Yes, briefly worked" to "", "No, first time" to "")
private val loseOptions = listOf("Mornings" to "", "During the day" to "", "Evenings" to "", "All day" to "", "Not sure" to "")
private val goalOptions = listOf("Improve focus" to "", "Reduce mindless scrolling" to "", "Sleep better" to "", "Be more present" to "", "Be more productive" to "", "Just curious" to "")
private val ageOptions = listOf("Under 18" to "", "18-24" to "", "25-34" to "", "35-44" to "", "45-54" to "", "55+" to "")
private val peakOptions = listOf("Mindful Morning" to "🌅", "Work Focus" to "💼", "Evening Wind Down" to "🌙")
private val weekHelp = listOf("Regain control of your phone", "Save over 3 hours every day", "Improve your focus and mental clarity", "Build healthier digital habits")
private val facts = listOf(
    "The average person spends over 4 hours per day on their phone",
    "Most of us check our phone 58 times per day without realizing",
    "Too much screen time messes with your memory, focus and sleep",
    "7 out of 10 people get tired, dry eyes from staring at screens",
)

@Composable
private fun Chrome(step: OnboardingStep, showBack: Boolean, repo: RotRunRepository, light: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showBack && step != OnboardingStep.Persona) {
            Text("‹", color = if (light) Color.White else Melt.Ink, fontSize = 28.sp, modifier = Modifier.clickable(onClick = repo::back))
        } else {
            Spacer(Modifier.size(20.dp))
        }
        LinearProgressIndicator(
            progress = { step.progress() },
            modifier = Modifier.padding(horizontal = 12.dp).weight(1f).height(6.dp).clip(CircleShape),
            color = Melt.Action,
            trackColor = Color.Black.copy(alpha = 0.08f),
        )
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
    mascot: Boolean = true,
    apply: (SavedState) -> SavedState,
) {
    Chrome(step, showBack, repo)
    if (mascot) CartoonBrain(BrainStyle.Mug, Modifier.size(72.dp).alignCenter(), animated = false)
    Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
    if (subtitle != null) Text(subtitle, color = Melt.Muted, modifier = Modifier.padding(horizontal = 22.dp))
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        options.forEach { (label, emoji) ->
            ChoiceRow(label, selected == label, emoji.ifBlank { null }) {
                repo.update { current ->
                    val picked = when (step) {
                        OnboardingStep.Persona -> current.copy(persona = label)
                        OnboardingStep.ScreenTimeAffect -> current.copy(screenTimeAffect = label)
                        OnboardingStep.TriedReduce -> current.copy(triedReduce = label)
                        OnboardingStep.LoseControl -> current.copy(loseControl = label)
                        OnboardingStep.GoalWithApp -> current.copy(goal = label)
                        OnboardingStep.Age -> current.copy(ageRange = label)
                        OnboardingStep.PeakFocus -> current.copy(peakFocus = label)
                        else -> current
                    }
                    picked
                }
            }
        }
    }
    PrimaryButton("Continue", enabled = selected != null) { repo.advance() }
}

@Composable
private fun Story(
    step: OnboardingStep,
    title: String,
    subtitle: String? = null,
    button: String = "Continue",
    mascot: BrainStyle,
    repo: RotRunRepository,
    lightButton: Boolean = false,
) {
    Chrome(step, true, repo, light = step == OnboardingStep.HoldToAscend || step == OnboardingStep.Committed)
    Spacer(Modifier.height(12.dp))
    CartoonBrain(mascot, Modifier.size(160.dp).alignCenter())
    Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = if (lightButton || step == OnboardingStep.Committed) Color.White else Melt.Ink, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp))
    if (subtitle != null) {
        Text(subtitle, textAlign = TextAlign.Center, color = if (step == OnboardingStep.Committed) Color.White.copy(alpha = 0.9f) else Melt.Muted, modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp))
    }
    Spacer(Modifier.height(20.dp))
    Box(Modifier.padding(horizontal = 20.dp)) { PrimaryButton(button, light = lightButton) { repo.advance() } }
}

@Composable
private fun Reveal(state: SavedState, repo: RotRunRepository) {
    Chrome(OnboardingStep.SeeForYourself, true, repo)
    Text("See for yourself!", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(22.dp))
    CartoonBrain(if (state.rotReveal < 0.45f) BrainStyle.Sad else BrainStyle.Meadow, Modifier.size(160.dp).alignCenter())
    Slider(
        value = state.rotReveal,
        onValueChange = { value -> repo.update { it.copy(rotReveal = value) } },
        modifier = Modifier.padding(horizontal = 22.dp),
    )
    Box(Modifier.padding(horizontal = 20.dp)) { PrimaryButton("Continue") { repo.advance() } }
}

@Composable
private fun Bullets(step: OnboardingStep, title: String, lines: List<String>, repo: RotRunRepository, mascot: BrainStyle = BrainStyle.Meadow) {
    Chrome(step, true, repo)
    CartoonBrain(mascot, Modifier.size(100.dp).alignCenter(), animated = false)
    Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
    lines.forEach { line ->
        Text("•  $line", modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp), fontSize = 16.sp)
    }
    Box(Modifier.padding(horizontal = 20.dp)) { PrimaryButton("Continue") { repo.advance() } }
}

@Composable
private fun Personalizing(repo: RotRunRepository) {
    var line by remember { mutableStateOf("Analyzing your habits...") }
    LaunchedEffect(Unit) {
        delay(700); line = "Calculating your profile..."
        delay(700); line = "Generating insights..."
        delay(800); repo.advance()
    }
    Spacer(Modifier.height(80.dp))
    CartoonBrain(BrainStyle.Meadow, Modifier.size(140.dp).alignCenter())
    Text("Personalizing your experience!", fontWeight = FontWeight.Bold, fontSize = 26.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
    Text(line, color = Melt.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Attention(repo: RotRunRepository) {
    Chrome(OnboardingStep.AttentionProfile, true, repo)
    Text("Your attention profile is", color = Melt.Muted, modifier = Modifier.padding(horizontal = 22.dp))
    Text("The Distracted Achiever", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp))
    Text("You value focus and productivity, but constant interruptions keep pulling you off track.", modifier = Modifier.padding(horizontal = 22.dp))
    BarLine("Focus interruptions", "Low", "High", 0.72f)
    BarLine("Daytime vulnerability", "Mornings", "Evenings", 0.8f)
    Box(Modifier.padding(horizontal = 20.dp)) { PrimaryButton("Makes sense") { repo.advance() } }
}

@Composable
private fun BarLine(title: String, start: String, end: String, amount: Float) {
    Column(Modifier.padding(horizontal = 22.dp, vertical = 8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(Color(0xFFE5E5EA))) {
            Box(Modifier.fillMaxWidth(amount).height(10.dp).background(Melt.Orange))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(start, color = Melt.Muted, fontSize = 12.sp)
            Text(end, color = Melt.Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Hours(state: SavedState, repo: RotRunRepository, goal: Boolean) {
    val step = if (goal) OnboardingStep.SetGoal else OnboardingStep.DailyEstimate
    val value = if (goal) state.goalHours else state.estimatedDailyHours
    Chrome(step, true, repo)
    Text(if (goal) "Set your goal" else "How much time do you spend on screens daily?", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 22.dp))
    Text(
        if (goal) "What's a healthy daily screen time target for you?" else "We don't judge, you can tell us the truth!",
        color = Melt.Muted,
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
    )
    Text("$value Hours", fontSize = 42.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 22.dp))
    Slider(
        value = value.toFloat(),
        onValueChange = { next ->
            val hours = next.toInt()
            repo.update { if (goal) it.copy(goalHours = hours.coerceIn(1, 8)) else it.copy(estimatedDailyHours = hours.coerceIn(0, 12)) }
        },
        valueRange = if (goal) 1f..8f else 0f..12f,
        modifier = Modifier.padding(horizontal = 22.dp),
    )
    Text(
        if (goal) Scoring.goalCopy(state.estimatedDailyHours, state.goalHours) else Scoring.estimateCopy(state.estimatedDailyHours),
        modifier = Modifier.padding(horizontal = 22.dp),
    )
    Box(Modifier.padding(horizontal = 20.dp)) { PrimaryButton("Continue") { repo.advance() } }
}

@Composable
private fun BeforeAfter(repo: RotRunRepository) {
    Chrome(OnboardingStep.BeforeAfter, true, repo)
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CompareCard("Before RotRun", "Daily Average 6h 32m", Modifier.weight(1f))
        CompareCard("After RotRun", "1h 49m", Modifier.weight(1f))
    }
    Text("Use RotRun and gain 2+ Hours back", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp))
    listOf(
        "Focus, uninterrupted." to "Finally do your best work yet.",
        "Own your time." to "And fill your days with more intention.",
        "Be fully present." to "Because life isn't happening on your screen.",
    ).forEach { (title, body) ->
        Column(Modifier.padding(horizontal = 22.dp, vertical = 6.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, color = Melt.Muted)
        }
    }
    Text("10,000+ Reviews", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp))
    Text("Join users using RotRun", color = Melt.Muted, modifier = Modifier.padding(horizontal = 22.dp))
    Box(Modifier.padding(horizontal = 20.dp)) { PrimaryButton("Continue") { repo.advance() } }
}

@Composable
private fun CompareCard(title: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Color.White).padding(12.dp)) {
        Text(title, fontWeight = FontWeight.Bold)
        Text(value, color = Melt.Muted, fontSize = 13.sp)
    }
}

@Composable
private fun Hold(repo: RotRunRepository) {
    var progress by remember { mutableFloatStateOf(0f) }
    var done by remember { mutableStateOf(false) }
    Chrome(OnboardingStep.HoldToAscend, true, repo, light = true)
    CartoonBrain(BrainStyle.Meditating, Modifier.size(150.dp).alignCenter())
    Text("Ready to take control?", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
    Text("Hold the button to help the brain ascend!", color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp))
    Box(
        Modifier
            .padding(24.dp)
            .size(120.dp)
            .alignCenter()
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.2f))
            .pointerInput(Unit) {
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
        Box(Modifier.size(88.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Text("${(progress * 100).toInt()}%", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NotAddicted(repo: RotRunRepository) {
    Chrome(OnboardingStep.NotAddicted, true, repo)
    Text("You're not addicted.", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 22.dp))
    Text("Your brain is being exploited.", fontSize = 22.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp))
    listOf("Trending now", "Someone commented", "Your friend posted", "Check this out").forEach { bubble ->
        Text(bubble, modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(horizontal = 14.dp, vertical = 8.dp))
    }
    Text("Modern apps are engineered to hijack attention.", modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
    Text("RotRun helps you take control, without deleting your apps.", color = Melt.Muted, modifier = Modifier.padding(horizontal = 22.dp))
    Box(Modifier.padding(horizontal = 20.dp)) { PrimaryButton("Good to know") { repo.advance() } }
}

@Composable
private fun SocialProof(repo: RotRunRepository) {
    Chrome(OnboardingStep.SocialProof, true, repo)
    CartoonBrain(BrainStyle.Meadow, Modifier.size(120.dp).alignCenter())
    Text("RotRun was made for people like you.", fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(22.dp))
    Text("4.7", fontSize = 48.sp, fontWeight = FontWeight.Black, modifier = Modifier.alignCenter())
    Text("★★★★★", color = Melt.Orange, fontSize = 22.sp, modifier = Modifier.alignCenter())
    Text("650K+ RotRun users", fontWeight = FontWeight.SemiBold, modifier = Modifier.alignCenter().padding(top = 6.dp))
    Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) { PrimaryButton("Continue") { repo.advance() } }
}

@Composable
private fun Carousel(step: OnboardingStep, onLegal: (String) -> Unit, repo: RotRunRepository) {
    val (eyebrow, title) = when (step) {
        OnboardingStep.CarouselThemes -> "Make it yours" to "Unlock themes and customize."
        OnboardingStep.CarouselFocus -> "Focus Mode" to "Heal your brain with Focus Mode."
        else -> "Welcome to RotRun" to "Stop scrolling. Save your Brain."
    }
    Chrome(step, true, repo)
    Text(eyebrow, color = Melt.Action, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 22.dp))
    Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
    CartoonBrain(if (step == OnboardingStep.CarouselFocus) BrainStyle.Spa else BrainStyle.Meadow, Modifier.size(180.dp).alignCenter())
    Box(Modifier.padding(horizontal = 20.dp)) { PrimaryButton("Get started") { repo.advance() } }
    Text("By continuing, you agree to our Terms of Use and Privacy Policy", textAlign = TextAlign.Center, color = Melt.Muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Text("Terms of Use", color = Melt.Action, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onLegal("terms") }.padding(8.dp))
        Text("Privacy Policy", color = Melt.Action, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onLegal("privacy") }.padding(8.dp))
    }
}

@Composable
private fun Notifications(onContinue: () -> Unit) {
    ChromeStub()
    Text("Enable Screen Time Notifications!", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 22.dp))
    Text("RotRun needs to send you notifications to help you stay on track.", color = Melt.Muted, modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp))
    listOf(
        "Brain Health Insights" to "Get updates about your brain health and screen usage.",
        "Daily Stats" to "See how you're doing with your Screen Time goals.",
        "Excessive Use Alerts" to "Be notified when you're spending too much time on certain apps.",
        "Intervention Reminders" to "Gentle reminders to take breaks and stay mindful.",
    ).forEach { (title, body) ->
        Column(Modifier.padding(horizontal = 22.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(14.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, color = Melt.Muted, fontSize = 14.sp)
        }
    }
    Box(Modifier.padding(horizontal = 20.dp)) { PrimaryButton("Continue", onClick = onContinue) }
}

@Composable
private fun ChromeStub() {
    LinearProgressIndicator(
        progress = { OnboardingStep.Notifications.progress() },
        modifier = Modifier.padding(20.dp).fillMaxWidth().height(6.dp).clip(CircleShape),
        color = Melt.Action,
    )
}

@Composable
private fun RulePreview(repo: RotRunRepository) {
    Chrome(OnboardingStep.RulePreview, true, repo)
    Text("Check out your new rule!", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 22.dp))
    Text("This is what your rule will look like on the Rules tab.", color = Melt.Muted, modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp))
    Column(Modifier.padding(22.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(16.dp)) {
        Text("Brick Mode", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("All Day", color = Melt.Muted)
        Text("Active", color = Color(0xFF34C759), fontWeight = FontWeight.Bold)
    }
    Box(Modifier.padding(horizontal = 20.dp)) { PrimaryButton("Get Started") { repo.advance() } }
}

@Composable
private fun TwoActions(step: OnboardingStep, title: String, body: String, primary: String, secondary: String, repo: RotRunRepository, headline: String? = null) {
    Chrome(step, true, repo)
    if (headline != null) Text(headline, fontSize = 40.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp))
    Text(body, textAlign = TextAlign.Center, color = Melt.Muted, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp))
    CartoonBrain(BrainStyle.Flower, Modifier.size(120.dp).alignCenter())
    Box(Modifier.padding(horizontal = 20.dp)) { PrimaryButton(primary) { repo.advance() } }
    Text(secondary, color = Melt.Muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.alignCenter().padding(12.dp).clickable { repo.advance() })
}

@Composable
private fun WidgetSteps(repo: RotRunRepository) {
    Chrome(OnboardingStep.WidgetHowTo, true, repo)
    listOf(
        "Long Press your Home Screen" to "Tap and hold an empty area of your Home Screen until the menu appears.",
        "Tap Widgets" to "On Android, open the widget picker. iPhone uses a + button in the corner.",
        "Search for “RotRun”" to "Use the search bar at the top to find RotRun.",
        "Select the RotRun widget" to "Choose the brain health widget from the options.",
        "Tap “Add Widget”" to "Confirm adding the widget to your home screen.",
    ).forEachIndexed { index, (title, body) ->
        Row(Modifier.padding(horizontal = 22.dp, vertical = 8.dp)) {
            Text("${index + 1}", fontWeight = FontWeight.Black, color = Melt.Action, fontSize = 20.sp)
            Column(Modifier.padding(start = 12.dp)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(body, color = Melt.Muted)
            }
        }
    }
    Box(Modifier.padding(horizontal = 20.dp)) { PrimaryButton("Done") { repo.advance() } }
}

@Composable
private fun SelectApps(state: SavedState, installed: List<TrackedApp>, repo: RotRunRepository) {
    val selected = state.appLists.firstOrNull { it.id == "list.brainmelt" }?.apps.orEmpty()
    Chrome(OnboardingStep.SelectApps, true, repo)
    Text("Select your RotRun Apps", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 22.dp))
    Text("Select the apps that distract you the most, the ones you open on autopilot.", color = Melt.Muted, modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp))
    Text("RotRun Apps", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 22.dp))
    Text("You can update this later in Settings.", color = Melt.Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp))
    if (installed.isEmpty()) {
        Text("Open this screen again after RotRun loads your apps.", modifier = Modifier.padding(22.dp))
        LaunchedEffect(Unit) { repo.loadInstalled() }
    }
    installed.take(40).forEach { app ->
        val on = selected.any { it.packageName == app.packageName }
        Text(
            if (on) "✓  ${app.label}" else app.label,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp).clickable {
                repo.update { current ->
                    val lists = current.appLists.ifEmpty { listOf(AppList("list.brainmelt", "RotRun Apps")) }.map { list ->
                        if (list.id != "list.brainmelt") list
                        else {
                            val apps = if (on) list.apps.filterNot { it.packageName == app.packageName } else list.apps + app
                            list.copy(apps = apps)
                        }
                    }
                    val apps = lists.first { it.id == "list.brainmelt" }.apps
                    current.copy(appLists = lists, blockers = current.blockers.map { if (it.id == "brick") it.copy(apps = apps) else it })
                }
            },
        )
    }
    Box(Modifier.padding(horizontal = 20.dp)) { PrimaryButton("Continue") { repo.advance() } }
}

@Composable
private fun ScreenDialog(repo: RotRunRepository, onAllow: () -> Unit) {
    Chrome(OnboardingStep.ScreenTimeDialog, true, repo)
    Text("Connect RotRun to Screen Time", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 22.dp))
    Text("Your data is completely private and never leaves your device.", color = Melt.Muted, modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp))
    Column(Modifier.padding(22.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(16.dp)) {
        Text("“RotRun” Would Like to Access Screen Time", fontWeight = FontWeight.Bold)
        Text("Providing “RotRun” access to Screen Time may allow it to see your activity data, restrict content, and limit the usage of apps and websites.", color = Melt.Muted, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Text("Don't Allow", color = Melt.Action, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { repo.advance() })
            Text("Continue", color = Melt.Action, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onAllow(); repo.advance() })
        }
    }
}

@Composable
private fun EnableRule(repo: RotRunRepository) {
    Chrome(OnboardingStep.EnableFirstRule, true, repo)
    Text("Enable Your First Rule", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 22.dp))
    Text("Block your RotRun apps every day!", color = Melt.Muted, modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp))
    Column(Modifier.padding(22.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(16.dp)) {
        Text("Brick Mode", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("All Day", color = Melt.Muted)
    }
    Box(Modifier.padding(horizontal = 20.dp)) { PrimaryButton("Activate Rule") { repo.advance() } }
}

private fun Modifier.alignCenter(): Modifier = this.then(Modifier.fillMaxWidth()).padding(top = 4.dp)
