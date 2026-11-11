package dev.grocool.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.grocool.music.adapter.ChartItem
import dev.grocool.music.adapter.SongItem
import dev.grocool.music.adapter.coverTint
import dev.grocool.music.ui.shape.SquirclePillShape

/**
 * iOS-style shared components.
 *
 * Deliberately avoids Material 3's `Surface` / `AssistChip` / `OutlinedTextField`
 * / `NavigationBar`: those were exactly the elements that made earlier builds read
 * as plain MD3. Shapes come from the ported Apple continuous-corner implementation,
 * surfaces are semi-transparent glass cards, and controls use squircle pills.
 */

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = IosShapes.Card
    val base = glassSurface()
    val outline = glassBorder()
    Box(
        modifier = modifier
            .shadow(8.dp, shape, clip = false)
            .clip(shape)
            .background(base)
            .border(0.5.dp, outline, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) { content() }
}

/** Real cover art via Coil; a stable tinted initial shows while loading. */
@Composable
fun CoverBox(
    title: String,
    hash: String,
    size: Int = 52,
    coverUrl: String = "",
    modifier: Modifier = Modifier,
) {
    val shape = IosShapes.SmallCard
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(coverTint(hash), coverTint(hash).copy(alpha = 0.65f))
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title.take(1).ifEmpty { "♪" },
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size / 2.6).sp,
        )
        if (coverUrl.startsWith("http")) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size.dp).clip(shape),
            )
        }
    }
}

/** Apple-style large section title with a red subtitle. */
@Composable
fun SectionHeader(title: String, subtitle: String = "", modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(top = 26.dp, bottom = 12.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (subtitle.isNotBlank()) {
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Accent)
        }
    }
}

/** iOS search field: filled grey squircle, magnifier prefix, no MD3 outline. */
@Composable
fun IosSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(SquirclePillShape())
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = secondaryLabel(),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
            ),
            cursorBrush = SolidColor(Accent),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.size(8.dp))
        Box(
            modifier = Modifier
                .clip(SquirclePillShape())
                .background(Accent)
                .clickable(onClick = onSearch)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("搜索", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Squircle keyword pill, replacing Material 3 AssistChip. */
@Composable
fun KeywordPill(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(SquirclePillShape())
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Song row inside an iOS glass card. */
@Composable
fun SongRow(
    item: SongItem,
    selected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (item.rank > 0) {
                Text(
                    "${item.rank}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (item.rank <= 3) Accent else secondaryLabel(),
                    modifier = Modifier.width(26.dp),
                )
            }
            CoverBox(item.title, item.hash, size = 46, coverUrl = item.coverUrl)
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selected) Accent else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = secondaryLabel(),
                )
            }
            Spacer(Modifier.size(6.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(item.badge, style = MaterialTheme.typography.labelSmall, color = Accent)
                Text(item.durationLabel, style = MaterialTheme.typography.labelSmall, color = secondaryLabel())
            }
        }
    }
}

/** Horizontal chart cards with real artwork. */
@Composable
fun ChartShelf(charts: List<ChartItem>, onClick: (ChartItem) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(end = 20.dp),
    ) {
        items(charts, key = { it.id }) { chart ->
            GlassCard(modifier = Modifier.width(150.dp), onClick = { onClick(chart) }) {
                Column(modifier = Modifier.padding(12.dp)) {
                    CoverBox(chart.title, chart.id.toString(), size = 126, coverUrl = chart.coverUrl)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        chart.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        chart.subtitle.take(24),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = secondaryLabel(),
                    )
                }
            }
        }
    }
}

/** Floating glass mini player — a card, not an MD3 bottom bar. */
@Composable
fun MiniPlayer(item: SongItem?, playing: Boolean, onToggle: () -> Unit) {
    if (item == null) return
    GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverBox(item.title, item.hash, size = 42, coverUrl = item.coverUrl)
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = secondaryLabel(),
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

@Composable
fun StatusLine(text: String, busy: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Accent, strokeWidth = 2.dp)
            Spacer(Modifier.size(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, color = secondaryLabel())
    }
}
