# 消息回退、置顶、归档与更多菜单核查

日期：2026-10-03；基线：`c2c32c5`。用户本轮要求先分析，不修改功能代码。

## 核查结论

| 能力 | 现有支持 | 所需工作 |
| --- | --- | --- |
| 消息 Undo to this point | Go 已有预览/执行路由；Android 已有方法和数据模型；桌面端同样回到所选用户消息之前 | 校正后端模型配置与参数/状态校验，再接用户消息入口、预览确认、草稿恢复和列表同步 |
| 置顶/取消置顶 | 本机原生 `annotations.pinned`，桌面通过 UpdateConversationAnnotations 更新 | Android 保留字段、接入操作、统一列表排序和选中反馈；无需另造本地置顶协议 |
| 归档/恢复 | 本机原生 `annotations.archived`，桌面使用同一更新接口 | Android 保留字段、过滤普通列表、增加已归档页与恢复操作 |
| 重命名/删除 | Android 已接入并通过上一轮测试 | 复用操作，支持已归档列表条目按其 ID 执行；不用先打开会话才能操作 |
| 更多菜单外观 | 目前为默认 Material DropdownMenu，仅文字两行 | 按用户图片调整为白色圆角浮层、标题与图标行、红色删除，保持现有页面风格 |

## 实际证据与边界

- 阅读 Android、Web、Go 调用链；没有执行真实归档、置顶、恢复、删除或回退，没有启动聊天任务、安装新包或重启 mgy。
- 读取本机已安装 Antigravity Electron 包，确认界面由其原生 LanguageServer 提供。只读取得该运行实例 `/main.js`，SHA-256：`47f36abaabd34f7d54a95df40d942b609c02db9f5c04d56b124a048571b9f16d`。副本仅放忽略目录 `android/design/.verification/feature-research/`，不提交第三方整个包。
- 本机原生 GetAllCascadeTrajectories 返回 200；69 条原始摘要中有 2 条归档、1 条置顶。原生 annotations 字段包括 title、pinned、archived、archivalStatusTimestamp、markedAsUnread、lastUserViewTime。计数属于原始摘要，不等于 Android 过滤后的顶层会话数量；不记录私密标题、ID 或令牌。
- 对一个非运行的真实顶层会话读取轨迹，按最后一个用户步骤的前一步调用原生 GetRevertPreview，返回 200，含 1 项文件变更预览。没有调用 RevertToCascadeStep。此证明原生预览可用，不代表真实网关执行回退已验收。
- 运行现成测试：`go test ./internal/proxy -run '^TestHandleCascadeRevert(Preview|Execute)_Success$' -count=1`，通过。它们使用模拟上游，仅覆盖预览转换和 step 0 → target -1 的基本执行请求，未证明实际文件恢复、模式选择或模型保持正确。
- 外部官方文档检索连接失败，结论依据本地源码、当前安装包和只读原生响应；没有依赖第三方帖子推测字段。

## 置顶与归档的协议

桌面端 `w0` 的操作与 `gjb` 更新链都使用：

```text
POST /api/exa.language_server_pb.LanguageServerService/UpdateConversationAnnotations
{
  "cascadeIds": ["目标会话 ID"],
  "annotations": { "pinned": true },
  "mergeAnnotations": true
}
```

- 取消置顶：`pinned: false`；归档：`archived: true`；恢复：`archived: false`。须显式发送 false，不能因为序列化默认值而省略。
- mergeAnnotations 保持 true，避免更新单个标记时丢掉标题、已读状态等其他注解。无需客户端猜测或手写 archivalStatusTimestamp。
- 网关 `handleUpdateConversationAnnotations` 转发原始请求体，虽然它的本地标题解析结构仅声明 title，其他字段没有因此被丢弃；列表处理同样保留原生 annotations。置顶、归档的基础能力不需要新增服务端存储或新路由。
- 桌面索引排除已归档的置顶项，但归档操作只写 archived，并未主动清除 pinned。恢复后可保留原置顶身份，移动端应跟随这一语义。
- 桌面归档还调用 ForceStopCascadeTree，故归档运行中的会话不只是隐藏一行。移动端实施时应先明确采用与桌面一致的停止行为，或只允许非运行会话归档；不能默默换成 CancelCascadeInvocation 并当作同一语义。
- 桌面恢复可能同时取消所属项目的归档。项目归档暂不在当前手机需求内，但不能让恢复后的会话在项目过滤中继续不可见。

