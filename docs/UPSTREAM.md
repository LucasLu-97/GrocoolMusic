# 上游版本基线与同步规则

> 所有 agent 在开发前必须确认本文件中的 commit 未被静默切换。
> 基线日期：2026-11-11。

## 锁定的上游

| 项目 | 用途 | 版本 / Commit | 日期 | License |
| --- | --- | --- | --- | --- |
| [BitChord](https://github.com/kushagrasinghx/BitChord) | UI 基线、播放层、工程骨架 | `v1.9.1` / `acf219a7e4a7` | 2026-11-06 | GPL-3.0 |
| [EchoMusic](https://github.com/hoowhoami/EchoMusic) | 酷狗调用实践、人机验证、M5 桌面端候选 | `v2.3.3-beta.1`（稳定 `v2.3.2`）/ `f3c6964512f2` | 2026-11-09 | GPL-3.0 |
| [KuGouMusicApi](https://github.com/MakcRe/KuGouMusicApi) | 接口与签名算法真源 | main / `7ac64e1cdd33` | 2026-11-04 | MIT |

## 规模参考（基线时刻）

- BitChord：1409 个文件、681 个 Kotlin 文件、`/ui/` 下 227 个；`MainViewModel.kt` 3639 行 / 131 函数；`YtMusicRepository.kt` 1253 行 / 67 函数。
- EchoMusic：1670 个文件、1046 个 TS/Vue 文件、`src/renderer/api/` 17 个模块；Electron 43 + Vue 3.5 + TS 5.9 + Rust NAPI。
- KuGouMusicApi：284 个文件、`module/` 下 236 个接口实现、README 对外列 132 条；概念版 youth/lite 专属 24 个。

## 同步规则

1. 仓库保留 `upstream/bitchord` 分支，**只镜像上游，禁止写业务代码**。
2. 上游发新 tag → 更新 `upstream/bitchord` → 生成 diff → **只挑选** `sharedUi/`、`app/ui/`、主题与资源文件。
3. 合并进 `main` 后，冲突必须全部落在 `adapter/`；若冲突出现在 UI 深处，说明分层被破坏，先修架构再同步。
4. **禁止**合并上游 `shared/` 数据层、`YtMusicRepository`、`Innertube*` 与任何 C 类国际服务目录。
5. 每次同步在本文件追加一行记录：日期、上游 tag、同步范围、冲突文件数、回归结果。
6. **禁止全量月度合并**：BitChord 迭代速度（3 个月 1409 文件 / 424 open issues）会反复撕裂数据层。

## 变更记录

| 日期 | 上游 | 版本 | 范围 | 备注 |
| --- | --- | --- | --- | --- |
| 2026-11-11 | 三个上游 | 见上表 | 仅调研与契约提取 | M0 起点，尚未导入 UI 代码 |
