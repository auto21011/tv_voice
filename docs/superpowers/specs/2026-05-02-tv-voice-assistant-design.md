# TV语音助手设计文档

## 概述

一个最小化的Android TV应用，启动后自动录音，通过Vosk离线语音识别将语音转为文本，提取搜索关键词，通过广播发送到TVBox执行影视搜索。

## 技术栈

- **平台**: Android TV (API 21+)
- **语言**: Kotlin
- **语音识别**: Vosk 离线 SDK
- **模型**: vosk-model-small-cn-0.22 (~42MB)
- **构建**: Gradle + Android SDK

## 交互流程

1. 用户启动应用
2. 自动开始录音，UI显示麦克风动画 + 波形 + "请说出您想搜索的内容"
3. Vosk实时识别，UI显示部分识别结果
4. 检测静音2秒后自动停止录音
5. 取最终识别文本，提取关键词
6. 构造Intent并sendBroadcast到TVBox SearchReceiver
7. finish()退出Activity，用户看到TVBox搜索结果

## 项目结构

```
app/
├── build.gradle.kts
├── src/main/
│   ├── java/com/voice/search/
│   │   ├── MainActivity.kt          # 唯一Activity
│   │   ├── VoiceRecognizer.kt       # Vosk封装
│   │   ├── KeywordExtractor.kt      # 关键词提取
│   │   └── TvBoxHelper.kt           # 广播发送
│   ├── res/
│   │   ├── layout/activity_main.xml
│   │   └── values/
│   │       ├── strings.xml
│   │       └── themes.xml
│   └── assets/
│       └── vosk-model-small-cn/     # 离线模型
```

## 核心组件

### MainActivity
- 全屏TV Activity，禁止状态栏/导航栏
- onCreate中自动开始录音
- 显示录音状态UI：等待中 → 录音中(波形) → 识别中 → 完成
- 处理生命周期（onPause释放资源）

### VoiceRecognizer
- 封装Vosk SpeechService
- 提供回调：`onPartialResult(text)` / `onFinalResult(text)` / `onError(e)`
- 静音检测：连续2秒无语音输入触发停止
- 权限处理：`RECORD_AUDIO`

### KeywordExtractor
- 输入："搜索狂飙电视剧" → 输出："狂飙"
- 输入："看西游记" → 输出："西游记"
- 输入："播放甄嬛传" → 输出："甄嬛传"
- 输入："我想看琅琊榜这部" → 输出："琅琊榜"
- 实现：正则去除常见前缀（搜索|看|播放|我想看|我要看|给我搜）和后缀（电视剧|电影|综艺|动漫|这部）

### TvBoxHelper
- 构造Intent：
  ```kotlin
  Intent().apply {
      component = ComponentName(
          "com.mygithub0.tvbox0.osdkitkat",
          "com.github.tvbox.osc.receiver.SearchReceiver"
      )
      putExtra("title", keyword)
  }
  ```
- `sendBroadcast(intent)`（无需特殊权限）

## 关键依赖

```kotlin
// Vosk
implementation("com.alphacephei:vosk-android:0.3.47")

// AndroidX
implementation("androidx.core:core-ktx:1.12.0")
implementation("androidx.appcompat:appcompat:1.6.1")
```

## AndroidManifest配置

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<application>
    <activity
        android:name=".MainActivity"
        android:theme="@style/Theme.VoiceSearch"
        android:launchMode="singleTask">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
            <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
        </intent-filter>
    </activity>
</application>
```

## UI设计

- 深色背景（#1a1a2e）
- 居中布局，大字体（适配TV 1920x1080）
- 麦克风图标 + 脉冲动画（录音中）
- 波形条动画（模拟实时识别）
- 识别文本显示区域
- 底部提示文字

## 错误处理

| 场景 | 处理方式 |
|------|---------|
| 无录音权限 | 提示用户授权，finish() |
| Vosk模型加载失败 | 显示"模型加载失败"，finish() |
| 识别结果为空 | 显示"未识别到语音"，3秒后finish() |
| 无有效关键词 | 显示"未识别到搜索内容"，3秒后finish() |
| Broadcast发送失败 | 静默处理（TVBox可能未安装） |