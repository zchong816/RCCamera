package net.vicp.zchong.live.central.client.spp

import android.annotation.SuppressLint
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.SystemClock
import android.util.Log
import net.vicp.zchong.live.central.client.AbstractReceiver
import net.vicp.zchong.live.central.client.AbstractSocketReceiver
import net.vicp.zchong.live.central.client.FillBlockingQueue
import net.vicp.zchong.live.central.client.FillBufferedInputStream
import net.vicp.zchong.live.central.client.IFill
import net.vicp.zchong.live.common.SppUUIDS
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream

/**
 * @author zhangchong
 * @date 2024/8/20 00:39
 */
@SuppressLint("MissingPermission")
class SppReceiver(context: Context, isStreamMode: Boolean) :
    AbstractSocketReceiver(context,isStreamMode) {

    private var socket: BluetoothSocket? = null
    override fun getInputStream(): InputStream? {
        try {
            socket = device?.createInsecureRfcommSocketToServiceRecord(SppUUIDS.CONNECT_UUID)
            socket!!.connect()
            return socket!!.getInputStream()
        } catch (t: Throwable) {
            Log.d(TAG, "createReceiverBuffer fail : $t")
            t.printStackTrace()
        }
        return null
    }

    override fun colse() {
        if (socket != null) {
            try {
                socket!!.close()
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                socket = null
            }
        }
    }

    companion object {
        private const val TAG = "SppReceiver"
    }
}
