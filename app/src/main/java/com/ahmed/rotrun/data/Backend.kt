package com.ahmed.rotrun.data

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.ahmed.rotrun.BuildConfig
import com.ahmed.rotrun.billing.PlayPurchaseUpload
import com.ahmed.rotrun.billing.PlaySyncResult
import com.ahmed.rotrun.billing.ServerEntitlement
import com.ahmed.rotrun.logic.BoardSnapshot
import com.ahmed.rotrun.logic.DailyBoard
import com.ahmed.rotrun.logic.RankedXp
import com.ahmed.rotrun.logic.XpRow
import com.ahmed.rotrun.logic.BlockBackup
import com.ahmed.rotrun.logic.FreeTier
import com.ahmed.rotrun.logic.PaywallGate
import com.ahmed.rotrun.logic.Scoring
import com.ahmed.rotrun.logic.VoiceLimits
import com.ahmed.rotrun.logic.VoiceSession
import com.ahmed.rotrun.logic.ScreenTimeSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.SecureRandom
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ChatCard(val type: String, val data: Map<String, String>)

data class ProfileDraft(val givenName: String, val familyName: String, val avatarUrl: String?)

data class ProfileResult(val saved: Boolean, val message: String?)

class Backend(private val app: Context, private val repo: RotRunRepository) {
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private val playHttp = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    private val jsonType = "application/json".toMediaType()
    private val session = openSession(app)
    private var voice: VoiceCall? = null
    private var playSync: (suspend () -> PlaySyncResult)? = null
    var onSignedIn: (() -> Unit)? = null
    private val cardLock = Any()
    private var pendingCards: List<ChatCard> = emptyList()
    val signedIn: Boolean get() = accessToken().isNotBlank()
    val userId: String? get() = session.getString("user_id", null)
    private val _needsProfile = MutableStateFlow(false)
    val needsProfile: StateFlow<Boolean> = _needsProfile.asStateFlow()

    init {
        syncProfileGate()
    }

    fun restore() {
        val token = accessToken()
        if (token.isBlank()) return
        if (expiresAt() < System.currentTimeMillis() + 60_000) {
            refresh()
        }
    }

    fun signIn(activity: Activity, onDone: (String?) -> Unit) {
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (clientId.isBlank()) {
            onDone("Sign-in isn’t set up yet")
            return
        }
        val nonce = randomNonce()
        val hashed = GoogleNonce.hash(nonce)
        GoogleSignIn.start(activity, clientId, hashed) { result ->
            // Credential Manager returns on the main thread. The token exchange is a network call.
            CoroutineScope(Dispatchers.IO).launch {
                val error = result.fold(
                    onSuccess = { idToken -> exchange(idToken, nonce) },
                    onFailure = { GoogleSignInPolicy.message(it.javaClass.name, it.message.orEmpty()) },
                )
                if (activity.isDestroyed) return@launch
                withContext(Dispatchers.Main) { onDone(error) }
            }
        }
    }

    fun signOut() {
        voice?.stop()
        session.edit().clear().apply()
        app.getSharedPreferences("rotrun", Context.MODE_PRIVATE).edit().remove("chat.turns").apply()
        flags().edit { remove(XP_POST) }
        syncProfileGate()
    }

    fun signInLater(): Boolean = flags().getBoolean(PROFILE_LATER, false)

    fun markSignInLater() {
        flags().edit().putBoolean(PROFILE_LATER, true).apply()
    }

    fun bindPlaySync(block: suspend () -> PlaySyncResult) {
        playSync = block
    }

    fun profileDraft(): ProfileDraft {
        val given = session.getString(GIVEN_NAME, null)?.trim().orEmpty()
        val family = session.getString(FAMILY_NAME, null)?.trim().orEmpty()
        if (given.isNotEmpty() || family.isNotEmpty()) return ProfileDraft(given, family, storedAvatar())
        val split = DailyBoard.splitName(session.getString("full_name", null) ?: session.getString("display_name", null))
        return ProfileDraft(split.first, split.second, storedAvatar())
    }

    fun hasLocalDisplayName(): Boolean {
        if (DailyBoard.boardName(session.getString(GIVEN_NAME, null), session.getString(FAMILY_NAME, null)) != null) return true
        return DailyBoard.isBoardName(session.getString("display_name", null)) ||
            DailyBoard.isBoardName(session.getString("full_name", null))
    }

    /** Copies a real name from auth metadata or daily_xp. Returns true when a name is saved. */
    fun adoptRemoteDisplayName(): Boolean {
        if (!signedIn) return false
        if (hasLocalDisplayName()) return true
        val meta = googleName(call("GET", "/auth/v1/user", null, accessToken()).json)
        if (DailyBoard.isBoardName(meta)) {
            rememberChosenName(meta!!)
            return true
        }
        val id = userId ?: return false
        val rows = call(
            "GET",
            "/rest/v1/daily_xp?user_id=eq.$id&select=display_name&order=day.desc&limit=8",
            null,
            accessToken(),
        ).array ?: return false
        for (index in 0 until rows.length()) {
            val name = rows.optJSONObject(index)?.optString("display_name")
            if (DailyBoard.isBoardName(name)) {
                rememberChosenName(name!!)
                return true
            }
        }
        return false
    }

