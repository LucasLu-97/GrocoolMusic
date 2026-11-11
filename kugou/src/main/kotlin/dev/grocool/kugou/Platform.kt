package dev.grocool.kugou

/**
 * 酷狗接口平台。
 *
 * 概念版（lite）与标准版共用同一套接口服务，但 **token 不通用**，且
 * appid / clientver / 签名盐值 / RSA 公钥都不同。凭据必须按平台隔离存储。
 */
enum class Platform(
    val id: String,
    val appId: Int,
    val clientVer: Int,
) {
    /** 酷狗音乐概念版，对应 KuGouMusicApi 的 `platform=lite`。 */
    LITE("lite", 3116, 11440),

    /** 酷狗音乐标准版。 */
    STANDARD("standard", 1005, 20489),
    ;

    val isLite: Boolean get() = this == LITE
}
