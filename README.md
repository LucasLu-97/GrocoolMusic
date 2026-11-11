# Grocool Music

一个第三方在线音乐软件：**界面深度模仿 Apple Music，内容与功能完全移植酷狗音乐概念版**（功能等价，非逐项照搬）。

> 当前状态：**M1 播放闭环开发中**（M0 已完成，真实网关验签 / 搜索 / 取流均已跑通）。`app/` 已换成 Compose 的 Apple Music 风格起点并接入 Media3，`kugou/` 协议层可直接搜歌取流播放；云端构建 / 统一签名 / 测试版发布链路已验证可用。完整 UI 基线导入与内容层在 M1 后段 / M2 推进。

## 项目文档

| 文档 | 内容 |
| --- | --- |
| `docs/01-Project-Brief.md` | 修订后的项目需求说明（可独立转发给其他 AI / agent 阅读） |
| `docs/02-Implementation-Plan.md` | 实施规格与任务分解：架构契约、功能对照、里程碑、WP-0~WP-8 工作包 |
| `docs/UPSTREAM.md` | 三个上游仓库的锁定版本与同步规则 |
| `docs/M0-Protocol-Spec.md` | 酷狗请求签名层协议规格（M0 实现依据，含真实网关实测要点） |
| `docs/M0-Smoke-Test.md` | M0 真实接口冒烟测试结果矩阵与定案结论 |
| `docs/M1-Playback.md` | M1 播放闭环、明文流量约束与 Apple Music 风格基线 |
| `docs/CI-Release.md` | 云端构建、统一签名与测试版发布流程 |

## 架构

```
KugouApi（协议 / 签名） → KugouRepository（领域模型） → adapter/（隔离带） → BitChord UI + Media3 播放层
```

`kugou/` 是**独立 Kotlin/JVM 模块**，不依赖 Android SDK，可单独编译与测试；它对应 KuGouMusicApi 的 `util/request.js` + `util/crypto.js`，是后续所有 236 个接口的公共底座。

## 构建与发布（全云端）

**本机不需要安装 JDK / Gradle / Android SDK / Android Studio** —— 编译、签名、打包、发布全部由 GitHub Actions 完成。

| 工作流 | 触发 | 产物 |
| --- | --- | --- |
| `check.yml` | push / PR / 手动 | `kugou` 编译 + 签名 golden 测试 + `app` debug 包 |
| `release.yml` | Actions 手动运行或 `v*` tag | 已签名 APK + **测试版 pre-release** |
| `smoke.yml` | Actions 手动运行（诊断） | 真实酷狗网关验签 / 取流冒烟测试 |

发测试版：仓库 **Actions → release → Run workflow**，填版本号即可。统一签名密钥存于仓库 Actions Secrets，私钥本体不在仓库内，后续版本可直接覆盖安装。完整说明见 `docs/CI-Release.md`。

若仍想本地编译，需要 JDK 17 + Gradle 8.10.2 + Android SDK 35：

```bash
gradle :kugou:test :app:assembleDebug
```

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
