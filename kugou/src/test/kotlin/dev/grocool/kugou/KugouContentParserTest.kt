package dev.grocool.kugou

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * M2 内容层契约测试。
 *
 * 夹具是 2026-11-11 真实网关响应的**精简形状**（不含用户数据/音频 URL），
 * 用来锁定字段映射；防止上游字段改名时 UI 静默拿到空列表。
 */
class KugouContentParserTest {

    @Test
    fun `charts parses rank list and normalizes cover placeholder`() {
        val raw = """
        {"status":1,"data":{"total":2,"info":[
          {"rankid":8888,"rank_cid":167680,"rankname":"TOP500",
           "img_9":"http://imge.kugou.com/stdmusic/{size}/a.jpg",
           "intro":"每天更新","update_frequency":"每天",
           "songinfo":[{"songname":"歌手A - 歌一"},{"songname":"歌手B - 歌二"}]},
          {"rankid":6666,"rank_cid":167678,"rankname":"飙升榜","img_9":""}
        ]}}
        """.trimIndent()
        val charts = KugouContentParser.charts(raw)
        assertEquals(2, charts.size)
        assertEquals(8888L, charts[0].id)
        assertEquals(167680L, charts[0].cid)
        assertEquals("TOP500", charts[0].name)
        assertTrue(charts[0].coverUrl.startsWith("https://"))
        assertFalse(charts[0].coverUrl.contains("{size}"))
        assertEquals(listOf("歌手A - 歌一", "歌手B - 歌二"), charts[0].preview)
        assertEquals("飙升榜", charts[1].name)
    }

    @Test
    fun `chart songs extract hash and duration from nested audio_info`() {
        val raw = """
        {"status":1,"data":{"total":1,"songlist":[
          {"songname":"慌啥子嘛","author_name":"村口吴老二","audio_id":658331615,
           "album_audio_id":968734427,
           "audio_info":{"hash_128":"D0F928C394C175BC0F4CAD8B9C3B5E9E","duration_128":"186000"},
           "album_info":{"album_name":"慌啥子嘛","sizable_cover":"http://x/{size}/c.jpg"},
           "trans_param":{"union_cover":""}}
        ]}}
        """.trimIndent()
        val songs = KugouContentParser.chartSongs(raw)
        assertEquals(1, songs.size)
        val song = songs[0]
        assertEquals("d0f928c394c175bc0f4cad8b9c3b5e9e", song.hash)
        assertEquals("慌啥子嘛", song.name)
        assertEquals("村口吴老二", song.artist)
        assertEquals(186000L, song.durationMs)
        assertEquals(968734427L, song.albumAudioId)
        assertTrue(song.coverUrl.contains("/480/"))
        assertTrue(song.isPlayable)
        assertFalse(song.isVip)
    }

    @Test
    fun `hot search groups keep keyword order`() {
        val raw = """
        {"status":1,"errcode":0,"data":{"list":[
          {"name":"热搜榜","keywords":[{"keyword":"烂奇迹"},{"keyword":"为证共此生"},{"keyword":""}]},
          {"name":"空榜","keywords":[]}
        ]}}
        """.trimIndent()
        val groups = KugouContentParser.hotSearchGroups(raw)
        assertEquals(1, groups.size)
        assertEquals("热搜榜", groups[0].name)
        assertEquals(listOf("烂奇迹", "为证共此生"), groups[0].keywords)
    }

    @Test
    fun `malformed json degrades to empty lists instead of throwing`() {
        assertTrue(KugouContentParser.charts("not json").isEmpty())
        assertTrue(KugouContentParser.chartSongs("{}").isEmpty())
        assertTrue(KugouContentParser.hotSearchGroups("<html>").isEmpty())
    }

    @Test
    fun `repository surfaces gateway errors instead of empty results`() {
        val transport = HttpTransport { """{"status":0,"error_code":20006,"err_msg":"err signature"}""" }
        val repo = KugouRepository(KugouApi(transport = transport))
        val error = assertThrows(IllegalStateException::class.java) { repo.charts() }
        assertTrue(error.message!!.contains("20006"))
    }

    @Test
    fun `repository assembles home feed shelves from fake transport`() {
        val transport = HttpTransport { request ->
            when {
                request.url.contains("/api/v3/search/hot_tab") -> HOT
                request.url.contains("/ocean/v6/rank/list") -> CHARTS
                request.url.contains("/openapi/kmr/v2/rank/audio") -> SONGS
                else -> """{"status":1,"data":{}}"""
            }
        }
        val feed = KugouRepository(KugouApi(transport = transport)).homeFeed()
        assertEquals(3, feed.shelves.size)
        assertTrue(feed.failures.isEmpty())
        assertEquals(ShelfKind.KEYWORDS, feed.shelves[0].kind)
        assertEquals(ShelfKind.CHARTS, feed.shelves[1].kind)
        assertEquals(ShelfKind.SONGS, feed.shelves[2].kind)
        assertEquals(1, feed.shelves[2].songs.size)
    }

    private companion object {
        const val HOT = """{"status":1,"data":{"list":[{"name":"热搜榜","keywords":[{"keyword":"烂奇迹"},{"keyword":"为证共此生"}]}]}}"""
        const val CHARTS = """{"status":1,"data":{"info":[{"rankid":8888,"rank_cid":167680,"rankname":"TOP500","img_9":""}]}}"""
        const val SONGS = """{"status":1,"data":{"songlist":[{"songname":"慌啥子嘛","author_name":"村口吴老二","audio_info":{"hash_128":"D0F928C394C175BC0F4CAD8B9C3B5E9E","duration_128":"186000"}}]}}"""
    }
}

/** POST body 签名契约：body 必须参与签名，且序列化顺序稳定。 */
class PostBodySignatureTest {

    @Test
    fun `json body is compact and keeps declaration order`() {
        val body = KugouContentApi.jsonBody(
            "pagesize" to 30,
            "rank_cid" to 167680L,
            "page" to 1,
            "rank_id" to 8888L,
        )
        assertEquals(""""pagesize":30,"rank_cid":167680,"page":1,"rank_id":8888""", body.drop(1).dropLast(1))
    }

    @Test
    fun `POST body is included in signature and sent by request builder`() {
        val credentials = KugouCredentials()
        val builder = KugouRequestBuilder(Platform.LITE, credentials) { 1700000000L }
        val body = KugouContentApi.jsonBody("page" to 1)
        val withBody = builder.build(
            "/openapi/kmr/v2/rank/audio",
            emptyMap(),
            RequestOptions(method = "POST", body = body),
        )
        val withoutBody = builder.build(
            "/openapi/kmr/v2/rank/audio",
            emptyMap(),
            RequestOptions(method = "POST"),
        )
        assertNotEquals(withoutBody.params["signature"], withBody.params["signature"])
        assertEquals(body, withBody.body)
        assertEquals("application/json", withBody.headers["Content-Type"])
        assertTrue(withBody.url.contains("/openapi/kmr/v2/rank/audio"))
    }

    @Test
    fun `GET requests keep the M0 golden signature unchanged`() {
        val builder = KugouRequestBuilder(Platform.LITE, KugouCredentials()) { 1700000000L }
        val request = builder.build("/v2/search/song", mapOf("keyword" to "儿歌"))
        assertEquals("", request.body)
        assertFalse(request.headers.containsKey("Content-Type"))
        assertTrue(request.params["signature"]!!.length == 32)
    }
}