    fun saveProfile(givenName: String, familyName: String, jpeg: ByteArray?): ProfileResult {
        val shown = DailyBoard.boardName(givenName, familyName) ?: return ProfileResult(false, "Enter your first and last name.")
        val id = userId ?: return ProfileResult(false, "Sign in first.")
        if (accessToken().isBlank()) return ProfileResult(false, "Sign in first.")
        if (jpeg != null && jpeg.size > ProfilePhoto.MAX_BYTES) return ProfileResult(false, "That photo is still over 2 MB.")
        var warning: String? = null
        var avatar = storedAvatar()
        if (jpeg != null) {
            val uploaded = uploadAvatar(id, jpeg)
            if (uploaded.error != null) return ProfileResult(false, uploaded.error)
            avatar = uploaded.url
        }
        val data = JSONObject().put("full_name", shown)
        if (!avatar.isNullOrBlank()) data.put("avatar_url", avatar)
        val meta = call("PUT", "/auth/v1/user", JSONObject().put("data", data).toString(), accessToken())
        if (meta.code !in 200..299) return ProfileResult(false, failText(meta, "Couldn’t save your profile."))
        session.edit()
            .putString(GIVEN_NAME, givenName.trim())
            .putString(FAMILY_NAME, familyName.trim())
            .putString("full_name", shown)
            .putString("display_name", shown)
            .putString("avatar_url", avatar.orEmpty())
            .apply()
        flags().edit().putString(PROFILE_DONE, id).remove(XP_FINGER).remove(BOARD_CACHE).apply()
        val row = JSONObject()
            .put("display_name", shown)
            .put("avatar_url", if (avatar.isNullOrBlank()) JSONObject.NULL else avatar)
            .put("updated_at", ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        val patched = call("PATCH", "/rest/v1/daily_xp?user_id=eq.$id", row.toString(), accessToken(), "return=minimal")
        if (patched.code !in 200..299) warning = failText(patched, "Couldn’t update the leaderboard row.")
        syncProfileGate()
        return ProfileResult(true, warning)
    }

    fun deleteAccount(): String? {
        val token = accessToken()
        if (token.isBlank()) return "Sign in first."
        val response = call(
            "POST",
            "/functions/v1/delete-account",
            JSONObject().toString(),
            token,
        )
        if (response.code !in 200..299) return response.error ?: "Couldn’t delete the account (${response.code})"
        signOut()
        return null
    }

    fun grantConsent(): String? {
        val id = userId ?: return "Sign in first."
        session.edit().putBoolean("ai_consent", true).putString("ai_consent_user", id).apply()
        val body = JSONObject()
            .put("agent_enabled", true)
            .put("ai_consent_at", ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        val response = call("PATCH", "/rest/v1/profiles?user_id=eq.$id", body.toString(), accessToken())
        return if (response.code in 200..299) null else response.error
    }

    fun needsConsent(): Boolean {
        val id = userId ?: return true
        return !(session.getBoolean("ai_consent", false) && session.getString("ai_consent_user", "") == id)
    }

    fun seedGreeting() {
        if (turns().isNotEmpty()) return
        remember("assistant", "Hey, how are you? How can I help you?")
    }

    fun chatCards(): List<ChatCard> = synchronized(cardLock) { pendingCards.toList() }

    fun clearChatCards() {
        synchronized(cardLock) { pendingCards = emptyList() }
    }

    fun sendChat(message: String, surface: String): String {
        val text = message.trim()
        if (text.isEmpty()) return ""
        if (!signedIn) return "Sign in with Google to talk to your brain. Your conversations help it learn about you and give better advice."
        if (needsConsent()) return "Rot needs your OK before chatting."
        val state = repo.state.value
        if (!canSend(state)) {
            repo.requestPaywall()
            return FreeTier.limitMessage
        }
        localReply(text, state)?.let { local ->
            remember("user", text)
            remember("assistant", local)
            return local
        }
        val turns = loadTurns()
        val recent = JSONArray()
        val start = maxOf(0, turns.length() - 12)
        for (index in start until turns.length()) recent.put(turns.get(index))
        val body = JSONObject()
            .put("type", "chat")
            .put("surface", surface)
            .put("message", text)
            .put("recent_turns", recent)
            .put("session_turn_count", turns.length())
            .put("end_session", false)
            .put("client_context", context(state))
        val response = call("POST", "/functions/v1/agent-qa", body.toString(), accessToken())
        if (response.code == 401) {
            refresh()
            return "Your session expired. Please sign in again."
        }
        val payload = response.json
        val code = payload?.optString("error_code").orEmpty()
        val resets = payload?.optString("budget_resets_at").orEmpty()
        if (code == "consent_required") return "Rot needs your OK before chatting."
        if (code == "paywall_required") {
            repo.requestPaywall()
            return FreeTier.limitMessage
        }
        if (code == "hourly_limit") return FreeTier.hourlyMessage
        if (code == "daily_limit") return FreeTier.serverChatDone
        if (code == "monthly_limit" || code == "budget_exhausted" || code == "user_budget_exhausted") {
            return if (resets.isNotBlank()) FreeTier.periodMessage(dayLabel(resets)) else FreeTier.serverChatDone
        }
        if (response.code !in 200..299) return payload?.optString("error").takeUnless { it.isNullOrBlank() } ?: FreeTier.tryAgain
        val answer = payload?.optString("text").orEmpty()
        if (answer.isBlank()) return payload?.optString("error").takeUnless { it.isNullOrBlank() } ?: FreeTier.tryAgain
        remember("user", text)
        remember("assistant", answer)
        absorbCards(payload?.optJSONArray("cards"))
        if (PaywallGate.shows && !state.proFor(userId)) {
            repo.update { current ->
                val day = Scoring.dayKey()
                val count = if (current.chatDay == day) current.chatCount else 0
                current.copy(chatDay = day, chatCount = count + 1)
            }
        }
        return answer
    }

    suspend fun startVoice(onUpdate: (String, String) -> Unit = { _, _ -> }, allowRetry: Boolean = true): String? {
        val state = repo.state.value
        if (!state.proFor(userId)) {
            if (PaywallGate.shows) repo.requestPaywall()
            return FreeTier.voiceMessage
        }
        if (!signedIn) return "Sign in with Google to talk to your brain."
        if (needsConsent()) return "Rot needs your OK before a voice call."
        if (voiceBlocked()) return session.getString("voice_limit_message", VoiceLimits.dailyMessage)
        voiceAllowance()?.let { message ->
            session.edit().putString("voice_limit_message", message).putLong("voice_limit_until", voiceResetAt(message)).apply()
            return message
        }
        val response = call("POST", "/functions/v1/agent-qa", JSONObject().put("type", "voice_secret").toString(), accessToken())
        val payload = response.json
        val code = payload?.optString("error_code").orEmpty()
        if (VoiceSession.isPaywallRequired(code)) {
            return paywallVoice(state.proFor(userId), onUpdate, allowRetry)
        }
        if (VoiceSession.isVoiceLimit(response.code, code)) {
            val message = if (code == "voice_monthly_limit") {
                "Voice limit reached, resets on ${dayLabel(payload?.optString("budget_resets_at").orEmpty())}"
            } else {
                "Voice limit reached, resets tomorrow"
            }
            session.edit().putString("voice_limit_message", message).putLong("voice_limit_until", nextReset(payload)).apply()
            return message
        }
        val token = payload?.optString("token").orEmpty()
        if (response.code !in 200..299 || token.isBlank()) {
            return VoiceSession.spokenFailure(
                payload?.optString("error") ?: response.error,
                payload?.optString("message") ?: payload?.optString("msg"),
                "Voice couldn't start. Rot couldn’t start voice just now.",
            )
        }
        val agentId = payload?.optString("agent_id").orEmpty()
        val personality = if (agentId.isBlank()) payload?.optString("personality").orEmpty() else ""
        val usage = repo.usage.value
        val screen = if (usage.authorized) Scoring.formatDuration(usage.todaySeconds) else ""
        val pickups = if (usage.authorized) usage.pickups.toString() else ""
        val offenders = if (usage.authorized) {
            usage.offenders.filter { it.seconds >= 60 }.joinToString { "${it.label} ${Scoring.formatDuration(it.seconds)}" }
        } else {
            ""
        }
        val instructions = VoiceSession.instructions(
            agentId,
            personality,
            VoiceSession.liveReading(usage.authorized, screen, pickups, offenders),
        )
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        fun emit(kind: String, value: String) {
            main.post { onUpdate(kind, value) }
        }
        val call = VoiceCall(
            context = app,
            onStatus = { status -> emit("status", status) },
            onEnded = { reason -> emit("ended", reason.orEmpty()) },
            onTranscript = { emit("transcript", it) },
        )
        voice = call
        val serverMax = payload?.optDouble("voice_token_seconds")?.takeIf { !it.isNaN() && it > 0 }
            ?: payload?.optDouble("max_session_seconds")?.takeIf { !it.isNaN() && it > 0 }
        val max = VoiceLimits.cappedSeconds(serverMax)
        val failed = call.start(
            token = token,
            model = payload?.optString("model").takeUnless { it.isNullOrBlank() } ?: "grok-voice-latest",
            agentId = agentId,
            voice = payload?.optString("voice").takeUnless { it.isNullOrBlank() } ?: "eve",
            instructions = instructions,
            maxSeconds = max,
            onConnected = { noteVoiceStart() },
        )
        if (failed != null) {
            voice = null
            return failed
        }
        return null
    }

    private suspend fun paywallVoice(
        locallyPremium: Boolean,
        onUpdate: (String, String) -> Unit,
        allowRetry: Boolean,
    ): String? {
        return when (VoiceSession.paywallAction(locallyPremium, alreadyRetried = !allowRetry)) {
            VoiceSession.PaywallAction.ShowPaywall -> {
                repo.requestPaywall()
                FreeTier.voiceMessage
            }
            VoiceSession.PaywallAction.RetryPurchaseSync -> {
                val uploaded = uploadPlayPurchases()
                if (uploaded.active) startVoice(onUpdate, allowRetry = false)
                else VoiceSession.verificationFailure(uploaded.error)
            }
            VoiceSession.PaywallAction.ShowVerificationFailure -> VoiceSession.verificationFailure(null)
        }
    }

    private suspend fun uploadPlayPurchases(): PlaySyncResult {
        val sync = playSync ?: return PlaySyncResult(null, "Google Play isn't ready to verify this purchase.")
        return runCatching { sync() }.getOrElse {
            PlaySyncResult(null, it.message ?: "Rot couldn't verify the Play purchase.")
        }
    }

    fun stopVoice() {
        voice?.stop()
        voice = null
    }

    fun saveChatNote(body: String): String? {
        val id = userId ?: return "Sign in first."
        val text = body.trim().take(200)
        if (text.isEmpty()) return "Nothing to remember."
        val response = call(
            "POST",
            "/rest/v1/memory_notes",
            JSONObject().put("user_id", id).put("body", text).toString(),
            accessToken(),
            "return=minimal",
        )
        return if (response.code in 200..299) null else response.error ?: "Couldn’t save that."
    }

    fun confirmChatFact(key: String, value: String): String? {
        val response = call(
            "POST",
            "/functions/v1/agent-qa",
            JSONObject().put("type", "confirm_fact").put("key", key).put("value", value).toString(),
            accessToken(),
        )
        return if (response.code in 200..299) null else response.error ?: "Couldn’t save that."
    }

    fun updateChatPrefs(quietStart: String?, quietEnd: String?, maxPerDay: Int?): String? {
        val body = JSONObject().put("type", "update_prefs")
        if (!quietStart.isNullOrBlank()) body.put("quiet_start", quietStart)
        if (!quietEnd.isNullOrBlank()) body.put("quiet_end", quietEnd)
        if (maxPerDay != null) body.put("max_per_day", maxPerDay)
        val response = call("POST", "/functions/v1/agent-qa", body.toString(), accessToken())
        return if (response.code in 200..299) null else response.error ?: "Couldn’t update that."
    }

    fun archiveChat() {
        val rows = turns().filter { it.second.isNotBlank() }
        if (rows.none { it.first == "user" }) return
        val id = userId ?: return
        val summary = rows.takeLast(12).joinToString("\n") { "${it.first}: ${it.second}" }.take(600)
        call(
            "POST",
            "/rest/v1/conversation_summaries",
            JSONObject().put("user_id", id).put("summary", summary).put("topics", JSONArray()).toString(),
            accessToken(),
            "return=minimal",
        )
        call(
            "POST",
            "/rest/v1/memory_notes",
            JSONObject().put("user_id", id).put("body", summary.take(200)).toString(),
            accessToken(),
            "return=minimal",
        )
        rows.lastOrNull { it.first == "user" }?.second?.let { postFact("last_chat", it) }
    }

    fun noteFromRot(text: String) {
        if (text.isBlank()) return
        remember("assistant", text)
    }

    fun forgetAll(): String? {
        if (!signedIn) return "Sign in first."
        val response = call("POST", "/functions/v1/agent-qa", JSONObject().put("type", "forget_all").toString(), accessToken())
        return if (response.code in 200..299) null else response.error ?: "Couldn’t forget memories."
    }

    fun storedDailyXp(): Scoring.DailyXpPost? = Scoring.DailyXpPost.decode(flags().getString(XP_POST, null))

    fun rememberDailyXp(post: Scoring.DailyXpPost) {
        flags().edit { putString(XP_POST, post.encode()) }
    }

    /** True when this score is already on the server or the upsert succeeded. */
    fun publishXp(xp: Int, hasReading: Boolean): Boolean {
        if (!signedIn) return false
        flushPendingXp()
        if (!DailyBoard.shouldUpload(authorized = true, hasReading = hasReading)) return false
        val id = userId ?: return false
        ensureProfile()
        val name = storedDisplayName()
        val avatar = storedAvatar()
        val day = Scoring.dayKey()
        val finger = DailyBoard.fingerprint(id, day, xp.coerceAtLeast(0), name, avatar)
        val prefs = flags()
        if (DailyBoard.shouldSkip(prefs.getString(XP_FINGER, null), prefs.getString(XP_PENDING, null), finger)) return true
        val body = xpBody(id, day, xp.coerceAtLeast(0), name, avatar)
        val code = postDaily(body.toString())
        return if (code in 200..299) {
            prefs.edit().putString(XP_FINGER, finger).remove(XP_PENDING).apply()
            true
        } else {
            prefs.edit().putString(XP_PENDING, body.toString()).apply()
            false
        }
    }

    fun loadDailyBoard(yourXp: Int?): BoardSnapshot {
        if (!signedIn) return BoardSnapshot(signedOut = true)
        if (expiresAt() < System.currentTimeMillis() + 60_000) refresh()
        if (!signedIn) return BoardSnapshot(signedOut = true)
        flushPendingXp()
        val day = Scoring.dayKey()
        var response = fetchBoard(day)
        if (response.code == 401) {
            refresh()
            if (!signedIn) return BoardSnapshot(signedOut = true)
            response = fetchBoard(day)
        }
        if (response.code == 401 || !signedIn) return BoardSnapshot(signedOut = true)
        if (response.code !in 200..299 || response.array == null) {
            val cached = cachedBoard(day)
            val offline = response.code == 0
            return BoardSnapshot(
                rows = cached,
                offline = offline || cached.isNotEmpty(),
                message = if (offline) "Offline" else response.error ?: "Couldn’t load the leaderboard.",
            )
        }
        val rows = parseBoard(response.array)
        flags().edit().putString(BOARD_CACHE, response.array.toString()).putString(BOARD_DAY, day).apply()
        val ranked = DailyBoard.withRanks(rows, userId.orEmpty())
        val pinned = if (ranked.none { it.you } && yourXp != null) pinnedYou(day, yourXp) else null
        return BoardSnapshot(rows = ranked, pinned = pinned)
    }

    fun syncAccount(state: SavedState, allowPull: Boolean) {
        if (!signedIn) return
        ensureProfile()
        if (allowPull && !hasBlockedApps(state)) {
            val remote = pullAndroidBlocks()
            if (remote != null && (remote.first.any { it.apps.isNotEmpty() } || remote.second.any { it.apps.isNotEmpty() })) {
                repo.applyRemoteBlocks(remote.first, remote.second)
                syncStreak(repo.state.value)
                return
            }
        }
        pushBlockers(state)
        syncStreak(state)
    }

    fun pushBlockers(state: SavedState) {
        if (!signedIn) return
        val id = userId ?: return
        ensureProfile()
        val lists = JSONArray()
        state.appLists.forEachIndexed { index, list ->
            lists.put(
                BlockBackup.listJson(
                    BlockBackup.ListRow(
                        id = list.id,
                        name = list.name,
                        apps = list.apps.map { BlockBackup.AppRef(it.packageName, it.label) },
                        builtin = list.id == "list.brainmelt",
                        sort = index,
                    ),
                ).put("user_id", id),
            )
        }
        if (lists.length() > 0) {
            call(
                "POST",
                "/rest/v1/blocker_lists?on_conflict=user_id,client_id",
                lists.toString(),
                accessToken(),
                "resolution=merge-duplicates,return=minimal",
            )
        }
        val rules = JSONArray()
        state.blockers.forEach { blocker ->
            rules.put(
                BlockBackup.ruleJson(
                    BlockBackup.RuleRow(
                        id = blocker.id,
                        name = blocker.name,
                        mode = blocker.mode,
                        appListId = blocker.appListId,
                        startHour = blocker.startHour,
                        endHour = blocker.endHour,
                        limitMinutes = blocker.limitMinutes,
                        days = blocker.days,
                        apps = blocker.apps.map { BlockBackup.AppRef(it.packageName, it.label) },
                        modeApps = blocker.modeApps.mapValues { (_, apps) ->
                            apps.map { BlockBackup.AppRef(it.packageName, it.label) }
                        },
                    ),
                ).put("user_id", id),
            )
        }
        if (rules.length() > 0) {
            call(
                "POST",
                "/rest/v1/blocker_rules?on_conflict=user_id,client_id",
                rules.toString(),
                accessToken(),
                "resolution=merge-duplicates,return=minimal",
            )
        }
        retireMissing("blocker_lists", state.appLists.map { BlockBackup.clientId(it.id) }, "selection_data")
        retireMissing("blocker_rules", state.blockers.map { BlockBackup.clientId(it.id) }, "config")
    }

    fun syncStreak(state: SavedState) {
        val id = userId ?: return
        val existing = call("GET", "/rest/v1/user_streaks?select=longest_streak,extra&user_id=eq.$id", null, accessToken())
        val row = existing.array?.optJSONObject(0)
        val extra = row?.optJSONObject("extra") ?: JSONObject()
        val focusSeconds = state.focusMinutesByDay.values.sum() * 60
        extra.put("focus_time_seconds", focusSeconds)
        val longest = maxOf(state.longestStreak, state.streak, row?.optInt("longest_streak") ?: 0)
        val body = JSONObject()
            .put("user_id", id)
            .put("current_streak", state.streak)
            .put("longest_streak", longest)
            .put("last_active_day", state.lastOpenDay.ifBlank { Scoring.dayKey() })
            .put("extra", extra)
        call(
            "POST",
            "/rest/v1/user_streaks?on_conflict=user_id",
            body.toString(),
            accessToken(),
            "resolution=merge-duplicates,return=minimal",
        )
    }

    fun syncScreenTime(snapshot: UsageSnapshot) {
        val id = userId ?: return
        if (!snapshot.authorized || snapshot.todaySeconds < 60L) return
        val reading = ScreenTimeSync.reading(
            ScreenTimeSync.UsageView(
                authorized = true,
                todaySeconds = snapshot.todaySeconds,
                pickups = snapshot.pickups,
                avgSeconds = snapshot.avgSeconds,
                ahead = snapshot.aheadOfPace,
                offenders = snapshot.offenders.map { it.label to it.seconds },
                hourlyToday = snapshot.hourlyToday,
                hourlyAvg = snapshot.hourlyAvg,
                week = snapshot.week.map { it.day to it.seconds },
            ),
        ) ?: return
        val offenders = JSONArray()
        reading.offenders.forEach { row ->
            offenders.put(JSONObject().put("name", row.name).put("minutes", row.minutes))
        }
        val hourly = JSONArray()
        reading.hourly.forEach { row ->
            hourly.put(
                JSONObject()
                    .put("hour", row.hour)
                    .put("today_minutes", row.todayMinutes)
                    .put("average_minutes", row.averageMinutes),
            )
        }
        val categories = JSONArray()
        snapshot.offenders.filter { it.seconds >= 60 }.forEach { app ->
            val category = categoryOf(app.packageName) ?: return@forEach
            val name = app.label.trim().take(80)
            if (name.isBlank() || name.lowercase() in setOf("unknown", "app", "website")) return@forEach
            categories.put(JSONObject().put("name", name).put("category", category))
        }
        val body = JSONObject()
            .put("user_id", id)
            .put("today_minutes", reading.todayMinutes)
            .put("pickups", reading.pickups)
            .put("captured_at", ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            .put("ahead_of_usual_pace", reading.ahead)
        reading.topApp?.let { body.put("top_app", it) }
        reading.topAppMinutes?.let { body.put("top_app_minutes", it) }
        reading.paceAverageMinutes?.let { body.put("pace_average_minutes", it) }
        if (offenders.length() > 0) body.put("top_offenders", offenders)
        if (hourly.length() > 0) body.put("hourly_trend", hourly)
        if (categories.length() > 0) body.put("top_offender_categories", categories)
        call(
            "POST",
            "/rest/v1/screen_time_readings?on_conflict=user_id",
            body.toString(),
            accessToken(),
            "resolution=merge-duplicates,return=minimal",
        )
        if (reading.days.isNotEmpty()) {
            val days = JSONArray()
            reading.days.forEach { day ->
                days.put(JSONObject().put("user_id", id).put("day", day.day).put("minutes", day.minutes))
            }
            call(
                "POST",
                "/rest/v1/screen_time_days?on_conflict=user_id,day",
                days.toString(),
                accessToken(),
                "resolution=merge-duplicates,return=minimal",
            )
        }
    }

    private fun storedDisplayName(): String {
        val full = session.getString("full_name", null)
        val raw = if (!full.isNullOrBlank()) full else session.getString("display_name", null)
        return DailyBoard.displayName(raw)
    }

    private fun storedAvatar(): String? = session.getString("avatar_url", null)?.takeIf { it.startsWith("http") }

    private fun googleName(user: JSONObject?): String? {
        val meta = user?.optJSONObject("user_metadata") ?: return null
        return meta.optString("full_name").ifBlank { meta.optString("name") }.ifBlank { null }
    }

    private fun googleAvatar(user: JSONObject?): String? {
        val meta = user?.optJSONObject("user_metadata") ?: return null
        return meta.optString("avatar_url").ifBlank { meta.optString("picture") }.takeIf { it.startsWith("http") }
    }

    private fun rememberIdentity(user: JSONObject?) {
        if (flags().getString(PROFILE_DONE, null) == userId) return
        val name = googleName(user)
        val avatar = googleAvatar(user)
        val edit = session.edit()
        if (session.getString("full_name", null).isNullOrBlank() && DailyBoard.isBoardName(name)) {
            val shown = DailyBoard.displayName(name)
            edit.putString("full_name", shown)
            edit.putString("display_name", shown)
        }
        if (storedAvatar() == null && !avatar.isNullOrBlank()) edit.putString("avatar_url", avatar)
        edit.apply()
    }

    private fun rememberChosenName(name: String) {
        val shown = DailyBoard.displayName(name)
        if (!DailyBoard.isBoardName(shown)) return
        val split = DailyBoard.splitName(shown)
        session.edit()
            .putString(GIVEN_NAME, split.first)
            .putString(FAMILY_NAME, split.second)
            .putString("full_name", shown)
            .putString("display_name", shown)
            .apply()
        userId?.let { flags().edit().putString(PROFILE_DONE, it).apply() }
        syncProfileGate()
    }

    private fun syncProfileGate() {
        _needsProfile.value = signedIn && !hasLocalDisplayName()
    }

    private fun uploadAvatar(id: String, jpeg: ByteArray): Upload {
        val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        if (key.isBlank()) return Upload(null, "Add SUPABASE_PUBLISHABLE_KEY to local.properties.")
        val request = Request.Builder()
            .url(BuildConfig.SUPABASE_URL + "/storage/v1/object/avatars/$id.jpg")
            .header("apikey", key)
            .header("Authorization", "Bearer ${accessToken()}")
            .header("x-upsert", "true")
            .header("Content-Type", "image/jpeg")
            .post(jpeg.toRequestBody("image/jpeg".toMediaType()))
            .build()
        val result = runCatching {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (response.code in 200..299) {
                    val stamp = System.currentTimeMillis()
                    Upload("${BuildConfig.SUPABASE_URL}/storage/v1/object/public/avatars/$id.jpg?v=$stamp", null)
                } else {
                    val json = runCatching { JSONObject(text) }.getOrNull()
                    val message = json?.optString("message")?.ifBlank { null }
                        ?: json?.optString("error")?.ifBlank { null }
                        ?: text.ifBlank { "Couldn’t upload the photo (${response.code})" }
                    Upload(null, message)
                }
            }
        }.getOrElse { Upload(null, it.message ?: "Couldn’t upload the photo.") }
        return result
    }

    private fun failText(response: HttpResult, fallback: String): String {
        val json = response.json
        return json?.optString("message")?.ifBlank { null }
            ?: json?.optString("error_description")?.ifBlank { null }
            ?: json?.optString("msg")?.ifBlank { null }
            ?: response.error
            ?: fallback
    }

    private fun flags() = app.getSharedPreferences("rotrun.flags", Context.MODE_PRIVATE)

    private fun xpBody(id: String, day: String, xp: Int, name: String, avatar: String?): JSONObject {
        val shown = if (name.equals("You", ignoreCase = true)) DailyBoard.fallbackName else name
        return DailyBoard.xpUploadBody(
            id,
            day,
            xp,
            shown,
            avatar,
            ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        )
    }

    private fun postDaily(body: String): Int {
        val path = "/rest/v1/daily_xp?on_conflict=user_id,day"
        val prefer = "resolution=merge-duplicates,return=minimal"
        var response = call("POST", path, body, accessToken(), prefer)
        if (response.code == 401) {
            refresh()
            if (!signedIn) return 401
            response = call("POST", path, body, accessToken(), prefer)
        }
        return response.code
    }

    private fun flushPendingXp() {
        val prefs = flags()
        val pending = prefs.getString(XP_PENDING, null) ?: return
        if (!signedIn) return
        ensureProfile()
        val code = postDaily(pending)
        if (code in 200..299) prefs.edit().remove(XP_PENDING).apply()
    }

    private fun fetchBoard(day: String): HttpResult =
        call(
            "GET",
            "/rest/v1/daily_xp?day=eq.$day&select=user_id,display_name,xp,avatar_url&order=xp.desc,updated_at.asc&limit=50",
            null,
            accessToken(),
        )

    private fun pinnedYou(day: String, yourXp: Int): RankedXp? {
        val id = userId ?: return null
        val response = call(
            "HEAD",
            "/rest/v1/daily_xp?day=eq.$day&xp=gt.$yourXp&select=user_id",
            null,
            accessToken(),
            "count=exact",
        )
        val higher = DailyBoard.countFromContentRange(response.contentRange) ?: return null
        val rank = DailyBoard.outsideRank(higher, yourXp) ?: return null
        return RankedXp(id, storedDisplayName(), yourXp, rank, true, storedAvatar())
    }

    private fun parseBoard(array: JSONArray): List<XpRow> = buildList {
        for (index in 0 until array.length()) {
            val row = array.optJSONObject(index) ?: continue
            val id = row.optString("user_id")
            if (id.isBlank()) continue
            val avatar = row.optString("avatar_url").takeIf { it.startsWith("http") }
            add(XpRow(id, DailyBoard.displayName(row.optString("display_name")), row.optInt("xp"), avatar))
        }
    }

    private fun cachedBoard(day: String): List<RankedXp> {
        val prefs = flags()
        if (prefs.getString(BOARD_DAY, null) != day) return emptyList()
        val raw = prefs.getString(BOARD_CACHE, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return DailyBoard.withRanks(parseBoard(array), userId.orEmpty())
    }

    private fun exchange(idToken: String, nonce: String): String? {
        if (BuildConfig.SUPABASE_PUBLISHABLE_KEY.isBlank()) return "Sign-in isn’t set up yet"
        val body = JSONObject().put("provider", "google").put("id_token", idToken).put("nonce", nonce)
        val response = call("POST", "/auth/v1/token?grant_type=id_token", body.toString(), null)
        val payload = response.json ?: return response.error ?: "Sign-in failed (${response.code})"
        if (response.code !in 200..299) return payload.optString("msg").ifBlank { payload.optString("error_description", "Sign-in failed") }
        val user = payload.optJSONObject("user")
        session.edit()
            .putString("access", payload.optString("access_token"))
            .putString("refresh", payload.optString("refresh_token"))
            .putLong("expires", System.currentTimeMillis() + payload.optLong("expires_in") * 1000)
            .putString("user_id", user?.optString("id"))
            .putString("email", user?.optString("email"))
            .apply()
        rememberIdentity(user)
        syncProfileGate()
        notifySignedIn()
        val profile = JSONObject().put("user_id", user?.optString("id")).put("timezone", ZoneId.systemDefault().id)
        call(
            "POST",
            "/rest/v1/profiles?on_conflict=user_id",
            profile.toString(),
            accessToken(),
            "resolution=merge-duplicates,return=minimal",
        )
        return null
    }

    private fun refresh() {
        val refresh = session.getString("refresh", null) ?: return
        val body = JSONObject().put("refresh_token", refresh)
        val response = call("POST", "/auth/v1/token?grant_type=refresh_token", body.toString(), null)
        val payload = response.json
        if (response.code !in 200..299 || payload == null) {
            signOut()
            return
        }
        val user = payload.optJSONObject("user")
        session.edit()
            .putString("access", payload.optString("access_token"))
            .putString("refresh", payload.optString("refresh_token", refresh))
            .putLong("expires", System.currentTimeMillis() + payload.optLong("expires_in") * 1000)
            .putString("user_id", user?.optString("id") ?: userId)
            .apply()
        rememberIdentity(user)
        syncProfileGate()
        notifySignedIn()
    }

    private fun notifySignedIn() {
        android.os.Handler(android.os.Looper.getMainLooper()).post { onSignedIn?.invoke() }
    }

    private fun ensureProfile() {
        val id = userId ?: return
        val body = JSONObject().put("user_id", id).put("timezone", ZoneId.systemDefault().id)
        call(
            "POST",
            "/rest/v1/profiles?on_conflict=user_id",
            body.toString(),
            accessToken(),
            "resolution=merge-duplicates,return=minimal",
        )
    }

    private fun pullAndroidBlocks(): Pair<List<AppList>, List<Blocker>>? {
        val listFilter = URLEncoder.encode("android:*", "UTF-8")
        val listsResponse = call(
            "GET",
            "/rest/v1/blocker_lists?select=client_id,name,is_builtin,selection_data,sort_order&client_id=like.$listFilter&deleted_at=is.null",
            null,
            accessToken(),
        )
        val rulesResponse = call(
            "GET",
            "/rest/v1/blocker_rules?select=client_id,list_client_id,name,schedule,config&client_id=like.$listFilter&deleted_at=is.null",
            null,
            accessToken(),
        )
        val lists = listsResponse.array ?: return null
        val rules = rulesResponse.array ?: return null
        val parsedLists = buildList {
            for (index in 0 until lists.length()) {
                val row = BlockBackup.parseList(lists.optJSONObject(index) ?: continue) ?: continue
                add(AppList(row.id, row.name, row.apps.map { TrackedApp(it.packageName, it.label) }))
            }
        }
        val parsedRules = buildList {
            for (index in 0 until rules.length()) {
                val row = BlockBackup.parseRule(rules.optJSONObject(index) ?: continue) ?: continue
                add(
                    Blocker(
                        id = row.id,
                        name = row.name,
                        mode = row.mode,
                        appListId = row.appListId,
                        startHour = row.startHour,
                        endHour = row.endHour,
                        limitMinutes = row.limitMinutes,
                        days = row.days,
                        apps = row.apps.map { TrackedApp(it.packageName, it.label) },
                        modeApps = row.modeApps.mapValues { (_, apps) -> apps.map { TrackedApp(it.packageName, it.label) } },
                    ),
                )
            }
        }
        return parsedLists to parsedRules
    }

    private fun retireMissing(table: String, keep: List<String>, payloadColumn: String) {
        val filter = URLEncoder.encode("android:*", "UTF-8")
        val response = call(
            "GET",
            "/rest/v1/$table?select=client_id,$payloadColumn&client_id=like.$filter&deleted_at=is.null",
            null,
            accessToken(),
        )
        val rows = response.array ?: return
        val keepSet = keep.toSet()
        val now = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val clientId = row.optString("client_id")
            if (!clientId.startsWith(BlockBackup.PREFIX) || clientId in keepSet) continue
            val payload = if (payloadColumn == "config") row.optJSONObject("config")?.toString() else row.optString(payloadColumn)
            if (!payload.isNullOrBlank() && !BlockBackup.isAndroidOwned(payload)) continue
            val encoded = URLEncoder.encode(clientId, "UTF-8")
            call(
                "PATCH",
                "/rest/v1/$table?client_id=eq.$encoded",
                JSONObject().put("deleted_at", now).toString(),
                accessToken(),
                "return=minimal",
            )
        }
    }

    private fun hasBlockedApps(state: SavedState): Boolean =
        state.blockers.any { it.apps.isNotEmpty() } || state.appLists.any { it.apps.isNotEmpty() }

    private fun categoryOf(packageName: String): String? {
        val info = runCatching { app.packageManager.getApplicationInfo(packageName, 0) }.getOrNull() ?: return null
        if (android.os.Build.VERSION.SDK_INT < 26) return null
        return when (info.category) {
            android.content.pm.ApplicationInfo.CATEGORY_GAME -> "Game"
            android.content.pm.ApplicationInfo.CATEGORY_AUDIO -> "Audio"
            android.content.pm.ApplicationInfo.CATEGORY_VIDEO -> "Video"
            android.content.pm.ApplicationInfo.CATEGORY_IMAGE -> "Image"
            android.content.pm.ApplicationInfo.CATEGORY_SOCIAL -> "Social"
            android.content.pm.ApplicationInfo.CATEGORY_NEWS -> "News"
            android.content.pm.ApplicationInfo.CATEGORY_MAPS -> "Maps"
            android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
            else -> null
        }
    }

    private fun dayLabel(raw: String): String {
        if (raw.isBlank()) return "later"
        val parsed = runCatching { java.time.OffsetDateTime.parse(raw) }.getOrNull()
            ?: runCatching { java.time.Instant.parse(raw).atZone(ZoneId.systemDefault()).toOffsetDateTime() }.getOrNull()
        return parsed?.format(DateTimeFormatter.ofPattern("MMM d", Locale.US)) ?: raw
    }

    private fun canSend(state: SavedState): Boolean {
        if (!PaywallGate.shows || state.proFor(userId)) return true
        val used = if (state.chatDay == Scoring.dayKey()) state.chatCount else 0
        return used < FreeTier.dailyMessageCap
    }

    private fun voiceBlocked(): Boolean = session.getLong("voice_limit_until", 0L) > System.currentTimeMillis()

    private fun voiceAllowance(): String? {
        val now = ZonedDateTime.now()
        val day = now.toLocalDate().toString()
        val month = day.take(7)
        val dayCount = if (session.getString("voice_day", "") == day) session.getInt("voice_day_count", 0) else 0
        val monthCount = if (session.getString("voice_month", "") == month) session.getInt("voice_month_count", 0) else 0
        return VoiceLimits.denial(dayCount, monthCount)
    }

    private fun noteVoiceStart() {
        val now = ZonedDateTime.now()
        val day = now.toLocalDate().toString()
        val month = day.take(7)
        val dayCount = if (session.getString("voice_day", "") == day) session.getInt("voice_day_count", 0) else 0
        val monthCount = if (session.getString("voice_month", "") == month) session.getInt("voice_month_count", 0) else 0
        session.edit()
            .putString("voice_day", day)
            .putInt("voice_day_count", dayCount + 1)
            .putString("voice_month", month)
            .putInt("voice_month_count", monthCount + 1)
            .apply()
    }

    private fun voiceResetAt(message: String): Long {
        val zone = ZoneId.systemDefault()
        val start = if (message == VoiceLimits.monthlyMessage) {
            java.time.LocalDate.now(zone).withDayOfMonth(1).plusMonths(1).atStartOfDay(zone)
        } else {
            java.time.LocalDate.now(zone).plusDays(1).atStartOfDay(zone)
        }
        return start.toInstant().toEpochMilli()
    }

    private fun nextReset(payload: JSONObject?): Long {
        val raw = payload?.optString("budget_resets_at").orEmpty()
        val parsed = runCatching { java.time.Instant.parse(raw).toEpochMilli() }.getOrNull()
        return parsed ?: (System.currentTimeMillis() + 24L * 60 * 60 * 1000)
    }

    private fun context(state: SavedState): JSONObject {
        val usage = repo.usage.value
        val level = com.ahmed.rotrun.logic.BrainHealthLevel.fromLabel(state.brainHealth)
        val health = Scoring.shownHealth(usage.authorized, usage.todaySeconds, state.focusMinutesToday(), level)
        val objectJson = JSONObject()
            .put("local_time", ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            .put("timezone", ZoneId.systemDefault().id)
            .put("unlock_minutes_left", ((state.unlockUntil - System.currentTimeMillis()) / 60_000).toInt().coerceAtLeast(0))
            .put("streak_days", state.streak)
            .put("screen_time_goal_min", level.goalMinutes)
            .put("is_premium", state.proFor(userId))
            .put("app_version", BuildConfig.VERSION_NAME)
            .put("caught_doomscrolling", false)
            .put("leaderboard", "unknown")
        if (usage.authorized) {
            val minutes = Scoring.scoredMinutes(usage.todaySeconds, authorized = true)
            if (minutes != null) {
                val xp = Scoring.dailyXp(minutes, level.goalMinutes, state.askBrainCount, state.ransomDebit, level.xpMultiplier)
                objectJson.put("xp", xp)
                objectJson.put("screen_time_today_min", minutes)
            }
            if (health != null) objectJson.put("brain_health", health)
        }
        if (usage.authorized && usage.pickups > 0) objectJson.put("pickups_today", usage.pickups)
        if (state.focusMinutesToday() > 0) objectJson.put("focus_time_today_min", state.focusMinutesToday())
        val offenders = usage.offenders.filter { it.seconds >= 60 }
        if (offenders.isNotEmpty()) {
            objectJson.put("top_offenders", offenders.joinToString { "${it.label} ${Scoring.formatDuration(it.seconds)}" })
        }
        return objectJson
    }

    private fun localReply(message: String, state: SavedState): String? {
        val usage = repo.usage.value
        val lower = message.lowercase()
        val asksScreen = "screen time" in lower || "screen-time" in lower
        val asksPickups = "pickup" in lower
        val asksOffenders = "offender" in lower || "which app" in lower
        if (!asksScreen && !asksPickups && !asksOffenders) return null
        if (!usage.authorized) return "Usage access is off. Connect it from Home or Settings."
        if (asksPickups && !asksScreen && !asksOffenders) {
            return "Pickups today: ${usage.pickups}."
        }
        if (asksOffenders && !asksScreen) {
            val rows = usage.offenders.filter { it.seconds >= 60 }
            return if (rows.isEmpty()) "No apps over a minute yet." else rows.joinToString { "${it.label} ${Scoring.formatDuration(it.seconds)}" }
        }
        return "Screen time today is ${Scoring.formatDuration(usage.todaySeconds)}."
    }

    private fun remember(role: String, content: String) {
        val turns = loadTurns()
        turns.put(JSONObject().put("role", role).put("content", content))
        while (turns.length() > 40) turns.remove(0)
        app.getSharedPreferences("rotrun", Context.MODE_PRIVATE).edit().putString("chat.turns", turns.toString()).apply()
    }

    private fun loadTurns(): JSONArray {
        val raw = app.getSharedPreferences("rotrun", Context.MODE_PRIVATE).getString("chat.turns", null) ?: return JSONArray()
        return runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    }

    fun turns(): List<Pair<String, String>> {
        val turns = loadTurns()
        return buildList {
            for (index in 0 until turns.length()) {
                val row = turns.optJSONObject(index) ?: continue
                add(row.optString("role") to row.optString("content"))
            }
        }
    }

    private fun absorbCards(array: JSONArray?) {
        if (array == null) return
        val parsed = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val type = item.optString("type")
                if (type.isBlank()) continue
                val data = linkedMapOf<String, String>()
                val raw = item.optJSONObject("data")
                if (raw != null) {
                    val keys = raw.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = raw.opt(key)
                        if (value != null && value != JSONObject.NULL) data[key] = value.toString()
                    }
                }
                add(ChatCard(type, data))
                if (type == "confirm_fact") postFact(data["key"].orEmpty(), data["value"].orEmpty())
            }
        }
        if (parsed.isEmpty()) return
        synchronized(cardLock) { pendingCards = pendingCards + parsed }
    }

