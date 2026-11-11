package dev.grocool.kugou

/**
 * M2 内容层接口：榜单、热搜、榜单歌曲、歌单。
 *
 * 全部写成 [KugouApi] 的扩展函数，复用同一个 [KugouRequestBuilder] —— 签名、
 * 设备标识、默认参数只在一处实现。每个接口只描述「路径 + 参数表 + 请求头」，
 * 与 KuGouMusicApi 的 module 目录下各 JS 接口实现一一对应。
 *
 * 参数与响应形状均于 2026-11-11 经真实网关实测，见 `docs/M2-Content.md`。
 * 依据上游 MIT 实现：`module/rank_list.js` / `rank_info.js` / `rank_audio.js` /
 * `rank_vol.js` / `rank_top.js` / `search_hot.js` / `playlist_track_all.js`。
 */
object KugouContentApi {
    /**
     * 紧凑 JSON 序列化，对齐 JS 的 `JSON.stringify(dataMap)`。
     * 必须保持插入顺序且不插入空格：body 字符串会逐字节参与签名。
     */
    internal fun jsonBody(vararg pairs: Pair<String, Any?>): String = buildString {
        append('{')
        pairs.forEachIndexed { index, (key, value) ->
            if (index > 0) append(',')
            append('"').append(key).append("\":")
            append(
                when (value) {
                    null -> "null"
                    is Number, is Boolean -> value.toString()
                    is String -> "\"" + value
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r") + "\""
                    is Map<*, *> -> jsonBody(*value.entries.map {
                        it.key.toString() to it.value
                    }.toTypedArray())
                    is Iterable<*> -> value.joinToString(",", "[", "]") { item ->
                        when (item) {
                            is Number, is Boolean -> item.toString()
                            null -> "null"
                            else -> "\"" + item.toString()
                                .replace("\\", "\\\\")
                                .replace("\"", "\\\"") + "\""
                        }
                    }
                    else -> "\"" + value.toString()
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"") + "\""
                }
            )
        }
        append('}')
    }
}

/** `module/rank_list.js` — 排行榜分类列表，实测返回 56 个榜单。 */
fun KugouApi.rankList(withSong: Int = 1): String = transport.execute(
    builder.build(
        "/ocean/v6/rank/list",
        mapOf<String, Any?>("plat" to 2, "withsong" to withSong, "parentid" to 0),
    )
)

/** `module/rank_info.js` — 单个榜单的元信息与 3 首预览曲。 */
fun KugouApi.rankInfo(rankId: Long, rankCid: Long = 0): String = transport.execute(
    builder.build(
        "/ocean/v6/rank/info",
        mapOf<String, Any?>(
            "rank_cid" to rankCid,
            "rankid" to rankId,
            "with_album_img" to 1,
            "zone" to "",
        ),
    )
)

/**
 * `module/rank_audio.js` — 榜单内歌曲列表（分页）。
 *
 * **POST + JSON body 参与签名**：不带 body 参与签名实测返回
 * `status=0 / error_code=20006`。这是内容层与 M0 三接口最大的协议差异。
 */
fun KugouApi.rankAudio(
    rankId: Long,
    rankCid: Long = 0,
    page: Int = 1,
    pageSize: Int = 30,
): String {
    val body = KugouContentApi.jsonBody(
        "show_portrait_mv" to 1,
        "show_type_total" to 1,
        "filter_original_remarks" to 1,
        "area_code" to 1,
        "pagesize" to pageSize,
        "rank_cid" to rankCid,
        "type" to 1,
        "page" to page,
        "rank_id" to rankId,
    )
    return transport.execute(
        builder.build(
            "/openapi/kmr/v2/rank/audio",
            emptyMap(),
            RequestOptions(
                method = "POST",
                body = body,
                extraHeaders = mapOf("kg-tid" to "369"),
            ),
        )
    )
}

/** `module/rank_vol.js` — 榜单往期列表。 */
fun KugouApi.rankVolumes(rankId: Long, rankCid: Long = 0): String = transport.execute(
    builder.build(
        "/ocean/v6/rank/vol",
        mapOf<String, Any?>(
            "rank_cid" to rankCid,
            "rankid" to rankId,
            "ranktype" to 1,
            "type" to 0,
            "plat" to 2,
        ),
    )
)

/** `module/rank_top.js` — 编辑推荐的精选榜单。 */
fun KugouApi.recommendedCharts(): String = transport.execute(
    builder.build("/mobileservice/api/v5/rank/rec_rank_list", emptyMap())
)

/** `module/search_hot.js` — 热搜榜分组关键词。 */
fun KugouApi.hotSearch(): String = transport.execute(
    builder.build(
        "/api/v3/search/hot_tab",
        mapOf<String, Any?>("navid" to 1, "plat" to 2),
        RequestOptions(extraHeaders = mapOf("x-router" to "msearch.kugou.com")),
    )
)

/**
 * `module/playlist_track_all.js` — 歌单内全部歌曲。
 * `globalCollectionId` 必须是真实歌单 id，空值会返回 `error_code=20010 参数错误`。
 */
fun KugouApi.playlistTracks(globalCollectionId: String, page: Int = 1, pageSize: Int = 30): String =
    transport.execute(
        builder.build(
            "/pubsongs/v2/get_other_list_file_nofilt",
            mapOf<String, Any?>(
                "area_code" to 1,
                "begin_idx" to (page - 1) * pageSize,
                "plat" to 1,
                "type" to 1,
                "mode" to 1,
                "personal_switch" to 1,
                "extend_fields" to "abtags,hot_cmt,popularization",
                "pagesize" to pageSize,
                "global_collection_id" to globalCollectionId,
            ),
        )
    )
