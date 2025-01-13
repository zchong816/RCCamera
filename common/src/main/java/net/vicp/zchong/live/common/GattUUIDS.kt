package net.vicp.zchong.live.common

import java.util.UUID

/**
 * @author zhangchong
 * @date 2024/8/18 09:09
 */
interface GattUUIDS {
    companion object {
        const val NotificationDescriptorUUID: String = "00002902-0000-1000-8000-00805f9b34fb"
        //服务 UUID
        val UUID_SERVICE: UUID = UUID.fromString("03258806-EC30-4DAC-94C6-3E4E2F667231")
        //特征 UUID
        val UUID_DATA_CHARACTERISTIC: UUID = UUID.fromString("AF0BADB1-5B99-43CD-917A-A77BC549E111")
        val UUID_COMMAND_CHARACTERISTIC: UUID = UUID.fromString("AF0BADB1-5B99-43CD-917A-A77BC549E112")
        //描述 UUID
        val UUID_DESCRIPTOR: UUID = UUID.fromString(NotificationDescriptorUUID)
    }
}
