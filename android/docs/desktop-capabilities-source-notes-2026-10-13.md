# Antigravity 桌面能力一手来源记录

核查日期为 2026-10-13，按 Asia/Shanghai。本文供移动端功能对齐研究使用，只记官方能力和核查边界，不代表手机端已实现或缺失。

主代理提供的本机信息为 `Antigravity.exe` ProductVersion `2.19.1.0`、FileVersion `2.19.1`，安装目录 `C:\Users\F\AppData\Local\Programs\antigravity`。本研究未启动、操作或修改真实桌面会话，也未核验本机每项开关。

## 产品线和版本边界

Google 将 Antigravity IDE、独立 Antigravity 2.0、CLI、IDE 扩展分别发布。2.0 于 2026-05-19 发布，独立于 IDE，同时保留 Agent Manager 的协作方向。原 IDE 的 Agent Manager 是跨工作区观察与管理并行代理的入口。本机产品线最终由本机 UI 核验确定，不能只按名称混用不同产品的文档。[2.0 官方发布说明](https://antigravity.google/blog/introducing-google-antigravity-2)、[原 Agent Manager 发布说明](https://antigravity.google/blog/introducing-google-antigravity)

官网相同版本 `2.19.1` 属于独立 2.0 的版本序列。因此它是本次版本对照基线，官网当前文档中的新增能力仍需逐项对照，不能自动算为本机已具备。官网发布会分批到达用户。[官方 Changelog](https://antigravity.google/docs/changelog)

| 产品 | 版本 / 日期 | 可定位的能力变化 | 本轮归类 |
| --- | --- | --- | --- |
| 2.0 | 2.1.4 / 2026-06-11 | PDF 发给 Gemini 模型 | 基线前能力，仍核附件契约 |
| 2.0 | 2.4.3 / 2026-07-28 | 录音首次权限修复，证明输入区已有录音能力 | 基线前能力，仍核本机和接口 |
| IDE | 2.5.2 / 2026-08-12 | `.txt`、`.pdf`、`.md`；排队消息、Send Now、思考等级 | 独立产品线证据 |
| 2.0 | 2.16.0 / 2026-09-22 | Office 附件、实时子代理卡片 | 基线前能力 |
| 2.0 | 2.17.0 / 2026-09-22 | `/plan`；三种计划审阅策略 | 基线前能力 |
| 2.0 | 2.19.1 / 2026-09-30 | 直接给子代理发消息；仅撤回会话；Markdown 导出 PDF | 本机版本对应记录 |
| 2.0 | 2.21.1 / 2026-10-07 | 会话全文检索；Automations；步骤分组 | 升级后能力 |
| 2.0 | 2.22.0 / 2026-10-08 | 侧栏拖拽；UI Extensions | 升级后能力 |
| 2.0 | 2.23.0 / 2026-10-12 | `.zip`、文件夹附件；输入区权限选择；全屏媒体 | 升级后能力 |

上述日期和版本均核查自 [官方 Changelog](https://antigravity.google/docs/changelog)。PDF 附件、Office 附件与最新的 `.zip` / 文件夹附件必须分别处理。

## 手机远程协作的核心工作流

下表的“手机核查点”是基于官方工作流提出的对齐建议，属于研究推断。是否缺失由 Android、Go 网关和本机 UI 的联合核查决定。

| 工作流 | 官方已确认 | 未确认边界 / 手机核查点 | 一手来源 |
| --- | --- | --- | --- |
| 多会话与任务管理 | IDE 可同时运行多个代理会话；原 Manager 可跨工作区管理并行任务并通过 Inbox 关注进展 | 核查项目、会话身份和运行状态是否一致；切到另一会话后，原任务是否仍在进行且可找回 | [Agent](https://antigravity.google/docs/agent)、[Manager 发布说明](https://antigravity.google/blog/introducing-google-antigravity) |
| 后台任务和子代理 | 2.0 支持异步命令、动态子代理和实时进度；子代理继承主代理权限 | 本机及网关的子代理数据契约待核；手机不应将后台任务误显示为完成，等待审批的子代理应有入口 | [2.0 深入说明](https://antigravity.google/blog/google-io-2026-feature-deep-dive) |
| 项目与 worktree | 2.0 项目可包含多个文件夹，有项目权限，创建会话可选 Local 或 New worktree | 项目不能等同于单个文件夹；需识别工作目录、worktree 和项目，防止同名项目或隔离任务混淆 | [Projects](https://antigravity.google/docs/projects/) |
| Planning / Fast | Planning 先分析并形成任务组和计划；Fast 直接处理局部任务 | 手机要展示真实当前模式和适用含义，发送前可确认；不能将模式切换与模型思考等级混为一项 | [Artifact review](https://antigravity.google/docs/artifact-review) |
| 计划审阅与继续 | Implementation plan 可加评论，Proceed 继续执行，Review 可提交反馈让代理迭代计划 | 仅展示 Markdown 不等于完整审阅；核查手机能否看全文、提交定位评论、继续执行且收到确认 | [Implementation plan](https://antigravity.google/docs/implementation-plan/) |
| 工具审批与问答 | IDE 任务组将待审批的终端命令、浏览器准备步骤集中显示；远程控制支持跨端回应工具确认和澄清问题 | 核查所有审批类型、问答选项和自由输入，不能只有一个泛化“同意”按钮；审批应定位到原任务 | [Task groups](https://antigravity.google/docs/tools/)、[Remote Control](https://antigravity.google/docs/remote-control/) |
| Artifact 和视觉反馈 | 文本计划、diff、截图、浏览器录制均可作为协作结果；截图支持评论，浏览器录制可回放 | 只支持聊天文字会漏掉审阅证据；核查附件列表、图片查看、视频播放及评论，尤其由浏览器产出的 UI 验证结果 | [Artifacts](https://antigravity.google/docs/artifacts/)、[Screenshots](https://antigravity.google/docs/screenshots/)、[Browser recordings](https://antigravity.google/docs/ide/browser-recordings/) |
| `@` 上下文与文件引用 | 规则可用 `@` 显式激活或引用文件；官方版本记录也有输入区文件引用搜索与路径相关修正 | 不据此假定任意 `@` 字符串等同于桌面上下文对象；须核本机菜单类型、定位数据和网关契约，附件上传与电脑工作区引用是不同动作 | [Rules](https://antigravity.google/docs/rules/)、[官方 Changelog](https://antigravity.google/docs/changelog) |
| 文件附件 | 官方已确认的格式及模型限制见版本表，不能从“文件”一词推断任意格式 | 核接口支持的 MIME、扩展名、大小、数量、模型、解析失败与发送反馈；在契约明确前不扩大手机过滤器 | [官方 Changelog](https://antigravity.google/docs/changelog) |
| 斜杠命令、Rules、Skills | 官方有 `/plan` 等命令；Rules 是持续约束，Skills 用于多步工作。不同产品支持的命令有区别 | 手机可核命令发现、说明、参数与传递；不能将 CLI 专有命令直接放进桌面对齐清单 | [Slash commands](https://antigravity.google/docs/slash-commands/)、[Rules](https://antigravity.google/docs/rules/) |
| 旧 Workflows | IDE 的 Markdown workflow 可通过 `/workflow-name` 调用 | 官方明确旧 Workflows 将于 2026-10-19 停止工作，当前建议迁移 Skills。完整旧 Workflow 编辑器不宜成为本轮必做项 | [Workflows](https://antigravity.google/docs/ide/workflows/) |
| 终端与浏览器 | IDE 可操作本地 Chrome 并记录；编辑器会话工具栏可追踪终端过程，2.0 有集成终端 | 手机需要看到命令、目录、运行状态、结果与审批，再核交互式输入和中断；手机自行打开网站不等于操作电脑浏览器 | [Browser overview](https://antigravity.google/docs/ide/browser/)、[Agent side panel](https://antigravity.google/docs/ide/agent-side-panel/)、[2.0 Features](https://antigravity.google/docs/features) |
| diff 审阅与反馈 | IDE 有 Review Changes，可以评论文件 diff；2.0 VCS 支持工作区、分支、Agent edits 的 diff，以及逐文件 Git 操作 | 需要明确审阅对象和基准；2.0 的 Git discard、会话撤回、VS Code 扩展逐块 Accept / Reject 是不同操作，不能合成一个“拒绝”接口 | [IDE Review changes](https://antigravity.google/docs/ide/review-changes-editor/)、[2.0 VCS panel](https://antigravity.google/docs/features#version-control-system-vcs-panel) |
| 回退 / 撤回 | 本机对应版本的变化见版本表 | 必须核查仅撤回会话与同时撤回文件的行为、受影响文件及当前工作目录。原会话存在并不证明网关可安全回退 | [官方 Changelog](https://antigravity.google/docs/changelog) |
| 模型、额度与账号 | 模型选择保存在会话中；正在运行时换模型，下次执行才生效。可用模型和额度受账号方案约束 | 手机应说明切换何时生效、剩余额度和恢复时间；官网模型清单动态变化，应使用后端返回。本次未发现官方承诺本项目的多账号池或 Cockpit 切号能力 | [Models](https://antigravity.google/docs/models/)、[Plans](https://antigravity.google/docs/plans/) |
| MCP | 官方支持连接 MCP，桌面可管理服务器、开关、刷新和配置，以工具及资源补充上下文 | 核查手机能否看到电脑 MCP 连接状态、错误和待审批调用；不等于手机自行运行 MCP。OAuth、凭据和工具权限契约待核 | [MCP](https://antigravity.google/docs/mcp/) |
| 安全策略 | Windows 保留终端自动执行、非工作区文件访问等设置；浏览器有独立配置与 URL 权限机制 | 不能将 macOS / Linux 权限预设照搬到 Windows；展示电脑当前策略并让远程审批遵守同一策略。页面术语与新发布记录有差异，枚举以本机契约为准 | [Agent settings](https://antigravity.google/docs/agent-settings)、[Browser URL policy](https://antigravity.google/docs/ide/allowlist-denylist/)、[Separate Chrome profile](https://antigravity.google/docs/ide/separate-chrome-profile/) |
| 离开电脑后的通知 | 官方 Remote Control 可从手机浏览器查看会话、新建任务、审计划与 Artifact，并提供完成或需输入的浏览器推送 | 此能力是 2.0 / CLI 的参考，确切适用本机版本未核。手机端需核退后台、断网恢复、通知跳转和重复审批；客户端断线不应误报电脑任务停止 | [Remote Control](https://antigravity.google/docs/remote-control/) |

## 需要隔离的新增能力

2.19.1 之后的能力见版本表，列入“升级后 / 待版本核验”，不直接算当前手机缺口。官网当前 2.0 / CLI 的 Remote Control、自动化与新增插件能力，也需确认本机 UI 和网关暴露范围。

语音转写在本机 2.19.1 之前已有发布记录，作用是把声音变成可编辑输入，也能用于 Artifact 评论。它没有证明手机已有语音调用接口，更没有证明项目应支持语音通话或角色朗读。[官方 Changelog](https://antigravity.google/docs/changelog)、[2.0 Voice](https://antigravity.google/blog/google-io-2026-feature-deep-dive)、[Voice transcription](https://antigravity.google/docs/features#voice-transcription)

官网存在信息不同步。Artifact review 页面写两种策略，但基线前的发布记录已描述三种 Plan Review Policy；计划策略数量和后端枚举应由本机再确认。当前 Features 与首次发布的 Scheduled Tasks 示例模型也已不同，不能把历史固定模型写进手机产品逻辑。[Artifact review](https://antigravity.google/docs/artifact-review)、[官方 Changelog](https://antigravity.google/docs/changelog)、[当前 Features](https://antigravity.google/docs/features)

## 建议主代理优先核查的闭环

下面是产品优先级判断，不是官方要求。

1. 用户离开电脑后，手机能发现等待计划审阅、命令审批或澄清的问题，打开原会话，读懂当前动作，再答复并看到电脑继续运行。漏掉这条闭环，手机只能看聊天。
2. 审阅计划、截图、浏览器录制和真实文件 diff，评论能定位并回传，继续执行与回退有明确范围。这条闭环直接影响 UI 验收和用户对变更的判断。
3. 发送正确的电脑工作区上下文与真实支持的文件。图库、手机文件和电脑文件引用应分别说明并有预览，格式或模型不支持时在发送前解释。
4. 多会话、后台任务和子代理要展示真实身份、状态及等待操作，切换和重连不丢草稿、不跳阅读位置、不重复发送。
5. 再核模型思考等级、额度、命令发现、MCP 状态及安全设置。先使用本机能力，后考虑升级功能。

这些闭环的 UI / UX 验收应包含触控可读性、长内容阅读位置、加载与错误提示、返回后草稿、等待审批的突出反馈、打开与返回的连续动效，以及减少动态效果设置。它们应进入每个功能的验收条件，而不应排到“功能完成后再美化”。

## 本记录的核验限制

本机 2.19.1 二进制补充核验确认了子代理元数据和内部工具形态：会话摘要包含 `parentConversationId`、`rootConversationId`、`nestingDepth`、`subagentSpec` / `agentScript`；内部 `send_message` 工具使用 `Recipient` 和 `Message`，其中 Recipient 可为会话 ID 或 `@children`。当前只发现内部工具执行入口，没有发现可由 Android 安全调用的等价 HTTP/RPC，因此移动端只接入子代理查看和轨迹导航，不据此猜测定向消息请求。

普通文件契约也已由本机二进制与隔离真实会话交叉确认。`SendUserCascadeMessageRequest.GetMedia` 使用公共 `Media` 类型；有效 JSON 字段是 `mimeType`、`displayName` 和 base64 `inlineData`。纯文本探针发送成功后，`GetCascadeTrajectory` 读回相同 MIME 与名称，正文已被服务端写入 `.user_uploaded/uploaded_media_*.txt` 并改以 `uri` 表示。`ClientModelConfig.supportedMimeTypes` 是 MIME 能力的权威来源；没有发现独立 `files` 数组。两次隔离诊断会话均已删除。

电脑工作区引用契约已从同一本机 2.19.1 protobuf 描述符确认。`SendUserCascadeMessageRequest.items` 为 `TextOrScopeItem[]`，其 oneof 可以是 `text` 或 `item`；文件与目录分别使用 `ContextScopeItem.file` / `directory`，共同的 `PathScopeItem` 包含 `absoluteUri`、`workspaceUrisToRelativePaths`、`numFiles` 和 `numBytes`。桌面搜索 RPC 是 `GetMatchingContextScopeItems`，文件/目录枚举分别为 `CONTEXT_SCOPE_TYPE_FILE` 与 `CONTEXT_SCOPE_TYPE_DIRECTORY`；因此移动端应发送结构化 scope item，不能只在提示文字里拼接 `@路径`。

官方能力部分仅使用 Google 官方文档和官方发布说明，并已打开核查正文；协议边界另以本机二进制符号和仓库源码交叉确认。当前文档可能继续变化，引用用于证明官方能力，无法代替真实请求验证。本文未操作真实会话，未上传文件、发出审批、撤回代码、改桌面设置或启用官方远程控制。
