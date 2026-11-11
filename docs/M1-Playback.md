# M1 播放闭环与 Apple Music 风格 UI 起点

> 目标：把 M0 的 `kugou` 协议层真正接进安卓，用 Media3 播放真实酷狗 URL，
> 并把界面从「签名自检」的最小壳换成 Compose 风格基线。

## 数据流

```
搜索框 → KugouMusicRepository.search (IO)
       → KugouApi.search → KugouResponse.searchTracks → List<KugouTrack>
点击曲目 → KugouMusicRepository.streamUrl (IO)
       → KugouApi.songUrl → KugouResponse.streamUrl → CDN URL
       → MusicPlayer(ExoPlayer).play(url)
```

| 文件 | 职责 |
| --- | --- |
| `app/.../KugouMusicRepository.kt` | suspend 封装，所有网络调用在 `Dispatchers.IO` |
| `app/.../MusicPlayer.kt` | ExoPlayer 最小封装（播放 / 暂停 / 释放） |
| `app/.../MainActivity.kt` | Compose 界面：搜索、结果列表、底部迷你播放器 |
| `kugou/.../KugouResponse.kt` | `searchTracks` / `streamUrl` 轻量解析 + `KugouTrack` DTO |

## 三条硬约束（踩过才知道）

1. **明文流量**：酷狗 CDN 实测返回 `http://fs.youthandroid2.kugou.com/...`，Android 9+ 默认禁止明文，manifest 必须保留 `android:usesCleartextTraffic="true"`，否则 Media3 会静默播放失败。
2. **网络不能在主线程**：`DefaultHttpTransport` 用 `HttpURLConnection`，必须在 `Dispatchers.IO` 调用；Repository 已统一封装，UI 层不得直接调 `KugouApi`。
3. **VIP 曲目**：`Privilege=10` 未登录时 `status=2`。当前对 VIP 曲自动用 `freePart=true` 取试听片段；完整播放要等 M3/M4 的登录态与概念版免费听 Auth。界面用「VIP / 免费」标签区分。

## Apple Music 风格基线

- 强调色 `#FA233B`，浅色背景 `#F5F5F7`，深色背景 `#121214`，深浅色跟随系统。
- 大标题、圆角卡片列表、底部迷你播放器、专辑位占位方块。
- **仅作风格参考**：不复用 Apple 的图标、字体、动效与商标资源，也不 1:1 复刻 BitChord 的品牌资源。
- BitChord `v1.9.1` 的完整 UI 基线在本阶段后段按 tag 挑选导入，所有自有改动继续隔离在 `adapter/`（见 `docs/UPSTREAM.md`）。

## 完成标志与验收

- [x] Compose + Media3 + 协程依赖接入，CI 编译 release / debug 通过
- [x] 真机实测：搜索「儿歌」→ 点一首免费曲目 → **出声**（2026-11-11，见下节证据）
- [x] 真机实测：VIP 曲目有明确标签且不崩溃（列表中「两只老虎 / 小兔子乖乖 / 虫儿飞」等显示 VIP，点击免费曲正常）

真机验收使用**有线 ADB**：`adb devices` 确认设备在线 → `adb install -r <apk>` →
`adb logcat -d -s GrocoolMusic` 观察取流状态。到这一步会主动提醒发起人插线。

## 真机验收结果（2026-11-11，M1 收官）

设备 `13a4bf99`（Xiaomi 17 / `25113PN0EC`），`v0.2.1-build.6`（versionCode 6），
由发起人手动点击屏幕，本环境只做只读采集（`tools/adb_watch.py`）。

| 证据 | 内容 |
| --- | --- |
| 搜索日志 | `D GrocoolMusic: search status=1 error=0 msg=null bytes=84701` |
| 取流日志 | `D GrocoolMusic: stream status=1 error=null msg=null bytes=1906` |
| 界面状态 | `正在播放：卖报歌`；列表显示「免费 / VIP」标签与歌手、专辑信息 |
| **真实出声** | `D AudioTrackImpl: [audioTrackData][fine] 50s(f:50478 ...) : pid 19294 uid 10408 sessionId 72689 sr 44100 ch 2 fmt 1 maxAmplitude 6561` |
| 崩溃 | `fatal_lines` 为空，无 `AndroidRuntime` / `FATAL EXCEPTION` |

`AudioTrackImpl` 这一行是硬证据：App 进程（pid 19294）建立了 44.1 kHz / 双声道 /
PCM 音轨并持续输出 **50 秒**，`maxAmplitude=6561` 非零 —— 不是静音，也不是只改了状态文字。
结论：**搜索 → 取流 → Media3 出声闭环在真机上成立**，M1 验收通过。

## 真机反馈与根因（2026-11-11）

首个真机版本 `v0.2.0-build.5` 反馈「搜索没有结果」。用 `tools/smoke_test.py`
独立复现请求参数，定位到 **`KugouCredentials.mid` 默认为空字符串**：

