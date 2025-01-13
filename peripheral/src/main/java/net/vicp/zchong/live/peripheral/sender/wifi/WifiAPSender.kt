package net.vicp.zchong.live.peripheral.sender.wifi

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import net.vicp.zchong.live.common.DataConnectType
import net.vicp.zchong.live.common.GattCommandNotify
import net.vicp.zchong.live.peripheral.sender.AbstractSocketSender
import java.io.IOException
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale

class WifiAPSender(context: Context?, isStreamMode: Boolean, callback: ICreateSenderCallback) :
    AbstractSocketSender(
        context!!, isStreamMode, callback
    ) {
    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null

    private val port = 8999

    @SuppressLint("DefaultLocale")
    override fun getAddress(): String? {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return null
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return null
        if (!networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return null
        }

        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo ?: return null
        val ip = wifiInfo.ipAddress
        return String.format(
            "%d.%d.%d.%d",
            ip and 0xff,
            ip shr 8 and 0xff,
            ip shr 16 and 0xff,
            ip shr 24 and 0xff
        ) + "," + port
    }

    override fun close() {
        if (socket != null) {
            try {
                socket!!.close()
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                socket = null
            }
        }
        if (serverSocket != null) {
            try {
                serverSocket!!.close()
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                serverSocket = null
            }
        }
    }

    override fun getOutputStream(): OutputStream? {
        try {
            socket = serverSocket!!.accept()
            return socket!!.getOutputStream()
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        return null
    }

    override fun createServer(): Boolean {
        try {
            val address = getAddress()
            if (address == null) {
                createSenderCallback?.callback("${GattCommandNotify.PERIPHERAL_ERROR}-${DataConnectType.WIFI_AP}")
                return false
            } else {
                createSenderCallback?.callback(address)
            }
            serverSocket = ServerSocket(port)
            return true
        } catch (e: IOException) {
            Log.e(TAG, "Error creating SocketServer: ", e)
        }
        return false
    }

    companion object {
        private const val TAG = "WifiAPSender"
    }
}