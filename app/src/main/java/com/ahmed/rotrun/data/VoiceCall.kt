package com.ahmed.rotrun.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import com.ahmed.rotrun.logic.FreeTier
import com.ahmed.rotrun.logic.VoiceSession
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Live voice with the short-lived token from agent-qa. The xAI API key stays on the server. */
class VoiceCall(
    private val context: Context,
    private val onStatus: (String) -> Unit,
    private val onEnded: (String?) -> Unit,
    private val onTranscript: (String) -> Unit = {},
) {
    private val running = AtomicBoolean(false)
    private val generation = AtomicInteger(0)
    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    private var socket: WebSocket? = null
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private var micThread: Thread? = null
    private var playThread: Thread? = null
    private val playback = LinkedBlockingQueue<ByteArray>()
    private var routed = false
    private var previousMode = AudioManager.MODE_NORMAL
    private var previousSpeaker = false

    fun start(
        token: String,
        model: String,
        agentId: String,
        voice: String,
        instructions: String,
        maxSeconds: Double?,
        onConnected: () -> Unit,
    ): String? {
        stop()
        val gen = generation.incrementAndGet()
        running.set(true)
        val routed = routeSpeaker(true)
        if (routed != null) {
            running.set(false)
            releaseAudio()
            return routed
        }
        val request = Request.Builder()
            .url(VoiceSession.realtimeUrl(model, agentId))
            .header("Authorization", "Bearer $token")
            .build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (generation.get() != gen) return
                val session = JSONObject()
                    .put("type", "session.update")
                    .put(
                        "session",
                        JSONObject()
                            .put("voice", voice)
                            .put("instructions", instructions)
                            .put("turn_detection", JSONObject().put("type", "server_vad"))
                            .put(
                                "audio",
                                JSONObject()
                                    .put(
                                        "input",
                                        JSONObject()
                                            .put("format", JSONObject().put("type", "audio/pcm").put("rate", 16000))
                                            .put("transport", "binary"),
                                    )
                                    .put(
                                        "output",
                                        JSONObject()
                                            .put("format", JSONObject().put("type", "audio/pcm").put("rate", 16000))
                                            .put("transport", "binary"),
                                    ),
                            ),
                    )
                webSocket.send(session.toString())
                if (!startMic(webSocket)) {
                    if (running.getAndSet(false)) onEnded("The microphone didn’t start. Allow the microphone for RotRun.")
                    releaseAudio()
                    return
                }
                val playbackError = ensurePlayer()
                if (playbackError != null) {
                    if (running.getAndSet(false)) onEnded(playbackError)
                    releaseAudio()
                    return
                }
                onConnected()
                armLimit(maxSeconds)
                onStatus("Listening")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (generation.get() != gen) return
                val json = runCatching { JSONObject(text) }.getOrNull() ?: return
                val type = json.optString("type")
                if (type == "error") {
                    val message = json.optJSONObject("error")?.optString("message").orEmpty()
                        .ifBlank { json.optString("message") }
                        .ifBlank { "xAI voice returned an error." }
                    if (running.getAndSet(false)) onEnded(message)
                    releaseAudio()
                    webSocket.close(1000, "error")
                    return
                }
                transcriptOf(json).takeIf { it.isNotBlank() }?.let(onTranscript)
                when {
                    type.contains("speech_stopped") || type.contains("response.done") || type.contains("response.audio.done") -> onStatus("Listening")
                    type.contains("speech_started") || type.contains("output_audio") || type.contains("audio") -> onStatus("Talking")
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (generation.get() != gen || !running.get()) return
                playback.offer(bytes.toByteArray())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (generation.get() != gen) return
                val http = response?.code
                val detail = t.message?.takeIf { it.isNotBlank() } ?: "Voice disconnected"
                val message = if (http != null) "$detail ($http)" else detail
                if (running.getAndSet(false)) onEnded(message)
                releaseAudio()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (generation.get() != gen) return
                if (running.getAndSet(false)) onEnded(null)
                releaseAudio()
            }
        })
        return null
    }

    fun stop() {
        generation.incrementAndGet()
        running.set(false)
        socket?.close(1000, "done")
        socket = null
        releaseAudio()
    }

    private fun armLimit(maxSeconds: Double?) {
        val seconds = maxSeconds ?: return
        if (seconds <= 15) return
        val gen = generation.get()
        Thread {
            val wrapAt = ((seconds - 15) * 1000).toLong()
            sleepWhile(wrapAt)
            if (generation.get() != gen || !running.get()) return@Thread
            onStatus(FreeTier.voiceWrapUp)
            sleepWhile(15_000)
            if (generation.get() == gen && running.get()) stop()
        }.start()
    }

    private fun sleepWhile(millis: Long) {
        var left = millis
        while (left > 0 && running.get()) {
            val slice = minOf(left, 250L)
            Thread.sleep(slice)
            left -= slice
        }
    }

    private fun startMic(webSocket: WebSocket): Boolean {
        val record = openMic() ?: return false
        recorder = record
        val thread = Thread {
            val buffer = ByteArray(3200)
            while (running.get()) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) webSocket.send(buffer.toByteString(0, read))
            }
        }
        micThread = thread
        thread.start()
        return true
    }

    private fun openMic(): AudioRecord? {
        val sources = intArrayOf(MediaRecorder.AudioSource.VOICE_COMMUNICATION, MediaRecorder.AudioSource.MIC)
        for (source in sources) {
            val min = AudioRecord.getMinBufferSize(16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (min <= 0) continue
            val record = try {
                AudioRecord(source, 16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 3200))
            } catch (_: SecurityException) {
                return null
            } catch (_: IllegalArgumentException) {
                continue
            }
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                continue
            }
            val started = runCatching { record.startRecording() }.isSuccess &&
                record.recordingState == AudioRecord.RECORDSTATE_RECORDING
            if (started) return record
            record.release()
        }
        return null
    }

    private fun ensurePlayer(): String? {
        if (playThread != null) return null
        val track = try {
            AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(16_000)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(16_000 * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        } catch (error: Exception) {
            return "Playback couldn't start. ${error.message ?: "The speaker didn't start."}"
        }
        player = track
        val started = runCatching { track.play() }
        if (started.isFailure) {
            track.release()
            player = null
            return "Playback couldn't start. ${started.exceptionOrNull()?.message ?: "The speaker didn't start."}"
        }
        val thread = Thread {
            while (running.get() || playback.isNotEmpty()) {
                val chunk = playback.poll(200, TimeUnit.MILLISECONDS) ?: continue
                track.write(chunk, 0, chunk.size)
            }
        }
        playThread = thread
        thread.start()
        return null
    }

    private fun transcriptOf(json: JSONObject): String {
        json.optString("transcript").takeIf { it.isNotBlank() }?.let { return it }
        if (json.optString("type").contains("transcript")) {
            json.optString("delta").takeIf { it.isNotBlank() }?.let { return it }
        }
        listOf("item", "part", "audio").forEach { key ->
            json.optJSONObject(key)?.optString("transcript")?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return ""
    }

    private fun routeSpeaker(on: Boolean): String? {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return "The microphone session couldn't start."
        return try {
            if (on) {
                if (!routed) {
                    previousMode = audio.mode
                    previousSpeaker = audio.isSpeakerphoneOn
                    routed = true
                }
                audio.mode = AudioManager.MODE_IN_COMMUNICATION
                @Suppress("DEPRECATION")
                audio.isSpeakerphoneOn = true
            } else if (routed) {
                @Suppress("DEPRECATION")
                audio.isSpeakerphoneOn = previousSpeaker
                audio.mode = previousMode
                routed = false
            }
            null
        } catch (error: Exception) {
            "The microphone session couldn't start. ${error.message ?: "Audio routing failed."}"
        }
    }

    private fun releaseAudio() {
        runCatching { recorder?.stop() }
        recorder?.release()
        recorder = null
        playback.clear()
        runCatching { player?.stop() }
        player?.release()
        player = null
        micThread = null
        playThread = null
        routeSpeaker(false)
    }
}
