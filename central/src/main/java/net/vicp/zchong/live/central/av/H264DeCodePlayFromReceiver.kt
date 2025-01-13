package net.vicp.zchong.live.central.av

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import java.io.IOException
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

/**
 * @author zhangchong
 * @date 2024/9/9 11:00
 */
class H264DeCodePlayFromReceiver(private val surface: Surface,private val width:Int,private val height:Int,private val fps:Int) {
    //使用android MediaCodec解码
    private var mediaCodec: MediaCodec? = null

    private val queue: BlockingQueue<ByteArray> = LinkedBlockingQueue()

    private fun initMediaCodec() {
        try {
            mediaCodec = MediaCodec.createDecoderByType("video/avc")
            val mediaFormat =
                MediaFormat.createVideoFormat("video/avc", width, height)
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            mediaCodec!!.configure(mediaFormat, surface, null, 0)
        } catch (e: IOException) {
            e.printStackTrace()
            //创建解码失败
            Log.e(TAG, "创建解码失败")
        }
    }


    var thread: Thread = Thread(MyRun())

    init {
        initMediaCodec()
    }

    /**
     * 解码播放
     */
    fun decodeAndPlay() {
        mediaCodec!!.start()
        thread.start()
    }

    fun stop() {
        try {
            mediaCodec!!.stop()
            thread.interrupt()
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }


    private inner class MyRun : Runnable {
        val useSkipFrame = false
        var prevRenderMs = SystemClock.uptimeMillis()
        override fun run() {
            try {
                val inputBuffers = mediaCodec!!.inputBuffers
                while (true) {
                    var data: ByteArray? = null
                    try {
                        data = queue.take()
                    } catch (e: InterruptedException) {
                        throw RuntimeException(e)
                    }

                    if (data == null) {
                        break
                    }

                    val info = MediaCodec.BufferInfo()
                    // 查询10000毫秒后，如果DSP芯片的buffer全部被占用，返回-1；存在则大于0
                    val inIndex = mediaCodec!!.dequeueInputBuffer(10000)
                    if (inIndex >= 0) {
                        //根据返回的index拿到可以用的buffer
                        val byteBuffer = inputBuffers[inIndex]
                        //清空缓存
                        byteBuffer.clear()
                        //开始为buffer填充数据
                        byteBuffer.put(data, 0, data.size)
                        //填充数据后通知mediacodec查询inIndex索引的这个buffer,
                        mediaCodec!!.queueInputBuffer(inIndex, 0, data.size, 0, 0)
                    } else {
                        //等待查询空的buffer
                        continue
                    }
                    //mediaCodec 查询 "mediaCodec的输出方队列"得到索引
                    val outIndex = mediaCodec!!.dequeueOutputBuffer(info, 10000)
//                    Log.e(TAG, "outIndex $outIndex")
                    if (outIndex >= 0) {
                        if(useSkipFrame) {
                            val fpsInterval = 1000 / fps
                            val currentMs = SystemClock.uptimeMillis()
                            val deltaMs = currentMs - prevRenderMs
                            prevRenderMs = currentMs
                            if (deltaMs <= fpsInterval) {
                                try {
                                    val sleep = fpsInterval - deltaMs
                                    Log.d(
                                        TAG,
                                        "render sleep: $sleep deltaMs:$deltaMs fpsInterval:$fpsInterval"
                                    )
                                    Thread.sleep(sleep)
                                } catch (e: InterruptedException) {
                                    e.printStackTrace()
                                }
                                //如果surface绑定了，则直接输入到surface渲染并释放
                                mediaCodec!!.releaseOutputBuffer(outIndex, true)
                            } else {
                                Log.e(
                                    TAG,
                                    "render skip frame deltaMs:$deltaMs fpsInterval:$fpsInterval"
                                )
                                mediaCodec!!.releaseOutputBuffer(outIndex, false)
                            }
                        } else {
                            mediaCodec!!.releaseOutputBuffer(outIndex, true)
                        }
                    } else {
                        Log.e(TAG, "没有解码成功")
                    }
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
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
        private const val TAG = "H264DeCodePlayFromReceiver"
    }
}
