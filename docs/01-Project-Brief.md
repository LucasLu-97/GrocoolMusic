# Grocool Music 项目需求说明（修订版）

> 本文由项目发起人最初的需求描述修订润色而来，已依据 2026-11-11 对三个上游仓库的源码调研与讨论结论校正事实、补齐约束。**供其他 AI 或开发 agent 作为需求基线阅读**，请以本文为准，不必重新推导文中已确认的结论。
> 当前仍处于可行性探讨收口阶段，**尚未创建任何项目代码**。

---

## 一、项目是什么

我要做一个名为 **Grocool Music** 的第三方在线音乐软件：**界面深度模仿 Apple Music，内容与功能完全移植「酷狗音乐概念版」**。直观地说，就是一个披着 Apple Music 外衣的第三方酷狗概念版音乐客户端。

「完全移植」的定义已明确为**功能等价，而非逐项照搬**：酷狗概念版在手机上能做的事，本项目要提供等价能力；但桌面端形态专属的功能（听歌识曲、DLNA / AirPlay 投放、插件系统、桌面歌词、系统托盘、Rust 音效引擎等），在安卓上用等价能力替代或直接不做，不要求照搬同一套实现。

平台顺序：**先把 Android 版做到稳定可用，再考虑 Windows / macOS**。安卓端要求语言与架构先进、低功耗低资源占用、源码可读性高，并预留后续新平台适配空间。

分发方式：建一个 **GitHub 公开仓库**，采用 **GitHub Actions** 构建，先发布**测试版 Release**；**全程使用统一签名密钥**，保证后续迭代直接覆盖安装即可。需要手机实机参与测试时，请提醒我进行**有线 ADB 连接调试**。

---

## 二、主要修正摘要

原描述中的若干技术判断在讨论后被证伪或需要精确化，阅读时请以下表为准：

| 原设想 | 修订后的事实 |
| --- | --- |
| EchoMusic 自己实现了酷狗的全部 API | EchoMusic 的酷狗 API 来自 `MakcRe/KuGouMusicApi`（MIT）的 git submodule，它本身是 Electron 桌面端，不是引擎库 |
| 把 API 抽象成一份静态「接口文档手册」即可 | 应产出面向 Kotlin / Android 的《酷狗 API 契约》：接口真源 + 公共签名层规格 + 风控 / 免费听 Auth 处理 |
| BitChord 是一层可剥离的「UI 外衣」 | BitChord 是 Kotlin + Compose + Media3 的 Android 原生应用，UI 与 YouTube Music 数据深度耦合，且自带完整播放引擎 |
| 外壳 + 引擎整合，项目即可诞生 | 必须**新增数据适配层**：其可插拔音源接口只覆盖 `health / search / stream`，首页、推荐、歌单、电台等内容功能全部绑定 YouTube |
| 功能大致 1:1 替换 | 分四类：近似 1:1、需移植适配、国内不可用需剔除、桌面形态需功能等价替代 |
| 隔一个月同步一次上游外观 | 按上游 tag 挑选同步 + 保留 upstream 分支 + 改动隔离在适配层；不做全量月度合并 |
| 酷狗与概念版接口通用 | 大体通用（同一服务用 `platform=lite` 切换），但**不同平台 token 不通用**，部分接口有 lite 专属分支，概念版「免费听」需独立 Auth 链路 |

---

## 三、修正后的八点设想

