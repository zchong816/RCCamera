package net.vicp.zchong.live.peripheral.server

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import net.vicp.zchong.live.common.BtTrack
import net.vicp.zchong.live.common.GattCommandNotify
import net.vicp.zchong.live.peripheral.R
import net.vicp.zchong.live.peripheral.av.IAVCodecCallback
import net.vicp.zchong.live.peripheral.sender.ISenderCallback
import net.vicp.zchong.live.peripheral.server.gatt.GattServer

/**
 * @author zhangchong
 * @date 2024/8/15 13:47
 */
class BlueToothPeripheralService : Service(), IServerCallback, IAVCodecCallback {
    private val binder: IBinder = PeripheralBinder()
    private var gattServer: GattServer? = null
    private var isBind = false
    var senderCallback: ISenderCallback? = null
    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        Log.d(TAG, "onCreate")
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
        gattServer = GattServer(applicationContext, this)
    }

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 如果服务被杀死，系统会尝试重新启动服务（不保证成功）
        Log.d(TAG, "onStartCommand intent:$intent flags:$flags startId:$startId")
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        Log.d(TAG, "onConfigurationChanged newConfig$newConfig")
        super.onConfigurationChanged(newConfig)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
        // 在服务销毁时执行清理操作
        Log.d(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent): IBinder? {
        Log.d(TAG, "onBind intent:$intent")
        isBind = true
        notifyPeripheralPageOpenIfAvailable()
        return binder
    }

    override fun onRebind(intent: Intent) {
        Log.d(TAG, "onRebind intent:$intent")
        isBind = true
        notifyPeripheralPageOpenIfAvailable()
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent): Boolean {
        Log.d(TAG, "onUnbind intent:$intent")
        isBind = false
        Log.d(BtTrack.TAG,"Peripheral send CAMERA_CLOSE")
        gattServer!!.notify(GattCommandNotify.CAMERA_CLOSE)
        val sender = gattServer!!.sender
        sender?.destroy()
        return true
    }

    private fun checkCanNotifyPeripheralPageOpen(): Boolean {
        return if (gattServer != null && gattServer!!.sender != null && gattServer!!.device != null) {
            true
        } else false
    }

    private fun notifyPeripheralPageOpenIfAvailable() {
        val checkCanNotifyPeripheralPageOpen = checkCanNotifyPeripheralPageOpen()
        Log.d(TAG, "notifyPeripheralPageOpenIfAvailable checkPeripheralPageOpen:$checkCanNotifyPeripheralPageOpen")
        if (checkCanNotifyPeripheralPageOpen) {
            Log.d(BtTrack.TAG,"Peripheral send PERIPHERAL_PAGE_OPEN")
            gattServer!!.notify(GattCommandNotify.PERIPHERAL_PAGE_OPEN)
        }
    }

    private fun createNotification(): Notification {
        Log.d(TAG, "createNotification")
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
        val notification = builder
            .setOngoing(true)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        return builder.build()
    }

    inner class PeripheralBinder : Binder()  {
        val service: BlueToothPeripheralService
            get() = this@BlueToothPeripheralService
        fun prepare() {
            Log.d(TAG, "prepare")
        }
    }

    override fun onConnect(dataConnectType: Int) {
        Log.d(TAG, "onConnect dataConnectType:$dataConnectType gattServer:$gattServer sender:${gattServer?.sender}")
        Log.d(BtTrack.TAG, "onConnect dataConnectType:$dataConnectType gattServer:$gattServer sender:${gattServer?.sender}")
        setSenderCallback()
    }

    override fun onDisonnect() {
        Log.d(TAG, "onDisonnect")
        if (isBind) {
            startActivity("zchong://rclive/disconnect")
        }
    }

    override fun onOpenCamera(params: String?) {
        Log.d(TAG, "onOpenCamera params:$params")
        startActivity("zchong://rclive/camera?$params")
    }

    override fun openDebugBandwidth() {
        Log.d(TAG, "openDebugBandwidth")
        startActivity("zchong://rclive/debug_bandwidth")
        setSenderCallback(5000)
    }

    override fun onGetAVData(data: ByteArray, isAudio: Boolean) {
        if (gattServer != null && gattServer!!.sender != null) {
            gattServer!!.sender!!.writeBuffer(data)
        }
    }

    override fun onGetSpsPpsVps(data: ByteArray) {
        if (gattServer != null && gattServer!!.sender != null) {
            gattServer!!.sender!!.writeBuffer(data)
        }
    }

    private fun startActivity(url: String) {
        Log.d(TAG, "startActivity url:$url")
        val intent = Intent(Intent.ACTION_VIEW)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.setData(Uri.parse(url))
        application.startActivity(intent)
    }

    private fun setSenderCallback(delay: Long = 3000) {
        gattServer?.sender?.getReadHandler()?.postDelayed({
            senderCallback?.let {
                gattServer?.sender?.setCallback(it)
            }
        }, delay)
    }

    companion object {
        private const val TAG = "BlueToothPeripheralService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "BlueToothPeripheralServiceChannel"
        private const val CHANNEL_NAME = CHANNEL_ID
    }
}
