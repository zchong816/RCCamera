package net.vicp.zchong.live.common

/**
 * @author zhangchong
 * @date 2024/8/12 21:13
 */
interface AVDataCommandHeader {
    companion object {
        const val SPS_PPS_VPS = 0x87.toByte()
        const val AUDIO = 0x88.toByte()
        const val VIDEO = 0x89.toByte()
        const val DEBUG_BANDWIDTH = 0x90.toByte()
    }
}
