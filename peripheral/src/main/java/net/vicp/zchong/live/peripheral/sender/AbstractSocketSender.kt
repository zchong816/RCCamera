package net.vicp.zchong.live.peripheral.sender
import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.util.Log
import net.vicp.zchong.live.common.BtTrack
import java.io.IOException
import java.io.OutputStream

/**
 * @author zhangchong
 * @date 2025/1/13 23:45
 */
@SuppressLint("MissingPermission")
abstract class AbstractSocketSender(
    context: Context?,
    private val isStreamMode: Boolean, callback: ICreateSenderCallback? = null
) :
    AbstractSender(
        context!!,callback
    ) {

    private var serverSocketThread: Thread? = null
    private var outputStream: OutputStream? = null

    private var writeMs = 0L
    private var writeSize: Int = 0
    private var writeTotalSize: Long = 0L
    override fun getBufferSize(): Int {
        return if (!isStreamMode) {
            2000
        } else -1
    }

    override fun getMtu(): Int {
        return -1
    }

    abstract fun close()
    abstract fun getOutputStream(): OutputStream?

    abstract fun createServer(): Boolean
    override fun writeBuffer(data: ByteArray?) {
        if (!isStreamMode) {
            super.writeBuffer(data)
        } else {
            val bytesSize = data?.size ?: 0
            writeBufferHandler!!.post {
                if (outputStream != null) {
                    if (writeMs == 0L) {
                        writeMs = SystemClock.uptimeMillis()
                    }
                    try {
                        outputStream!!.write(data)
                    } catch (e: IOException) {
                        return@post
                    }
                    writeSize += bytesSize
                    if (writeTotalSize > Long.MAX_VALUE - 10 * 1024 * 1024) {
                        writeTotalSize = 0
                    }
                    writeTotalSize += bytesSize
                    val deltaMs = SystemClock.uptimeMillis() - writeMs
                    if (deltaMs > 3000) {
                        val kbps = (writeSize / deltaMs * 1000.0 / 1000 * 8).toInt()
                        Log.d(
                            BtTrack.TAG,
                            "onUploadBandwidthChange ${kbps}kbps total: ${(writeTotalSize / 1000)}KB"
                        )
                        senderCallback?.onUploadBandwidthChange(kbps, writeTotalSize)
                        writeMs = SystemClock.uptimeMillis()
                        writeSize = 0
                    }
                }
            }
        }
    }

    override fun send() {
        if (!isStreamMode) {
            var sendMs = SystemClock.uptimeMillis()
            var sendSize = 0
            while (true) {
                var data: ByteArray? = null
                try {
                    data = buffer!!.take()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
                if (data != null && outputStream != null) {
                    try {
                        outputStream!!.write(data)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                    sendSize += data.size
                    if (writeTotalSize > Long.MAX_VALUE - 10 * 1024 * 1024) {
                        writeTotalSize = 0
                    }
                    writeTotalSize += data.size
                    val deltaMs = SystemClock.uptimeMillis() - sendMs
                    if (deltaMs > 3000) {
                        val kbps = (sendSize / deltaMs * 1000.0 / 1000 * 8).toInt()
                        Log.d(
                            BtTrack.TAG,
                            "onUploadBandwidthChange ${kbps}kbps total: ${(writeTotalSize / 1000)}KB"
                        )
                        senderCallback?.onUploadBandwidthChange(kbps, writeTotalSize)
                        sendMs = SystemClock.uptimeMillis()
                        sendSize = 0
                        Log.d(TAG, "send buffer_size:" + buffer!!.size)
                    }
                }
            }
        }
    }

    override fun startSendData() {
        if (createServer()) {
            if (!isStreamMode) {
                super.startSendData()
            }
            serverSocketThread = object : Thread("server_socket_thread") {
                override fun run() {
                    try {
                        outputStream = getOutputStream()
                    } catch (t: Throwable) {
                        t.printStackTrace()
                        return
                    }
                }
            }
            serverSocketThread!!.start()
        }
    }

    override fun destroy() {
        super.destroy()
        if (outputStream != null) {
            try {
                outputStream!!.close()
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                outputStream = null
            }
        }
        close()
        if (serverSocketThread != null) {
            serverSocketThread!!.interrupt()
        }
    }

    companion object {
        private const val TAG = "AbstractSocketSender"
    }
}
