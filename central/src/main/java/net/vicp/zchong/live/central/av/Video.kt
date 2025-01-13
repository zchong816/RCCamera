package net.vicp.zchong.live.central.av

/**
 * @author zhangchong
 * @date 2024/8/14 07:12
 */
class Video {
    var presentationTimeMs: Long = 0
    var data: ByteArray? = null
    var length = 0
    var keyFrame: Byte = 0
}