| mid | 网关响应 |
| --- | --- |
| 32 位 hex（冒烟脚本基线） | `status=1`，30 条结果 |
| **空字符串（Kotlin 原默认值）** | **`status=0`、`error_code=20006`，0 条结果** |
| `"-"` | `status=1`，30 条结果 |

修复：默认 mid 改为与冒烟脚本一致的 32 位 hex；M2 起改用基于 `ANDROID_ID`
的设备级标识并持久化。同时在 `KugouResponse.state()` 中解析响应状态，
`KugouMusicRepository` 在 `status != 1` 时抛出带错误码的异常，并用
`Log.d("GrocoolMusic", ...)` 把状态打到 logcat —— 避免再把任何失败都显示成「没有结果」。

> 排查命令（有线 ADB）：`adb logcat -d -s GrocoolMusic`；
> 界面上也会直接显示 `酷狗返回 status=..., error_code=...`。

## ADB 工具与分工

本机 adb 可执行文件：`D:\VibeCodingSpace\tools\platform-tools\adb.exe`（v37.0.1）。
2026-11-11 设备已成功连通：`13a4bf99   device`（早期 `PresentOnly` 为空是手机尚未
以数据/ADB 模式握手，重插并授权后即恢复；WinUSB 驱动全程正常、无故障码）。

| 工具 | 行为 | 适用场景 |
| --- | --- | --- |
| `tools/adb_verify.py` | 等待设备 → `install -r` → 启动 → **自动点击**搜索与免费曲目 → 抓日志 | 无人值守、屏幕已解锁且 App 在前台 |
| `tools/adb_watch.py` | **纯只读**：只轮询 `logcat -d` 与结束时读一次屏幕文字，绝不 tap / swipe / 唤醒 / 装包 | **推荐**：由人手动点屏幕，本环境只采集分析 |

```bash
# 推荐流程：先清日志，再启动只读采集，然后人工操作手机
adb logcat -c
python tools/adb_watch.py --duration 900 --result-file C:/tmp/watch.json
```

`adb_watch.py` 每轮增量写结果文件，检测到 `stream status=` 后再收 8 秒自动结束，
并给出 `PLAYBACK_OK / SEARCH_OK_NO_PLAY / GATEWAY_ERROR / NO_APP_LOGS` 判定。
真机验收采用此模式：人点屏幕、脚本只读，互不干扰。

**两个已修的工具坑**：

1. `adb devices -l` 的列用**空格**对齐（`13a4bf99      device product:...`），
   而不带 `-l` 时用 **TAB**。原 `adb_verify.py` 按 `"\tdevice"` 匹配，导致设备明明在线
   却永远判定「no device」、后台任务空跑 10 分钟。已改为解析 `line.split()[1]`。
2. 自动点击在**屏幕锁定 / App 不在前台**时会静默失效（UI dump 能拿到按钮，但
   tap 不触发，`status_line` 为空）。因此默认推荐 `adb_watch.py` 人工点击模式，
   不要把 `adb_verify.py` 返回 1 直接当成「功能失败」。

下载 Release 用 `tools/download_release.py`：并发测速 10 个镜像 → 选最优 →
复用探测阶段已下完的文件 → 校验 zip / `AndroidManifest.xml` / dex / sha256。
实测 `gh-proxy.com` 约 1.2–2.1 MB/s，12.8 MB APK 在 6–10 秒内下完；
直链在本环境被代理拦（`CONNECT 502`），其余多数镜像不可用。

## 发布状态

- `v0.2.0-build.5`（pre-release，`GrocoolMusic-0.2.0-release.apk`，12.8 MB）：首个可搜歌播放版本；CI 编译 + 签名校验 + golden 测试全绿。**真机搜索无结果，根因见上节，已由 0.2.1 修复**。
- `v0.2.1-build.6`（pre-release，12,859,413 bytes，sha256 `d971601e…`）：修复设备 MID 为空导致的 `error_code=20006`，并让网关错误可见。**真机验收通过（搜索 / 取流 / 出声全链路）**。
- 与 0.1.x / 0.2.0 使用同一张签名证书（`0535ad27813ce0ca...`），`versionCode` 3→4→5→6 递增，可直接覆盖安装；本次真机覆盖安装 `Success`。
- 至此 M1 完成，下一步是 M1 后段：按 tag 导入 BitChord `v1.9.1` UI 基线并裁剪国际服务，随后进入 M2 内容层。

## 已知限制（有意为之）

- 播放只在 Activity 生命周期内，无后台播放 / 通知栏 / 锁屏控制 —— M2 换 `MediaSessionService` + 前台服务。
- 无封面、无缓存、无歌词、无播放队列、无历史记录。
- 无登录、无收藏、无歌单、无评论 —— M2/M3 的内容层。
- 未导入 BitChord 的 `sharedUi`；本阶段先把「真实 URL 能出声」这条链路跑通，避免一次性引入 1409 个上游文件把 CI 拖垮。
