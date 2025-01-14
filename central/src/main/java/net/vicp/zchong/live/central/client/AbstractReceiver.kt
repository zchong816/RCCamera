package net.vicp.zchong.live.central.client

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import net.vicp.zchong.live.central.IErrorCallback
import net.vicp.zchong.live.central.av.Audio
import net.vicp.zchong.live.central.av.SpsPpsVps
import net.vicp.zchong.live.central.av.Video
import net.vicp.zchong.live.central.rtmp.RtmpManager
import net.vicp.zchong.live.common.AVDataCommandHeader
import net.vicp.zchong.live.common.BtTrack
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.Arrays

/**
 * @author zhangchong
 * @date 2024/8/20 00:27
 */
abstract class AbstractReceiver protected constructor(protected var context: Context) : IReceiver {
    protected var btDevice: BluetoothDevice? = null
    protected var buffer: IFill? = null
    protected var fillDataHandler: Handler? = null
    private var fillDataHandlerThread: HandlerThread? = null
    protected var rtmpManager: RtmpManager = RtmpManager()
    override var callback: IReceiverCallback? = null
    override var avCallback: IAVCallback? = null
    override var errorCallback: IErrorCallback? = null

//    var videoOutputStream: FileOutputStream? = FileOutputStream(File(context.cacheDir, "v.h264"))
//    var audioOutputStream: FileOutputStream? = FileOutputStream(File(context.cacheDir, "a.aac"))
    var videoOutputStream: FileOutputStream? = null
    var audioOutputStream: FileOutputStream? = null


    private val reeciveThread: Thread? = object : Thread("receive_thread") {
        override fun run() {
            handleData()
        }
    }

    init {
        if (fillDataHandlerThread == null && fillDataHandler == null) {
            fillDataHandlerThread = HandlerThread("fill_data_handler_thread")
            fillDataHandlerThread!!.start()
            fillDataHandler = Handler(fillDataHandlerThread!!.getLooper())
        }
    }

    override fun setDevice(device: BluetoothDevice?) {
        this.btDevice = device
    }

    override fun pushRtmpLiveStream(rtmp: String?, w: Int, h: Int, fps: Int) {
//        val finalRtmp = "rtmp://192.168.1.214/rtmplive"
        val finalRtmp = rtmp
        Log.d(BtTrack.TAG,"pushRtmpLiveStream $finalRtmp ${w}x${h} $fps")
        rtmpManager.prepareAudioRtp(false, 44100)
        rtmpManager.startStreamRtp(finalRtmp!!, w, h, fps)
    }

    override fun fillData(data: ByteArray?) {
        fillDataHandler!!.post {
            if (data != null && data.size > 0) {
                buffer!!.offer(data)
            }
        }
    }

    override fun startReceiveData() {
        reeciveThread!!.start()
    }

    override fun destroy() {
        Log.d(TAG, "destroy")
        if (fillDataHandler != null) {
            fillDataHandler!!.removeCallbacksAndMessages(null)
            fillDataHandler!!.looper.quit()
        }
        if (fillDataHandlerThread != null) {
            fillDataHandlerThread!!.interrupt()
        }
        reeciveThread?.interrupt()
        callback = null
        rtmpManager.destroy()

        try {
            audioOutputStream?.close()
            videoOutputStream?.close()
        } catch (t: Throwable) {
            t.printStackTrace()
        } finally {
            audioOutputStream = null
            videoOutputStream = null
        }

    }

