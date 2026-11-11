package dev.grocool.kugou

import java.net.URLEncoder

/** 登录态与设备标识。lite 与标准版的 token 必须分开存储，不可复用。 */
data class KugouCredentials(
    val dfid: String = "-",
    val mid: String = "",
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
)

/** 构造完成的请求，交给 [HttpTransport] 发送。 */
data class BuiltRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val params: Map<String, String>,
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

        // M0 的请求均为 GET，body 为空串；POST 接口在 M2 之后接入时需把序列化后的 body 传入。
        val body = ""
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
        )
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        const val USER_AGENT = "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi"
    }
}
