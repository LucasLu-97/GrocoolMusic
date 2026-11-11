package dev.grocool.kugou

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * M0 阶段的轻量响应提取器。
 *
 * 完整的 DTO 与领域模型收敛属于 `KugouRepository`（M2）；这里只负责从原始
 * JSON 里取出播放闭环需要的两个值：歌曲 hash 与可播放 URL。所有解析都容错，
 * 接口字段变动时返回空值而不是让 UI 崩溃。
 *
 * 响应形状来自 2026-11-11 的真实网关实测，见 `docs/M0-Smoke-Test.md`。
 */
object KugouResponse {

    /** `/v2/search/song` → `data.lists[].FileHash`（按返回顺序，小写）。 */
    fun searchHashes(rawJson: String): List<String> = runCatching {
        val root = Json.parseToJsonElement(rawJson).jsonObject
        val lists = root["data"]?.jsonObject?.get("lists") as? JsonArray ?: JsonArray(emptyList())
        lists.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            for (key in listOf("FileHash", "HQFileHash", "SQFileHash", "hash")) {
                val value = obj[key]?.jsonPrimitive?.contentOrNull
                if (!value.isNullOrEmpty() && value.length >= 16) {
                    return@mapNotNull value.lowercase()
                }
            }
            null
        }
    }.getOrDefault(emptyList())

    /**
     * `/v5/url` → 第一个可播放 URL。
     * `status != 1`（VIP 限制、未登录、需 Auth）时返回 null，而不是抛异常。
     */
    fun streamUrl(rawJson: String): String? = runCatching {
        val root = Json.parseToJsonElement(rawJson).jsonObject
        if (root["status"]?.jsonPrimitive?.content != "1") {
            null
        } else {
            when (val url = root["url"]) {
                is JsonArray -> url.firstOrNull()?.jsonPrimitive?.contentOrNull
                is JsonPrimitive -> url.contentOrNull
                else -> null
            }?.takeIf { it.startsWith("http") }
        }
    }.getOrNull()
}
