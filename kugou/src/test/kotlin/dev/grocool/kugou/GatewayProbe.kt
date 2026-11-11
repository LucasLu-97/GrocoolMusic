package dev.grocool.kugou

/**
 * 本地诊断入口（不属于测试套件，不会被 JUnit 执行）。
 *
 * 目的：用**真实编译出的 Kotlin 代码**打酷狗网关，验证 M2 的 POST body 签名、
 * 领域模型解析与首页装配在 JVM / Android 实现上同样成立。Python 冒烟脚本
 * (`tools/smoke_test.py`) 是独立实现，只能证明协议规则，不能证明本仓库代码。
 *
 * 运行：`gradle :kugou:gatewayProbe`（需国内网络；境外 CI 会触发人机验证）。
 * 只读取公开榜单 / 热搜元数据，不存储音频文件、不输出播放 URL。
 */
fun main() {
    val repo = KugouRepository()

    fun section(title: String, block: () -> Unit) {
        println("\n=== $title ===")
        runCatching(block).onFailure { println("  FAILED: ${it.message}") }
    }

    section("榜单列表 rankList (GET)") {
        val result = repo.charts()
        println("  count=${result.items.size} status=${result.state.status}")
        result.items.take(4).forEach {
            println("   ${it.id} | cid=${it.cid} | ${it.name} | ${it.updateFrequency}")
        }
    }

    section("热搜 hotSearch (GET)") {
        val result = repo.hotSearchGroups()
        println("  groups=${result.items.size}")
        result.items.take(3).forEach {
            println("   ${it.name}: ${it.keywords.take(4)}")
        }
    }

    section("榜单歌曲 rankAudio (POST + body 参与签名)") {
        val chart = repo.charts().items.firstOrNull()
        if (chart == null) {
            println("  无榜单可用")
        } else {
            val songs = repo.chartSongs(chart, pageSize = 5)
            println("  chart=${chart.name} count=${songs.items.size}")
            songs.items.take(3).forEach {
                println("   ${it.name} | ${it.artist} | hash=${it.hash.take(10)}… | ${it.durationMs}ms | ${it.album}")
            }
        }
    }

    section("首页内容流 homeFeed (多接口装配)") {
        val feed = repo.homeFeed()
        println("  shelves=${feed.shelves.size} kinds=${feed.shelves.map { it.kind }}")
        feed.shelves.forEach {
            println("   [${it.kind}] ${it.title}: charts=${it.charts.size} songs=${it.songs.size} keywords=${it.keywords.size}")
        }
        println("  failures=${feed.failures}")
    }
}
