# Grocool Music 实施规格与任务分解

> **文档定位**：本文是「内容与功能完全移植酷狗概念版 + 界面像 Apple Music」这一最终目标的**可执行实施规格**，供把开发任务拆派给多个 AI agent 时使用。
> **事实基线日期**：2026-11-11。文中仓库版本、许可证、文件规模、源码路径均为当日核实结果。
> **配套文档**：`01-Project-Brief.md`（需求基线）。两者冲突时，需求文档定义「做什么」，本文定义「怎么做」。
> **当前状态**：尚未创建任何项目代码。本文不含任何已实现内容的声明。

---

## 0. 如何使用本文

- 每个工作包（WP-0 ~ WP-8）是自包含的任务单元，含输入、输出、依赖、验收标准、禁止事项，可直接指派给单个 agent。
- 模块之间的**接口契约**（第 4 节）是硬约束：不同 agent 并行开发时，只允许依赖契约，不允许依赖对方的实现细节。
- 附录 A 是 132 条对外文档接口的完整清单；附录 B 是关键源码证据；附录 C 是术语与链接。
- 任何 agent 若发现上游源码与本文冲突，**以当前源码为准并显式上报差异**，不得静默改写规格。

---

## 1. 目标、范围与非目标

### 1.1 目标

在 Android 上交付一个第三方在线音乐客户端：**界面深度模仿 Apple Music，内容与功能完全移植酷狗音乐概念版**；后续扩展到 Windows / macOS。

### 1.2 「完全移植」的判定标准（已由发起人确认）

**功能等价，而非逐项照搬**。判定规则：

| 情形 | 处理 |
| --- | --- |
| 酷狗概念版手机端已有的能力 | 必须提供等价能力（可用不同接口组合实现） |
| 桌面端形态专属能力 | 用安卓等价能力替代，或明确不做；不要求同实现 |
| 依赖国际网络服务的能力 | 剔除或自建，默认不可复用 |
| 上游 BitChord 的播放引擎能力 | 与音源无关，原样保留 |

### 1.3 客观范围基线

- KuGouMusicApi：README 对外列 **132 条**接口；`module/` 下实际有 **236 个** `.js` 接口实现文件。**排期以 236 个实现文件为准，132 条只是文档子集**——存在未文档化但可用的接口（例如 `user_cloud*` 云盘系列、`effects_*`、`sheet_*`）。
- 接口功能域分布（`module/` 文件名前缀统计）：`user` 31、`youth` 17、`playlist` 16、`login` 15、`comment` 12、`artist` 10、`song` 10、`longaudio` 9、`scene` 8、`search` 7、`effects` 6、`sheet` 6、`team` 6、`top` 6、`album` 5、`audio` 5、`ip` 5、`listen` 5、`rank` 5、`video` 5、`everyday` 4、`fm` 4、`theme` 4 等。
- **概念版专属接口 24 个**：`privilege_lite`、`song_auth`、`song_url_auth`、`song_url_auth_merge`、`top_card_youth`、`video_privilege`、`youth_channel_all / amway / detail / similar / song / song_detail / song_list / sub`、`youth_day_vip`、`youth_day_vip_upgrade`、`youth_dynamic`、`youth_dynamic_recent`、`youth_listen_song`、`youth_month_vip_record`、`youth_union_vip`、`youth_user_song`、`youth_vip`、`login_qr_authorize` 等。这是「概念版」区别于普通酷狗的核心（免费听、青春会员、频道社区）。
- EchoMusic 侧业务模块：`src/renderer/api/` 17 个（`album / artist / audioEffect / blacklist / channel / comment / external / importPlaylist / listenTogether / listeningPreferences / music / playlist / purchased / recognize / search / user / video`）。

### 1.4 明确非目标

- 不做 YouTube Music / Google / Spotify / Discord / Last.fm 等国际服务接入。
- 不把范围降级为「搜索 + 播放 + 换皮」。
- 不在安卓主线中引入 Node.js 运行时或 Electron（违背低功耗与可读性目标）。
- 不逐项照搬桌面专属功能（听歌识曲、DLNA / AirPlay、插件系统、系统托盘、全局快捷键、桌面歌词、Rust DSP 音效引擎）。
- 不做全量月度上游合并。

---

## 2. 上游版本基线（锁定）

| 项目 | URL | 锁定版本 | Commit / 日期 | License | 本项目中的角色 |
| --- | --- | --- | --- | --- | --- |
| BitChord | `https://github.com/kushagrasinghx/BitChord` | `v1.9.1` | `acf219a7e4a7` / 2026-11-06 | **GPL-3.0** | UI 基线、播放层、工程骨架 |
| EchoMusic | `https://github.com/hoowhoami/EchoMusic` | `v2.3.3-beta.1`（稳定 `v2.3.2`） | `f3c6964512f2` / 2026-11-09 | **GPL-3.0** | 酷狗调用实践真源、人机验证流程、M5 桌面端候选 |
| KuGouMusicApi | `https://github.com/MakcRe/KuGouMusicApi` | main | `7ac64e1cdd33` / 2026-11-04 | **MIT** | 接口真源、签名算法、lite 分支 |

所有 agent 在 M0 前必须**记录并固定这三个 commit**，后续同步写进 `docs/UPSTREAM.md`。禁止在开发中期无记录地切换上游版本。

---

