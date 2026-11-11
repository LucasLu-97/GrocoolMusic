# 云端构建与测试版发布

> 目标：**本机不需要安装 JDK、Gradle、Android SDK 或 Android Studio**。
> 所有编译、签名、打包、发布都在 GitHub Actions 完成；本机只需要编辑源码与提交。

## 工作流

| 工作流 | 触发 | 作用 |
| --- | --- | --- |
| `.github/workflows/check.yml` | push 到 `main` / PR / 手动 | JDK 17 + Gradle 8.10.2 + Android SDK 35；编译 `kugou` 与 `app` debug 包，跑签名 golden 测试 |
| `.github/workflows/release.yml` | 手动 `workflow_dispatch` 或 push `v*` tag | 解码统一密钥 → 编译签名 release APK → `apksigner` 校验 → 发布 **pre-release** |
| `.github/workflows/smoke.yml` | 手动 `workflow_dispatch` | 用 `tools/smoke_test.py` 打真实酷狗网关，验证签名 / 搜索 / 取流 |

> `smoke.yml` 是**诊断工作流**：GitHub runner 位于境外，实测搜索可过但取流会被
> 酷狗风控拦截（`ssa-code` + `errcode 20028 本次请求需要验证`），因此步骤标记
> `continue-on-error`，不阻断发布。M0 验收以本机 / 国内网络运行结果为准，
> 详见 `docs/M0-Smoke-Test.md`。同时这也说明人机验证流程是 M3/M4 的必需工作。

## 发一个测试版

1. 打开仓库 **Actions → release → Run workflow**，填版本号（例如 `0.1.0`）。
2. 工作流会生成 tag `v0.1.0-build.<run_number>` 与同名 pre-release，附带
   `GrocoolMusic-0.1.0-release.apk`。
3. 也可以本地 `git tag v0.1.0 && git push origin v0.1.0` 触发，tag 即发布标识。

## 统一签名密钥

私钥**不在仓库里**，以四个仓库 Actions Secrets 保存：

| Secret | 内容 |
| --- | --- |
| `KEYSTORE_BASE64` | PKCS12 keystore 的 base64 |
| `STORE_PASSWORD` | keystore 密码 |
| `KEY_ALIAS` | `grocool-release` |
| `KEY_PASSWORD` | 私钥密码 |

密钥本体与密码备份在**仓库之外**的用户目录：

```
C:\Users\Lucas.Lu\.workbuddy\secrets\
    grocool-release.p12        # 唯一发布密钥
    keystore-password.txt      # 密码
    keystore-base64.txt        # CI 用的 base64
    release-key.pem / release-cert.pem
```

> 请自行把 `grocool-release.p12` 与密码再备份到一处离线安全位置。
> **密钥一旦丢失，后续版本就无法覆盖安装，只能卸载重装并丢失本地数据。**

## 覆盖安装的四个必要条件

1. `applicationId` 固定为 `dev.grocool.music`（不可改动）。
2. `versionCode` 严格递增：由 CI 注入 `github.run_number`，天然单调。
3. release 签名证书一致：所有版本都用同一个 keystore 与 alias。
4. 产物为非 debuggable 的 release 包。

调试包已用 `applicationIdSuffix = ".debug"` 与正式包**并存**（`dev.grocool.music.debug`），
避免本地调试包覆盖统一签名的正式包。注意小米真机对 USB 安装会弹「允许安装」确认
（`INSTALL_FAILED_USER_RESTRICTED`），需手机侧点一次；日常发版仍走 release 覆盖安装。

## 覆盖安装实证（2026-11-11）

| 版本 | tag | versionCode（`github.run_number`） | APK 大小 |
| --- | --- | --- | --- |
| 0.1.0 | `v0.1.0-build.3` | 3 | 817593 bytes |
| 0.1.1 | `v0.1.1-build.4` | 4 | 818405 bytes |
| 0.2.0 | `v0.2.0-build.5` | 5 | 12859413 bytes（M1，含 Compose + Media3） |
| 0.2.1 | `v0.2.1-build.6` | 6 | 12859413 bytes（MID 热修复） |
| 0.3.0 | `v0.3.0-build.7` | 7 | 12924953 bytes（M2 内容层） |

- 每次发布都由同一个 `KEYSTORE_BASE64` 解码签名，CI 的 `apksigner verify` 均通过。
- 下载 0.1.0 / 0.1.1 的 APK 并解析 **APK Signing Block**（v2，pair ID `0x7109871a`），提取到的 X.509 证书 DER 的 SHA-256 前缀**同为 `0535ad27813ce0ca`** → 确认是同一张签名证书。
- 0.2.0 的发布日志给出了官方指纹：`apksigner --print-certs` 输出
  `V2 Signer: certificate DN: CN=Grocool Music Release, O=GrocoolMusic, C=CN`，
  `SHA-256 digest: 0535ad27813ce0ca074b91fcef2944ea8c0c48f61e102f4d6daaed7baea8901c` —— 前缀一致，三版同证书。
