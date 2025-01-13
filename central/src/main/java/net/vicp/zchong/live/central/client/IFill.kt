package net.vicp.zchong.live.central.client

import java.io.IOException

/**
 * @author zhangchong
 * @date 2024/8/14 08:23
 */
interface IFill {
    @Throws(IOException::class, InterruptedException::class)
    fun fill(size: Int): ByteArray?
    fun getFillSize(): Int
    fun offer(data: ByteArray?): Boolean
}