    override fun handleData() {
        var buffer: ByteArray?
        var tmpBuffer: ByteArray?
        var bytesRead: Int
        var remainBuf: ByteArray? = null
        var begin = SystemClock.uptimeMillis()
        val defaultFillSize = 1024
        var fillSize = defaultFillSize
        while (true) {
            if (this.buffer == null) {
                return
            }
            tmpBuffer = try {
                ProtocolParseHelper.getInstance().fillBuffer(this.buffer!!, ByteArray(0), fillSize)
            } catch (e: InterruptedException) {
                e.printStackTrace()
                break
            } catch (e: IOException) {
                e.printStackTrace()
                break
            }
            if (SystemClock.uptimeMillis() - begin > 3000) {
                begin = SystemClock.uptimeMillis()
                Log.d(TAG, "receive buffer_size: " + this.buffer!!.getFillSize() + " tmpBuffer_size:" + tmpBuffer!!.size)
            }
            bytesRead = tmpBuffer!!.size
            if (remainBuf == null) {
                buffer = ByteArray(bytesRead)
                System.arraycopy(tmpBuffer, 0, buffer, 0, bytesRead)
            } else {
                buffer = ByteArray(remainBuf.size + bytesRead)
                System.arraycopy(remainBuf, 0, buffer, 0, remainBuf.size)
                System.arraycopy(tmpBuffer, 0, buffer, remainBuf.size, bytesRead)
            }
            //            String bytesReadInfo = "bytesRead: " + bytesRead
//                    + " buffer_len: " + buffer.length
//                    + " buffer[0]: " + (String.format("0x%02x", buffer[0]))
//            Log.d(TAG, bytesReadInfo)
            remainBuf = try {
                handleDebugBandWidthPocket(
                    this.buffer,
                    handleAudioPocket(
                        this.buffer,
                        handleVideoPocket(
                            this.buffer,
                            handleCodecParams(this.buffer, buffer)
                        )
                    )
                )
            } catch (e: InterruptedException) {
                e.printStackTrace()
                break
            } catch (e: IOException) {
                e.printStackTrace()
                break
            }

            remainBuf?.let {
                if (it.isNotEmpty()) {
                    //校验数据是否合法
                    if (it[0] != AVDataCommandHeader.SPS_PPS_VPS
                        && it[0] != AVDataCommandHeader.AUDIO
                        && it[0] != AVDataCommandHeader.VIDEO
                        && it[0] != AVDataCommandHeader.DEBUG_BANDWIDTH
                    ) {
                        //数据不合法,逐个(stream:逐字节,queue:逐数据)尝试找到有效数据
                        remainBuf = null
                        fillSize = 1
                    Log.e(TAG, "Central buf not available try fix buffer_size: ${this.buffer!!.getFillSize()} buffer[0]:${String.format("0x%02x", it[0])} string:${String(it)}")
                        Log.e(BtTrack.TAG, "Central buf not available try fix buffer_size: ${this.buffer!!.getFillSize()} buffer[0]:${String.format("0x%02x", it[0])} string:${String(it)}")
                    } else {
                        //数据合法,重置fillSize
                        fillSize = defaultFillSize
                    }
                }
            }

        }
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun handleCodecParams(fill: IFill?, buffer: ByteArray): ByteArray {
        val spsPpsVps = SpsPpsVps()
        val remainBuffer = ProtocolParseHelper.getInstance().fillSpsPpsVps(fill!!, buffer, spsPpsVps)
        if (spsPpsVps.sps != null) {
            rtmpManager.onSpsPpsVpsRtp(
                ByteBuffer.wrap(spsPpsVps.sps),
                ByteBuffer.wrap(spsPpsVps.pps),
                ByteBuffer.wrap(spsPpsVps.vps)
            )
            return remainBuffer
        }
        return buffer
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun handleDebugBandWidthPocket(fill: IFill?, buffer: ByteArray): ByteArray {
        return ProtocolParseHelper.getInstance().fillDebugBandWidth(fill!!, buffer)
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun handleAudioPocket(fill: IFill?, buffer: ByteArray): ByteArray {
        val audio = Audio()
        val remainBuffer = ProtocolParseHelper.getInstance().fillAudio(fill!!, buffer, audio)
        audio.data?.let {
            val info = MediaCodec.BufferInfo()
            info.presentationTimeUs = audio.presentationTimeMs * 1000
            info.size = it.size
            info.offset = 0
            rtmpManager.getAacDataRtp(ByteBuffer.wrap(it), info)

            if (it.size > 2) {
                val adtsHeader = createAdtsHeader(it.size)
                val data = ByteArray(it.size + adtsHeader.size)
                System.arraycopy(adtsHeader, 0, data, 0, adtsHeader.size)
                System.arraycopy(it, 0, data, adtsHeader.size, it.size)
                audioOutputStream?.write(data)
                audioOutputStream?.flush()
            }
            avCallback?.onAudio(it)
        }
        return remainBuffer
    }
    private val AudioSampleRates = arrayOf(
        96000,  // 0
        88200,  // 1
        64000,  // 2
        48000,  // 3
        44100,  // 4
        32000,  // 5
        24000,  // 6
        22050,  // 7
        16000,  // 8
        12000,  // 9
        11025,  // 10
        8000,  // 11
        7350,  // 12
        -1,  // 13
        -1,  // 14
        -1
    )

    private fun createAdtsHeader(length: Int): ByteArray {
        val channels = 1
        val frameLength = length + 7
        val adtsHeader = ByteArray(7)
        val sampleRateIndex = Arrays.asList<Int>(*AudioSampleRates).indexOf(44100)

        adtsHeader[0] = 0xFF.toByte() // Sync Word
        adtsHeader[1] = 0xF1.toByte() // MPEG-4, Layer (0), No CRC
        adtsHeader[2] = ((MediaCodecInfo.CodecProfileLevel.AACObjectLC - 1) shl 6).toByte()
        adtsHeader[2] = (adtsHeader[2].toInt() or (sampleRateIndex.toByte().toInt() shl 2)).toByte()
        adtsHeader[2] = (adtsHeader[2].toInt() or (channels.toByte().toInt() shr 2)).toByte()
        adtsHeader[3] = (((channels and 3) shl 6) or ((frameLength shr 11) and 0x03)).toByte()
        adtsHeader[4] = ((frameLength shr 3) and 0xFF).toByte()
        adtsHeader[5] = (((frameLength and 0x07) shl 5) or 0x1f).toByte()
        adtsHeader[6] = 0xFC.toByte()
        return adtsHeader
    }




    @Throws(IOException::class, InterruptedException::class)
    private fun handleVideoPocket(fill: IFill?, buffer: ByteArray): ByteArray {
        val video = Video()
        val remainBuffer = ProtocolParseHelper.getInstance().fillVideo(fill!!, buffer, video)
        video.data?.let {
            val info = MediaCodec.BufferInfo()
            info.presentationTimeUs = video.presentationTimeMs * 1000
            info.size = it.size
            info.offset = 0
            info.flags = java.lang.Short.toUnsignedInt(video.keyFrame.toShort())
            rtmpManager.getH264DataRtp(ByteBuffer.wrap(it), info)

            videoOutputStream?.write(it)
            videoOutputStream?.flush()

            avCallback?.onVideo(it)

            return remainBuffer
        }
        return buffer
    }

    companion object {
        private const val TAG = "AbstractReceiver"
    }
}
