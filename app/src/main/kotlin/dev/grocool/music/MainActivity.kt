package dev.grocool.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.grocool.kugou.KugouTrack
import kotlinx.coroutines.launch

/** Apple Music 风格强调色；仅作风格参考，不使用其图标、字体或商标资源。 */
private val Accent = Color(0xFFFA233B)
private val LightBackground = Color(0xFFF5F5F7)
private val DarkBackground = Color(0xFF121214)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GrocoolTheme { GrocoolApp() }
        }
    }
}

@Composable
private fun GrocoolTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) {
            darkColorScheme(primary = Accent, background = DarkBackground, surface = Color(0xFF1C1C1E))
        } else {
            lightColorScheme(primary = Accent, background = LightBackground, surface = Color.White)
        },
        content = content,
    )
}

@Composable
private fun GrocoolApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { KugouMusicRepository() }
    val player = remember { MusicPlayer(context) }
    DisposableEffect(Unit) { onDispose { player.release() } }

    var query by remember { mutableStateOf("儿歌") }
    var status by remember { mutableStateOf("输入关键词，点搜索开始") }
    var tracks by remember { mutableStateOf<List<KugouTrack>>(emptyList()) }
    var current by remember { mutableStateOf<KugouTrack?>(null) }
    var playing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun play(track: KugouTrack) {
        busy = true
        status = "正在获取播放地址…"
        scope.launch {
            val url = repository.streamUrl(track.hash, freePart = track.privilege == 10).getOrNull()
            if (url == null) {
                status = "该曲目需要 VIP / 概念版免费听 Auth（M3/M4）"
            } else {
                player.play(url)
                current = track
                playing = true
                status = "正在播放：${track.name}"
            }
            busy = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            val track = current
            if (track != null) {
                MiniPlayer(track, playing) {
                    player.toggle()
                    playing = player.isPlaying
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                "Grocool Music",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text("收听", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Accent)
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("搜索酷狗概念版") },
                )
                Spacer(Modifier.size(8.dp))
                Button(
                    onClick = {
                        busy = true
                        status = "搜索中…"
                        scope.launch {
                            repository.search(query)
                                .onSuccess { list ->
                                    tracks = list
                                    status = if (list.isEmpty()) "没有结果" else "找到 ${list.size} 首，点击播放"
                                }
                                .onFailure { error -> status = "搜索失败：${error.message}" }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                ) { Text("搜索") }
            }

            Spacer(Modifier.height(12.dp))
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Accent)
            }
            Text(
                status,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tracks, key = { it.hash }) { track ->
                    TrackRow(track, selected = current?.hash == track.hash) { play(track) }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(track: KugouTrack, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) Accent else Accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = if (selected) Color.White else Accent,
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.name,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · "),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Text(if (track.privilege == 10) "VIP" else "免费", fontSize = 11.sp, color = Accent)
        }
    }
}

@Composable
private fun MiniPlayer(track: KugouTrack, playing: Boolean, onToggle: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Accent)
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.name,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.artist,
                    fontSize = 12.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "暂停" else "播放",
                    tint = Accent,
                )
            }
        }
    }
}
