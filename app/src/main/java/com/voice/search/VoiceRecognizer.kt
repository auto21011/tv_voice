package com.voice.search

import android.content.Context
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
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
    private var speechService: SpeechService? = null
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var lastSpeechTimestamp = 0L
    private var silenceCheckRunning = false
    private var hasSpoken = false
    private var globalTimeoutHandler: android.os.Handler? = null

    fun start() {
        StorageService.unpack(
            context, "vosk-model-small-cn", "model",
            { model ->
                this@VoiceRecognizer.model = model
                try {
                    recognizer = Recognizer(model, 16000.0f)
                } catch (e: IOException) {
                    callback.onError("Vosk初始化失败: ${e.message}")
                    return@unpack
                }
                startSpeechService()
            },
            { exception ->
                callback.onError("模型加载失败: ${exception.message}")
            }
        )
    }

    private fun startSpeechService() {
        try {
            recognizer?.setWords(true)
            recognizer?.setPartialWords(true)
            speechService = SpeechService(recognizer, 16000.0f)
            speechService?.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    val text = extractPartial(hypothesis)
                    if (text != null) {
                        if (!hasSpoken) {
                            hasSpoken = true
                            cancelGlobalTimeout()
                        }
                        lastSpeechTimestamp = System.currentTimeMillis()
                        callback.onPartialResult(text)
                        startSilenceDetection()
                    }
                }

                override fun onResult(hypothesis: String?) {
                    val text = extractPartial(hypothesis)
                    if (!text.isNullOrBlank()) {
                        callback.onFinalResult(text)
                    }
                }

                override fun onFinalResult(hypothesis: String?) {
                    val text = extractPartial(hypothesis)
                    if (!text.isNullOrBlank()) {
                        callback.onFinalResult(text)
                    }
                }

                override fun onError(e: Exception) {
                    callback.onError("识别错误: ${e.message}")
                }

                override fun onTimeout() {
                    if (!hasSpoken) {
                        callback.onSilenceDetected()
                    }
                }
            })
            globalTimeoutHandler = android.os.Handler(android.os.Looper.getMainLooper()).apply {
                postDelayed({
                    if (!hasSpoken) {
                        callback.onSilenceDetected()
                    }
                }, globalTimeoutMs)
            }
        } catch (e: Exception) {
            callback.onError("SpeechService启动失败: ${e.message}")
        }
    }

    private fun extractPartial(hypothesis: String?): String? {
        if (hypothesis == null) return null
        return try {
            val text = JSONObject(hypothesis).optString("partial", "")
            if (text.isEmpty()) null else text
        } catch (e: Exception) {
            null
        }
    }

    private fun startSilenceDetection() {
        if (silenceCheckRunning) return
        silenceCheckRunning = true

        Thread {
            while (true) {
                try {
                    Thread.sleep(500)
                } catch (e: InterruptedException) {
                    break
                }
                val elapsed = System.currentTimeMillis() - lastSpeechTimestamp
                if (elapsed >= silenceTimeoutMs) {
                    silenceCheckRunning = false
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
        try {
            speechService?.stop()
            speechService = null
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