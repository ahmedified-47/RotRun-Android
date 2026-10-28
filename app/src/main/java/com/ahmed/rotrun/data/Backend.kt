package com.ahmed.rotrun.data

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ahmed.rotrun.BuildConfig
import com.ahmed.rotrun.logic.FreeTier
import com.ahmed.rotrun.logic.PaywallGate
import com.ahmed.rotrun.logic.Scoring
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class Backend(private val app: Context, private val repo: RotRunRepository) {
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private val jsonType = "application/json".toMediaType()
    private val session = openSession(app)
    private var voice: VoiceCall? = null
    val signedIn: Boolean get() = accessToken().isNotBlank()
    val userId: String? get() = session.getString("user_id", null)

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
            onDone("Add GOOGLE_WEB_CLIENT_ID to local.properties. RotRun does not create that client.")
            return
        }
        val nonce = randomNonce()
        val hashed = sha256(nonce)
        GoogleSignIn.start(activity, clientId, hashed) { result ->
            result.fold(
                onSuccess = { idToken ->
                    val error = exchange(idToken, nonce)
                    onDone(error)
                },
                onFailure = { onDone(it.message ?: "Google sign-in failed") },
            )
        }
    }

    fun signOut() {
        voice?.stop()
        session.edit().clear().apply()
        app.getSharedPreferences("rotrun", Context.MODE_PRIVATE).edit().remove("chat.turns").apply()
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
        if (code == "daily_limit" || code == "hourly_limit") return FreeTier.serverChatDone
        if (code == "monthly_limit" || code == "budget_exhausted" || code == "user_budget_exhausted") {
            return if (resets.isNotBlank()) "You’ve used your chats with Rot for this period. Back on $resets." else FreeTier.serverChatDone
        }
        if (response.code !in 200..299) return payload?.optString("error").takeUnless { it.isNullOrBlank() } ?: FreeTier.tryAgain
        val answer = payload?.optString("text").orEmpty()
        if (answer.isBlank()) return payload?.optString("error").takeUnless { it.isNullOrBlank() } ?: FreeTier.tryAgain
        remember("user", text)
        remember("assistant", answer)
        if (PaywallGate.shows && !state.premiumActive()) {
            repo.update { current ->
                val day = Scoring.dayKey()
                val count = if (current.chatDay == day) current.chatCount else 0
                current.copy(chatDay = day, chatCount = count + 1)
            }
        }
        return answer
    }

    fun startVoice(instructions: String): String? {
        val state = repo.state.value
        if (PaywallGate.shows && !state.premiumActive()) {
            repo.requestPaywall()
            return FreeTier.voiceMessage
        }
        if (!signedIn) return "Sign in with Google to talk to your brain."
        if (needsConsent()) return "Rot needs your OK before a voice call."
        if (voiceBlocked()) return session.getString("voice_limit_message", "Voice limit reached, resets tomorrow")
        val response = call("POST", "/functions/v1/agent-qa", JSONObject().put("type", "voice_secret").toString(), accessToken())
        val payload = response.json
        val code = payload?.optString("error_code").orEmpty()
        if (code == "voice_daily_limit" || code == "voice_monthly_limit" || code == "budget_exhausted" || response.code == 429) {
            val message = if (code == "voice_monthly_limit") "Voice limit reached, resets on ${payload?.optString("budget_resets_at")}" else "Voice limit reached, resets tomorrow"
            session.edit().putString("voice_limit_message", message).putLong("voice_limit_until", nextReset(payload)).apply()
            return message
        }
        val token = payload?.optString("token").orEmpty()
        if (response.code !in 200..299 || token.isBlank()) return payload?.optString("error").takeUnless { it.isNullOrBlank() } ?: "Rot couldn’t start voice just now."
        val call = VoiceCall(
            onStatus = { repo.toast(it) },
            onEnded = { reason -> if (reason != null) repo.toast(reason) },
        )
        voice = call
        val max = payload?.optDouble("voice_token_seconds")?.takeIf { !it.isNaN() && it > 0 }
            ?: payload?.optDouble("max_session_seconds")?.takeIf { !it.isNaN() && it > 0 }
        call.start(
            token = token,
            model = payload?.optString("model").takeUnless { it.isNullOrBlank() } ?: "grok-voice-latest",
            voice = payload?.optString("voice").takeUnless { it.isNullOrBlank() } ?: "eve",
            instructions = instructions,
            maxSeconds = max,
        )
        return null
    }

    fun stopVoice() {
        voice?.stop()
        voice = null
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

    fun syncXp(xp: Int, name: String) {
        val id = userId ?: return
        val body = JSONObject()
            .put("user_id", id)
            .put("day", Scoring.dayKey())
            .put("xp", xp)
            .put("display_name", name.ifBlank { "You" })
        call("POST", "/rest/v1/daily_xp?on_conflict=user_id,day", body.toString(), accessToken(), "resolution=merge-duplicates,return=minimal")
    }

    fun leaderboard(): List<Pair<String, Int>> {
        if (!signedIn) return emptyList()
        val day = Scoring.dayKey()
        val response = call("GET", "/rest/v1/daily_xp?day=eq.$day&select=display_name,xp&order=xp.desc&limit=50", null, accessToken())
        val array = response.array ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val row = array.optJSONObject(index) ?: continue
                add(row.optString("display_name", "Someone") to row.optInt("xp"))
            }
        }
    }

    private fun exchange(idToken: String, nonce: String): String? {
        if (BuildConfig.SUPABASE_PUBLISHABLE_KEY.isBlank()) return "Add SUPABASE_PUBLISHABLE_KEY to local.properties."
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
    }

    private fun canSend(state: SavedState): Boolean {
        if (!PaywallGate.shows || state.premiumActive()) return true
        val used = if (state.chatDay == Scoring.dayKey()) state.chatCount else 0
        return used < FreeTier.dailyMessageCap
    }

    private fun voiceBlocked(): Boolean = session.getLong("voice_limit_until", 0L) > System.currentTimeMillis()

    private fun nextReset(payload: JSONObject?): Long {
        val raw = payload?.optString("budget_resets_at").orEmpty()
        val parsed = runCatching { java.time.Instant.parse(raw).toEpochMilli() }.getOrNull()
        return parsed ?: (System.currentTimeMillis() + 24L * 60 * 60 * 1000)
    }

    private fun context(state: SavedState): JSONObject {
        val usage = repo.usage.value
        val level = com.ahmed.rotrun.logic.BrainHealthLevel.fromLabel(state.brainHealth)
        val health = if (usage.drainSeconds >= 60) {
            Scoring.displayedHealth(Scoring.drainHealth(usage.drainSeconds, level), state.focusMinutesToday(), level.restoreMinutes)
        } else {
            null
        }
        val xp = Scoring.dailyXp((usage.todaySeconds / 60).toInt(), state.goalHours * 60, state.askBrainCount, state.ransomDebit)
        val objectJson = JSONObject()
            .put("local_time", ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            .put("timezone", ZoneId.systemDefault().id)
            .put("xp", xp)
            .put("unlock_minutes_left", ((state.unlockUntil - System.currentTimeMillis()) / 60_000).toInt().coerceAtLeast(0))
            .put("streak_days", state.streak)
            .put("screen_time_goal_min", state.goalHours * 60)
            .put("is_premium", state.premiumActive())
            .put("app_version", "1.0.0")
            .put("caught_doomscrolling", false)
            .put("leaderboard", "unknown")
        if (health != null) objectJson.put("brain_health", health)
        if (usage.todaySeconds >= 60) objectJson.put("screen_time_today_min", usage.todaySeconds / 60)
        if (usage.pickups > 0) objectJson.put("pickups_today", usage.pickups)
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
        if (!usage.authorized) return "Screen time is not in yet."
        if (asksPickups && !asksScreen && !asksOffenders) {
            return if (usage.pickups > 0) "Pickups today: ${usage.pickups}." else "Pickups are not in yet."
        }
        if (asksOffenders && !asksScreen) {
            val rows = usage.offenders.filter { it.seconds >= 60 }
            return if (rows.isEmpty()) "Top offenders are not in yet." else rows.joinToString { "${it.label} ${Scoring.formatDuration(it.seconds)}" }
        }
        return if (usage.todaySeconds >= 60) "Screen time today is ${Scoring.formatDuration(usage.todaySeconds)}." else "Screen time is not in yet."
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

    private fun accessToken(): String = session.getString("access", "").orEmpty()
    private fun expiresAt(): Long = session.getLong("expires", 0L)

    private fun call(method: String, path: String, body: String?, bearer: String?, prefer: String? = null): HttpResult {
        val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        if (key.isBlank()) return HttpResult(0, null, null, "Add SUPABASE_PUBLISHABLE_KEY to local.properties.")
        val builder = Request.Builder().url(BuildConfig.SUPABASE_URL + path).header("apikey", key)
        if (!bearer.isNullOrBlank()) builder.header("Authorization", "Bearer $bearer")
        if (prefer != null) builder.header("Prefer", prefer)
        if (method == "GET") builder.get() else builder.method(method, (body ?: "{}").toRequestBody(jsonType))
        return runCatching {
            http.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val obj = runCatching { JSONObject(text) }.getOrNull()
                val array = runCatching { JSONArray(text) }.getOrNull()
                HttpResult(response.code, obj, array, obj?.optString("error")?.ifBlank { obj.optString("msg") })
            }
        }.getOrElse { HttpResult(0, null, null, it.message) }
    }

    private data class HttpResult(val code: Int, val json: JSONObject?, val array: JSONArray?, val error: String?)

    companion object {
        private fun openSession(context: Context): SharedPreferences {
            val master = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            return EncryptedSharedPreferences.create(
                context,
                "rotrun.session",
                master,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }

        private fun randomNonce(): String {
            val alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._"
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            return buildString(32) { bytes.forEach { append(alphabet[(it.toInt() and 0xff) % alphabet.length]) } }
        }

        private fun sha256(raw: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
