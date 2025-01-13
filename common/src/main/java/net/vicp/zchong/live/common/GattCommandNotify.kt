package net.vicp.zchong.live.common

/**
 * @author zhangchong
 * @date 2024/8/18 14:41
 */
interface GattCommandNotify {
    companion object {
        const val CONNECT_OK = "CONNECT_OK"
        const val PERIPHERAL_PAGE_OPEN = "PERIPHERAL_PAGE_OPEN"
        const val DEBUG_BANDWIDTH_OK = "DEBUG_BANDWIDTH_OK"
        const val CAMERA_CLOSE = "CAMERA_CLOSE"
        const val PERIPHERAL_ERROR = "PERIPHERAL_ERROR"
    }
}