- `applicationId` 固定、`versionCode` 3 → 4 递增 → **覆盖安装四要素全部成立**。
- 真机实测方法：先装 0.1.0，再直接安装 0.1.1，应无需卸载、本地数据保留。
- `release.yml` 的 `Verify APK is signed` 步骤已加 `--print-certs`，并把证书指纹写入 job summary，后续每个版本都可直接比对。

## 密钥轮换

若需更换密钥（例如 PAT 或密钥疑似泄露）：

1. 生成新的 PKCS12 与随机密码；
2. 用 `C:\Users\Lucas.Lu\.workbuddy\tmp\set_secrets.py` 同款方式，通过 GitHub
   Actions Secrets public key 加密覆写四个 Secret；
3. **注意**：换签名证书会破坏覆盖安装，只能在尚未正式分发前做，或提前准备好迁移方案。

## 本机开发的边界

- 写代码、改文档、看 CI 日志：本机够用。
- 编译验证：交给 `check.yml`，不需要本地 JDK。
- 真机调试（M1 起）：仍然需要一台手机做**有线 ADB 连接**，但电脑侧不需要
  Android Studio —— 可以用 CI 产出的 APK + 平台工具 `adb install -r` 安装；
  若要做断点调试与性能分析，再考虑装 Android Studio。
- 当前环境的 git 协议走代理被拦（CONNECT 502），远程提交通过 GitHub Git Data API
  推送；用户本机正常网络下可直接 `git push`。

## Release 资产下载加速

本机代理下 GitHub release 直链返回 `CONNECT tunnel 502`，而 `api.github.com`
的 asset 端点实测只有约 27 KB/s（12.8 MB 要下 8 分钟）。为此提供
`tools/download_release.py`：**并发测速 10 个镜像 → 选最快 → 复用探测时已下完的文件 → 校验 APK**。

```bash
python tools/download_release.py                       # 最新 release
python tools/download_release.py --tag v0.3.0-build.7
python tools/download_release.py --out /path/app.apk --probe-seconds 10
```

2026-11-11 实测（6~10 秒探测窗口）：

| 线路 | 速度 | 结果 |
| --- | --- | --- |
| **`gh-proxy.com`** | **约 1.2~2.1 MB/s** | 探测窗口内即完整下载 12.8 MB，最优 |
| `ghfast.top` | 约 170~310 KB/s | 可用，次选 |
| `ghproxy.net` | 约 7~35 KB/s | 可用但慢 |
| 其余 7 条 / 直链 | 0 或返回非 APK | 不可用 |

脚本会校验文件大小、zip 头、`AndroidManifest.xml` 与 dex，并输出 sha256；
某条线路不完整时自动尝试下一条。默认输出到
`C:\Users\Lucas.Lu\.workbuddy\tmp\<asset-name>`（仓库外）。

## 已知坑

- **不要用 `android-actions/setup-android@v3`**：在 ubuntu-24.04 runner 上，它判定预装 cmdline-tools 版本「不对」而重新下载，随后在 `sdkmanager --licenses` 的 `(y/N)?` 交互提示处无人应答、直接 exit 1。两条工作流都改为使用 runner 预装 SDK
  （`/usr/local/lib/android/sdk`），显式 `yes | sdkmanager --licenses` 并按需安装 platform 35 / build-tools 35.0.0。
- **空仓库不能直接建 blob**：Git Data API 对空仓库返回 409 `Git Repository is empty`，需先用 contents API 写入一个种子文件初始化默认分支。
- `kugou` 的网络实现必须用 `HttpURLConnection`，不能用 `java.net.http.HttpClient` —— 后者在 Android 运行时不存在。
- **引入 AndroidX / Compose / Media3 后必须在 `gradle.properties` 打开 `android.useAndroidX=true`**，否则 `:app:checkDebugAarMetadata` 直接报 "contains AndroidX dependencies, but the android.useAndroidX property is not enabled"。M1 首次编译即踩此坑。
- **`adb devices -l` 的输出列用空格对齐，不带 `-l` 才用 TAB**：`tools/adb_verify.py` 原按 `"\tdevice"` 匹配，导致设备明明在线却永远判定「no device」、后台任务空跑 10 分钟。已改为解析 `line.split()[1]`；并新增**纯只读**的 `tools/adb_watch.py`（人手动点屏幕、脚本只采集 logcat 与屏幕文字），避免自动点击在锁屏 / App 不在前台时静默失效。真机验收结果与证据见 `docs/M1-Playback.md`。
- **设备 MID 不能为空**：真机首版搜索无结果，根因是 `KugouCredentials.mid=""` 导致网关返回 `status=0 / error_code=20006`。用独立冒烟脚本复现确认后改为 32 位 hex 默认值；App 层必须解析并展示响应状态，不能把失败一律当成「空结果」。

## M0 骨架 APK 的定位

`app/` 目前是**最小验证壳**：一个 `Activity` + `kugou` 协议层的签名自检显示，
不含 Compose、Media3 或任何在线音乐功能。它只用于验证「CI 编译 → 统一签名 →
pre-release → 覆盖安装」这条链路是否成立。M1 导入 BitChord UI 基线时会被完整替换。
