package net.vicp.zchong.live.peripheral

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.media.MediaRecorder
import android.os.Bundle
import android.os.IBinder
import android.text.TextUtils
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.pedro.encoder.IPreviewFrameFpsCallback
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.encoder.utils.CodecUtil
import net.vicp.zchong.live.common.BtTrack
import net.vicp.zchong.live.peripheral.av.AVEncoderCamera
import net.vicp.zchong.live.peripheral.av.IAVCodecCallback
import net.vicp.zchong.live.peripheral.sender.ISenderCallback
import net.vicp.zchong.live.peripheral.server.BlueToothPeripheralService
import net.vicp.zchong.live.peripheral.server.BlueToothPeripheralService.PeripheralBinder
import org.json.JSONObject

/**
 * @author zhangchong
 * @date 2024/8/16 15:30
 */
class CameraActivity : AppCompatActivity(), SurfaceHolder.Callback, IPreviewFrameFpsCallback,
    IAVCodecCallback {
    private var blueToothPeripheralService: BlueToothPeripheralService? = null
    private var isBind = false
    private var abInfo: TextView? = null
    private var vbInfo: TextView? = null
    private var writeBuffer: TextView? = null
    private var upload: TextView? = null
    private var bytes: TextView? = null
    private var buffer: TextView? = null
    private var serverInfo: TextView? = null
    private var previewFps: TextView? = null
    private var camera: AVEncoderCamera? = null
    private var surfaceView: SurfaceView? = null
    private val config = JSONObject()

    private val senderCallback = object : ISenderCallback {
        override fun onUploadBandwidthChange(kbps: Int, totalSize: Long) {
            runOnUiThread {
                upload?.text = "upload: $kbps kbps"
                bytes?.text = "bytes: ${totalSize / 1000} KB"
            }
        }

        override fun onBufferWriteBandwidthChange(kbps: Int) {
            runOnUiThread {
                writeBuffer?.text = "write buffer: $kbps kbps"
            }
        }

        override fun onBufferPercentChange(bufferPercent: Int) {
            runOnUiThread {
                buffer?.text =
                    "buffer: ${(if (bufferPercent >= 0) ("$bufferPercent %") else "--")}"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_remote_camera)
        init()
    }

    override fun onBackPressed() {
        Log.d(TAG, "onBackPressed")
        super.onBackPressed()
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        Log.d(TAG, "onNewIntent intent: $intent")
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        stopStreaming()
        stopPreview()
    }

    override fun onStart() {
        Log.d(TAG, "onStart")
        super.onStart()
        val intent = Intent(this, BlueToothPeripheralService::class.java)
        intent.setData(getIntent().data)
        application.bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }

    override fun onStop() {
        Log.d(TAG, "onStop")
        super.onStop()
        if (isBind) {
            application.unbindService(serviceConnection)
            isBind = false
        }
    }

    override fun onResume() {
        Log.d(TAG, "onResume")
        super.onResume()
    }

    override fun onFps(fps: Float) {
        if (previewFps != null) {
            runOnUiThread {
                previewFps!!.text = "fps : " + String.format("%.2f", fps)
            }
        }
    }

    override fun surfaceCreated(surfaceHolder: SurfaceHolder) {
        Log.d(TAG, "surfaceCreated $surfaceHolder")
    }

    override fun surfaceChanged(surfaceHolder: SurfaceHolder, i: Int, i1: Int, i2: Int) {
        Log.d(TAG, "surfaceChanged $surfaceHolder $i $i1 $i2")
        handleIntent(intent)
    }

    override fun surfaceDestroyed(surfaceHolder: SurfaceHolder) {
        Log.d(TAG, "surfaceDestroyed $surfaceHolder")
    }

    private fun stopStreaming() {
        if (camera != null && camera!!.isStreaming) {
            camera!!.stopStream()
        }
    }

    private fun stopPreview() {
        if (camera != null) {
            camera!!.stopPreview()
        }
    }

    var abTs: Long = 0
    var deltaAbSum: Long = 0
    var vbTs: Long = 0
    var deltaVbSum: Long = 0
    override fun onGetAVData(data: ByteArray, isAudio: Boolean) {
        if (isAudio) {
            if (abTs == 0L) {
                abTs = System.currentTimeMillis()
            }
            deltaAbSum += data.size.toLong()
            val delta = System.currentTimeMillis() - abTs
            if (delta > 3000) {
                runOnUiThread {
                    abInfo!!.text =
                        "audio: " + Math.round(deltaAbSum * 1.0f / delta * 1000.0f / 1000.0f * 8) + " kbps"
                    abTs = System.currentTimeMillis()
                    deltaAbSum = 0
                }
            }
        } else {
            if (vbTs == 0L) {
                vbTs = System.currentTimeMillis()
            }
            deltaVbSum += data.size.toLong()
            val delta = System.currentTimeMillis() - vbTs
            if (delta > 3000) {
                runOnUiThread {
                    vbInfo!!.text =
                        "video: " + Math.round(deltaVbSum * 1.0 / delta * 1000.0f / 1000.0f * 8) + " kbps"
                    vbTs = System.currentTimeMillis()
                    deltaVbSum = 0
                }
            }
        }
        if (blueToothPeripheralService != null) {
            blueToothPeripheralService!!.onGetAVData(data, isAudio)
        }
    }

    override fun onGetSpsPpsVps(data: ByteArray) {
        if (blueToothPeripheralService != null) {
            blueToothPeripheralService!!.onGetSpsPpsVps(data)
        }
    }

    private fun allCodecs() {
        try {
            val allCodecsInfo = CodecUtil.showAllCodecsInfo()
            if (allCodecsInfo != null) {
                for (info in allCodecsInfo) {
                    Log.d(TAG, "codecs:$info")
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private fun init() {
        setContentView(R.layout.activity_remote_camera)
        surfaceView = findViewById(R.id.surfaceView)
        abInfo = findViewById(R.id.live_info)
        vbInfo = findViewById(R.id.ext_info)
        buffer = findViewById(R.id.buffer)
        writeBuffer = findViewById(R.id.write_buffer_kbps)
        upload = findViewById(R.id.upload_kbps)
        bytes = findViewById(R.id.bytes)
        serverInfo = findViewById(R.id.server_info)
        camera = AVEncoderCamera(surfaceView)
        camera!!.setiPreviewFrameFpsCallback(this)
        surfaceView = findViewById(R.id.surfaceView)
        surfaceView!!.holder.addCallback(this)
        previewFps = findViewById(R.id.preview_fps)
        allCodecs()
    }

    private fun handleIntent(intent: Intent?) {
        Log.d(TAG, "handleIntent " + intent!!.data)
        if (intent != null && intent.data != null) {
            Log.d(
                TAG, "handleIntent path:" + intent.data!!
                    .path
            )



            when (intent.data!!.path) {
                "/" -> {}
                "/debug_bandwidth" -> {}
                "/connect" -> {}
                "/disconnect" -> {
                    finish()
                }

                "/stop" -> {}
                "/pause" -> {}

                "/resume" -> {
                }

                "/start",
                "/camera" -> {
                    //视频宽
                    try {
                        val tmp = intent.data!!.getQueryParameter("w")
                        if (!TextUtils.isEmpty(tmp)) {
                            config.put("w", tmp!!.toInt())
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }

                    //视频高
                    try {
                        val tmp = intent.data!!.getQueryParameter("h")
                        if (!TextUtils.isEmpty(tmp)) {
                            config.put("h", tmp!!.toInt())
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }

                    //视频rotation
                    try {
                        val tmp = intent.data!!.getQueryParameter("r")
                        if (!TextUtils.isEmpty(tmp)) {
                            config.put("r", tmp!!.toInt())
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }

                    //视频码率
                    try {
                        val tmp = intent.data!!.getQueryParameter("vb")
                        if (!TextUtils.isEmpty(tmp)) {
                            config.put("vb", tmp!!.toInt())
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }

                    //视频帧率
                    try {
                        val tmp = intent.data!!.getQueryParameter("fps")
                        if (!TextUtils.isEmpty(tmp)) {
                            config.put("fps", tmp!!.toInt())
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }

                    //视频Profile
                    try {
                        val tmp = intent.data!!.getQueryParameter("pf")
                        if (!TextUtils.isEmpty(tmp)) {
                            config.put("pf", tmp!!.toInt())
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }

                    //视频Level
                    try {
                        val tmp = intent.data!!.getQueryParameter("lv")
                        if (!TextUtils.isEmpty(tmp)) {
                            config.put("lv", tmp!!.toInt())
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }

                    //视频iFrameInterval
                    try {
                        val tmp = intent.data!!.getQueryParameter("ifi")
                        if (!TextUtils.isEmpty(tmp)) {
                            config.put("ifi", tmp!!.toInt())
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }

                    //音频码率
                    try {
                        val tmp = intent.data!!.getQueryParameter("ab")
                        if (!TextUtils.isEmpty(tmp)) {
                            config.put("ab", tmp!!.toInt())
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }

                    //音频采样率
                    try {
                        val tmp = intent.data!!.getQueryParameter("sr")
                        if (!TextUtils.isEmpty(tmp)) {
                            config.put("sr", tmp!!.toInt())
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }

                    //音频声道
                    try {
                        val tmp = intent.data!!.getQueryParameter("ac")
                        if (!TextUtils.isEmpty(tmp)) {
                            config.put("ac", tmp!!.toInt())
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }

                    //音频源
                    try {
                        val tmp = intent.data!!.getQueryParameter("as")
                        if (!TextUtils.isEmpty(tmp)) {
                            config.put("as", tmp!!.toInt())
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }

                    //音频回声消除
                    try {
                        val tmp = intent.data!!.getQueryParameter("ec")
                        if (!TextUtils.isEmpty(tmp)) {
                            config.put("ec", tmp!!.toInt())
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }

                    //音频降噪
                    try {
                        val tmp = intent.data!!.getQueryParameter("ns")
                        if (!TextUtils.isEmpty(tmp)) {
                            config.put("ns", tmp!!.toInt())
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }

                    //recordingHintEnable
                    try {
                        val tmp = intent.data!!.getQueryParameter("rh")
                        if (!TextUtils.isEmpty(tmp)) {
                            config.put("rh", tmp!!.toInt())
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }

                    //videoStabilizationEnable
                    try {
                        val tmp = intent.data!!.getQueryParameter("vs")
                        if (!TextUtils.isEmpty(tmp)) {
                            config.put("vs", tmp!!.toInt())
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }

                    //useCustomSurfaceTexture
                    try {
                        val tmp = intent.data!!.getQueryParameter("cs")
                        if (!TextUtils.isEmpty(tmp)) {
                            config.put("cs", tmp!!.toInt())
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }

                    if (!camera!!.isStreaming) {
                        val prepareEncodersSuccess = prepareEncoders()
                        Log.d(TAG, "prepareEncodersSuccess:$prepareEncodersSuccess")
                        camera!!.startStream(null)
                    }
                }
            }
        }
    }

    private fun prepareEncoders(): Boolean {
        Log.d(TAG, "getCameraOrientation:" + CameraHelper.getCameraOrientation(this))
        Log.d(TAG, "prepareEncoders config:$config")
        val prepareVideoSuccess: Boolean
        val prepareAudioSuccess: Boolean
        var width = 864
        try {
            if (config.has("w") && config.getInt("w") > 0) {
                width = config.getInt("w")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        var height = 480
        try {
            if (config.has("h") && config.getInt("h") > 0) {
                height = config.getInt("h")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        var rotation = 0
        try {
            if (config.has("r") && config.getInt("r") >= 0) {
                rotation = config.getInt("r")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        var fps = 30
        try {
            if (config.has("fps") && config.getInt("fps") > 0) {
                fps = config.getInt("fps")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        var videoBitrate = 1000
        try {
            if (config.has("vb") && config.getInt("vb") > 0) {
                videoBitrate = config.getInt("vb")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        var profile = -1
        try {
            if (config.has("pf") && config.getInt("pf") > 0) {
                profile = config.getInt("pf")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        var level = -1
        try {
            if (config.has("lv") && config.getInt("lv") > 0) {
                level = config.getInt("lv")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        var iFrameInterval = 3
        try {
            if (config.has("ifi") && config.getInt("ifi") >= 0) {
                iFrameInterval = config.getInt("ifi")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        var audioBitrate = 64
        try {
            if (config.has("ab") && config.getInt("ab") > 0) {
                audioBitrate = config.getInt("ab")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        var sampleRate = 44100
        try {
            if (config.has("sr") && config.getInt("sr") > 0) {
                sampleRate = config.getInt("sr")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        var isStereo = false
        try {
            if (config.has("ac") && config.getInt("ac") == 2) {
                isStereo = true
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        var audioSource = MediaRecorder.AudioSource.MIC
        try {
            if (config.has("as") && config.getInt("as") >= 0) {
                audioSource = config.getInt("as")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        var echoCanceler = true
        try {
            if (config.has("ec") && config.getInt("ec") == 0) {
                echoCanceler = false
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        var noiseSuppressor = true
        try {
            if (config.has("ns") && config.getInt("ns") == 0) {
                noiseSuppressor = false
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        var videoStabilizationEnable = false
        try {
            if (config.has("vs") && config.getInt("vs") == 1) {
                videoStabilizationEnable = true
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        var recordingHintEnable = false
        try {
            if (config.has("rh") && config.getInt("rh") == 1) {
                recordingHintEnable = true
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        var useCustomSurfaceTexture = false
        try {
            if (config.has("cs") && config.getInt("cs") == 1) {
                useCustomSurfaceTexture = true
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        camera!!.setVideoStabilizationEnable(videoStabilizationEnable)
        camera!!.setRecordingHintEnable(recordingHintEnable)
        camera!!.setUseCustomSurfaceTexture(useCustomSurfaceTexture)
        camera!!.startPreview(CameraHelper.Facing.BACK, width, height, fps, rotation)
        camera!!.setAvCodecCallback(this)
        prepareVideoSuccess = camera!!.prepareVideo(
            width,
            height,
            fps,
            videoBitrate * 1024,
            iFrameInterval,
            rotation,
            profile,
            level
        )
        prepareAudioSuccess = camera!!.prepareAudio(
            audioSource,
            audioBitrate * 1024,
            sampleRate,
            isStereo,
            echoCanceler,
            noiseSuppressor
        )
        Log.d(
            TAG,
            "prepareEncoders prepareVideoSuccess:$prepareVideoSuccess prepareAudioSuccess:$prepareAudioSuccess"
        )
        return prepareVideoSuccess && prepareAudioSuccess
    }

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            Log.d(TAG, "onServiceConnected $name $service")
            val binder = service as PeripheralBinder
            isBind = true
            binder.prepare()
            blueToothPeripheralService = binder.service
            blueToothPeripheralService?.senderCallback = senderCallback
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.d(TAG, "onServiceDisconnected $name")
            isBind = false
            blueToothPeripheralService = null
            if (!isDestroyed && !isFinishing) {
                finish()
            }
        }
    }

    companion object {
        private const val TAG = "CameraActivity"
    }
}