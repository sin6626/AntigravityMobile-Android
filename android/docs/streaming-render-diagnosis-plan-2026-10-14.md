# 回复边输出边渲染：排查与实施计划

日期：2026-10-14。代码基线：`32f71a5`。状态：排查与计划完成，业务代码尚未修改。

## 目标与结论

目标：回复生成过程中，手机持续显示已到达的文字；标题、加粗、列表、代码等也随内容逐步成形，不等整条回复完成后再统一格式化。继续保持阅读历史不被拉走、最新位置自动跟随和结束时不整段闪换。

当前已经有分批更新，但没有生成中的 Markdown 正文渲染：

1. Go 网关轮询桌面轨迹，再通过 WebSocket 推送消息快照。运行中每次获取处理完成后重置 250 ms 定时器；空闲时为 1200 ms。它不是直接转发模型 token 的上游流，250 ms 也不是端到端延迟保证。
2. Android 收到快照就更新状态，没有等会话结束才提交正文。最新增长的 AI 消息进入 `StreamingMessageText`，以纯文本加逐字展示动画输出。
3. 只有流式标记解除，正文才走 CommonMark 的标题、列表、代码等排版。已完成标题在生成中依然是 `# 标题` 原文，这是本轮直接复现的行为差距。
4. 网关的原始轨迹签名和推送指纹都主要依赖最后一项及文字长度。等长修正、较早消息更新可能被当成“没有变化”。已用真实函数和合成输入复现；尚未证明用户这次实际会话触发了这些条件。

不能据此断言所有回复都等生成结束才显示，也不能把实际公网延迟归因于上述任何单一因素。

## 链路和代码证据

```text
桌面 GetCascadeTrajectory（JSON 快照）
  → Go：缓存读取 / 250 ms 运行轮询
  → rawTrajectorySignature 去重
  → ParseTrajectoryDetails 提取已有正文
  → StreamUpdatePayload.Fingerprint 去重
  → WebSocket messages 快照
  → StreamWebSocketClient / StateFlow
  → ChatViewModel 合并消息、标记 streamingMessageId
  → ConversationContent 后台准备列表
  → 生成中：StreamingMessageText → Text
  → 生成后：MarkdownBlock
```

| 位置 | 当前行为 | 对体验的意义 |
| --- | --- | --- |
| `internal/proxy/trajectory.go:2178` | `GetCascadeTrajectory` 获取完整 JSON，流式调用允许复用不足 200 ms 的缓存 | 当前拿到的是快照；200 ms 是缓存有效窗口，不是固定附加等待 |
| `internal/proxy/stream.go:211,235,331` | 同步首取；运行中 250 ms、空闲 1200 ms；串行完成后重新计时 | 获取耗时会叠加到周期；仅改小定时器不能保证更快 |
| `internal/proxy/stream.go:43,96,242,322` | 原始签名、推送指纹两道变化过滤 | 两道都需要正确，单修其中一层仍可能漏更新 |
| `internal/proxy/trajectory.go:1043` | 非空 `plannerResponse.response` 即可转成 AI 正文，没有要求步骤完成 | 网关正文提取没有统一“等最终结果”门槛 |
| `StreamWebSocketClient.kt:130`、`ChatViewModel.kt:374` | 每个有效消息快照解码并合并；增长检测设置流式消息 ID | 接收/状态层已有实时路径，StateFlow 可合并过时快照；当前快照协议不要求逐帧保留每个中间版本 |
| `ConversationContent.kt:171,344,360,432,465` | 后台准备列表，但流式消息跳过 Markdown；生成中使用 `Text(shown)` | 格式化推迟的直接位置 |
| `StreamingTextReveal.kt:17,39` | 对已收到文字再分批显示；约 16 ms 驱动；积压过大或输入暂停后追齐 | 这是展示动画，不能让尚未传来的内容提前出现；其参数不是实测延迟结论 |
| `ConversationContent.kt:499` | 按完整正文保存最近 80 个解析结果 | 若直接将每个流式前缀塞入缓存，会增加长文内存和解析负担 |

Android 文件均位于 `android/app/src/main/java/com/antigravity/mobile/` 相应的 `data/service`、`ui/chat`、`ui/demo` 目录。上述行号对应排查基线。

## 本轮检查与边界

诊断材料保存在项目忽略目录 `.verification/2026-10-14-streaming/`。没有修改正式测试或产品源码，没有安装 APK、重启网关、改配对或向真实会话发送消息。ADB 只发现 `emulator-5554`，没有连接真机。

