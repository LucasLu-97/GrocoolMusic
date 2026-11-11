package dev.grocool.kugou

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * 内容层原始 JSON → 领域模型解析器。
 *
 * 全部解析都容错：酷狗字段是「能缺就缺」的，任何字段变更都必须退化为
 * 空值 / 空列表，绝不能让 UI 崩溃。解析规则来自 2026-11-11 真实响应，
 * 见 `docs/M2-Content.md`。
 */
object KugouContentParser {

    private fun root(raw: String): JsonObject? = runCatching {
        Json.parseToJsonElement(raw).jsonObject
    }.getOrNull()

    private fun JsonObject.obj(key: String): JsonObject? = runCatching {
        get(key)?.jsonObject
    }.getOrNull()

    private fun JsonObject.array(key: String): JsonArray? = runCatching {
        get(key)?.jsonArray
    }.getOrNull()

    private fun JsonElement?.text(vararg keys: String): String {
        val obj = this as? JsonObject ?: return ""
        for (key in keys) {
            val value = runCatching { obj[key]?.jsonPrimitive?.contentOrNull }.getOrNull()
            if (!value.isNullOrBlank()) return cleanText(value)
        }
        return ""
    }

    private fun JsonElement?.number(vararg keys: String): Long {
        val obj = this as? JsonObject ?: return 0L
        for (key in keys) {
            val primitive = runCatching { obj[key]?.jsonPrimitive }.getOrNull()
            val value = runCatching { primitive?.longOrNull }.getOrNull()
                ?: runCatching { primitive?.contentOrNull?.toLongOrNull() }.getOrNull()
            if (value != null) return value
        }
        return 0L
    }

    private fun JsonObject.intOrZero(key: String): Int = runCatching {
        this[key]?.jsonPrimitive?.intOrNull ?: 0
    }.getOrDefault(0)

    private fun cleanText(value: String): String =
        value.replace(Regex("<[^>]*>"), "").trim()

    /**
     * 封面 URL 里的 `{size}` 是占位符，必须替换成真实尺寸，否则图片加载失败。
     * 同时把上游偶尔返回的 `http://` 升级为 `https://`（Android 明文限制）。
     */
    fun normalizeCover(url: String, size: Int = 480): String = url
        .replace("{size}", size.toString())
        .replace("http://", "https://")
        .trim()

    /** `rank_list` → 榜单列表。实测 56 个。 */
    fun charts(raw: String): List<Chart> = runCatching {
        val info = root(raw)?.obj("data")?.array("info") ?: return emptyList()
        info.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj["rankid"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            val cover = obj.text("img_9", "banner_9", "imgurl", "img_cover")
            Chart(
                id = id,
                cid = obj["rank_cid"]?.jsonPrimitive?.longOrNull ?: 0L,
                name = obj.text("rankname").ifEmpty { "榜单 $id" },
                coverUrl = normalizeCover(cover),
                intro = obj.text("intro"),
                updateFrequency = obj.text("update_frequency"),
                preview = (obj.array("songinfo") ?: JsonArray(emptyList())).mapNotNull {
                    (it as? JsonObject)?.text("songname").takeUnless { s -> s.isNullOrBlank() }
                },
            )
        }
    }.getOrDefault(emptyList())

    /**
     * `rank_audio` → 榜单歌曲列表。
     *
     * 关键字段：`audio_info.hash_128` 是取流 hash，`audio_info.duration_128` 是毫秒，
     * `album_info.sizable_cover` 是封面。榜单响应**没有**搜索结果里的 `Privilege`
     * 字段，因此这里默认可播放；真正是否 VIP 由 `/v5/url` 的 `status=2` 判定。
     */
    fun chartSongs(raw: String): List<Song> = runCatching {
        val list = root(raw)?.obj("data")?.array("songlist") ?: return emptyList()
        list.mapNotNull { element -> songFromChart(element) }
    }.getOrDefault(emptyList())

    private fun songFromChart(element: JsonElement): Song? {
        val obj = element as? JsonObject ?: return null
        val audio = obj.obj("audio_info")
        val hash = audio.text("hash_128", "hash_320", "hash_flac").lowercase()
        if (hash.length < 16) return null
        val cover = obj.obj("album_info").text("sizable_cover", "cover")
            .ifEmpty { obj.obj("trans_param").text("union_cover") }
        return Song(
            hash = hash,
            name = obj.text("songname", "name").ifEmpty { "未知歌曲" },
            artist = obj.text("author_name", "author"),
            album = obj.obj("album_info").text("album_name"),
            durationMs = audio.number("duration_128", "duration_320"),
            privilege = obj.intOrZero("Privilege"),
            coverUrl = normalizeCover(cover),
            albumAudioId = obj.number("album_audio_id"),
            audioId = obj.number("audio_id"),
        )
    }

    /** `search_hot` → 热搜分组。 */
    fun hotSearchGroups(raw: String): List<HotSearchGroup> = runCatching {
        val list = root(raw)?.obj("data")?.array("list") ?: return emptyList()
        list.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val keywords = (obj.array("keywords") ?: JsonArray(emptyList())).mapNotNull {
                (it as? JsonObject)?.text("keyword").takeUnless { s -> s.isNullOrBlank() }
            }
            if (keywords.isEmpty()) return@mapNotNull null
            HotSearchGroup(name = obj.text("name"), keywords = keywords)
        }
    }.getOrDefault(emptyList())

    /** `rank_vol` → 往期榜单期号（字段名在不同榜单间有差异，做宽松提取）。 */
    fun chartVolumes(raw: String): List<String> = runCatching {
        val data = root(raw)?.obj("data") ?: return emptyList()
        val list = data.array("vol_list") ?: data.array("list") ?: return emptyList()
        list.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            obj.text("vol_name", "name", "issue").takeUnless { it.isBlank() }
        }
    }.getOrDefault(emptyList())
}

/** 把 M0 的轻量 [KugouTrack] 升级为领域模型 [Song]。 */
fun KugouTrack.toSong(): Song = Song(
    hash = hash,
    name = name,
    artist = artist,
    album = album,
    durationMs = durationMs,
    privilege = privilege,
)
