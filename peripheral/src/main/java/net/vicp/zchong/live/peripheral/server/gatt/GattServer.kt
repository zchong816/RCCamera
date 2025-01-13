package net.vicp.zchong.live.peripheral.server.gatt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.SystemClock
import android.util.Log
import net.vicp.zchong.live.common.BtTrack
import net.vicp.zchong.live.common.DataConnectType
import net.vicp.zchong.live.common.GattCommand
import net.vicp.zchong.live.common.GattCommandNotify
import net.vicp.zchong.live.common.GattUUIDS
import net.vicp.zchong.live.peripheral.sender.AbstractSender
import net.vicp.zchong.live.peripheral.sender.ISender
import net.vicp.zchong.live.peripheral.sender.bt.AbstractGattSender
import net.vicp.zchong.live.peripheral.sender.bt.SppSender
import net.vicp.zchong.live.peripheral.sender.wifi.WifiAPSender
import net.vicp.zchong.live.peripheral.sender.wifi.WifiP2PSender
import net.vicp.zchong.live.peripheral.server.IServerCallback


/**
 * @author zhangchong
 * @date 2024/8/15 16:07
 */

var TAG = "GattServer"

@SuppressLint("MissingPermission")
class GattServer {
    private val context: Context
    val serverCallback: IServerCallback

    //广播时间(设置为0则持续广播)
    private val mTime = 0

    //蓝牙管理类
    private lateinit var mBluetoothManager: BluetoothManager

    //蓝牙设配器
    private lateinit var mBluetoothAdapter: BluetoothAdapter

    //GattService
    private val mGattService = BluetoothGattService(GattUUIDS.UUID_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)

    //数据Characteristic
    private lateinit var mDataGattCharacteristic: BluetoothGattCharacteristic

    //命令Characteristic
    private lateinit var mCommandGattCharacteristic: BluetoothGattCharacteristic

    //GattDescriptor
    private lateinit var mGattDescriptor: BluetoothGattDescriptor

    //BLE广播操作类
    private var mBluetoothLeAdvertiser: BluetoothLeAdvertiser? = null

    //蓝牙广播回调类
    private lateinit var mAdvCallback: AdvCallback

    //广播设置(必须)
    private lateinit var mAdvertiseSettings: AdvertiseSettings

    //广播数据(必须，广播启动就会发送)
    private lateinit var mAdvertiseData: AdvertiseData

    //扫描响应数据(可选，当客户端扫描时才发送)
    private lateinit var mScanResponseData: AdvertiseData

    //GattServer回调
    private lateinit var mBluetoothGattServerCallback: BluetoothGattServerCallback

    //GattServer
    private var mBluetoothGattServer: BluetoothGattServer? = null

    public var device: BluetoothDevice? = null

    var sender: ISender? = null

    constructor(context: Context, serverCallback: IServerCallback) {
        this.context = context
        this.serverCallback = serverCallback
        Log.d(TAG, "GattServer init")
        Log.d(BtTrack.TAG, "peripheral GattServer init")
        initBluetooth()
        initListener()
    }

    fun createSender(context: Context): ISender {
        sender = GattSender(context)
        return sender!!
    }


    private fun initListener() {
        Log.d(BtTrack.TAG, "peripheral initListener")
//        startAdvert()
        addGattServer()
    }

    @SuppressLint("MissingPermission")
    private fun startAdvert() {
        Log.d(TAG, "开始Ble广播")
        mBluetoothAdapter.name = "RCLive"
        Log.d(BtTrack.TAG, "peripheral startAdvert ${mBluetoothAdapter.name}")

        //初始化广播设置
        mAdvertiseSettings = AdvertiseSettings.Builder()
            //设置广播模式，以控制广播的功率和延迟。 ADVERTISE_MODE_LOW_LATENCY为高功率，低延迟
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            //设置蓝牙广播发射功率级别
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            //广播时限。最多180000毫秒。值为0将禁用时间限制。（不设置则为无限广播时长）
            .setTimeout(mTime)
            //设置广告类型是可连接还是不可连接。
            .setConnectable(true)
            .build()

        //设置广播报文
        mAdvertiseData = AdvertiseData.Builder()
            //设置广播包中是否包含设备名称。
            .setIncludeDeviceName(true)
            //设置广播包中是否包含发射功率
            .setIncludeTxPowerLevel(true)
            .build()

        //设置广播扫描响应报文(可选)
        mScanResponseData = AdvertiseData.Builder()
            //设备厂商自定义数据，将其转化为字节数组传入
            .addManufacturerData(0x36, "RCLive".toByteArray())
            .setIncludeTxPowerLevel(true)
            .setIncludeDeviceName(true)
            .build()

        //获取BLE广播操作对象
        //官网建议获取mBluetoothLeAdvertiser时，先做mBluetoothAdapter.isMultipleAdvertisementSupported判断，
        // 但部分华为手机支持Ble广播却还是返回false,所以最后以mBluetoothLeAdvertiser是否不为空且蓝牙打开为准
        mBluetoothLeAdvertiser = mBluetoothAdapter.bluetoothLeAdvertiser
        if (mBluetoothLeAdvertiser != null && mBluetoothAdapter.isEnabled) {
            Log.d(
                TAG,
                "mBluetoothLeAdvertiser != null = ${mBluetoothLeAdvertiser != null} " +
                        "mBluetoothAdapter.isMultipleAdvertisementSupported = ${mBluetoothAdapter.isMultipleAdvertisementSupported}"
            )
            //开始广播（不附带扫描响应报文）
            //mBluetoothLeAdvertiser.startAdvertising(mAdvertiseSettings, mAdvertiseData, mAdvertiseCallback)
            //开始广播（附带扫描响应报文）
            mBluetoothLeAdvertiser?.startAdvertising(
                mAdvertiseSettings, mAdvertiseData,
                mScanResponseData, mAdvCallback
            )
        }
    }


