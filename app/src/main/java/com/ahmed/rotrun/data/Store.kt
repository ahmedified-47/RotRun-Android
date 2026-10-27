package com.ahmed.rotrun.data

import android.content.Context
import com.ahmed.rotrun.logic.Products
import com.ahmed.rotrun.logic.Scoring
import com.ahmed.rotrun.watch.WatchController
import com.ahmed.rotrun.widget.BrainWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

sealed interface UiPulse {
    data object OpenFocusChooser : UiPulse
    data object ShowAudioSlap : UiPulse
}

class RotRunRepository(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("rotrun", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lock = Any()

    private val _state = MutableStateFlow(load())
    val state: StateFlow<SavedState> = _state.asStateFlow()

    private val _usage = MutableStateFlow(UsageSnapshot())
    val usage: StateFlow<UsageSnapshot> = _usage.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _installed = MutableStateFlow<List<TrackedApp>>(emptyList())
    val installed: StateFlow<List<TrackedApp>> = _installed.asStateFlow()

    private val _pulses = MutableSharedFlow<UiPulse>(extraBufferCapacity = 8)
    val pulses: SharedFlow<UiPulse> = _pulses.asSharedFlow()

    fun clearToast() {
        _toast.value = null
    }

    fun toast(message: String) {
        _toast.value = message
    }

    fun onLaunch() {
        update { current ->
            var next = rollDay(current)
            next = expireFocus(next)
            next = clampRewards(next)
            if (!next.onboardingCompleted) {
                val step = OnboardingStep.fromRaw(next.onboardingStep)
                if (!next.premiumActive() && step.raw > OnboardingStep.OneTimeOffer.raw) {
                    next = next.copy(pendingResumeStep = step.raw, onboardingStep = OnboardingStep.GetAccess.raw)
                }
            }
            next
        }
        refreshUsage()
        loadInstalled()
    }

    fun refreshUsage() {
        _refreshing.value = true
        scope.launch {
            val snapshot = withContext(Dispatchers.Default) { UsageReader.read(app, _state.value) }
            _usage.value = snapshot
            publishWidget()
            _refreshing.value = false
        }
    }

    fun loadInstalled() {
        scope.launch {
            val apps = withContext(Dispatchers.Default) { UsageReader.installedApps(app) }
            _installed.value = apps
        }
    }

    fun update(block: (SavedState) -> SavedState) {
        val next: SavedState
        synchronized(lock) {
            next = block(_state.value)
            _state.value = next
            persist(next)
        }
        publishWidget()
        WatchController.sync(app, next)
    }

    fun advance() = update { stepForward(it) }

    fun back() = update { stepBack(it) }

    fun declineAccess() = update { current ->
        if (current.skippedOneTimeOffer) current
        else current.copy(declinedGetAccess = true, onboardingStep = OnboardingStep.OneTimeOffer.raw)
    }

    fun leaveOffer() = update { it.copy(skippedOneTimeOffer = true, onboardingStep = OnboardingStep.GetAccess.raw) }

    fun purchase(productId: String) {
        update { current ->
            val days = if (productId == Products.MONTHLY) 30L else 365L
            val until = System.currentTimeMillis() + days * 24L * 60L * 60L * 1000L
            var next = current.copy(
                premium = true,
                productId = productId,
                premiumUntil = until,
                introUsed = true,
            )
            if (!next.onboardingCompleted) {
                val pending = next.pendingResumeStep
                next = if (pending != null) {
                    next.copy(onboardingStep = pending, pendingResumeStep = null)
                } else {
                    stepForward(next)
                }
            }
            next
        }
        toast("RotRun Premium is on.")
    }

    fun restore() {
        val active = _state.value.premiumActive()
        toast(
            if (active) "Purchases restored."
            else "No active RotRun Premium purchase found for this account.",
        )
    }

    fun saveBlocker(blocker: Blocker) {
        update { current ->
            val without = current.blockers.filterNot { it.id == blocker.id }
            val lists = if (blocker.id == "brick") {
                current.appLists.map { list ->
                    if (list.id == blocker.appListId) list.copy(apps = blocker.apps) else list
                }
            } else {
                current.appLists
            }
            current.copy(blockers = listOf(blocker) + without, appLists = lists)
        }
        toast("Saved ${blocker.name}")
    }

    fun deleteBlocker(id: String) {
        val name = _state.value.blockers.firstOrNull { it.id == id }?.name ?: "Blocker"
        update { current ->
            if (id == "brick") current else current.copy(blockers = current.blockers.filterNot { it.id == id })
        }
        toast("Deleted $name")
    }

    fun saveList(list: AppList) {
        update { current ->
            val lists = current.appLists.toMutableList()
            val index = lists.indexOfFirst { it.id == list.id }
            if (index >= 0) lists[index] = list else lists.add(list)
            val blockers = current.blockers.map { blocker ->
                if (blocker.appListId == list.id && blocker.id == "brick") blocker.copy(apps = list.apps) else blocker
            }
            current.copy(appLists = lists, blockers = blockers)
        }
    }

    fun startFocus(targetMinutes: Int?, legendary: Boolean, scopeName: String) {
        val session = FocusSession(
            startedAt = System.currentTimeMillis(),
            targetMinutes = if (legendary) null else targetMinutes,
            legendary = legendary,
            scope = scopeName,
        )
        update { it.copy(focus = session) }
        val target = if (legendary || targetMinutes == null) "As long as you can!" else "${targetMinutes}m"
        val apps = if (scopeName == "brainrot") "Brainrot Apps" else "All Apps"
        val legend = if (legendary) " · Legendary" else ""
        toast("Focus started · $target · $apps$legend")
    }

    fun endFocus() {
        val had = _state.value.focus != null
        update { expireFocus(it, force = true) }
        if (had) toast("Focus ended")
    }

    fun maybeExpireFocus() {
        update { expireFocus(it, force = false) }
    }

    fun chargeAskBrain(token: Long) {
        update { current ->
            if (token == current.lastAskToken) current
            else current.copy(askBrainCount = current.askBrainCount + 1, lastAskToken = token)
        }
    }

    fun grantUnlock(minutes: Int): Boolean {
        if (_state.value.focus != null) return false
        val until = System.currentTimeMillis() + minutes * 60_000L
        update { it.copy(unlockUntil = until) }
        return true
    }

    fun payRansom() {
        update { it.copy(ransomDebit = it.ransomDebit + 100) }
        toast("−100 XP · ransom paid")
    }

    fun noteBailout(from: String) {
        val name = from.ifBlank { "a friend" }.take(40)
        update {
            it.copy(cameoName = name, cameoKind = "bailout", cameoDay = Scoring.dayKey(), bailoutFrom = name)
        }
    }

    fun clearBailout() = update { it.copy(bailoutFrom = "") }

    fun noteAudioSlap(from: String) {
        val name = from.ifBlank { "a friend" }.take(40)
        update { it.copy(cameoName = name, cameoKind = "audioSlap", cameoDay = Scoring.dayKey()) }
        _pulses.tryEmit(UiPulse.ShowAudioSlap)
    }

    fun pinDuel(name: String, theirScore: Int = 0) {
        val who = name.ifBlank { "a friend" }.take(40)
        val midnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        update {
            it.copy(
                cameoName = who,
                cameoKind = "duel",
                cameoDay = Scoring.dayKey(),
                duelName = who,
                duelScore = theirScore,
                duelUntil = midnight,
            )
        }
        toast("Duel pinned vs $who. More Daily XP before midnight.")
    }

    fun connectPlayGames() = update { it.copy(playGamesConnected = true) }

    fun logWorkout() {
        update { it.copy(workoutsLogged = it.workoutsLogged + 1) }
        toast("Saved on this device")
    }

    fun requestFocusChooser() {
        _pulses.tryEmit(UiPulse.OpenFocusChooser)
    }

    fun pickStyle(): String? {
        val current = _state.value
        val styles = buildList {
            if (current.standardDialogue) add("standard")
            if (current.mirrorMode) add("mirror")
            if (current.quickCheck) add("quick")
            if (current.breathingExercise) add("breathing")
            if (current.friendCameo && current.cameoToday()) add("cameo")
        }
        return styles.randomOrNull()
    }

    fun isBlocked(packageName: String, safety: Set<String>): Boolean {
        val current = _state.value
        if (packageName in safety) return false
        if (System.currentTimeMillis() < current.unlockUntil) return false
        if (current.excluded.any { it.packageName == packageName }) return false
        val focus = current.focus
        if (focus != null) {
            return if (focus.scope == "brainrot") brainrotPackages(current).contains(packageName) else true
        }
        return brickPackages(current).contains(packageName)
    }

    fun safetyPackages(): Set<String> = UsageReader.safetyPackages(app)

    private fun brickPackages(current: SavedState): Set<String> {
        val brick = current.blockers.firstOrNull { it.id == "brick" } ?: return emptySet()
        val fromList = current.appLists.firstOrNull { it.id == brick.appListId }?.apps.orEmpty()
        val apps = if (brick.apps.isNotEmpty()) brick.apps else fromList
        return apps.map { it.packageName }.toSet()
    }

    private fun brainrotPackages(current: SavedState): Set<String> = brickPackages(current)

    private fun rollDay(current: SavedState): SavedState {
        val today = Scoring.dayKey()
        val yesterday = Scoring.dayKey(LocalDate.now().minusDays(1))
        val streak = when (current.lastOpenDay) {
            "" -> maxOf(1, current.streak)
            today -> maxOf(1, current.streak)
            yesterday -> current.streak + 1
            else -> 1
        }
        var next = current.copy(streak = streak, lastOpenDay = today)
        if (next.xpDay != today) {
            next = next.copy(xpDay = today, askBrainCount = 0, ransomDebit = 0)
        }
        return next
    }

    private fun clampRewards(current: SavedState): SavedState {
        var next = current
        if (themeStreak(next.theme) > next.streak) next = next.copy(theme = "Grassy")
        if (iconStreak(next.appIcon) > next.streak) next = next.copy(appIcon = "Default")
        return next
    }

    private fun expireFocus(current: SavedState, force: Boolean = false): SavedState {
        val session = current.focus ?: return current
        val elapsed = System.currentTimeMillis() - session.startedAt
        val reached = session.targetMinutes != null && elapsed >= session.targetMinutes * 60_000L
        if (!force && !reached) return current
        val cap = session.targetMinutes?.times(60L)
        val creditSeconds = if (cap == null) elapsed / 1000 else minOf(elapsed / 1000, cap)
        val creditMinutes = (creditSeconds / 60).toInt().coerceAtLeast(0)
        val day = Scoring.dayKey()
        val map = current.focusMinutesByDay.toMutableMap()
        map[day] = (map[day] ?: 0) + creditMinutes
        return current.copy(focus = null, focusMinutesByDay = map)
    }

    private fun publishWidget() {
        val current = _state.value
        val level = com.ahmed.rotrun.logic.BrainHealthLevel.fromLabel(current.brainHealth)
        val drain = Scoring.drainHealth(_usage.value.drainSeconds, level)
        val health = Scoring.displayedHealth(drain, current.focusMinutesToday(), level.restoreMinutes)
        prefs.edit()
            .putInt("widget_health", health)
            .putString("widget_caption", Scoring.caption(health))
            .putInt("widget_streak", current.streak)
            .apply()
        BrainWidgetProvider.updateAll(app)
    }

    private fun load(): SavedState {
        val raw = prefs.getString("state", null) ?: return freshState()
        return runCatching { decode(JSONObject(raw)) }.getOrElse { freshState() }
    }

    private fun persist(state: SavedState) {
        prefs.edit().putString("state", encode(state).toString()).apply()
    }

    private fun encode(state: SavedState): JSONObject = JSONObject().apply {
        put("onboardingCompleted", state.onboardingCompleted)
        put("onboardingStep", state.onboardingStep)
        put("pendingResumeStep", state.pendingResumeStep ?: -1)
        put("premium", state.premium)
        put("productId", state.productId ?: "")
        put("premiumUntil", state.premiumUntil)
        put("introUsed", state.introUsed)
        put("declinedGetAccess", state.declinedGetAccess)
        put("skippedOneTimeOffer", state.skippedOneTimeOffer)
        put("persona", state.persona ?: "")
        put("screenTimeAffect", state.screenTimeAffect ?: "")
        put("triedReduce", state.triedReduce ?: "")
        put("loseControl", state.loseControl ?: "")
        put("goal", state.goal ?: "")
        put("ageRange", state.ageRange ?: "")
        put("rotReveal", state.rotReveal.toDouble())
        put("estimatedDailyHours", state.estimatedDailyHours)
        put("peakFocus", state.peakFocus)
        put("goalHours", state.goalHours)
        put("brainHealth", state.brainHealth)
        put("theme", state.theme)
        put("appIcon", state.appIcon)
        put("standardDialogue", state.standardDialogue)
        put("breathingExercise", state.breathingExercise)
        put("mirrorMode", state.mirrorMode)
        put("quickCheck", state.quickCheck)
        put("friendCameo", state.friendCameo)
        put("streak", state.streak)
        put("lastOpenDay", state.lastOpenDay)
        put("askBrainCount", state.askBrainCount)
        put("ransomDebit", state.ransomDebit)
        put("xpDay", state.xpDay)
        put("focusMinutesByDay", JSONObject(state.focusMinutesByDay.mapValues { it.value }))
        put("appLists", JSONArray().apply { state.appLists.forEach { put(encodeList(it)) } })
        put("blockers", JSONArray().apply { state.blockers.forEach { put(encodeBlocker(it)) } })
        put("excluded", JSONArray().apply { state.excluded.forEach { put(encodeApp(it)) } })
        put("unlockUntil", state.unlockUntil)
        state.focus?.let { put("focus", encodeFocus(it)) }
        put("playGamesConnected", state.playGamesConnected)
        put("cameoName", state.cameoName)
        put("cameoKind", state.cameoKind)
        put("cameoDay", state.cameoDay)
        put("duelName", state.duelName)
        put("duelScore", state.duelScore)
        put("duelUntil", state.duelUntil)
        put("lastAskToken", state.lastAskToken)
        put("bailoutFrom", state.bailoutFrom)
        put("workoutsLogged", state.workoutsLogged)
    }

    private fun decode(json: JSONObject): SavedState {
        val fresh = freshState()
        val lists = json.optJSONArray("appLists").toLists().ifEmpty { fresh.appLists }
        val blockers = json.optJSONArray("blockers").toBlockers().ifEmpty { fresh.blockers }
        val focusJson = json.optJSONObject("focus")
        return SavedState(
            onboardingCompleted = json.optBoolean("onboardingCompleted"),
            onboardingStep = json.optInt("onboardingStep"),
            pendingResumeStep = json.optInt("pendingResumeStep", -1).takeIf { it >= 0 },
            premium = json.optBoolean("premium"),
            productId = json.optString("productId").ifBlank { null },
            premiumUntil = json.optLong("premiumUntil"),
            introUsed = json.optBoolean("introUsed"),
            declinedGetAccess = json.optBoolean("declinedGetAccess"),
            skippedOneTimeOffer = json.optBoolean("skippedOneTimeOffer"),
            persona = json.optString("persona").ifBlank { null },
            screenTimeAffect = json.optString("screenTimeAffect").ifBlank { null },
            triedReduce = json.optString("triedReduce").ifBlank { null },
            loseControl = json.optString("loseControl").ifBlank { null },
            goal = json.optString("goal").ifBlank { null },
            ageRange = json.optString("ageRange").ifBlank { null },
            rotReveal = json.optDouble("rotReveal", 0.35).toFloat(),
            estimatedDailyHours = json.optInt("estimatedDailyHours", 6),
            peakFocus = json.optString("peakFocus", "Work Focus"),
            goalHours = json.optInt("goalHours", 4),
            brainHealth = json.optString("brainHealth", "Medium"),
            theme = json.optString("theme", "Grassy"),
            appIcon = json.optString("appIcon", "Default"),
            standardDialogue = json.optBoolean("standardDialogue", true),
            breathingExercise = json.optBoolean("breathingExercise", true),
            mirrorMode = json.optBoolean("mirrorMode", true),
            quickCheck = json.optBoolean("quickCheck", true),
            friendCameo = json.optBoolean("friendCameo", true),
            streak = json.optInt("streak", 1),
            lastOpenDay = json.optString("lastOpenDay"),
            askBrainCount = json.optInt("askBrainCount"),
            ransomDebit = json.optInt("ransomDebit"),
            xpDay = json.optString("xpDay"),
            focusMinutesByDay = json.optJSONObject("focusMinutesByDay").toIntMap(),
            appLists = lists,
            blockers = blockers,
            excluded = json.optJSONArray("excluded").toApps(),
            unlockUntil = json.optLong("unlockUntil"),
            focus = focusJson?.let {
                FocusSession(
                    startedAt = it.optLong("startedAt"),
                    targetMinutes = if (it.has("targetMinutes") && !it.isNull("targetMinutes")) it.optInt("targetMinutes") else null,
                    legendary = it.optBoolean("legendary"),
                    scope = it.optString("scope", "all"),
                )
            },
            playGamesConnected = json.optBoolean("playGamesConnected"),
            cameoName = json.optString("cameoName"),
            cameoKind = json.optString("cameoKind"),
            cameoDay = json.optString("cameoDay"),
            duelName = json.optString("duelName"),
            duelScore = json.optInt("duelScore"),
            duelUntil = json.optLong("duelUntil"),
            lastAskToken = json.optLong("lastAskToken"),
            bailoutFrom = json.optString("bailoutFrom"),
            workoutsLogged = json.optInt("workoutsLogged"),
        )
    }
}

fun themeStreak(theme: String): Int = when (theme) {
    "Sunrise" -> 3
    "Zen" -> 7
    "Twilight" -> 14
    "Cosmic" -> 30
    else -> 0
}

fun iconStreak(icon: String): Int = when (icon) {
    "Spark" -> 3
    "Zen" -> 7
    "Scholar" -> 14
    "Cosmic" -> 30
    else -> 0
}

fun stepForward(state: SavedState): SavedState {
    val step = OnboardingStep.fromRaw(state.onboardingStep)
    if (!state.premiumActive() && step.raw > OnboardingStep.OneTimeOffer.raw) {
        return state.copy(onboardingStep = OnboardingStep.GetAccess.raw, pendingResumeStep = step.raw)
    }
    if (!state.premiumActive() && step in setOf(
            OnboardingStep.GetAccess,
            OnboardingStep.OneTimeOffer,
            OnboardingStep.LimitedOffer,
            OnboardingStep.ChoosePlan,
        )
    ) {
        return state
    }
    var cursor = step.raw + 1
    while (cursor <= OnboardingStep.EnableFirstRule.raw) {
        val next = OnboardingStep.fromRaw(cursor)
        if (!next.skipped(state)) return state.copy(onboardingStep = next.raw)
        cursor++
    }
    return if (state.premiumActive()) state.copy(onboardingCompleted = true) else state.copy(onboardingStep = OnboardingStep.GetAccess.raw)
}

fun stepBack(state: SavedState): SavedState {
    if (state.onboardingStep <= 0) return state
    var cursor = state.onboardingStep - 1
    while (cursor > 0) {
        val next = OnboardingStep.fromRaw(cursor)
        if (!next.skipped(state)) return state.copy(onboardingStep = next.raw)
        cursor--
    }
    return state.copy(onboardingStep = 0)
}

private fun encodeApp(app: TrackedApp) = JSONObject().put("packageName", app.packageName).put("label", app.label)

private fun encodeList(list: AppList) = JSONObject()
    .put("id", list.id)
    .put("name", list.name)
    .put("apps", JSONArray().apply { list.apps.forEach { put(encodeApp(it)) } })

private fun encodeBlocker(blocker: Blocker) = JSONObject()
    .put("id", blocker.id)
    .put("name", blocker.name)
    .put("mode", blocker.mode)
    .put("appListId", blocker.appListId)
    .put("startHour", blocker.startHour)
    .put("endHour", blocker.endHour)
    .put("limitMinutes", blocker.limitMinutes)
    .put("days", JSONArray(blocker.days))
    .put("apps", JSONArray().apply { blocker.apps.forEach { put(encodeApp(it)) } })

private fun encodeFocus(session: FocusSession) = JSONObject()
    .put("startedAt", session.startedAt)
    .put("legendary", session.legendary)
    .put("scope", session.scope)
    .apply { if (session.targetMinutes != null) put("targetMinutes", session.targetMinutes) }

private fun JSONArray?.toApps(): List<TrackedApp> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val packageName = item.optString("packageName")
            if (packageName.isBlank()) continue
            add(TrackedApp(packageName, item.optString("label", packageName)))
        }
    }
}

private fun JSONArray?.toLists(): List<AppList> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            add(AppList(item.optString("id"), item.optString("name"), item.optJSONArray("apps").toApps()))
        }
    }
}

private fun JSONArray?.toBlockers(): List<Blocker> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val daysJson = item.optJSONArray("days")
            val days = if (daysJson == null) {
                (0..6).toList()
            } else {
                buildList { for (day in 0 until daysJson.length()) add(daysJson.optInt(day)) }
            }
            add(
                Blocker(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    mode = item.optString("mode", "allDay"),
                    appListId = item.optString("appListId", "list.brainmelt"),
                    startHour = item.optInt("startHour", 9),
                    endHour = item.optInt("endHour", 17),
                    limitMinutes = item.optInt("limitMinutes", 30),
                    days = days,
                    apps = item.optJSONArray("apps").toApps(),
                ),
            )
        }
    }
}

private fun JSONObject?.toIntMap(): Map<String, Int> {
    if (this == null) return emptyMap()
    return buildMap {
        val keys = keys()
        while (keys.hasNext()) {
            val key = keys.next()
            put(key, optInt(key))
        }
    }
}
