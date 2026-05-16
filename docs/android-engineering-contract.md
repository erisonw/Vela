# Android 工程契约

本文档根据当前工程机和已连接真机环境，定义 Vela Android MVP 的工程约束。三位开发者必须遵守这里的版本、模块、依赖和合并规则，保证代码合并后可以在手机上成功构建、安装和运行。

## 当前工程机环境

已确认环境：

- 真机：Samsung SM-S9180
- Android 版本：Android 16
- 设备 API：36
- CPU ABI：arm64-v8a
- ADB：已连接，设备状态为 `device`
- Android SDK：`/Users/erison/Library/Android/sdk`
- 已安装平台：`android-34`、`android-36.1`
- 已安装 Build Tools：`34.0.0`、`36.0.0`、`36.1.0`、`37.0.0`
- 系统 `gradle`：未安装
- `sdkmanager` / `avdmanager`：未在 PATH 中发现
- Java：当前系统 Java 为 OpenJDK 26
- Android Studio JBR：OpenJDK 21，路径为 `/Applications/Android Studio.app/Contents/jbr/Contents/Home`

工程结论：

- 项目必须提交 Gradle Wrapper，所有人使用 `./gradlew` 构建。
- 不依赖系统级 `gradle` 命令。
- 不要求开发者通过命令行 `sdkmanager` 安装依赖。
- 首次建工程时统一使用 Android Studio JBR 21 作为 Gradle JDK，避免直接依赖系统 Java 26。

## 固定工程参数

Android 项目第一版固定为：

```kotlin
namespace = "com.vela.app"
applicationId = "com.vela.app"
minSdk = 26
targetSdk = 36

compileSdk {
    version = release(36) {
        minorApiLevel = 1
    }
}
```

说明：

- 当前工程机安装的是 `android-36.1`，不是 `android-36`。
- 如果直接写 `compileSdk = 36`，可能因为缺少 `platforms/android-36` 导致构建失败。
- 使用 Android 16 QPR2 / API 36.1 时，Android Gradle Plugin 最低需要 8.13.0。
- 本项目锁定使用 Android Gradle Plugin 9.2.0 和 Gradle 9.4.1，避免三人各自选择版本。
- 首个工程骨架应由 Android Studio 2025.3 创建或升级，确保 AGP 支持 36.1 minor API DSL。

技术栈固定为：

- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- ViewModel
- StateFlow
- Kotlin Serialization
- Jetpack Glance

Gradle/JDK 约束：

- Gradle Wrapper 必须提交到仓库。
- Gradle Wrapper 固定使用 Gradle 9.4.1。
- Gradle JDK 统一使用 Android Studio JBR 21。
- Android Gradle Plugin 固定使用 9.2.0。
- AGP 9.2.0 的最低 JDK 要求是 17；当前工程机使用 Android Studio JBR 21。
- 首次构建需要联网下载 Gradle Wrapper 和 Gradle 依赖。
- 不允许三位开发者各自升级 Gradle、AGP、Kotlin、Compose 或 Glance 版本。
- 如果命令行构建遇到 Java 26 兼容问题，先执行：

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
```

第一版不引入：

- 多模块复杂拆分
- Hilt
- Room
- Retrofit
- 多 flavor
- Firebase
- 真实系统日历写入

这些能力等本地闭环跑通后再加。

## 推荐目录结构

第一版采用单 Android app module，避免三人合并时被 Gradle 多模块配置拖住。

```text
app/src/main/java/com/vela/app
├── MainActivity.kt
├── data
│   ├── model
│   ├── mock
│   └── repository
├── feature
│   ├── home
│   ├── importchat
│   └── calendar
├── navigation
├── ui
│   ├── components
│   └── theme
└── widget
```

目录归属：

- App 前端开发负责 `feature`、`ui`、`navigation`。
- 小组件开发负责 `widget`。
- 后端开发不直接改 Android UI，可提供 mock JSON、接口文档和本地 mock service。
- 共享数据模型放在 `data/model`，任何人修改前必须同步另外两人。
- 共享仓库逻辑放在 `data/repository`，负责 App 和小组件之间的数据来源。

## 三人代码边界

### App 前端开发

可改：

- `feature/home`
- `feature/importchat`
- `feature/calendar`
- `ui`
- `navigation`

谨慎改：

- `data/model`
- `data/repository`

不应改：

- `widget` 的布局和 Glance 逻辑，除非和小组件开发约好。

### 小组件开发

可改：

- `widget`
- 小组件相关 manifest receiver 配置
- 小组件预览资源和 metadata XML

谨慎改：

- `data/model/WidgetSnapshot`
- `data/repository/WidgetSnapshotRepository`

不应改：

- 聊天导入页业务逻辑
- 候选日程审核状态流

### 后端 AI 开发

可提交：

- `docs` 下接口说明更新
- mock response JSON
- App 内 mock API adapter
- 后续真实网络 service 的接口层

不应改：

- App 页面布局
- 小组件布局
- 用户勾选和导入状态逻辑

## 共享模型规则

以下模型必须保持稳定：

- `EventCandidate`
- `Event`
- `ImportSession`
- `ChatMessage`
- `WidgetSnapshot`

规则：

- 字段名使用 `camelCase`。
- 时间字段统一 ISO 8601 字符串，带时区偏移。
- 可空字段显式 nullable。
- 列表字段默认空列表。
- `EventCandidate.isSelectedForImport` 只归 App 前端管理。
- 小组件只能读取 `WidgetSnapshot`，不能直接读取或修改 `EventCandidate`。

## Deep Link 规则

Android manifest 必须支持：

```text
vela://home
vela://import/chat
vela://event/{eventId}
```

小组件点击规则：

- 今日总览区域打开 `vela://home`。
- AI 导入按钮打开 `vela://import/chat`。
- 下一个日程第一版可以打开 `vela://home`，后续再打开 `vela://event/{eventId}`。