    private fun stopAdvertising() {
        mBluetoothLeAdvertiser?.let { advertiser ->
            advertiser.stopAdvertising(mAdvCallback)
            Log.d(TAG, "停止Ble广播")
        }
    }


    private fun addGattServer() {
        Log.d(BtTrack.TAG, "peripheral add GattService")

        //--Command
        mCommandGattCharacteristic = BluetoothGattCharacteristic(
            GattUUIDS.UUID_COMMAND_CHARACTERISTIC,
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                    BluetoothGattCharacteristic.PROPERTY_READ,
            (BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ)
        )
        mCommandGattCharacteristic.value = byteArrayOf(0x77, (0x88).toByte(), (0x99).toByte())
        //初始化描述
        mGattDescriptor = BluetoothGattDescriptor(
            GattUUIDS.UUID_DESCRIPTOR,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        mGattDescriptor.setValue(byteArrayOf(12, 33, 54, 45))
        //Service添加特征值
        mGattService.addCharacteristic(mCommandGattCharacteristic)
        //特征值添加描述
        mCommandGattCharacteristic.addDescriptor(mGattDescriptor)
        //--Command

        //--Data
        mDataGattCharacteristic = BluetoothGattCharacteristic(
            GattUUIDS.UUID_DATA_CHARACTERISTIC,
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                    BluetoothGattCharacteristic.PROPERTY_READ,
            (BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ)
        )
        mDataGattCharacteristic.value = byteArrayOf(0x11, 0x22, 0x33)
        //初始化描述
        mGattDescriptor = BluetoothGattDescriptor(
            GattUUIDS.UUID_DESCRIPTOR,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        mGattDescriptor.setValue(byteArrayOf(12, 33, 54, 45))
        //Service添加特征值
        mGattService.addCharacteristic(mDataGattCharacteristic)
        //特征值添加描述
        mDataGattCharacteristic.addDescriptor(mGattDescriptor)
        //--Data


        //初始化GattServer回调
        mBluetoothGattServerCallback = GattServerCallback()
        //添加服务
        mBluetoothGattServer =
            mBluetoothManager.openGattServer(context, mBluetoothGattServerCallback)
        mBluetoothGattServer?.removeService(mGattService)
        mBluetoothGattServer?.addService(mGattService)
    }

    private fun notifyConnectOKOrDebugBandwidthOK(isDebugBandWidth: Boolean = false){
        if (isDebugBandWidth) {
            Log.d(BtTrack.TAG,"Peripheral send DEBUG_BANDWIDTH_OK")
            notify(GattCommandNotify.DEBUG_BANDWIDTH_OK + "-" + (if (sender?.getAddress() != null) sender?.getAddress() else ""))
        } else {
            Log.d(BtTrack.TAG,"Peripheral send CONNECT_OK")
            notify(GattCommandNotify.CONNECT_OK + "-" + (if (sender?.getAddress() != null) sender?.getAddress() else ""))
        }
    }

    private fun initSender(dataConnectType: Int, isDebugBandWidth: Boolean = false) {
        val createSenderCallback = object:AbstractSender.ICreateSenderCallback {
            override fun callback(address: String?) {
                address?.let {
                    val split = it.split("-")
                    if (GattCommandNotify.PERIPHERAL_ERROR == split[0]) {
                        Log.d(BtTrack.TAG,"Peripheral send PERIPHERAL_ERROR")
                        notify(address)
                        return
                    }
                }
                notifyConnectOKOrDebugBandwidthOK(isDebugBandWidth)
            }
        }
        sender?.destroy()
        sender = createSender(dataConnectType, createSenderCallback)
        sender?.createBuffer()
        sender?.startSendData()
        if (sender?.getCreateCallback() == null) {
            notifyConnectOKOrDebugBandwidthOK(isDebugBandWidth)
        }
    }
    protected fun createSender(
        dataConnectType: Int,
        createSenderCallback: AbstractSender.ICreateSenderCallback
    ): ISender {
        return if (dataConnectType == DataConnectType.SPP_STREAM) {
            SppSender(context, true)
        } else if (dataConnectType == DataConnectType.SPP_BUFFER) {
            SppSender(context, false)
        } else if (dataConnectType == DataConnectType.GATT_OVER_BREDR) {
            createSender(context)
        } else if (dataConnectType == DataConnectType.WIFI_AP) {
            WifiAPSender(context, false, createSenderCallback)
        } else if (dataConnectType == DataConnectType.WIFI_P2P) {
            WifiP2PSender(context, false, createSenderCallback)
        } else {
            throw RuntimeException("not support dataConnectType:$dataConnectType")
        }
    }

    private fun initBluetooth() {
        Log.d(BtTrack.TAG, "peripheral initBluetooth")
        //初始化ble设配器
        mBluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = mBluetoothManager.adapter
        //初始化蓝牙回调包
        mAdvCallback = AdvCallback()
    }

    private inner class AdvCallback : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            Log.d(
                TAG, "AdvertiseCallback onStartSuccess"
                        + " isConnectable:" + settingsInEffect?.isConnectable
                        + " timeout:" + settingsInEffect?.timeout)
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.d(TAG, "AdvertiseCallback onStartFailure errorCode:$errorCode")
            if (errorCode == ADVERTISE_FAILED_DATA_TOO_LARGE) {
                Log.d(TAG, "启动Ble广播失败 数据报文超出31字节")
            }
        }
    }

