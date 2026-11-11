package dev.grocool.kugou

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 跨平台 golden 测试：期望值由 Node.js（node:crypto，MD5/UTF-8）按
 * KuGouMusicApi `util/helper.js` 的原始算法独立算出，用于保证 Kotlin 移植
 * 与 JavaScript 参考实现逐字节一致。任何盐值或排序规则被误改都会在此失败。
 *
 * 参考输入：
 *   params = {appid:3116, clientver:11440, clienttime:1700000000,
 *             dfid:'-', mid:'goldenmid123', uuid:'-'}
 */
class SignatureGoldenTest {

    private val params = mapOf<String, Any?>(
        "appid" to 3116,
        "clientver" to 11440,
        "clienttime" to 1700000000L,
        "dfid" to "-",
        "mid" to "goldenmid123",
        "uuid" to "-",
    )

    @Test
    fun `android signature matches node reference for lite`() {
        assertEquals(
            "c8c82518cd5aaac024073dc50b2566be",
            KugouCrypto.signatureAndroid(params, Platform.LITE),
        )
    }

    @Test
    fun `android signature matches node reference for standard`() {
        assertEquals(
            "928c54da0d758762222a887f833cd801",
            KugouCrypto.signatureAndroid(params, Platform.STANDARD),
        )
    }

    @Test
    fun `register signature sorts values only`() {
        val values = mapOf<String, Any?>(
            "a" to "1005", "b" to "20489", "c" to "-", "d" to "mid",
        )
        assertEquals(
            "d9fa304c36599199f553598356ac945d",
            KugouCrypto.signatureRegister(values),
        )
    }

    @Test
    fun `signKey uses lite salt`() {
        assertEquals(
            "bab8e4795d3aab0861793cd1a73e79c5",
            KugouCrypto.signKey(
                hash = "ABCDEF123456",
                mid = "goldenmid123",
                userId = 0,
                appId = 3116,
                platform = Platform.LITE,
            ),
        )
    }

    @Test
    fun `signParamsKey uses lite appid and clientver`() {
        assertEquals(
            "bad0ee207bf429bd91402b77fc8f7a5b",
            KugouCrypto.signParamsKey("1700000000", Platform.LITE),
        )
    }

    @Test
    fun `lite and standard salts produce different signatures`() {
        val lite = KugouCrypto.signatureAndroid(params, Platform.LITE)
        val standard = KugouCrypto.signatureAndroid(params, Platform.STANDARD)
        assertTrue(lite != standard, "concept edition and standard edition must not share a signature")
    }
}

class KugouRequestBuilderTest {

    private val credentials = KugouCredentials(dfid = "-", mid = "goldenmid123")
    private val fixedClock: () -> Long = { 1700000000L }

    @Test
    fun `injects lite default params and device headers`() {
        val builder = KugouRequestBuilder(Platform.LITE, credentials, fixedClock)
        val request = builder.build(
            "/v2/search/song",
            mapOf("keyword" to "test"),
            RequestOptions(extraHeaders = mapOf("x-router" to "complexsearch.kugou.com")),
        )

        assertEquals("3116", request.params["appid"])
        assertEquals("11440", request.params["clientver"])
        assertEquals("1700000000", request.params["clienttime"])
        assertEquals("-", request.params["uuid"])
        assertEquals("complexsearch.kugou.com", request.headers["x-router"])
        assertEquals("1", request.headers["kg-rc"])
        assertEquals("5d816a0", request.headers["kg-thash"])
        assertEquals(32, request.params["signature"]?.length)
        assertTrue(request.url.startsWith("https://gateway.kugou.com/v2/search/song?"))
        assertTrue(request.url.contains("signature="))
    }

    @Test
    fun `signature is deterministic for a fixed clock`() {
        val builder = KugouRequestBuilder(Platform.LITE, credentials, fixedClock)
        val first = builder.build("/v2/search/song", mapOf("keyword" to "test"))
        val second = builder.build("/v2/search/song", mapOf("keyword" to "test"))
        assertEquals(first.params["signature"], second.params["signature"])
    }

    @Test
    fun `encryptKey adds key parameter`() {
        val builder = KugouRequestBuilder(Platform.LITE, credentials, fixedClock)
        val request = builder.build(
            "/v5/url",
            mapOf("hash" to "ABCDEF123456"),
            RequestOptions(encryptKey = true),
        )
        assertEquals(32, request.params["key"]?.length)
        assertEquals("ABCDEF123456", request.params["hash"])
    }

    @Test
    fun `login token is isolated per platform`() {
        val withToken = KugouCredentials(dfid = "-", mid = "m", token = "lite-token", userId = 42)
        val request = KugouRequestBuilder(Platform.LITE, withToken, fixedClock)
            .build("/v2/search/song", emptyMap())
        assertEquals("lite-token", request.params["token"])
        assertEquals("42", request.params["userid"])
    }
}
