package net.vicp.zchong.live.peripheral

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import net.vicp.zchong.live.peripheral.server.BlueToothPeripheralService

/**
 * @author zhangchong
 * @date 2024/8/21 15:28
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.intent.action.BOOT_COMPLETED") {
            val sendThread = object : Thread("start_gatt_service_thread") {
                override fun run() {
                    try {
                        sleep(10 * 1000)
                    } catch (t: Throwable) {
                        t.printStackTrace()
                        return
                    }
                    startGattService(context)
                }
            }
            sendThread.start()
        }
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"
        @JvmStatic
        fun startGattService(context: Context) {
            val intent = Intent(context, BlueToothPeripheralService::class.java)
            try {
                intent.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Log.i(TAG, "startForegroundService: $intent")
                    context.startForegroundService(intent)
                } else {
                    Log.i(TAG, "startService:$intent")
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