## 3. 技术栈与仓库结构

### 3.1 技术栈（安卓）

| 层 | 选型 |
| --- | --- |
| 语言 | Kotlin（Java 17 / JVM_17），协程 + Flow |
| UI | Jetpack Compose + Material 3（沿用上游 BOM 与 `sharedUi`） |
| 播放 | Media3 / ExoPlayer + MediaSession（原样保留上游实现） |
| 网络 | Ktor Client（OkHttp engine）或 OkHttp；`kotlinx.serialization` |
| 存储 | Room（本地库、收藏、历史、设置），`androidx.security:security-crypto`（token） |
| 图片 | Coil 3 |
| 构建 | Gradle Kotlin DSL，`versionCatalog` 管理依赖 |
| 最低系统 | minSdk 26 / targetSdk 36 / compileSdk 37（与上游一致） |

**必须删除或剔除的上游依赖**：`innertubex-android`、`NewPipeExtractor`、`rhino` / `rhino-engine`、`quickjs-kt-android`、`onnxruntime-android`、`play-services-cast-framework`、`play-services-wearable`、`wear-remote-interactions`、`libadb-android`、`smbj`、`conscrypt`、FFmpeg native 扩展、`Spotify` / `Discord` / `Last.fm` 相关模块。删除前先确认 `Media3` 播放链不依赖它们。

### 3.2 建议目录结构

```
GrocoolMusic/
├── app/                        # 安卓主模块（由 BitChord app/ 裁剪而来）
│   └── src/main/java/.../
│       ├── ui/                 # 尽量保持与上游一致（同步区）
│       ├── playback/           # Media3 播放层（保留区）
│       └── adapter/            # 【隔离带】所有自有改动集中于此
├── sharedUi/                   # 上游 Compose Multiplatform UI（同步区）
├── shared/                     # 上游共享模型（同步区，尽量不改）
├── kugou/                      # 【新建】独立模块，可单独测试与替换
│   ├── api/                    # KugouApi：公共签名层 + 接口薄封装
│   ├── model/                  # 原始响应 DTO
│   ├── crypto/                 # MD5 / AES / RSA / 签名 / dfid / 行为指纹
│   └── contract/               # 契约测试与 fixtures
├── repository/                 # 【新建】KugouRepository：领域模型与装配
├── docs/                       # 需求、规格、UPSTREAM、合规声明
├── .github/workflows/          # android.yml / check.yml
└── LICENSE / NOTICE / THIRD_PARTY_NOTICES.md
```

### 3.3 分支与同步策略

- `main`：可发布主线。
- `upstream/bitchord`：只镜像上游，禁止在此分支写业务代码。
- `feature/*`：各 WP 工作分支。
- 同步流程：上游发新 tag → 在 `upstream/bitchord` 更新 → 生成 diff → **只挑选 `sharedUi/`、`app/ui/`、资源与主题文件** → 合并进 `main` → 冲突必须全部落在 `adapter/` → 跑回归 → 记录到 `docs/UPSTREAM.md`。
- **禁止**把上游 `shared/` 数据层、`YtMusicRepository`、Innertube 相关目录合并进来。

---

## 4. 架构分层与模块契约

四层，**依赖方向单向向下**，禁止反向引用。

### 4.1 分层职责

| 层 | 职责 | 输入 | 输出 | 禁止 |
| --- | --- | --- | --- | --- |
| `KugouApi` | 发请求、签名、加解密、token / cookie 管理 | 参数 + 凭据 | 原始 DTO | 不得包含 UI 概念，不得直接被 UI 调用 |
| `KugouRepository` | 把 236 类接口响应收敛为统一领域模型、缓存、分页 | DTO | 领域模型 | 不得暴露酷狗字段名给上层 |
| `adapter/` | 把领域模型转换为上游 ViewModel / Screen 所需的数据形态 | 领域模型 | 上游数据形态 | 不得在此层发网络请求；上游改动只允许改这里 |
| UI + 播放层 | 呈现与播放 | 上游数据形态 | 界面 / 音频 | 不得出现 `kugou` 包引用 |

### 4.2 建议接口签名（契约，agent 之间以此为准）

```kotlin
// kugou/api —— 唯一的网络出口
interface KugouApi {
    suspend fun <T> call(
        endpoint: String,
        params: Map<String, Any?>,
        options: RequestOptions = RequestOptions()
    ): Result<T>
    suspend fun health(): SourceHealth
}

data class RequestOptions(
    val platform: Platform = Platform.LITE,   // lite = 酷狗概念版
    val encryptType: EncryptType? = null,     // android / web / register
    val sign: Boolean = true,
    val auth: Boolean = false,                // 免费听 Auth 链路
    val cookie: Map<String, String> = emptyMap()
)

// repository —— 统一领域模型出口
interface KugouRepository {
    suspend fun search(query: String, type: SearchType, page: Int): Result<Page<SearchResult>>
    suspend fun stream(songId: String, quality: Quality): Result<Stream>
    suspend fun home(): Result<List<Shelf>>
    suspend fun playlist(id: String): Result<PlaylistDetail>
    suspend fun rankList(): Result<List<RankMeta>>
    suspend fun comments(target: CommentTarget, page: Int): Result<Page<Comment>>
    suspend fun loginQr(): Result<QrSession>
    suspend fun userLibrary(): Result<UserLibrary>
}
```

