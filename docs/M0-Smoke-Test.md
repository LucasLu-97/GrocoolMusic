# M0 真实接口冒烟测试

> 日期：2026-11-11 　工具：`tools/smoke_test.py`（Python 标准库独立实现，零依赖）
> 目的：验证 `kugou/` 协议层的签名与请求构造是否被**真实酷狗网关**接受，而不是只通过本地 golden 自测。
> 脚本刻意与 Kotlin 实现分离编写，避免「自己验自己」。

## 结论

**M0 协议层验收通过**：概念版 lite 与标准版 standard 双平台验签成功；搜索返回真实数据；取流拿到真实可播放 CDN URL。

```bash
python tools/smoke_test.py --keyword 儿歌          # PASS，返回可播放 URL
python tools/smoke_test.py --keyword 周杰伦 --skip-signature   # 预期 FAIL：err signature
```

## 实测结果矩阵

| 用例 | 结果 |
| --- | --- |
| lite 搜索「周杰伦」 | HTTP 200，`status=1`，`error_code=0`，`total=480`，返回 30 条真实歌曲 |
| **去掉 signature** | `status=0`，`error_code=20006`，`err signature` → **签名必需** |
| standard 搜索「周杰伦」 | HTTP 200，`status=1`，`total=480` → 标准版 appid / clientver / 盐值同样有效 |
| `privilegefilter=1` 搜索 | 返回 30 条，全部 `Privilege=0` → 可用于筛选免费曲目 |
| `/v5/url` 免 VIP 曲目（儿歌，`Privilege=8`） | `status=1`，`url` 数组（CDN `fs.youthandroid2.kugou.com`）、`bitRate=128000`、`backupUrl`、`timeLength` |
| `/v5/url` VIP 曲目（`Privilege=10`） | `status=2`，`fail_process=["pkg","buy"]`，`hash_offset` 试听区段，无 `url` |
| `/v5/url` VIP 曲目 + `IsFreePart=1` | `status=1`，返回试听片段 URL |
| `ssa-code` 风控头 | 本次请求量小，未触发 |

## 定案与发现

1. **`notSign` 歧义已定案**：`module/song_url.js` 传 `notSign: true`，但 `util/request.js` 读的是 `notSignature`；实测证明**不签名会被网关拒绝（20006）**，因此本项目 `KugouRequestBuilder.skipSignature` 默认 `false`（始终签名）是正确的，不要按上游字面改成跳过。
2. **lite 分支参数全部正确**：搜索 `clientver=201`、取流 `page_id=967177915` / `pid=411` / `ppage_id=356753938,...`，返回的 CDN 是概念版域名 `fs.youthandroid2.kugou.com`，说明平台切换生效。
3. **`status=2` 不是协议错误**，是版权 / VIP 限制。完整播放 VIP 曲目需要登录 token 与概念版「免费听」Auth 链路（`song_auth` → `song_url_auth`），这是 M3/M4 的核心工作，也是 EchoMusic 实践者已经跑通的部分。
4. `encryptKey`（signKey）在取流接口上正常工作；未触发人机验证，但真实 App 高频请求时仍需按 `KugouVerificationFlow.vue` 实现可恢复流程。
5. 两套平台 token 不通用这一约束仍未验证（本次均为未登录请求），M3 登录时必须实测。

## `/v5/url` 响应结构要点

| 情形 | 字段 |
| --- | --- |
| 成功 | `status=1`、`url: string[]`、`backupUrl: string[]`、`bitRate`、`timeLength`、`extName`、`q`、`fileHead`、`trans_param` |
| 受限 | `status=2`、`priv_status`、`fail_process: ["pkg","buy"]`、`hash_offset`（试听区段 `start_ms`/`end_ms`）、`auth_through` |

## M0 完成标志核对

| 标志 | 状态 |
| --- | --- |
| Kotlin 公共签名层 + 三个接口 | ✅ 已实现，golden 测试与 CI 通过 |
| 国内网络下取到可播放 URL | ✅ 真实网关返回 CDN URL |
| 用 Media3 实际播放出声 | ⬜ 待真机 + 有线 ADB（M1） |
| 登录态 / 免费听 Auth | ⬜ M3 / M4 |
| 统一签名 APK 发布 | ✅ `v0.1.0-build.3` pre-release |

> 本测试仅用于验证协议正确性，不存储、不传播任何音频文件；返回的 URL 未写入仓库。

## 云端 CI 对照（境外 runner）

`smoke.yml` 在 GitHub Actions（境外 IP）上实测的结果与本机不同：

| 步骤 | 结果 |
| --- | --- |
| 搜索 | 验签通过，`status=1`（与本机一致） |
| 取流 | **被风控拦截**：响应头 `ssa-code: gz_tx_event_...`，响应体 `{"errcode":20028,"status":0,"error":"本次请求需要验证"}` |
| 负对照（不带签名） | 仍正确返回 `err signature` |

三条结论：

1. **国内网络取流可行，境外 IP 会触发酷狗 SSA 人机验证**，因此 `smoke.yml` 只能作为诊断工作流（已设 `continue-on-error`），不能当作 M0 验收依据；验收以本机 / 国内网络结果为准。
2. 真实 App **必须**实现 EchoMusic `KugouVerificationFlow.vue` / `kugouVerification.ts` 的可恢复验证流程与 `sid` / `edt` 行为指纹生成——这是 M3/M4 的硬性工作，不是可选项。这也再次印证「以实践者为准」的原则。
3. 不要在 CI 里高频打真实接口，以免风控策略升级；需要时按需手动触发。
