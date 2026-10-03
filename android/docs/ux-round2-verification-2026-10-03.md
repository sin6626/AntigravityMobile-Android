# Android 第二轮 UX 实施与验收

日期：2026-10-03。基线：`409f573`。范围对应 [后续计划](ux-next-steps-2026-10-03.md) 的 2.1–2.4。

## 已实施

- 网络与加载：消息、分页、会话和项目分别显示失败与重试；刷新失败保留已有内容与草稿。连接提示区分连接中、断线和等待用户操作。WebSocket 连接恢复后重新同步当前会话；断线不把生成状态改成已完成。
- 异步保护：用连接代数排除已离开的连接回调，取消上一会话的消息读取；HTTP 快照落后于实时更新时保留最新正文与运行状态。重复重试不并发请求同一份消息；旧授权的列表结果不覆盖重新配对后的数据。HTTP/握手 401 回到配对，草稿保留。
- 路由保护：共享拦截器不把鉴权失效当成连接失败；只有已配置网关的同源请求才允许切换到云端，外部图片读取失败不改变网关地址。继续沿用结构化 `API_TRACE` 和敏感字段隐藏。
- 图片：聊天缩略图、Markdown 图片与文件图片复用查看组件；提供关闭、加载/失败/重试、双指缩放、拖动、缩放按钮与重置，多图切换恢复比例与位置。缩略图失败说明与下方重试分开，点击图片仍可放大。查看器保留原图尺寸与请求授权，长图的最大倍率依据实际尺寸计算。
- 搜索与列表：区分暂无数据和无匹配，支持清空搜索，抽屉标识当前会话。项目按 ID 和 URI/path 保持展开身份，过滤和重排后不串到其他项目。
- 点击与无障碍：添加/移除附件、发送、复制、回到最新、分页读取和顶部标签等主要操作达到 48dp；保持原有图标视觉尺寸。补选中、展开、发送/停止忙碌与禁用状态；语音提示明确文字与图片已支持、语音尚未开放。图片操作采用原有蓝色，缩放操作行可随字体换行。

未增加运行时依赖，未修改 Go 网关。第三轮动画与后续功能仍按计划排期。

## 实际验证

仅使用 `emulator-5554`，未安装或操作 USB 真机。原网关 `58900`、第一轮验收网关 `58901` 保持原进程运行；真实检查使用模拟器 `http://10.0.2.2:58901`。

| 检查 | 结果与证据 |
| --- | --- |
| Android 单测、Debug 主包与测试包构建 | 8 项单测通过；三个 Gradle 任务通过，结果在 `round2-build.log` 与应用 test-results 中 |
| 全套模拟器仪器回归 | 报告 `OK (24 tests)`，318.8 秒；19 项实际执行通过，5 项要求真实网关/路径参数的检查因参数缺省跳过，不计为真实链路通过 |
| 最终包检查 | 连接代数改用原子计数并重新构建后，重连/迟到快照、重新配对、真实断网恢复、真实文件预览共 4 项通过（85.207 秒），无跳过；最新 APK 已安装模拟器 |
| 网络恢复及并发 | 首次 500 后重试、WebSocket 真实连接关闭后重连、迟到 HTTP 不覆盖实时消息、切到 B 保留其草稿、HTTP/握手 401 保留草稿、列表失败保留内容与重复重试去重，针对性检查通过 |
| 路由与重新配对 | 401 和外部图片连接失败不访问候选云端或改网关地址；旧列表请求进行中重新配对仍发起新列表读取，检查通过 |
| 图片、搜索与触达 | 1.5 倍字体、三项系统动画比例设为 0：图片重试/双指缩放/拖动/重置/切换/关闭、搜索清空与项目重排、48dp 操作与发送忙碌禁用，共 3 项通过（20.873 秒） |
| 图片尺寸 | 测试图片含 200×400 竖图、800×200 横图与 100×20000 长图；长图放大到 1600%，拖动并重置通过。使用测试位图，不使用私人附件 |
| TalkBack | 启用模拟器原生 TalkBack；系统无障碍节点能找到“添加图片”，执行无障碍聚焦成功；触达、移除附件和发送禁用检查通过，1 项（7.046 秒） |
| 真实网关原图与网络 | 项目内临时 Markdown、PNG 经 App 文件预览读取成功，图片达到已加载；同一原图无授权请求返回 401。关闭 Wi-Fi/移动数据后草稿保留，恢复网络后同步成功；组合 2 项通过（15.904 秒） |

测试材料在项目内 Git 忽略的 `android/design/.verification/`。真实网络测试只创建并删除自己的空会话，不发送文字或启动 Agent；真实文件检查仅读取自建验收文件。网络、字体、动画和 TalkBack 设置均恢复原值。没有拿用户开发会话执行删除或回退。

## 复跑入口

本机 Gradle 9.7.1，`JAVA_HOME=G:\develop\Android Studio\jbr`，`GRADLE_USER_HOME` 指向项目内 `.gradle-user`。应用没有 Gradle wrapper，使用现有安装执行：

```text
gradle -p android :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
adb -s emulator-5554 install -r android/app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w com.antigravity.mobile.test/androidx.test.runner.AndroidJUnitRunner
```

构建失败必须停止安装；`am instrument` 的退出码不能单独作为成功依据，须检查报告的 `OK`、失败和跳过记录。测试包是单独的自动化辅助包，日常运行在 Android Studio 选择 `app` 主应用。

- `ChatUxTest.realEmulatorOfflineRecoveryKeepsTheDraft`：只在模拟器且参数 `round2RealNetwork=true` 时关闭网络；传 `uxGatewayUrl=http://10.0.2.2:58901`，保留已配对令牌。
- `ChatUxTest.composerActionsHaveAccessibleTargetsAndBusyStates`：普通运行检查 Compose 语义和点击尺寸；参数 `round2TalkBack=true` 才额外检查实际 TalkBack 服务与系统聚焦，运行前启用，结束后恢复原设置。
- `ReplyMediaAndCopyTest.actualFileLinksShowMarkdownAndLoadImageBytes`：运行时提供 `documentUri`、`imageUri`；本轮文档样例标题为“1. 项目简介”。不把用户私密文件路径写入仓库。
- 大字体与关闭动画：先保存 `font_scale`、`window_animation_scale`、`transition_animation_scale`、`animator_duration_scale`，设为 `1.5 / 0 / 0 / 0`；运行上述图片、搜索及触达检查，最后逐项还原。

## 限制与后续

- TalkBack 验证覆盖按钮识别与真实无障碍聚焦，未逐项人工试听所有页面朗读；本轮没有真机性能结论。
- 原图查看使用现有 Coil 与缓存，不增加分块解码；极大图片的内存压力没有做专项压力测试。搜索范围仍是已加载标题/项目名，未增加服务端全文搜索。
- 缺少 ID、URI 和 path 的项目只能回退名称；同名且无稳定身份的条目需要后端补标识。没有无依据地重写抽屉列表或引入新图片/动画库。
- 首次全套出现测试辅助服务器向已取消请求写入时的 `Broken pipe`，修正测试线程对正常断开的处理后重跑；不把它记录为业务服务故障。
- 第一轮真实对话/文件回退的证据沿用 [第一轮验收记录](conversation-management-research-2026-10-03.md)；本轮通过模拟接口回归这些功能，未再次触发真实文件回退。

原生手势与图片尺寸行为依据 [Compose 多点触控文档](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/multi-touch)、[Coil Compose 文档](https://coil-kt.github.io/coil/compose/)，实现使用项目已有的 Coil 版本，未升级依赖。
