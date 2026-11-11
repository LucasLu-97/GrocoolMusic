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
- [ ] 真机实测：搜索「儿歌」→ 点一首免费曲目 → **出声**
- [ ] 真机实测：VIP 曲目显示试听或明确提示，不崩溃

真机验收需要**有线 ADB 连接**：`adb devices` 确认设备在线后 `adb install -r <apk>`，
用 `adb logcat` 观察取流与播放器错误。到这一步会主动提醒发起人插线。

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

## ADB 自动化验证

`tools/adb_verify.py` 把真机验证做成一条命令：等待设备上线 → `adb install -r`
覆盖安装 → 启动 App → 用 `uiautomator dump` 找到「搜索」按钮并点击 → 点击第一首
「免费」曲目 → 抓 `adb logcat -d -s GrocoolMusic` → 输出结论（可写 JSON 结果文件）。

```bash
python tools/adb_verify.py --apk C:/path/GrocoolMusic-0.2.1-release.apk --wait 600 \
  --result-file C:/tmp/adb_verify_result.json
```

本机 adb 可执行文件：`D:\VibeCodingSpace\tools\platform-tools\adb.exe`（v37.0.1）。

**2026-11-11 环境诊断**：`adb devices` 持续为空。WinUSB 驱动本身正常
（`DEVPKEY_Device_Service=WINUSB`、`HasProblem=False`），但
`DEVPKEY_Device_IsPresent=False`，且 `Get-PnpDevice -PresentOnly` 过滤后没有任何
Xiaomi / Android 设备 —— 系统里那些 `USB\VID_18D1&PID_4E11` 记录是**历史条目，
手机当前并未以数据/ADB 模式在线**。网络 ADB（`10.42.0.1:5555`）与 mDNS 也均无响应。

因此在手机侧完成一次以下操作后，上面的脚本会自动接管，无需再手动装包/点按：

1. 换一条**支持数据传输**的 USB 线（充电线不行），或换一个 USB 口；
2. 下拉通知栏把 USB 用途从「仅充电」改为「传输文件 (MTP)」；
3. 确认「允许 USB 调试？」弹窗点「允许」（可勾选「一律允许」）；若仍无设备，
   在开发者选项里「撤销 USB 调试授权」后重新插拔。

备选：若手机开启的是 Android 11+ **无线调试**，把开发者选项里显示的
`IP:端口` 给我，用 `adb connect <IP>:<端口>` 即可全自动，不依赖 USB 驱动状态。

## 发布状态

- `v0.2.0-build.5`（pre-release，`GrocoolMusic-0.2.0-release.apk`，12.8 MB）：首个可搜歌播放版本；CI 编译 + 签名校验 + golden 测试全绿。**真机搜索无结果，根因见上节，已由 0.2.1 修复**。
- `v0.2.1-build.6`（pre-release，12.8 MB）：修复设备 MID 为空导致的 `error_code=20006`，并让网关错误可见。请覆盖安装此版本重新验证。
- 与 0.1.x / 0.2.0 使用同一张签名证书（`0535ad27813ce0ca...`），`versionCode` 递增，可直接覆盖安装。
- 真机验收尚未完成，需要**有线 ADB**：`adb devices` → `adb install -r GrocoolMusic-0.2.1-release.apk` → `adb logcat -d -s GrocoolMusic` 观察搜索与取流状态。

## 已知限制（有意为之）

- 播放只在 Activity 生命周期内，无后台播放 / 通知栏 / 锁屏控制 —— M2 换 `MediaSessionService` + 前台服务。
- 无封面、无缓存、无歌词、无播放队列、无历史记录。
- 无登录、无收藏、无歌单、无评论 —— M2/M3 的内容层。
- 未导入 BitChord 的 `sharedUi`；本阶段先把「真实 URL 能出声」这条链路跑通，避免一次性引入 1409 个上游文件把 CI 拖垮。
