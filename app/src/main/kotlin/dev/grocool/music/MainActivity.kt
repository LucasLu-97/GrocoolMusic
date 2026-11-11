package dev.grocool.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.grocool.music.adapter.ChartItem
import dev.grocool.music.adapter.ContentAdapter
import dev.grocool.music.adapter.ShelfItem
import dev.grocool.music.adapter.SongItem
import dev.grocool.music.adapter.Tab
import dev.grocool.music.adapter.chartItem
import dev.grocool.music.adapter.shelfItems
import dev.grocool.music.adapter.songItem
import dev.grocool.music.ui.Accent
import dev.grocool.music.ui.ChartDetailScreen
import dev.grocool.music.ui.ExploreScreen
import dev.grocool.music.ui.GrocoolTheme
import dev.grocool.music.ui.HomeScreen
import dev.grocool.music.ui.IosShapes
import dev.grocool.music.ui.LibraryScreen
import dev.grocool.music.ui.MiniPlayer
import dev.grocool.music.ui.SearchScreen
import dev.grocool.music.ui.glassBorder
import dev.grocool.music.ui.glassSurface
import dev.grocool.music.ui.secondaryLabel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GrocoolTheme { GrocoolApp() }
        }
    }
}

private val TabIcon: Map<Tab, ImageVector> = mapOf(
    Tab.LISTEN to Icons.Filled.Home,
    Tab.EXPLORE to Icons.Filled.Explore,
    Tab.SEARCH to Icons.Filled.Search,
    Tab.LIBRARY to Icons.AutoMirrored.Filled.LibraryBooks,
)

/**
 * 浮动的 iOS 风格玻璃标签栏（替代 Material 3 NavigationBar）。
 * 悬浮卡片 + squircle 连续圆角 + 选中态胶囊，是 Apple Music 底栏的排版语言。
 */
@Composable
private fun GlassTabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val shape = IosShapes.LargeCard
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(14.dp, shape, clip = false)
            .clip(shape)
            .background(glassSurface())
            .border(0.5.dp, glassBorder(), shape)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { tab ->
            val active = tab == selected
            val tint = if (active) Accent else secondaryLabel()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(IosShapes.Pill)
                    .background(if (active) Accent.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(TabIcon.getValue(tab), contentDescription = tab.label, tint = tint, modifier = Modifier.size(21.dp))
                Text(tab.label, style = MaterialTheme.typography.labelSmall, color = tint)
            }
        }
    }
}

/**
 * 应用外壳：浮动玻璃标签栏 + 悬浮迷你播放器。
 * 所有业务状态在此编排，屏幕只消费 adapter 模型，不接触酷狗原始响应。
 */
@Composable
private fun GrocoolApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { KugouMusicRepository() }
    val player = remember { MusicPlayer(context) }
    DisposableEffect(Unit) { onDispose { player.release() } }

    var tab by remember { mutableStateOf(Tab.LISTEN) }
    var shelves by remember { mutableStateOf<List<ShelfItem>>(emptyList()) }
    var charts by remember { mutableStateOf<List<ChartItem>>(emptyList()) }
    var openChart by remember { mutableStateOf<ChartItem?>(null) }
    var chartSongs by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var query by remember { mutableStateOf("儿歌") }
    var results by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var current by remember { mutableStateOf<SongItem?>(null) }
    var playing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("正在加载首页内容…") }
    var busy by remember { mutableStateOf(false) }

    fun loadHome() {
        busy = true
        status = "正在加载首页内容…"
        scope.launch {
            repository.homeFeed()
                .onSuccess { feed ->
                    shelves = ContentAdapter.shelfItems(feed)
                    status = if (feed.failures.isEmpty()) {
                        "已加载 ${shelves.size} 个内容板块"
                    } else {
                        "部分板块失败：${feed.failures.first()}"
                    }
                }
                .onFailure { status = "首页加载失败：${it.message}" }
            busy = false
        }
    }

    fun loadCharts() {
        busy = true
        status = "正在获取排行榜…"
        scope.launch {
            repository.charts()
                .onSuccess { list ->
                    charts = list.map { ContentAdapter.chartItem(it) }
                    status = "共 ${charts.size} 个官方榜单"
                }
                .onFailure { status = "榜单加载失败：${it.message}" }
            busy = false
        }
    }

    fun openChartDetail(chart: ChartItem) {
        openChart = chart
        busy = true
        status = "正在获取「${chart.title}」歌曲…"
        scope.launch {
            repository.chartSongs(chart.source)
                .onSuccess { list ->
                    chartSongs = list.mapIndexed { i, song -> ContentAdapter.songItem(song, i + 1) }
                    status = "「${chart.title}」共 ${chartSongs.size} 首"
                }
                .onFailure { status = "榜单歌曲失败：${it.message}" }
            busy = false
        }
    }

    fun doSearch(keyword: String = query, switchTab: Boolean = false) {
        query = keyword
        if (switchTab) tab = Tab.SEARCH
        busy = true
        status = "搜索中…"
        scope.launch {
            repository.search(keyword)
                .onSuccess { list ->
                    results = list.map { ContentAdapter.songItem(it) }
                    status = if (results.isEmpty()) "没有结果" else "找到 ${results.size} 首，点击播放"
                }
                .onFailure { status = "搜索失败：${it.message}" }
            busy = false
        }
    }

    fun play(item: SongItem) {
        busy = true
        status = "正在获取播放地址…"
        scope.launch {
            val url = repository.streamUrl(item.source).getOrNull()
            if (url == null) {
                status = "该曲目需要 VIP / 概念版免费听 Auth（M3/M4）"
            } else {
                player.play(url)
                current = item
                playing = true
                status = "正在播放：${item.title}"
            }
            busy = false
        }
    }

    LaunchedEffect(Unit) { loadHome() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                MiniPlayer(current, playing) {
                    player.toggle()
                    playing = player.isPlaying
                }
                GlassTabBar(selected = tab) { item ->
                    tab = item
                    openChart = null
                    when (item) {
                        Tab.LISTEN -> if (shelves.isEmpty()) loadHome()
                        Tab.EXPLORE -> if (charts.isEmpty()) loadCharts()
                        else -> Unit
                    }
                }
            }
        },
    ) { padding ->
        val contentModifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())
        when (tab) {
            Tab.LISTEN -> HomeScreen(
                shelves = shelves,
                status = status,
                busy = busy,
                onKeyword = { doSearch(it, switchTab = true) },
                onChart = { chart -> tab = Tab.EXPLORE; openChartDetail(chart) },
                onSong = ::play,
                selectedHash = current?.hash,
                modifier = contentModifier,
            )

            Tab.EXPLORE -> if (openChart != null) {
                ChartDetailScreen(
                    chart = openChart!!,
                    songs = chartSongs,
                    status = status,
                    busy = busy,
                    onBack = { openChart = null },
                    onSong = ::play,
                    selectedHash = current?.hash,
                    modifier = contentModifier,
                )
            } else {
                ExploreScreen(
                    charts = charts,
                    status = status,
                    busy = busy,
                    onChart = ::openChartDetail,
                    modifier = contentModifier,
                )
            }

            Tab.SEARCH -> SearchScreen(
                query = query,
                onQueryChange = { query = it },
                onSearch = { doSearch() },
                results = results,
                status = status,
                busy = busy,
                onSong = ::play,
                selectedHash = current?.hash,
                modifier = contentModifier,
            )

            Tab.LIBRARY -> LibraryScreen(modifier = contentModifier)
        }
    }
}
