package com.example.robotarmcontroller.protocol

data class ProtocolFrame(
    val type: UInt,
    val payload: ByteArray
)

data class ProtocolCommandFrame(
    val cmd: UByte,
    val payload: ByteArray
)

object ProtocolFrameType {
    const val SYS: UInt = 0x01u
    const val SERVO: UInt = 0x10u
    const val MOTION: UInt = 0x11u
    const val ARM: UInt = 0x12u
    const val STATE: UInt = 0xD0u
    const val CONFIG: UInt = 0xE0u
    const val DEBUG: UInt = 0xF0u
}

object ProtocolCommand {
    object Sys {
        const val PING: UByte = 0x01u
        const val PONG: UByte = 0x02u
        const val GET_INFO: UByte = 0x04u
        const val INFO: UByte = 0x05u
        const val HEARTBEAT: UByte = 0x06u
    }

    object Servo {
        const val ENABLE: UByte = 0x01u
        const val DISABLE: UByte = 0x02u
        const val SET_PWM: UByte = 0x03u
        const val SET_POS: UByte = 0x04u
        const val GET_STATUS: UByte = 0x05u
        const val STATUS: UByte = 0x06u
        const val HOME: UByte = 0x07u
    }

    object State {
        const val SYS: UByte = 0x01u
        const val SERVO: UByte = 0x02u
        const val MOTION: UByte = 0x03u
        const val ARM: UByte = 0x04u
        const val CONFIG: UByte = 0x05u
    }

    object Motion {
        const val START: UByte = 0x01u
        const val STOP: UByte = 0x02u
        const val PAUSE: UByte = 0x03u
        const val RESUME: UByte = 0x04u
        const val SET_PLAN: UByte = 0x05u
        const val GET_STATUS: UByte = 0x06u
        const val STATUS: UByte = 0x07u
        const val CYCLE_CREATE: UByte = 0x10u
        const val CYCLE_START: UByte = 0x11u
        const val CYCLE_RESTART: UByte = 0x12u
        const val CYCLE_PAUSE: UByte = 0x13u
        const val CYCLE_RELEASE: UByte = 0x14u
        const val CYCLE_GET_STATUS: UByte = 0x15u
        const val CYCLE_STATUS: UByte = 0x16u
        const val CYCLE_LIST: UByte = 0x17u
    }
}

fun ProtocolFrame.parseCommandFrame(): ProtocolCommandFrame? {
    if (payload.isEmpty()) return null
    return ProtocolCommandFrame(
        cmd = payload[0].toUByte(),
        payload = payload.copyOfRange(1, payload.size)
    )
}
