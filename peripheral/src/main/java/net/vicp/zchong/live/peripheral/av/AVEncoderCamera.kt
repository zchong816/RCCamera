package net.vicp.zchong.live.peripheral.av

import android.media.MediaCodec
import android.util.Log
import android.view.SurfaceView
import com.pedro.common.AudioCodec
import com.pedro.common.VideoCodec
import com.pedro.common.isKeyframe
import com.pedro.common.removeInfo
import com.pedro.encoder.input.video.CameraHelper.Facing
import com.pedro.library.base.Camera1PreviewSyncVideo
import com.pedro.library.util.streamclient.StreamBaseClient
import net.vicp.zchong.live.common.AVDataCommandHeader
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * @author zhangchong
 * @date 2024/8/12 09:37
 */
class AVEncoderCamera(surfaceView: SurfaceView?) : Camera1PreviewSyncVideo(surfaceView) {
    private var avCodecCallback: IAVCodecCallback? = null
    private var spsBytes: ByteArray? = null
    private var ppsBytes: ByteArray? = null
    private var vpsBytes: ByteArray? = null
    fun setAvCodecCallback(avCodecCallback: IAVCodecCallback?) {
        this.avCodecCallback = avCodecCallback
    }

    override fun startPreview(cameraFacing: Facing) {
        Log.d(TAG, "startPreview cameraFacing:$cameraFacing")
        super.startPreview(cameraFacing)
    }

    override fun prepareAudioRtp(isStereo: Boolean, sampleRate: Int) {
        Log.d(TAG, "prepareAudioRtp isStereo:$isStereo sampleRate:$sampleRate")
        //todo send prepare audio
    }

    override fun startStreamRtp(url: String) {
        Log.d(TAG, "startStreamRtp url:$url")
        //todo send start stream
    }

    override fun stopStreamRtp() {
        Log.d(TAG, "stopStreamRtp")
        //todo send stop stream
    }

    override fun getAacDataRtp(byteBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        //encoder audio线程产生
//        Log.d(TAG, "getAacDataRtp hasRemaining:" + byteBuffer.hasRemaining() + " " + byteBuffer + " " + info.size + " " + info.offset + " " + info.presentationTimeUs + " " + info.flags)
        try {
            setSendRawData(byteBuffer, info, true)
        } catch (t: Throwable) {
            Log.d(TAG, "getAacDataRtp out e:$t")
            t.printStackTrace()
        }
    }

    override fun getH264DataRtp(byteBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        //encoder video线程产生
//        Log.d(TAG, "getH264DataRtp: " + byteBuffer + " " + info.size + " " + info.offset + " " + info.presentationTimeUs + " " + info.flags)
        try {
            setSendRawData(byteBuffer, info, false)
        } catch (t: Throwable) {
            Log.d(TAG, "getH264DataRtp out e:$t")
            t.printStackTrace()
        }
    }

    private fun removeHeader(byteBuffer: ByteBuffer, size: Int = -1): ByteBuffer {
        val position = if (size == -1) getStartCodeSize(byteBuffer) else size
        byteBuffer.position(position)
        return byteBuffer.slice()
    }

    private fun getStartCodeSize(byteBuffer: ByteBuffer): Int {
        var startCodeSize = 0
        if (byteBuffer[0].toInt() == 0x00 && byteBuffer[1].toInt() == 0x00 && byteBuffer[2].toInt() == 0x00 && byteBuffer[3].toInt() == 0x01) {
            //match 00 00 00 01
            startCodeSize = 4
        } else if (byteBuffer[0].toInt() == 0x00 && byteBuffer[1].toInt() == 0x00 && byteBuffer[2].toInt() == 0x01) {
            //match 00 00 01
            startCodeSize = 3
        }
        return startCodeSize
    }

