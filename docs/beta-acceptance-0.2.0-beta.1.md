# Vela 0.2.0-beta.1 验收记录

验收日期：2026-07-16

测试分支：`codex/beta-stabilization`

测试范围：内部技术测试版自动化、构建与交付物检查

## 已通过

- `git diff --check`
- Android 单元测试：25 项，0 失败，0 跳过
- AI 本机代理 Node.js 测试：4 项，0 失败
- Debug Lint：0 错误，31 条非阻断警告
- Debug APK 构建
- AndroidTest APK 编译与打包
- R8 Release APK 构建
- APK Manifest 版本检查

Lint 剩余警告集中在依赖版本提示、启动图标、KTX 建议和高版本小组件属性，不阻断内部测试版。

## 交付物

```text
dist/Vela-0.2.0-beta.1-debug.apk
```

- 包名：`com.vela.app`
- `versionCode`：`2`
- `versionName`：`0.2.0-beta.1`
- `minSdk`：26
- `targetSdk`：36
- SHA-256：`25e2a95a06966e483a060d1688108698bc7680a6c6676cca3d99af37666fc99e`

## 真机验收状态

执行验收时 `adb devices -l` 没有返回已连接设备，本机也没有可用 AVD，因此以下项目尚未执行：

- 安装 Debug APK；
- 全新安装空状态检查；
- 文字、图片和语音真实 AI 联调；
- 通知、精确闹钟和开机恢复；
- 定位天气；
- 悬浮窗和系统截屏授权；
- 4x2 小组件刷新；
- `connectedDebugAndroidTest`。

真机验收步骤见 [内部测试说明](internal-beta-testing.md)。`v0.2.0-beta.1` 标签必须在上述真机流程通过后创建。
