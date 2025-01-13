package net.vicp.zchong.live.central

import android.app.Application
import android.content.Context
import android.util.Log
import com.bytedance.android.openlive.broadcast.BroadcastInitConfig
import com.bytedance.android.openlive.broadcast.DouyinBroadcastApi
import com.bytedance.android.openlive.broadcast.InitBroadcastListener


/**
 * @author zhangchong
 * @date 2024/8/19 08:57
 */
class CentralApplication : Application() {

    private val TAG = "CetrralApplication"

    companion object {
        private lateinit var appContext: Context
        fun getContext() = appContext
    }
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        initDouyinLive(this)
    }

    private fun initDouyinLive(application: Application) {
        Log.d(TAG, "initDouyinLive")
        //初始化 douyinsdk
        val builder: BroadcastInitConfig.Builder = BroadcastInitConfig.Builder(
            this, "514086",
            "openbroadcastappdemo", "2.0", 2L
        )

        builder.initBroadcastListener = object : InitBroadcastListener {
            override fun onInitializeSuccess() {
                Log.d(TAG,"插件初始化完成")

                if (DouyinBroadcastApi.isAuthorized()) {
                    Log.d(TAG,"已授权")
                } else {
                    Log.d(TAG,"未授权")
                }
            }

            override fun onInitializeFail(code: String) {
                Log.d(TAG,"插件初始化失败 $code")
            }
        }

        //初始化
        DouyinBroadcastApi.init(builder.build())


    }
}