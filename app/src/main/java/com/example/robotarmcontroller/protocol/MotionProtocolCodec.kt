package com.example.robotarmcontroller.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

object MotionProtocolCodec {
    const val MODE_PWM = 0
    const val MODE_ANGLE = 1

    fun encodeStart(
        mode: Int,
        servoIds: List<Int>,
        values: List<Number>,
        durationMs: Int
    ): ByteArray {
        require(servoIds.isNotEmpty()) { "servoIds cannot be empty" }
        require(servoIds.size == values.size) { "values size must match ids size" }

        val count = servoIds.size
        val header = ByteArray(7)
        header[0] = ProtocolCommand.Motion.START.toByte()
        header[1] = mode.toByte()
        header[2] = count.toByte()
        ByteBuffer.wrap(header, 3, 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(durationMs)

        val ids = servoIds.map { it.toByte() }.toByteArray()
        val valueBytes = ByteArray(count * 4)
        val buffer = ByteBuffer.wrap(valueBytes).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { value ->
            when (mode) {
                MODE_PWM -> buffer.putInt(value.toInt())
                MODE_ANGLE -> buffer.putFloat(value.toFloat())
                else -> error("Unsupported mode: $mode")
            }
        }
        return header + ids + valueBytes
    }

    fun encodeStop(groupId: Int): ByteArray = encodeGroupCommand(ProtocolCommand.Motion.STOP, groupId)
    fun encodePause(groupId: Int): ByteArray = encodeGroupCommand(ProtocolCommand.Motion.PAUSE, groupId)
    fun encodeResume(groupId: Int): ByteArray = encodeGroupCommand(ProtocolCommand.Motion.RESUME, groupId)
    fun encodeGetStatus(groupId: Int): ByteArray = encodeGroupCommand(ProtocolCommand.Motion.GET_STATUS, groupId)

    private fun encodeGroupCommand(cmd: UByte, groupId: Int): ByteArray {
        val data = ByteArray(5)
        data[0] = cmd.toByte()
        ByteBuffer.wrap(data, 1, 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(groupId)
        return data
    }
}
