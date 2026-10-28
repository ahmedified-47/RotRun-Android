package com.ahmed.rotrun.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import com.ahmed.rotrun.logic.FreeTier
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Live voice with the short-lived token from agent-qa. The xAI API key stays on the server. */
class VoiceCall(
    private val onStatus: (String) -> Unit,
    private val onEnded: (String?) -> Unit,
) {
    private val running = AtomicBoolean(false)
    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    private var socket: WebSocket? = null
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private var micThread: Thread? = null

    fun start(token: String, model: String, voice: String, instructions: String, maxSeconds: Double?) {
        stop()
        running.set(true)
        val url = "wss://api.x.ai/v1/realtime?model=$model"
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
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
                startMic(webSocket)
                armLimit(maxSeconds)
                onStatus("Listening")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val type = runCatching { JSONObject(text).optString("type") }.getOrDefault("")
                if (type.contains("speech_started") || type.contains("audio")) onStatus("Rot is talking")
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                play(bytes.toByteArray())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (running.getAndSet(false)) onEnded(t.message ?: "Voice disconnected")
                releaseAudio()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (running.getAndSet(false)) onEnded(null)
                releaseAudio()
            }
        })
    }

    fun stop() {
        running.set(false)
        socket?.close(1000, "done")
        socket = null
        releaseAudio()
    }

    private fun armLimit(maxSeconds: Double?) {
        val seconds = maxSeconds ?: return
        if (seconds <= 15) return
        Thread {
            val wrapAt = ((seconds - 15) * 1000).toLong()
            sleepWhile(wrapAt)
            if (!running.get()) return@Thread
            onStatus(FreeTier.voiceWrapUp)
            sleepWhile(15_000)
            if (running.get()) stop()
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

    private fun startMic(webSocket: WebSocket) {
        val min = AudioRecord.getMinBufferSize(16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            16_000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(min, 3200),
        )
        recorder = record
        record.startRecording()
        val thread = Thread {
            val buffer = ByteArray(3200)
            while (running.get()) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) webSocket.send(ByteString.of(*buffer.copyOf(read)))
            }
        }
        micThread = thread
        thread.start()
    }

    private fun play(pcm: ByteArray) {
        val track = player ?: AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).build())
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(16_000)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(pcm.size * 4, 8192))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .also {
                player = it
                it.play()
            }
        track.write(pcm, 0, pcm.size)
    }

    private fun releaseAudio() {
        runCatching { recorder?.stop() }
        recorder?.release()
        recorder = null
        runCatching { player?.stop() }
        player?.release()
        player = null
        micThread = null
    }
}