### 4.3 领域模型字段建议

- `Song`：`id`、`hash`、`name`、`artists: List<ArtistRef>`、`album: AlbumRef`、`durationMs`、`quality: Set<Quality>`、`coverUrl`、`lyricId`、`mvId`、`payFlags`（免 VIP / 试听 / Auth 需求）
- `Album` / `Artist` / `Playlist`：`id`、`name`、`coverUrl`、`description`、`count`、`publishTime`、`extra`
- `Shelf`：`id`、`title`、`subtitle`、`type`（`TRACK / PLAYLIST / ALBUM / ARTIST / RANK / BANNER / VIDEO`）、`items`、`continuation`（分页令牌，用于替代上游的 YTM continuation 机制）
- `User`：`id`、`nickname`、`avatar`、`vipType`、`liteToken`、`tokenExpiry`
- `Comment`：`id`、`content`、`user`、`likeCount`、`replyCount`、`floor`、`replies`
- `Lyric`：`lines: List<LyricLine>`、`translation`、`format`（`LRC / YRC / VERBATIM`）

> 上游 `YtMusicRepository.home()` 的实现是 `Innertube.browse("FEmusic_home")` + `InnertubeParser.parseHome(...)`，并依赖 YTM 的 continuation token 分页。**`Shelf` 模型与 `continuation` 字段就是用来替换这套机制的**，M2 的核心工作即在此。

---

## 5. 协议层规格（`KugouApi`，M0 的核心）

### 5.1 必须移植的公共能力

参考文件（KuGouMusicApi @ `7ac64e1cdd33`）：

| 文件 | 行数 | 内容 |
| --- | --- | --- |
| `util/request.js` | 410 | 注入默认设备标识与时间戳、生成 signature / sign、构造 UA 与设备头、axios 发送、解析 Cookie、处理 SSA 验证码、二次验证时生成行为指纹 |
| `util/crypto.js` | 318 | MD5 / AES / RSA，内置**普通版与 lite 版两套 RSA 公钥** |
| `util/helper.js` | — | 签名函数（`signKey`、`signatureAndroidParams`、`signatureRegisterParam`） |
| `util/generate_simulate.js` | — | `sid` / `edt` 行为指纹模拟（风控） |
| `util/config.json` | — | 平台配置（含 `platform=lite`） |
| `util/util.js` / `runtime.js` | — | `randomString`、代理配置解析 |

**参数清单（必须完整覆盖）**：`dfid`、`clienttime`、`mid`、`uuid`、`appid`、`token`、`clientver`、`cookie`、`signature` / `sign`、`x-router` 头、`User-Agent` 设备伪装、`area_code`、`version` 等接口级默认值。

### 5.2 单接口实现范式

`module/song_url.js` 仅 52 行：拼 `dataMap`（`hash`、`quality`、`album_id`、`page_id`、`pid`、`cmd`、`clientver` 等）→ 调 `useAxios({ url: '/v5/url', method: 'GET', encryptType: 'android', encryptKey: true, notSign: true, headers: { 'x-router': 'trackercdn.kugou.com' }, cookie })`。其中 `isLite = process.env.platform === 'lite'` 会改变 `page_id`、`ppage_id`、`pid`。

**结论**：不要逐个「啃」236 个接口。实现一次公共层后，每个接口只是几十行的参数表。新增接口必须是纯配置式薄封装，禁止复制粘贴签名逻辑。

### 5.3 概念版（lite）专属规则（硬约束）

1. `platform=lite` 切换概念版；**普通版与概念版的 token 不通用**，凭据必须按平台隔离存储。
2. 部分接口有 lite 分支（搜索适配、`privilege_lite`、`song_url` 的 `pid` / `page_id` 差异），移植时逐个对照 `module/*.js` 中的 `isLite` 判断。
3. **免费听 Auth 链路**：`song_auth` → `song_url_auth` / `song_url_auth_merge`，涉及 `GetAuth` / 用户 Auth / 音乐 Auth；`privilege_lite`、`youth_*_vip`、`user_batch_union_vipinfo`（YVipInfo）用于会员状态判断。该链路必须在 M4 完整实现，M0 只需预留 `RequestOptions.auth`。
4. **音质与免 VIP 判断**：接口 048「获取音乐详情（含音质能否免 VIP 获取判断）」是取流前的必要决策依据。
5. **人机验证 / 风控**：EchoMusic 的 `src/renderer/components/app/KugouVerificationFlow.vue`、`src/renderer/utils/kugouVerification.ts`、`src/renderer/plugins/kugou.ts`、`tests/kugou-verification-flow.test.mjs` 是唯一可靠的实践参考；KuGouMusicApi 侧对应 `public/` 下的人机验证测试页与 `generate_simulate.js` 的 `ssaCode` 模拟数据。**这部分静态接口文档里没有，必须照抄实践者流程**。

### 5.4 可维护性要求

- `kugou/` 必须是**独立 Gradle 模块**，不依赖 `app/`；可单独跑契约测试。
- 每个已移植接口配一份 fixture（脱敏响应样本）与契约测试，上游接口变动时能快速定位。
- 禁止把 `platform`、密钥、token 硬编码在 UI 层；凭据统一走 `androidx.security:security-crypto` + lite/普通双槽。
- 可选验证（**不作为主线**）：BitChord 自带 `QuickJsExecutor`，理论上可在 QuickJS 中直接跑 MIT 的 `module/*.js` 以省去 Kotlin 重写；但 `request.js` 依赖 axios、`crypto.js` 依赖 crypto-js / node-forge，需写 HTTP / 加密 shim，且常驻 JS 引擎违背低功耗目标。仅允许作为 M0 的一次性对照实验。

