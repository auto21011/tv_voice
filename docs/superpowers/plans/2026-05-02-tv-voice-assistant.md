# TV语音助手实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建Android TV底部悬浮窗语音助手，Vosk离线识别→提取关键词→广播到TVBox搜索

**Architecture:** 单个半透明Activity（上半透明可见TVBox，底部悬浮窗显示录音状态）+ Vosk SpeechService离线识别 + UsageStatsManager检测前台应用

**Tech Stack:** Kotlin, Gradle Kotlin DSL, Vosk Android SDK, AndroidX, API 21+

**前置准备：** 
- 从 https://alphacephei.com/vosk/models 下载 `vosk-model-small-cn-0.22.zip`，解压到 `app/src/main/assets/vosk-model-small-cn/`
- 从 https://github.com/alphacep/vosk-android-demo 获取 `vosk-android-demo` 作为Vosk集成参考

---

## 文件结构

```
voice/
├── build.gradle.kts                    # 根构建文件
├── settings.gradle.kts                 # 模块配置
├── gradle.properties                   # Gradle属性
├── gradle/wrapper/                     # Gradle wrapper
├── gradlew / gradlew.bat
├── app/
│   ├── build.gradle.kts                # 应用构建文件
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/voice/search/
│       │   ├── MainActivity.kt         # 半透明Activity
│       │   ├── VoiceRecognizer.kt      # Vosk封装
│       │   ├── KeywordExtractor.kt     # 关键词提取
│       │   ├── TvBoxHelper.kt          # 广播+启动
│       │   └── ForegroundDetector.kt   # 前台检测
│       ├── res/
│       │   ├── layout/
│       │   │   └── activity_main.xml
│       │   └── values/
│       │       ├── strings.xml
│       │       ├── colors.xml
│       │       └── themes.xml
│       └── assets/
│           └── vosk-model-small-cn/    # 手动放置模型
```

---

### Task 1: Project Scaffolding

**Files:**
- Create: `build.gradle.kts`
- Create: `settings.gradle.kts`
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/colors.xml`

- [ ] **Step 1: Create root build.gradle.kts**

```kotlin
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
```

- [ ] **Step 2: Create settings.gradle.kts**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "VoiceSearch"
include(":app")
```

- [ ] **Step 3: Create gradle.properties**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 4: Create app/build.gradle.kts**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.voice.search"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.voice.search"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("com.alphacephei:vosk-android:0.3.47")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("net.java.dev.jna:jna:5.13.0@aar")
}
```

- [ ] **Step 5: Create app/proguard-rules.pro**

```
-keep class com.voice.search.** { *; }
```

- [ ] **Step 6: Create AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" />

    <uses-feature android:name="android.software.leanback" android:required="false" />
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.AppCompat.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:theme="@style/Theme.VoiceSearch.Translucent"
            android:launchMode="singleTask"
            android:taskAffinity=""
            android:excludeFromRecents="true"
            android:exported="true">

            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

- [ ] **Step 7: Create res/values/strings.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">语音搜索</string>
    <string name="listening">正在聆听…</string>
    <string name="stop_hint">停止说话后自动识别</string>
    <string name="no_speech">未识别到语音</string>
    <string name="no_keyword">未识别到搜索内容</string>
    <string name="model_error">模型加载失败</string>
    <string name="tvbox_timeout">无法启动TVBox</string>
    <string name="permission_audio">请授予录音权限</string>
    <string name="permission_usage">请授予使用情况访问权限</string>
</resources>
```

- [ ] **Step 8: Create res/values/colors.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="panel_bg">#DD1A1A2E</color>
    <color name="accent">#FF667EEA</color>
    <color name="accent_dark">#FF764BA2</color>
    <color name="text_primary">#FFFFFFFF</color>
    <color name="text_secondary">#FFAAAAAA</color>
    <color name="text_hint">#FF666666</color>
    <color name="wave_bar">#FF667EEA</color>
