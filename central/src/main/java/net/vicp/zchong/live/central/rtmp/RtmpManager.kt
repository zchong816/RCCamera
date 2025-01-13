package net.vicp.zchong.live.central.rtmp

import android.media.MediaCodec
import android.util.Log
import com.pedro.common.ConnectChecker
import com.pedro.library.util.streamclient.RtmpStreamClient
import com.pedro.rtmp.rtmp.RtmpClient
import java.nio.ByteBuffer

/**
 * @author zhangchong
 * @date 2024/8/28 17:59
 */
class RtmpManager : ConnectChecker {
    private val rtmpClient: RtmpClient
    private val streamClient: RtmpStreamClient

    init {
        Log.d(TAG, "RtmpManager()")
        rtmpClient = RtmpClient(this)
        streamClient = RtmpStreamClient(rtmpClient, null)
    }

    fun getStreamClient(): RtmpStreamClient {
        Log.d(TAG, "getStreamClient")
        return streamClient
    }

    fun prepareAudioRtp(isStereo: Boolean, sampleRate: Int) {
        Log.d(TAG, "prepareAudioRtp isStereo:$isStereo sampleRate:$sampleRate")
        rtmpClient.setAudioInfo(sampleRate, isStereo)
    }

    fun startStreamRtp(url: String, w: Int, h: Int, fps: Int) {
        Log.d(TAG, "startStreamRtp url:$url w:$w h:$h fps:$fps")
        rtmpClient.setVideoResolution(w, h)
        rtmpClient.setFps(fps)
        rtmpClient.connect(url)
    }

    protected fun stopStreamRtp() {
        Log.d(TAG, "stopStreamRtp")
        rtmpClient.disconnect()
    }

    fun getAacDataRtp(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        Log.d(TAG, "getAacDataRtp $aacBuffer $info")
        rtmpClient.sendAudio(aacBuffer, info)
    }

    fun onSpsPpsVpsRtp(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer) {
        Log.d(TAG, "onSpsPpsVpsRtp $sps $pps $vps")
        rtmpClient.setVideoInfo(sps, pps, vps)
    }

    fun getH264DataRtp(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        Log.d(TAG, "getH264DataRtp $h264Buffer $info")
        rtmpClient.sendVideo(h264Buffer, info)
    }

    fun destroy() {
        Log.d(TAG, "destroy")
        try {
            rtmpClient.disconnect()
            rtmpClient.clearCache()
            rtmpClient.closeConnection()
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    override fun onAuthError() {
        Log.d(TAG, "onAuthError")
    }

    override fun onAuthSuccess() {
        Log.d(TAG, "onAuthSuccess")
    }

    override fun onConnectionFailed(s: String) {
        Log.d(TAG, "onConnectionFailed $s")
    }

    override fun onConnectionStarted(s: String) {
        Log.d(TAG, "onConnectionStarted $s")
    }

    override fun onConnectionSuccess() {
        Log.d(TAG, "onConnectionSuccess")
    }

    override fun onDisconnect() {
        Log.d(TAG, "onDisconnect")
    }

    override fun onNewBitrate(l: Long) {
        Log.d(TAG, "onNewBitrate $l")
    }

    companion object {
        private const val TAG = "RtmpManager"
    }
}
