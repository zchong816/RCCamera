package net.vicp.zchong.live.common

/**
 * @author zhangchong
 * @date 2024/8/20 17:52
 */
interface DataConnectType {
    companion object {
        const val SPP_STREAM = 0
        const val SPP_BUFFER = 1
        const val GATT_OVER_BREDR = 2
        const val WIFI_P2P = 3
        const val WIFI_AP = 4
        const val CORE = 5
        const val DEFAULT = SPP_BUFFER
    }
}
