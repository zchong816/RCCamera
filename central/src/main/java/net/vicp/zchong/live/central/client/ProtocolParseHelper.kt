package net.vicp.zchong.live.central.client

import android.util.Log
import net.vicp.zchong.live.central.av.Audio
import net.vicp.zchong.live.central.av.SpsPpsVps
import net.vicp.zchong.live.central.av.Video
import net.vicp.zchong.live.common.AVDataCommandHeader
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * @author zhangchong
 * @date 2024/8/14 07:05
 */
class ProtocolParseHelper private constructor() {
    companion object {
        private val TAG = "ProtocolParseHelper"
        private val instance = ProtocolParseHelper()
        fun getInstance(): ProtocolParseHelper {
            return instance
        }
    }

    private fun md5(data: ByteArray?): String {
        try {
            // 创建 MessageDigest 实例并指定算法为 MD5
            val digest = MessageDigest.getInstance("MD5")

            // 将输入字符串转换为字节数组并计算哈希值
            val hashBytes = digest.digest(data)

            // 将字节数组转换为十六进制字符串
            val hexString = StringBuilder()
            for (b: Byte in hashBytes) {
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

    @Throws(IOException::class, InterruptedException::class)
    fun fillSpsPpsVps(fill: IFill, buffer: ByteArray, spsPpsVps: SpsPpsVps): ByteArray {
        var buffer = buffer
        buffer = fillBufferIfZero(fill, buffer)
        if (buffer[0] == AVDataCommandHeader.SPS_PPS_VPS) {
            if (buffer.size < 100) {
                //buf过小，填充buf
                buffer = fillBuffer(fill, buffer, 100 - buffer.size)
            }
            val spsLen = java.lang.Byte.toUnsignedInt(buffer[1])
            val ppsLen = java.lang.Byte.toUnsignedInt(buffer[2])
            val vpsLen = java.lang.Byte.toUnsignedInt(buffer[3])
            val total = 1 + 3 + spsLen + ppsLen + vpsLen
            if (buffer.size < total) {
                //buf过小，填充buf
                buffer = fillBuffer(fill, buffer, total - buffer.size)
            }
            spsPpsVps.sps = ByteArray(spsLen)
            spsPpsVps.pps = ByteArray(ppsLen)
            spsPpsVps.vps = ByteArray(vpsLen)
            System.arraycopy(buffer, 4, spsPpsVps.sps, 0, spsLen)
            System.arraycopy(buffer, 4 + spsLen, spsPpsVps.pps, 0, ppsLen)
            System.arraycopy(buffer, 4 + spsLen + ppsLen, spsPpsVps.vps, 0, vpsLen)
            val remainBuf = ByteArray(buffer.size - total)
            System.arraycopy(buffer, total, remainBuf, 0, remainBuf.size)
            Log.d(
                TAG, "sps_pps_vps_packet"
                        + " cmd:" + String.format(
                    "0x%02x",
                    AVDataCommandHeader.SPS_PPS_VPS
                ) + " sps_md5:" + md5(spsPpsVps.sps)
                        + " pps_md5:" + md5(spsPpsVps.pps)
                        + " vps_md5:" + md5(spsPpsVps.vps)
                        + " remain_buf_len:" + remainBuf.size
                        + " remain_buf_pos[0]:" + if (remainBuf.size > 0) String.format(
                    "0x%02x",
                    remainBuf[0]
                ) else null
            )
            return remainBuf
        }
        return buffer
    }

    private fun fillVideoHeader(buffer: ByteArray, video: Video): ByteArray {
        val rawDataLenBytes = ByteArray(Integer.BYTES)
        System.arraycopy(buffer, 1, rawDataLenBytes, 1, rawDataLenBytes.size - 1)
        val rawLenBuf = ByteBuffer.wrap(rawDataLenBytes)
        val rawLen = rawLenBuf.getInt()
        val rawData = ByteArray(rawLen)
        val presentationTimeMs: Long
        val presentationTimeUsBytes = ByteArray(Integer.BYTES)
        System.arraycopy(buffer, 1 + 3, presentationTimeUsBytes, 0, presentationTimeUsBytes.size)
        val presentationTimeUsBuf = ByteBuffer.wrap(presentationTimeUsBytes)
        presentationTimeMs = Integer.toUnsignedLong(presentationTimeUsBuf.getInt())
        val keyFrame = buffer[1 + 3 + presentationTimeUsBytes.size]
        val total = 1 + 3 + presentationTimeUsBytes.size + 1 + rawData.size
        video.length = total
        video.presentationTimeMs = presentationTimeMs
        video.data = ByteArray(rawLen)
        video.keyFrame = keyFrame
        return buffer
    }

    private fun fillVideoData(buffer: ByteArray, video: Video): ByteArray {
        System.arraycopy(buffer, 1 + 3 + Integer.BYTES + 1, video.data, 0, video.data!!.size)
        val total = video.length
        val remainBuf = ByteArray(buffer.size - total)
        System.arraycopy(buffer, total, remainBuf, 0, remainBuf.size)
        if (video.presentationTimeMs / 1000 % 5 == 1L) {
            val tmpInfo = ("video_packet"
                    + " cmd:" + String.format(
                "0x%02x",
                AVDataCommandHeader.VIDEO
            ) + " keyFrame:" + video.keyFrame
                    + " rawDataLen:" + video.data!!.size
                    + " presentationTimeMs:" + video.presentationTimeMs
                    + " remain_buf_len:" + remainBuf.size
                    + " remain_buf_pos[0]:" + (if (remainBuf.size > 0) String.format(
                "0x%02x",
                remainBuf[0]
            ) else null)
                    + " md5:" + md5(video.data))
            Log.d(TAG, tmpInfo)
        }
        return remainBuf
    }

    @Throws(IOException::class, InterruptedException::class)
    fun fillVideo(fill: IFill, buffer: ByteArray, video: Video): ByteArray {
        var buffer = buffer
        buffer = fillBufferIfZero(fill, buffer)
        if (buffer[0] == AVDataCommandHeader.VIDEO) {
            if (buffer.size < 1024) {
                //buf过小，填充buf
                buffer = fillBuffer(fill, buffer, 1024 - buffer.size)
            }
            fillVideoHeader(buffer, video)
            val total = video.length
            if (buffer.size < total) {
                //buf过小，填充buf
                buffer = fillBuffer(fill, buffer, total - buffer.size)
            }
            return fillVideoData(buffer, video)
        }
        return buffer
    }

    private fun fillAudioHeader(buffer: ByteArray, audio: Audio): ByteArray {
        val rawDataLenBytes = ByteArray(java.lang.Short.BYTES)
        System.arraycopy(buffer, 1, rawDataLenBytes, 0, rawDataLenBytes.size)
        val rawLenBuf = ByteBuffer.wrap(rawDataLenBytes)
        val rawLen = java.lang.Short.toUnsignedInt(rawLenBuf.getShort())
        val presentationTimeMs: Long
        val presentationTimeUsBytes = ByteArray(Integer.BYTES)
        System.arraycopy(
            buffer,
            1 + rawDataLenBytes.size,
            presentationTimeUsBytes,
            0,
            presentationTimeUsBytes.size
        )
        val presentationTimeUsBuf = ByteBuffer.wrap(presentationTimeUsBytes)
        presentationTimeMs = Integer.toUnsignedLong(presentationTimeUsBuf.getInt())
        val total = 1 + java.lang.Short.BYTES + Integer.BYTES + rawLen
        audio.length = total
        audio.presentationTimeMs = presentationTimeMs
        audio.data = ByteArray(rawLen)
        return buffer
    }

    private fun fillAudioData(buffer: ByteArray, audio: Audio): ByteArray {
        System.arraycopy(
            buffer,
            1 + java.lang.Short.BYTES + Integer.BYTES,
            audio.data,
            0,
            audio.data!!.size
        )
        val total = audio.length
        val remainBuf = ByteArray(buffer.size - total)
        System.arraycopy(buffer, total, remainBuf, 0, remainBuf.size)
        if (audio.presentationTimeMs / 1000 % 5 == 1L) {
            val tmpInfo = ("audio_packet"
                    + " cmd:" + String.format(
                "0x%02x",
                AVDataCommandHeader.AUDIO
            ) + " rawDataLen:" + audio.data!!.size
                    + " presentationTimeMs:" + audio.presentationTimeMs
                    + " remain_buf_len:" + remainBuf.size
                    + " remain_buf_pos[0]:" + (if (remainBuf.size > 0) String.format(
                "0x%02x",
                remainBuf[0]
            ) else null)
                    + " md5:" + md5(audio.data))
            Log.d(TAG, tmpInfo)
        }
        return remainBuf
    }

    @Throws(IOException::class, InterruptedException::class)
    fun fillDebugBandWidth(fill: IFill, buffer: ByteArray): ByteArray {
        var buffer = buffer
        buffer = fillBufferIfZero(fill, buffer)
        if (buffer[0] == AVDataCommandHeader.DEBUG_BANDWIDTH) {
            if (buffer[buffer.size - 1] == AVDataCommandHeader.DEBUG_BANDWIDTH) {
                return ByteArray(0)
            }
        }
        return buffer
    }

    @Throws(IOException::class, InterruptedException::class)
    fun fillAudio(fill: IFill, buffer: ByteArray, audio: Audio): ByteArray {
        var buffer = buffer
        buffer = fillBufferIfZero(fill, buffer)
        if (buffer[0] == AVDataCommandHeader.AUDIO) {
            if (buffer.size < 100) {
                //buf过小，填充buf
                buffer = fillBuffer(fill, buffer, 100 - buffer.size)
            }
            fillAudioHeader(buffer, audio)
            val total = audio.length
            if (buffer.size < total) {
                //buf过小，填充buf
                buffer = fillBuffer(fill, buffer, total - buffer.size)
            }
            return fillAudioData(buffer, audio)
        }
        return buffer
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun fillBufferIfZero(fill: IFill, buffer: ByteArray): ByteArray {
        return if (buffer.size > 0) {
            buffer
        } else fillBuffer(fill, buffer, 1024)
    }

    @Throws(IOException::class, InterruptedException::class)
    public fun fillBuffer(fill: IFill, buffer: ByteArray, size: Int): ByteArray {
        var buffer = buffer
        var size = size
        val len = buffer.size
        if (size <= 0) {
            size = 1024
        }
        var totalRead = 0
        var bytesRead: Int
        while (totalRead < size) {
            var tmpBuffer: ByteArray? = null
            tmpBuffer = fill.fill(size - totalRead)
            bytesRead = tmpBuffer!!.size
            totalRead += bytesRead
            sum += bytesRead.toLong()
            val t = ByteArray(buffer.size + bytesRead)
            System.arraycopy(buffer, 0, t, 0, buffer.size)
            System.arraycopy(tmpBuffer, 0, t, buffer.size, bytesRead)
            buffer = ByteArray(t.size)
            System.arraycopy(t, 0, buffer, 0, t.size)
        }
        Log.d(
            TAG,
            "fillBuffer before_fill_buffer_len:" + len + " after_fill_buffer_len:" + buffer.size + " size:" + size + " totalRead:" + totalRead
        )
        handleTransmissionSpeed()
        return buffer
    }

    var sumTime: Long = 0
    var sumBytes: Long = 0
    var sum: Long = 0
    var beinTs: Long = 0
    var iTransmissionSpeed: ITransmissionSpeed? = null
    fun setiTransmissionSpeed(iTransmissionSpeed: ITransmissionSpeed?) {
        this.iTransmissionSpeed = iTransmissionSpeed
    }

    fun handleTransmissionSpeed() {
        if (beinTs == 0L) {
            beinTs = System.currentTimeMillis()
        }
        val deltaTime = System.currentTimeMillis() - beinTs
        if (deltaTime > 3000) {
            sumTime += deltaTime
            sumBytes += sum
            val avgSpeed = Math.round(sumBytes * 1.0f / sumTime * 1000.0f / 1000.0f)
            val speed = Math.round(sum * 1.0f / deltaTime * 1000.0f / 1000.0f)
            val info = ("deltaBytes:$sumBytes deltaTime:$deltaTime" +
                    (("\nsumBytes:" + ((sumBytes / 1000).toString() + "KB")
                            + "\nsumTime:" + (sumTime / 1000) + "S"
                            + "\navgSpeed:" + avgSpeed + "KB/S" + " " + (avgSpeed * 8) + "kbps")))
            val speedInfo = "speed:" + speed + "KB/S" + " " + (speed * 8) + "kbps"
            Log.d(
                TAG, (info + "\n" + speedInfo
                        + ((" sumBytes:" + ((sumBytes / 1000).toString() + "KB")
                        + " sumTime:" + (sumTime / 1000) + "S")))
            )
            if (iTransmissionSpeed != null) {
                iTransmissionSpeed!!.callback(speed, deltaTime, avgSpeed, sumTime, sumBytes)
            }
            sum = 0
            beinTs = System.currentTimeMillis()
        }
    }
}