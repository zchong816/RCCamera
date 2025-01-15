package net.vicp.zchong.live.central.client.gatt


import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import net.vicp.zchong.live.central.IErrorCallback
import net.vicp.zchong.live.central.client.gatt.BluetoothUtils.Companion.bytesToHexString
import net.vicp.zchong.live.central.utils.ToastUtils
import net.vicp.zchong.live.common.BtTrack
import net.vicp.zchong.live.common.DataConnectType
import net.vicp.zchong.live.common.GattCommand
import net.vicp.zchong.live.common.GattCommandNotify
import net.vicp.zchong.live.common.GattUUIDS
import java.util.*
import kotlin.math.roundToInt

/**
 * @author zhangchong
 * @date 2024/8/16 15:51
 */


//Gatt Service列表
val mGattServiceList = ArrayList<BluetoothGattService>()


@SuppressLint("MissingPermission")
class BleGattHelper(var mContext: Context, val macAddress: String) {

    var TAG = "BleGattHelper"

    //蓝牙设配器
    private lateinit var mBluetoothAdapter: BluetoothAdapter

    //原生蓝牙对象
    private lateinit var mBluetoothDevice: BluetoothDevice

    //蓝牙管理类
    private lateinit var mBluetoothManager: BluetoothManager

    //标记是否连接
    var isConnected = false

    //标记重置次数
    private var retryCount = 0

    private val maxRetryCount = 1

    //蓝牙Gatt回调
    private lateinit var mGattCallback: GattCallback

    //蓝牙Gatt
    private lateinit var mBluetoothGatt: BluetoothGatt

    //工作子线程Handler
    private var mHandler: Handler

    //回调监听
    private var mListener: BleConnectionListener? = null

    var errorCallback: IErrorCallback? = null

    private var mDataCharacteristic: BluetoothGattCharacteristic? = null

    private var mCommandCharacteristic: BluetoothGattCharacteristic? = null

    init {
        mContext = mContext.applicationContext
        val handlerThread = HandlerThread("BleConnection")
        handlerThread.start()
        //初始化工作线程Handler
        mHandler = Handler(handlerThread.looper)
        initBluetooth()
    }

    /**
     * 初始化蓝牙
     */
    private fun initBluetooth() {
        Log.d(TAG, "initBluetooth")
        //初始化ble设配器
        mBluetoothManager = mContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = mBluetoothManager.adapter


        //芯片组是否支持 LE 2M PHY 功能
        val isLe2MPhySupported = mBluetoothAdapter.isLe2MPhySupported()
        Log.d(TAG, "isLe2MPhySupported: $isLe2MPhySupported")

        mGattCallback = GattCallback()
    }

    /**
     * 连接
     */

