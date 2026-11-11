package dev.grocool.music.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.grocool.kugou.ShelfKind
import dev.grocool.music.adapter.ChartItem
import dev.grocool.music.adapter.ShelfItem
import dev.grocool.music.adapter.SongItem

/**
 * 首页「收听」：把 [ShelfItem] 列表渲染成 Apple Music 风格的内容流。
 *
 * 内容全部来自 `KugouRepository.homeFeed()` 的多接口装配 —— 这正是替换
 * BitChord `Innertube.browse("FEmusic_home")` 的地方。单个货架失败不会让整页空白。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    shelves: List<ShelfItem>,
    status: String,
    busy: Boolean,
    onKeyword: (String) -> Unit,
    onChart: (ChartItem) -> Unit,
    onSong: (SongItem) -> Unit,
    selectedHash: String?,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    "Grocool Music",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "收听",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Accent,
                )
                StatusLine(status, busy)
            }
        }

        items(shelves, key = { it.id }) { shelf ->
            SectionHeader(shelf.title, shelf.subtitle)
            when (shelf.kind) {
                ShelfKind.KEYWORDS -> FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    shelf.keywords.forEach { keyword -> KeywordPill(keyword) { onKeyword(keyword) } }
                }

                ShelfKind.CHARTS -> ChartShelf(shelf.charts, onChart)

                ShelfKind.SONGS -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    shelf.songs.forEach { song ->
                        SongRow(
                            item = song,
                            selected = song.hash == selectedHash,
                            onClick = { onSong(song) },
                        )
                    }
                }
            }
        }
    }
}
