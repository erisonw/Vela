# 聊天式日程导入契约

本文档定义 Vela 首个 MVP 的核心数据契约：用户在聊天界面发送文字、图片、语音或文档后，AI 如何返回候选日程，用户如何逐条勾选、编辑、批准，以及小日历如何根据勾选状态标红。

## 目标

第一版先完成一个最小闭环：

1. 用户在聊天界面发送一段包含日程的信息。
2. AI 解析并返回多个候选日程。
3. 用户逐条勾选、编辑或删除候选日程。
4. 下方小日历只标红已勾选候选日程的日期。
5. 用户点击确认导入。
6. 系统只把已勾选候选日程写入应用内日历。

## 核心原则

- AI 识别结果只是候选项，不等于真实日程。
- 用户勾选状态决定是否导入。
- 小日历只表达“准备导入的日期”，不表达“AI 曾识别到的全部日期”。
- 所有导入前修改都发生在 `EventCandidate` 上。
- 确认导入后才生成正式 `Event`。

## 数据实体

### ImportSession

一次聊天式导入会话。

```json
{
  "id": "session_001",
  "status": "draft",
  "createdAt": "2026-05-16T19:50:00+08:00",
  "updatedAt": "2026-05-16T19:52:00+08:00",
  "sourceType": "mixed",
  "messageIds": ["msg_001", "msg_002"],
  "candidateIds": ["candidate_001", "candidate_002"],
  "importedEventIds": []
}
```

字段说明：

- `status`: `draft`、`reviewing`、`imported`、`cancelled`。
- `sourceType`: `text`、`image`、`audio`、`file`、`mixed`。
- `messageIds`: 当前会话中的消息。
- `candidateIds`: AI 解析出的候选日程。
- `importedEventIds`: 最终导入后生成的正式日程。

### ChatMessage

聊天消息，包括用户输入和 AI 回复。

```json
{
  "id": "msg_001",
  "sessionId": "session_001",
  "role": "user",
  "type": "image",
  "text": "这是下周活动安排，帮我导入日程",
  "attachmentIds": ["attachment_001"],
  "createdAt": "2026-05-16T19:50:00+08:00"
}
```

字段说明：

- `role`: `user`、`assistant`、`system`。
- `type`: `text`、`image`、`audio`、`file`、`mixed`。
- `text`: 文本内容或语音转写结果。
- `attachmentIds`: 图片、文档、音频等附件。

### EventCandidate

AI 解析出的候选日程，也是用户在导入前编辑的对象。

```json
{
  "id": "candidate_001",
  "sessionId": "session_001",
  "sourceMessageId": "msg_001",
  "title": "产品讨论会",
  "startTime": "2026-05-18T10:00:00+08:00",
  "endTime": "2026-05-18T11:00:00+08:00",
  "timezone": "Asia/Shanghai",
  "isAllDay": false,
  "location": {
    "name": "公司 12 楼会议室",
    "address": "",
    "latitude": null,
    "longitude": null
  },
  "participants": ["张三", "李四"],
  "notes": "讨论产品原型和开发排期",
  "reminders": [
    {
      "type": "notification",
      "offsetMinutes": 30
    }
  ],
  "recurrence": null,
  "confidence": 0.92,
  "missingFields": [],
  "sourceEvidence": "原图第 2 行：周一 10:00 产品讨论会",
  "isSelectedForImport": true,
  "reviewStatus": "pending",
  "createdAt": "2026-05-16T19:51:00+08:00",
  "updatedAt": "2026-05-16T19:51:00+08:00"
}
```

字段说明：

- `isSelectedForImport`: 是否被用户勾选，决定是否导入，也决定小日历是否标红。
- `reviewStatus`: `pending`、`edited`、`approved`、`rejected`、`imported`。
- `confidence`: AI 对解析结果的置信度。
- `missingFields`: 缺失字段，如 `startTime`、`location`、`title`。
- `sourceEvidence`: AI 解析依据，用于提高用户信任。

### Event

正式日程。只有用户确认导入后才生成。

```json
{
  "id": "event_001",
  "sourceCandidateId": "candidate_001",
  "title": "产品讨论会",
  "startTime": "2026-05-18T10:00:00+08:00",
  "endTime": "2026-05-18T11:00:00+08:00",
  "timezone": "Asia/Shanghai",
  "location": {
    "name": "公司 12 楼会议室",
    "address": "",
    "latitude": null,
    "longitude": null
  },
  "participants": ["张三", "李四"],
  "notes": "讨论产品原型和开发排期",
  "reminders": [
    {
      "type": "notification",
      "offsetMinutes": 30
    }
  ],
  "createdAt": "2026-05-16T19:55:00+08:00",
  "updatedAt": "2026-05-16T19:55:00+08:00"
}
```

