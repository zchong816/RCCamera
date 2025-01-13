package net.vicp.zchong.live.central.av

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

/**
 * @author zhangchong
 * @date 2024/9/9 11:39
 */
class AacDeCodePlayFromReceiver(var sample: Int, var channelCount: Int, var csd0: ByteArray) {
    //使用android MediaCodec解码
    private var mediaCodec: MediaCodec? = null

    private val queue: BlockingQueue<ByteArray> = LinkedBlockingQueue()

    var mIsPalying: Boolean = false

    private var audioTrack: AudioTrack? = null

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

    private fun initMediaCodec() {
        try {
            mediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            val mediaFormat =
                MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sample, channelCount)
            val csd0Buffer = ByteBuffer.wrap(csd0)
            mediaFormat.setByteBuffer("csd-0", csd0Buffer)
            mediaFormat.setInteger(
                MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC
            )
            mediaCodec!!.configure(mediaFormat, null, null, 0)
        } catch (e: IOException) {
            e.printStackTrace()
            //创建解码失败
            Log.e(TAG, "创建解码失败")
        }
    }

    fun initAudioTrack() {
        val streamType = AudioManager.STREAM_MUSIC
        val channelConfig =
            (if (channelCount == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO)
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val mode = AudioTrack.MODE_STREAM
        val minBufferSize = AudioTrack.getMinBufferSize(sample, channelConfig, audioFormat)
        audioTrack = AudioTrack(
            streamType, sample, channelConfig, audioFormat,
            minBufferSize, mode
        )
        audioTrack!!.play()
    }


    var thread: Thread = Thread(MyRun())

    init {
        initMediaCodec()
    }

    private inner class MyRun : Runnable {
        override fun run() {
            try {
                val isFinish = false
                mIsPalying = true
                while (!isFinish && mIsPalying) {
                    var data: ByteArray? = null
                    try {
                        data = queue.take()
                    } catch (e: InterruptedException) {
                        throw RuntimeException(e)
                    }
                    val info = MediaCodec.BufferInfo()
                    // 查询10000毫秒后，如果dSP芯片的buffer全部被占用，返回-1；存在则大于等于0
                    val inIndex = mediaCodec!!.dequeueInputBuffer(10000)
                    if (inIndex >= 0) {
                        //根据返回的index拿到可以用的buffer
                        val byteBuffer = mediaCodec!!.getInputBuffer(inIndex)
                        if (byteBuffer != null) {
                            //清空缓存
                            byteBuffer.clear()
                            //开始为buffer填充数据
                            byteBuffer.put(data, 0, data.size)
                            //填充数据后通知mediacodec查询inIndex索引的这个buffer,
                            mediaCodec!!.queueInputBuffer(inIndex, 0, data.size, 0, 0)
                        }
                    } else {
                        //等待查询空的buffer
                        continue
                    }

                    var outputIndex =
                        mediaCodec!!.dequeueOutputBuffer(info, 10000) //获取解码得到的byte[]数据

                    var outputBuffer: ByteBuffer?
                    var chunkPCM: ByteArray
                    //每次解码完成的数据不一定能一次吐出 所以用while循环，保证解码器吐出所有数据
                    while (outputIndex >= 0) {
//                        Log.d(TAG, "outputIndex:$outputIndex")
                        outputBuffer = mediaCodec!!.getOutputBuffer(outputIndex)
                        chunkPCM = ByteArray(info.size)
                        outputBuffer!![chunkPCM]
                        outputBuffer.clear() //数据取出后一定记得清空此Buffer MediaCodec是循环使用这些Buffer的，不清空下次会得到同样的数
                        // 播放解码后的PCM数据
                        audioTrack!!.write(chunkPCM, 0, info.size)
                        mediaCodec!!.releaseOutputBuffer(outputIndex, false)
                        outputIndex = mediaCodec!!.dequeueOutputBuffer(info, 10000) //再次获取数据
                    }
                }
                stop()
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    fun decodeAndPlay() {
        mediaCodec!!.start()
        thread.start()
    }


    fun stop() {
        try {
            mediaCodec!!.stop()
            audioTrack!!.stop()
            mIsPalying = false
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }


    fun pushData(data: ByteArray) {
        try {
            queue.put(data)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val TAG = "AacDeCodePlayFromReceiver"
    }
}
