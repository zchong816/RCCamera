package net.vicp.zchong.live.peripheral.sender

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import net.vicp.zchong.live.common.AVDataCommandHeader
import net.vicp.zchong.live.common.BtTrack
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue

/**
 * @author zhangchong
 * @date 2024/8/12 20:50
 */
abstract class AbstractSender(protected val context: Context, protected val createSenderCallback: ICreateSenderCallback? = null) : ISender {
    @JvmField
    protected var buffer: BlockingQueue<ByteArray>? = null
    @JvmField
    protected var writeBufferHandlerThread: HandlerThread?
    protected var writeBufferHandler: Handler?
    @JvmField
    protected var readBufferHandlerThread: HandlerThread?
    protected var readBufferHandler: Handler?
    var senderCallback: ISenderCallback? = null
    protected var sendThread: Thread? = null

    protected var debugThread: Thread? = null

    interface ICreateSenderCallback {
        fun callback(address: String?)
    }

    override fun getCreateCallback(): ICreateSenderCallback? {
        return createSenderCallback
    }

    override fun createBuffer() {
        if (getBufferSize() > 0) {
            buffer = ArrayBlockingQueue(getBufferSize())
        }
    }

    override fun getReadHandler(): Handler? {
        return readBufferHandler;
    }

    var getDataMs = SystemClock.uptimeMillis()

    init {
        writeBufferHandlerThread = HandlerThread("write_buffer_handler_thread")
        writeBufferHandlerThread!!.start()
        writeBufferHandler = Handler(writeBufferHandlerThread!!.getLooper())

        readBufferHandlerThread = HandlerThread("read_buffer_handler_thread")
        readBufferHandlerThread!!.start()
        readBufferHandler = Handler(readBufferHandlerThread!!.getLooper())
    }

    fun startDebugBandWidth(kbps: Int) {
        if(debugThread == null) {
            debugThread = object : Thread("debug_thread") {
                override fun run() {
                    while (true) {
                        try {
                            sleep(20)
                        } catch (t: Throwable) {
                            t.printStackTrace()
                            break
                        }
                        val data = ByteArray(kbps * 1000 / 8 / 100 * 2){AVDataCommandHeader.DEBUG_BANDWIDTH}
                        writeBuffer(data)
                    }
                }
            }
        }
        debugThread?.start()
    }

    @Throws(InterruptedException::class)
    override fun readBuffer(): ByteArray? {
        return try {
            val data: ByteArray = buffer!!.take()
            if (SystemClock.uptimeMillis() - getDataMs > 3000) {
                getDataMs = SystemClock.uptimeMillis()
                Log.d(TAG, "readBuffer buffer_size:" + buffer!!.size)
            }
            data
        } catch (t: Throwable) {
            t.printStackTrace()
            ByteArray(0)
        }
    }

    override fun setCallback(callback: ISenderCallback) {
        this.senderCallback = callback
    }

    private  var writeBufferMs = 0L
    private var writeBufferByteSize: Int = 0
    override fun writeBuffer(data: ByteArray?) {
        writeBufferHandler!!.post(Runnable {
            if (data == null || data.size == 0) {
                return@Runnable
            }
            if (writeBufferMs == 0L) {
                writeBufferMs = SystemClock.uptimeMillis()
            }
            val mtu = getMtu()
            val remainingCapacity = buffer!!.remainingCapacity()
            if (mtu > 0) {
                var tmp: ByteArray
                val size = data.size / mtu
                val remainSize = data.size % mtu
                val segCount = size + if (remainSize == 0) 0 else 1
                if (remainingCapacity < segCount) {
                    Log.e(
                        TAG,
                        "writeBuffer fail mtu:" + mtu + " remainingCapacity:" + remainingCapacity
                                + " buffer_size:" + buffer!!.size
                    )
                    return@Runnable
                }
                for (i in 0 until size) {
                    tmp = ByteArray(mtu)
                    System.arraycopy(data, i * mtu, tmp, 0, mtu)
                    var success = buffer!!.offer(tmp)
                    if (success) {
                        writeBufferByteSize += tmp.size
                    }
                }
                if (remainSize > 0) {
                    tmp = ByteArray(remainSize)
                    System.arraycopy(data, size * mtu, tmp, 0, remainSize)
                    var success = buffer!!.offer(tmp)
                    if (success) {
                        writeBufferByteSize += tmp.size
                    }
                }
            } else {
                val success = buffer!!.offer(data)
                if (success) {
                    writeBufferByteSize += data.size
                }
                if (!success) {
                    Log.e(TAG, "writeBuffer fail mtu:$mtu remainingCapacity:$remainingCapacity")
                }
            }
            val deltaMs = SystemClock.uptimeMillis() - writeBufferMs
            if (deltaMs > 3000) {
                Log.d(TAG, "writeBuffer buffer_size:" + buffer!!.size)
                val size = buffer!!.size
                val total: Int = (buffer?.remainingCapacity() ?: -1) + (buffer?.size ?: -1)
                val kbps = (writeBufferByteSize / deltaMs * 1000.0 / 1000 * 8).toInt()
                val bufPercent =
                    if (total > 0) (Math.round(size* 1.0 / total * 100)).toInt() else -1
                Log.d(BtTrack.TAG, "onBufferSizeChange used:$size total:$total per:$bufPercent%")
                Log.d(BtTrack.TAG, "onBufferWriteBandwidthChange : $kbps")
                senderCallback?.onBufferPercentChange(bufPercent)
                senderCallback?.onBufferWriteBandwidthChange(kbps)
                writeBufferMs = SystemClock.uptimeMillis()
                writeBufferByteSize = 0
            }

        })
    }

    override fun startSendData() {
        if (sendThread == null) {
            sendThread = object : Thread("send_thread") {
                override fun run() {
                    Log.d(TAG, "send_thread start buffer_size:" + buffer!!.size)
                    while (buffer!!.size < 3) {
                        val sleepMs = 20
                        Log.e(
                            TAG,
                            "send_thread start sleep " + sleepMs + "ms buffer_size:" + buffer!!.size
                        )
                        try {
                            sleep(sleepMs.toLong())
                        } catch (t: Throwable) {
                            t.printStackTrace()
                            break
                        }
                    }
                    send()
                }
            }
            sendThread!!.start()
        }
    }

    override fun destroy() {
        Log.d(TAG, "destroy")
        if (writeBufferHandler != null && writeBufferHandler!!.looper != null) {
            writeBufferHandler!!.looper.quit()
        }
        if (readBufferHandler != null && readBufferHandler!!.looper != null) {
            readBufferHandler!!.looper.quit()
        }
        if (sendThread != null) {
            sendThread!!.interrupt()
            sendThread = null
        }
        if (buffer != null) {
            buffer!!.clear()
        }
        if (writeBufferHandlerThread != null) {
            writeBufferHandlerThread!!.interrupt()
            writeBufferHandlerThread = null
        }

        if (readBufferHandlerThread != null) {
            readBufferHandlerThread!!.interrupt()
            readBufferHandlerThread = null
        }
        debugThread?.interrupt()
        senderCallback = null
    }

    companion object {
        private const val TAG = "AbstractSender"
    }
}
