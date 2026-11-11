# UI 基线第一批：为什么之前是 MD3，以及改了什么

> 起因（2026-11-11）：发起人真机验收 0.3.0 后指出「UI + 播放层没有一点 Apple Music
> 风格 / BitChord 风格，感觉就是典型 MD3」。这个判断**完全正确**，本文件记录量化证据、
> 根因、本批次的改动与仍存差距。

## 一、问题确认：之前确实是 MD3

对 0.3.0 的 `app/src/main/kotlin` 做静态统计：

| 指标 | 结果 |
| --- | --- |
| Material 3 控件 | `NavigationBar` / `OutlinedTextField` / `AssistChip` / `Button` / `Surface` / `Scaffold` / `CircularProgressIndicator` 全部使用 |
| 自定义绘制 | `Canvas` / `Brush` / `Path` / `graphicsLayer` / `blur` **0 处** |
| 所谓「Apple Music 风格」 | 仅 `#FA233B` 一个强调色 + 大标题 + `RoundedCornerShape` 普通圆角 |
| 封面 | 色块 + 首字占位，无真实图片 |

真机截图（`ui_before.png`）显示：描边胶囊 chip、纯白直角卡片、贴底导航栏、色块封面、
Roboto 默认字阶 —— 典型 Material 3，**不是** Apple Music。

**根因**：M0→M2 的优先级全部放在「协议能不能通 / 签名对不对 / 内容能不能装配 /
能不能出声」（最大不确定性），UI 一直是「能点能看」的临时骨架。实施规格里写的
「M1 后段按 tag 导入 BitChord UI 基线」被一再往后推；README 中「Apple Music 风格起点」
描述的是意图而非现状，措辞有误导。按里程碑顺序属计划内，按最终目标则是明确缺口。

## 二、BitChord v1.9.1 的「Apple 感」到底来自哪里

按 tag `v1.9.1`（commit `7f45c768a461fd7e68bf4613f71e8a767e643d4c`）拉取并分析：
1409 个文件 / 681 个 Kotlin / 227 个 UI 文件。风格不是"配色"，而是下面这一整套：

| 资产 | 上游文件 | 本批次处理 |
| --- | --- | --- |
| iOS 系统色板（纯黑/纯白 + `#1C1C1E` / `#8E8E93` / `#F2F2F7`） | `ui/theme/Theme.kt` | ✅ 移植，红仅作 accent |
| Apple 字阶（34/30/22/20sp，负字距，字重整体降一级） | `ui/theme/Theme.kt` | ✅ 移植数值，**不复制 SF Pro 字体文件**（Apple 专有字体，不可在 GPL 项目再分发），用平台 sans-serif 近似 |
| **squircle 连续曲率圆角** | `ContinuousCornerShape.kt` / `SquirclePillShape.kt` | ✅ 原样移植算法与常数（`GLASS_CORNER_SMOOTHING=0.607`、`SQUIRCLE_PILL_EXPONENT=2.1`），这两个文件零上游依赖、纯 Compose |
| 真实封面 | `RemoteArtwork.kt` + coil3 | ✅ 本批次接 Coil 2.6.0 加载 `coverUrl` |
| 毛玻璃 / Liquid Glass | `LiquidGlass.kt` / `FrostedTopBar.kt` / `GlassNavBar.kt` | ⚠️ 依赖 `dev.chrisbanes.haze` + 上游 backdrop 系统 + `Song` 数据模型（分别 11 / 5 / 12 个内部类引用），**不能直接移植**；本批次用半透明玻璃卡片 + 细高光边框近似 |
| 封面动态取色 | `ArtworkPalette.kt` | ⚠️ 依赖 coil3 + 7 个上游内部类，留待后续 |
| iOS 控件 / 悬浮底栏 / 悬浮迷你播放器 | `IosSwitch.kt` / `FloatingBottomBar.kt` / `MiniPlayer.kt` | ✅ 按视觉语言**重写**为本项目自有组件（不复制数据耦合代码） |

**耦合度实测**（import 统计）：`HomeScreen.kt` 90 个 import 中 33 个是上游内部类、
`MiniPlayer.kt` 13 个、`GlassNavBar.kt` 12 个 —— 这正是「外壳不是能整块剥下来的皮」的
证据，也是必须保留 `adapter/` 隔离带的原因。

## 三、本批次实际改动

新增：

- `ui/shape/Squircle.kt`：移植连续圆角与 squircle 胶囊，文件头保留 GPL-3.0 来源声明、
  commit 与逐条修改说明。
