# Tree-sitter 原生代码高亮研究

日期：2026-10-03。范围：官方文档、绑定源码和 grammar 源码的只读研究；没有引入应用依赖，没有构建原型，也没有测量包体积、耗时或内存。

## 结论

Tree-sitter 可以作为本项目的候选高亮引擎：官方有支持 Android 的 Kotlin 绑定，查询可以给代码区间标记类型，再交给 Compose `AnnotatedString` 显示。它不要求 WebView；这条组合路线是基于两者接口的实现推断，尚未做本项目实测。[KTreeSitter 官方文档](https://tree-sitter.github.io/kotlin-tree-sitter/)、[QueryCursor](https://tree-sitter.github.io/kotlin-tree-sitter/-k-tree-sitter/io.github.treesitter.ktreesitter/-query-cursor/index.html)、[Compose 多样式文本](https://developer.android.com/develop/ui/compose/text/style-text#add-multiple-styles-in-text)

不能直接认定“很轻量”或比其他方案快。Tree-sitter 核心是 C11 增量解析器，但完整 Android 方案还包含 JNI 绑定、各语言的 parser/scanner、高亮查询、结果与文本缓存。核心特性不能替代最终 APK 与真机数据。[Tree-sitter 官方介绍](https://tree-sitter.github.io/)、[官方 Kotlin Android 构建](https://github.com/tree-sitter/kotlin-tree-sitter/blob/master/ktreesitter/build.gradle.kts)、[官方 Java grammar 构建示例](https://github.com/tree-sitter/kotlin-tree-sitter/blob/master/languages/java/build.gradle.kts)

## 1. Kotlin / Android 路线与集成成本

- 官方 `io.github.tree-sitter:ktreesitter` 绑定支持 Android、JVM 和 native。查阅时 API 文档标注 0.25.1；仓库 master 的 `gradle.properties` 仍标注 0.25.0，因此本次不把文档版本当成已经核验过的 Maven 可解析版本。实施前要固定实际发布版本并验证依赖解析。[官方安装说明](https://tree-sitter.github.io/kotlin-tree-sitter/)、[gradle.properties](https://github.com/tree-sitter/kotlin-tree-sitter/blob/master/gradle.properties)
- 官方 Android 构建通过 CMake / NDK 生成 native 库，ABI 配置为 `x86_64`、`arm64-v8a`、`armeabi-v7a`。这些是源码配置，不等于我们已经检查发布 AAR 内的 `.so`。[ktreesitter/build.gradle.kts](https://github.com/tree-sitter/kotlin-tree-sitter/blob/master/ktreesitter/build.gradle.kts)
- 语言需要独立 grammar。官方 Java 示例将 grammar 目录、名称和绑定类配置给 `ktreesitter-plugin`，编译 `parser.c`，存在 `scanner.c` 时也编译它；不是加入核心依赖就获得所有语言。[languages/java/build.gradle.kts](https://github.com/tree-sitter/kotlin-tree-sitter/blob/master/languages/java/build.gradle.kts)
- AndroidIDE 另有其项目维护的 Android Java 绑定及 Java / JSON / Kotlin / Python / XML 等 grammar 依赖。这是另一条现成路线，不能把它与 Tree-sitter 官方 Kotlin 绑定混成同一套依赖。[AndroidIDE android-tree-sitter 项目说明](https://github.com/AndroidIDEOfficial/android-tree-sitter)

## 2. 高亮查询、主题与 Compose

Tree-sitter 高亮采用 grammar 配套查询：`highlights.scm` 的捕获给节点分配 `keyword`、`string`、`comment` 等名称；locals 查询处理局部定义/引用，injections 查询处理嵌套语言。应用仍需选择配色和适配语言，不需要重写各语言的正则词法器。[官方高亮机制](https://tree-sitter.github.io/tree-sitter/3-syntax-highlighting.html)

例如 `fwcd/tree-sitter-kotlin` 包含 Kotlin 的 `highlights.scm`，实际用到 `#eq?`、`#any-of?` 等谓词，文件头还说明其查询来源与许可证。接入应使用同一 grammar 版本配套的查询，核对绑定对谓词的处理，保留查询来源与许可证，不能将所有捕获无条件着色。[Kotlin grammar 查询源码](https://github.com/fwcd/tree-sitter-kotlin/blob/main/queries/highlights.scm)、[Kotlin grammar 配置](https://github.com/fwcd/tree-sitter-kotlin/blob/main/tree-sitter.json)

推荐组合（设计建议，未实现）：

1. 后台解析原始代码，执行查询，得到带类型的代码区间。
2. 将区间转换为原文上的 `SpanStyle`，生成 `AnnotatedString`；代码内容保持逐字不变。
3. 继续使用原生 `Text` 和现有横向滚动、复制按钮；完成后的文本保持 `SelectionContainer`，按钮保持 `DisableSelection`。

Compose 原生支持同一文本中的不同样式，以及选择容器中的文本选择；因此高亮不必改成图片或网页。字体大小、行高和布局保持一致，仅改变文字颜色，可避免因着色而改变排版，这是本项目 UX 设计建议。[Compose 样式 API](https://developer.android.com/develop/ui/compose/text/style-text)、[Compose 选择 API](https://developer.android.com/develop/ui/compose/text/user-interactions)

### 字符偏移必须核实

KTreeSitter `Parser.parse(String)` 默认 UTF-8，而节点 `startByte/endByte` 是字节范围。不能直接把这些数当成 Compose 的字符串下标：代码里的中文注释、emoji、非 ASCII 字符会发生错位。实施应明确输入编码并验证区间转换，包含中文与代理对的样本必须检查；本次没有选择未经验证的转换捷径。[Parser API](https://tree-sitter.github.io/kotlin-tree-sitter/-k-tree-sitter/io.github.treesitter.ktreesitter/-parser/index.html)、[Node API](https://tree-sitter.github.io/kotlin-tree-sitter/-k-tree-sitter/io.github.treesitter.ktreesitter/-node/index.html)、[AnnotatedString API](https://developer.android.com/reference/kotlin/androidx/compose/ui/text/AnnotatedString)

## 3. 流式追加与增量解析

官方增量流程为：按照实际文本变更构造 `InputEdit`，先 `Tree.edit`，再向 `Parser.parse` 传入编辑后的旧树。Kotlin API 还提供 `changedRanges`。只保存旧树却不正确编辑它，不属于正确增量更新。[官方编辑流程](https://tree-sitter.github.io/tree-sitter/using-parsers/3-advanced-parsing.html#editing)、[Tree API](https://tree-sitter.github.io/kotlin-tree-sitter/-k-tree-sitter/io.github.treesitter.ktreesitter/-tree/index.html)

设计建议：先保留即时显示文本，高亮结果在后台生成，丢弃已过时结果；已完成代码块复用结果，不因列表重组再次解析。流式代码块合并更新后再着色，结束时做最终校正。语法树增量更新不会自动让 Compose 文本布局也变成增量，因此需要分别观察解析与布局成本；具体更新间隔要根据 UX / 真机数据决定。

不能假定“只高亮本次追加字符”总是正确。未闭合字符串/注释在后续输入后可能影响已有区域；语法树变化也不一定等于全部着色影响范围。官方查询还支持 locals 与嵌套语言，增量着色边界必须与实际语言查询共同验证。[官方高亮机制](https://tree-sitter.github.io/tree-sitter/3-syntax-highlighting.html)、[树编辑与复用规则](https://tree-sitter.github.io/tree-sitter/using-parsers/3-advanced-parsing.html)

## 4. 生命周期、线程与 ABI

- Android `Parser`、`Tree`、`Query`、`QueryCursor` 等对象具有 native 资源；API 标注 `AutoCloseable`，Parser / Tree 文档明确 SDK < 33 时必须 `use` / `close`。本项目实现建议显式管理生命周期，不把 native 对象散放在列表每个 Composable 的重组路径。[KTreeSitter 类型 API](https://tree-sitter.github.io/kotlin-tree-sitter/-k-tree-sitter/io.github.treesitter.ktreesitter/index.html)、[Android Parser 源码固定提交](https://github.com/tree-sitter/kotlin-tree-sitter/blob/ee7283285972ceb305f33685f9270fe69d1879bb/ktreesitter/src/androidMain/kotlin/io/github/treesitter/ktreesitter/Parser.kt)
- 官方说明同一个树实例不是线程安全的，跨线程并发使用应复制树。查询对象可以共享，查询 cursor 不可并发共享。建议单个后台任务拥有自己的 parser/tree/cursor，只将不可变的文本样式结果交给 UI。[官方并发规则](https://tree-sitter.github.io/tree-sitter/using-parsers/3-advanced-parsing.html#concurrency)、[官方 Query API](https://tree-sitter.github.io/tree-sitter/using-parsers/queries/4-api.html)
- Tree-sitter grammar ABI 与手机 CPU ABI 是两件事：grammar 由 CLI 生成，runtime 有支持的 grammar ABI 范围，通常向后兼容而不向前兼容。依赖 runtime、grammar、查询要成套固定，不随意混用最新版本。[官方 C API ABI 说明](https://github.com/tree-sitter/tree-sitter/blob/master/lib/include/tree_sitter/api.h)

## 5. UI / UX 优先的验收与待验证

以下为本项目的实施/验收建议，均未实测：

- 阅读：浅色/深色背景下关键字、字符串、注释清晰，不依靠颜色作为唯一语义；不改变行高、缩进、横向滚动位置。
- 交互：长按选中高亮仍明显；复制得到原始代码，保留缩进、中文、emoji、换行；现有蓝色对钩反馈继续使用。
- 流式：内容先显示，高亮更新不闪空白、不丢字符、不把滚动位置拉走；半个字符串、注释、未闭合围栏与修订内容均可回退。
- 性能：比较当前包与候选原型的 APK 增量、首次解析与后续追加耗时、滚动帧时间、常驻内存、退出预览后 native 资源释放；Android 手机 arm64 与模拟器 x86_64 均通过。
- 语言：先验证真实样本 Kotlin、XML、JSON 和当前项目常见语言；未知语言或解析失败保持可读纯文本，而非空白/错误页面。
- native 包：检查实际 AAR、最终 APK 的 ABI 与 `.so`，验证目标 Android 环境；不能仅以源码构建配置判定发布二进制兼容。

最终选择：Tree-sitter **技术上适配原生 Compose 高亮，可做候选原型**；是否作为正式方案，须结合 RikkaHub 实际实现、语言覆盖、打包维护成本和上述 UX 验收结果决定。本研究没有依据认定它是唯一方案或性能最优方案。

## 6. RikkaHub 实际实现对照

本次直接读取仓库源码，固定提交为 `2267943a32d9208769b1ee199f3186d26066d1a3`。以下事实只描述这个版本，不能推断其过去版本或未经测量的性能。

| 核查项 | 源码事实 | 本项目取舍建议 |
| --- | --- | --- |
| 引擎 | `CodeHighlighter` 使用 highlight.js 11.11.1 的纯 Kotlin grammar / mode-stack 引擎移植，没有使用 Tree-sitter。 | 学习原生输出及成熟语法规则，不能把它当成 Tree-sitter 的性能证据。 |
| 显示 | `CodeHighlightText` 将 token 转为 `AnnotatedString`，交给 Compose `Text`。 | 与我们现有原生代码块、选择及复制方式匹配，无须引入 WebView。 |
| 复用与调度 | 使用 `remember(code, language, colors, highlighter)`；高亮在该回调内同步执行。引擎另缓存已编译的语言规则。 | `remember` 可避免同输入重组重复计算，但不是后台任务或跨列表项缓存；我们需要防止首次高亮堵住 UI。 |
| 大代码限制 | 传入 `CodeHighlightText` 的字符串超过 4,096 字符便用纯文本；未知语言也回退纯文本。 | 可以借鉴可靠回退，不直接照搬阈值，让长代码失去高亮；具体工作预算须实测。 |

表中事实来源：[Highlighter.kt](https://github.com/rikkahub/rikkahub/blob/2267943a32d9208769b1ee199f3186d26066d1a3/highlight/src/main/java/me/rerere/highlight/Highlighter.kt)、[HighlightEngine.kt](https://github.com/rikkahub/rikkahub/blob/2267943a32d9208769b1ee199f3186d26066d1a3/highlight/src/main/java/me/rerere/highlight/core/HighlightEngine.kt)、[模块依赖](https://github.com/rikkahub/rikkahub/blob/2267943a32d9208769b1ee199f3186d26066d1a3/highlight/build.gradle.kts)。

代码块 UI 将语言与复制等操作放在独立顶部栏，普通代码区使用 `SelectionContainer`。默认布局让整个代码串经过高亮；开启自动换行且显示行号时，另一分支逐行调用高亮组件。代码自动折叠是设置项，折叠时仅传前 10 行给高亮组件，因此 4,096 字符限制针对实际传入内容，而非必然针对原始完整代码。[HighlightCodeBlock.kt](https://github.com/rikkahub/rikkahub/blob/2267943a32d9208769b1ee199f3186d26066d1a3/app/src/main/java/me/rerere/rikkahub/ui/components/richtext/HighlightCodeBlock.kt)

它的 HTML / SVG 预览和 Mermaid 分支另有 WebView；这些分支与普通代码高亮不是同一个实现，本项目不采用。普通 Markdown 解析管线存在 `flowOn(Dispatchers.Default)`，也不能据此声称 `CodeHighlightText` 的高亮在后台执行。[HighlightCodeBlock.kt](https://github.com/rikkahub/rikkahub/blob/2267943a32d9208769b1ee199f3186d26066d1a3/app/src/main/java/me/rerere/rikkahub/ui/components/richtext/HighlightCodeBlock.kt)、[Markdown.kt](https://github.com/rikkahub/rikkahub/blob/2267943a32d9208769b1ee199f3186d26066d1a3/app/src/main/java/me/rerere/rikkahub/ui/components/richtext/Markdown.kt)

## 7. 对本项目的建议顺序

以下是依据源码形成的实施建议，尚未实施：

1. 文件预览先按类型分流：Markdown 复用现有正文渲染，图片使用已有 raw 文件接口及 Coil，代码和未知文本保留原文。不能让图片字节进入文本预览。这两项与选择高亮引擎无关，不需要为了它们改后端。
2. 高亮接入现有原生 `CodeBlock`，语言栏、右上角复制、成功对钩、选区、横向滚动继续复用。高亮只改变文字颜色，不改变字号、行高、原文与按钮位置；不新增一套代码块 UI。
3. Tree-sitter 优先作为候选验证：取真实 Kotlin/XML/JSON 以及包含中文、emoji、多行注释、未闭合字符串的样本，验证 grammar、查询、坐标、手机/模拟器 ABI。比较首次可读时间、追加更新、滚动帧时间和包体积，再决定正式引擎。RikkaHub 的纯 Kotlin 路线作为维护成本的对照，不能凭名字判断谁更快。
4. 内容立即可读，后台高亮完成后更新颜色；合并流式更新并丢弃过时结果，完成块按内容及语言复用结果。首屏、滚动、选中与复制是验收条件，不能以“已接入库”替代 UX 验收。

本轮只增加研究记录，没有修改 Android 应用、Go 后端或运行中的 mgy，也没有做性能测试。

## 8. 用户确认后的实施结果（2026-10-03）

用户随后选择 RikkaHub 原生路线并授权实施。已在 `android/highlight/` 固定提取该版本引擎与语言规则，保留源码和许可证；应用复用既有代码块 UI，后台计算颜色区间并缓存结果，没有加入 Tree-sitter 或 WebView。超过 4,096 字符的代码不按上游 UI 阈值取消高亮，未知语言保留原文。流式正文仍采用原有增量文字展示，完成后渲染 Markdown 与代码高亮；本次不宣称实现 Tree-sitter 式增量解析。

Markdown 文件预览已复用正文渲染并按块懒加载，图片链接使用已有 raw 接口。高亮模块 36 项测试、应用 8 项单测和最终模拟器 6 项界面测试通过；USB 真机已安装最终 APK，并实际检查原会话的 Markdown、PNG 与彩色 Kotlin 代码。验证的是显示和交互正确性，尚未重新采集性能轨迹，不能据此宣称比 Tree-sitter 更快。实施记录详见根目录 `Agent.md`。
