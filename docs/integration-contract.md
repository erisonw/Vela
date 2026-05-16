# 三端集成契约

本文档定义 Vela Android MVP 中 App 前端、后端 AI 服务和 Android 小组件三条开发线的协作边界。三个人可以并行开发，但必须共同遵守这里的数据结构、接口和状态归属。

工程版本、Gradle、SDK、目录边界、真机运行和合并规则见 [Android 工程契约](android-engineering-contract.md)。如果接口契约和工程契约发生冲突，以工程契约中的构建和运行要求为准，以本文档中的业务数据结构为准。

## 分工边界

### App 前端

负责 Android App 内完整交互闭环：

- 聊天式导入页
- 候选日程列表
- 候选日程勾选、编辑、删除、确认导入
- 小日历标红逻辑
- 应用内日历数据
- 生成小组件需要的 `WidgetSnapshot`

App 前端是候选日程审核状态的唯一管理方。

### 后端 AI 服务

负责把用户输入解析成候选日程：

- 接收文字、图片、文件或语音转写文本
- 调用 AI/OCR/文档解析能力
- 返回结构化 `EventCandidate[]`
- 返回解析摘要、置信度、缺失字段和来源依据

后端只生成候选项，不决定最终导入哪些日程。

### Android 小组件

负责桌面 4x2 小组件：

- 展示今日总览
- 展示下一个日程
- 展示今日剩余日程数量
- 展示轻量天气或准备提示
- 提供 AI 导入入口
- 点击后跳转 App 指定页面

小组件只读取 `WidgetSnapshot`，不直接调用 AI 服务，也不直接修改候选日程。

## 数据流

```text
用户输入
  -> App 前端收集文本/图片/文件
  -> 后端 AI 服务返回 EventCandidate[]
  -> App 前端展示候选日程
  -> 用户勾选/编辑/删除
  -> App 前端生成正式 Event[]
  -> App 前端生成 WidgetSnapshot
  -> Android 小组件读取 WidgetSnapshot 并刷新
```

第一版可以用 mock 后端替代真实 AI 服务，但 mock 返回结构必须和本文档一致。

## 通用约定

- 时间统一使用 ISO 8601 字符串，并带时区偏移，例如 `2026-05-18T10:00:00+08:00`。
- 默认时区为 `Asia/Shanghai`。
- ID 统一使用字符串。
- 可空字段用 `null`，列表字段默认返回空数组。
- 后端返回字段使用 `camelCase`。
- App 内部模型命名尽量和接口字段保持一致。
- AI 返回的内容永远是候选项，不能直接写入正式日程。

## 后端接口

### 解析候选日程

```http
POST /v1/event-candidates:extract
Content-Type: application/json
```

请求：

```json
{
  "sessionId": "session_001",
  "input": {
    "type": "text",
    "text": "下周一上午 10 点产品会，下午 3 点和设计评审",
    "attachmentIds": []
  },
  "timezone": "Asia/Shanghai",
  "locale": "zh-CN"
}
```

响应：

```json
{
  "sessionId": "session_001",
  "summary": "我识别到 2 个可能的日程，请确认是否导入。",
  "candidates": [
    {
      "clientTempId": "candidate_tmp_001",
      "title": "产品会",
      "startTime": "2026-05-18T10:00:00+08:00",
      "endTime": "2026-05-18T11:00:00+08:00",
      "timezone": "Asia/Shanghai",
      "isAllDay": false,
      "location": {
        "name": "",
        "address": "",
        "latitude": null,
        "longitude": null
      },
      "participants": [],
      "notes": "",
      "reminders": [
        {
          "type": "notification",
          "offsetMinutes": 30
        }
      ],
      "recurrence": null,
      "confidence": 0.88,
      "missingFields": ["location"],
      "sourceEvidence": "用户文本：下周一上午 10 点产品会",
      "defaultSelected": true
    }
  ]
}
```

字段说明：

- `clientTempId`: 后端返回的临时候选 ID，App 可以替换成本地正式 ID。
- `defaultSelected`: 后端建议是否默认勾选，最终以用户操作为准。
- `missingFields`: 缺失字段，允许值包括 `title`、`startTime`、`endTime`、`location`、`participants`。
- `confidence`: 0 到 1 之间的小数。

### 错误响应

```json
{
  "error": {
    "code": "EXTRACTION_FAILED",
    "message": "无法从当前内容中识别出明确日程。",
    "retryable": true
  }
}
```

第一版错误码：

- `INVALID_INPUT`: 输入为空或格式不支持。
- `EXTRACTION_FAILED`: AI 没能识别出日程。
- `UNSUPPORTED_ATTACHMENT`: 附件类型暂不支持。
- `SERVICE_UNAVAILABLE`: AI 服务暂不可用。

