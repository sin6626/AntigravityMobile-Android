# 2026-10-16 聊天与文件阅读 UI

## 图片与消息

图片独立放在用户消息气泡上方，右对齐，图片与气泡间距 8 dp；普通文件和文字继续留在蓝色气泡内。只有图片时不绘制空气泡。继续复用 MessageImages 和现有图片查看器，本地预览、回执合并、分页及缩放协议不变。

## 回复下方的代码变更

最新完成的回复正文与复制按钮之间显示会话变更汇总，使用 14 dp 圆角、1 dp 细边框和低对比底色，增删行用绿色/红色区分。整条可点，进入已有全屏审阅页。窄屏、大字号时统计自然换行，右侧审阅入口独立保留。

数据来自已有 `GET /gateway/cascade/diffs?cascadeId=...`，后端返回 `originalContent`、`modifiedContent` 和 `unavailableUris`。复用审阅页 diff 算法计算增删行，后台运行；不新增接口、依赖或请求类型。该协议返回会话累计变更，因此标注「当前会话变更」，只挂在最新回复末尾，不把累计数据复制到每条历史回复。沿用既有 diff 算法对超大差异的降级行为。

加载历史完成及回复结束时查询，生成期间不逐字请求；无变更时隐藏，不可用文件显示提示。切换会话时清理汇总，继续沿用 ViewModel 对迟到响应的会话校验。没有改变审阅评论及提交协议。

## 文件阅读

- 默认开启自动换行，关闭后代码与纯文本可左右滚动。Markdown 里的代码块同步开关，文档正文保持自然换行。
- 换行只改变显示，复制仍保留原文。关闭/复制、换行和重试入口至少 48 dp。
- 错误态包含稳定标题、可单独滚动的错误详情与独立的「重新加载」按钮；长错误不再把按钮挤出可见区域，重试在原弹窗完成。
- 保存、分享、系统打开及方案定位评论继续复用已有实现。

## 验证

- Gradle 9.7.1：Debug 与 AndroidTest 构建通过；`ConversationDiffSummaryTest`、`DiffReviewTest`、`FilePreviewAndHighlightTest` 共 5 项 JVM 检查通过。
- API 37 模拟器，共 11 项不同 UI 用例最终通过：`FileReadingLayoutTest` 3 项、`LocalImagePreviewTest` 1 项、`ChatUxTest` 6 项、回复等待衔接 1 项。
- 首轮 11 项中 9 项通过；图片查看/方案评论两项旧测试仍找已替换的「关闭」文字，改为现有关闭图标语义后，与最终错误态调整一起复跑 4 项，全部通过，15.698 秒。
- 实际检查了回复下方变更条、图片与气泡的分离、代码换行、错误重试及窄屏大字号截图。测试数据来自隔离网关，未向真实聊天发消息。
- 最终 APK：`android/app/build/outputs/apk/debug/app-debug.apk`，SHA256 `EC1CE0C9062CA751543D8BB9721DC7BF9E7C6AF82B1E0ECC4B3AA54E0DEC4B35`，已保数据安装模拟器。

证据位于项目忽略目录 `.verification/2026-10-16-reading-ui/`；初次构建与 JVM 日志分别在 `.verification/ui-polish-build.log`、`android/app/build/test-results/testDebugUnitTest/`。

真机未连接，未验实机表现。自动审批拒绝恢复真实应用与复查网关的组合命令，仅给出 `blocked by policy`，故不声明真实 HTTP/WS 最终检查通过；已有截图为隔离测试截图，短错误文本最终居中对齐之后没有回取新版截图。没有推送到远端。