### Android：验证真实列表准备函数

临时 JVM 测试经 `probe.init.gradle` 注入测试源集，直接调用 `buildConversationRenderItems`：

- 增长正文在 `isRunning=true` 时进入渲染列表：通过。
- 同一回复的完整标题在结束后能得到 `Heading`，生成中也应得到 `Heading`：失败，实际流式节点为 `null`。
- 复跑并附带既有 `ConversationRenderItemsTest`、`StreamingTextRevealTest`：共 7 项，6 项通过、上述 1 项预期失败。旧测试明确断言生成中没有 Markdown 节点，实施时必须一起更新预期。

执行命令（在 `android/` 下，`$streamGradle` 指向本机已安装的 Gradle 9.7.1 `bin/gradle.bat`；本仓库没有 `gradlew.bat`）：

```powershell
& $streamGradle -I ..\.verification\2026-10-14-streaming\probe.init.gradle :app:testDebugUnitTest --tests '*StreamingRenderingDiagnosisTest' --tests '*ConversationRenderItemsTest' --tests '*StreamingTextRevealTest' --console=plain
```

输出：`7 tests completed, 1 failed`，失败点 `completedHeadingShouldRenderWhileReplyIsStillGrowing`。日志为 `android-probe.log`。这是修复前功能差距的信号，不是验收通过，也没有测量 Compose 实际帧耗时。

### Go：验证两道真实去重函数

通过 Go overlay 加载临时测试，不向正式包目录写文件。使用实际 `PlannerResponse.Response` 字段和移动端 messages 格式：

```powershell
go test -overlay .verification/2026-10-14-streaming/overlay.json ./internal/proxy -run '^TestStreamingDiagnosis' -count=1 -v
```

输出：6 个子用例中，最后一项追加的 2 项通过；等长修正与非最后项追加的 4 项失败。两道去重都会返回与修改前相同的签名。日志为 `go-probe.log`。这是可重复的函数级验证，尚不是实际桌面会话或 WebSocket 端到端重放。

本轮不修改功能，因此不执行修复后的验收阶段。实际首字时延、公网分块节奏、真机帧率以及上游是否持续提供中间正文仍待实施阶段测量。

## 实施顺序

### 1. Android 生成过程中渲染 Markdown

建议提交：`feat(android): 支持回复生成中的富文本渲染`。

- 复用当前 CommonMark、Compose `MarkdownBlock`、代码高亮、公式和图表能力，不引入 WebView 或新的渲染依赖。
- 去掉“整条消息必须结束才能 Markdown 排版”的限制，流式与完成态使用连续的块列表。普通文字和完整的标题、强调、列表、链接等在生成过程中就能显示。
- 按到达的正文快照触发后台解析，保持最新版本优先；不要把每 16 ms 的逐字动画当作全文解析触发器。先采用现有解析器处理当前完整前缀，不自写 Markdown 增量解析器。
- 已经不变的可见块尽量复用，只更新受影响块；不能仅凭空行就永久冻结块，因为后续文本可能改变列表、表格或引用链接的含义。最终结果必须与整篇解析一致。
- 进行中的代码围栏可显示已有代码，补齐后继续更新；不完整链接、表格、公式有可读的临时展示。Mermaid/公式等高成本内容在语法足够完整时再生成图形，未完成时保留源码，避免闪空或反复错误卡。
- 流式前缀不占满 80 项历史全文缓存；为当前活动消息保留必要的最新解析状态即可。取消的任务不得发布旧正文。
- 稳定消息/块身份和底部锚点，结束时只收敛最终状态，不重新播整段。增量展示动画只用于新增可见文本；如果第一步尚不支持富文本逐字动画，先按真实快照即时更新格式，不能为动画继续延迟整个消息的格式化。
- 原文仍是唯一权威内容。停止、纠正、缩短、重连快照和切会话均能替换展示；关闭系统动画不影响内容及时到达。保持复制原文、文件/图片链接、TalkBack、执行过程折叠和历史阅读行为。

涉及：`ConversationContent` 的列表准备、消息行、解析缓存与 key；`StreamingMessageText/StreamingTextReveal` 的职责；`ChatDemoScreen` 调用参数、`ChatViewModel` 的流式标记检查；共享 Markdown 阅读器/执行过程作为回归调用方。优先不改网络协议、数据模型及依赖配置。

