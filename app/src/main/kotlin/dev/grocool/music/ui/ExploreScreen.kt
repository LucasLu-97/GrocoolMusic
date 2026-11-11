package dev.grocool.music.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.grocool.music.adapter.ChartItem
import dev.grocool.music.adapter.SongItem

/** 「浏览」页：全部官方榜单（实测 56 个）。 */
@Composable
fun ExploreScreen(
    charts: List<ChartItem>,
    status: String,
    busy: Boolean,
    onChart: (ChartItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(top = 8.dp, bottom = 10.dp)) {
                Text(
                    "浏览",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text("酷狗官方排行榜", fontSize = 14.sp, color = Accent)
                StatusLine(status, busy)
            }
        }
        items(charts, key = { it.id }) { chart ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onChart(chart) },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoverBox(chart.title, chart.id.toString(), size = 52)
                    Spacer(Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(chart.title, fontWeight = FontWeight.Medium)
                        Text(
                            chart.subtitle.take(40),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }
}

/** 榜单详情：POST 接口返回的真实歌曲列表，带排名，可直接点击播放。 */
@Composable
fun ChartDetailScreen(
    chart: ChartItem,
    songs: List<SongItem>,
    status: String,
    busy: Boolean,
    onBack: () -> Unit,
    onSong: (SongItem) -> Unit,
    selectedHash: String?,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Accent)
                }
                Column {
                    Text(chart.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(chart.subtitle.take(36), fontSize = 12.sp, color = Accent)
                }
            }
            StatusLine(status, busy)
        }
        items(songs, key = { it.hash }) { song ->
            SongRow(
                item = song,
                selected = song.hash == selectedHash,
                onClick = { onSong(song) },
            )
        }
    }
}
