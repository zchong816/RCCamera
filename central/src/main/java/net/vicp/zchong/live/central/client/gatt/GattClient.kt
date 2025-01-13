package net.vicp.zchong.live.central.client.gatt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import net.vicp.zchong.live.central.IPeripheralPage
import net.vicp.zchong.live.central.IConfigParams
import net.vicp.zchong.live.central.IErrorCallback
import net.vicp.zchong.live.central.client.AbstractClient
import net.vicp.zchong.live.central.client.IAVCallback
import net.vicp.zchong.live.central.client.IReceiver
import net.vicp.zchong.live.central.client.IReceiverCallback
import net.vicp.zchong.live.central.client.spp.SppReceiver
import net.vicp.zchong.live.central.client.wifi.WifiAPReceiver
import net.vicp.zchong.live.central.client.wifi.WifiP2PReceiver
import net.vicp.zchong.live.common.BtTrack
import net.vicp.zchong.live.common.DataConnectType
import net.vicp.zchong.live.common.GattCommand
import java.lang.RuntimeException

/**
 * @author zhangchong
 * @date 2024/8/16 16:05
 */
@SuppressLint("MissingPermission")
class GattClient(context: Context) : AbstractClient(context) {
    private val TAG = "GattClient"

    //蓝牙设配器
    private lateinit var mBluetoothAdapter: BluetoothAdapter
    private var bleGattHelper: BleGattHelper? = null

    private var avCallback: IAVCallback? = null

    init {
        initBluetooth()
    }

    var peripheralPageCallback: IPeripheralPage? = null

    var errorCallback: IErrorCallback? = null
        set(value) {
            field = value
            bleGattHelper?.errorCallback = value
        }

    var conigParams: IConfigParams? = null

