package dev.grocool.kugou

import java.net.HttpURLConnection
import java.net.URL

/** 网络出口抽象，便于契约测试注入假实现，也便于后续替换成 Ktor / OkHttp。 */
interface HttpTransport {
    fun execute(request: BuiltRequest): String
}

/**
 * 基于 HttpURLConnection 的默认实现：JVM 与 Android 运行时都支持，
 * 零第三方依赖。刻意不用 java.net.http.HttpClient —— 它在 Android 上不可用。
 */
class DefaultHttpTransport(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 20_000,
) : HttpTransport {
    override fun execute(request: BuiltRequest): String {
        val connection = URL(request.url).openConnection() as HttpURLConnection
        connection.requestMethod = request.method.uppercase()
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        request.headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
        try {
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * M0 的三个验证接口：搜索、取流、歌词。
 *
 * 均返回原始 JSON 字符串 —— 领域模型与 DTO 收敛属于 `KugouRepository`（M2），
 * 协议层只负责「把请求正确发出去」。M0 阶段可用 [KugouResponse] 从响应中
 * 提取歌曲 hash 与可播放 URL。
 *
 * 2026-11-11 已通过真实网关验证（lite / standard 双平台、签名、搜索、取流），
 * 详见 `docs/M0-Smoke-Test.md`。响应中的 `ssa-code` 二次验证头、sid/edt
 * 行为指纹与概念版免费听 Auth 链路在 M3/M4 补齐。
 */
class KugouApi(
    private val platform: Platform = Platform.LITE,
    private val credentials: KugouCredentials = KugouCredentials(),
    private val transport: HttpTransport = DefaultHttpTransport(),
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val builder = KugouRequestBuilder(platform, credentials, clock)

    /**
     * `module/search.js` — 概念版搜索会把 clientver 覆盖为 201。
     * `privilegeFilter=1` 可只返回免费曲目（`Privilege=0`），便于演示与自动化测试。
     */
    fun search(
        keyword: String,
        page: Int = 1,
        pageSize: Int = 30,
        privilegeFilter: Int = 0,
    ): String {
        val params = mutableMapOf<String, Any?>(
            "keyword" to keyword,
            "page" to page,
            "pagesize" to pageSize,
            "platform" to "AndroidFilter",
            "iscorrection" to 1,
            "privilegefilter" to privilegeFilter,
            "area_code" to 1,
            "dopicfull" to 1,
        )
        if (platform.isLite) params["clientver"] = 201
        return transport.execute(
            builder.build(
                "/v2/search/song",
                params,
                RequestOptions(extraHeaders = mapOf("x-router" to "complexsearch.kugou.com")),
            )
        )
    }

    /**
     * `module/song_url.js` — 需要 encryptKey（signKey），lite 的 page_id / ppage_id / pid 不同。
     *
     * VIP 曲目（`Privilege=10`）在未登录时返回 `status=2`；`freePart=true`
     * 可取试听片段，完整播放需 M3/M4 的登录态与免费听 Auth 链路。
     * 上游的 `notSign: true` 不可照抄：实测不签名会被网关以 20006 拒绝。
     */
    fun songUrl(
        hash: String,
        quality: String = "128",
        freePart: Boolean = false,
    ): String {
        val params = mutableMapOf<String, Any?>(
            "album_id" to 0,
            "area_code" to 1,
            "hash" to hash.lowercase(),
            "ssa_flag" to "is_fromtrack",
            "version" to 11430,
            "page_id" to if (platform.isLite) 967177915 else 151369488,
            "quality" to quality,
            "album_audio_id" to 0,
            "behavior" to "play",
            "pid" to if (platform.isLite) 411 else 2,
            "cmd" to 26,
            "pidversion" to 3001,
            "IsFreePart" to if (freePart) 1 else 0,
            "ppage_id" to if (platform.isLite) {
                "356753938,823673182,967485191"
            } else {
                "463467626,350369493,788954147"
            },
            "cdnBackup" to 1,
            "module" to "",
            "clientver" to 11430,
        )
        return transport.execute(
            builder.build(
                "/v5/url",
                params,
                RequestOptions(
                    extraHeaders = mapOf("x-router" to "trackercdn.kugou.com"),
                    encryptKey = true,
                ),
            )
        )
    }

    /** `module/lyric.js` — 独立域名 lyrics.kugou.com。 */
    fun lyric(id: String, accessKey: String, fmt: String = "krc"): String {
        val params = mapOf<String, Any?>(
            "ver" to 1,
            "client" to "android",
            "id" to id,
            "accesskey" to accessKey,
            "fmt" to fmt,
            "charset" to "utf8",
        )
        return transport.execute(
            builder.build(
                "/download",
                params,
                RequestOptions(baseUrl = "https://lyrics.kugou.com"),
            )
        )
    }
}
