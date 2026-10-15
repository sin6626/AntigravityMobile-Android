# Multigravity 品牌图标与开屏

2026-10-15，按用户要求通过内置 imagegen 实际生成。App 名称仍为 Multigravity，未改包名、配对信息或上游归属。

最终源图：`android/app/src/main/res/drawable-nodpi/multigravity_mark.png`，1254 × 1254、RGBA 真透明背景。原图直接复制到项目，没有用 CSS / SVG 代替生成资产，也没有重绘图标。色彩与现有青蓝紫回复动效呼应。

Android 26+ 的普通与圆形自适应图标共用这张图，通过资源 inset 保留安全区；Android 13+ 提供同一轮廓的 monochrome 层。系统启动图标也使用该源图。历史密度目录的旧 PNG 未删除，但受支持系统实际使用 anydpi 自适应图标资源。

## 开屏节奏

- 使用 AndroidX core-splashscreen 1.2.0 接续系统启动屏，兼容项目 minSdk 26；没有另建第二个 Splash Activity。
- 第一个应用画面准备好后，原位接过系统图标：760 ms 时间轴内轻微浮起 8 dp、最多放大 4.5%，一道高光经过图标轮廓，后半程与背景一起退场。
- 网络同步照常进行；首次历史富文本排版在开屏退场后开始，动画期间复用静态会话骨架，避免长历史的首次组合抢占退场动画帧。
- 重建页面、深链接、关闭系统动画时跳过自定义动效；从后台恢复原 Activity 不重播。中途进入后台时取消动画、移除覆盖层并释放临时位图。
- 图标帧复用真实系统图标位置和尺寸，高光只在源图 alpha 范围内绘制。

实现参考 [Android 启动屏文档](https://developer.android.com/develop/ui/views/launch/splash-screen) 与 [AndroidX Core 发布说明](https://developer.android.com/jetpack/androidx/releases/core)。

## 生图提示词（内置工具原文）

```text
Use case: logo-brand. Create ONE production-ready Android app icon foreground mark for Multigravity, a carefully redesigned mobile companion for an AI coding workspace. It must be a single isolated sculptural symbol on a genuinely transparent background, square canvas, no words, no letters, no watermark, no phone mockup, no icon grid, no rounded-square tile. Visual language must relate to the app's existing cyan-blue-violet flowing loop animation: a distinctive continuous folded ribbon, an elegant double arch with a subtle central crossover and upward lift, readable as a compact abstract M / connected orbit rather than a generic thin infinity sign. The ribbon has substantial width, clean silhouette and generous negative space, with a polished satin-glass surface (not shiny chrome), restrained translucent edges and precise soft highlights. Color travels from luminous turquoise at the left through saturated azure to violet at the right. Front-facing composition with slight sculptural depth, balanced but not mechanically mirrored. Make the mark unmistakably readable at 48 px. No particles, no sparkles, no floor, no external cast shadow, no extra decoration. The entire visible mark must fit inside the central 58 percent of the square canvas, centered horizontally and vertically, surrounded by real transparent padding for Android adaptive icon safe zone. Keep all details within that mark. Premium, calm, precise, sophisticated software brand, crisp anti-aliased edges.
```

工具生成结果的主要非透明区域约占画布宽度的 70%，因此通过 Android inset 进一步收进自适应图标安全区；没有把提示词期望当作生成结果实测值。

原生启动录像、逐帧图、构建和测试记录保存在项目忽略目录 `.verification/2026-10-15-brand-launch/`。录像可能包含恢复的真实聊天，不作为作品集公开视频素材。

## 验证结果

- Debug / AndroidTest 构建通过，最新 APK 保数据安装到 API 37 模拟器；SHA256：`0C13B2A9E3886EB7EED77EC6D903084A743E43BFE3CD299F8AAA399260024DF9`。
- 4 项不同的聚焦用例通过：图标接续/扫光/淡出/取消清理、系统关闭动画直接完成、开屏期间保持消息同步与草稿并在结束后展示历史、既有回复流光回归。实际系统动画比例测试后已恢复原设置及有效比例。
- 实际冷启动录像检查了图标裁切、背景、原位接续与退出过程。最初隔离组件通过，但真实长历史恢复使时间轴从约 0.42 跳到 0.99；针对性延后历史组合后，`exit-smooth-tail.png` 显示连续的退场透明度，`launch-smooth.mp4` 保留完整记录。当前调试日志已清除。
- 最近一次录屏条件下，`am start -W` 报告冷启动 TotalTime 3436 ms。这不是 760 ms 的动画时长，也不是性能保证；没有用模拟器数据宣称真机 60 fps。
- 从后台回到原任务未创建新 Activity；真实端点 healthz/messages/subagents 为 200、WebSocket 为 101，当前页面无连接错误。
- 未验证 Android 26～30 实机和真机性能，深链接及页面重建的跳过逻辑已实现，但本轮没有使用真实配对链接做写入测试。
