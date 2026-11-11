package dev.grocool.music

import dev.grocool.kugou.KugouApi
import dev.grocool.kugou.KugouResponse
import dev.grocool.kugou.KugouTrack
import dev.grocool.kugou.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * M1 的播放数据入口：把协议层的原始 JSON 调用包成 suspend 函数，
 * 全部在 IO 线程执行（Android 主线程禁止网络请求）。
 *
 * 这里的 DTO 仍是协议层的轻量 [KugouTrack]；完整的领域模型、缓存与
 * 分页属于 M2 的 `KugouRepository`，不要在 App 层继续堆积业务逻辑。
 */
class KugouMusicRepository(
    private val api: KugouApi = KugouApi(platform = Platform.LITE),
) {
    suspend fun search(keyword: String): Result<List<KugouTrack>> = runCatching {
        withContext(Dispatchers.IO) {
            KugouResponse.searchTracks(api.search(keyword))
        }
    }

    /**
     * 取可播放 URL。VIP 曲目（Privilege=10）在未登录时用 `freePart=true`
     * 取试听片段；完整播放需要 M3/M4 的登录态与概念版免费听 Auth。
     */
    suspend fun streamUrl(hash: String, freePart: Boolean = false): Result<String?> = runCatching {
        withContext(Dispatchers.IO) {
            KugouResponse.streamUrl(api.songUrl(hash, quality = "128", freePart = freePart))
        }
    }
}
