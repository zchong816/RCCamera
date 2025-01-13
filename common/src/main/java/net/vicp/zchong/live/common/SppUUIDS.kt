package net.vicp.zchong.live.common

import java.util.UUID

/**
 * @author zhangchong
 * @date 2024/8/20 06:02
 */
interface SppUUIDS {
    companion object {
        @kotlin.jvm.JvmField
        val CONNECT_UUID:UUID = UUID.fromString("00001101-2000-1000-8000-00805F9B34FB")
    }
}
