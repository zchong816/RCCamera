package net.vicp.zchong.live.peripheral.sender.wifi

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import androidx.core.os.postDelayed
import net.vicp.zchong.live.common.DataConnectType.Companion.WIFI_P2P
import net.vicp.zchong.live.common.GattCommandNotify.Companion.PERIPHERAL_ERROR
import net.vicp.zchong.live.peripheral.sender.AbstractSocketSender
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket


@SuppressLint("MissingPermission")
class WifiP2PSender(context: Context?, isStreamMode: Boolean, callback: ICreateSenderCallback) :
    AbstractSocketSender(
        context!!, isStreamMode, callback
    ), WifiP2pManager.ChannelListener {
    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null

    private val port = 8998

    private var manager: WifiP2pManager? = null

    private var channel: WifiP2pManager.Channel? = null

    private var looper: Looper? = null

    protected var p2pHandlerThread: HandlerThread? = null
    private var address: String? = null

    private var isGroupOwner: Boolean? = null

    private val p2pServiceCheckAvailableRunnable = Runnable {
        Log.d(TAG, "WifiP2pManager:$manager")
        if (manager == null) {
            isGroupOwner = false
            createSenderCallback?.callback("$PERIPHERAL_ERROR-$WIFI_P2P")
        }
    }

    init {
        Log.d(TAG,"init ...")
        p2pHandlerThread = HandlerThread("WifiP2p_Handler_Thread")
        p2pHandlerThread!!.start()
        looper = p2pHandlerThread!!.looper
         (context!!.getSystemService(Context.WIFI_P2P_SERVICE))?.let { it ->
             manager = it as WifiP2pManager
             channel = manager!!.initialize(context, looper, this)
             Log.d(TAG,"requestConnectionInfo ...")
             manager!!.requestConnectionInfo(channel, object : WifiP2pManager.ConnectionInfoListener {
                 override fun onConnectionInfoAvailable(wifiP2pInfo: WifiP2pInfo?) {
                     wifiP2pInfo?.let {
                         val ownerHostAddress = it.groupOwnerAddress?.hostAddress
                         Log.d(
                             TAG, "WifiP2p ownerHostAddress: $ownerHostAddress" +
                                     " isGroupOwner:${it.isGroupOwner}"
                         )

                         if (ownerHostAddress == null) {
                             getReadHandler()?.removeCallbacks(p2pServiceCheckAvailableRunnable)
                             isGroupOwner = false
                             createSenderCallback?.callback("$PERIPHERAL_ERROR-$WIFI_P2P")
                             return
                         }

                         isGroupOwner = it.isGroupOwner
                         address = ownerHostAddress
                         getReadHandler()?.removeCallbacks(p2pServiceCheckAvailableRunnable)
                         createSenderCallback?.callback(if (isGroupOwner!!) address else null)
                     }
                 }
             })
        }
        getReadHandler()?.postDelayed(p2pServiceCheckAvailableRunnable, 10 * 1000)
    }


    override fun onChannelDisconnected() {
        Log.d(TAG, "onChannelDisconnected")
    }

    override fun getAddress(): String? {
        if(isGroupOwner!!) {
            return "$address,$port"
        }
        return null
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
            if (isGroupOwner!!) {
                socket = serverSocket!!.accept()
                return socket!!.getOutputStream()
            } else {
                while (true) {
                    try {
                        socket = Socket()
                        socket!!.bind(null)
                        socket!!.connect(
                            InetSocketAddress(address, port),
                            3000
                        )
                        return socket!!.getOutputStream()
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }
                    try {
                        Thread.sleep(5)
                    } catch (t: Throwable) {
                        t.printStackTrace()
                        break
                    }
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        return null
    }

    override fun createServer(): Boolean {
        try {
            while (isGroupOwner == null) {
                try {
                    Thread.sleep(5)
                } catch (t: Throwable) {
                    t.printStackTrace()
                    break
                }
            }
            if (isGroupOwner!!) {
                serverSocket = ServerSocket(port)
            }
            return true
        } catch (e: IOException) {
            Log.e(TAG, "Error creating SocketServer: ", e)
        }
        return false
    }

    companion object {
        private const val TAG = "WifiP2PSender"
    }
}