</resources>
```

- [ ] **Step 9: Verify project structure**

```bash
ls voice/app/src/main/java/com/voice/search/ voice/app/src/main/res/ voice/app/src/main/AndroidManifest.xml
```

Expected: All directories and files exist.

---

### Task 2: Theme (半透明)

**Files:**
- Create: `app/src/main/res/values/themes.xml`

- [ ] **Step 1: Create translucent theme**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.VoiceSearch.Translucent" parent="Theme.AppCompat.NoActionBar">
        <item name="android:windowIsTranslucent">true</item>
        <item name="android:windowBackground">@android:color/transparent</item>
        <item name="android:windowNoTitle">true</item>
        <item name="android:windowFullscreen">true</item>
        <item name="android:windowContentOverlay">@null</item>
        <item name="android:backgroundDimEnabled">false</item>
        <item name="android:windowAnimationStyle">@null</item>
    </style>
</resources>
```

- [ ] **Step 2: Verify theme file exists**

```bash
ls voice/app/src/main/res/values/themes.xml
```

---

### Task 3: KeywordExtractor

**Files:**
- Create: `app/src/main/java/com/voice/search/KeywordExtractor.kt`

- [ ] **Step 1: Implement KeywordExtractor**

```kotlin
package com.voice.search

object KeywordExtractor {
    private val PREFIX_PATTERN = Regex(
        "^(搜索|搜|看|播放|我想看|我要看|我想搜|我要搜|给我搜|帮我搜|帮我找)\\s*",
        RegexOption.IGNORE_CASE
    )
    private val SUFFIX_PATTERN = Regex(
        "\\s*(电视剧|电影|综艺|动漫|纪录片|这部|这个|一下|吧|呗|啊|啦|呢|吗)?$",
        RegexOption.IGNORE_CASE
    )

    fun extract(raw: String?): String? {
        if (raw.isNullOrBlank()) return null

        var keyword = raw.trim()
        keyword = PREFIX_PATTERN.replace(keyword, "")
        keyword = SUFFIX_PATTERN.replace(keyword, "")
        keyword = keyword.trim()

        return if (keyword.isEmpty()) null else keyword
    }
}
```

- [ ] **Step 2: Verify with manual test cases**

Test cases (mental verification):
| 输入 | 期望输出 |
|------|---------|
| "搜索 狂飙 电视剧" | "狂飙" |
| "搜索 狂飙 电视剧" | "狂飙" |
| "看西游记" | "西游记" |
| "播放甄嬛传" | "甄嬛传" |
| "我想看琅琊榜这部" | "琅琊榜" |
| "搜 权力的游戏" | "权力的游戏" |
| null | null |
| "" | null |
| "   " | null |
| "搜索" | null |
| "看" | null |

---

### Task 4: ForegroundDetector

**Files:**
- Create: `app/src/main/java/com/voice/search/ForegroundDetector.kt`

- [ ] **Step 1: Implement ForegroundDetector**

```kotlin
package com.voice.search

import android.app.usage.UsageStatsManager
import android.content.Context

object ForegroundDetector {
    private const val TVBOX_PACKAGE = "com.mygithub0.tvbox0.osdkitkat"

    fun isTvBoxInForeground(context: Context): Boolean {
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val endTime = System.currentTimeMillis()
            val beginTime = endTime - 5000

            val usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, beginTime, endTime
            )

            val foreground = usageStats
                .filter { it.lastTimeUsed > 0 }
                .maxByOrNull { it.lastTimeUsed }

            foreground?.packageName == TVBOX_PACKAGE
        } catch (e: Exception) {
            false
        }
    }

    fun waitForTvBoxForeground(context: Context, timeoutMs: Long = 10_000): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (isTvBoxInForeground(context)) return true
            Thread.sleep(500)
        }
        return false
    }
}
```

- [ ] **Step 2: Verify ForegroundDetector compiles**

Mental check: isTvBoxInForeground uses UsageStatsManager, which requires PACKAGE_USAGE_STATS permission. Without it, the method returns false. waitForTvBoxForeground polls every 500ms with a 10s timeout. Both are sensible defaults.

---

### Task 5: TvBoxHelper

**Files:**
- Create: `app/src/main/java/com/voice/search/TvBoxHelper.kt`

- [ ] **Step 1: Implement TvBoxHelper**

