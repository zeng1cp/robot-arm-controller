package com.example.robotarmcontroller.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ServoSetPwmPayload(
    val servoId: Int,
    val pwmValue: Int,
    val durationMs: Int = 300
)

data class ServoStatePayload(
    val servoId: Int,
    val moving: Boolean,
    val currentPwm: Int,
    val targetAngleDeg: Float,
    val remainingMs: Int
)

object ServoProtocolCodec {
    fun encodeSetPwm(payload: ServoSetPwmPayload): ByteArray {
        val data = ByteArray(10)
        data[0] = ProtocolCommand.Servo.SET_PWM.toByte()
        data[1] = payload.servoId.toByte()

        ByteBuffer.wrap(data, 2, 8)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(payload.pwmValue)
            .putInt(payload.durationMs)

        return data
    }

    fun encodeGetStatus(servoId: Int): ByteArray {
        return byteArrayOf(
            ProtocolCommand.Servo.GET_STATUS.toByte(),
            servoId.toByte()
        )
    }

    fun decodeStatePayload(payload: ByteArray): ServoStatePayload? {
        if (payload.size != 14) return null

        val id = payload[0].toInt() and 0xFF
        val moving = payload[1].toInt() != 0

        val bb = ByteBuffer.wrap(payload, 2, 12).order(ByteOrder.LITTLE_ENDIAN)
        val currentPwm = bb.int
        val targetAngle = bb.float
        val remainingMs = bb.int

        return ServoStatePayload(
            servoId = id,
            moving = moving,
            currentPwm = currentPwm,
            targetAngleDeg = targetAngle,
            remainingMs = remainingMs
        )
    }
}
