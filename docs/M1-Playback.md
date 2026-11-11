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

## 已知限制（有意为之）

- 播放只在 Activity 生命周期内，无后台播放 / 通知栏 / 锁屏控制 —— M2 换 `MediaSessionService` + 前台服务。
- 无封面、无缓存、无歌词、无播放队列、无历史记录。
- 无登录、无收藏、无歌单、无评论 —— M2/M3 的内容层。
- 未导入 BitChord 的 `sharedUi`；本阶段先把「真实 URL 能出声」这条链路跑通，避免一次性引入 1409 个上游文件把 CI 拖垮。
