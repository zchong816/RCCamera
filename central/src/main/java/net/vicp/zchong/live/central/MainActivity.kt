package net.vicp.zchong.live.central

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.bytedance.android.openlive.broadcast.AccountAuthCallback
import com.bytedance.android.openlive.broadcast.DouyinBroadcastApi
import com.bytedance.android.openlive.broadcast.ResponseCode
import com.bytedance.android.openlive.broadcast.model.CamType
import com.bytedance.android.openlive.broadcast.model.LiveAngle
import net.vicp.zchong.live.central.av.AacDeCodePlayFromReceiver
import net.vicp.zchong.live.central.av.H264DeCodePlayFromReceiver
import net.vicp.zchong.live.central.client.IAVCallback
import net.vicp.zchong.live.central.client.ITransmissionSpeed
import net.vicp.zchong.live.central.client.ProtocolParseHelper
import net.vicp.zchong.live.central.client.gatt.GattClient
import net.vicp.zchong.live.central.databinding.ActivityMainBinding
import net.vicp.zchong.live.central.setting.LiveSettingDialog
import net.vicp.zchong.live.central.setting.LiveSettingHelper
import net.vicp.zchong.live.central.utils.ToastUtils
import net.vicp.zchong.live.common.BtTrack
import net.vicp.zchong.live.common.DataConnectType
import net.vicp.zchong.live.common.ModelUtils


/**
 * @author zhangchong
 * @date 2024/8/16 15:45
 */
