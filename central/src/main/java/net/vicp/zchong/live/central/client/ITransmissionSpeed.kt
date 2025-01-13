package net.vicp.zchong.live.central.client

/**
 * @author zhangchong
 * @date 2024/8/14 11:51
 */
interface ITransmissionSpeed {
    fun callback(speed: Int, deltaTimes: Long, avgSpeed: Int, sumTime: Long, sumBytes: Long)
}
