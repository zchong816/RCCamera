package net.vicp.zchong.live.central.setting

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.media.MediaCodecInfo
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import net.vicp.zchong.live.central.CentralApplication
import net.vicp.zchong.live.central.R
import net.vicp.zchong.live.central.databinding.LiveSettingDialogBinding
import org.json.JSONObject

/**
 * @author zhangchong
 * @date 2024/8/12
 */
class LiveSettingDialog(val activity: Activity) : RadioGroup.OnCheckedChangeListener {

    companion object {
        private const val TAG = "LiveSettingDialog"
    }

    private var dialog: Dialog = Dialog(activity, R.style.setting_dialog)
    private var binding: LiveSettingDialogBinding =
        LiveSettingDialogBinding.inflate(activity.layoutInflater)
    private var onConfirmClickListener: View.OnClickListener? = null
    private var onCancelClickListener: View.OnClickListener? = null

    private var configJson: JSONObject = JSONObject()

    init {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(binding.root)
        dialog.setCancelable(true)
        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
            onCancelClickListener?.onClick(it)
        }
        binding.btnConfirm.setOnClickListener {
            dialog.dismiss()
            onConfirmClickListener?.onClick(it)
        }

        binding.rgSize.setOnCheckedChangeListener(this)
        binding.rgVb.setOnCheckedChangeListener(this)
        binding.rgFps.setOnCheckedChangeListener(this)

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
        var trans = "spp_buffer"
        if (configJson.has("trans")) {
            trans = configJson.getString("trans")
        }
        when (trans.lowercase()) {
            "spp_stream" -> {
                binding.transSppStream.isChecked = true
            }
            "spp_buffer" -> {
                binding.transSppBuffer.isChecked = true
            }
            "gatt" -> {
                binding.transGatt.isChecked = true
            }
            "p2p" -> {
                binding.transP2p.isChecked = true
            }
            "ap" -> {
                binding.transAp.isChecked = true
            }
            "core" -> {
                binding.transCore.isChecked = true
            }
            else -> {
                binding.transSppBuffer.isChecked = true
            }
        }

        var size = "480P"
        if (configJson.has("size")) {
            size = configJson.getString("size")
        }
        when (size.uppercase()) {
            "480P" -> {
                binding.size480p.isChecked = true
            }
            "720P" -> {
                binding.size720p.isChecked = true
            }
            "1080P" -> {
                binding.size1080p.isChecked = true
            }
            else -> {
                binding.size480p.isChecked = true
            }
        }

        var vb = 1200
        if (configJson.has("vb")) {
            vb = configJson.getInt("vb")
        }
        when (vb) {
            600 -> {
                binding.vb600k.isChecked = true
            }
            1200 -> {
                binding.vb1200k.isChecked = true
            }
            1500 -> {
                binding.vb1500k.isChecked = true
            }
            2000 -> {
                binding.vb2000k.isChecked = true
            }
            5000 -> {
                binding.vb5000k.isChecked = true
            }
            10000 -> {
                binding.vb10000k.isChecked = true
            }
            else -> {
                binding.vb1200k.isChecked = true
            }
        }

        var fps = 24
        if (configJson.has("fps")) {
            fps = configJson.getInt("fps")
        }
        when (fps) {
            20 -> {
                binding.fps20.isChecked = true
            }
            24 -> {
                binding.fps24.isChecked = true
            }
            30 -> {
                binding.fps30.isChecked = true
            }
            else -> {
                binding.fps24.isChecked = true
            }
        }

        var pf = MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline
        if (configJson.has("pf")) {
            pf = configJson.getInt("pf")
        }
        when (pf) {
            MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline -> {
                binding.pfBaseline.isChecked = true
            }
            MediaCodecInfo.CodecProfileLevel.AVCProfileMain -> {
                binding.pfMain.isChecked = true
            }
            MediaCodecInfo.CodecProfileLevel.AVCProfileHigh -> {
                binding.pfHigh.isChecked = true
            }
            else -> {
                binding.pfBaseline.isChecked = true
            }
        }

    }

    fun setOnConfirmClickListener(onClickListener: View.OnClickListener) {
        onConfirmClickListener = onClickListener
    }

    fun setOnCancelClickListener(onClickListener: View.OnClickListener) {
        onCancelClickListener = onClickListener
    }

    fun setTitle(title: String?) {
        binding.tvTitle.text = title
    }

    fun setCancelText(text: String?) {
        binding.btnCancel.text = text
    }

    fun setConfirmText(text: String?) {
        binding.btnConfirm.text = text
    }

    fun setContent(content: String?) {

    }

    fun dismiss() {
        dialog.dismiss()
    }

    fun save() {
        when (binding.rgTrans.checkedRadioButtonId) {
            binding.transSppStream.id -> {
                configJson.put("trans","spp_stream")
            }

            binding.transSppBuffer.id -> {
                configJson.put("trans","spp_buffer")
            }

            binding.transGatt.id -> {
                configJson.put("trans","gatt")
            }

            binding.transP2p.id -> {
                configJson.put("trans","p2p")
            }

            binding.transAp.id -> {
                configJson.put("trans","ap")
            }

            binding.transCore.id -> {
                configJson.put("trans","core")
            }
        }

        when (binding.rgSize.checkedRadioButtonId) {
            binding.size480p.id -> {
                configJson.put("size","480P")
            }

            binding.size720p.id -> {
                configJson.put("size","720P")
            }

            binding.size1080p.id -> {
                configJson.put("size","1080P")
            }
        }

        when (binding.rgVb.checkedRadioButtonId) {
            binding.vb600k.id -> {
                configJson.put("vb",600)
            }

            binding.vb1200k.id -> {
                configJson.put("vb",1200)
            }

            binding.vb1500k.id -> {
                configJson.put("vb",1500)
            }

            binding.vb2000k.id -> {
                configJson.put("vb",2000)
            }

            binding.vb5000k.id -> {
                configJson.put("vb",5000)
            }

            binding.vb10000k.id -> {
                configJson.put("vb",10000)
            }
        }

        when (binding.rgFps.checkedRadioButtonId) {
            binding.fps20.id -> {
                configJson.put("fps",20)
            }

            binding.fps24.id -> {
                configJson.put("fps",24)
            }

            binding.fps30.id -> {
                configJson.put("fps",30)
            }
        }

        when (binding.rgPf.checkedRadioButtonId) {
            binding.pfBaseline.id -> {
                configJson.put("pf", MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
            }

            binding.pfMain.id -> {
                configJson.put("pf", MediaCodecInfo.CodecProfileLevel.AVCProfileMain)
            }

            binding.pfHigh.id -> {
                configJson.put("pf",MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
            }
        }
        Log.d(TAG,"configJson: $configJson")
        val sp = CentralApplication.getContext()
            .getSharedPreferences("LIVE", AppCompatActivity.MODE_PRIVATE)
        sp.edit().putString("live_config", configJson.toString()).apply()

    }

    @SuppressLint("UseCompatLoadingForDrawables")
    fun show() {
        dialog.setCanceledOnTouchOutside(false)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        dialog.window?.setLayout(
            activity.resources.getDimensionPixelSize(R.dimen.dimens_320dp),
            activity.resources.getDimensionPixelSize(R.dimen.dimens_600dp),
        )
        dialog.show()
    }

    override fun onCheckedChanged(p0: RadioGroup?, p1: Int) {
    }
}