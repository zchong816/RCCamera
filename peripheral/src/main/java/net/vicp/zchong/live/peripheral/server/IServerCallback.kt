package net.vicp.zchong.live.peripheral.server

/**
 * @author zhangchong
 * @date 2024/8/16 09:44
 */
interface IServerCallback {
    fun onConnect(dataConnectType: Int)
    fun onDisonnect()
    fun onOpenCamera(params: String?)
    fun openDebugBandwidth()
}
