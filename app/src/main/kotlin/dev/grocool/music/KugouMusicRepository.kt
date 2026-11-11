package dev.grocool.music

import android.util.Log
import dev.grocool.kugou.Chart
import dev.grocool.kugou.HomeFeed
import dev.grocool.kugou.KugouApi
import dev.grocool.kugou.KugouRepository
import dev.grocool.kugou.Platform
import dev.grocool.kugou.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * App 层数据入口：把纯 JVM 的 [KugouRepository] 包成 suspend 函数并统一在
 * [Dispatchers.IO] 执行（Android 主线程禁止网络请求）。
 *
 * 保留 M1 的 `Log.d("GrocoolMusic")` 诊断链路：真机出问题时
 * `adb logcat -d -s GrocoolMusic` 能直接看到每个接口的 status / error_code，
 * 不会再把验签或风控失败误显示成「没有结果」。
 */
class KugouMusicRepository(
    private val repository: KugouRepository = KugouRepository(KugouApi(platform = Platform.LITE)),
) {

    suspend fun homeFeed(): Result<HomeFeed> = io("homeFeed") { repository.homeFeed() }

    suspend fun charts(): Result<List<Chart>> = io("charts") { repository.charts().items }

    suspend fun chartSongs(chart: Chart, page: Int = 1): Result<List<Song>> =
        io("chartSongs(${chart.id})") { repository.chartSongs(chart, page).items }

    suspend fun search(keyword: String): Result<List<Song>> =
        io("search($keyword)") { repository.search(keyword).items }

    /** 取可播放 URL；VIP 曲自动取试听片段。 */
    suspend fun streamUrl(song: Song): Result<String?> = io("stream(${song.hash.take(8)})") {
        repository.streamUrl(song)
    }

    private suspend inline fun <T> io(
        label: String,
        crossinline block: () -> T,
    ): Result<T> = runCatching {
        withContext(Dispatchers.IO) {
            val value = block()
            Log.d(TAG, "$label ok")
            value
        }
    }.onFailure { Log.d(TAG, "$label failed: ${it.message}") }

    private companion object {
        const val TAG = "GrocoolMusic"
    }
}
