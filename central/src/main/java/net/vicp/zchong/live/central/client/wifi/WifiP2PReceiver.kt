package net.vicp.zchong.live.central.client.wifi

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.HandlerThread
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import net.vicp.zchong.live.central.client.AbstractSocketReceiver
import java.io.IOException
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket

@SuppressLint("MissingPermission")
open class WifiP2PReceiver(context: Context, address: String?) :
    AbstractSocketReceiver(context, false, address), WifiP2pManager.ChannelListener {

    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null
    private val port = 8998

    private var manager: WifiP2pManager? = null

    private var channel: WifiP2pManager.Channel? = null

    private var looper: Looper? = null

    private var p2pHandlerThread: HandlerThread? = null

    private val guideRunnable = Runnable {
        Toast.makeText(context, "请设置WIFI直连配对", Toast.LENGTH_LONG).show()
        fillDataHandler?.postDelayed({
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }, 2000)
    }

    init {
        p2pHandlerThread = HandlerThread("p2p_handler_thread")
        p2pHandlerThread!!.start()
        looper = p2pHandlerThread!!.looper
        manager = context!!.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        if (manager != null) {
            channel = manager!!.initialize(context, looper, this)
        }

        manager!!.requestConnectionInfo(channel, object : WifiP2pManager.ConnectionInfoListener {
            override fun onConnectionInfoAvailable(wifiP2pInfo: WifiP2pInfo?) {
                wifiP2pInfo?.let {
                    try {
                        val ownerHostAddress = it.groupOwnerAddress?.hostAddress
                        Log.d(
                            TAG, "WifiP2p ownerHostAddress: $ownerHostAddress" +
                                    " isGroupOwner:${it.isGroupOwner}"
                        )
                        if (ownerHostAddress != null) {
                            fillDataHandler?.removeCallbacks(guideRunnable)
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }
                }
            }
        })
        fillDataHandler?.postDelayed(guideRunnable, 10 * 1000)
    }


    override fun onChannelDisconnected() {
        Log.d(TAG, "onChannelDisconnected")
    }

    override fun getBufferSize(): Int {
        return 500
    }

    override fun getInputStream(): InputStream? {
        try {
            Log.d(
                TAG, "WifiP2p server address: $address"
            )
            address?.let {
                if (address != "") {
                    val splitAddress = it.split(",")
                    val ip = splitAddress[0]  // 获取IP地址
                    val port = splitAddress[1].toInt()  // 获取端口并转换为整
                    socket = Socket(ip, port)
                    return socket!!.getInputStream()
                }
            }

            //Peripheral设备不是Group Owner，因此这里在Central端启动ServerSocket
            try {
                serverSocket = ServerSocket(port)
                socket = serverSocket!!.accept()
                return socket!!.getInputStream()
            } catch (e: IOException) {
                Log.e(TAG, "Error creating SocketServer: ", e)
            }


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

    companion object {
        private const val TAG = "WifiP2PReceiver"
    }
}
