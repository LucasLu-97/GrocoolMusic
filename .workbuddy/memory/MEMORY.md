# Grocool Music — 项目长期记忆

## 项目定义
- 目标：UI 深度模仿 Apple Music、内容与功能完全移植「酷狗音乐概念版」的第三方在线音乐软件（披 Apple Music 外衣的酷狗概念版）。
- 平台顺序：先做出 Android 稳定可用版，再考虑 Windows / macOS 等平台。
- 分发：GitHub 公开仓库 + GitHub Actions 构建 + 测试版 Release；全程统一签名密钥，保证后续迭代覆盖安装即可。实机测试阶段需提醒用户进行有线 ADB 连接调试。
- 要求：安卓端语言/架构先进、低功耗低资源占用、源码可读性高；需预留后续新平台适配空间。

## 用户明确要求牢记的原则
- 酷狗与酷狗概念版的接口大体通用；以 EchoMusic 的真实调用实践为首要参考（它是实践者），比其他项目更靠谱、更能避免踩坑。
- 精确补充（2026-11-11 调研 KuGouMusicApi README）：接口服务可用 `platform=lite` 切换概念版，但**不同平台的 token 不通用**；部分接口有 lite 专属分支（如搜索适配、概念版免费听、音乐 URL 的 Auth 版本）。

## 技术基线（2026-11-11 调研）
- BitChord：Kotlin + Jetpack Compose + Media3 的 Android 原生应用，含 KMP / Compose Multiplatform 的 `shared`、`sharedUi` 模块，GPL-3.0。UI 与 YouTube Music 数据深度耦合；`MusicSource` 接口仅 `health/search/stream`，源码注释明确首页推荐、歌单、电台等「归 YouTube 独有」。
- EchoMusic：Electron 43 + Vue 3 + TS + Rust NAPI 的桌面端（Win/mac/Linux），GPL-3.0；酷狗 API 直接以 git submodule 引入 `https://github.com/MakcRe/KuGouMusicApi.git`（MIT，132+ 接口），自身含酷狗人机验证流程与免费听 Auth 处理。
- 关键结论：两个 GPL-3.0 项目合并后，成品必须 GPL-3.0 开源、保留双方版权声明并标注修改；「换引擎」不是 1:1 替换，需新建数据适配层重写 Repository 与 UI 数据装配。

## 最终目标（2026-11-11 用户明确锁定）
- 内容与功能**完全移植酷狗音乐概念版** + 界面像 Apple Music。不接受「只搜能放 + 换个皮」的降级范围，中间数据适配层必须新建。
- 客观范围基线：KuGouMusicApi 共 236 个 `module/` 接口实现（登录 15、用户 31、概念版 youth/lite 专属 24、歌单 16、评论 12、歌手 10、歌曲 10、搜索 7、榜单 11、FM 4、云盘 5、长音频 9 等）；EchoMusic 侧 17 个 `renderer/api` 业务模块。
- 待用户确认：「完全移植」指功能等价还是逐项照搬（听歌识曲 / DLNA / 插件系统 / 桌面歌词 / Rust 音效引擎等属桌面形态专属）。

## 当前阶段
- 可行性探讨收口中。用户明确说「先不着急动手做」，**尚未创建任何项目代码**。
- 已定实施主线：M0 协议验证 → M1 播放闭环 → M2 内容层 → M3 账号与个性化 → M4 完整功能与打磨 → M5 桌面端；以里程碑完成标志推进，不按时间估工。
- 后续候选产出：① 酷狗 API 契约/接口手册（面向 Kotlin）② 全量功能对照表 ③ 架构与仓库/CI/签名方案 ④ M0 验证工程。
