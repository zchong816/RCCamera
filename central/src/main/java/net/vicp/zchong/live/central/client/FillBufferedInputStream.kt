package net.vicp.zchong.live.central.client

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream

/**
 * @author zhangchong
 * @date 2024/8/14 08:41
 */
class FillBufferedInputStream(`in`: InputStream?) : BufferedInputStream(`in`), IFill {
    @Throws(IOException::class)
    override fun fill(size: Int): ByteArray? {
        val tmp = ByteArray(size)
        val bytesRead = read(tmp)
        val buffer = ByteArray(bytesRead)
        System.arraycopy(tmp, 0, buffer, 0, buffer.size)
        return buffer
    }

    override fun getFillSize(): Int {
        return -1
    }

    override fun offer(data: ByteArray?): Boolean {
        return false
    }
}
