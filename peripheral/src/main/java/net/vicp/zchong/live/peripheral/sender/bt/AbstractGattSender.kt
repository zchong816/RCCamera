package net.vicp.zchong.live.peripheral.sender.bt

import android.content.Context
import net.vicp.zchong.live.peripheral.sender.AbstractSender

/**
 * @author zhangchong
 * @date 2025/1/13 20:59
 */
abstract class AbstractGattSender(context: Context) : AbstractSender(context!!) {
    override fun getMtu(): Int {
        return 512
    }
}