    private fun initBluetooth(): Boolean {
        Log.d(BtTrack.TAG, "Central init Bluetooth")
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = manager.adapter

        val pairedDevices: Set<BluetoothDevice> = mBluetoothAdapter.getBondedDevices()
        Log.d(
            TAG,
            "Bluetooth pairedDevices size:" + if (pairedDevices != null) pairedDevices.size else null
        )
        Log.d(BtTrack.TAG, "Central begin found paired Device")
        var pairedDevice: BluetoothDevice? = null
        var pairedUUIDS: String? = null
        for (bd in pairedDevices) {
            var uuids = ""
            for (uuid in bd.uuids) {
                uuids += ("$uuid ")
            }
            BluetoothDevice.BOND_BONDING
            //BOND_NONE = 10
            //BOND_BONDING = 11
            //BOND_BONDED = 12
            Log.d(
                TAG,
                "Bluetooth bd:" + bd.toString()
                        + " name:" + bd.name
                        + " type:" + bd.type
                        + " address:" + bd.address
                        + " bondState:" + bd.bondState
                        + " uuids:" + uuids

            )
            if (pairedDevice == null) {
                pairedDevice = bd
                pairedUUIDS = uuids
            }
            if (bd.name.uppercase().startsWith("LAWK S1")) {
                pairedDevice = bd
                pairedUUIDS = uuids
            }
            break
        }
        if (pairedDevice == null) {
            Log.e(BtTrack.TAG, "Central not found paired Device")
            Log.e(TAG, "没有配对的蓝牙设备")
            return false
        } else {
            Log.d(
                BtTrack.TAG,
                "Central found paired Device ${pairedDevice.name} ${pairedDevice.type} ${pairedDevice.address} ${pairedDevice.bondState}  $pairedUUIDS"
            )
            bleGattHelper = BleGattHelper(context, pairedDevice.address)
            bleGattHelper!!.setBleConnectionListener(BleListener())
            return true
        }
    }
    fun connect() {
        if (bleGattHelper == null) {
            if (!initBluetooth()) {
                Toast.makeText(context, "请和外设进行蓝牙配对", Toast.LENGTH_LONG).show()
                Thread {
                    Thread.sleep(2000)
                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }.start()
                return
            }
        }
        Log.d(TAG,"connect")
        try {
            bleGattHelper?.connection()
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun disconnect() {
        Log.d(TAG,"disconnect")
        bleGattHelper?.disConnection()
        receiver?.destroy()
        receiver = null
    }

    fun setAVCallback(avCallback: IAVCallback) {
        this.avCallback = avCallback
    }


    private fun createReceiver(dataConnectType: Int, address: String? = null): IReceiver {
        Log.d(TAG, "createReceiver: dataConnectType$dataConnectType address:$address")
        when (dataConnectType) {
            DataConnectType.SPP_STREAM -> {
                return SppReceiver(context,true)
            }
            DataConnectType.SPP_BUFFER -> {
                return SppReceiver(context,false)
            }
            DataConnectType.GATT_OVER_BREDR -> {
                return GattReceiver(context)
            }
            DataConnectType.WIFI_AP -> {
                return WifiAPReceiver(context, address)
            }
            DataConnectType.WIFI_P2P -> {
                return WifiP2PReceiver(context, address)
            }
            else -> {
                throw RuntimeException("not support createReceiver dataConnectType:$dataConnectType")
            }
        }
    }

    private fun initReceiver(
        device: BluetoothDevice, dataConnectType: Int, isDebugBandWidth: Boolean = false,
        address: String? = null
    ) {
        Log.d(TAG,"initReceiver")
        receiver?.destroy()
        receiver = createReceiver(dataConnectType, address)
        receiver?.avCallback = avCallback
        receiver?.errorCallback = errorCallback
        receiver?.callback = object : IReceiverCallback {
            override fun onReceiveBufferCreate(success: Boolean) {
                receiver?.startReceiveData()
                if (isDebugBandWidth) {
                    val cameraParams = (conigParams?.getCarmerParams()) ?: ""
                    Log.d(BtTrack.TAG, "Central send OPEN_DEBUG_BANDWIDTH $cameraParams")
                    bleGattHelper?.writeCommandCharacteristic(
                        (GattCommand.OPEN_DEBUG_BANDWIDTH + cameraParams).toByteArray()
                    )
                } else {
                    val cameraParams = (conigParams?.getCarmerParams()) ?: ""
                    Log.d(BtTrack.TAG, "Central send OPEN_CAMERA $cameraParams")
                    bleGattHelper?.writeCommandCharacteristic(
                        (GattCommand.OPEN_CAMERA + cameraParams).toByteArray()
                    )
                }
            }
        }
        receiver?.setDeivce(device)
        receiver?.createReceiverBuffer()
    }

    private inner class BleListener : BleGattHelper.BleConnectionListener {
        override fun onConnectionSuccess() {
            Log.d(TAG, "onConnectionSuccess")
        }

        override fun onConnectionFail() {
            Log.d(TAG, "onConnectionFail")
        }

        override fun disConnection() {
            Log.d(TAG, "disConnection")
        }

        override fun discoveredServices() {
            Log.d(TAG, "discoveredServices")
        }

        override fun readCharacteristic(data: String) {
            Log.d(TAG, "readCharacteristic: " + data)
        }

        override fun writeCharacteristic(data: String) {
            Log.d(TAG, "writeCharacteristic: " + data)
        }

        override fun readDescriptor(data: String) {
            Log.d(TAG, "readDescriptor: " + data)
        }

        override fun writeDescriptor(data: String) {
            Log.d(TAG, "writeDescriptor: " + data)
        }

        override fun characteristicChange(data: ByteArray) {
            receiver?.fillData(data)
        }

        override fun onConnectOk(
            device: BluetoothDevice, isDebugBandWidth: Boolean,
            address: String?
        ) {
            Log.d(TAG,"onConnectOK device:$device isDebugBandWidth:$isDebugBandWidth address:$address")
            Log.d(BtTrack.TAG,"onConnectOK device:$device isDebugBandWidth:$isDebugBandWidth address:$address")
            val dataConnectType = getDataConnectType()
            initReceiver(device, dataConnectType, isDebugBandWidth, address)
        }

        override fun onPeripheralPageClose() {
            peripheralPageCallback?.onClose()
            receiver?.destroy()
            receiver = null
        }

        override fun onPeripheralPageOpen() {
            peripheralPageCallback?.onOpen()
        }

        override fun getCarmerParams(): String? {
            return conigParams?.getCarmerParams()
        }

        override fun getDataConnectType(): Int{
            return conigParams?.getDataConnectType() ?: DataConnectType.DEFAULT
        }

        override fun isDebugBandWidth(): Boolean {
            return conigParams?.isDebugBandWidth() ?: false
        }

        override fun getDebugBandWidth(): Int {
            return conigParams?.getDebugBandWidth() ?: 1000
        }
    }

}