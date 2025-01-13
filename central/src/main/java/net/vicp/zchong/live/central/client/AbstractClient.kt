package net.vicp.zchong.live.central.client

import android.content.Context

/**
 * @author zhangchong
 * @date 2024/8/16 15:20
 */
abstract class AbstractClient protected
constructor(protected var context: Context) : IClient {
    var receiver: IReceiver? = null
}