## App 内部模型

### EventCandidate

App 收到后端候选项后，补充本地审核字段：

```json
{
  "id": "candidate_001",
  "sessionId": "session_001",
  "sourceMessageId": "msg_001",
  "title": "产品会",
  "startTime": "2026-05-18T10:00:00+08:00",
  "endTime": "2026-05-18T11:00:00+08:00",
  "timezone": "Asia/Shanghai",
  "isAllDay": false,
  "location": {
    "name": "",
    "address": "",
    "latitude": null,
    "longitude": null
  },
  "participants": [],
  "notes": "",
  "reminders": [
    {
      "type": "notification",
      "offsetMinutes": 30
    }
  ],
  "recurrence": null,
  "confidence": 0.88,
  "missingFields": ["location"],
  "sourceEvidence": "用户文本：下周一上午 10 点产品会",
  "isSelectedForImport": true,
  "reviewStatus": "pending"
}
```

App 独占字段：

- `isSelectedForImport`
- `reviewStatus`
- `sourceMessageId`

小日历只能从 `isSelectedForImport = true` 的候选项计算标红日期。

### Event

正式日程由 App 在用户确认导入后生成：

```json
{
  "id": "event_001",
  "sourceCandidateId": "candidate_001",
  "title": "产品会",
  "startTime": "2026-05-18T10:00:00+08:00",
  "endTime": "2026-05-18T11:00:00+08:00",
  "timezone": "Asia/Shanghai",
  "isAllDay": false,
  "location": {
    "name": "",
    "address": "",
    "latitude": null,
    "longitude": null
  },
  "participants": [],
  "notes": "",
  "reminders": [
    {
      "type": "notification",
      "offsetMinutes": 30
    }
  ]
}
```

## 小组件数据

### WidgetSnapshot

App 前端负责把本地日程整理成小组件快照。

```json
{
  "date": "2026-05-18",
  "timezone": "Asia/Shanghai",
  "nextEvent": {
    "id": "event_001",
    "title": "产品会",
    "startTime": "2026-05-18T10:00:00+08:00",
    "endTime": "2026-05-18T11:00:00+08:00",
    "locationName": "公司 12 楼会议室"
  },
  "remainingEventCount": 3,
  "weatherHint": "下午可能有雨，出门记得带伞。",
  "prepHint": "10 点前准备产品方案草稿。",
  "pendingImportCount": 2,
  "updatedAt": "2026-05-18T08:30:00+08:00"
}
```

小组件展示规则：

- 有 `nextEvent` 时，优先显示下一个日程标题和时间。
- 没有 `nextEvent` 时，显示“今天暂无后续日程”。
- `remainingEventCount` 只统计当前时间之后仍未结束的今日日程。
- `weatherHint` 和 `prepHint` 第一版可以使用 mock 文案。
- `pendingImportCount` 大于 0 时，小组件可提示“有待确认日程”。

## Deep Link

小组件点击行为统一走 deep link。

```text
vela://home
vela://import/chat
vela://event/{eventId}
```

约定：

- 点击今日总览区域打开 `vela://home`。
- 点击 AI 导入按钮打开 `vela://import/chat`。
- 点击下一个日程可以打开 `vela://event/{eventId}`，第一版也可以先跳首页。

## 集成顺序

1. App 前端先用 mock `EventCandidate[]` 完成聊天式导入。
2. 小组件先用 mock `WidgetSnapshot` 完成 4x2 展示和跳转。
3. 后端按接口返回固定 JSON mock。
4. App 将 mock AI 替换为后端接口。
5. App 导入正式 `Event` 后生成真实 `WidgetSnapshot`。
6. 小组件改为读取真实 `WidgetSnapshot`。

## 联调验收

三端联调必须满足：

- 后端返回的候选日程能被 App 无转换歧义地展示。
- App 勾选状态变化能正确影响小日历标红。
- App 只导入已勾选候选日程。
- App 导入后能生成 `WidgetSnapshot`。
- 小组件能展示最新 `WidgetSnapshot`。
- 小组件 AI 导入按钮能打开聊天式导入页。

## 不允许各自发挥的部分

- 后端不要返回自然语言列表代替结构化 `candidates`。
- 后端不要决定最终导入状态。
- 小组件不要直接调用 AI 接口。
- 小组件不要自己计算正式日程，只读取 `WidgetSnapshot`。
- App 不要把未勾选候选项写入正式日程。
- 小日历不要根据 AI 识别到的全部日期标红。