检查：将标题失败用例移入正式测试并转绿；更新旧“生成中 node=null”断言；补段落追加、闭合围栏、列表/表格续写、正文修正和最终解析一致性。仪器测试在仍 RUNNING 时检查格式化标题、强调与代码，而不是只断言原文存在。

### 2. 修复网关的正文变化漏推送

建议提交：`fix(gateway): 避免流式正文更新被去重遗漏`。

- 将本轮四个失败用例移入正式 Go 回归，覆盖 `rawTrajectorySignature` 与 `StreamUpdatePayload.Fingerprint` 两个门槛。
- 变化判断要覆盖实际可见内容，包含等长替换和非最后一项变化；不能继续仅以最后一项长度作为全文相同的依据。使用项目现有办法或标准库实现内容比较/摘要，避免新增依赖。
- 同时保留不变快照不重复发送的优化，并检查待处理交互、队列、后台任务等同一 payload 中的状态不会被去重误吞。
- 用隔离假上游经过真实 `HandleCascadeStream` 读 WS：保持 RUNNING，分次追加/等长修正/更改较早消息，每次读取到最新正文；不变快照不重复发送；结束状态和重连首帧完整。
- 初期保留既有 250/1200 ms 节奏、200 ms 缓存规则和 messages 契约。网关由 Android/iOS/Web 共用，分别检查 messages-only 与含 steps 的推送，不随意更换成 delta 协议。

涉及：`internal/proxy/stream.go`、`stream_test.go`、轨迹解析/缓存的相关用例；Android 的 `StreamUpdatePayload` 解码、`mergeMessages` 及旧读取不覆盖新快照的行为需回归。仅在测试发现需要时调整这些调用方。

### 3. 端到端时延、长文与真机验收

建议提交：`test(android): 验证流式富文本与阅读位置`，实际性能修正有证据后另列最小提交。

- 先用隔离假网关每 250 ms 提供一份增长快照，覆盖正常、突发多块、慢响应、停止、断线重连、切会话和非追加修正。确认正文和富文本在完成事件之前已显示。
- 对同一段确定性长回复记录“上游响应完成→WS 发出→手机收到→状态合并→内容实际绘制”的耗时。跨设备时间不同步时使用同设备单调时钟分段统计，不直接相减冒充精确端到端值。
- 先定功能门槛：非忙碌模拟器收到测试快照后 500 ms 内出现对应文字和已完整格式，最终快照无遗漏。这个值是待验证的测试预算，不是当前实测，也不是用户公网承诺；真实设备再报告首字及更新延迟的中位数、P95 和丢帧情况。
- 长文分别用约 2 千、1 万、5 万字，含代码、表格、图片链接、公式/图表；检查解析耗时、内存与 UI 卡顿。仅在测量显示全文解析成为瓶颈时增加尾部解析等优化，不预建复杂增量框架。
- 回归 `MotionTest.streamFormattingKeepsLatestVisibleAndDoesNotPullHistory`、`jumpButtonDoesNotFlashWhileStreamingLatest`、`ReducedMotionTest`、`ReplyMediaAndCopyTest`、执行过程和阅读器相关用例。最新位置自然跟随，阅读历史时锚点保持，回到最新按钮不闪，完成时不整段跳换。
- 真实设备接回 ADB 后保数据覆盖安装，再做局域网与当前公网路径对照。不用会卸载目标应用的测试流程，不改输入法、配对、真实队列或历史。
- 既有 `API_TRACE` 结构继续记录请求信息、脱敏请求头、状态和响应；HTTP 拦截器只能覆盖 WS 握手。必要的 WS 诊断另记录事件类型、消息数量、长度和时间，不输出令牌、附件正文或私人回复全文；临时性能探针验完移除。

若实测发现上游只在结束时提供正文，需要先确认桌面是否公开可用的流式/订阅 RPC，再决定协议工作。必须明确消息字段、更新方式、结束/错误、恢复和取消契约；资料不全时列出具体问题交由用户向后端确认，不能猜接口，也不能靠逐字动画伪装上游实时。

## 完成标准与当前状态

后续功能完成须同时满足：生成中可见文字和 Markdown、最终原文完全一致、已确认的漏推送用例转绿、阅读位置稳定、长文性能有数据、连接恢复和会话隔离不回退。真机未测时只能标注模拟器验证完成。

本轮仅完成排查和计划，保留上述失败信号。没有新 UI 图形或切图需求；没有新增依赖、改轮询频率或声称打通真正 token 级上游流。下一步从 Android 生成中富文本开始，随后修复网关去重并做整链路验收。
