package net.vicp.zchong.live.peripheral

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import net.vicp.zchong.live.common.BtTrack
import net.vicp.zchong.live.common.ModelUtils
import net.vicp.zchong.live.peripheral.BootCompletedReceiver.Companion.startGattService

class ServerActivity : AppCompatActivity() {
    private val PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH,
        )
    } else {
        arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH,
        )
    }

    private val PERMISSIONS_LWK_GLASS_S1 = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )

    private fun hasPermissions(context: Context): Boolean {
        Log.d(TAG, "model:" + Build.MODEL)
        Log.d(BtTrack.TAG, "model:" + Build.MODEL)
        if (ModelUtils.isLawkS1) {
            return hasPermissions(context, *PERMISSIONS_LWK_GLASS_S1)
        }
        return hasPermissions(context, *PERMISSIONS)
    }

    private fun hasPermissions(context: Context?, vararg permissions: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null && permissions != null) {
            for (permission in permissions) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        permission
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return false
                }
            }
        }
        return true
    }

    private fun requestPermissions() {
        Log.d(TAG, "requestPermissions SDK_INT:" + Build.VERSION.SDK_INT)
        ActivityCompat.requestPermissions(this, PERMISSIONS, 1)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(TAG, "onRequestPermissionsResult requestCode: $requestCode")
        var grant = true
        for (permission in permissions) {
            Log.d(TAG, "onRequestPermissionsResult permission: $permission")
        }
        for (grantResult in grantResults) {
            Log.d(TAG, "onRequestPermissionsResult grantResult: $grantResult")
            if (grantResult != 0) {
                grant = false
            }
        }
        if (grant) {
            init()
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server)
        checkPermissions()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    private fun init() {
        startGattService(application)
    }

    private fun checkPermissions() {
        val hasPermissions = hasPermissions(this)
        Log.d(TAG, "hasPermissions:$hasPermissions")
        if (!hasPermissions) {
            requestPermissions()
        } else {
            init()
        }
    }

    companion object {
        private const val TAG = "ServerActivity"
    }
}