**1、关于「引擎」——酷狗能力从哪来。**
GitHub 上的 [EchoMusic](https://github.com/hoowhoami/EchoMusic) 是一个很成功的第三方酷狗概念版客户端，几乎覆盖了概念版的主要功能，是本项目最值得信赖的**实践者**。但它并非自研协议：它通过 `.gitmodules` 直接引入 [KuGouMusicApi](https://github.com/MakcRe/KuGouMusicApi)（Node.js，MIT，README 列 132 个接口、`module/` 下实际有 236 个接口实现文件）作为本地服务。因此本项目的做法不是从零「抽象一份接口文档」，而是产出一份**《酷狗 API 契约（Kotlin 版）》**：以 KuGouMusicApi 的接口实现为**接口真源**，以 EchoMusic 的 `src/renderer/api/*.ts`（17 个业务模块）与 `kugouVerification.ts` 为**调用实践真源**，覆盖公共签名层、参数拼装、token 生命周期、人机验证、概念版免费听 Auth、音质免 VIP 判断。这才相当于汽车的引擎与核心配件，而且能直接装进安卓。

**2、关于「外壳」——Apple Music 风格界面从哪来。**
[BitChord](https://github.com/kushagrasinghx/BitChord) 是一个很成功的 Apple Music 风格客户端，界面与动画完成度很高。但它是 **Kotlin + Jetpack Compose + Media3 的 Android 原生应用**（不是 UI 组件库），还包含 KMP / Compose Multiplatform 的 `shared`、`sharedUi` 模块。本项目以它当前版本为 UI 基线，取用的是外观、交互、播放器界面与播放引擎；同时必须裁掉与 YouTube Music / Google / Spotify 等国际服务绑定的数据逻辑与依赖。这相当于汽车的外壳与内饰，外加一套现成的底盘（Media3 播放层）。

**3、关于「整合」——不是 1:1 拼装。**
BitChord 的 `MusicSource` 接口只有 `health() / search() / stream()` 三个方法，源码注释明确写着首页推荐、相关推荐、电台「归 YouTube 独有」。也就是说换个音源只能做到「能搜能放」，酷狗概念版的内容功能一个都覆盖不了。因此本项目需要在外壳与引擎之间**新建一层数据适配层**：把酷狗接口收敛成统一领域模型，再喂给 BitChord 的界面。这是工程主体，也是原设想里唯一缺失的一层。

**4、关于「持续同步外观」。**
BitChord 迭代极快（建仓三个月、六千余 star、上千文件、数百个 open issue）。**不做全量月度合并**：仓库保留 `upstream/bitchord` 分支，按上游 tag 挑选版本同步，只合并 `sharedUi/` 与 `app/ui/` 相关目录，所有自有逻辑严格隔离在 `data/kugou/` 与 `adapter/`，同步时做 diff 评审。这样本项目能持续保持较新的外观，又不至于每次同步都撕裂数据层。

**5、关于「功能对照」。**
两项目都属于主流在线音乐软件，但功能并非约等于 1:1 替换，需按四类处理：① 近似 1:1（搜索、播放页、队列、收藏、均衡器、睡眠定时、主题、倍速、离线下载等，换数据绑定即可）；② 需移植适配（首页推荐、每日推荐、榜单、歌手专辑详情、私人 FM、评论、登录、歌词、MV、无损音质、动态封面，需重写数据装配）；③ 国内不可用需剔除（Google 登录、Spotify、Discord、Last.fm / ListenBrainz、Google Cast、Wear OS、Listen Together 官方服务、AddonAuth 官方 token、YouTube / Apple / Spotify 元数据）；④ 桌面形态专属需功能等价替代（音乐云盘、听歌识曲、DLNA / AirPlay、插件系统、音效引擎、实时频谱、桌面歌词、系统托盘）。这一点在国内网络环境下尤其重要：**凡是依赖国际网络服务的功能，默认不可复用**。

**6、关于「平台与技术栈」。**
先 Android、后 Windows / macOS，这个顺序不变。安卓端采用 Kotlin + Jetpack Compose + Media3 + Ktor / OkHttp + kotlinx.serialization + Room + Coil，删掉上游的 Rhino / QuickJS / ONNX / NewPipe / FFmpeg native / Google Play Services 等重依赖，以满足低功耗、低资源占用与源码可读性。BitChord 已有的 `shared` / `sharedUi` 为后续桌面适配留了口子；另一条更省的路是桌面端直接复用 EchoMusic（Electron + Vue 3）换皮，两端共享同一份《酷狗 API 契约》而非共享 UI 代码——该选型留待 M5 决定。

**7、一个必须牢记的原则。**
很多接口在酷狗与酷狗概念版之间是通用的，**EchoMusic 是真正的实践者，看它如何调用 API 比参考其他项目更靠谱、更能避免不必要的踩坑尝试**。精确补充：接口服务可用 `platform=lite` 切换概念版，但**不同平台的 token 不通用**；部分接口有 lite 专属分支（如搜索适配、概念版免费听、音乐 URL 的 Auth 版本）；概念版「免费听」要走独立的 Auth 链路，且存在人机验证流程——这些细节只存在于 EchoMusic 的调用链里，静态接口文档没有。后续开发一律以实践者的调用链为准。

**8、最终目标。**
一个**披着 Apple Music 外表的酷狗概念版音乐软件**：内容与功能完全移植酷狗概念版（功能等价），界面像 Apple Music。不接受「只搜能放 + 换个皮」的降级范围。

---

## 四、经讨论确认的关键结论

- **目标锁定**：上述第 8 点，即「完全移植 + Apple Music 外观」，范围不降级。
- **范围基线**：以 KuGouMusicApi 的 `module/` 目录（236 个实现文件）为客观边界，README 的 132 条只是文档子集；其中概念版 `youth / lite / auth` 专属接口 24 个（免费听、青春会员、频道社区）。
- **架构**：`KugouApi`（协议）→ `KugouRepository`（领域模型）→ `adapter/`（隔离带）→ BitChord UI 与 Media3 播放层。播放引擎能力（队列、均衡器、Automix、逐字歌词渲染、离线下载）与音源无关，原样保留。
- **里程碑**：M0 协议验证 → M1 播放闭环 → M2 内容层 → M3 账号与个性化 → M4 完整功能与打磨 → M5 桌面端，按完成标志验收，不按时间估工。
- **合规**：BitChord 与 EchoMusic 均为 **GPL-3.0**，KuGouMusicApi 为 **MIT**。合并后的成品必须 GPL-3.0 开源（公开仓库正好满足），需保留双方版权声明、标注修改、提供完整构建源码；Apple 外观不 1:1 复刻图标与商标；沿用「仅供学习研究、非商业、不托管音频」免责声明。
- **当前阶段**：可行性探讨已收口，**尚未开始编码**。下一步是 M0：在国内真机网络下跑通「搜索 → 取流 → 播放」。

---

## 五、给阅读本需求的 AI / agent

1. 本文中的仓库技术栈、许可证、接口规模、源码结构均为已核实事实，请勿重新推导或直接套用训练数据中的旧印象。
2. 不要因为「实现更简单」而把范围降级为「能搜能放 + 换皮」；发起人在讨论中已明确拒绝该降级。
3. 不要把国际服务（Google / YouTube / Spotify / Discord / Last.fm / Cast / Wear OS）当作可复用能力，国内环境默认不可用。
4. 不要提交任何签名密钥、token 或账号凭据到公开仓库；统一签名密钥走 GitHub Secrets。
5. 需要实机验证时，主动提醒发起人进行有线 ADB 连接。
6. 若发现事实与本文冲突，以三个上游仓库当前源码为准，并显式说明差异。