    protected fun updateSpsPpsVps(sps: ByteBuffer?, pps: ByteBuffer?, vps: ByteBuffer?) {
        val mSps = sps?.let { removeHeader(it) }
        val mPps = pps?.let { removeHeader(it) }
        val mVps = vps?.let { removeHeader(it) }
        spsBytes = ByteArray(mSps?.slice()?.remaining() ?: 0)
        ppsBytes = ByteArray(mPps?.slice()?.remaining() ?: 0)
        vpsBytes = ByteArray(mVps?.slice()?.remaining() ?: 0)
        mSps?.get(spsBytes, 0, spsBytes!!.size)
        mPps?.get(ppsBytes, 0, ppsBytes!!.size)
        mVps?.get(vpsBytes, 0, vpsBytes!!.size)
    }

    private fun setSendSpsPpsVpsData() {
        if (spsBytes != null && ppsBytes != null && vpsBytes != null) {
            //协议
            //[0] 0x87:编码参数
            //[1] sps bytes length
            //[2] pps bytes length
            //[3] vps bytes length  h265使用
            //[sps bytes] sps 数据
            //[pps bytes] pps 数据
            //[vps bytes] vps 数据 h265使用
            //0x87 sps_len pps_len vps_len sps_bytes pps_bytes vps_bytes
            val sendDataBytes =
                ByteArray(1 + 3 + spsBytes!!.size + ppsBytes!!.size + vpsBytes!!.size)
            sendDataBytes[0] = AVDataCommandHeader.SPS_PPS_VPS
            sendDataBytes[1] = spsBytes!!.size.toByte()
            sendDataBytes[2] = ppsBytes!!.size.toByte()
            sendDataBytes[3] = vpsBytes!!.size.toByte()
            System.arraycopy(spsBytes, 0, sendDataBytes, 4, spsBytes!!.size)
            System.arraycopy(ppsBytes, 0, sendDataBytes, 4 + spsBytes!!.size, ppsBytes!!.size)
            System.arraycopy(
                vpsBytes,
                0,
                sendDataBytes,
                4 + spsBytes!!.size + ppsBytes!!.size,
                vpsBytes!!.size
            )
            Log.d(
                TAG, "sps_pps_vps_packet sps_md5:" + md5(
                    spsBytes!!
                ) + " pps_md5:" + md5(ppsBytes!!) + " vps_md5:" + md5(vpsBytes!!)
                        + " sps_len:" + spsBytes!!.size + " pps_len:" + ppsBytes!!.size + " vps_len:" + vpsBytes!!.size
            )
            if (avCodecCallback != null) {
                avCodecCallback!!.onGetSpsPpsVps(sendDataBytes)
            }
        }
    }

    override fun onSpsPpsVpsRtp(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer?) {
        //encoder video线程产生
        Log.d(TAG, "onSpsPpsVpsRtp: $sps $pps $vps")
        updateSpsPpsVps(sps, pps, vps)
        setSendSpsPpsVpsData()
    }

    override fun getStreamClient(): StreamBaseClient? {
        return null
    }

    override fun setVideoCodecImp(codec: VideoCodec) {
        Log.d(TAG, "setVideoCodecImp: $codec")
        //todo send video codec
    }

    override fun setAudioCodecImp(codec: AudioCodec) {
        Log.d(TAG, "setAudioCodecImp: $codec")
        //todo send audio codec
    }

    private fun md5(data: ByteArray): String {
        try {
            // 创建 MessageDigest 实例并指定算法为 MD5
            val digest = MessageDigest.getInstance("MD5")

            // 将输入字符串转换为字节数组并计算哈希值
            val hashBytes = digest.digest(data)

            // 将字节数组转换为十六进制字符串
            val hexString = StringBuilder()
            for (b in hashBytes) {
                val hex = Integer.toHexString(0xff and b.toInt())
                if (hex.length == 1) {
                    hexString.append('0')
                }
                hexString.append(hex)
            }
            return hexString.toString()
        } catch (e: NoSuchAlgorithmException) {
            Log.e(TAG, "MD5 algorithm not found.")
        }
        return ""
    }

