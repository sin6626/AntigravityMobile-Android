package com.antigravity.mobile.ui.demo

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresPermission
import kotlin.concurrent.thread
import kotlin.math.sqrt

internal class VoiceRecorder {
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var recording = false
    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var stopped: (() -> Unit)? = null

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(
        onAudio: (bytes: ByteArray, level: Float, elapsedMillis: Long) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        if (recording) return
        val minSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT)
        if (minSize <= 0) {
            onFailure("当前设备无法初始化麦克风")
            return
        }
        val recorder = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minSize, CHUNK_SAMPLES * 4))
        } catch (_: Exception) {
            onFailure("当前设备无法初始化麦克风")
            return
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            onFailure("当前设备无法初始化麦克风")
            return
        }
        recording = true
        audioRecord = recorder
        thread(name = "voice-recorder", isDaemon = true) {
            val samples = ShortArray(CHUNK_SAMPLES)
            val startedAt = android.os.SystemClock.elapsedRealtime()
            try {
                recorder.startRecording()
                while (recording) {
                    val count = recorder.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)
                    if (count <= 0) continue
                    var energy = 0.0
                    val bytes = ByteArray(count * 2)
                    repeat(count) { index ->
                        val sample = samples[index].toInt()
                        energy += sample.toDouble() * sample
                        bytes[index * 2] = sample.toByte()
                        bytes[index * 2 + 1] = (sample ushr 8).toByte()
                    }
                    val level = (sqrt(energy / count) / Short.MAX_VALUE).toFloat().coerceIn(0f, 1f)
                    onAudio(bytes, level, android.os.SystemClock.elapsedRealtime() - startedAt)
                }
            } catch (_: Exception) {
                if (recording) mainHandler.post { onFailure("录音中断，请重试") }
            } finally {
                recording = false
                try { recorder.stop() } catch (_: Exception) {}
                recorder.release()
                audioRecord = null
                stopped?.also { stopped = null; mainHandler.post(it) }
            }
        }
    }

    fun stop(onStopped: () -> Unit) {
        if (!recording) {
            onStopped()
            return
        }
        stopped = onStopped
        recording = false
        try { audioRecord?.stop() } catch (_: Exception) {}
    }

    fun cancel() {
        stopped = null
        recording = false
        try { audioRecord?.stop() } catch (_: Exception) {}
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val CHUNK_SAMPLES = 1_600
    }
}
