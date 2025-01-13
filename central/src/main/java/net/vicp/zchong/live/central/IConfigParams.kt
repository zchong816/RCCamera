package net.vicp.zchong.live.central

/**
 * @author zhangchong
 * @date 2024/8/21 14:57
 */
interface IConfigParams {
    fun getCarmerParams(): String?
    fun getDataConnectType(): Int
    fun isDebugBandWidth(): Boolean
    fun getDebugBandWidth(): Int
    fun getCameraFps():Int
    fun getCameraPreviewSize():Pair<Int,Int>
}