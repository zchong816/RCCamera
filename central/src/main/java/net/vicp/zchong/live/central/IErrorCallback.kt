package net.vicp.zchong.live.central

interface IErrorCallback {
    fun onPeripheralError(connectType:Int)
    fun onCentralError(connectType:Int)
}