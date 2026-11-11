package dev.grocool.kugou

import java.security.MessageDigest
import java.security.SecureRandom

/** MD5，UTF-8 输入，32 位小写 hex。对齐 crypto-js 的 `MD5(...).toString(enc.Hex)`。 */
internal fun md5(input: String): String {
    val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}

/**
 * 酷狗请求签名算法。
 *
 * 移植自 KuGouMusicApi `util/helper.js` 与 `util/crypto.js`（MIT）。
 * 所有算法都是「盐值 + 排序后的参数串 + 请求体 + 盐值」取 MD5，
 * 差别只在盐值、排序方式与参数拼接格式 —— 这三处错一处就会整体验签失败。
 */
object KugouCrypto {
    const val SALT_ANDROID_LITE = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA"
    const val SALT_ANDROID_NORMAL = "OIlwieks28dk2k092lksi2UIkp"
    const val SALT_WEB = "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt"
    const val SALT_SIGN = "R6snCXJgbCaj9WFRJKefTMIFp0ey6Gza"
    const val SALT_KEY_LITE = "185672dd44712f60bb1736df5a377e82"
    const val SALT_KEY_NORMAL = "57ae12eb6890223e355ccfcb74edf70d"
    const val SALT_CLOUD_KEY = "ebd1ac3134c880bda6a2194537843caa0162e2e7"

    /** 设备注册签名盐值（helper.js 中硬编码的 "1014"）。 */
    const val REGISTER_SALT = "1014"

    /** 标准版 RSA 公钥，登录 / 云歌单等接口使用；M0 仅登记，不实现。 */
    const val PUBLIC_KEY_STANDARD =
        "-----BEGIN PUBLIC KEY-----\n" +
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDIAG7QOELSYoIJvTFJhMpe1s/gbjDJX51HBNnEl5HXqTW6lQ7LC8jr9fWZTwusknp+sVGzwd40MwP6U5yDE27M/X1+UR4tvOGOqp94TJtQ1EPnWGWXngpeIW5GxoQGao1rmYWAu6oi1z9XkChrsUdC6DJE5E221wf/4WLFxwAtRQIDAQAB\n" +
            "-----END PUBLIC KEY-----"

    /** 概念版 RSA 公钥，与标准版不同。 */
    const val PUBLIC_KEY_LITE =
        "-----BEGIN PUBLIC KEY-----\n" +
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDECi0Np2UR87scwrvTr72L6oO01rBbbBPriSDFPxr3Z5syug0O24QyQO8bg27+0+4kBzTBTBOZ/WWU0WryL1JSXRTXLgFVxtzIY41Pe7lPOgsfTCn5kZcvKhYKJesKnnJDNr5/abvTGf+rHG3YRwsCHcQ08/q6ifSioBszvb3QiwIDAQAB\n" +
            "-----END PUBLIC KEY-----"

    private val androidSalt: (Platform) -> String = { p ->
        if (p.isLite) SALT_ANDROID_LITE else SALT_ANDROID_NORMAL
    }

    /**
     * Android 版 signature。
     * 规则：params 按 **key** 排序 → 拼 `key=value`（对象值 JSON.stringify）→ MD5(salt + 串 + body + salt)。
     */
    fun signatureAndroid(params: Map<String, Any?>, platform: Platform, body: String = ""): String {
        val salt = androidSalt(platform)
        val joined = params.toSortedMap().entries.joinToString("") { (k, v) -> "$k=${render(v)}" }
        return md5(salt + joined + body + salt)
    }

    /**
     * Web 版 signature。
     * 注意与 Android 版的差别：先拼成 `key=value` 数组，再对**整个字符串数组**排序。
     */
    fun signatureWeb(params: Map<String, Any?>, body: String = ""): String {
        val joined = params.entries.map { (k, v) -> "$k=${render(v)}" }.sorted().joinToString("")
        return md5(SALT_WEB + joined + body + SALT_WEB)
    }

    /** 设备注册签名：只取参数**值**，排序后 MD5("1014" + 值串 + "1014")。 */
    fun signatureRegister(params: Map<String, Any?>): String {
        val values = params.values.map { render(it) }.sorted().joinToString("")
        return md5(REGISTER_SALT + values + REGISTER_SALT)
    }

    /** 通用 sign：排序后拼 `key+value`（无等号），末尾加盐。 */
    fun signParams(params: Map<String, Any?>, body: String = ""): String {
        val joined = params.toSortedMap().entries.joinToString("") { (k, v) -> "$k${render(v)}" }
        return md5(joined + body + SALT_SIGN)
    }

    /** `encryptKey` 接口使用的 key 参数：MD5(hash + 盐 + appid + mid + userid)。 */
    fun signKey(
        hash: String,
        mid: String,
        userId: Long,
        appId: Int,
        platform: Platform,
    ): String {
        val salt = if (platform.isLite) SALT_KEY_LITE else SALT_KEY_NORMAL
        return md5(hash + salt + appId + mid + userId)
    }

    /** 参数密钥签名：MD5(appid + 盐 + clientver + data)。 */
    fun signParamsKey(data: String, platform: Platform): String =
        md5("${platform.appId}${androidSalt(platform)}${platform.clientVer}$data")

    /** 云盘 / 云歌单密钥签名（M3 使用）。 */
    fun signCloudKey(hash: String, pid: String): String =
        md5("musicclound$hash$pid$SALT_CLOUD_KEY")

    /**
     * 随机设备标识字符串，对齐 `util/util.js` 的 randomString。
     * 上游字符池为 36 个字符；Kotlin 实现用 SecureRandom 全池均匀取样。
     */
    fun randomString(length: Int = 16): String {
        val alphabet = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val random = SecureRandom()
        return buildString(length) {
            repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) }
        }
    }

    /** JS 的 `String(value)`；对象与数组按紧凑 JSON 渲染（对齐 JSON.stringify）。 */
    private fun render(value: Any?): String = when (value) {
        null -> "null"
        is Map<*, *>, is Iterable<*> -> compactJson(value)
        else -> value.toString()
    }

    private fun compactJson(value: Any?): String = when (value) {
        null -> "null"
        is String -> "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        is Number, is Boolean -> value.toString()
        is Map<*, *> -> value.entries.joinToString(",", "{", "}") { (k, v) ->
            "\"$k\":${compactJson(v)}"
        }
        is Iterable<*> -> value.joinToString(",", "[", "]") { compactJson(it) }
        else -> "\"$value\""
    }
}
