# Third-party notices

本文件随里程碑推进持续更新；发布任何二进制前必须核对一次。

## 运行时依赖（以 `gradle/libs.versions.toml` 为准）

| 组件 | 版本 | 用途 | 许可证 |
| --- | --- | --- | --- |
| Kotlin Standard Library | 2.0.21 | 语言运行时 | Apache-2.0 |
| kotlinx-serialization-json | 1.7.3 | JSON 解析 | Apache-2.0 |
| kotlinx-coroutines-android | 1.9.0 | 协程 | Apache-2.0 |
| AndroidX core-ktx / activity-compose | 1.13.1 / 1.9.3 | 基础组件 | Apache-2.0 |
| Jetpack Compose BOM | 2024.12.01 | UI | Apache-2.0 |
| Material 3 | (BOM) | 基础主题 / 少量原子控件 | Apache-2.0 |
| Material Icons Extended | (BOM) | 图标 | Apache-2.0 |
| AndroidX Media3 ExoPlayer | 1.5.1 | 播放 | Apache-2.0 |
| **Coil Compose** | **2.6.0** | **封面图片加载** | **Apache-2.0** |
| JUnit Jupiter / Platform | 5.10.2 / 1.10.2 | 单元测试（仅测试期） | EPL-2.0 |

## 上游衍生来源

| 项目 | 许可证 | 在本项目中的角色 |
| --- | --- | --- |
| BitChord `v1.9.1` (`7f45c768a461fd7e68bf4613f71e8a767e643d4c`) | GPL-3.0 | UI 视觉基线（见下） |
| EchoMusic `v2.3.3-beta.1` | GPL-3.0 | 酷狗接口调用实践参考；未复制源码 |
| KuGouMusicApi (`7ac64e1cdd33`) | MIT | 签名算法 / 请求构造 / 接口参数的实现依据 |

### 从 BitChord 复制的代码（GPL-3.0，已标注修改）

| 目标文件 | 来源文件（tag `v1.9.1`） | 修改 |
| --- | --- | --- |
| `app/.../ui/shape/Squircle.kt` | `app/.../ui/components/ContinuousCornerShape.kt` + `sharedUi/.../ui/components/SquirclePillShape.kt` | 改包名、合并为单文件、删未使用的 `continuous()` 扩展、压缩注释、增加 `Dp` 构造；**算法与常数逐字保留** |
| `app/.../ui/Theme.kt`（色板与字阶数值） | `app/.../ui/theme/Theme.kt` | 改包名 / 函数名；移植色值与字阶数值；**未复制 SF Pro Display 字体资源**；删除 `AppFont` / legacy 字体 / 系统栏图标辅助 |

上述两个来源文件均为**纯 Compose、零上游内部类依赖**，因此移植不引入 BitChord 的
YouTube Music 数据层。其它 iOS 风格组件（`GlassCard` / `GlassTabBar` /
`IosSearchField` / `MiniPlayer` / `CoverBox`）为**本项目按视觉语言重写**，非复制。

每个移植文件头部均保留来源、commit、原作者版权与逐条修改说明。完整构建源码见
公开仓库 <https://github.com/LucasLu-97/GrocoolMusic>。详见 `docs/03-UI-Baseline.md`。

### 明确未复制

- SF Pro Display 字体文件（Apple 专有许可，不可在 GPL 项目中再分发）。
- Apple 图标、商标、`Apple Music` 字样与任何官方资源。
- BitChord 的 `LiquidGlass` / `FrostedTopBar` / `GlassNavBar` / `ArtworkPalette` /
  `HomeScreen` 等（依赖 haze / coil3 / 上游 `Song` 数据模型，无法直接移植）。

> 后续若按上游 tag 同步更多 UI 文件，必须同步更新本表与对应文件头声明。
