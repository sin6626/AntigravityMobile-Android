# Antigravity 2.19.1 手机端功能对齐审计与开发计划

核查日期为 2026-10-13。本报告回答两个问题：电脑端已有而 Android 还不能完成的工作是什么，以及后续按什么顺序补齐。功能判断以本机版本、Google 官方资料、Android 源码和 Go 网关源码交叉核对。官方资料原始记录见[桌面能力一手来源记录](desktop-capabilities-source-notes-2026-10-13.md)。

## 结论

手机端已经能完成会话查看、文字和图片发送、流式回复、模型与推理强度选择、单选审批、停止、会话管理和带文件预览的回退。当前最大问题不在基础聊天，而在远程控制闭环：电脑等待方案审阅、复杂问答、排队消息或后台任务操作时，手机经常看不到，或者只能提示回电脑处理。

下一批应先把网关已经返回但 Android 丢弃的状态接到界面，再研究新协议。优先级如下：

1. 方案审阅与继续执行、多选审批和澄清问答。
2. 排队消息、后台任务和子代理状态。
3. 电脑工作区上下文与普通文件附件。
4. Artifact、文件和 diff 的审阅、评论、下载或分享。
5. `/plan` 规划入口、计划审阅策略、语音、额度和电脑设置状态。

Google 官方 Remote Control 的手机工作流也是这个顺序：看活动会话、发起任务、审阅计划和 Artifact、收到完成或待输入通知。它适合作为手机体验参照，不代表本项目可以直接复用其私有接口。[Remote Control](https://antigravity.google/docs/remote-control/)

## 对齐基线

本机 `C:\Users\F\AppData\Local\Programs\antigravity\Antigravity.exe` 的 ProductVersion 是 `2.19.1.0`，FileVersion 是 `2.19.1`。该版本号与官方独立 Antigravity 2.0 的 2026-09-30 发布记录一致，本轮以 2.19.1 为基线。[官方 Changelog](https://antigravity.google/docs/changelog)

2.21.1 的会话全文搜索和 Automations、2.22.0 的侧栏拖拽与 UI Extensions、2.23.0 的 `.zip` 和文件夹附件属于本机版本之后的能力。本轮只记录，不算 Android 必做缺口。旧 IDE Workflows 将于 2026-10-19 停止工作，完整旧 Workflow 编辑器也不进入开发计划。[Workflows](https://antigravity.google/docs/ide/workflows/)

本轮对齐目标是手机上的远程协作闭环。手机需要能理解电脑正在做什么、在关键节点给出输入、审阅产物并安全恢复。完整复制桌面终端、浏览器和 Git 工作台不是前几批目标。

## 当前能力矩阵

状态说明：已完成表示 Android 已有入口和完整操作；部分完成表示数据、接口或阅读能力存在，但流程没有闭合；缺失表示当前源码没有对应入口；待协议表示不能从现有字段安全推断请求格式。

| 工作流 | 2.19.1 基线 | Android 当前状态 | 代码证据 | 处理级别 |
| --- | --- | --- | --- | --- |
| 项目与会话浏览 | 项目、多会话、归档、置顶和工作区归属 | 已完成主要浏览和会话管理；多文件夹项目、worktree 选择未呈现 | `ProjectOverview.kt`、`DemoDrawer.kt`、`ChatViewModel.kt:399-565` | P2 补项目结构 |
| 文字与图片对话 | 消息、图片、流式输出和模型选择 | 已完成，含草稿恢复、失败恢复和阅读锚点 | `ChatViewModel.kt:590-710`、`ConversationContent.kt` | 保持回归 |
| 模型与推理强度 | 每模型 Low / Medium / High，切换对后续执行生效 | 已完成主要选择体验，模型目录来自后端 | `ModelConfigBottomSheet.kt:41-43`、`ChatViewModel.kt:461-493` | 保持回归 |
| 单选权限与问答 | 命令权限、确认和澄清问题 | 已完成，单选和自由输入可提交 | `InteractionPanel.kt`、`ChatViewModel.respondToInteraction` | 保持回归 |
| 多选问答 | 电脑可呈现多选问题 | 已完成。支持多项选择、自定义回答、必答校验、失败保留和重试 | `InteractionPanel.kt`、`ChatViewModel.respondToInteraction`、`InteractionSubmitRequest.selectedOptionIds` | 保持回归 |
| 方案审阅与 Proceed | `/plan`、计划评论、Proceed 和 Review | 已完成。支持整份阅读与批准，也可把待提交定位评论和批准放在同一请求中 | `ApiClient.proceedArtifact`、`ChatViewModel.proceedArtifact`、`PlanReviewPanel` | 保持回归 |
| 计划定位评论 | 对计划选区评论并要求代理修订 | 已完成。按 Markdown 段落定位原文与行范围，可积累多条、持久化草稿、独立提交修订并失败重试 | `ArtifactCommentDraft.kt`、`LinkedFilePreview.kt`、`ApiClient.submitArtifactComments` | 保持回归 |
| 运行中排队消息 | Queue、Send Now、编辑和删除 | 已完成。历史/实时队列、运行中新增队列、立即发送、编辑回填、删除、失败留态和草稿恢复均已接入 | `QueuedMessagesPanel.kt`、`ChatViewModel.queueCurrentMessage`、`ChatViewModel.sendQueuedMessageNow` | 保持回归 |
| 后台任务 | 查看任务、命令、状态并单独停止 | 已完成。历史/实时状态、摘要、命令、动态时长、单项停止、失败留态与空列表清理均已接入 | `RunningTasksPanel.kt`、`ChatViewModel.stopRunningTask`、`ApiClient.stopTask` | 保持回归 |
| 子代理 | 2.16 有实时卡片，2.19.1 可直接给子代理发消息 | 查看与导航已完成：直属子代理、状态、步骤、父子层级、进入只读轨迹和逐级返回均已接入；定向消息仍待公开调用契约 | `/gateway/subagents`、`SubagentsPanel.kt`、`ChatViewModel.openSubagent` | P0 剩定向消息协议 |
| 普通文件附件 | 基线前已支持 PDF、纯文本、JSON、Markdown、CSV、音频和 Office 文档，能力受模型限制 | 已完成。文件选择、模型 MIME 校验、8 MB 单文件草稿、进程/失败恢复、普通与排队发送、文件胶囊和无障碍均已接入；使用 `media[].mimeType/displayName/inlineData` | `ChatViewModel.addFile/send`、`PreferencesManager`、`ApiClient.sendMessage`、`ClientModelConfig.supportedMimeTypes` | 保持回归；多文件/文件夹不在 2.19.1 范围 |
| 电脑工作区 `@` 上下文 | 输入区能检索并引用项目文件，文件附件与工作区引用是两种动作 | 已完成。`@` 检索、独立胶囊、草稿恢复和结构化 `file/directory` 发送均已接入 | `/gateway/workspace/context`、`DraftComposerContext`、`SendUserCascadeMessageRequest.items` | 保持回归 |
| 规划入口与计划审阅策略 | 2.19.1 已移除旧 Planning/Fast 开关，以 `/plan` 进入规划；全局策略为 Always Ask / Always Proceed | 已完成。发送前显示直接/规划，规划复用真实 `/plan` 命令；可读取并更新电脑全局审阅策略 | `/gateway/preferences/plan-review`、`WorkModeBottomSheet.kt`、`ChatViewModel.selectPlanningMode` | 保持回归 |
| 斜杠命令、Skills 和 Rules | 输入区可发现 `/plan` 等命令，Skills 与 Rules 影响代理上下文 | 斜杠命令已完成真实目录发现、独立胶囊和结构化发送；Skills / Rules 独立管理入口仍未实现 | `/gateway/commands`、`Composer`、`ContextScopeItem.slashCommand` | P2 剩管理入口 |
| 文件和 Artifact 阅读 | 文件树、Markdown、媒体、Artifact 和浏览器录制 | 已完成手机适配范围：Markdown、代码、文本和图片原生阅读；PDF、Office、音视频下载后交给系统兼容应用预览或播放 | `LinkedFilePreview.kt`、`LinkedFileActions.kt` | 保持回归；不内嵌 Office 阅读器 |
| 下载、分享与导出 | 2.19.1 可把渲染 Markdown 导出 PDF，文件与媒体有保存入口 | 已完成文件与 Artifact 的系统保存、分享；图片同样支持。Android 直接保存 Markdown 原文件，未另造渲染 PDF 管线 | `ApiClient.downloadFile`、`LinkedFileActions.kt`、`FileProvider` | 保持回归 |
| diff 审阅与评论 | 文件 diff、Review Changes、评论与 Git 操作 | 当前变更阅读与定位评论已完成；Accept、Reject、discard、stage 和 commit 仍待各自动作契约 | `/gateway/cascade/diffs`、`DiffReviewScreen.kt`、`ApiClient.submitDiffComments` | P1 剩 Git 动作协议 |
| 会话撤回 | 2.19.1 支持只撤回会话 | 已完成会话与文件两种回退范围，并保留草稿和图片 | `ChatViewModel.kt:788-860` | 保持回归 |
| 模型额度 | 模型组共享每周与 5 小时额度，可读剩余比例、恢复时间并强制刷新 | 已完成。模型配置页可查看官方分组额度、倒计时并在失败时保留旧值重试 | `RetrieveUserQuotaSummary`、`ModelQuotaBottomSheet.kt`、`ChatViewModel.loadModelQuota` | 保持回归 |
| Cockpit 多账号切换 | 未发现 Google 官方将第三方账号池列为桌面对齐能力 | 接口存在但 UI 未接。应作为本项目附加功能单独评估 | `ApiClient.kt:317-337` | 非核心对齐 |
| MCP、权限和电脑设置 | 桌面管理 MCP、权限、项目设置和插件 | 缺失。手机看不到电脑当前策略、MCP 错误或待审批工具来源 | Android 无设置状态接口 | P2，待协议 |
| 语音转写 | 2.19.1 使用 16 kHz PCM 流式转写，停止后文字留在可编辑输入框 | 已完成。真实波形、计时、取消、整理中、失败重试和原草稿保护均已接入 | `VoiceRecorder.kt`、`StreamAudioTranscription`、`ChatViewModel.startVoiceInput` | 保持回归；当前桌面账号未开放转写权限时明确提示 |
| 通知直达 | 官方 Remote Control 支持完成和需输入提醒 | 半成品且按用户要求暂缓。FCM 类存在，但会话深链仍被交给配对流程 | `AntigravityMessagingService.kt`、`MainActivity.kt:42` | 暂缓 |

## 已经到达客户端但被丢弃的数据

这部分最适合先做。修改范围小，能最快让手机从“聊天查看器”变成远程控制端。

`StreamUpdatePayload` 已包含 `queuedMessages`、`runningTasks`、`canProceed` 和 `proceedArtifactUri`。`ChatViewModel` 在 WebSocket 更新和首次消息加载时只复制消息、运行状态、待交互和模型，四个字段没有进入 `ChatUiState`。对应代码位于 `ChatViewModel.kt:192-202` 和 `ChatViewModel.kt:922-933`。

现有 `ApiClient` 已有以下动作，但 Android 主流程没有调用：

- `proceedArtifact`：批准整份方案并继续执行。
- `deleteAgentMessage`：删除队列消息。
- `stopTask`：单独停止后台任务。
- `downloadFile`：下载文件。
- `fetchCockpitQuotas` 和 `refreshCockpitQuotas`：读取与刷新额度。

这些接口存在不等于 UI 可以直接拼出来。每个入口仍需处理加载、成功确认、失败恢复、会话切换和重复点击。

## 开发顺序与提交边界

下面每一项单独提交。UI、交互、动画、错误恢复和对应验证放在同一个功能提交里，不把多个功能攒成大提交。

### 第一批：审批和运行控制

1. `feat(android): 展示待审方案并支持继续执行`
   - **已于 2026-10-13 完成。**
   - 在输入框上方显示清楚的待审卡片，包含计划标题、来源会话和“查看方案”。
   - 用全屏阅读页打开已有 Markdown 预览。关闭后保留滚动位置，Proceed 显示提交中、成功和失败恢复。
   - 卡片出现与消失使用高度和透明度连续过渡；运行状态变化时不抢正文滚动位置。减少动态效果时直接切换最终状态。

2. `feat(android): 支持多选审批与澄清问答`
   - **已于 2026-10-13 完成。**
   - 复用现有 InteractionPanel 的排版和单选视觉，增加复选状态、完成按钮、自由输入与必答校验。
   - 选项长文本可完整阅读，提交失败保留全部选择。面板高度变化不遮住输入框。
   - 已从网关上游类型确认 `selectedOptionIds`、`writeInResponse`、`skipped/cancelled` 结构，并保留旧 `optionId` 兼容；未增加未经证实的 scope 或拒绝枚举。

3. `feat(android): 展示并管理排队消息`
   - **已于 2026-10-13 完成。**
   - 运行时保留输入与发送能力，发送后在输入框上方显示队列卡片。
   - 每项支持立即发送、编辑和删除。编辑先回填草稿，接口失败时恢复原队列位置。
   - 展开、折叠、插入、删除和快速反向操作保持连续；TalkBack 读出队列长度和三个动作。

4. `feat(android): 展示并停止后台任务`
   - **已于 2026-10-13 完成。**
   - 显示真实命令摘要、运行状态和持续时间，允许单独停止，不把“停止当前回复”当作“所有后台任务都已停止”。
   - 状态更新只改对应行，列表不闪烁、不跳位；停止中禁用重复操作，失败恢复按钮。

5. `feat(android): 展示并联动子代理`
   - **查看与导航已于 2026-10-13 完成；定向消息仍单列待协议。**
   - 子代理与普通后台命令分开显示，保留父子关系、等待审批、运行和完成状态。
   - 先完成查看和跳转，再单独提交“向子代理发消息”。两者不得合并成一个提交。
   - 网关已从本机 `GetAllCascadeTrajectories` 的 `parentConversationId/rootConversationId/nestingDepth` 返回直属子代理；Android 进入子轨迹后使用现有消息读取和实时流，仅显示只读记录栏。
   - 本机二进制确认的 `send_message` 是内部代理工具（`Recipient` 为会话 ID 或 `@children`），尚未找到等价的移动端 HTTP/RPC；因此没有把 `SendUserCascadeMessage` 猜成子代理定向消息。

### 第二批：上下文、文件和审阅

6. `feat(gateway): 明确并转发文件附件契约`
   - **已于 2026-10-13 完成。**
   - 先从本机 2.19.1 请求中确认文件名、MIME、内容或句柄字段、大小、数量、模型限制和失败码。
   - 已确认通用附件继续使用既有 `media`，字段为 `mimeType/displayName/inlineData`，不是新造 `files` 数组；上游接收后清空内联正文并生成 `.user_uploaded` URI。格式能力来自每个模型的 `supportedMimeTypes`。
   - 网关保持 50 MB 总请求上限和原样转发，并把文件 MIME、名称、URI、正文长度/前缀纳入短时去重，避免相同提示附带不同文件时误丢第二条。Android 首版采用更小的本地限制，不把网关总上限展示为单文件承诺。

7. `feat(android): 支持普通文件草稿与发送`
   - **已于 2026-10-13 完成。**
   - “图片”和“文件”继续是两个入口。文件胶囊显示类型、名称、大小、上传状态和移除操作。
   - 选择后立即做大小、格式和模型能力检查；错误在发送前说明，失败不丢文字和已选附件。
   - 第一版只开放已核实的格式。`.zip` 和文件夹属于 2.23，不进入本机 2.19.1 实现。
   - 当前实现一次选择 1 个、单文件最多 8 MB；格式严格跟随所选模型 `supportedMimeTypes`。草稿位于应用私有 no-backup 目录，支持进程重建、发送失败和排队失败恢复。
   - 新会话、现有会话与运行中 `deliveryStrategy=2` 都发送命名 `media`；队列响应保留 MIME/显示名/内联内容，使编辑与立即发送不会把普通文件误作图片。接口日志保留名称和 MIME、隐藏正文。

8. `feat(gateway): 暴露电脑工作区上下文查询`
   - **已于 2026-10-13 完成。**
   - 本机 2.19.1 protobuf 确认请求对象为 `items[].item.file|directory`，路径对象使用 `absoluteUri`、`workspaceUrisToRelativePaths`、`numFiles` 和 `numBytes`；网关不把引用降级成文本或手机附件。
   - `/gateway/workspace/context` 支持普通目录和多根 `.code-workspace`，返回根名、相对路径、稳定 URI、大小和可发送 `scopeItem`；`limit/cursor` 提供确定性分页，同名结果保留根和长路径。
   - 查询不跟随符号链接，拒绝远程和受限根目录，沿用敏感文件过滤并跳过版本库、依赖、构建缓存和验收材料；50,000 项扫描上限通过 `truncated` 告知客户端。

9. `feat(android): 添加电脑文件引用与命令发现`
   - **已于 2026-10-13 完成。**
   - 在输入框内用独立胶囊区分手机附件、电脑文件和斜杠命令。
   - `@` 与 `/` 面板跟随键盘，长路径可辨识，选择后焦点回到输入框。加载、空结果和网络失败都有可恢复状态。

10. `feat(android): 支持方案定位评论`
    - **已于 2026-10-13 完成。**
    - 选区后出现克制的评论入口，评论草稿在关闭和旋转后保留。
    - 评论提交与 Proceed 分开。用户可只提意见，让电脑端重新生成方案。
    - 已从本机 2.19.1 protobuf 与桌面前端确认：定位评论使用 `scope.selections[].selection.textSelection`，范围为零基起始行和尾后结束行；普通反馈省略审批状态，Proceed 使用 `APPROVED=1`；多条评论通过同一 `artifactComments[]` 提交。
    - Android 按 Markdown 可读段落点选并高亮，支持 20 条评论、移除、关闭/旋转/进程恢复、失败留态和重试；只提交评论不会附带整份批准，Proceed 会按桌面行为合并待提交评论。
    - 网关短时去重改为哈希完整评论对象，避免同一方案上的不同范围或文字被误判重复。
    - 聚焦 Go 测试、Android 单测、Debug/AndroidTest 构建及 API 37 两条仪器测试通过；主网关 58900、管理网关 58901、主公网和模拟器链路均已部署并健康。

11. `feat(android): 扩展文件与 Artifact 阅读操作`
    - **已于 2026-10-13 完成。**
    - 普通文件增加下载和系统分享；PDF、Office、音频、视频按平台能力选择系统预览或下载说明。
    - 浏览器录制先提供可播放与下载，再考虑评论。不要在 Compose 内重造 Office 阅读器。
    - 所有预览页提供 48 dp 的保存、分享操作；PDF、Office 与音视频额外提供系统打开或播放。文件先经带鉴权的网关原始流写入隔离缓存，再用 FileProvider 临时授权；系统“创建文档”负责用户选择保存位置。
    - 下载沿用网关原始流、Range 与敏感路径保护，并补齐会话 ID；设备令牌只发送给当前网关同源地址。临时文件采用原子替换，失败时清理单个 `.tmp` 文件并保留预览页重试。
    - 没有增加内嵌 Office/PDF/媒体依赖，也没有把 Markdown 渲染为另一份 PDF；Android 分享/保存原始 Artifact，避免内容与桌面源文件不一致。

12. `feat(android): 添加当前变更 diff 审阅`
    - **已于 2026-10-13 完成读取与定位评论。**
    - 与“回退到某条消息”使用不同标题、颜色和确认文案。
    - 第一版先读 diff 和定位评论。Accept、Reject、Git discard、stage 和 commit 必须按真实后端动作分别设计。
    - 网关按本机 2.19.1 的 `GetTrajectoryFileDiffs` 读取当前会话文件变更，过滤 Artifact、Knowledge、noop 与内容未变项；Android 展示文件、增删行号和折叠上下文。
    - 新增行使用 `modifiedSelection`，删除行使用 `originalSelection`，行号为零基起始和尾后结束；评论草稿按会话保存，关闭后可继续编辑，提交失败不丢内容。
    - Go 聚焦测试、Android 单测/构建和 API 37 仪器测试通过；同一二进制已部署到 58900 与 58901，本地、公网、模拟器健康及 58901 真实 diff 读取均通过。

### 第三批：模式与管理

13. `feat(android): 添加规划入口和计划审阅策略`
    - **已于 2026-10-13 完成。**
    - 本机 2.19.1 已移除旧 Planning/Fast 开关；手机端发送前显示“直接/规划”，规划从真实斜杠命令目录选择 `/plan`，不伪造已删除的会话模式。
    - 桌面当前 UI 只开放 `ALWAYS`（总是询问）和 `TURBO`（自动继续）；手机端同步同一全局配置，并清楚警示自动继续会跳过人工确认。
    - 工作方式和全局策略分区展示，包含 48 dp 触控区、180 ms 选中反馈、加载、失败保留、重试、完成动作和 TalkBack 描述。

14. `feat(android): 展示模型额度与恢复时间`
    - **已于 2026-10-13 完成。**
    - 使用桌面官方 `RetrieveUserQuotaSummary`，呈现共享模型组、每周/5 小时剩余比例、本地恢复时间和强制刷新；刷新失败保留旧值。
    - 第三方 Cockpit 多账号池没有混入此入口，继续作为非核心附加功能单独评估。

15. `feat(android): 添加语音转写草稿`
    - **已于 2026-10-13 完成。**
    - 与桌面 2.19.1 一致使用 `StreamAudioTranscription` 建立流，`SendAudioChunk` 发送 `audio/pcm;rate=16000` 分块，停止时调用 `EndAudioSession`；最终文字保留在可编辑草稿中。
    - 麦克风权限按需申请，波形读取真实 PCM 能量，计时和 3 分 30 秒上限跟录音状态同步；取消、会话切换和网络失败均恢复录音前草稿。
    - 请求拦截日志保留方法、URL、脱敏请求头、状态和字节数，录音数据与草稿上下文不写入 Logcat。当前真实桌面账号返回 `insufficient authentication scopes`，手机会持续显示“当前电脑账号未开放语音转写”并允许重试；成功链路由隔离 ConnectRPC 假网关验证。

16. `feat(android): 展示电脑 MCP 与权限状态`
    - 第一版只读显示连接、错误和审批来源。修改 MCP 配置、OAuth 或全局权限在确认安全契约后另做提交。

通知直达保持暂缓，不列入上述提交。恢复时应先修复会话深链分流，再申请权限和注册推送；不得把 `antigravity://cascade/...` 继续传给 `pair`。

## 接口未明确前的阻塞项

根据项目约束，以下字段没有文档或真实请求样本时不能开始页面写入逻辑：

- 普通文件上游是否另有限制单文件大小或多文件数量；Android 首版先限制为单个小文件，上传字段、文件名、MIME、模型能力和落盘恢复方式已经确认。
- `@` 文件引用、Skills、Rules 和斜杠命令的结构化请求对象。
- diff 的 Accept、Reject、Git discard、stage 和 commit 动作对象；读取与定位评论对象已经确认并接入。
- 子代理定向消息的公开 HTTP/RPC 入口和停止目标。身份与父子关系已经由轨迹元数据确认并接入。
- MCP、权限和项目 worktree 的读取与修改接口。

这些项应先用隔离测试会话观察本机 2.19.1 的真实请求，再固化网关 DTO。所有新调用继续经过现有 `ApiTraceInterceptor`，记录方法、URL、参数、脱敏请求头、状态码和响应。

## UI、UX 和动画验收门槛

每个功能开工前先画出正常、加载、空、失败、重试、提交中、快速返回和断线恢复状态。接口能通不是完成标准。

- 待处理事项必须靠近输入区并在会话列表有清楚标记，打开后定位到原任务。不能用短暂 Toast 代替持续状态。
- 长计划、diff 和文件阅读要保留滚动位置。实时更新不能把用户拉回底部。
- 卡片插入、移除、展开和折叠使用可中断的原生过渡，快速反向从当前进度继续。系统减少动态效果时不依赖动画传达状态。
- 所有动作至少 48 dp 点击区域，有选中、处理中、禁用和失败反馈。TalkBack 能读出对象、状态和动作结果。
- 键盘、底部输入框和上方卡片一起避让。状态变化不能让发送按钮、正文或底部留白突然跳位。
- 需要图片、品牌图标或专用插画时向用户索取切图，不用 CSS、SVG 或临时绘制替代正式资源。
- 验证至少覆盖功能状态测试、慢速逐帧、快速反向、减少动态效果、会话切换、断网重连和真实 58901 网关。涉及写操作时使用隔离测试会话。

## 本轮没有实施的内容

审计后的前五项已经分别完成：待审方案与继续执行、多选审批与澄清问答、排队消息管理、后台任务查看与单项停止、子代理查看与导航。均使用隔离网关和模拟器验证，没有发送真实消息、批准真实计划、停止真实任务或上传文件。子代理定向消息仍因公开调用契约未确认而保留为独立后续功能，其余功能继续按一个功能一个中文 Conventional Commit 提交。

没有把 2.21.1 之后的全文搜索、Automations、侧栏拖拽、UI Extensions、`.zip` 和文件夹附件列为当前缺口。没有把第三方 Cockpit 多账号池当作 Google 官方桌面对齐要求。通知继续遵守用户此前的暂缓决定。