---

## 6. 功能对照表（全量口径）

### 6.1 A 类：近似 1:1，换数据绑定即可（上游 UI 与播放层原样保留）

搜索页与结果列表、播放页 / 逐字歌词渲染组件、播放队列与播放模式、收藏与本地歌单、播放进度拖动、倍速、淡入淡出切歌、音量、均衡器（系统 EQ 接入）、睡眠定时、深浅色主题与动态取色、后台播放与媒体通知 / 媒体按键、锁屏控制、离线下载与内嵌元数据、本地音乐库、音质分级设置、播放历史。

### 6.2 B 类：需重写数据装配（M2 / M3 主体）

| 目标界面 / 能力 | 对应酷狗接口（编号见附录 A） | 工作要点 |
| --- | --- | --- |
| 首页推荐 / 乐库 | 051–063、077、041、085–087、107、011 | 用 `banner` + `乐库` + `每日推荐` + `编辑精选` + `AI 推荐` 组合出 `List<Shelf>`，替换 `FEmusic_home` |
| 搜索（综合 / 单曲 / 歌手 / 专辑 / 歌单 / 歌词 / MV） | 023–029、127 | 综合搜索结果需映射为统一 `SearchResult` 密封类 |
| 榜单（列表 / 推荐 / 往期 / 详情 / 歌曲） | 088–092 | 往期榜单需要时间维度模型 |
| 歌单（分类 / 详情 / 全部歌曲 / 相似 / 主题 / 增删收藏） | 012–015、030–040 | 分页与「新版」接口 036 优先 |
| 专辑 / 歌手 | 016–020、042–043、065–072 | 歌手图片 / 动态封面需降级占位策略 |
| 私人 FM / 电台 | 050、053、055–058 | 操作参数以实践者调用为准（上游曾修复私人 FM 参数） |
| 评论（歌曲 / 分类 / 热词 / 楼层 / 歌单 / 专辑） | 093–098 | 楼层跳转需两级分页 |
| 登录与账号 | 001–011、104–105、130–132、125–126 | 扫码 / 验证码 / 密码 / 第三方；lite token 独立存储 |
| 歌词（LRC / YRC / 翻译 / 逐字） | 028–029 | 上游已有 `KuGou.kt` 歌词源可复用其解析思路，但需换成概念版链路 |
| 取流与音质 | 021–022、044–049、021 的 Auth 版本 | 先查 048 的免 VIP 判断，再决定 URL 版本 |
| MV / 视频 | 069、073–076 | 视频播放接入 Media3，注意国内 CDN |
| 动态封面 | 020 | 替代上游 Apple HLS Canvas / Spotify Canvas |
| 场景音乐 / 频道社区 | 078–084、108–116 | 概念版频道是特色能力，M4 补齐 |
| 曲谱 / 成绩单 / 已购 | 099–103、123–126 | 低频功能，M4 视排期取舍 |

### 6.3 C 类：必须剔除或自建（国内不可用 / fork 后失效）

Google 账号登录、Spotify 集成与歌单导入、Discord Rich Presence、Last.fm / ListenBrainz scrobbling、Google Cast、Wear OS 联动、Listen Together 官方服务器（`bitchord-listen-together.onrender.com`）、AddonAuth 官方 token 服务（`bitchord-auth.kushagrasingh.in`，源码注释明确 fork 指向自建服务无意义）、YouTube / Apple / Spotify 元数据与动态封面、应用内更新检查指向 BitChord release（`UPDATE_APK_PREFIX = "BitChord-"`）、`ic_google_g` 等品牌资源。

### 6.4 D 类：桌面形态专属，按功能等价处理

| EchoMusic 能力 | 安卓处置建议 |
| --- | --- |
| 音乐云盘 | 用 `user_cloud*` 未文档化接口实现等价的上传 / 匹配 / 播放 |
| 听歌识曲 | 系统麦克风 + 第三方识别能力，或明确不做（M4 决策） |
| DLNA / AirPlay 投放 | 安卓可用 MediaRouter / 本地投屏协议做等价，或不做 |
| 插件系统 | 不做，避免安全与 GPL 复杂度 |
| Rust DSP 音效引擎（10 段 EQ / LUFS / IRS） | Media3 音频处理链 + 系统 EQ 等价替代 |
| 实时频谱 / 桌面歌词 / 写真模式 | 频谱可用 Visualizer 等价；桌面歌词不做 |
| 系统托盘 / 全局快捷键 / 开机自启 / mini 模式 | 不适用 |

### 6.5 反向保留（上游白捡的资产，禁止误删）

Gapless 与真交叉淡入淡出（0–12s）、Automix、播放速度 0.5×–2.0×、跳过静音、系统均衡器接入、USB bit-perfect 输出、Stats for nerds、歌词动画（AMLL 思路）、动态取色主题、Haze 毛玻璃、离线下载内嵌元数据、睡眠定时器、MediaSession 前台服务。

---

## 7. 里程碑与验收标准

