package dev.grocool.music

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

/**
 * M1 最小播放封装。后续 M2 会替换为 Media3 MediaSessionService +
 * 前台服务 + 通知栏控制，此处先验证「真实 URL 能否在安卓上放出声」。
 */
class MusicPlayer(context: Context) {

    private val player: ExoPlayer = ExoPlayer.Builder(context).build()

    fun play(url: String) {
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.play()
    }

    fun toggle() {
        if (player.isPlaying) player.pause() else player.play()
    }

    val isPlaying: Boolean get() = player.isPlaying

    fun release() = player.release()
}