```kotlin
package com.voice.search

import android.content.ComponentName
import android.content.Context
import android.content.Intent

object TvBoxHelper {
    private const val TVBOX_PACKAGE = "com.mygithub0.tvbox0.osdkitkat"
    private const val SEARCH_RECEIVER = "com.github.tvbox.osc.receiver.SearchReceiver"

    fun launchTvBox(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(TVBOX_PACKAGE)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            context.startActivity(intent)
        }
    }

    fun isTvBoxInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(TVBOX_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun sendSearchBroadcast(context: Context, keyword: String) {
        val intent = Intent().apply {
            component = ComponentName(TVBOX_PACKAGE, SEARCH_RECEIVER)
            putExtra("title", keyword)
        }
        try {
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            // TVBox可能未安装或Receiver未注册，静默处理
        }
    }
}
```

- [ ] **Step 2: Verify TvBoxHelper**

Check:
- `launchTvBox`: Uses PackageManager to find launch intent, adds FLAG_ACTIVITY_NEW_TASK + FLAG_ACTIVITY_REORDER_TO_FRONT
- `isTvBoxInstalled`: Safe PackageManager lookup
- `sendSearchBroadcast`: Sends explicit broadcast with ComponentName + title extra

---

### Task 6: VoiceRecognizer (Vosk封装 + 静音检测)

**Files:**
- Create: `app/src/main/java/com/voice/search/VoiceRecognizer.kt`

- [ ] **Step 1: Implement VoiceRecognizer**

```kotlin
package com.voice.search

import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException

interface VoiceRecognizerCallback {
    fun onPartialResult(text: String)
    fun onFinalResult(text: String)
    fun onError(error: String)
    fun onSilenceDetected()
}

class VoiceRecognizer(
    private val context: android.content.Context,
    private val callback: VoiceRecognizerCallback,
    private val silenceTimeoutMs: Long = 2000
) {
    private var speechService: SpeechService? = null
    private var model: Model? = null
    private var recognizer: org.vosk.Recognizer? = null
    private var lastSpeechTimestamp = 0L
    private var silenceCheckRunning = false

    fun start() {
        StorageService.unpack(
            context, "vosk-model-small-cn", "model",
            object : StorageService.StorageServiceCallback {
                override fun onComplete(model: Model) {
                    this@VoiceRecognizer.model = model
                    try {
                        recognizer = Recognizer(model, 16000.0f)
                    } catch (e: IOException) {
                        callback.onError("Vosk初始化失败: ${e.message}")
                        return
                    }
                    startSpeechService()
                }

                override fun onError(message: String) {
                    callback.onError("模型加载失败: $message")
                }
            },
            "model"
        )
    }

    private fun startSpeechService() {
        try {
            speechService = SpeechService(recognizer, 16000.0f)
            speechService?.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    val text = extractPartial(hypothesis)
                    if (text != null) {
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
                    // SpeechService超时，由silence detection接管
                }
            })
            lastSpeechTimestamp = System.currentTimeMillis()
            startSilenceDetection()
        } catch (e: Exception) {
            callback.onError("SpeechService启动失败: ${e.message}")
        }
    }

    private fun extractPartial(hypothesis: String?): String? {
        if (hypothesis == null) return null
        return try {
            val text = org.json.JSONObject(hypothesis).optString("partial", "")
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

    fun stop() {
        try {
            speechService?.stop()
            speechService = null
        } catch (e: Exception) {
            // ignore
        }
        try {
            recognizer?.close()
            recognizer = null
        } catch (e: Exception) {
            // ignore
        }
        model = null
    }

    fun release() {
        stop()
        try {
            model?.close()
            model = null
        } catch (e: Exception) {
            // ignore
        }
    }
}
```

- [ ] **Step 2: Verify VoiceRecognizer flow**

Review: 
1. `start()` → StorageService.unpack loads model from assets
2. Model loaded → `startRecognizer()` creates Recognizer(16kHz)
3. Recognizer → `startSpeechService()` creates SpeechService
4. SpeechService starts listening with RecognitionListener
5. Partial results update `lastSpeechTimestamp`
6. Silence detection thread checks every 500ms, fires `onSilenceDetected()` after `silenceTimeoutMs`
7. `stop()` / `release()` clean up resources

---

### Task 7: Layout (底部悬浮窗)

**Files:**
- Create: `app/src/main/res/layout/activity_main.xml`