按**完成标志**验收，禁止用「预计耗时」排期。

| 里程碑 | 目标 | 完成标志（可测） | 依赖 |
| --- | --- | --- | --- |
| **M0 协议验证** | 打通国内网络下的酷狗请求 | Kotlin 公共签名层 + `search` / `song_url` / `lyric` 三接口；在国内真机上成功取到可播放 URL 并用 Media3 出声；契约测试通过；lite 与普通 token 双槽可用 | WP-0、WP-1 |
| **M1 播放闭环** | 外壳可运行 | 实现 `MusicSource`（`health / search / stream`）接入；能搜能放；C 类依赖已删除且编译通过；dev / prod flavor 可共存安装 | M0、WP-3 |
| **M2 内容层** | 界面显示的是酷狗内容 | 首页、搜索、榜单、歌单、专辑 / 歌手、歌曲详情六个屏幕全部由 `KugouRepository` 供数；不再出现任何 Innertube / YTM 调用 | M1、WP-2、WP-4 |
| **M3 账号与个性化** | 登录态与个性化 | 扫码 / 验证码登录、刷新登录、收藏与自建歌单、每日推荐、私人 FM、评论与楼层、云盘等价能力可用 | M2、WP-5 |
| **M4 完整功能与打磨** | 达到「功能等价」 | 概念版免费听 Auth 链路跑通、YRC 逐字歌词与翻译、MV、动态封面、离线下载、设置项完整；低功耗指标达标；C 类清零 | M3 |
| **M5 桌面端** | 扩展平台 | Windows / macOS 可运行版本（EchoMusic 换皮 或 Compose Multiplatform，二选一，开工前再定），共享同一份《酷狗 API 契约》 | M4 |

---

## 8. 工作包分解（可直接指派）

> 每个 WP 输出必须可独立验收；跨 WP 只允许依赖第 4 节契约。

### WP-0 仓库骨架、构建与合规底座
- **输入**：本文档、三个上游 URL 与锁定 commit。
- **输出**：公开仓库结构、`settings.gradle.kts` / 版本目录、`docs/UPSTREAM.md`、`LICENSE`（GPL-3.0）、`NOTICE`、`THIRD_PARTY_NOTICES.md`、README（含免责声明）、`keystore.properties.example`、`.gitignore`（密钥不入库）、`android.yml` / `check.yml` 工作流骨架。
- **验收**：空工程可 `./gradlew assembleProdRelease` 产出**未签名** release APK；CI 在无密钥时构建不失败；版权声明齐全。
- **并行性**：可立即启动，与 WP-1 / WP-3 并行。
- **禁止**：提交任何真实 keystore、密码、token；复制上游品牌资源。

### WP-1 协议层 `KugouApi`（M0 关键路径）
- **输入**：第 5 节规格；KuGouMusicApi `util/` 六个文件；`module/song_url.js` / `search.js` / `lyric.js`。
- **输出**：`kugou/` 独立模块；公共签名层；三接口实现；fixtures 与契约测试；`RequestOptions`（含 `platform` / `auth`）；lite 与普通凭据双槽。
- **验收**：在**国内网络真机**上取到可播放 URL；MD5 / AES / RSA 结果与 Node 版逐字节一致（用固定输入做 golden test）；`isLite` 分支参数与上游一致。
- **并行性**：与 WP-0 / WP-3 并行；不依赖 UI。
- **禁止**：在协议层引入 UI 或 Room 依赖；把签名逻辑复制到各接口文件里。

### WP-2 领域模型与 `KugouRepository`
- **输入**：第 4.3 节模型、附录 A 清单、WP-1 契约。
- **输出**：领域模型、`Shelf.continuation` 分页、映射器、缓存策略、接口 048 的免 VIP 判断封装。
- **验收**：单元测试覆盖主要 DTO → 领域模型映射；上层拿不到任何酷狗原始字段名。
- **并行性**：接口契约定稿后即可与 WP-1 并行（用 mock DTO）。

### WP-3 上游 UI 基线导入与裁剪
- **输入**：BitChord `v1.9.1` / `acf219a7e4a7`。
- **输出**：fork 后的 `app/` / `sharedUi/` / `shared/`；`upstream/bitchord` 分支；C 类依赖与屏幕清单删除；Media3 播放层保留；构建通过。
- **验收**：UI 可启动并显示 Apple Music 风格骨架（可先用假数据）；删除清单逐项核对；无 Google Play Services 依赖残留。
- **并行性**：与 WP-1 并行。
- **禁止**：改动 `sharedUi/` 深处的布局代码；把 Innertube 相关目录留在 classpath 中「备用」。

### WP-4 `adapter/` 与 ViewModel 重写（工作量最大，建议按屏幕再拆子任务）
- **输入**：WP-2 领域模型、WP-3 上游 UI、第 6.2 节映射表。
- **输出**：六个屏幕的数据装配（建议子任务 WP-4a 首页、4b 搜索、4c 榜单 / 歌单、4d 专辑 / 歌手 / 详情、4e 播放器数据绑定、4f 设置与资料库）。
- **验收**：每个屏幕不再引用 `YtMusicRepository` / `Innertube`；分页与下拉刷新可用；滚动无明显掉帧。
- **并行性**：子任务之间可并行，但必须共用同一份 `adapter/` 契约。
- **禁止**：在 `app/ui/` 里写网络请求；把酷狗字段名泄漏到 Composable。

