package net.vicp.zchong.live.peripheral.av

/**
 * @author zhangchong
 * @date 2024/8/12 14:08
 */
interface IAVCodecCallback {
    fun onGetAVData(data: ByteArray, isAudio: Boolean)
    fun onGetSpsPpsVps(data: ByteArray)
}