## 小组件工程规则

小组件第一版固定：

- 类型：4x2 Glance App Widget
- 功能：今日总览 + AI 导入入口
- 数据来源：`WidgetSnapshot`
- 刷新时机：App 导入日程后主动刷新；App 启动时刷新一次

小组件不做：

- 桌面内聊天
- 直接调用后端 AI
- 直接编辑日程
- 多尺寸适配
- 实时天气请求

## 构建与真机运行命令

所有人统一使用：

```bash
./gradlew clean
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.vela.app/.MainActivity
```

如果设备上已有旧包但签名不一致：

```bash
adb uninstall com.vela.app
adb install app/build/outputs/apk/debug/app-debug.apk
```

检查设备：

```bash
adb devices -l
```

查看崩溃日志：

```bash
adb logcat | grep com.vela.app
```

## 合并前必须通过

每个人提交前至少执行：

```bash
./gradlew assembleDebug
```

负责 App 前端或共享逻辑的人还需要执行：

```bash
./gradlew testDebugUnitTest
```

最终集成负责人执行：

```bash
./gradlew clean assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

并在真机上验收：

- App 能启动。
- 首页能打开。
- 聊天导入页能打开。
- 输入文本能生成候选日程。
- 勾选候选日程能驱动小日历标红。
- 确认导入后首页能看到日程。
- 4x2 小组件能添加到桌面。
- 小组件能显示今日总览。
- 小组件 AI 导入按钮能打开聊天导入页。

## 分支和合并规则

建议分支：

- `feature/app-shell`
- `feature/import-chat`
- `feature/widget`
- `feature/backend-mock`

合并顺序：

1. 先合并 `feature/app-shell`，建立 Gradle、包名、主题、导航和共享模型。
2. 合并 `feature/backend-mock`，提供稳定 mock 数据源。
3. 合并 `feature/import-chat`，完成 App 内候选日程流。
4. 合并 `feature/widget`，接入 `WidgetSnapshot` 和 deep link。
5. 最后做真机集成修复。

共享文件修改规则：

- `build.gradle.kts`、`settings.gradle.kts`、`AndroidManifest.xml`、`data/model`、`data/repository` 不能多人同时大改。
- 修改共享模型必须同步更新 `docs/integration-contract.md`。
- 修改 deep link 必须同步更新 App 导航和小组件点击逻辑。

## 第一版验收标准

合并后必须在已连接真机 Samsung SM-S9180 上成功：

- 构建 debug APK。
- 安装 APK。
- 启动 App。
- 完成一次 mock 聊天导入。
- 导入至少一条已勾选日程。
- 小组件展示导入后的下一个日程。
- 小组件 AI 导入按钮跳回聊天导入页。

只要以上流程完整跑通，第一版工程集成视为通过。
