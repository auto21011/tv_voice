package com.voice.search

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Random

class MainActivity : Activity() {

    private var voiceRecognizer: VoiceRecognizer? = null
    private var isProcessing = false

    private lateinit var tvStatus: TextView
    private lateinit var tvPartialResult: TextView
    private lateinit var tvHint: TextView
    private lateinit var micContainer: View
    private lateinit var waveBars: List<View>
    private val random = Random()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tv_status)
        tvPartialResult = findViewById(R.id.tv_partial_result)
        tvHint = findViewById(R.id.tv_hint)
        micContainer = findViewById(R.id.mic_container)

        waveBars = listOf(
            findViewById(R.id.wave_bar_1),
            findViewById(R.id.wave_bar_2),
            findViewById(R.id.wave_bar_3),
            findViewById(R.id.wave_bar_4),
            findViewById(R.id.wave_bar_5)
        )

        findViewById<View>(R.id.transparent_area).setOnClickListener {
            if (!isProcessing) finish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (voiceRecognizer == null && !isProcessing) {
            startFlow()
        }
    }

    private fun startFlow() {
        isProcessing = true

        if (!hasAudioPermission()) {
            requestAudioPermission()
            return
        }

        if (!hasUsageStatsPermission()) {
            showUsageStatsHint()
            return
        }

        if (!TvBoxHelper.isTvBoxInstalled(this)) {
            showAndFinish("TVBox未安装", 3000)
            return
        }

        if (ForegroundDetector.isTvBoxInForeground(this)) {
            startVoiceRecognition()
        } else {
            tvStatus.text = "正在启动TVBox..."
            TvBoxHelper.launchTvBox(this)
            Thread {
                val ready = ForegroundDetector.waitForTvBoxForeground(this, 10_000)
                runOnUiThread {
                    if (ready) {
                        startVoiceRecognition()
                    } else {
                        showAndFinish(getString(R.string.tvbox_timeout), 3000)
                    }
                }
            }.start()
        }
    }

    private fun startVoiceRecognition() {
        tvStatus.text = getString(R.string.listening)
        tvHint.text = getString(R.string.stop_hint)
        startMicAnimation()
        startWaveAnimation()

        voiceRecognizer = VoiceRecognizer(
            this,
            object : VoiceRecognizerCallback {
                override fun onPartialResult(text: String) {
                    runOnUiThread {
                        tvPartialResult.text = text
                    }
                }

                override fun onFinalResult(text: String) {
                    runOnUiThread {
                        onRecognized(text)
                    }
                }

                override fun onError(error: String) {
                    runOnUiThread {
                        showAndFinish(error, 3000)
                    }
                }

                override fun onSilenceDetected() {
                    runOnUiThread {
                        voiceRecognizer?.stop()
                        val text = tvPartialResult.text?.toString()
                        if (!text.isNullOrBlank()) {
                            onRecognized(text)
                        } else {
                            showAndFinish(getString(R.string.no_speech), 3000)
                        }
                    }
                }
            }
        )
        voiceRecognizer?.start()
    }

    private fun onRecognized(text: String) {
        stopAnimations()
        val keyword = KeywordExtractor.extract(text)

        if (keyword != null && keyword.isNotBlank()) {
            tvStatus.text = "正在搜索: $keyword"
            tvPartialResult.text = ""
            tvHint.text = ""

            Thread {
                Thread.sleep(500)
                TvBoxHelper.sendSearchBroadcast(this, keyword)
                runOnUiThread {
                    finish()
                }
            }.start()
        } else {
            showAndFinish(getString(R.string.no_keyword), 3000)
        }
    }

    private fun showAndFinish(message: String, delayMs: Long) {
        tvStatus.text = message
        tvPartialResult.text = ""
        tvHint.text = ""
        stopAnimations()

        micContainer.postDelayed({
            if (!isFinishing) finish()
        }, delayMs)
    }

    private fun startMicAnimation() {
        val anim = ScaleAnimation(
            1.0f, 1.15f, 1.0f, 1.15f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 1000
            repeatCount = Animation.INFINITE
            repeatMode = Animation.REVERSE
        }
        micContainer.startAnimation(anim)
    }

    private fun startWaveAnimation() {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                waveBars.forEach { bar ->
                    val lp = bar.layoutParams
                    lp.height = 12 + random.nextInt(30)
                    bar.layoutParams = lp
                }
                handler.postDelayed(this, 200)
            }
        }
        handler.post(runnable)
    }

    private fun stopAnimations() {
        micContainer.clearAnimation()
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
        return try {
            val mode = appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    private fun requestAudioPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_AUDIO_PERMISSION
        )
    }

    private fun showUsageStatsHint() {
        tvStatus.text = getString(R.string.permission_usage)
        tvHint.visibility = View.GONE

        micContainer.postDelayed({
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            try {
                startActivity(intent)
            } catch (e: Exception) {
                // fallback
            }
            finish()
        }, 2000)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startFlow()
            } else {
                showAndFinish(getString(R.string.permission_audio), 3000)
            }
        }
    }

    override fun onBackPressed() {
        voiceRecognizer?.release()
        voiceRecognizer = null
        super.onBackPressed()
    }

    override fun onPause() {
        super.onPause()
        if (!isProcessing) {
            voiceRecognizer?.release()
            voiceRecognizer = null
        }
    }

    override fun onDestroy() {
        voiceRecognizer?.release()
        voiceRecognizer = null
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_AUDIO_PERMISSION = 100
    }
}