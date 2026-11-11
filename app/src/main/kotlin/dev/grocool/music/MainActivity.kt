package dev.grocool.music

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import dev.grocool.kugou.KugouCrypto
import dev.grocool.kugou.Platform

/**
 * M0 构建管线验证用的最小入口。
 *
 * 这里故意不使用 Compose 或 Media3：目的只是验证「CI 编译 / 统一签名 /
 * 测试版 Release / 覆盖安装」这条链路。M1 导入 BitChord UI 基线时，本文件
 * 会被完整的 Compose 界面替换。
 *
 * 界面上显示的是协议层的签名自检值，用于确认 `kugou` 模块确实被打进 APK，
 * 且 Kotlin 移植的签名算法在 Android 运行时上得到与 JVM 相同的结果：
 * c8c82518cd5aaac024073dc50b2566be
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val probe = mapOf<String, Any?>(
            "appid" to 3116,
            "clientver" to 11440,
            "clienttime" to 1700000000L,
            "dfid" to "-",
            "mid" to "goldenmid123",
            "uuid" to "-",
        )
        val signature = KugouCrypto.signatureAndroid(probe, Platform.LITE)

        val view = TextView(this).apply {
            text = buildString {
                appendLine("Grocool Music")
                appendLine("M0 构建管线验证版")
                appendLine()
                appendLine("协议层签名自检：")
                appendLine(signature)
                appendLine()
                append("期望值：c8c82518cd5aaac024073dc50b2566be")
            }
            textSize = 15f
            setPadding(48, 120, 48, 48)
        }
        setContentView(view)
    }
}
