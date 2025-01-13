package net.vicp.zchong.live.central.client

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream

/**
 * @author zhangchong
 * @date 2024/8/20 00:39
 */
@SuppressLint("MissingPermission")
abstract class AbstractSocketReceiver(
    context: Context,
    private val isStreamMode: Boolean,
    val address: String? = null
) :
    AbstractReceiver(context) {
    private var inputStream: BufferedInputStream? = null
    private var fillDataThread: Thread? = null

    abstract fun getInputStream(): InputStream?
    abstract fun colse()

    open fun getBufferSize(): Int {
        return 200
    }
    override fun createReceiverBuffer(): IFill? {
        Log.d(TAG, "createReceiverBuffer ....")
        try {
            inputStream = BufferedInputStream(getInputStream())
        } catch (t: Throwable) {
            Log.d(TAG, "createReceiverBuffer fail : $t")
            t.printStackTrace()
        }
        if (inputStream == null) {
            callback?.onReceiveBufferCreate(false)
            return null
        }
        if (isStreamMode) {
            buffer = FillBufferedInputStream(inputStream)
        } else {
            buffer = FillBlockingQueue(getBufferSize())
            startFillReceiverBuffer()
        }
        Log.d(TAG, "createReceiverBuffer success")
        callback?.onReceiveBufferCreate(true)
        return buffer
    }

    override fun destroy() {
        Log.d(TAG, "destroy")
        super.destroy()
        if (inputStream != null) {
            try {
                inputStream!!.close()
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                inputStream = null
            }
        }
        colse()
        if (fillDataThread != null) {
            fillDataThread!!.interrupt()
        }
    }

    fun startFillReceiverBuffer() {
        if (fillDataThread == null) {
            fillDataThread = object : Thread("fill_data_thread") {
                override fun run() {
                    try {
                        fillData()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }
        fillDataThread!!.start()
    }

    @Throws(IOException::class)
    private fun fillData() {
        var fillDataMs = SystemClock.uptimeMillis()
        var buffer: ByteArray
        var bytesRead: Int
        val tmpBuffer = ByteArray(1024)
        if (inputStream == null) {
            return
        }
        val bufferedInputStream = BufferedInputStream(inputStream)
        while (bufferedInputStream!!.read(tmpBuffer).also { bytesRead = it } != -1) {
            if (SystemClock.uptimeMillis() - fillDataMs > 3000) {
                Log.d(
                    TAG,
                    "fillData bytesRead:$bytesRead receive buffer_size:${this.buffer!!.getFillSize()}"
                )
                fillDataMs = SystemClock.uptimeMillis()
            }
            buffer = ByteArray(bytesRead)
            System.arraycopy(tmpBuffer, 0, buffer, 0, bytesRead)
            val offerSuccess = this.buffer!!.offer(buffer)
            if (!offerSuccess) {
                Log.e(
                    TAG,
                    "receive buffer_size:" + this.buffer!!.getFillSize() + " offer_success:" + offerSuccess
                )
            }
        }
    }

    companion object {
        private const val TAG = "AbstractSocketReceiver"
    }
}