- [ ] **Step 1: Create activity_main.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <!-- 上半透明区域：click to dismiss -->
    <View
        android:id="@+id/transparent_area"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:background="@android:color/transparent"
        android:clickable="true"
        android:focusable="true" />

    <!-- 底部悬浮窗 -->
    <LinearLayout
        android:id="@+id/bottom_panel"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        android:background="@drawable/panel_bg"
        android:gravity="center"
        android:orientation="vertical"
        android:paddingTop="24dp"
        android:paddingBottom="32dp"
        android:paddingStart="32dp"
        android:paddingEnd="32dp">

        <!-- 状态指示 -->
        <LinearLayout
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <!-- 麦克风图标 -->
            <FrameLayout
                android:id="@+id/mic_container"
                android:layout_width="64dp"
                android:layout_height="64dp"
                android:layout_marginEnd="20dp">

                <ImageView
                    android:id="@+id/iv_mic"
                    android:layout_width="64dp"
                    android:layout_height="64dp"
                    android:src="@drawable/ic_mic"
                    android:contentDescription="@string/listening" />
            </FrameLayout>

            <!-- 波形条 -->
            <LinearLayout
                android:id="@+id/wave_container"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal">

                <View
                    android:id="@+id/wave_bar_1"
                    android:layout_width="5dp"
                    android:layout_height="20dp"
                    android:layout_marginEnd="6dp"
                    android:background="@color/wave_bar" />

                <View
                    android:id="@+id/wave_bar_2"
                    android:layout_width="5dp"
                    android:layout_height="32dp"
                    android:layout_marginEnd="6dp"
                    android:background="@color/wave_bar" />

                <View
                    android:id="@+id/wave_bar_3"
                    android:layout_width="5dp"
                    android:layout_height="24dp"
                    android:layout_marginEnd="6dp"
                    android:background="@color/wave_bar" />

                <View
                    android:id="@+id/wave_bar_4"
                    android:layout_width="5dp"
                    android:layout_height="38dp"
                    android:layout_marginEnd="6dp"
                    android:background="@color/wave_bar" />

                <View
                    android:id="@+id/wave_bar_5"
                    android:layout_width="5dp"
                    android:layout_height="16dp"
                    android:background="@color/wave_bar" />
            </LinearLayout>

            <!-- 识别文本区域 -->
            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:layout_marginStart="20dp"
                android:orientation="vertical">

                <TextView
                    android:id="@+id/tv_status"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/listening"
                    android:textColor="@color/text_primary"
                    android:textSize="20sp" />

                <TextView
                    android:id="@+id/tv_partial_result"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:textColor="@color/accent"
                    android:textSize="16sp" />
            </LinearLayout>
        </LinearLayout>

        <!-- 底部提示 -->
        <TextView
            android:id="@+id/tv_hint"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:text="@string/stop_hint"
            android:textColor="@color/text_hint"
            android:textSize="14sp" />
    </LinearLayout>

</FrameLayout>
```

- [ ] **Step 2: Create drawable shape for panel background**

Create `app/src/main/res/drawable/panel_bg.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/panel_bg" />
    <corners
        android:topLeftRadius="24dp"
        android:topRightRadius="24dp" />
</shape>
```

- [ ] **Step 3: Create mic icon drawable**

Create `app/src/main/res/drawable/ic_mic.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="64dp"
    android:height="64dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="@color/text_primary">
    <path
        android:fillColor="@color/text_primary"
        android:pathData="M12,14c1.66,0 3,-1.34 3,-3V5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6C9,12.66 10.34,14 12,14z" />
    <path
        android:fillColor="@color/text_primary"
        android:pathData="M17,11c0,2.76 -2.24,5 -5,5s-5,-2.24 -5,-5H5c0,3.53 2.61,6.43 6,6.92V21h2v-3.08c3.39,-0.49 6,-3.39 6,-6.92H17z" />
</vector>
```

- [ ] **Step 4: Verify layout files**

```bash
ls voice/app/src/main/res/layout/activity_main.xml voice/app/src/main/res/drawable/panel_bg.xml voice/app/src/main/res/drawable/ic_mic.xml
```

---

### Task 8: MainActivity

**Files:**
- Create: `app/src/main/java/com/voice/search/MainActivity.kt`

- [ ] **Step 1: Implement MainActivity**

```kotlin
package com.voice.search

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.ScaleAnimation
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Random