### WP-5 概念版专属能力（免费听 / 登录 / 风控）
- **输入**：第 5.3 节；EchoMusic `KugouVerificationFlow.vue` / `kugouVerification.ts` / `renderer/api/user.ts`；24 个 lite 专属 module。
- **输出**：扫码登录与 token 刷新、免费听 Auth 链路、人机验证流程、会员状态判断、频道社区基础能力。
- **验收**：`platform=lite` 全流程可在真机走通；token 按平台隔离；风控触发时有可恢复的验证界面而非崩溃。
- **并行性**：依赖 WP-1；UI 部分可与 WP-4 并行。

### WP-6 测试、性能与可读性
- **输入**：全部 WP 产物。
- **输出**：契约测试、UI 回归、真机测试清单、低功耗基线（后台播放内存 / 唤醒锁持有时长 / 空闲 CPU / 电量）、代码规范（命名、注释、模块边界）与 lint 配置。
- **验收**：M2 / M4 回归通过；后台播放无持续唤醒锁；新增代码符合分层约束（可用架构测试或 lint 规则校验）。
- **并行性**：从 M0 起持续介入。

### WP-7 签名、CI 与测试版 Release
- **输入**：WP-0 工作流骨架、统一签名密钥。
- **输出**：GitHub Secrets 配置说明（`KEYSTORE_BASE64` / `STORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`）、release 签名配置、`v*` tag 触发的多架构构建、pre-release 产物与更新日志、应用内更新检查改为本项目仓库。
- **验收**：固定 `applicationId`、`versionCode` 递增、release 签名一致 → 新版本可**覆盖安装**；dev / prod 不互相覆盖；未配置密钥时产出 `app-release-unsigned.apk` 而不失败。
- **并行性**：M1 前完成即可。
- **禁止**：把密钥或 base64 写进仓库；用 debug key 发布。

### WP-8 桌面端（M5，暂缓启动）
- **输入**：《酷狗 API 契约》、M4 成果、EchoMusic `v2.3.2`。
- **输出**：Windows / macOS 版本；选型二选一（EchoMusic 换皮 / Compose Multiplatform）在启动前由发起人确认。
- **验收**：与安卓共享同一份接口契约；GPL-3.0 合规。
- **并行性**：M4 完成后启动。

### 依赖关系

```
WP-0 ─┬─> WP-1 ─> WP-5 ─┐
      ├─> WP-3 ─────────┼─> WP-4 ─> M2/M3/M4 ─> WP-8
      └─> WP-7 ─────────┘
WP-2 (契约定稿后即可并行) ─> WP-4
WP-6 贯穿全程
```

---

## 9. 工程与发布规格

- **统一签名密钥**：`keystore.properties` 只存路径与别名引用，文件本身与密码均不入库；CI 用 GitHub Secrets 注入，构建时 base64 解码出 `.jks` 到临时目录，构建结束清理。
- **覆盖安装四要素**：`applicationId` 固定不变、`versionCode` 严格递增、release 签名证书一致、非 debuggable。任何一项变动都会导致无法覆盖安装。
- **flavor**：保留上游 `dev` / `prod` 双 flavor 思路（`com.dev.grocoolmusic` / 正式包名），避免调试包覆盖正式包。
- **ABI**：`armeabi-v7a`、`arm64-v8a`、`x86_64` + universal APK；国内以 arm64 为主。
- **Release**：`v*` tag 触发；先发 **pre-release 测试版**；产物命名统一前缀（例如 `GrocoolMusic-`），应用内更新检查按该前缀匹配。
- **R8**：保持 `isMinifyEnabled = true` 但 `-dontobfuscate`，裁剪后重新核对 keep 规则（上游的 keep 规则多是为 NewPipe / Rhino / QuickJS 写的，删除依赖后要同步精简）。
- **实机调试**：M0 起即需真机；每次需要实机验证时**主动提醒发起人进行有线 ADB 连接**（`adb devices` 确认设备在线、`adb install -r` 覆盖安装、`logcat` 抓接口错误）。

---

## 10. 合规清单（发布前逐项打勾）

1. 成品采用 **GPL-3.0**（或 `GPL-3.0-or-later`），公开仓库提供完整对应源码（含构建脚本与配置说明）。
2. 保留 BitChord 与 EchoMusic 的版权声明；在 `NOTICE` / 源码头标注「基于上述项目修改」，并说明修改内容。
3. 保留 KuGouMusicApi 的 **MIT** 版权声明（其算法被移植到 Kotlin）。
4. 列出全部第三方依赖许可（`THIRD_PARTY_NOTICES.md`），注意上游 `LICENSES/` 目录的既有条目。
5. 沿用「仅供个人学习与技术研究、非商业、不存储不传播音频文件、版权归原平台所有」免责声明；不接入任何捐赠 / 广告 / 商业合作。
6. Apple Music 仅作为**风格参考**：不 1:1 复刻图标、Logo、商标文字、专有字体与动效资源；应用名使用 `Grocool Music`，不使用酷狗或 Apple 商标。
7. 不内置任何 API key、token、账号或签名密钥；不内置受版权保护的音频与图片资源。
8. 上游 `AddonAuth` 官方 token 服务与 Listen Together 官方服务不可用，不得伪装成官方客户端。