    private fun setSendRawData(
        byteBuffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        isAudio: Boolean
    ) {
        val fixedBuffer = byteBuffer.removeInfo(info)
        val rawDataBytes = ByteArray(fixedBuffer.remaining())
        fixedBuffer[rawDataBytes]
        var presentationTimeMs = info.presentationTimeUs / 1000L
        presentationTimeMs = presentationTimeMs % 0xffffffffL
        val buffer = ByteBuffer.allocate(java.lang.Long.BYTES)
        buffer.putLong(presentationTimeMs)
        val presentationTimeMsBytes = buffer.array()
        val sendDataBytes: ByteArray
        if (isAudio) {
            // 协议
            // [0] 0x88:音频
            // [1-2]  int 包rawData size int
            // [3-6] int MediaCodec.BufferInfo presentationTimeMs
            // [6-rawData.len] audioData
            val sumBuffer = ByteBuffer.allocate(java.lang.Short.BYTES)
            val rawDataSize = rawDataBytes.size.toShort()
            sumBuffer.putShort(rawDataSize)
            val sumBytes = sumBuffer.array()
            sendDataBytes = ByteArray(1 + sumBytes.size + 4 + rawDataSize)
            sendDataBytes[0] = AVDataCommandHeader.AUDIO
            System.arraycopy(sumBytes, 0, sendDataBytes, 1, sumBytes.size)
            System.arraycopy(presentationTimeMsBytes, 4, sendDataBytes, 1 + sumBytes.size, 4)
            System.arraycopy(
                rawDataBytes,
                0,
                sendDataBytes,
                1 + sumBytes.size + 4,
                rawDataBytes.size
            )
            if (presentationTimeMs / 1000 % 5 == 1L) {
                Log.d(
                    TAG, "audio_packet"
                            + " rawDataLen:" + rawDataSize
                            + " sendDataBytesLen:" + sendDataBytes.size
                            + " presentationTimeMs:" + presentationTimeMs
                            + " pos[0]:" + String.format("0x%02x", sendDataBytes[0])
                            + " md5:" + md5(rawDataBytes)
                            + " rawData[0,1]:" + String.format(
                        "0x%02x 0x%02x",
                        rawDataBytes[0],
                        rawDataBytes[1]
                    )
                )
            }
        } else {
            //协议
            // [0] 0x89:视频
            // [1-3]  int 包rawData size int
            // [4-7] int MediaCodec.BufferInfo presentationTimeMs
            // [8] int isKeyFrame
            // [9-rawData.len] videoData
            val sumBuffer = ByteBuffer.allocate(Integer.BYTES)
            val rawDataSize = rawDataBytes.size
            sumBuffer.putInt(rawDataSize)
            val sumBytes = sumBuffer.array()
            sendDataBytes = ByteArray(1 + 3 + 4 + 1 + rawDataSize)
            sendDataBytes[0] = AVDataCommandHeader.VIDEO
            System.arraycopy(sumBytes, 1, sendDataBytes, 1, 3)
            System.arraycopy(presentationTimeMsBytes, 4, sendDataBytes, 1 + 3, 4)
            val isKeyframe = info.isKeyframe()
            val keyFrame = if (isKeyframe) 1.toByte() else 0.toByte()
            sendDataBytes[1 + 3 + 4] = keyFrame
            System.arraycopy(rawDataBytes, 0, sendDataBytes, 1 + 3 + 4 + 1, rawDataBytes.size)
            if (presentationTimeMs / 1000 % 5 == 1L) {
                Log.d(
                    TAG, "video_packet"
                            + " rawDataLen:" + rawDataSize
                            + " sendDataBytesLen:" + sendDataBytes.size
                            + " presentationTimeMs:" + presentationTimeMs
                            + " pos[0]:" + String.format("0x%02x", sendDataBytes[0])
                            + " md5:" + md5(rawDataBytes)
                            + " rawData[0,1]:" + String.format(
                        "0x%02x 0x%02x",
                        rawDataBytes[0],
                        rawDataBytes[1]
                    )
                            + " keyFrame:" + keyFrame
                )
            }
        }
        if (avCodecCallback != null) {
            avCodecCallback!!.onGetAVData(sendDataBytes, isAudio)
        }
    }

    companion object {
        private const val TAG = "AVEncoderCamera"
    }
}