    private inner class GattServerCallback : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            Log.d(
                TAG, "onConnectionStateChange"
                        + " status:" + status
                        + " newState:" + newState
            )

            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTING -> {
                            Log.d(
                                BtTrack.TAG,
                                "Peripheral onConnectionStateChange STATE_CONNECTING  ${device?.name} ${device?.address} $status $newState"
                            )
                            Log.e(TAG, "onConnectionStateChange 连接成功")
                        }

                        BluetoothProfile.STATE_CONNECTED -> {
                            Log.d(
                                BtTrack.TAG,
                                "Peripheral onConnectionStateChange STATE_CONNECTED  ${device?.name} ${device?.address} $status $newState"
                            )
                            Log.d(TAG, "onConnectionStateChange 连接成功")
                            this@GattServer.device = device
                        }

                        BluetoothProfile.STATE_DISCONNECTING -> {
                            Log.d(
                                BtTrack.TAG,
                                "Peripheral onConnectionStateChange STATE_DISCONNECTING  ${device?.name} ${device?.address} $status $newState"
                            )
                            Log.e(TAG, "onConnectionStateChange 连接成功")
                        }

                        BluetoothProfile.STATE_DISCONNECTED -> {
                            Log.e(
                                BtTrack.TAG,
                                "Peripheral onConnectionStateChange STATE_DISCONNECTED  ${device?.name} ${device?.address} $status $newState"
                            )
                            Log.d(TAG, "onConnectionStateChange 断开连接")
                            this@GattServer.device = null
                            mBluetoothGattServer?.clearServices()
                            mBluetoothGattServer?.close()
                            mBluetoothGattServer = null
                            addGattServer()
                            sender?.destroy()
                            sender = null
                            serverCallback?.onDisonnect()
                        }

