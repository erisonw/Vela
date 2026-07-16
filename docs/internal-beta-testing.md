# Vela 内部测试说明

本文档适用于 `0.2.0-beta.1` 内部技术测试版。测试版不会在 APK 中内置 AI API Key，AI 能力通过用户自配服务或开发机本地代理完成。

## 环境要求

- Android Studio JBR 21
- Android SDK `/Users/erison/Library/Android/sdk`
- Node.js 20 或更高版本
- Android 真机或模拟器
- 一个兼容 OpenAI Chat Completions 和 Audio Transcriptions 的上游服务

## 启动本机 AI 代理

在项目根目录设置上游服务。密钥只存在当前终端环境中，不会写入仓库或 APK：

```bash
export VELA_AI_BASE_URL="https://api.example.com/v1"
export VELA_AI_API_KEY="your-api-key"
export VELA_AI_TEXT_MODEL="your-text-model"
export VELA_AI_VISION_MODEL="your-vision-model"
export VELA_AI_VOICE_MODEL="your-voice-model"
node tools/ai-proxy.mjs
```

检查代理健康状态：

```bash
curl http://127.0.0.1:8787/health
```

响应中的 `configured` 应为 `true`。代理日志会隐藏 Bearer Token 和 Base64 图片内容。

## 让 Android 设备访问开发机代理

USB 连接设备并确认 `adb devices` 显示为 `device`，然后执行：

```bash
adb reverse tcp:8787 tcp:8787
```

在 Vela 设置页点击“使用本机代理测试配置”，再点击“检查 AI 服务连接”。预期显示：

- 本机代理连接正常；
- 文本、图片和语音模型均已配置；
- 实际导入失败时不会生成伪造候选日程。

本机 HTTP 代理只用于 Debug 构建。Release 构建应使用 HTTPS 服务地址。

## 核心冒烟流程

1. 全新安装后确认日历为空，没有固定演示日程。
2. 本地新建一条日程，重启 App 后确认仍然存在。
3. 使用文字生成候选日程，编辑后确认导入。
4. 使用图片生成多条候选，只导入勾选项。
5. 录制语音并转写，再将转写文本提交解析。
6. 修改日程时间和提醒，确认旧提醒不会继续触发。
7. 授权定位并刷新天气；断网时日历和小组件仍可正常使用。
8. 添加 4x2 小组件，确认日程和待确认数量刷新。
9. 重启设备，确认未来日程提醒重新调度。

## 构建验证

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:assembleDebugAndroidTest
./gradlew :app:assembleRelease
```

有真机连接时运行：

```bash
./gradlew :app:connectedDebugAndroidTest
```

内部测试 APK 使用 Debug 签名，最终复制到：

```text
dist/Vela-0.2.0-beta.1-debug.apk
```
