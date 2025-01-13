package net.vicp.zchong.live.central.client.gatt

import android.content.Context
import net.vicp.zchong.live.central.client.AbstractReceiver
import net.vicp.zchong.live.central.client.FillBlockingQueue
import net.vicp.zchong.live.central.client.IFill

/**
 * @author zhangchong
 * @date 2024/8/20 07:30
 */
class GattReceiver(context: Context) : AbstractReceiver(context) {
    override fun createReceiverBuffer(): IFill? {
        buffer = FillBlockingQueue(100)
        callback?.onReceiveBufferCreate(true)
        return buffer
    }
}
