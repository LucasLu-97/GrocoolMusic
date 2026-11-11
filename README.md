# Grocool Music

一个第三方在线音乐软件：**界面深度模仿 Apple Music，内容与功能完全移植酷狗音乐概念版**（功能等价，非逐项照搬）。

> 当前状态：**M0 协议验证阶段**。仓库包含需求基线、实施规格、上游版本锁定，以及独立的 Kotlin/JVM 协议层 `kugou/`。安卓主应用尚未开始，界面与播放层将在 M1 引入。

## 项目文档

| 文档 | 内容 |
| --- | --- |
| `docs/01-Project-Brief.md` | 修订后的项目需求说明（可独立转发给其他 AI / agent 阅读） |
| `docs/02-Implementation-Plan.md` | 实施规格与任务分解：架构契约、功能对照、里程碑、WP-0~WP-8 工作包 |
| `docs/UPSTREAM.md` | 三个上游仓库的锁定版本与同步规则 |
| `docs/M0-Protocol-Spec.md` | 酷狗请求签名层协议规格（M0 实现依据） |

## 架构

```
KugouApi（协议 / 签名） → KugouRepository（领域模型） → adapter/（隔离带） → BitChord UI + Media3 播放层
```

`kugou/` 是**独立 Kotlin/JVM 模块**，不依赖 Android SDK，可单独编译与测试；它对应 KuGouMusicApi 的 `util/request.js` + `util/crypto.js`，是后续所有 236 个接口的公共底座。

## 构建

需要 JDK 17+ 与 Gradle 8.x（或使用 CI）：

```bash
gradle :kugou:test          # 运行签名算法 golden 测试与请求构造测试
```

本机尚无 JDK / Android SDK 时，可直接查看 `.github/workflows/check.yml` 的 CI 结果。安卓主应用（`app/`）将在 M1 引入，届时补充 Gradle Wrapper 与签名配置。

## 上游与致谢

- [BitChord](https://github.com/kushagrasinghx/BitChord) — `v1.9.1`，GPL-3.0，UI 基线与播放层
- [EchoMusic](https://github.com/hoowhoami/EchoMusic) — `v2.3.3-beta.1`，GPL-3.0，酷狗调用实践与人机验证流程
- [KuGouMusicApi](https://github.com/MakcRe/KuGouMusicApi) — MIT，接口与签名算法真源

详见 `NOTICE` 与 `THIRD_PARTY_NOTICES.md`。

## 合规与免责声明

本项目基于 **GNU GPL v3.0** 发布，完整文本见 `LICENSE`。它是基于公开接口开发的第三方客户端，**仅供个人学习和技术研究使用**：

- 不存储、不传播、不托管任何音频文件，音乐内容版权归原平台及版权方所有；
- 禁止用于任何商业用途或违法行为，不接受广告、捐赠与商业合作；
- Apple Music 仅作为界面风格参考，不 1:1 复刻其图标、商标与专有资源；本项目不使用酷狗或 Apple 商标；
- 因使用本项目产生的任何法律纠纷或损失，由使用者自行承担。

音乐平台不易，请尊重版权，支持正版。