---

## 11. 风险登记册

| 风险 | 影响 | 对策 | 负责 WP |
| --- | --- | --- | --- |
| 酷狗接口无官方承诺，签名 / 风控会变 | 播放与登录失效 | `kugou/` 独立模块 + golden test + fixture；跟踪上游 commit，记录到 `UPSTREAM.md` | WP-1 / WP-6 |
| 概念版 token 与普通版不通用 | 免费听 / 登录态异常 | 凭据双槽隔离，`RequestOptions.platform` 强制传参 | WP-1 / WP-5 |
| 人机验证 / SSA 行为指纹 | 取流被拒 | 严格照抄 EchoMusic 验证流程，配可恢复 UI | WP-5 |
| BitChord 迭代极快（3 个月 1409 文件、424 open issues） | 同步撕裂数据层 | `upstream` 分支 + 只同步 UI 目录 + 改动隔离在 `adapter/` + 按 tag 挑选 | WP-3 / WP-4 |
| GPL-3.0 义务遗漏 | 开源合规风险 | 第 10 节清单逐项打勾后再发 Release | WP-0 / WP-7 |
| 国内网络 / 区域校验 | M0 直接失败 | M0 优先在真机国内网络验证，不通则不进入 M1 | WP-1 |
| 误删 Media3 依赖链 | 播放崩溃 | 裁剪时逐项确认，保留 6.5 节资产 | WP-3 |

---

## 12. 全局 Definition of Done

- 任一里程碑：完成标志全部可测通过；无 C 类依赖残留；分层约束校验通过；`docs/UPSTREAM.md` 已更新；合规清单对应项已打勾。
- 任一 WP：输出物可独立编译 / 测试；接口契约未擅自变更；新增代码有注释与命名规范；未提交任何密钥。
- 发版：覆盖安装验证通过、pre-release 产物可下载、更新日志可读、真机回归通过、发起人已完成有线 ADB 实机确认。

---

## 附录 A：KuGouMusicApi 对外文档 132 条接口清单

> 排期时请以 `module/` 目录 236 个实现文件为准；本表仅为文档子集，用于功能域映射与界面排期。

| 编号 | 接口 | 编号 | 接口 |
| --- | --- | --- | --- |
| 001 | 登录 | 002 | 刷新登录 |
| 003 | 发送验证码 | 004 | dfid 获取 |
| 005 | 获取用户额外信息 | 006 | 获取用户 vip 信息 |
| 007 | 获取用户歌单 | 008 | 获取用户关注歌手 |
| 009 | 获取用户听歌历史排行 | 010 | 获取用户最近听歌历史 |
| 011 | 获取继续播放信息 | 012 | 收藏歌单/新建歌单 |
| 013 | 取消收藏歌单/删除歌单 | 014 | 对歌单添加歌曲 |
| 015 | 对歌单删除歌曲 | 016 | 新碟上架 |
| 017 | 专辑信息 | 018 | 专辑详情 |
| 019 | 专辑音乐列表 | 020 | 专辑动态封面 |
| 021 | 获取音乐 URL | 022 | 获取歌曲高潮部分 |
| 023 | 搜索 | 024 | 默认搜索关键词 |
| 025 | 综合搜索 | 026 | 热搜列表 |
| 027 | 搜索建议 | 028 | 歌词搜索 |
| 029 | 获取歌词 | 030 | 歌单分类 |
| 031 | 歌单 | 032 | 主题歌单 |
| 033 | 音效歌单 | 034 | 获取歌单详情 |
| 035 | 获取歌单所有歌曲 | 036 | 获取歌单所有歌曲（新版） |
| 037 | 相似歌单 | 038 | 获取主题歌单所有歌曲 |
| 039 | 获取主题音乐 | 040 | 获取主题音乐详情 |
| 041 | 歌曲推荐 | 042 | 获取歌手和专辑图片 |
| 043 | 获取歌手图片 | 044 | 获取音乐相关信息 |
| 045 | 获取更多音乐版本 | 046 | 获取音乐伴奏信息 |
| 047 | 获取音乐 k 歌数量 | 048 | 获取音乐详情（含音质能否免 VIP 获取判断） |
| 049 | 获取音乐专辑/歌手信息 | 050 | 私人 FM（猜你喜欢） |
| 051 | banner | 052 | 乐库 banner |
| 053 | 乐库电台 | 054 | 乐库 |
| 055 | 电台 - 推荐 | 056 | 电台 |
| 057 | 电台 - 图片 | 058 | 电台 - 音乐列表 |
| 059 | 编辑精选 | 060 | 编辑精选数据 |
| 061 | 编辑精选歌单 | 062 | 编辑精选专区 |
| 063 | 编辑精选专区详情 | 064 | 领取 VIP（测试接口） |
| 065 | 获取歌手列表 | 066 | 获取歌手详情 |
| 067 | 获取歌手专辑 | 068 | 获取歌手单曲 |
| 069 | 获取歌手 MV | 070 | 关注歌手 |
| 071 | 取消关注歌手 | 072 | 获取关注歌手新歌 |
| 073 | 获取视频 url | 074 | 获取歌曲 MV |
| 075 | 获取视频相关信息 | 076 | 获取视频详情 |
| 077 | 新歌速递 | 078 | 场景音乐列表 |
| 079 | 场景音乐详情 | 080 | 获取场景音乐讨论区 |
| 081 | 获取场景音乐模块 Tag | 082 | 获取场景音乐歌单列表 |
| 083 | 获取场景音乐视频列表 | 084 | 获取场景音乐音乐列表 |
| 085 | 每日推荐 | 086 | 历史推荐 |
| 087 | 风格推荐 | 088 | 排行列表 |
| 089 | 排行榜推荐列表 | 090 | 排行榜往期列表 |
| 091 | 排行榜信息 | 092 | 排行榜歌曲列表 |
| 093 | 歌曲评论 | 094 | 歌曲评论-根据分类返回 |
| 095 | 歌曲评论-根据热词返回 | 096 | 楼层评论 |
| 097 | 歌单评论 | 098 | 专辑评论 |
| 099 | 歌曲曲谱 | 100 | 曲谱详情 |
| 101 | 推荐曲谱 | 102 | 曲谱合集 |
| 103 | 曲谱合集详情 | 104 | 提交听歌历史 |
| 105 | 获取服务器时间 | 106 | 刷刷 |
| 107 | AI 推荐 | 108 | 频道 - 获取用户所有频道 |
| 109 | 频道 - 详情 | 110 | 频道 - 频道安利 |
| 111 | 频道 - 相似频道 | 112 | 频道 - 订阅 |
| 113 | 频道 - 音乐故事 | 114 | 频道 - 音乐故事详情 |
| 115 | 动态 - 最常访问 | 116 | 获取用户公开的音乐 |
| 117 | 听书 - 每日推荐 | 118 | 听书 - 排行榜推荐 |
| 119 | 听书 - VIP 推荐 | 120 | 听书 - 每周推荐 |
| 121 | 听书 - 专辑详情 | 122 | 听书 - 专辑音乐列表 |
| 123 | 歌曲详情 - 歌曲成绩单 | 124 | 歌曲详情 - 歌曲成绩单详情 |
| 125 | 获取已购单曲 | 126 | 获取已购专辑 |
| 127 | 听书 - 搜索 | 128 | 听书 -免费书库 |
| 129 | 听书 -免费书库分类标签列表 | 130 | 修改个人资料 |
| 131 | 修改头像 | 132 | 批量查询概念版会员产品（YVipInfo） |

