package net.vicp.zchong.live.central.client

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.LinkedBlockingQueue

/**
 * @author zhangchong
 * @date 2024/8/14 07:41
 */
class FillBlockingQueue(capacity: Int) : ArrayBlockingQueue<ByteArray?>(capacity), IFill {
    @Throws(InterruptedException::class)
    override fun fill(size: Int): ByteArray? {
        return take()
    }
    override fun getFillSize(): Int {
        return super.size
    }

    override fun offer(data: ByteArray?): Boolean {
        return super.offer(data)
    }
}
