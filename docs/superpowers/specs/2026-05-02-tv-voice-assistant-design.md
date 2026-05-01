# TV语音助手设计文档

## 概述

一个最小化的Android TV应用，通过Vosk离线语音识别将语音转为文本，提取搜索关键词，通过广播发送到TVBox执行影视搜索。界面为底部半透明悬浮窗形式，始终可见TVBox界面。

## 技术栈

- **平台**: Android TV (API 21+)
- **语言**: Kotlin
- **语音识别**: Vosk 离线 SDK
- **模型**: vosk-model-small-cn-0.22 (~42MB)
- **构建**: Gradle + Android SDK

## 交互流程

1. 用户启动应用（或通过其他方式触发语音助手）
2. 检测当前前台应用是否为 `com.mygithub0.tvbox0.osdkitkat`
   - **是**：直接透明叠加录音悬浮窗，录音开始
   - **否**：启动 `com.mygithub0.tvbox0.osdkitkat`，等待其前台运行后再显示录音悬浮窗
3. 底部悬浮窗显示：麦克风图标 + 波形 + 识别文本
4. Vosk实时识别，静音2秒后自动停止
5. 取最终识别文本，提取关键词
6. 构造Intent并sendBroadcast到TVBox SearchReceiver
7. finish()退出悬浮窗，TVBox搜索结果页自然显示

## 项目结构

```
app/
├── build.gradle.kts
├── src/main/
│   ├── java/com/voice/search/
│   │   ├── MainActivity.kt          # 半透明Activity，底部悬浮窗
│   │   ├── VoiceRecognizer.kt       # Vosk封装 + 静音检测
│   │   ├── KeywordExtractor.kt      # 关键词提取
│   │   ├── TvBoxHelper.kt           # 广播发送 + 前台检测 + 应用启动
│   │   └── ForegroundDetector.kt    # 前台应用检测
│   ├── res/
│   │   ├── layout/activity_main.xml # 底部悬浮窗布局
│   │   └── values/
│   │       ├── strings.xml
│   │       └── themes.xml           # 半透明TV主题
│   └── assets/
│       └── vosk-model-small-cn/     # 离线模型
```

## 核心组件

### MainActivity
- 半透明Activity：上半透明（能看到TVBox），底部不透明暗色悬浮窗
- 禁止触摸穿透：上半透明区域点击关闭Activity，下半部分正常交互
- 布局：底部200dp高的暗色面板，包含麦克风、波形、文本
- 启动流程：
  1. onCreate → 检查前台应用
  2. 若TVBox已在前台 → 直接initVosk + 开始录音
  3. 若TVBox不在前台 → TvBoxHelper.launchTvBoxAndWait() → 轮询前台直到TVBox出现 → 开始录音
- 生命周期：onPause释放录音资源，onResume恢复（如有需要）

### ForegroundDetector
- 使用 `UsageStatsManager` 检测当前前台应用
- 权限：`PACKAGE_USAGE_STATS`（需引导用户到设置页授权）
- 备用方案：`ActivityManager.getRunningAppProcesses()`（API 21+，部分可用）
- 轮询间隔：500ms

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
- 实现：正则去除常见前缀（搜索|看|播放|我想看|我要看|给我搜|搜）和后缀（电视剧|电影|综艺|动漫|这部|这个）

### TvBoxHelper
- **前台检测**：`isTvBoxInForeground(): Boolean`
- **启动TVBox**：`launchTvBox(context)` — 通过包名启动
- **等待就绪**：`waitForTvBoxForeground()` — 轮询最多10秒
- **发送广播**：
  ```kotlin
  Intent().apply {
      component = ComponentName(
          "com.mygithub0.tvbox0.osdkitkat",
          "com.github.tvbox.osc.receiver.SearchReceiver"
      )
      putExtra("title", keyword)
  }
  ```
  `sendBroadcast(intent)`（无需特殊权限）

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
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" />

<application>
    <activity
        android:name=".MainActivity"
        android:theme="@style/Theme.VoiceSearch.Translucent"
        android:launchMode="singleTask"
        android:taskAffinity=""
        android:excludeFromRecents="true">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
            <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
        </intent-filter>
    </activity>
</application>
```

## UI设计（底部悬浮窗）

```
┌──────────────────────────────────┐
│                                  │
│       TVBox 界面（可见）          │  ← 上半透明，touch穿透到TVBox
│                                  │
│                                  │
│                                  │
├──────────────────────────────────┤
│  🎤  [波形动画]                   │  ← 底部不透明暗色面板 (~200dp)
│  "正在聆听..."                    │     深色背景 #CC1a1a2e
│      停止说话后自动识别            │
└──────────────────────────────────┘
```

- 半透明背景：`@android:color/transparent` + `windowIsTranslucent=true`
- 底部面板：深色半透明背景 `#DD1a1a2e`，圆角顶部
- 麦克风图标：白色，脉冲动画
- 波形条：5根竖条随机高度动画
- 识别文本：大号白色字体，行高充足
- 提示文字：小号灰色

## 错误处理

| 场景 | 处理方式 |
|------|---------|
| 无录音权限 | 提示"请授予录音权限"，finish() |
| 无使用统计权限 | 提示"请授予使用情况访问权限"，打开设置页 |
| Vosk模型加载失败 | 显示"模型加载失败"，3秒后finish() |
| TVBox启动超时(10s) | 显示"无法启动TVBox"，3秒后finish() |
| 识别结果为空 | 显示"未识别到语音"，3秒后finish() |
| 无有效关键词 | 显示"未识别到搜索内容"，3秒后finish() |
| Broadcast发送失败 | 静默处理（TVBox可能未安装） |