class MainActivity : Activity() {

    private val TAG = "VoiceSearch"
    private var voiceRecognizer: VoiceRecognizer? = null
    private var finalText: String? = null
    private var isProcessing = false

    // UI references
    private lateinit var tvStatus: TextView
    private lateinit var tvPartialResult: TextView
    private lateinit var tvHint: TextView
    private lateinit var micContainer: View
    private lateinit var waveContainer: View
    private lateinit var waveBars: List<View>
    private val random = Random()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupTransparentArea()
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tv_status)
        tvPartialResult = findViewById(R.id.tv_partial_result)
        tvHint = findViewById(R.id.tv_hint)
        micContainer = findViewById(R.id.mic_container)
        waveContainer = findViewById(R.id.wave_container)

        waveBars = listOf(
            findViewById(R.id.wave_bar_1),
            findViewById(R.id.wave_bar_2),
            findViewById(R.id.wave_bar_3),
            findViewById(R.id.wave_bar_4),
            findViewById(R.id.wave_bar_5)
        )
    }

    private fun setupTransparentArea() {
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

        // Check permissions
        if (!hasAudioPermission()) {
            requestAudioPermission()
            return
        }

        if (!hasUsageStatsPermission()) {
            showUsageStatsHint()
            return
        }

        // Check / launch TVBox
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

    // --- Animations ---

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
                    val height = 12 + random.nextInt(30)
                    lp.height = height
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

    // --- Permissions ---

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
```

- [ ] **Step 2: Verify MainActivity matches spec**

Checklist:
- [x] Translucent theme (via AndroidManifest)
- [x] Foreground detection before recording
- [x] Launch TVBox if not in foreground, wait up to 10s
- [x] Auto start recording when ready
- [x] Silence detection → stop recording → extract keyword → broadcast → finish()
- [x] Permission handling (RECORD_AUDIO, PACKAGE_USAGE_STATS)
- [x] Error states (no TVBox, no model, no speech, no keyword)
- [x] Cleanup on destroy
- [x] Back button exits

---

### Task 9: Verification & Final Check

**Files to verify exist:**
```
app/src/main/AndroidManifest.xml
app/src/main/java/com/voice/search/MainActivity.kt
app/src/main/java/com/voice/search/VoiceRecognizer.kt
app/src/main/java/com/voice/search/KeywordExtractor.kt
app/src/main/java/com/voice/search/TvBoxHelper.kt
app/src/main/java/com/voice/search/ForegroundDetector.kt
app/src/main/res/layout/activity_main.xml
app/src/main/res/values/themes.xml
app/src/main/res/values/strings.xml
app/src/main/res/values/colors.xml
app/src/main/res/drawable/panel_bg.xml
app/src/main/res/drawable/ic_mic.xml
```

- [ ] **Step 1: Verify all files exist**

```bash
find voice/app/src -type f | sort
```

- [ ] **Step 2: Check keyword extraction edge cases**

Open `KeywordExtractor.kt` and verify:
- Prefix removal handles all predefined patterns
- Suffix removal handles all predefined patterns
- Returns null for empty/blank input
- Returns null for input that becomes empty after stripping

- [ ] **Step 3: Check manifest permissions**

```bash
grep -E "RECORD_AUDIO|PACKAGE_USAGE_STATS" voice/app/src/main/AndroidManifest.xml
```

Expected: Both permissions declared.

- [ ] **Step 4: Final review of MainActivity flow**

Trace through `startFlow()`:
1. Check audio permission → request if needed
2. Check usage stats permission → open settings if needed
3. Check TVBox installed → exit if not
4. Check foreground → if yes, start recording; if no, launch TVBox + wait
5. Recording flow: start → partial results → silence → final result → extract → broadcast → finish()

Edge cases handled:
- Permission denied: show message, finish()
- TVBox not installed: show message, finish()
- TVBox launch timeout: show message, finish()
- No speech detected: show message, finish()
- No keyword extracted: show message, finish()
- Model load error: show message, finish()
- Recognition error: show message, finish()

- [ ] **Step 5: Manual build verification (if Android SDK available)**

```bash
cd voice && ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL (assuming Vosk model in assets).