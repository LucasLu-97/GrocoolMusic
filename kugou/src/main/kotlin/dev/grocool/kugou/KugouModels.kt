package dev.grocool.kugou

/**
 * M2 统一领域模型。
 *
 * 目的：把酷狗 236 个接口各自不同的字段命名（FileHash / hash_128 / songname /
 * SongName / rankname / global_collection_id …）收敛成 UI 只认识的一组稳定类型。
 * UI 与 adapter 层不得直接引用酷狗原始字段名 —— 这样上游接口变动时只需改
 * [KugouRepository] 的解析，不必改任何屏幕。
 *
 * 依据 `docs/02-Implementation-Plan.md` 第 3 节的模块契约；
 * 字段形状来自 2026-11-11 真实网关实测（`docs/M2-Content.md`）。
 */

/** 歌曲。hash 是取流唯一凭据，非 VIP 曲可直接播放。 */
data class Song(
    val hash: String,
    val name: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    /** 10 = VIP 限制（需登录 + 概念版免费听 Auth）；0/其它 = 可直接播放。 */
    val privilege: Int = 0,
    val coverUrl: String = "",
    val albumAudioId: Long = 0L,
    val audioId: Long = 0L,
) {
    val isVip: Boolean get() = privilege == 10
    val isPlayable: Boolean get() = hash.length >= 16
}

/** 歌手。 */
data class Artist(
    val id: Long,
    val name: String,
    val avatarUrl: String = "",
)

/** 专辑。 */
data class Album(
    val id: String,
    val name: String,
    val artist: String = "",
    val coverUrl: String = "",
)

/** 歌单。 */
data class Playlist(
    val id: String,
    val name: String,
    val coverUrl: String = "",
    val intro: String = "",
    val songCount: Int = 0,
)

/**
 * 榜单。酷狗实测返回 56 个（TOP500 / 内地榜 / 飙升榜 / 新歌榜 …）。
 * [rankCid] 是取该榜单歌曲列表的必要参数，[rankId] 单独不够。
 */
data class Chart(
    val id: Long,
    val cid: Long,
    val name: String,
    val coverUrl: String = "",
    val intro: String = "",
    val updateFrequency: String = "",
    val preview: List<String> = emptyList(),
)

/** 热搜分组（热搜榜 / 飙升榜 / DJ榜 …）。 */
data class HotSearchGroup(
    val name: String,
    val keywords: List<String>,
)

/**
 * 首页内容 Shelf（内容货架）。
 * BitChord / Apple Music 的首页由多个横向或网格货架组成；这里用统一类型承载，
 * 便于后续把「每日推荐 / 编辑精选 / 私人 FM」等接入同一套渲染逻辑。
 */
data class Shelf(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val kind: ShelfKind,
    val songs: List<Song> = emptyList(),
    val charts: List<Chart> = emptyList(),
    val keywords: List<String> = emptyList(),
)

enum class ShelfKind { SONGS, CHARTS, KEYWORDS }

/** 评论（M3 接入登录与楼层后补全字段，此处先建立契约）。 */
data class Comment(
    val id: Long,
    val userName: String,
    val content: String,
    val likeCount: Int = 0,
)

/** 内容层统一结果：把网关失败与「确实为空」区分开。 */
data class ContentResult<T>(
    val items: List<T>,
    val state: ResponseState,
) {
    val isSuccess: Boolean get() = state.status == 1
}
