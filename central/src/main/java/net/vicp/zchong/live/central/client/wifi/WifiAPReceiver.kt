package net.vicp.zchong.live.central.client.wifi

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import net.vicp.zchong.live.central.client.AbstractSocketReceiver
import net.vicp.zchong.live.common.DataConnectType
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket


class WifiAPReceiver(context: Context, address: String?) :
    AbstractSocketReceiver(context, false, address) {

    private var socket: Socket? = null
    override fun getBufferSize(): Int {
        return 500
    }
    override fun getInputStream(): InputStream? {
        try {
            address?.let {
                val splitAddress = it.split(",")
                val ip = splitAddress[0]  // 获取IP地址
                val port = splitAddress[1].toInt()  // 获取端口并转换为整
                socket = Socket()
                socket!!.bind(null)
                socket!!.connect(
                    InetSocketAddress(ip, port),
                    3000
                )
                return socket!!.getInputStream()
            }
        } catch (t: Throwable) {
            Log.d(TAG, "createReceiverBuffer fail : $t")
            t.printStackTrace()
            errorCallback?.onCentralError(DataConnectType.WIFI_AP)
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
        private const val TAG = "WifiAPReceiver"
    }
}
