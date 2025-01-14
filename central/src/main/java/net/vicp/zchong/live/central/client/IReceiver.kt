package net.vicp.zchong.live.central.client

import android.bluetooth.BluetoothDevice
import net.vicp.zchong.live.central.IErrorCallback

/**
 * @author zhangchong
 * @date 2024/8/20 00:25
 */
interface IReceiver {
    fun fillData(data: ByteArray?)
    fun handleData()
    fun startReceiveData()
    fun pushRtmpLiveStream(rtmp: String?, w: Int, h: Int, fps: Int)
    fun createReceiverBuffer(): IFill?
    fun setDevice(device: BluetoothDevice?)
    fun destroy()
    var callback: IReceiverCallback?
    var avCallback: IAVCallback?
    var errorCallback: IErrorCallback?
}