## 附录 B：关键源码证据（供 agent 快速定位，避免重复调研）

| 事实 | 证据路径 |
| --- | --- |
| 可插拔音源只有 3 个方法 | `BitChord/app/src/main/java/com/music/bitchord/data/sources/MusicSource.kt`（`health / search / stream`；注释明确 home feeds / related / radio 归 YouTube 独有） |
| 首页写死 YTM browseId | `BitChord/shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/YtMusicRepository.kt` 的 `home()` = `Innertube.browse("FEmusic_home")` + `InnertubeParser.parseHome(...)`；全文件 1253 行 / 67 函数，`Innertube` 出现 126 次 |
| UI 大脑深度耦合 YTM | `BitChord/app/src/main/java/com/music/bitchord/ui/MainViewModel.kt`：3639 行 / 131 函数，`YtMusic` 59 次、`YouTube` 56 次、`playlist` 191 次、`home` 69 次 |
| UI 规模 | BitChord 共 681 个 `.kt` 文件、1409 个文件；`/ui/` 下 227 个 Kotlin 文件；`HomeScreen.kt` 1041 行 |
| 上游技术栈 | `BitChord/app/build.gradle.kts`：Kotlin + Compose + Media3 + Ktor + InnertubeX + NewPipe + Haze + Coil 3；minSdk 26 / targetSdk 36 / compileSdk 37；`keystore.properties` + `UPDATE_APK_PREFIX` 范式 |
| EchoMusic 的引擎来源 | `EchoMusic/.gitmodules`：`[submodule "server"] path = server url = https://github.com/MakcRe/KuGouMusicApi.git` |
| EchoMusic 技术栈 | Electron 43 + Vue 3.5 + TS 5.9 + Vite 8 + Pinia + Rust NAPI（8 个 native 模块）；桌面三平台；GPL-3.0 |
| 人机验证流程 | `EchoMusic/src/renderer/components/app/KugouVerificationFlow.vue`、`src/renderer/utils/kugouVerification.ts`、`src/renderer/plugins/kugou.ts` |
| 协议复杂度集中处 | `KuGouMusicApi/util/request.js`（410 行）、`util/crypto.js`（318 行，两套 RSA 公钥）、`util/helper.js`、`util/generate_simulate.js` |
| 单接口很薄 | `KuGouMusicApi/module/song_url.js`（52 行，含 `isLite` 分支） |

## 附录 C：术语与链接

- **概念版 / lite / youth**：酷狗音乐概念版，`platform=lite`；`youth_*` 为其会员与频道体系接口前缀。
- **功能等价**：本文 1.2 节定义的移植判定标准。
- **隔离带**：`adapter/`，上游同步时唯一允许发生冲突的位置。
- **C 类**：国内不可用 / fork 后失效、需剔除的国际与官方绑定服务（6.3 节）。
- 仓库：BitChord `https://github.com/kushagrasinghx/BitChord`；EchoMusic `https://github.com/hoowhoami/EchoMusic`；KuGouMusicApi `https://github.com/MakcRe/KuGouMusicApi`。
