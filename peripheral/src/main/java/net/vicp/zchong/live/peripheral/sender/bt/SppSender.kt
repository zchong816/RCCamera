package net.vicp.zchong.live.peripheral.sender.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import android.util.Log
import net.vicp.zchong.live.common.SppUUIDS
import net.vicp.zchong.live.peripheral.sender.AbstractSocketSender
import java.io.IOException
import java.io.OutputStream

/**
 * @author zhangchong
 * @date 2025/1/13 23:45
 */
@SuppressLint("MissingPermission")
class SppSender(context: Context?, isStreamMode: Boolean) : AbstractSocketSender(
    context!!, isStreamMode
) {
    private var serverSocket: BluetoothServerSocket? = null
    private val bluetoothAdapter: BluetoothAdapter?
    private var socket: BluetoothSocket? = null

    init {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth not supported")
        }
    }

    override fun getAddress(): String? {
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
            socket = serverSocket!!.accept()
            return socket!!.getOutputStream()
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        return null
    }

    override fun createServer(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                serverSocket = bluetoothAdapter!!.listenUsingInsecureRfcommWithServiceRecord(
                    APP_NAME, SppUUIDS.CONNECT_UUID
                )
                return true
            } catch (e: IOException) {
                Log.e(TAG, "Error creating SocketServer: ", e)
            }
        } else {
            try {
                serverSocket = bluetoothAdapter!!.listenUsingInsecureRfcommWithServiceRecord(
                    APP_NAME, SppUUIDS.CONNECT_UUID
                )
                return true
            } catch (e: IOException) {
                Log.e(TAG, "Error creating SocketServer: ", e)
            }
        }
        return false
    }

    companion object {
        private const val TAG = "SppSender"
        private const val APP_NAME = "SppSender"
    }
}