## 小日历标红规则

小日历的标红日期从当前 `ImportSession` 中所有 `isSelectedForImport = true` 的 `EventCandidate` 计算。

伪代码：

```ts
function getHighlightedDates(candidates: EventCandidate[]): string[] {
  const selectedDates = candidates
    .filter((candidate) => candidate.isSelectedForImport)
    .filter((candidate) => candidate.startTime)
    .map((candidate) => candidate.startTime.slice(0, 10));

  return Array.from(new Set(selectedDates)).sort();
}
```

交互规则：

- 用户勾选候选日程时，对应日期立即标红。
- 用户取消勾选候选日程时，如果该日期没有其他已勾选日程，则取消标红。
- 同一天多条已勾选日程时，该日期只显示一个标红状态。
- 可选增强：日期下方显示已勾选日程数量。
- 点击标红日期时，候选列表滚动到该日期下的第一条已勾选日程。

## 用户操作

### 勾选候选日程

```json
{
  "candidateId": "candidate_001",
  "isSelectedForImport": true
}
```

结果：

- 更新候选日程的 `isSelectedForImport`。
- 重新计算小日历标红日期。
- 不创建正式日程。

### 编辑候选日程

```json
{
  "candidateId": "candidate_001",
  "patch": {
    "title": "产品方案讨论会",
    "startTime": "2026-05-18T10:30:00+08:00",
    "endTime": "2026-05-18T11:30:00+08:00"
  }
}
```

结果：

- 更新候选日程字段。
- `reviewStatus` 变为 `edited`。
- 如果时间变化，重新计算小日历标红日期。

### 删除候选日程

```json
{
  "candidateId": "candidate_001",
  "reviewStatus": "rejected"
}
```

结果：

- 候选项从默认列表中隐藏或显示为已删除。
- 不再参与小日历标红。
- 不会被导入。

### 确认导入

```json
{
  "sessionId": "session_001",
  "candidateIds": ["candidate_001", "candidate_002"]
}
```

导入前校验：

- 只允许导入 `isSelectedForImport = true` 的候选日程。
- 必须有标题、开始时间和结束时间。
- 如果缺少关键字段，需要阻止导入并提示用户补全。
- 如果存在时间冲突，需要展示提醒，但不一定阻止导入。

结果：

- 为每条已勾选候选日程创建正式 `Event`。
- 将候选日程 `reviewStatus` 更新为 `imported`。
- 将 `ImportSession.status` 更新为 `imported`。

## AI 输出要求

AI 返回候选日程时必须尽量结构化，避免只返回自然语言总结。

```json
{
  "summary": "我从图片中识别到 3 个可能的日程，请确认是否导入。",
  "candidates": [
    {
      "title": "产品讨论会",
      "startTime": "2026-05-18T10:00:00+08:00",
      "endTime": "2026-05-18T11:00:00+08:00",
      "location": {
        "name": "公司 12 楼会议室"
      },
      "confidence": 0.92,
      "missingFields": [],
      "sourceEvidence": "原图第 2 行：周一 10:00 产品讨论会"
    }
  ]
}
```

AI 不应自行决定最终导入，只负责生成候选日程和解释依据。

## 第一版页面状态

### 空状态

- 显示聊天输入框。
- 支持输入文字和上传图片。
- 提示用户可以发送聊天记录、活动图、课程表或行程安排。

### 解析中

- 用户消息进入聊天流。
- AI 显示解析中状态。
- 如果是图片或文件，显示附件缩略信息。

### 待确认

- AI 返回候选日程卡片。
- 默认可以勾选高置信度候选项，低置信度候选项保持未勾选。
- 小日历根据已勾选候选项标红。
- 底部显示“导入已选日程”按钮。

### 已导入

- 显示导入成功结果。
- 每条候选日程显示已导入状态。
- 提供查看日历入口。

## MVP 边界

第一版先支持：

- 文本输入
- 图片上传
- 候选日程列表
- 单条勾选和取消勾选
- 单条编辑
- 小日历标红
- 确认导入应用内日历

第一版暂不支持：

- 多人协作
- 自动读取邮箱
- 后台自动扫描聊天软件
- 复杂重复规则编辑
- 第三方日历双向同步
