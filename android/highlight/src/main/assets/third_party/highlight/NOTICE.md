# 来源与修改记录

本模块的 `core/`、`languages/`、`HighlightToken.kt` 和语言黄金测试来自
[RikkaHub](https://github.com/rikkahub/rikkahub)，固定提交
`2267943a32d9208769b1ee199f3186d26066d1a3`，保留原包名与源码。
该仓库许可证为 GNU AGPL v3，完整文本见本目录 `LICENSE`。

其语法规则与 mode-stack 引擎移植自 highlight.js 11.11.1；原项目的 BSD
3-Clause 许可证见 `LICENSE.highlight-js`，Copyright (c) 2006 Ivan Sagalaev。

本项目修改：仅提取不依赖 Compose 的引擎和语言定义，将原 `Highlighter.kt`
中的公开入口独立为 `CodeHighlighter.kt`；未移植上游 UI、4096 字符限制、
WebView、工具脚本及预览。后台调度、结果缓存与颜色映射由 Android 应用负责。
不得将此模块标为本项目原创或去掉来源与许可证。