Android 当前 `Annotations` 已有 archived，但没有 pinned；`ConversationItem.fromSummary` 未保留 archived，ApiClient 列表只按修改时间排序，没有排除归档。需要统一模型映射后，让首页最近、抽屉普通会话和项目分组都使用同一归档判断，避免只隐藏某一个入口。

## 回退的实际语义与后端缺口

- Android 已有 `getRevertPreview(cascadeId, stepIndex)` 与 `executeRevert(cascadeId, stepIndex, conversationOnly)`，对应 `/gateway/cascade/revert/preview` 与 `/gateway/cascade/revert/execute`；目前没有聊天页/ChatViewModel 调用。用户消息响应已有真实 stepIndex，不能用懒列表序号或分页位置代替。
- 桌面 `pab` 计算目标为用户消息步骤 `b - 1`；Go 也做同样转换。回退不是滚动到消息，而是撤掉所选用户消息及其后的轨迹，将该消息放回输入框，供修改后重新发送；桌面还恢复原附件和可恢复的评审评论。
- `conversationOnly: false` 的路径可能恢复工作区文件；true 只回退对话。桌面是否展示此开关受功能配置控制。桌面存在工作区快照恢复路径，预览文件清单不应被描述成任何模式下的完整影响清单。移动端需明确确认说明，不能默认悄悄执行文件回退。
- 桌面禁用 CLEARED 步骤、特定 Best of N 边界，以及无法恢复评论的特殊消息。现有移动模型只带步骤状态/正文和媒体，没有充分暴露全部 eligibility 信息。后端应返回可否回退与原因，至少服务端检查真实用户步骤、清理状态、特殊边界与当前执行状态，避免仅靠前端猜测。
- 两个 Go handler 当前只验证 cascadeId。stepIndex 缺省为 0，会被当成目标 -1；负数、超范围和可选 targetStepIndex override 也缺少对应语义校验。实施前应把缺失与有效的 0 区分开，再依据实际轨迹校验目标。
- `ExecuteRevert` 先读取会话配置，再用默认 MODEL_PLACEHOLDER_M318 / gemini-2.5-flash，或网关缓存的模型调用 applyModelToCascadeConfig。该方法覆盖 plannerConfig.planModel/requestedModel/modelName；桌面已有会话若没有网关模型缓存，可能被硬编码模型覆盖。应优先保留实际会话配置，缺失时按明确协议处理，不猜模型。
- 成功后现有 Go 已清轨迹/待发消息缓存并通知流监听。Android 仍须丢弃此会话的过时加载结果、重拉历史/状态、清理已撤掉的本地消息，再恢复原消息到草稿；失败保留原会话及草稿，不能先删除 UI 内容后无法找回。

## 菜单与归档页建议

- 当前默认菜单缺少标题、图标与参考图的留白和圆角，确实不符合这次目标。改为锚定右上更多按钮的原生浮层：白底、大圆角、浅阴影；顶部灰色会话标题，下方置顶/取消置顶、归档、删除三行，删除图标和文字均用红色。尺寸按三项内容收紧，不照搬截图里其他功能或其长菜单高度。
- 保留重命名能力但不增加一长串菜单项；建议标题区域旁提供小编辑入口，点击进入现有重命名流程。此为布局建议，尚未实现。
- 抽屉增加“已归档”入口，进入独立页面，支持搜索、空态、加载/失败与返回。每条会话可恢复、重命名、删除；恢复保留原项目归属，删除继续确认。操作绑定条目 ID，避免误操作当前打开的另一条会话。
- 截图里的 Conversation History 是历史入口，桌面代码另有归档过滤，并非其中每条都是已归档。手机可直接提供用户需要的已归档专页，不必复制桌面全部历史管理功能。
- 当前标题在 Android 与 Go 列表链路都有 36 字符/符号截断处理。菜单展示和重命名输入应区分完整原始标题与视觉省略，防止点开重命名后把原长标题写成截断文本。
- 置顶/归档/恢复/回退图标尚未出现在当前 Symbol 字形映射中。实施时核实可复用的官方原生图标资源；需要专门切图时再向用户索取，不用 CSS/SVG 手绘替代。

## 实施顺序

先做菜单与会话管理：字段保留 → 置顶/取消置顶 → 归档过滤与专页 → 恢复/重命名/删除 → 模拟器回归与桌面同步验收。

再做回退：后端目标校验与模型保持 → 可否回退信息 → 用户消息入口/预览确认 → 草稿及附件恢复 → 使用明确的临时测试会话验证真实执行。真实文件恢复应在隔离测试工作区完成，不能拿用户正在开发的会话当试验样本。

本轮只完成核查与方案记录，尚未修改上述 Android 或 Go 功能。
