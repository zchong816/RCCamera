package net.vicp.zchong.live.peripheral.sender

import android.os.Handler

/**
 * @author zhangchong
 * @date 2024/8/19 20:47
 */
interface ISender {
    fun startSendData()
    fun writeBuffer(data: ByteArray?)
    @Throws(InterruptedException::class)
    fun readBuffer(): ByteArray?
    fun getMtu(): Int
    fun getBufferSize(): Int
    fun createBuffer()
    fun send()
    fun destroy()
    fun getReadHandler(): Handler?
    fun setCallback(callback:ISenderCallback)
    fun getAddress(): String?
    fun getCreateCallback(): AbstractSender.ICreateSenderCallback?
}
