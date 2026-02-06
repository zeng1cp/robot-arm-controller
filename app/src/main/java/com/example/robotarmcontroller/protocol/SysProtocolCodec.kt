package com.example.robotarmcontroller.protocol

object SysProtocolCodec {
    fun encodePing(message: String): ByteArray {
        val msg = message.toByteArray()
        return byteArrayOf(ProtocolCommand.Sys.PING.toByte()) + msg
    }

    fun encodeGetInfo(): ByteArray {
        return byteArrayOf(ProtocolCommand.Sys.GET_INFO.toByte())
    }
}
