package com.ahmed.rotrun.data

import com.ahmed.rotrun.logic.Scoring

enum class OnboardingStep(val raw: Int) {
    Persona(0),
    ScreenTimeAffect(1),
    TriedReduce(2),
    RotMessage(3),
    SeeForYourself(4),
    LoseControl(5),
    ThisWeekHelp(6),
    WelcomeBrand(7),
    GoalWithApp(8),
    PersonalizeIntro(9),
    Personalizing(10),
    LimitedOffer(11),
    MeetBrain(12),
    Age(13),
    ChoosePlan(14),
    AttentionProfile(15),
    DailyEstimate(16),
    Committed(17),
    BeforeAfter(18),
    HoldToAscend(19),
    NotAddicted(20),
    DidYouKnow(21),
    SocialProof(22),
    CarouselWelcome(23),
    CarouselThemes(24),
    CarouselFocus(25),
    PeakFocus(26),
    Notifications(27),
    RulePreview(28),
    GetAccess(29),
    OneTimeOffer(30),
    SetGoal(31),
    ConnectScreenTime(32),
    PurchaseComplete(33),
    ThemeUnlocked(34),
    WidgetHowTo(35),
    StreakReward(36),
    SelectApps(37),
    ScreenTimeDialog(38),
    EnableFirstRule(39),
    ;

    fun progress(): Float = if (this == GetAccess) 0.82f else raw / 39f

    fun skipped(state: SavedState): Boolean = when (this) {
        LimitedOffer, ChoosePlan -> true
        PurchaseComplete -> !state.premiumActive()
        GetAccess -> state.premiumActive()
        OneTimeOffer -> state.premiumActive() || !state.declinedGetAccess || state.skippedOneTimeOffer
        else -> false
    }

    companion object {
        fun fromRaw(raw: Int): OnboardingStep = entries.firstOrNull { it.raw == raw } ?: Persona
    }
}

data class TrackedApp(val packageName: String, val label: String)

data class AppList(val id: String, val name: String, val apps: List<TrackedApp> = emptyList())

data class Blocker(
    val id: String,
    val name: String,
    val mode: String,
    val appListId: String = "list.brainmelt",
    val startHour: Int = 9,
    val endHour: Int = 17,
    val limitMinutes: Int = 30,
    val days: List<Int> = (0..6).toList(),
    val apps: List<TrackedApp> = emptyList(),
)

data class FocusSession(
    val startedAt: Long,
    val targetMinutes: Int?,
    val legendary: Boolean,
    val scope: String,
)

data class SavedState(
    val onboardingCompleted: Boolean = false,
    val onboardingStep: Int = 0,
    val pendingResumeStep: Int? = null,
    val premium: Boolean = false,
    val productId: String? = null,
    val premiumUntil: Long = 0L,
    val introUsed: Boolean = false,
    val declinedGetAccess: Boolean = false,
    val skippedOneTimeOffer: Boolean = false,
    val persona: String? = null,
    val screenTimeAffect: String? = null,
    val triedReduce: String? = null,
    val loseControl: String? = null,
    val goal: String? = null,
    val ageRange: String? = null,
    val rotReveal: Float = 0.35f,
    val estimatedDailyHours: Int = 6,
    val peakFocus: String = "Work Focus",
    val goalHours: Int = 4,
    val brainHealth: String = "Medium",
    val theme: String = "Grassy",
    val appIcon: String = "Default",
    val standardDialogue: Boolean = true,
    val breathingExercise: Boolean = true,
    val mirrorMode: Boolean = true,
    val quickCheck: Boolean = true,
    val friendCameo: Boolean = true,
    val streak: Int = 1,
    val lastOpenDay: String = "",
    val askBrainCount: Int = 0,
    val ransomDebit: Int = 0,
    val xpDay: String = "",
    val focusMinutesByDay: Map<String, Int> = emptyMap(),
    val appLists: List<AppList> = emptyList(),
    val blockers: List<Blocker> = emptyList(),
    val excluded: List<TrackedApp> = emptyList(),
    val unlockUntil: Long = 0L,
    val focus: FocusSession? = null,
    val playGamesConnected: Boolean = false,
    val cameoName: String = "",
    val cameoKind: String = "",
    val cameoDay: String = "",
    val duelName: String = "",
    val duelScore: Int = 0,
    val duelUntil: Long = 0L,
    val lastAskToken: Long = 0L,
    val bailoutFrom: String = "",
    val workoutsLogged: Int = 0,
) {
    fun premiumActive(now: Long = System.currentTimeMillis()): Boolean = premium && premiumUntil > now

    fun cameoToday(day: String = Scoring.dayKey()): Boolean = cameoName.isNotBlank() && cameoDay == day

    fun focusMinutesToday(day: String = Scoring.dayKey()): Int = focusMinutesByDay[day] ?: 0
}

fun freshState(): SavedState = SavedState(
    appLists = listOf(AppList(id = "list.brainmelt", name = "RotRun Apps")),
    blockers = listOf(
        Blocker(id = "brick", name = "Brick Mode", mode = "allDay"),
        Blocker(
            id = "work",
            name = "Work Focus",
            mode = "schedule",
            startHour = 9,
            endHour = 17,
            days = listOf(1, 2, 3, 4, 5),
        ),
    ),
)

data class Offender(val packageName: String, val label: String, val seconds: Long)

data class DayUsage(val day: String, val label: String, val seconds: Long)

data class UsageSnapshot(
    val authorized: Boolean = false,
    val todaySeconds: Long = 0,
    val drainSeconds: Long = 0,
    val pickups: Int = 0,
    val avgSeconds: Long = 0,
    val diffPercent: Int = 0,
    val belowAverage: Boolean = true,
    val aheadOfPace: Boolean = false,
    val offenders: List<Offender> = emptyList(),
    val hourlyToday: List<Float> = List(24) { 0f },
    val hourlyAvg: List<Float> = List(24) { 0f },
    val week: List<DayUsage> = emptyList(),
)
