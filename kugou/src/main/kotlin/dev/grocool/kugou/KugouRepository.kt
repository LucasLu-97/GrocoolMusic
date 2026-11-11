package dev.grocool.kugou

/**
 * M2 领域仓库：UI 唯一的数据入口。
 *
 * 架构约束（`docs/02-Implementation-Plan.md` 第 3 节）：
 * - 单向依赖：UI → adapter → [KugouRepository] → [KugouApi] → 网关。
 * - 这里把酷狗 236 个接口的原始 JSON 收敛成 [Song] / [Chart] / [Shelf] 等
 *   稳定领域模型，屏蔽字段命名差异；UI 层禁止直接解析网关响应。
 * - 纯 JVM，无 Android 依赖，可在本地用 JUnit 快速迭代（无需真机 / CI）。
 *
 * 所有方法都返回 [ContentResult]，把「验签 / 风控失败」与「确实没有内容」
 * 区分开 —— 这是 M1 真机 `mid` 为空导致「没有结果」误判后定下的规则。
 */
class KugouRepository(
    private val api: KugouApi = KugouApi(platform = Platform.LITE),
) {

    /** 搜索 → 领域模型曲目。网关失败时抛带错误码的异常，由 UI 显示。 */
    fun search(keyword: String, page: Int = 1): ContentResult<Song> {
        val raw = api.search(keyword, page = page)
        return parse(raw, "搜索") { KugouResponse.searchTracks(it).map { t -> t.toSong() } }
    }

    /** 全部榜单（实测 56 个）。 */
    fun charts(): ContentResult<Chart> = parse(api.rankList(), "榜单列表") {
        KugouContentParser.charts(it)
    }

    /** 单个榜单的歌曲列表（分页）。POST body 参与签名。 */
    fun chartSongs(chart: Chart, page: Int = 1, pageSize: Int = 30): ContentResult<Song> =
        parse(api.rankAudio(chart.id, chart.cid, page, pageSize), "榜单歌曲") {
            KugouContentParser.chartSongs(it)
        }

    /** 热搜分组。 */
    fun hotSearchGroups(): ContentResult<HotSearchGroup> =
        parse(api.hotSearch(), "热搜") { KugouContentParser.hotSearchGroups(it) }

    /**
     * 统一的「读状态 → 校验 → 解析」流程。
     * 状态非 1 时抛出带错误码的异常，避免把验签/风控失败误显示成「没有结果」。
     */
    private inline fun <T> parse(
        raw: String,
        label: String,
        parser: (String) -> List<T>,
    ): ContentResult<T> {
        val state = KugouResponse.state(raw)
        if (state.status != 1) {
            throw IllegalStateException(
                "$label 失败：酷狗返回 status=${state.status}, " +
                    "error_code=${state.errorCode}, msg=${state.message ?: ""}"
            )
        }
        return ContentResult(items = parser(raw), state = state)
    }

    /**
     * 首页内容流：把多个内容接口装配成 [Shelf] 列表。
     *
     * 这是 M2 内容层的核心 —— BitChord 的 `home()` 写死
     * `Innertube.browse("FEmusic_home")`，酷狗没有等价物，必须由多个真实接口
     * 拼装。当前第一批接入「热搜关键词」「精选榜单」「榜单新歌」三种货架；
     * 后续按里程碑加入每日推荐、编辑精选、私人 FM、歌单广场等。
     *
     * 单个接口失败不应让整页空掉：失败的货架直接跳过并在 [HomeFeed] 里回报。
     */
    fun homeFeed(): HomeFeed {
        val shelves = mutableListOf<Shelf>()
        val failures = mutableListOf<String>()

        fun <T> add(label: String, block: () -> ContentResult<T>, build: (List<T>) -> Shelf) {
            val result = runCatching { block() }.getOrElse {
                failures += "$label: ${it.message}"
                return@add
            }
            if (!result.isSuccess) {
                failures += "$label: status=${result.state.status} error=${result.state.errorCode}"
                return@add
            }
            if (result.items.isNotEmpty()) shelves += build(result.items)
        }

        add("热搜", ::hotSearchGroups) { groups ->
            Shelf(
                id = "hot_search",
                title = "大家都在搜",
                subtitle = "实时热搜榜",
                kind = ShelfKind.KEYWORDS,
                keywords = groups.first().keywords.take(12),
            )
        }

        add("榜单", ::charts) { charts ->
            Shelf(
                id = "charts",
                title = "排行榜",
                subtitle = "酷狗官方榜单",
                kind = ShelfKind.CHARTS,
                charts = charts.take(12),
            )
        }

        // 用第一个榜单的歌曲作为「新歌速递」货架。
        shelves.firstOrNull { it.kind == ShelfKind.CHARTS }?.charts?.firstOrNull()?.let { first ->
            add("榜单新歌", { chartSongs(first, pageSize = 10) }) { songs ->
                Shelf(
                    id = "chart_songs",
                    title = "${first.name} · 新歌速递",
                    subtitle = "点击即可播放",
                    kind = ShelfKind.SONGS,
                    songs = songs.take(10),
                )
            }
        }

        return HomeFeed(shelves = shelves, failures = failures)
    }

    /** 取可播放 URL。VIP 曲用 freePart 取试听；完整播放待 M3/M4 Auth。 */
    fun streamUrl(song: Song): String? {
        val raw = api.songUrl(song.hash, freePart = song.isVip)
        return KugouResponse.streamUrl(raw)
    }
}

/** 首页装配结果；[failures] 用于 UI 提示某一货架加载失败但保留其它内容。 */
data class HomeFeed(
    val shelves: List<Shelf>,
    val failures: List<String> = emptyList(),
)