- `ui/Theme.kt`：iOS 深浅色板 + Apple 字阶 + `IosShapes` / `glassSurface()` / `secondaryLabel()`。
- `ui/Components.kt`：`GlassCard`（半透明 + 0.5dp 高光边 + squircle 剪裁 + 阴影）、
  `CoverBox`（Coil 真实封面，加载中显示渐变首字）、`IosSearchField`（填充式胶囊 +
  放大镜 + 红色胶囊按钮，无 MD3 描边）、`KeywordPill`、`SongRow`、`ChartShelf`、
  悬浮玻璃 `MiniPlayer`、`StatusLine`。
- `MainActivity.kt`：`GlassTabBar`（悬浮玻璃卡片 + 选中态 squircle 胶囊）替代
  `NavigationBarItem` / `NavigationBar`。
- 依赖：`io.coil-kt:coil-compose:2.6.0`。

删除的 MD3 元素：`OutlinedTextField`、`AssistChip`、`NavigationBar`、`Button`、
`Surface`、`RoundedCornerShape`（全部替换）。

**架构不变**：UI 仍只消费 `adapter/` 的 `SongItem/ChartItem/ShelfItem`，
`kugou/` 协议层零改动，播放层零改动。上游同步时冲突仍只在 `adapter/` + 这一批 UI 文件。

## 四、合规

BitChord 为 GPL-3.0，本项目同为 GPL-3.0，允许合并但必须保留双方版权声明、标注修改、
提供完整构建源码 —— 已在 `NOTICE` / `THIRD_PARTY_NOTICES.md` 登记。

明确的**不做**：

- 不复制 SF Pro Display 字体文件（Apple 专有许可，与 GPL 冲突）。
- 不复制 Apple 图标、商标、`Apple Music` 字样与官方资源。
- 不整包导入 1409 个文件；按 tag 挑选纯视觉组件 + 重写数据耦合部分。

## 五、仍存差距（后续批次）

1. **真毛玻璃**：上游用 haze 做背景模糊与透镜高光；当前是半透明近似。接入需引入 haze
   与简化版 backdrop，并评估 `minSdk 26` 的 `RenderEffect` 兼容。
2. **动态取色**：`ArtworkPalette` 从封面提取主色驱动播放页背景，当前未接。
3. **Now Playing 全屏页**：Apple Music 最标志性的界面（大封面、逐字歌词、音量条、
   队列抽屉）尚未实现，当前只有迷你播放器。
4. **字体**：平台 sans-serif 的字形与 SF Pro 仍有差异；若要求更接近，需选用可自由
   分发的替代字体（如 Inter / Roboto Flex）而非复制 SF Pro。
5. **动效**：转场、封面视差、播放页展开动画未做。

## 六、验证与真机截图对比

| 项 | 结果 |
| --- | --- |
| 本地 | `:kugou:test`（23 个契约测试）、`:app:assembleDebug` 全绿 |
| CI | `check` / `release` 均 success |
| 发布 | `v0.4.0-build.8`（13,557,800 bytes，versionCode 8），证书前缀 `0535ad27813ce0ca` 与前五版一致 |
| 真机 | `adb install -r` 覆盖安装 `Success`（7→8），`homeFeed ok`、无崩溃 |

对比截图（同一台 Xiaomi 17、同一首页、只读采集）：

| 改造前 `docs/screenshots/ui-0.3.0-md3.png` | 改造后 `docs/screenshots/ui-0.4.0-ios-baseline.png` |
| --- | --- |
| 描边 `AssistChip` | 填充式 squircle 胶囊 |
| 纯白直角卡片 + 色块首字封面 | 半透明玻璃卡片 + **真实酷狗榜单封面**（Coil） |
| 贴底全宽 `NavigationBar` | **悬浮分离式玻璃标签栏** + 选中态 squircle 胶囊 |
| 默认 Roboto 字阶 | 34/30/22/20sp 负字距大标题 + iOS 系统灰标签 |
| MD3 `OutlinedTextField` 搜索框 | 填充式 iOS 搜索栏（放大镜 + 红色胶囊按钮） |
| 普通 `RoundedCornerShape` | **squircle 连续曲率圆角**（移植自 BitChord） |

MD3 控件审计：`NavigationBar` / `OutlinedTextField` / `AssistChip` / `Button` /
`Surface` 的**实际 import 已全部清零**（剩余关键词仅存在于说明性注释中）。
