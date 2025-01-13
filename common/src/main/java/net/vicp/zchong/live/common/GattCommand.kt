package net.vicp.zchong.live.common

/**
 * @author zhangchong
 * @date 2024/8/18 14:22
 */
interface GattCommand {
    companion object {
        const val CONNECT = "1"
        const val OPEN_CAMERA = "2"
        const val DEBUG_BANDWIDTH = "3"
        const val OPEN_DEBUG_BANDWIDTH = "4"
    }
}
