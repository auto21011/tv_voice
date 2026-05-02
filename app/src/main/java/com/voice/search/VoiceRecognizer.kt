package com.voice.search

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.IOException
import org.json.JSONObject

interface VoiceRecognizerCallback {
    fun onPartialResult(text: String)
    fun onFinalResult(text: String)
    fun onError(error: String)
    fun onSilenceDetected()
}

class VoiceRecognizer(
    private val context: Context,
    private val callback: VoiceRecognizerCallback,
    private val silenceTimeoutMs: Long = 2000,
    private val globalTimeoutMs: Long = 20000
) {
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var lastSpeechTimestamp = 0L
    private var silenceCheckRunning = false
    private var hasSpoken = false
    private var globalTimeoutHandler: android.os.Handler? = null
    @Volatile private var isRunning = false

    private companion object {
        const val SAMPLE_RATE = 16000
    }

    fun start() {
        StorageService.unpack(
            context, "vosk-model-small-cn", "model",
            { model ->
                this@VoiceRecognizer.model = model
                try {
                    recognizer = Recognizer(model, SAMPLE_RATE.toFloat())
                    recognizer?.setWords(true)
                    recognizer?.setPartialWords(true)
                } catch (e: IOException) {
                    callback.onError("Vosk初始化失败: ${e.message}")
                    return@unpack
                }
                startRecording()
            },
            { exception ->
                callback.onError("模型加载失败: ${exception.message}")
            }
        )
    }

    private fun startRecording() {
        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val sources = intArrayOf(
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.DEFAULT,
                MediaRecorder.AudioSource.CAMCORDER,
                MediaRecorder.AudioSource.VOICE_RECOGNITION
            )

            for (source in sources) {
                try {
                    audioRecord = AudioRecord(
                        source,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize
                    )
                    if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                        break
                    }
                    audioRecord?.release()
                    audioRecord = null
                } catch (e: Exception) {
                    audioRecord?.release()
                    audioRecord = null
                }
            }

            if (audioRecord == null) {
                callback.onError("无法初始化麦克风，请检查权限")
                return
            }

            isRunning = true
            audioRecord?.startRecording()

            globalTimeoutHandler = android.os.Handler(android.os.Looper.getMainLooper()).apply {
                postDelayed({
                    if (!hasSpoken) {
                        callback.onSilenceDetected()
                    }
                }, globalTimeoutMs)
            }

            recordingThread = Thread {
                val buffer = ShortArray(bufferSize / 2)
                while (isRunning) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
                    if (read > 0 && isRunning) {
                        recognizer?.acceptWaveForm(buffer, read)
                        val result = recognizer?.partialResult
                        if (!result.isNullOrEmpty()) {
                            processResult(result)
                        }
                    }
                }
            }
            recordingThread?.start()
        } catch (e: Exception) {
            callback.onError("录音启动失败: ${e.message}")
        }
    }

    private fun processResult(result: String) {
        try {
            val partial = JSONObject(result).optString("partial", "")
            if (partial.isNotBlank()) {
                if (!hasSpoken) {
                    hasSpoken = true
                    cancelGlobalTimeout()
                }
                lastSpeechTimestamp = System.currentTimeMillis()
                callback.onPartialResult(partial)
                startSilenceDetection()
            }
        } catch (e: Exception) {
            // ignore parse errors
        }
    }

    private fun startSilenceDetection() {
        if (silenceCheckRunning) return
        silenceCheckRunning = true

        Thread {
            while (isRunning) {
                try {
                    Thread.sleep(500)
                } catch (e: InterruptedException) {
                    break
                }
                val elapsed = System.currentTimeMillis() - lastSpeechTimestamp
                if (elapsed >= silenceTimeoutMs) {
                    silenceCheckRunning = false
                    if (!isRunning) return
                    val finalResult = recognizer?.finalResult
                    if (!finalResult.isNullOrEmpty()) {
                        val text = try {
                            JSONObject(finalResult).optString("text", "")
                        } catch (e: Exception) { "" }
                        if (text.isNotBlank()) {
                            callback.onFinalResult(text)
                            return
                        }
                    }
                    callback.onSilenceDetected()
                    break
                }
            }
        }.start()
    }

    private fun cancelGlobalTimeout() {
        globalTimeoutHandler?.removeCallbacksAndMessages(null)
        globalTimeoutHandler = null
    }

    fun stop() {
        isRunning = false
        try {
            recordingThread?.interrupt()
            recordingThread = null
        } catch (e: Exception) {
            // ignore
        }
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            // ignore
        }
    }

    fun release() {
        cancelGlobalTimeout()
        stop()
        try {
            recognizer?.close()
            recognizer = null
        } catch (e: Exception) {
            // ignore
        }
        try {
            model?.close()
            model = null
        } catch (e: Exception) {
            // ignore
        }
    }
}