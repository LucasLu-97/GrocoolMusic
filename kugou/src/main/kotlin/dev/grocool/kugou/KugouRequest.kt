package dev.grocool.kugou

import java.net.URLEncoder

/** 登录态与设备标识。lite 与标准版的 token 必须分开存储，不可复用。 */
data class KugouCredentials(
    val dfid: String = "-",
    /**
     * 设备 MID。2026-11-11 真机反馈定位：**空字符串会让网关直接返回
     * `status=0 / error_code=20006`，搜索结果为空**（独立脚本复现确认）。
     * 上游 server.js 用 `calculateMid(UUID)` 生成 32 位 hex，这里先给一个
     * 与 M0 冒烟脚本一致的稳定非空默认值；M2 应替换为基于 ANDROID_ID
     * 的设备级标识并持久化。
     */
    val mid: String = "796b1ed3497b28a7c4f5626c84a88ef7",
    val token: String = "",
    val userId: Long = 0,
    val webglHash: String? = null,
)

enum class EncryptType { ANDROID, WEB, REGISTER }

/**
 * 对齐 KuGouMusicApi `util/request.js` 的 createRequest 配置项。
 *
 * 命名差异：上游 `module/song_url.js` 传的是 `notSign`，而 request.js 读取的是
 * `notSignature`；本项目统一为 [skipSignature]，是否跳过签名需以真机抓包复核。
 */
data class RequestOptions(
    val baseUrl: String = "https://gateway.kugou.com",
    val method: String = "GET",
    val encryptType: EncryptType = EncryptType.ANDROID,
    val extraHeaders: Map<String, String> = emptyMap(),
    val encryptKey: Boolean = false,
    val skipSignature: Boolean = false,
    val clearDefaultParams: Boolean = false,
    val clientIp: String = "",
    /**
     * POST 请求体（已序列化的 JSON 字符串）。
     *
     * 2026-11-11 真实网关实测确认：酷狗的 POST 接口把 **JSON.stringify(body)**
     * 拼进签名串（`salt + 排序参数串 + body + salt`）。不带 body 参与签名会返回
     * `status=0 / error_code=20006`；GET 的 body 为空串，不受影响。
     * 依据：`util/request.js:139` 与 `util/helper.js:61`；见 `docs/M2-Content.md`。
     */
    val body: String = "",
)

/** 构造完成的请求，交给 [HttpTransport] 发送。 */
data class BuiltRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val params: Map<String, String>,
    /** POST/PUT 请求体；GET 为空串。 */
    val body: String = "",
)

/**
 * 请求构造器：注入默认参数 → 生成 key / signature → 组装请求头 → 序列化查询串。
 *
 * 这是全部 236 个接口共用的唯一底座；新增接口只应写「参数表」，不得复制签名逻辑。
 */
class KugouRequestBuilder(
    private val platform: Platform,
    private val credentials: KugouCredentials,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    fun build(
        path: String,
        params: Map<String, Any?>,
        options: RequestOptions = RequestOptions(),
    ): BuiltRequest {
        val clientTime = clock()

        val defaults = buildMap<String, Any?> {
            put("dfid", credentials.dfid)
            put("mid", credentials.mid)
            put("uuid", "-")
            put("appid", platform.appId)
            put("clientver", platform.clientVer)
            put("clienttime", clientTime)
            if (credentials.token.isNotEmpty()) put("token", credentials.token)
            if (credentials.userId != 0L) put("userid", credentials.userId)
        }

        val merged: MutableMap<String, Any?> = if (options.clearDefaultParams) {
            params.toMutableMap()
        } else {
            (defaults + params).toMutableMap()
        }

        if (options.encryptKey) {
            merged["key"] = KugouCrypto.signKey(
                hash = merged["hash"]?.toString().orEmpty(),
                mid = merged["mid"]?.toString().orEmpty(),
                userId = credentials.userId,
                appId = (merged["appid"] as? Number)?.toInt() ?: platform.appId,
                platform = platform,
            )
        }

        // POST 的 JSON body 必须参与签名；GET 为空串。
        // 上游：const data = typeof options.data === 'object' ? JSON.stringify(options.data) : ''
        val body = options.body
        if (!merged.containsKey("signature") && !options.skipSignature) {
            merged["signature"] = when (options.encryptType) {
                EncryptType.ANDROID -> KugouCrypto.signatureAndroid(merged, platform, body)
                EncryptType.WEB -> KugouCrypto.signatureWeb(merged, body)
                EncryptType.REGISTER -> KugouCrypto.signatureRegister(merged)
            }
        }

        val headers = buildMap<String, String> {
            put("User-Agent", USER_AGENT)
            put("dfid", credentials.dfid)
            put("clienttime", (merged["clienttime"] ?: clientTime).toString())
            put("mid", credentials.mid)
            put("kg-rc", "1")
            put("kg-thash", "5d816a0")
            put("kg-rec", "1")
            put("kg-rf", "B9EDA08A64250DEFFBCADDEE00F8F25F")
            if (body.isNotEmpty()) put("Content-Type", "application/json")
            if (options.clientIp.isNotEmpty()) {
                put("X-Real-IP", options.clientIp)
                put("X-Forwarded-For", options.clientIp)
            }
            putAll(options.extraHeaders)
        }

        val query = merged.entries.joinToString("&") { (k, v) ->
            "${encode(k)}=${encode(v.toString())}"
        }
        val url = options.baseUrl.trimEnd('/') + path + "?" + query

        return BuiltRequest(
            method = options.method,
            url = url,
            headers = headers,
            params = merged.mapValues { (_, v) -> v.toString() },
            body = body,
        )
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        const val USER_AGENT = "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi"
    }
}
