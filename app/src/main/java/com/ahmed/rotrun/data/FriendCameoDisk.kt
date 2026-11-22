package com.ahmed.rotrun.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.ahmed.rotrun.logic.FriendCameoCache
import com.ahmed.rotrun.logic.FriendCameoPayload
import com.ahmed.rotrun.logic.FriendCameoPick
import com.ahmed.rotrun.logic.FriendCameoSurface
import java.io.File
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local copy of today's accepted friends for the block overlay.
 * The overlay reads this and does not call Supabase. Fresh reads replace it.
 * Same 15 minute lifetime as iOS `FriendCameoCacheStore`.
 */
object FriendCameoDisk {
    private const val PREFS = "rotrun.flags"
    private const val KEY = "social.friendCameo.cache.v1"
    private const val STATE_PREFS = "rotrun"
    private val uuid = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

    fun friendCameoEnabled(context: Context): Boolean {
        val raw = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).getString("state", null) ?: return true
        return runCatching { JSONObject(raw).optBoolean("friendCameo", true) }.getOrDefault(true)
    }

    fun surface(context: Context, enabled: Boolean, nowMillis: Long = System.currentTimeMillis()): FriendCameoSurface =
        FriendCameoCache.resolve(load(context), enabled, nowMillis)

    fun save(context: Context, payload: FriendCameoPayload) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, encode(payload)) }
    }

    fun load(context: Context): FriendCameoPayload? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return null
        return decode(raw)
    }

    fun portraitFile(context: Context, userId: String): File? {
        val id = userId.trim().lowercase(Locale.US)
        if (!uuid.matches(id)) return null
        return File(directory(context), "$id.jpg")
    }

    fun portraitBytes(context: Context, userId: String): ByteArray? {
        val file = portraitFile(context, userId) ?: return null
        if (!file.isFile) return null
        return runCatching { file.readBytes() }.getOrNull()
    }

    /** Replaces portrait files. Friends who left are deleted so a stale photo cannot appear. */
    fun replacePortraits(context: Context, images: Map<String, ByteArray>) {
        val directory = directory(context)
        directory.mkdirs()
        val keep = images.keys.map { it.trim().lowercase(Locale.US) }.toSet()
        directory.listFiles()?.forEach { file ->
            if (file.extension == "jpg" && file.nameWithoutExtension.lowercase(Locale.US) !in keep) {
                file.delete()
            }
        }
        images.forEach { (userId, data) ->
            if (data.isEmpty() || data.size > 80_000) return@forEach
            val file = portraitFile(context, userId) ?: return@forEach
            runCatching { file.writeBytes(data) }
        }
    }

    fun thumbnail(bytes: ByteArray): ByteArray? {
        val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val edge = 128
        val scale = maxOf(edge.toFloat() / image.width.coerceAtLeast(1), edge.toFloat() / image.height.coerceAtLeast(1))
        val width = (image.width * scale).toInt().coerceAtLeast(1)
        val height = (image.height * scale).toInt().coerceAtLeast(1)
        val scaled = image.scale(width, height, true)
        val square = createBitmap(edge, edge)
        val canvas = android.graphics.Canvas(square)
        canvas.drawBitmap(scaled, (edge - width) / 2f, (edge - height) / 2f, null)
        val stream = java.io.ByteArrayOutputStream()
        square.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        if (scaled !== image) image.recycle()
        if (scaled !== square) scaled.recycle()
        square.recycle()
        val jpeg = stream.toByteArray()
        return jpeg.takeIf { it.size in 1..80_000 }
    }

    private fun directory(context: Context) = File(context.filesDir, "FriendCameoAvatars")

    private fun encode(payload: FriendCameoPayload): String {
        val friends = JSONArray()
        payload.friends.forEach { friend ->
            friends.put(
                JSONObject()
                    .put("userID", friend.userId)
                    .put("name", friend.name)
                    .put("xp", friend.xp)
                    .put("avatarURL", friend.avatarUrl ?: JSONObject.NULL),
            )
        }
        return JSONObject()
            .put("writtenAt", payload.writtenAtMillis)
            .put("yourUserID", payload.yourUserId)
            .put("yourXP", payload.yourXp)
            .put("friends", friends)
            .toString()
    }

    private fun decode(raw: String): FriendCameoPayload? = runCatching {
        val obj = JSONObject(raw)
        val friends = obj.optJSONArray("friends") ?: JSONArray()
        val rows = buildList {
            for (index in 0 until friends.length()) {
                val row = friends.optJSONObject(index) ?: continue
                add(
                    FriendCameoPick.Candidate(
                        userId = row.optString("userID"),
                        name = row.optString("name"),
                        xp = row.optInt("xp"),
                        avatarUrl = row.optString("avatarURL").takeIf { it.isNotBlank() && it != "null" },
                    ),
                )
            }
        }
        FriendCameoPayload(
            writtenAtMillis = obj.optLong("writtenAt"),
            yourUserId = obj.optString("yourUserID"),
            yourXp = obj.optInt("yourXP"),
            friends = rows,
        )
    }.getOrNull()
}
