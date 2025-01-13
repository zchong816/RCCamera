package net.vicp.zchong.live.common

import android.os.Build

object ModelUtils {
    val isLawkS1: Boolean
        get() = Build.MODEL.startsWith("LAWK_ML_S1")
}