    fun connection() {
        Log.d(TAG, "connection isConnected: $isConnected")
        Log.d(BtTrack.TAG, "Central connection $macAddress $isConnected")
        if (!isConnected) {
            //获取原始蓝牙设备对象进行连接
            mBluetoothDevice = mBluetoothAdapter.getRemoteDevice(macAddress)
            //重置连接重试次数
            retryCount = 0
            //连接设备
            mHandler.post {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    mBluetoothGatt = mBluetoothDevice.connectGatt(
                        mContext,
                        false,
                        mGattCallback,
                        BluetoothDevice.TRANSPORT_BREDR,
                        BluetoothDevice.PHY_LE_2M_MASK
                    )
                } else {
                    mBluetoothGatt = mBluetoothDevice.connectGatt(mContext, false, mGattCallback)
                }
//                BluetoothUtils().refreshDeviceCache(mBluetoothGatt)
            }
        }
    }

    /**
     * 发现服务
     */

    private fun discoverServices() {
        Log.d(TAG, "discoverServices")
        if (isConnected) {
            mHandler.postDelayed({
                Log.d(BtTrack.TAG, "Central begin discoverServices")
                //发现服务
                mBluetoothGatt.discoverServices()
            }, 500)
        }
    }

    fun writeCommandCharacteristic(byteArray: ByteArray):Boolean{
        mCommandCharacteristic?.let {
            mCommandCharacteristic?.value = byteArray
            return writeCharacteristic(mCommandCharacteristic!!,byteArray)
        }
        Log.d(TAG, "writeCommandCharacteristic success: false mCommandCharacteristic is null")
        return false
    }

    /**
     * 写入特征
     */

    fun writeCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        byteArray: ByteArray
    ): Boolean {

        //在发送之前，先设置特征通知.
        //mBluetoothGatt.setCharacteristicNotification(characteristic, true)
        //官网上还有一个将该特征下的描述设置为 通知数值 的步骤，有些硬件需要有些不需要，看具体情况
//        val descriptor = characteristic.getDescriptor(UUID.fromString(GattUUIDS.NotificationDescriptorUUID))
//        descriptor.let {
//            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
//            mBluetoothGatt.writeDescriptor(descriptor)
//        }

        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        mBluetoothGatt.setCharacteristicNotification(characteristic, true)
        characteristic.value = byteArray
        val success = mBluetoothGatt.writeCharacteristic(characteristic)
        if (!success) {
            Log.e(TAG, "writeCharacteristic fail.... len:" + characteristic.value.size)
        } else {
            Log.d(TAG, "writeCharacteristic " + String(byteArray))
        }
        return success
    }

    /**
     * 设置特征通知
     */

    fun setCharacteristicNotification(characteristic: BluetoothGattCharacteristic) {
        mBluetoothGatt.setCharacteristicNotification(characteristic, true)
    }


    /**
     * 断开连接，会触发onConnectionStateChange回调，在onConnectionStateChange回调中调用mBluetoothGatt.close()
     */
    fun disConnection() {
        Log.d(TAG, "disConnection isConnected:$isConnected")
        if (isConnected) {
            isConnected = false
            mBluetoothGatt.disconnect()
        }
    }

    /**
     * 彻底关闭连接，不带onConnectionStateChange回调的
     */
    private fun closeConnection() {
        Log.d(TAG, "closeConnection isConnected:$isConnected retryCount:$retryCount")
        if (retryCount >= maxRetryCount) {
            Toast.makeText(mContext, "请检查外或重新配对蓝牙", Toast.LENGTH_LONG).show()
            mHandler.postDelayed({
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                mContext.startActivity(intent)
            }, 2000)
        }
        if (isConnected) {
            isConnected = false
            mBluetoothGatt.disconnect()
            //调用close()后，连接时传入callback会被置空，无法得到断开连接时onConnectionStateChange（）回调
            mBluetoothGatt.close()
        }
    }

    /**
     * 尝试重连
     */
    private fun tryReConnection() {
        Log.d(TAG, "tryReConnectio retryCount:$retryCount")
        retryCount++
        //之前尝试连接不成功，先关闭之前的连接
        closeConnection()
        //延迟500ms再重新尝试连接
        mHandler.postDelayed({
            //获取原始蓝牙设备对象进行连接
            mBluetoothDevice = mBluetoothAdapter.getRemoteDevice(macAddress)
            //连接设备
            mHandler.post {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    mBluetoothGatt = mBluetoothDevice.connectGatt(
                        mContext, false,
                        mGattCallback, BluetoothDevice.TRANSPORT_BREDR,
                        BluetoothDevice.PHY_LE_2M_MASK
                    )
                } else {
                    mBluetoothGatt = mBluetoothDevice.connectGatt(mContext, false, mGattCallback)
                }
            }
        }, 500)
    }

    /**
     * 处理连接状态改变
     */
    private fun connectionStateChange(status: Int, newState: Int) {
        Log.d(TAG, "connectionStateChange status = $status  newState = $newState")
        //断开连接或者连接成功时status = GATT_SUCCESS
        when (status) {
            BluetoothGatt.GATT_SUCCESS -> {
                //连接状态
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    isConnected = true
                    //连接成功回调
                    if (mListener != null) {
                        mListener?.onConnectionSuccess()
                    }
                    //发现服务
                    mHandler.postDelayed({
                        //发现服务
                        discoverServices()
                    }, 500)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    isConnected = false
                    //断开连接回调
                    if (mListener != null) {
                        mListener?.disConnection()
                    }
                    //断开连接状态
                    mBluetoothGatt.close()
                }
            }

            else -> {
                //遇到特殊情况尝试重连，增加连接成功率
                if (retryCount < maxRetryCount && !isConnected) {
                    //尝试重连
                    tryReConnection()
                } else {
                    mHandler.post {
                        //判断是否连接上
                        if (isConnected) {
                            //异常连接断开
                            if (mListener != null) {
                                mListener?.disConnection()
                            }
                        } else {
                            //连接失败回调
                            if (mListener != null) {
                                mListener?.onConnectionFail()
                            }
                        }
                    }
                    //断开连接
                    closeConnection()
                }
            }
        }
    }

    /**
     * 处理发现服务
     */
    private fun servicesDiscovered(status: Int) {
        Log.d(TAG, "servicesDiscovered status = $status")
        Log.d(BtTrack.TAG, "Central ServicesDiscovered $status")
        when (status) {
            BluetoothGatt.GATT_SUCCESS -> {
                //清空之前的Service列表
                mGattServiceList.clear()
                //重新设置Service列表
                mGattServiceList.addAll(mBluetoothGatt.getServices())

                mCommandCharacteristic = null
                mDataCharacteristic = null

                val services = mBluetoothGatt.getServices()

                Log.d(BtTrack.TAG, "Central ServicesDiscovered GATT_SUCCESS size:" + services.size)

                loop@ for (service in services) {
                    if (service.uuid.toString() == GattUUIDS.UUID_SERVICE.toString()) {
                        Log.e(TAG, "found service:" + service.uuid)
                        Log.e(BtTrack.TAG, "found service:" + service.uuid)
                    } else {
                        Log.d(TAG, "found service:" + service.uuid)
                        Log.d(BtTrack.TAG, "found service:" + service.uuid)
                    }
                    val characteristics = service.characteristics
                    for (characteristic in characteristics) {
                        if (characteristic.uuid.toString() == GattUUIDS.UUID_DATA_CHARACTERISTIC.toString()) {
                            Log.e(TAG, "found data characteristic:" + characteristic.uuid)
                            Log.e(BtTrack.TAG, "found data characteristic:" + characteristic.uuid)
                            mDataCharacteristic = characteristic
                            setCharacteristicNotification(mDataCharacteristic!!)
                        }
                        if (characteristic.uuid.toString() == GattUUIDS.UUID_COMMAND_CHARACTERISTIC.toString()) {
                            Log.e(TAG, "found command characteristic:" + characteristic.uuid)
                            Log.e(BtTrack.TAG, "found command characteristic:" + characteristic.uuid)
                            mCommandCharacteristic = characteristic
                            setCharacteristicNotification(mCommandCharacteristic!!)
                        }
//                        if (mCommandCharacteristic != null && mDataCharacteristic != null) {
//                            break@loop
//                        }
                    }
                }


                if (mCommandCharacteristic == null) {
                    Log.e(BtTrack.TAG, "Central ServicesDiscovered not found:" + GattUUIDS.UUID_COMMAND_CHARACTERISTIC)

                }
                if (mDataCharacteristic == null) {
                    Log.e(BtTrack.TAG, "Central ServicesDiscovered not found:" + GattUUIDS.UUID_DATA_CHARACTERISTIC)
                }

                if (mCommandCharacteristic == null || mDataCharacteristic == null) {
                    disConnection()
                    mListener?.disConnection()
                    return
                }

                //发现服务回调
                if (mListener != null) {
                    mListener?.discoveredServices()
                }

                mHandler.post {
                    mCommandCharacteristic?.let {
                        setCharacteristicNotification(it)
                        val dataConnectType = mListener?.getDataConnectType() ?: DataConnectType.DEFAULT
                        val isDebugBrandWidth = mListener?.isDebugBandWidth() ?: false
                        val cmd:String = "${(if (isDebugBrandWidth) GattCommand.DEBUG_BANDWIDTH else GattCommand.CONNECT)}" +
                                "$dataConnectType" +
                                "${(if (isDebugBrandWidth)  mListener?.getDebugBandWidth() else "")}"



                        Log.d(BtTrack.TAG, "Central send CONNECT $cmd")
                        writeCharacteristic(
                            it, cmd.toByteArray())
                    }
                    mDataCharacteristic?.let {
                        setCharacteristicNotification(it)
                    }

                }
            }

            else -> {
                //发现服务失败
                ToastUtils.showBottomToast(mContext, "发现服务失败")
            }
        }
    }

    /**
     * 特征读取
     */
    private fun characteristicRead(status: Int, characteristic: BluetoothGattCharacteristic?) {
        when (status) {
            BluetoothGatt.GATT_SUCCESS -> {
                characteristic?.let { characteristic ->
                    val stringBuilder = StringBuilder()
                    val value = bytesToHexString(characteristic.value)
                    stringBuilder.append("特征读取 CharacteristicUUID = ${characteristic.uuid.toString()}")
                    stringBuilder.append(" ,特征值 = ${String(characteristic.value)}")
                    //读取特征值回调
                    if (mListener != null) {
                        mListener?.readCharacteristic(stringBuilder.toString())
                    }
                }
            }
            //无可读权限
            BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                //读取特征值回调
                if (mListener != null) {
                    mListener?.readCharacteristic("无读取权限")
                }
            }

            else -> {
                //读取特征值回调
                if (mListener != null) {
                    mListener?.readCharacteristic("特征读取失败 status = $status")
                }
            }
        }
    }

    /**
     * 特征写入
     */
    private fun characteristicWrite(status: Int, characteristic: BluetoothGattCharacteristic?) {
        when (status) {
            BluetoothGatt.GATT_SUCCESS -> {
                characteristic?.let { characteristic ->
                    val stringBuilder = StringBuilder()
                    stringBuilder.append("特征写入 CharacteristicUUID = ${characteristic.uuid.toString()}")
                    stringBuilder.append(" ,写入值 = ${bytesToHexString(characteristic.value)}")
                    //读取特征值回调
                    if (mListener != null) {
                        mListener?.writeCharacteristic(stringBuilder.toString())
                    }
                }
            }

            else -> {
                //读取特征值回调
                if (mListener != null) {
                    mListener?.writeCharacteristic("特征写入失败 status = $status")
                }
            }
        }
    }

    var sum: Long = 0
    var count: Long = 0
    var ms: Long = 0

    /**
     * 特征改变
     */
    private fun characteristicChanged(characteristic: BluetoothGattCharacteristic?) {
        characteristic?.let { characteristic ->
            val size = characteristic.value.size
            sum += size
            count += size
            val deltaMS = SystemClock.uptimeMillis() - ms
            if (deltaMS > 3000) {
                val info =
                    "speed:" + (count * 1.0f / deltaMS * 1000.0f / 1000.0f).roundToInt() + "KB/S " + (count * 1.0f / deltaMS * 1000.0f / 1000.0f * 8).roundToInt() + "kbps " + "len:" + size + " count:" + count + " sum:" + sum
                Log.d(
                    TAG, info + "\n"
                )
                ms = SystemClock.uptimeMillis()
                count = 0
            }
        }
    }

    /**
     * 描述写入
     */
    private fun descriptorWrite(status: Int, descriptor: BluetoothGattDescriptor?) {
        when (status) {
            BluetoothGatt.GATT_SUCCESS -> {
                descriptor?.let { descriptor ->
                    val stringBuilder = StringBuilder()
                    stringBuilder.append("描述写入 DescriptorUUID = ${descriptor.uuid.toString()}")
                    stringBuilder.append(" ,写入值 = ${bytesToHexString(descriptor.value)}")
                    //读取特征值回调
                    if (mListener != null) {
                        mListener?.writeDescriptor(stringBuilder.toString())
                    }
                }
            }

            else -> {
                //描述写入失败
                //读取特征值回调
                if (mListener != null) {
                    mListener?.writeDescriptor("描述写入失败 status = $status")
                }

            }
        }
    }

    /**
     * 描述读取
     */
    private fun descriptorRead(status: Int, descriptor: BluetoothGattDescriptor?) {
        when (status) {
            //操作成功
            BluetoothGatt.GATT_SUCCESS -> {
                descriptor?.let { descriptor ->
                    val value = bytesToHexString(descriptor.value)
                    val stringBuilder = StringBuilder()
                    stringBuilder.append("描述读取 DescriptorUUID = ${descriptor.uuid.toString()}")
                    stringBuilder.append(" ,描述值 = ${value}")
                    mHandler.post {
                        //读取特征值回调
                        if (mListener != null) {
                            mListener?.readDescriptor(stringBuilder.toString())
                        }
                        //设置并显示描述值
                    }
                }
            }
            //无可读权限
            BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                mHandler.post {
                    //读取特征值回调
                    if (mListener != null) {
                        mListener?.readDescriptor("无读取权限")
                    }
                }
            }

            else -> {
                mHandler.post {
                    //读取特征值回调
                    if (mListener != null) {
                        mListener?.readDescriptor("读取描述失败 status = $status")
                    }
                }
            }
        }
    }


    /**
     * Gatt回调
     */
    private inner class GattCallback : BluetoothGattCallback() {

        override fun onPhyUpdate(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
            super.onPhyUpdate(gatt, txPhy, rxPhy, status)
            Log.d(TAG, "onPhyUpdate gatt:$gatt txPhy:$txPhy rxPhy:$rxPhy status:$status")
        }

        override fun onPhyRead(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
            super.onPhyRead(gatt, txPhy, rxPhy, status)
            Log.d(TAG, "onPhyRead gatt:$gatt txPhy:$txPhy rxPhy:$rxPhy status:$status")
        }

        override fun onReliableWriteCompleted(gatt: BluetoothGatt?, status: Int) {
            super.onReliableWriteCompleted(gatt, status)
            Log.d(TAG, "onReliableWriteCompleted gatt:$gatt status:$status")
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
            super.onReadRemoteRssi(gatt, rssi, status)
            Log.d(TAG, "onReadRemoteRssi gatt:$gatt status:$status")
        }

        override fun onServiceChanged(gatt: BluetoothGatt) {
            super.onServiceChanged(gatt)
            Log.d(TAG, "onServiceChanged gatt:$gatt")
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            Log.d(TAG, "onMtuChanged gatt:$gatt mtu:$mtu status:$status")
        }

        //连接状态改变回调
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange gatt:$gatt status:$status newState:$newState")
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTING -> {
                            Log.e(
                                BtTrack.TAG,
                                "Central onConnectionStateChange GATT_SUCCESS STATE_CONNECTING ${gatt?.device?.name} ${gatt?.device?.address} $status $newState"
                            )
                        }

                        BluetoothProfile.STATE_CONNECTED -> {
                            Log.d(TAG,"requestConnectionPriority  CONNECTION_PRIORITY_HIGH")
                            mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                            Log.d(
                                BtTrack.TAG,
                                "Central onConnectionStateChange GATT_SUCCESS STATE_CONNECTED ${gatt?.device?.name} ${gatt?.device?.address} $status $newState"
                            )
                        }

                        BluetoothProfile.STATE_DISCONNECTING -> {
                            Log.e(
                                BtTrack.TAG,
                                "Central onConnectionStateChange GATT_SUCCESS STATE_DISCONNECTING ${gatt?.device?.name} ${gatt?.device?.address} $status $newState"
                            )
                        }

                        BluetoothProfile.STATE_DISCONNECTED -> {
                            Log.e(
                                BtTrack.TAG,
                                "Central onConnectionStateChange GATT_SUCCESS STATE_DISCONNECTED ${gatt?.device?.name} ${gatt?.device?.address} $status $newState"
                            )
                        }

                        else -> {
                            Log.e(
                                BtTrack.TAG,
                                "Central onConnectionStateChange GATT_SUCCESS STATE_OTHER ${gatt?.device?.name} ${gatt?.device?.address} $status $newState"
                            )
                        }
                    }
                }
                else -> {
                    Log.e(
                        BtTrack.TAG,
                        "Central onConnectionStateChange GATT_OTHER  ${gatt?.device?.name} ${gatt?.device?.address} $status $newState"
                    )
                }
            }
            super.onConnectionStateChange(gatt, status, newState)
            //抛给工作线程处理，尽快返回
            mHandler.post {
                connectionStateChange(status, newState)
            }
        }

        //服务发现回调
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            Log.d(BtTrack.TAG, "onServicesDiscovered gatt:$gatt status:$status name:${gatt?.device?.name} address:${gatt?.device?.address}")
            super.onServicesDiscovered(gatt, status)
            //抛给工作线程处理
            mHandler.post{
                servicesDiscovered(status)
            }
        }

        //特征读取回调
        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?, status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            Log.d(TAG, "onCharacteristicRead  gatt:$gatt status:$status  uuid:${characteristic?.uuid}")
            //抛给工作线程处理
            mHandler.post {
                characteristicRead(status, characteristic)
            }
        }


        //特征写入回调
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?, status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            Log.d(TAG, "onCharacteristicWrite status:" + status + " uuid:" + characteristic?.uuid + " " + String(characteristic?.value ?: ByteArray(0)))
        }

        var onCharacteristicChangedMs = SystemClock.uptimeMillis()
        //特征改变回调（主要由外设回调）
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            if (characteristic?.uuid == GattUUIDS.UUID_COMMAND_CHARACTERISTIC) {
                Log.d(
                    TAG, "onCharacteristicChanged "
                            + " value[0]:" + String.format("%02x", (characteristic?.value?.get(0) ?: 0))
                            + " value_string:" + String(characteristic?.value ?: ByteArray(0))
                            + " uuid:" + characteristic?.uuid)
            } else {
                if (SystemClock.uptimeMillis() - onCharacteristicChangedMs > 3000) {
                    onCharacteristicChangedMs = SystemClock.uptimeMillis()
                    Log.d(
                        TAG, "onCharacteristicChanged "
                                + " value[0]:" + String.format("%02x", (characteristic?.value?.get(0) ?: 0))
                                + " uuid:" + characteristic?.uuid)
                }
            }

            //抛给工作线程处理
            mHandler.post {
                characteristicChanged(characteristic)
            }

            characteristic?.let {
                when (it?.uuid){
                    //数据通道
                    GattUUIDS.UUID_DATA_CHARACTERISTIC->{
                        mListener?.characteristicChange(it.value)
                    }
                    //命令通道
                    GattUUIDS.UUID_COMMAND_CHARACTERISTIC -> {
                        it?.value?.let { characteristic ->
                            val command = String(characteristic).split("-")
                            when(command[0]){
                                GattCommandNotify.PERIPHERAL_ERROR -> {
                                    Log.d(BtTrack.TAG,"Central receive PERIPHERAL_ERROR")
                                    disConnection()
                                    errorCallback?.onPeripheralError(command[1].toInt())
                                }
                                GattCommandNotify.DEBUG_BANDWIDTH_OK -> {
                                    Log.d(BtTrack.TAG,"Central receive DEBUG_BANDWIDTH_OK")
                                    gatt?.let {
                                        mHandler.post {
                                            mListener?.onConnectOk(it.device, true,
                                                if (command.size > 1) command[1] else null
                                            )
                                        }
                                    }
                                }

                                GattCommandNotify.CONNECT_OK -> {
                                    Log.d(BtTrack.TAG, "Central receive CONNECT_OK")
                                    gatt?.let {
                                        mHandler.post {
                                            mListener?.onConnectOk(
                                                it.device,
                                                false,
                                                if (command.size > 1) command[1] else null
                                            )
                                        }
                                    }
                                }
                                GattCommandNotify.PERIPHERAL_PAGE_OPEN -> {
                                    Log.d(BtTrack.TAG,"Central receive CAMERA_OK")
                                    gatt?.let {
                                        mHandler.post {
                                            mListener?.onPeripheralPageOpen()
                                        }
                                    }
                                }
                                GattCommandNotify.CAMERA_CLOSE->{
                                    Log.d(BtTrack.TAG,"Central receive CAMERA_CLOSE")
                                    gatt?.let {
                                        mHandler.post {
                                            mListener?.onPeripheralPageClose()
                                        }
                                    }
                                }
                                else ->{}
                            }
                        }
                    }

                    else->{

                    }
                }



            }


        }

        //描述写入回调
        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)
            Log.d(TAG, "onDescriptorWrite status: " + status + " descriptor:"+descriptor?.toString()+" "+descriptor?.uuid)
            //抛给工作线程处理
            mHandler.post {
                descriptorWrite(status, descriptor)
            }
        }

        //描述读取回调
        override fun onDescriptorRead(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            super.onDescriptorRead(gatt, descriptor, status)
            Log.d(TAG, "onDescriptorRead status: " + status + " descriptor:"+descriptor?.toString()+" "+descriptor?.uuid)
            //抛给工作线程处理
            mHandler.post {
                descriptorRead(status, descriptor)
            }
        }
    }

    /**
     * 监听回调
     */
    interface BleConnectionListener {
        //连接成功
        fun onConnectionSuccess()

        //连接失败
        fun onConnectionFail()

        //断开连接
        fun disConnection()

        //发现服务
        fun discoveredServices()

        //读取特征值
        fun readCharacteristic(data: String)

        //写入特征值
        fun writeCharacteristic(data: String)

        //读取描述
        fun readDescriptor(data: String)

        //写入描述
        fun writeDescriptor(data: String)

        //写入特征值
        fun characteristicChange(data: ByteArray)

        fun onConnectOk(
            device: BluetoothDevice, isDebugBandWidth: Boolean = false,
            address: String? = null
        )

        fun onPeripheralPageOpen()

        fun onPeripheralPageClose()

        fun getCarmerParams(): String?
        fun getDataConnectType(): Int
        fun isDebugBandWidth(): Boolean
        fun getDebugBandWidth(): Int
    }

    fun setBleConnectionListener(listener: BleConnectionListener) {
        mListener = listener
    }

}