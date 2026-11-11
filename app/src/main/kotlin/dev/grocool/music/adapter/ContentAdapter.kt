package dev.grocool.music.adapter

import dev.grocool.kugou.Chart
import dev.grocool.kugou.HomeFeed
import dev.grocool.kugou.Shelf
import dev.grocool.kugou.ShelfKind
import dev.grocool.kugou.Song

/**
 * **adapter/ 隔离带** —— 本项目所有自有业务逻辑收拢于此。
 *
 * 按 `docs/02-Implementation-Plan.md` 的架构契约，UI 屏幕只消费这里的
 * [SongItem] / [ChartItem] / [ShelfItem]，不直接接触酷狗领域模型，更不解析
 * 网关 JSON。这样：
 * - 上游 UI（BitChord）按 tag 同步时，冲突只可能发生在这一层；
 * - 酷狗接口字段改名时，只改 `kugou/` 的解析器，UI 零改动；
 * - 后续接入登录 / 免费听 Auth 时，状态扩展也只在这里。
 *
 * 界面外观刻意只参考 Apple Music 的排版语言（大标题、圆角卡片、强调色、
 * 横向货架），不复用其图标 / 字体 / 商标资源。
 */
object ContentAdapter

/** 底部导航的四个标签。 */
enum class Tab(val label: String) {
    LISTEN("收听"),
    EXPLORE("浏览"),
    SEARCH("搜索"),
    LIBRARY("资料库"),
}

/** 列表中的一行歌曲。 */
data class SongItem(
    val hash: String,
    val title: String,
    val subtitle: String,
    val durationLabel: String,
    val badge: String,
    val coverUrl: String,
    val rank: Int = 0,
    /** 原始领域模型，点击时用于取流；UI 不读取它的字段。 */
    val source: Song,
)

/** 榜单卡片。 */
data class ChartItem(
    val id: Long,
    val cid: Long,
    val title: String,
    val subtitle: String,
    val coverUrl: String,
    val source: Chart,
)

/** 首页货架。 */
data class ShelfItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val kind: ShelfKind,
    val songs: List<SongItem> = emptyList(),
    val charts: List<ChartItem> = emptyList(),
    val keywords: List<String> = emptyList(),
)

/** 把 [Song] 映射成 UI 行模型。 */
fun ContentAdapter.songItem(song: Song, rank: Int = 0): SongItem = SongItem(
    hash = song.hash,
    title = song.name,
    subtitle = listOf(song.artist, song.album)
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(" · ")
        .ifEmpty { "未知歌手" },
    durationLabel = formatDuration(song.durationMs),
    badge = if (song.isVip) "VIP" else "免费",
    coverUrl = song.coverUrl,
    rank = rank,
    source = song,
)

/** 把 [Chart] 映射成 UI 卡片模型。 */
fun ContentAdapter.chartItem(chart: Chart): ChartItem = ChartItem(
    id = chart.id,
    cid = chart.cid,
    title = chart.name,
    subtitle = chart.updateFrequency.ifBlank { chart.intro.lineSequence().firstOrNull().orEmpty() },
    coverUrl = chart.coverUrl,
    source = chart,
)

/** 把 [HomeFeed] 装配成首页货架列表。 */
fun ContentAdapter.shelfItems(feed: HomeFeed): List<ShelfItem> = feed.shelves.map { shelf ->
    shelfItem(shelf)
}

fun ContentAdapter.shelfItem(shelf: Shelf): ShelfItem = ShelfItem(
    id = shelf.id,
    title = shelf.title,
    subtitle = shelf.subtitle,
    kind = shelf.kind,
    songs = shelf.songs.mapIndexed { index, song -> songItem(song, rank = index + 1) },
    charts = shelf.charts.map(::chartItem),
    keywords = shelf.keywords,
)

/** 毫秒 → `m:ss`。酷狗的时长字段是毫秒字符串，展示层统一在这里转换。 */
fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "--:--"
    val seconds = durationMs / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

/** 从 hash 生成稳定的封面占位色，避免为每首歌引入图片依赖与额外内存开销。 */
fun coverTint(hash: String): androidx.compose.ui.graphics.Color {
    val palette = listOf(
        androidx.compose.ui.graphics.Color(0xFFFA233B),
        androidx.compose.ui.graphics.Color(0xFFFF8A3D),
        androidx.compose.ui.graphics.Color(0xFFFFC542),
        androidx.compose.ui.graphics.Color(0xFF4CC38A),
        androidx.compose.ui.graphics.Color(0xFF3D8BFF),
        androidx.compose.ui.graphics.Color(0xFF9B5DE5),
        androidx.compose.ui.graphics.Color(0xFFEF5DA8),
    )
    val index = (hash.hashCode().toLong() and Long.MAX_VALUE) % palette.size
    return palette[index.toInt()]
}