    private fun postFact(key: String, value: String) {
        val id = userId ?: return
        val factKey = key.trim().take(50)
        val factValue = value.trim().take(200)
        if (factKey.isEmpty() || factValue.isEmpty()) return
        call(
            "POST",
            "/rest/v1/user_facts?on_conflict=user_id,key",
            JSONObject().put("user_id", id).put("key", factKey).put("value", factValue).put("source", "inferred").toString(),
            accessToken(),
            "resolution=merge-duplicates,return=minimal",
        )
    }

    private fun accessToken(): String = session.getString("access", "").orEmpty()
    private fun expiresAt(): Long = session.getLong("expires", 0L)

    fun syncPlayPurchases(purchases: List<PlayPurchaseUpload>): PlaySyncResult {
        if (accessToken().isBlank()) return PlaySyncResult(null, "Sign in with Google so Rot can verify the purchase.")
        if (purchases.isEmpty()) {
            return PlaySyncResult(emptyList(), "This Google account has no active RotRun Premium purchase to verify.")
        }
        val body = JSONArray()
        purchases.take(8).forEach { purchase ->
            body.put(
                JSONObject()
                    .put("product_id", purchase.productId)
                    .put("purchase_token", purchase.purchaseToken)
                    .put("kind", purchase.kind),
            )
        }
        val response = call(
            "POST",
            "/functions/v1/sync-play-entitlements",
            JSONObject().put("purchases", body).toString(),
            accessToken(),
            client = playHttp,
        )
        if (response.code !in 200..299) {
            return PlaySyncResult(null, failText(response, "Rot couldn't verify the Play purchase (${response.code})."))
        }
        val rows = response.json?.optJSONArray("entitlements") ?: return PlaySyncResult(emptyList(), null)
        val entitlements = buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val productId = row.optString("product_id")
                if (productId.isBlank()) continue
                val expires = row.optString("expires_at").takeIf { it.isNotBlank() && it != "null" }
                val millis = expires?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
                val active = if (row.has("active")) row.optBoolean("active") else true
                add(ServerEntitlement(productId, millis, productId == com.ahmed.rotrun.logic.Products.LIFETIME, active))
            }
        }
        return PlaySyncResult(entitlements, null)
    }

    private fun call(
        method: String,
        path: String,
        body: String?,
        bearer: String?,
        prefer: String? = null,
        client: OkHttpClient = http,
    ): HttpResult {
        val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        if (key.isBlank()) return HttpResult(0, null, null, "Add SUPABASE_PUBLISHABLE_KEY to local.properties.")
        val builder = Request.Builder()
            .url(BuildConfig.SUPABASE_URL + path)
            .header("apikey", key)
            .header("X-RotRun-Platform", "android")
            .header("X-RotRun-Build", BuildConfig.VERSION_CODE.toString())
        if (!bearer.isNullOrBlank()) builder.header("Authorization", "Bearer $bearer")
        if (prefer != null) builder.header("Prefer", prefer)
        when (method) {
            "GET" -> builder.get()
            "HEAD" -> builder.head()
            else -> builder.method(method, (body ?: "{}").toRequestBody(jsonType))
        }
        return runCatching {
            client.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val obj = runCatching { JSONObject(text) }.getOrNull()
                val array = runCatching { JSONArray(text) }.getOrNull()
                HttpResult(
                    response.code,
                    obj,
                    array,
                    obj?.optString("error")?.ifBlank { obj.optString("msg") },
                    response.header("Content-Range"),
                )
            }
        }.getOrElse { HttpResult(0, null, null, it.message, null) }
    }

    private data class Upload(val url: String?, val error: String?)

    private data class HttpResult(
        val code: Int,
        val json: JSONObject?,
        val array: JSONArray?,
        val error: String?,
        val contentRange: String? = null,
    )

    companion object {
        private fun openSession(context: Context): SharedPreferences = SessionStore.open(context)

        private const val XP_FINGER = "xp.finger"
        private const val XP_POST = "xp.post"
        private const val XP_PENDING = "xp.pending"
        private const val BOARD_CACHE = "board.cache"
        private const val BOARD_DAY = "board.day"
        private const val PROFILE_DONE = "profile.done"
        private const val PROFILE_LATER = "profile.later"
        private const val GIVEN_NAME = "given_name"
        private const val FAMILY_NAME = "family_name"

        private fun randomNonce(): String {
            val alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._"
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            return buildString(32) { bytes.forEach { append(alphabet[(it.toInt() and 0xff) % alphabet.length]) } }
        }

    }
}
