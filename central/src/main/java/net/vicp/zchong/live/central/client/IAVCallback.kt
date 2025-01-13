package net.vicp.zchong.live.central.client

import net.vicp.zchong.live.central.av.Audio
import net.vicp.zchong.live.central.av.Video
import java.nio.ByteBuffer

interface IAVCallback {
    fun onAudio(data: ByteArray)
    fun onVideo(data: ByteArray)
}