class MainActivity : AppCompatActivity(), IPeripheralPage, IConfigParams, IAVCallback, IErrorCallback {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var binding: ActivityMainBinding? = null
    private val PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH,
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH,
        )
    }

    private val PERMISSIONS_LWK_GLASS_S1 = arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )

    private var openRoomId: String? = null
    private var rtmp: String? = null

    private var gattClient: GattClient? = null

    private var peripheralPageOpen = false
    private var isPreview = false
    private var isDebugBandWidth = false

    var h264DeCodePlayFromReceiver: H264DeCodePlayFromReceiver? = null

    var aacDeCodePlayFromReceiver: AacDeCodePlayFromReceiver? = null

    var audioCsd0 = byteArrayOf((0x12).toByte(), (0x08).toByte())

    private fun checkPermissions() {
        val hasPermissions = hasPermissions(this)
        Log.d(TAG, "hasPermissions:$hasPermissions")
        if (!hasPermissions) {
            requestPermissions()
        } else {
            init()
        }
    }

    private fun hasPermissions(context: Context): Boolean {
        Log.d(TAG, "model:" + Build.MODEL)
        Log.d(BtTrack.TAG, "model:" + Build.MODEL)
        if (ModelUtils.isLawkS1) {
            return hasPermissions(context, *PERMISSIONS_LWK_GLASS_S1)
        }
        return hasPermissions(context, *PERMISSIONS)
    }

    private fun hasPermissions(context: Context?, vararg permissions: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null && permissions != null) {
            for (permission in permissions) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        permission
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return false
                }
            }
        }
        return true
    }

    private fun requestPermissions() {
        Log.d(TAG, "requestPermissions SDK_INT:" + Build.VERSION.SDK_INT)
        ActivityCompat.requestPermissions(this, PERMISSIONS, 1)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(TAG, "onRequestPermissionsResult requestCode: $requestCode")
        var grant = true
        for (permission in permissions) {
            Log.d(TAG, "onRequestPermissionsResult permission: $permission")
        }
        for (grantResult in grantResults) {
            Log.d(TAG, "onRequestPermissionsResult grantResult: $grantResult")
            if (grantResult != 0) {
                grant = false
            }
        }
        if (grant) {
            init()
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = ActivityMainBinding.inflate(
            layoutInflater
        )
        setContentView(binding!!.getRoot())
        checkPermissions()
        val ip = getAddress()
        Log.d(BtTrack.TAG, "wifi ip: $ip")
        Log.d(BtTrack.TAG, "wifi ip: $ip")
    }

    private fun onError(connectType: Int) {
        disconnet()
        var info = ""
        when (connectType) {
            DataConnectType.WIFI_AP -> {
                info = "请设置WIFI热点"
            }

            DataConnectType.WIFI_P2P -> {
                info = "请设置WIFI直连"
            }

            else -> {
                info = "未知错误"
            }
        }
        runOnUiThread {
            Toast.makeText(this, info, Toast.LENGTH_LONG).show()
            if (connectType == DataConnectType.WIFI_AP || connectType == DataConnectType.WIFI_P2P)
                binding!!.disconnect.postDelayed({
                    val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                }, 2000)
        }
    }

    override fun onCentralError(connectType: Int) {
        onError(connectType)
    }

    override fun onPeripheralError(connectType: Int) {
        onError(connectType)
    }

    override fun onAudio(data: ByteArray) {
//        Log.d(BtTrack.TAG, "onAudio size:${data.size}")
        runOnUiThread{
            binding!!.voice.visibility = View.VISIBLE
        }
        if (data.size == 2) {
            Log.d(BtTrack.TAG, "onAudio size:${data.size} csd-0:[${String.format("0x%02x,0x%02x",data[0],data[1])}]")
            audioCsd0 = data
        }
        updateVoice()
        if (startAudio) {
            aacDeCodePlayFromReceiver?.pushData(data)
        }
    }

    var startVideo = false
    var startAudio = false
    override fun onVideo(data: ByteArray) {
//        Log.d(BtTrack.TAG, "onVideo size:${data.size}")
        if (!startVideo) {
            startVideo = true
            val surface = binding!!.surfaceView.holder.surface
            val previewSize = LiveSettingHelper.getPreviewSize()
            h264DeCodePlayFromReceiver = H264DeCodePlayFromReceiver(
                surface,
                previewSize.first,
                previewSize.second,
                LiveSettingHelper.getFps()
            )
            h264DeCodePlayFromReceiver?.decodeAndPlay()
        }
        if (startVideo) {
            h264DeCodePlayFromReceiver?.pushData(data)
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }


    private fun speedInfo() {
        setTransmissionSpeed(object : ITransmissionSpeed {
            override fun callback(
                speed: Int,
                deltaTimes: Long,
                avgSpeed: Int,
                sumTime: Long,
                sumBytes: Long
            ) {
                runOnUiThread {
                    val info = ("deltaBytes:$sumBytes deltaTime:$deltaTimes" +
                            (("\nsumBytes:" + ((sumBytes / 1000).toString() + "KB")
                                    + "\nsumTime:" + (sumTime / 1000) + "S"
                                    + "\navgSpeed:" + avgSpeed + "KB/S" + " " + (avgSpeed * 8) + "kbps")))
                    val speedInfo = "speed:" + speed + "KB/S" + " " + (speed * 8) + "kbps"
                    binding!!.speedInfo.text = info
                    if (speed >= 100) {
                        binding!!.speedCurrentInfo.setTextColor(Color.GREEN)
                    } else {
                        binding!!.speedCurrentInfo.setTextColor(Color.RED)
                    }
                    binding!!.speedCurrentInfo.text = speedInfo
                }
            }
        })
    }


    private fun init() {
        gattClient = GattClient(applicationContext)
        gattClient?.setAVCallback(this)
        gattClient!!.peripheralPageCallback = this
        gattClient!!.errorCallback = this
        gattClient!!.conigParams = this
        speedInfo()
        binding!!.live.setOnClickListener {
            isPreview = false
            isDebugBandWidth = false
            if (peripheralPageOpen) {
                onOpen()
            } else {
                gattClient?.connect()
            }
        }
        binding!!.preview.setOnClickListener {
            isPreview = true
            isDebugBandWidth = false
            if (peripheralPageOpen) {
                onOpen()
            } else {
                gattClient?.connect()
            }
        }
        binding!!.bandwidth.setOnClickListener {
            isPreview = true
            isDebugBandWidth = true
            if (peripheralPageOpen) {
                onOpen()
            } else {
                gattClient?.connect()
            }
        }
        binding!!.offlive.setOnClickListener {
            rtmp = null
            quitDouyinLive()
        }
        binding!!.disconnect.setOnClickListener {
            disconnet()
        }
        binding!!.douyinlive.setOnClickListener { getDouyinLivePushUrl(null) }

        binding!!.setting.setOnClickListener {
            showSettingDialog()
        }

        updateVoice()

        binding!!.voice.setOnClickListener{
            startAudio = !startAudio
            updateVoice()
            if (startAudio) {
                aacDeCodePlayFromReceiver = AacDeCodePlayFromReceiver(
                    LiveSettingHelper.getSampleRate(),
                    LiveSettingHelper.getChannelCount(),
                    audioCsd0
                )
                aacDeCodePlayFromReceiver?.initAudioTrack()
                aacDeCodePlayFromReceiver?.decodeAndPlay()
            }else{
                aacDeCodePlayFromReceiver?.stop()
            }
        }
    }

    private fun disconnet(){
        quitDouyinLive()
        resetStatus()
        gattClient?.disconnect()
        startVideo = false
        h264DeCodePlayFromReceiver?.stop()
        startAudio = false
        aacDeCodePlayFromReceiver?.stop()
        binding!!.voice.post {
            updateVoice()
            binding!!.voice.visibility = View.INVISIBLE
        }
    }

    private fun updateVoice() {
        if (startAudio) {
            binding!!.voice.setImageResource(R.drawable.voice)
            val color = Color.GREEN
            binding!!.voice.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
        } else {
            binding!!.voice.setImageResource(R.drawable.mute)
            val color = Color.RED
            binding!!.voice.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
        }
    }

    private fun resetStatus() {
        peripheralPageOpen = false
        isPreview = false

        runOnUiThread {
            binding!!.live.isEnabled = true
            binding!!.preview.isEnabled = true
            binding!!.bandwidth.isEnabled = true
            binding!!.setting.isEnabled = true
            isDebugBandWidth = false
            isPreview = false
            binding!!.live.text = "一键开播"
        }


    }

    fun setTransmissionSpeed(iTransmissionSpeed: ITransmissionSpeed?) {
        ProtocolParseHelper.getInstance().setiTransmissionSpeed(iTransmissionSpeed)
    }

    private fun quitDouyinLive() {
        Log.d(TAG, "quitDouyinLive openRoomId:$openRoomId")
        Thread {
            if (!TextUtils.isEmpty(openRoomId)) {
                val liveResp = DouyinBroadcastApi.turnOffBroadcast(openRoomId)
                if (liveResp != null) {
                    val info = "下播成功" + liveResp.getPrompts()
                    Log.d(TAG, info)
                    runOnUiThread {
                        ToastUtils.showBottomToast(applicationContext, info)
                    }
                } else {
                    var info =
                        "下播失败：" + (if (liveResp == null) "请求失败" else (liveResp.getStatusCode()
                            .toString() + " " + liveResp.getPrompts()))
                    Log.e(TAG, info)
                    runOnUiThread {
                        ToastUtils.showBottomToast(applicationContext, info)
                    }
                }
                runOnUiThread {
                    binding!!.live.isEnabled = true
                }
                rtmp = null
                openRoomId = null
            }
        }.start()
    }


    private fun getDouyinLivePushUrl(callback: IGetLivePushUrl?) {
        Log.d(TAG, "getDouyinLivePushUrl")
        Thread {
            val liveAngle = LiveAngle.STANDARD
            val camType = CamType.CAM
            val liveResp = DouyinBroadcastApi.startBroadcast(liveAngle, camType)
            if (liveResp != null && !TextUtils.isEmpty(liveResp.getRtmpPushUrl())) {
                Log.d(TAG, "开播地址获取成功: " + liveResp.getRtmpPushUrl())
                runOnUiThread {
                    ToastUtils.showBottomToast(applicationContext, "开播地址获取成功")
                }
                rtmp = liveResp.getRtmpPushUrl()
                openRoomId = liveResp.getOpenRoomId()
                callback?.callback(rtmp!!, openRoomId!!)
            } else {
                runOnUiThread {
                    ToastUtils.showBottomToast(applicationContext, "开播地址获取失败")
                }
                if (liveResp!!.getStatusCode() == ResponseCode.USER_UNAUTHORIZED) {
                    Log.e(TAG, "鉴权失败: " + ResponseCode.USER_UNAUTHORIZED)
                    authDouyinLive()
                } else {
                    Log.e(
                        TAG,
                        "开播失败: " + liveResp.getStatusCode() + " " + liveResp.getPrompts()
                    )
                }
            }
        }.start()
    }

    private fun authDouyinLive() {
        Log.d(TAG, "authDouyinLive")
        DouyinBroadcastApi.login(this, object : AccountAuthCallback {
            override fun onSuccess() {
                Log.d(TAG, "授权成功")
            }

            override fun onFailed(errorCode: Int, errorMsg: String) {
                Log.e(TAG, "授权失败 errorCode:" + errorCode + " errorMsg:" + errorMsg)
            }
        })

    }

    interface IGetLivePushUrl {
        fun callback(pushUrl: String, roomId: String)
    }

    override fun getDataConnectType(): Int {
        val connectType = LiveSettingHelper.getDataConnectType()
        Log.d(BtTrack.TAG, "getDataConnectType $connectType")
        return connectType
    }

    override fun getCarmerParams(): String? {
        val carmerParams = LiveSettingHelper.getCarmerParams()
        Log.d(BtTrack.TAG, "getCarmerParams $carmerParams")
        return carmerParams
    }

    override fun isDebugBandWidth(): Boolean {
        return isDebugBandWidth
    }

    override fun getDebugBandWidth(): Int {
        val bandWidth = LiveSettingHelper.getBandwidth()
        Log.d(BtTrack.TAG, "getDebugBandWidth $bandWidth")
        return bandWidth
    }

    override fun getCameraFps(): Int {
        val fps = LiveSettingHelper.getFps()
        Log.d(BtTrack.TAG, "getCameraFps $fps")
        return fps
    }

    override fun getCameraPreviewSize(): Pair<Int, Int> {
        val size = LiveSettingHelper.getPreviewSize()
        Log.d(BtTrack.TAG, "getCameraPreviewSize ${size.first} ${size.second}")
        return size
    }

    override fun onOpen() {
        Log.d(TAG, "onOpen")
        runOnUiThread {
            binding!!.live.isEnabled = false
            binding!!.preview.isEnabled = false
            binding!!.bandwidth.isEnabled = false
            binding!!.setting.isEnabled = false
        }
        peripheralPageOpen = true
        if (isPreview && !isDebugBandWidth) {
            runOnUiThread {
                binding!!.live.isEnabled = true
            }
            return
        }

        if (!isPreview) {
            if (!TextUtils.isEmpty(rtmp)) {
                runOnUiThread {
                    ToastUtils.showBottomToast(applicationContext, "开始抖音推流")
                }
                val size = getCameraPreviewSize()
                gattClient?.receiver?.pushRtmpLiveStream(
                    rtmp,
                    size.first,
                    size.second,
                    getCameraFps()
                )
                rtmp = null
            } else {
                getDouyinLivePushUrl(object : IGetLivePushUrl {
                    override fun callback(pushUrl: String, roomId: String) {
                        runOnUiThread {
                            ToastUtils.showBottomToast(applicationContext, "开始抖音推流")
                        }
                        val size = getCameraPreviewSize()
                        gattClient?.receiver?.pushRtmpLiveStream(
                            pushUrl,
                            size.first,
                            size.second,
                            getCameraFps()
                        )
                        rtmp = null
                    }
                })
            }
        }

    }

    override fun onClose() {
        Log.d(TAG, "onOpen")
        Log.d(BtTrack.TAG, "onOpen")
        resetStatus()
        quitDouyinLive()
        gattClient?.disconnect()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        quitDouyinLive()
    }

    private fun showSettingDialog() {
        val dialog = LiveSettingDialog(this)
        dialog?.apply {
            setTitle(
                getString(R.string.glasses_live_setting),
            )
            setCancelText(getString(R.string.text_cancel))
            setConfirmText(getString(R.string.text_confirm))
            setOnCancelClickListener {
                dismiss()
            }
            setOnConfirmClickListener {
                dialog.save()
                dismiss()
            }
            show()
        }
    }

    fun getAddress(): String? {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return null
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return null
        if (!networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return null
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo ?: return null
        val ip = wifiInfo.ipAddress
        return String.format(
            "%d.%d.%d.%d",
            ip and 0xff,
            ip shr 8 and 0xff,
            ip shr 16 and 0xff,
            ip shr 24 and 0xff
        )
    }
}