# Third-party notices

本文件随里程碑推进持续更新；发布任何二进制前必须核对一次。

## M0（当前）— `kugou/` Kotlin/JVM 模块

| 组件 | 用途 | 许可证 |
| --- | --- | --- |
| Kotlin Standard Library | 语言运行时 | Apache-2.0 |
| kotlinx-serialization-json | JSON 解析 | Apache-2.0 |
| JUnit Jupiter | 单元测试（仅测试期） | Eclipse Public License 2.0 |

## 上游项目（作为衍生来源，见 `NOTICE`）

| 项目 | 许可证 |
| --- | --- |
| BitChord | GPL-3.0 |
| EchoMusic | GPL-3.0 |
| KuGouMusicApi | MIT |

## 计划在 M1 引入（届时需补齐条目）

Jetpack Compose / Material 3、AndroidX Media3、Coil 3、Ktor 或 OkHttp、
Room、androidx.security:security-crypto、Haze、Palette — 均为 Apache-2.0
（具体版本与许可证以 `gradle/libs.versions.toml` 为准）。

> 注意：上游 BitChord 的 `LICENSES/` 目录包含其原有第三方许可清单，
> 裁剪依赖时应同步精简本文件，不要保留已删除依赖的条目。
