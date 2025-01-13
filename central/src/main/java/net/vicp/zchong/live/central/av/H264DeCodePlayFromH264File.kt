package net.vicp.zchong.live.central.av

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

/**
 * @author zhangchong
 * @date 2024/9/9 11:19
 */
class H264DeCodePlayFromH264File(
    private val videoPath: String, private val surface: Surface
) {
    //使用android MediaCodec解码
    private var mediaCodec: MediaCodec? = null

    init {
        initMediaCodec()
    }

    private fun initMediaCodec() {
        try {
            Log.e(TAG, "videoPath $videoPath")
            //创建解码器 H264的Type为  AAC
            mediaCodec = MediaCodec.createDecoderByType("video/avc")
            //创建配置
            val mediaFormat = MediaFormat.createVideoFormat("video/avc", 540, 960)
            //设置解码预期的帧速率【以帧/秒为单位的视频格式的帧速率的键】
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 15)
            //配置绑定mediaFormat和surface
            mediaCodec!!.configure(mediaFormat, surface, null, 0)
        } catch (e: IOException) {
            e.printStackTrace()
            //创建解码失败
            Log.e(TAG, "创建解码失败")
        }
    }

    /**
     * 解码播放
     */
    fun decodePlay() {
        mediaCodec!!.start()
        Thread(MyRun()).start()
    }

    private inner class MyRun : Runnable {
        override fun run() {
            try {
                //1、IO流方式读取h264文件【太大的视频分批加载】
                var bytes: ByteArray? = null
                bytes = getBytes(videoPath)
                Log.e(TAG, "bytes size " + bytes.size)
                //2、拿到 mediaCodec 所有队列buffer[]
                val inputBuffers = mediaCodec!!.inputBuffers
                //开始位置
                var startIndex = 0
                //h264总字节数
                val totalSize = bytes.size
                //3、解析
                while (true) {
                    //判断是否符合
                    if (totalSize == 0 || startIndex >= totalSize) {
                        break
                    }
                    //寻找索引
                    val nextFrameStart = findByFrame(bytes, startIndex + 1, totalSize)
                    if (nextFrameStart == -1) break
                    val info = MediaCodec.BufferInfo()
                    // 查询10000毫秒后，如果dSP芯片的buffer全部被占用，返回-1；存在则大于0
                    val inIndex = mediaCodec!!.dequeueInputBuffer(10000)
                    if (inIndex >= 0) {
                        //根据返回的index拿到可以用的buffer
                        val byteBuffer = inputBuffers[inIndex]
                        //清空缓存
                        byteBuffer.clear()
                        //开始为buffer填充数据
                        byteBuffer.put(bytes, startIndex, nextFrameStart - startIndex)
                        //填充数据后通知mediacodec查询inIndex索引的这个buffer,
                        mediaCodec!!.queueInputBuffer(inIndex, 0, nextFrameStart - startIndex, 0, 0)
                        //为下一帧做准备，下一帧首就是前一帧的尾。
                        startIndex = nextFrameStart
                    } else {
                        //等待查询空的buffer
                        continue
                    }
                    //mediaCodec 查询 "mediaCodec的输出方队列"得到索引
                    val outIndex = mediaCodec!!.dequeueOutputBuffer(info, 10000)
//                    Log.e(TAG, "outIndex $outIndex")
                    if (outIndex >= 0) {
                        try {
                            //暂时以休眠线程方式放慢播放速度
                            Thread.sleep(33)
                        } catch (e: InterruptedException) {
                            e.printStackTrace()
                        }
                        //如果surface绑定了，则直接输入到surface渲染并释放
                        mediaCodec!!.releaseOutputBuffer(outIndex, true)
                    } else {
                        Log.e(TAG, "没有解码成功")
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    //读取一帧数据
    private fun findByFrame(bytes: ByteArray?, start: Int, totalSize: Int): Int {
        for (i in start until totalSize - 4) {
            //对output.h264文件分析 可通过分隔符 0x00000001 读取真正的数据
            if (bytes!![i].toInt() == 0x00 && bytes[i + 1].toInt() == 0x00 && bytes[i + 2].toInt() == 0x00 && bytes[i + 3].toInt() == 0x01) {
                return i
            }
        }
        return -1
    }

    @Throws(IOException::class)
    private fun getBytes(videoPath: String): ByteArray {
        val `is`: InputStream = DataInputStream(FileInputStream(File(videoPath)))
        var len: Int
        val size = 1024
        val bos = ByteArrayOutputStream()
        var buf = ByteArray(size)
        while ((`is`.read(buf, 0, size).also { len = it }) != -1) bos.write(buf, 0, len)
        buf = bos.toByteArray()
        return buf
    }

    companion object {
        private const val TAG = "H264DeCodePlayFromH264File"
    }
}
