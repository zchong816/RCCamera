package net.vicp.zchong.live.central.setting

import android.media.MediaCodecInfo
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import net.vicp.zchong.live.central.CentralApplication
import net.vicp.zchong.live.common.DataConnectType
import net.vicp.zchong.live.common.ModelUtils
import org.json.JSONObject

/**
 * @author zhangchong
 * @date 2024/8/24 09:09
 */
object LiveSettingHelper {

    fun getCarmerParams(): String {
        var configJson = JSONObject()
        val sp = CentralApplication.getContext()
            .getSharedPreferences("LIVE", AppCompatActivity.MODE_PRIVATE)
        val liveConfigValue = sp.getString("live_config", null)
        if (liveConfigValue != null) {
            try {
                configJson = JSONObject(liveConfigValue)
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
        var params = ""

        var size = "w=864&h=480"
        if (configJson.has("size")) {
            size = configJson.getString("size")
        }
        when (size.uppercase()) {
            "480P" -> {
                size = "w=864&h=480"
            }

            "720P" -> {
                size = "w=1280&h=720"
            }

            "1080P" -> {
                size = "w=1920&h=1080"
            }

            else -> {
                size = "w=864&h=480"
            }
        }
        params += size
        params += "&"

        var vb = 1200
        if (configJson.has("vb")) {
            vb = configJson.getInt("vb")
        }
        params += "vb=$vb"
        params += "&"

        var fps = 24
        if (configJson.has("fps")) {
            fps = configJson.getInt("fps")
        }
        params += "fps=$fps"
        params += "&"

        var pf = MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline
        if (configJson.has("pf")) {
            pf = configJson.getInt("pf")
        }
        params += "pf=$pf&lv=${MediaCodecInfo.CodecProfileLevel.AVCLevel4}"
        params += "&"

//        if (ModelUtils.isLawkS1) {
            params += "cs=1&rh=1&vs=1&ifi=3&r=0"
//        } else {
//            params += "cs=0&rh=0&vs=0&ifi=3&r=0"
//        }

        return params
    }

    fun getBandwidth(): Int {
        var configJson = JSONObject()
        val sp = CentralApplication.getContext()
            .getSharedPreferences("LIVE", AppCompatActivity.MODE_PRIVATE)
        val liveConfigValue = sp.getString("live_config", null)
        var vb = 1200
        if (liveConfigValue != null) {
            try {
                configJson = JSONObject(liveConfigValue)
            } catch (t: Throwable) {
                t.printStackTrace()
            }

            if (configJson.has("vb")) {
                vb = configJson.getInt("vb")
            }
        }
        return vb
    }


    fun getFps(): Int {
        var configJson = JSONObject()
        val sp = CentralApplication.getContext()
            .getSharedPreferences("LIVE", AppCompatActivity.MODE_PRIVATE)
        val liveConfigValue = sp.getString("live_config", null)
        var fps = 24
        if (liveConfigValue != null) {
            try {
                configJson = JSONObject(liveConfigValue)
            } catch (t: Throwable) {
                t.printStackTrace()
            }

            if (configJson.has("fps")) {
                fps = configJson.getInt("fps")
            }
        }
        return fps
    }

    fun getPreviewSize(): Pair<Int, Int> {
        var configJson = JSONObject()
        val sp = CentralApplication.getContext()
            .getSharedPreferences("LIVE", AppCompatActivity.MODE_PRIVATE)
        val liveConfigValue = sp.getString("live_config", null)
        var w = 864
        var h = 480
        if (liveConfigValue != null) {
            try {
                configJson = JSONObject(liveConfigValue)
            } catch (t: Throwable) {
                t.printStackTrace()
            }
            var size = "480P"
            if (configJson.has("size")) {
                size = configJson.getString("size")
            }
            when (size.uppercase()) {
                "480P" -> {
                    w = 864
                    h = 480
                }

                "720P" -> {
                    w = 1280
                    h = 720
                }

                "1080P" -> {
                    w = 1920
                    h = 1080
                }

                else -> {

                }
            }
        }
        return Pair(w, h)
    }

    fun getDataConnectType(): Int {
        var configJson = JSONObject()
        val sp = CentralApplication.getContext()
            .getSharedPreferences("LIVE", AppCompatActivity.MODE_PRIVATE)
        val liveConfigValue = sp.getString("live_config", null)
        if (liveConfigValue != null) {
            try {
                configJson = JSONObject(liveConfigValue)
            } catch (t: Throwable) {
                t.printStackTrace()
            }

            var trans = "spp_buffer"
            if (configJson.has("trans")) {
                trans = configJson.getString("trans")
            }
            when (trans.lowercase()) {
                "spp_stream" -> {
                    return DataConnectType.SPP_STREAM
                }

                "spp_buffer" -> {
                    return DataConnectType.SPP_BUFFER
                }

                "gatt" -> {
                    return DataConnectType.GATT_OVER_BREDR
                }

                "p2p" -> {
                    return DataConnectType.WIFI_P2P
                }

                "ap" -> {
                    return DataConnectType.WIFI_AP
                }

                "core" -> {
                    return DataConnectType.CORE
                }

                else -> {
                    return DataConnectType.DEFAULT
                }
            }
        }
        return DataConnectType.DEFAULT
    }


    fun getSampleRate(): Int {
        var configJson = JSONObject()
        val sp = CentralApplication.getContext()
            .getSharedPreferences("LIVE", AppCompatActivity.MODE_PRIVATE)
        val liveConfigValue = sp.getString("live_config", null)
        var sample = 44100
        if (liveConfigValue != null) {
            try {
                configJson = JSONObject(liveConfigValue)
            } catch (t: Throwable) {
                t.printStackTrace()
            }

            if (configJson.has("sr")) {
                sample = configJson.getInt("sr")
            }
        }
        return sample
    }


    fun getChannelCount(): Int {
        var configJson = JSONObject()
        val sp = CentralApplication.getContext()
            .getSharedPreferences("LIVE", AppCompatActivity.MODE_PRIVATE)
        val liveConfigValue = sp.getString("live_config", null)
        var channelCount = 1
        if (liveConfigValue != null) {
            try {
                configJson = JSONObject(liveConfigValue)
            } catch (t: Throwable) {
                t.printStackTrace()
            }

            if (configJson.has("ac")) {
                channelCount = configJson.getInt("ac")
            }
        }
        return channelCount
    }


}