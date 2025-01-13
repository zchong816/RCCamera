package net.vicp.zchong.live.central.client.gatt

import android.bluetooth.BluetoothGatt
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import net.vicp.zchong.live.common.BtTrack

/**
 * @author zhangchong
 * @date 2024/8/16 15:54
 */
class BluetoothUtils {
    /**
     * 刷新缓存
     * @author daqi
     * @time 2019/3/25
     */
    fun refreshDeviceCache(bluetoothGatt: BluetoothGatt?): Boolean {
        if (bluetoothGatt != null) {
            try {
                val success = bluetoothGatt.javaClass
                    .getMethod("refresh", *arrayOfNulls(0))
                    .invoke(bluetoothGatt, *arrayOfNulls(0)) as Boolean
                Log.e(BtTrack.TAG, "refreshDeviceCache success:$success")
                return success
            } catch (localException: Exception) {
                Log.e(BtTrack.TAG, "refreshDeviceCache fial:$localException")
            }
        }
        return false
    }

    companion object {
        const val OpenBluetooth_Request_Code = 10086

        /**
         * 字节数组转十六进制字符串
         */
        fun bytesToHexString(src: ByteArray?): String? {
            if (src == null || src.size == 0) {
                return ""
            }
            val stringBuilder = StringBuilder("")
            if (src == null || src.size <= 0) {
                return null
            }
            for (i in src.indices) {
                val v = src[i].toInt() and 0xFF
                val hex = Integer.toHexString(v)
                if (hex.length < 2) {
                    stringBuilder.append(0)
                }
                stringBuilder.append(hex)
            }
            return stringBuilder.toString()
        }

        /**
         * 将字符串转成字节数组
         */
        fun hexStringToBytes(str: String): ByteArray {
            val abyte0 = ByteArray(str.length / 2)
            val s11 = str.toByteArray()
            for (i1 in 0 until s11.size / 2) {
                var byte1 = s11[i1 * 2 + 1]
                var byte0 = s11[i1 * 2]
                var s2: String
                abyte0[i1] = ((java.lang.Byte.decode(StringBuilder("0x".also { s2 = it }
                    .toString())
                    .append(String(byteArrayOf(byte0))).toString()).toInt() shl 4).toByte()
                    .also { byte0 = it }.toInt() xor
                        java.lang.Byte.decode(
                            StringBuilder(s2.toString())
                                .append(String(byteArrayOf(byte1))).toString()
                        ).also { byte1 = it }
                            .toInt()).toByte()
            }
            return abyte0
        }
    }
}
