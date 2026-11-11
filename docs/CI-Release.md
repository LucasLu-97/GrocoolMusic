# 云端构建与测试版发布

> 目标：**本机不需要安装 JDK、Gradle、Android SDK 或 Android Studio**。
> 所有编译、签名、打包、发布都在 GitHub Actions 完成；本机只需要编辑源码与提交。

## 两条工作流

| 工作流 | 触发 | 作用 |
| --- | --- | --- |
| `.github/workflows/check.yml` | push 到 `main` / PR / 手动 | JDK 17 + Gradle 8.10.2 + Android SDK 35；编译 `kugou` 与 `app` debug 包，跑签名 golden 测试 |
| `.github/workflows/release.yml` | 手动 `workflow_dispatch` 或 push `v*` tag | 解码统一密钥 → 编译签名 release APK → `apksigner` 校验 → 发布 **pre-release** |

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

调试包建议另用 flavor / `applicationIdSuffix`，避免覆盖正式包（M1 导入 BitChord 时沿用其 `dev` / `prod` flavor 思路）。

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

## M0 骨架 APK 的定位

`app/` 目前是**最小验证壳**：一个 `Activity` + `kugou` 协议层的签名自检显示，
不含 Compose、Media3 或任何在线音乐功能。它只用于验证「CI 编译 → 统一签名 →
pre-release → 覆盖安装」这条链路是否成立。M1 导入 BitChord UI 基线时会被完整替换。
