# M0 酷狗请求签名层协议规格

> 依据：KuGouMusicApi @ `7ac64e1cdd33` 的 `util/request.js`（410 行）、`util/crypto.js`（318 行）、
> `util/helper.js`（189 行）、`util/config.json`，以及 `module/search.js` / `song_url.js` / `lyric.js`。
> Kotlin 实现见 `kugou/src/main/kotlin/dev/grocool/kugou/`。本文是**实现依据**，不是推测。

## 1. 平台配置

| 项 | 标准版 | 概念版 lite |
| --- | --- | --- |
| `appid` | 1005 | **3116** |
| `clientver` | 20489 | **11440** |
| Android 签名盐 | `OIlwieks28dk2k092lksi2UIkp` | `LnT6xpN3khm36zse0QzvmgTZ3waWdRSA` |
| signKey 盐 | `57ae12eb6890223e355ccfcb74edf70d` | `185672dd44712f60bb1736df5a377e82` |
| RSA 公钥 | `crypto.js` 中 `publicRasKey` | `publicLiteRasKey`（**不同**） |
| 云歌单 appkey | `OIlwieks28dk2k092lksi2UIkp` | `LnT6xpN3khm36zse0QzvmgTZ3waWdRSA` |

**硬约束**：`platform=lite` 只是切换同一套接口服务的分支，**两平台 token 不通用**，凭据必须双槽隔离。

## 2. 默认参数（每个请求自动注入）

`dfid`（默认 `-`）、`mid`、`uuid`（固定 `-`）、`appid`、`clientver`、`clienttime`（Unix 秒）；
登录时额外注入 `token` 与 `userid`。`clearDefaultParams=true` 时不注入。

## 3. 默认请求头

```
User-Agent: Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi
dfid / clienttime / mid : 与参数一致
kg-rc    : 1
kg-thash : 5d816a0
kg-rec   : 1
kg-rf    : B9EDA08A64250DEFFBCADDEE00F8F25F
```
可选 `X-Real-IP` / `X-Forwarded-For`（IP 透传）。默认网关 `https://gateway.kugou.com`；
接口可以用 `x-router` 头改路由（如 `complexsearch.kugou.com`、`trackercdn.kugou.com`），
歌词接口使用独立域名 `https://lyrics.kugou.com`。

## 4. 签名算法（全部为 MD5 + UTF-8）

| 算法 | 规则 |
| --- | --- |
| `signatureAndroidParams` | params 按 **key 排序** → `key=value` 拼接（对象值 `JSON.stringify`）→ `MD5(salt + 串 + body + salt)` |
| `signatureWebParams` | 先生成 `key=value` 数组，再对**字符串数组整体排序** → `MD5(NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt + 串 + body + 同一盐)` |
| `signatureRegisterParams` | 只取**值**、排序 → `MD5("1014" + 值串 + "1014")` |
| `signParams` | 排序后 `key+value`（无等号）→ `MD5(串 + body + R6snCXJgbCaj9WFRJKefTMIFp0ey6Gza)` |
| `signKey` | `MD5(hash + 盐 + appid + mid + userid)`，`encryptKey=true` 时作为 `key` 参数 |
| `signParamsKey` | `MD5(appid + 盐 + clientver + data)` |
| `signCloudKey` | `MD5("musicclound" + hash + pid + ebd1ac3134c880bda6a2194537843caa0162e2e7)` |

> 最易错的三处：盐值随平台变、Android 版按 key 排序而 Web 版按整串排序、注册签名只取 value。
> `kugou` 模块的 `SignatureGoldenTest` 用 Node 独立算出的 5 个 golden 值锁定这些行为。

## 5. 加密与设备标识

- MD5 / AES-CBC(PKCS7) / RSA（PKCS1 与 raw 两种）见 `util/crypto.js`；标准版与 lite 各一套 1024-bit RSA 公钥，已登记在 `KugouCrypto`。
- `randomString(len)` 字符池 `1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ`；`song_url.js` 用 `randomString(24)` 兜底生成 dfid。
- `calculateMid` 由 UUID 推导 mid（见 `util/util.js`），M0 允许外部注入。
- **云歌单协议（`createCloudRequest`）是另一套**：`key = MD5(appid+appkey+clientver+clienttime)`，`p` = RSA(PKCS1) 加密 `{aes, uid, token}` 的大写 Hex，请求/响应体用 `MD5(aes)` 前 16 / 后 16 字节作 AES key / IV 做 CBC 加解密。M3 云盘能力时再实现。

## 6. 风控与二次验证

- 响应头 `ssa-code` 出现即表示需要二次验证；上游会调用 `generateSimulate(mid, userid, dfid, webglHash)` 生成 `sid` / `edt` 附加到响应体。
- 响应体 `status === 0` 或 `error_code !== 0` 视为失败（上游映射为 HTTP 502）。
- EchoMusic 的 `KugouVerificationFlow.vue` / `kugouVerification.ts` 是**唯一可靠的客户端处理实践**，M3/M4 必须照其流程实现可恢复的验证界面，不能只抛异常。

## 7. 三个 M0 接口的参数要点

| 接口 | 路径 | 关键点 |
| --- | --- | --- |
| 搜索 | `/v2/search/song` | `keyword/page/pagesize/platform=AndroidFilter/iscorrection/privilegefilter/area_code/dopicfull`；**lite 额外把 `clientver` 覆盖为 201**；`x-router: complexsearch.kugou.com` |
| 取流 | `/v5/url` | `hash` 小写、`quality` 默认 128、`version=11430`、`cmd=26`、`pidversion=3001`；lite 的 `page_id=967177915`、`pid=411`、`ppage_id=356753938,823673182,967485191`（标准版分别是 151369488 / 2 / 463467626,350369493,788954147）；`x-router: trackercdn.kugou.com`；`encryptKey=true` |
| 歌词 | `/download`（lyrics.kugou.com） | `ver=1/client=android/id/accesskey/fmt=krc/charset=utf8`；`fmt=lrc` 或 `contenttype != 0` 时 content 为 base64，否则需 KRC 解码 |

## 8. 已知不一致与待验证项（重要）

1. `module/song_url.js` 传 `notSign: true`，但 `util/request.js` 读取的是 `options.notSignature`。若 `app.js` 未做映射，则该参数**不生效**，签名仍会生成。本项目统一为 `skipSignature`，**默认按生成签名处理**，需在国内真机抓包复核。
2. `song_url` 的 `clientver=11430` 会覆盖默认的 11440（标准版为 20489），签名必须基于覆盖后的最终参数计算。
3. 概念版搜索的 `clientver=201` 同理。
4. `openapicdn` 类 baseURL 需把参数拼进 URL 而非 query（M0 未涉及）。
5. `POST` 请求的 body 需参与签名（M0 全为 GET，body 为空串）；M2 之后接入 POST 接口时必须补齐。
6. 上游接口与风控策略会变；`kugou/` 模块必须保持独立，改动时先跑 golden 测试。
