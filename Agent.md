# Antigravity Mobile (Multigravity) 项目开发与维护文档

## 1. 项目概览
- **项目名称**：Multigravity (`antigravity-mobile`)
- **定位**：Google Antigravity AI Agent 移动端与全栈端到端协同系统（Android / iOS / Web / Go 本地网关）。
- **当前重点任务**：Android 客户端前端 UI 二改与视觉/体验升级重构。

## 2. 技术栈架构 (Android 端)
- **开发语言**：Kotlin 2.2.10
- **UI 框架**：Jetpack Compose + Material 3
- **异步与流式处理**：Kotlin Coroutines + StateFlow + OkHttp (WebSocket / HTTP)
- **数据持久化与安全**：`EncryptedSharedPreferences` + Local Cache
- **图表与渲染**：Mermaid WebView, Markdown 渲染, Coil 图片加载与手势大图查看器

## 3. 核心目录结构
```
android/app/src/main/java/com/antigravity/mobile/
├── MainActivity.kt
├── data/
│   ├── model/         # 数据模型 (Conversation, Interaction, CockpitQuota 等)
│   └── service/       # 服务与网络层 (ApiClient, StreamWebSocketClient, CacheManager 等)
├── ui/chat/            # 配对、聊天状态、交互请求
└── ui/demo/            # 截图风格的聊天页面、顶部栏、输入栏、抽屉、消息内容
```

## 4. 当前 Android 二改工作记录（2026-09-26）

- 工作分支：`main`。用户明确要求取消 `codex/` 前缀分支；当前项目从提交 `348652c` 起只追踪 Android 端文件。
- 范围：只改 `android/`；保留 Go 网关、iOS、Web 的现有行为。
- 已确认：Android 使用 Jetpack Compose，主要入口为配对页、会话列表页、聊天页；现有界面大量采用 iOS 色板及布局。
- 构建环境：Android Studio 已成功导入项目，本机有 Android SDK、Java 和 adb。
- 设计基准：用户提供三张 ChatGPT Android 手机截图，分别为空白聊天页、左侧抽屉和已有对话页；详见 `android/design/android-ui-reference.md`。旧版卡片式设计稿已被用户否定，不再作为实现依据。
- 已实现：Kotlin + Compose 三状态 UI，接入 `mgy` 配对、真实会话列表与搜索、新建纯文字会话、消息发送与历史分页、WebSocket 实时更新、单选交互确认、会话删除。接口日志在 `API_TRACE` 中输出结构化 JSON，并隐藏令牌与配对码。图标用 Material Symbols 精简字体；Compose BOM `2026.09.00`。
- 已验证：Gradle `:app:assembleDebug` 成功，APK 已安装到 `emulator-5554`；真实网关配对、会话列表、历史消息、新建测试会话、连续两轮发送与 AI 回复、删除测试会话均已走通。截图留在 `android/design/.verification/`，由 Git 忽略，其中包含私人会话内容，不得对外展示。
- 新会话必须指定模型；Android 默认传入 Go 网关明确支持的 `gemini-3.8-flash-high`。之前省略模型时网关返回 `plan model not specified`；补齐后已实测两轮 AI 正常回复。
- 本轮范围之外：`工作`、语音、通话、附件、远程控制。Go 的交互接口对多选问题仅提供单个 `optionId`，Android 上提示使用桌面端处理。

## 5. 开发与规范准则
1. **严格遵守用户规范**：
   - 严禁批量删除文件；
   - 接口调用须明确类型与字段，缺少细节暂停并确认；
   - 前端网络请求拦截层保留完整日志打印；
   - UI 严格按规范还原，涉及图片资源直接向用户索取切图，禁止随意手绘替代；
   - Git 提交严格遵循 Conventional Commits 规范，提交信息使用中文；
   - 严禁在 C 盘根目录或临时路径生成临时文件，工作树/临时文件统一置于项目根目录内。
2. **UI 改造方向**：
   - 针对当前前端界面进行现代化重构、布局精细化与动效视觉提升。