                        else -> {
                            Log.e(
                                BtTrack.TAG,
                                "Peripheral onConnectionStateChange STATE_OTHER  ${device?.name} ${device?.address} $status $newState"
                            )
                        }
                    }
                }
                else -> {
                    Log.e(
                        BtTrack.TAG,
                        "Peripheral onConnectionStateChange GATT_OTHER  ${device?.name} ${device?.address} $status $newState"
                    )

                }
            }
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            super.onServiceAdded(status, service)
            Log.d(
                TAG, "onServiceAdded"
                        + " status:" + status
                        + " uud:" + service?.uuid)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(BtTrack.TAG, "Peripheral onServiceAdded success $status ${service?.uuid}")
            } else {
                Log.e(BtTrack.TAG, "Peripheral onServiceAdded fail $status ${service?.uuid}")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)

            Log.d(
                TAG, "onCharacteristicReadRequest device:" + device
                        + " requestId:" + requestId
                        + " offset:" + offset
                        + " characteristic_uuid:" + characteristic?.uuid)

            Log.d(
                TAG,
                "${device?.address} 请求读取特征值:  UUID = ${characteristic?.uuid} " +
                    "读取值 = ${(characteristic?.value)}")

            mBluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,
                offset, "888".toByteArray())

        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            super.onCharacteristicWriteRequest(
                device,
                requestId,
                characteristic,
                preparedWrite,
                responseNeeded,
                offset,
                value
            )
            Log.d(
                TAG, "onCharacteristicWriteRequest device:" + device
                        + " writeType:" + characteristic?.writeType
                        + " value:" + if (value == null) null else String(value)
                        + " requestId:" + requestId
                        + " characteristic_uuid:" + characteristic?.uuid
                        + " preparedWrite:" + preparedWrite
                        + " responseNeeded:" + responseNeeded
                        + " offset:" + offset

            )

            if (characteristic?.writeType == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) {
                mBluetoothGattServer?.sendResponse(
                    device, requestId, BluetoothGatt.GATT_SUCCESS,
                    offset, "999".toByteArray()
                )
            }

            if (characteristic?.uuid?.equals(GattUUIDS.UUID_COMMAND_CHARACTERISTIC) == true) {
                value?.let {
                    val cmdValue = String(it)
                    if (cmdValue.isEmpty()) {
                        return
                    }
                    val cmd = cmdValue.substring(0, 1)
                    val params = cmdValue.substring(1)
                    when (cmd) {
                        //DEBUG_BANDWIDTH
                        GattCommand.DEBUG_BANDWIDTH -> {
                            var dataConnectType: Int = params.substring(0, 1).toInt()
                            var kbps = 1000
                            if (params.length > 2) {
                                kbps = params.substring(1).toInt()
                            }
                            Log.d(BtTrack.TAG, "Peripheral receive DEBUG_BANDWIDTH $cmdValue kbps:$kbps")
                            initSender(dataConnectType, true)
                            (sender as? AbstractSender)?.startDebugBandWidth(kbps)
                        }
                        //Connect
                        GattCommand.CONNECT -> {
                            Log.d(BtTrack.TAG,"Peripheral receive CONNECT $cmdValue")
                            var dataConnectType: Int = params.substring(0,1).toInt()
                            initSender(dataConnectType)
                            serverCallback?.onConnect(dataConnectType)
                        }

                        //open_camera
                        GattCommand.OPEN_CAMERA -> {
                            Log.d(BtTrack.TAG,"Peripheral receive OPEN_CAMERA $cmdValue")
                            serverCallback?.onOpenCamera(params)
                        }

                        //open_debug_bandwidth
                        GattCommand.OPEN_DEBUG_BANDWIDTH -> {
                            Log.d(BtTrack.TAG,"Peripheral receive OPEN_DEBUG_BANDWIDTH $cmdValue")
                            serverCallback?.openDebugBandwidth()
                        }

                        else -> {
                        }
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor?
        ) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor)
            Log.d(
                TAG, "onDescriptorReadRequest device:" + device
                        + " requestId:" + requestId
                        + " offset:" + offset
                        + " descriptor:" + descriptor?.uuid
            )

            mBluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,
                offset,descriptor?.value)
            Log.d(
                TAG,"${device?.address} 请求读取描述值:  UUID = ${descriptor?.uuid} " +
                    "读取值 = ${descriptor?.value}")
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            descriptor: BluetoothGattDescriptor?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            super.onDescriptorWriteRequest(
                device,
                requestId,
                descriptor,
                preparedWrite,
                responseNeeded,
                offset,
                value
            )
            Log.d(
                TAG,"${device?.address} 请求写入描述值:  UUID = ${descriptor?.uuid} " +
                        "写入值 = ${value}")

            //刷新描述值
            descriptor?.value = value
            // 响应客户端
            mBluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,
                offset, value)

        }

        override fun onExecuteWrite(device: BluetoothDevice?, requestId: Int, execute: Boolean) {
            Log.d(TAG,"onExecuteWrite $device $requestId $execute")
            super.onExecuteWrite(device, requestId, execute)
        }

        private var writeMs = 0L
        private var writeSize: Int = 0
        private var writeTotalSize: Long = 0L
        @SuppressLint("MissingPermission")
        override fun onNotificationSent(device: BluetoothDevice?, status: Int) {
            super.onNotificationSent(device, status)
            if(status != BluetoothGatt.GATT_SUCCESS){
                Log.e(
                    TAG,
                    "onNotificationSent device:" + device + " status:" + status)

                //todo retry逻辑
                return
            }


            //只有SppSender会自己发送数据,无需使用notifyCharacteristicChanged送数据
            if (sender !is AbstractGattSender) {
                return
            }

            sender?.let { sender ->
                sender.getReadHandler()!!.post {
                    if (writeMs == 0L) {
                        writeMs = SystemClock.uptimeMillis()
                    }
                    //不能太快,否则会失败，如果不在handler中执行要sleep(1)
//                    try {
//                        Thread.sleep(1)
//                    } catch (t: Throwable) {
//                        t.printStackTrace()
//                        return@post
//                    }

                    val data = sender.readBuffer()
                    val bytesSize = data?.size ?: 0

                    mDataGattCharacteristic.value = data
                    //回复客户端,让客户端读取该特征新赋予的值，获取由服务端发送的数据
                    val notifySuccess = try {
                        mBluetoothGattServer?.notifyCharacteristicChanged(
                            device,
                            mDataGattCharacteristic,
                            false
                        )
                    } catch (t: Throwable) {
                        t.printStackTrace()
                        false
                    }
                    if (notifySuccess != true) {
                        Log.e(
                            TAG,
                            "onNotificationSent notifySuccess:" + notifySuccess
                        )
                    } else {
                        writeSize += bytesSize
                        if (writeTotalSize > Long.MAX_VALUE - 10 * 1024 * 1024) {
                            writeTotalSize = 0
                        }
                        writeTotalSize += bytesSize
                        val deltaMs = SystemClock.uptimeMillis() - writeMs
                        if (deltaMs > 3000) {
                            val kbps = (writeSize / deltaMs * 1000.0 / 1000 * 8).toInt()
                            sender?.let {
                                if (it is AbstractGattSender) {
                                    it.senderCallback?.onUploadBandwidthChange(kbps, writeTotalSize)
                                }
                            }
                            Log.d(BtTrack.TAG, "onUploadBandwidthChange ${kbps}kbps total: ${(writeTotalSize/1000)}KB")
                            writeMs = SystemClock.uptimeMillis()
                            writeSize = 0
                            Log.d(
                                TAG,
                                "onNotificationSent notifySuccess:" + notifySuccess
                            )
                        }
                    }
                }
            }
        }

        override fun onMtuChanged(device: BluetoothDevice?, mtu: Int) {
            super.onMtuChanged(device, mtu)
            Log.d(TAG, "onMtuChanged $device $mtu")
        }

        override fun onPhyUpdate(device: BluetoothDevice?, txPhy: Int, rxPhy: Int, status: Int) {
            super.onPhyUpdate(device, txPhy, rxPhy, status)
            Log.d(TAG, "onPhyUpdate $device $txPhy $rxPhy $status")
        }

        override fun onPhyRead(device: BluetoothDevice?, txPhy: Int, rxPhy: Int, status: Int) {
            super.onPhyRead(device, txPhy, rxPhy, status)
            Log.d(TAG, "onPhyRead $device $txPhy $rxPhy $status")
        }
    }


    @SuppressLint("MissingPermission")
    fun notify(command: String): Boolean {
        Log.d(TAG, "notify command:$command device:$device")
        device?.let {
            mCommandGattCharacteristic.value = command.toByteArray()
            val success =
                mBluetoothGattServer?.notifyCharacteristicChanged(
                    it,
                    mCommandGattCharacteristic,
                    false
                )
            if (success != true) {
                Log.e(TAG, "notify() notifySuccess:$success $command")
            } else {
                Log.d(TAG, "notify() notifySuccess:$success $command")
            }
            return success ?: false
        }
        return false
    }

    @SuppressLint("MissingPermission")
    inner class GattSender(context:Context) : AbstractGattSender(context){

        override fun getAddress(): String? {
            return null
        }

        override fun getBufferSize(): Int {
            return 5000
        }

        override fun send() {
            if (device == null || mDataGattCharacteristic == null) {
                return
            }

            mDataGattCharacteristic!!.value = readBuffer()
            val notifySuccess =
                mBluetoothGattServer?.notifyCharacteristicChanged(
                    device,
                    mDataGattCharacteristic,
                    false
                )

            //notifySuccess 状态不可靠
            //即时返回true，也有可能onNotificationSent status != BluetoothGatt.GATT_SUCCESS
            if (notifySuccess == true) {
                Log.d(
                    TAG,
                    "send() notifySuccess:" + notifySuccess
                )
            } else {
                Log.e(
                    TAG,
                    "send() notifySuccess:" + notifySuccess
                )
            }
        }
    }

}