package net.vicp.zchong.live.central.utils

import android.content.Context
import android.widget.Toast

/**
 * @author zhangchong
 * @date 2024/8/16 15:55
 */
object ToastUtils {
    private var mToast: Toast? = null

    /**
     * 弹出短时间的底部toast
     */
    fun showBottomToast(context: Context?, string: String?) {
        if (mToast != null) {
            mToast!!.cancel()
            mToast = Toast.makeText(context, string, Toast.LENGTH_SHORT)
        } else {
            mToast = Toast.makeText(context, string, Toast.LENGTH_SHORT)
        }
        mToast!!.show()
    }
}
