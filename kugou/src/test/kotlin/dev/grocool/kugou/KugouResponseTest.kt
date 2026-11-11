package dev.grocool.kugou

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** 用真实响应形状的脱敏 fixture 锁定解析行为，不依赖网络。 */
class KugouResponseTest {

    @Test
    fun `extracts the first playable url from a successful stream response`() {
        val raw = """
            {"status":1,"url":["http://fs.youthandroid2.kugou.com/202611111258/x/full/ap3.mp3"],
             "backupUrl":["http://fs.youthandroid.kugou.com/backup.mp3"],
             "bitRate":128000,"timeLength":61,"extName":"mp3"}
        """.trimIndent()
        assertEquals(
            "http://fs.youthandroid2.kugou.com/202611111258/x/full/ap3.mp3",
            KugouResponse.streamUrl(raw),
        )
    }

    @Test
    fun `returns null for a VIP restricted response`() {
        val raw = """
            {"status":2,"priv_status":0,"fail_process":["pkg","buy"],
             "hash_offset":{"start_ms":69600,"end_ms":129600}}
        """.trimIndent()
        assertNull(KugouResponse.streamUrl(raw))
    }

    @Test
    fun `extracts song hashes in order and lowercases them`() {
        val raw = """
            {"status":1,"data":{"lists":[
              {"SongName":"A","FileHash":"ABCDEF1234567890ABCDEF1234567890"},
              {"SongName":"B","FileHash":"11111111111111111111111111111111"}]}}
        """.trimIndent()
        assertEquals(
            listOf("abcdef1234567890abcdef1234567890", "11111111111111111111111111111111"),
            KugouResponse.searchHashes(raw),
        )
    }

    @Test
    fun `malformed json degrades to empty results`() {
        assertNull(KugouResponse.streamUrl("not-json"))
        assertEquals(emptyList<String>(), KugouResponse.searchHashes("{}"))
    }
}
