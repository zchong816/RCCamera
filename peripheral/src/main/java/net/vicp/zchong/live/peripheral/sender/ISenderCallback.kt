package net.vicp.zchong.live.peripheral.sender

/**
 * @author zhangchong
 * @date 2024/8/23 08:26
 */
interface ISenderCallback {
    fun onUploadBandwidthChange(kbps: Int, bytes: Long)
    fun onBufferWriteBandwidthChange(kbps: Int)
    fun onBufferPercentChange(bufferPercent: Int)
}