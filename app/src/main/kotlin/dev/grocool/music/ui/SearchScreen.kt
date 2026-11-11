package dev.grocool.music.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.grocool.music.adapter.SongItem

/** 「搜索」页：iOS 风格搜索栏 + 玻璃卡片结果列表。 */
@Composable
fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    results: List<SongItem>,
    status: String,
    busy: Boolean,
    onSong: (SongItem) -> Unit,
    selectedHash: String?,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)) {
                Text(
                    "搜索",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text("酷狗音乐概念版", style = MaterialTheme.typography.labelMedium, color = Accent)
                Spacer(Modifier.size(14.dp))
                IosSearchField(
                    value = query,
                    onValueChange = onQueryChange,
                    onSearch = onSearch,
                )
                Spacer(Modifier.size(10.dp))
                StatusLine(status, busy)
            }
        }
        items(results, key = { it.hash }) { song ->
            SongRow(
                item = song,
                selected = song.hash == selectedHash,
                onClick = { onSong(song) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 「资料库」页：M3 占位，明确标注，不伪装成已实现功能。 */
@Composable
fun LibraryScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "资料库",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text("我的音乐", style = MaterialTheme.typography.labelMedium, color = Accent)
        Text(
            "收藏、歌单、本地音乐与最近播放将在 M3（登录态）接入。\n" +
                "当前版本已可用：搜索、官方榜单、榜单播放、热搜。",
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryLabel(),
            lineHeight = 20.sp,
        )
    }
}
