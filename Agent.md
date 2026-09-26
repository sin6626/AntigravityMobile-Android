# Antigravity Mobile (Multigravity) 项目开发与维护文档

## 1. 项目概览
- **项目名称**：Multigravity (`antigravity-mobile`)
- **定位**：Google Antigravity AI Agent 移动端与全栈端到端协同系统（Android / iOS / Web / Go 本地网关）。
- **当前重点任务**：Android 客户端前端 UI 二改与视觉/体验升级重构。

## 2. 技术栈架构 (Android 端)
- **开发语言**：Kotlin 1.9+
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
├── ui/
│   ├── components/    # 通用组件 (MessageBubble, QuotaStatusBar, SettingsSheet 等)
│   ├── screen/        # 页面 (PairingScreen, ConversationListScreen, ChatScreen)
│   ├── theme/         # 样式与主题 (Color, Theme, Type)
│   ├── util/          # 工具类 (HapticUtils 等)
│   └── viewmodel/     # ViewModel (ChatViewModel, ConversationListViewModel 等)
```

## 4. 当前 Android 二改工作记录（2026-09-26）

- 工作分支：`codex/android-ui-redesign`，从初始提交 `78b6a16` 创建。
- 范围：只改 `android/`；保留 Go 网关、iOS、Web 的现有行为。
- 已确认：Android 使用 Jetpack Compose，主要入口为配对页、会话列表页、聊天页；现有界面大量采用 iOS 色板及布局。
- 构建环境：本机有 Android SDK、Java 和 adb；仓库未包含 Gradle Wrapper 启动脚本及 jar，当前没有可用 AVD。
- 待完成：确定目标视觉方向后统一设计系统，改造主页面与弹层，补齐构建与界面验证。

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
