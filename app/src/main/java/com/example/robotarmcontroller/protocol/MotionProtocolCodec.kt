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

    fun encodeCycleCreate(
        mode: Int,
        servoIds: List<Int>,
        poses: List<List<Number>>,
        durationsMs: List<Int>,
        maxLoops: Int
    ): ByteArray {
        require(servoIds.isNotEmpty()) { "servoIds cannot be empty" }
        require(poses.isNotEmpty()) { "poses cannot be empty" }
        require(poses.size == durationsMs.size) { "durations size must match pose count" }
        poses.forEach { pose ->
            require(pose.size == servoIds.size) { "pose size must match servo count" }
        }

        val servoCount = servoIds.size
        val poseCount = poses.size

        val header = ByteArray(7)
        header[0] = ProtocolCommand.Motion.CYCLE_CREATE.toByte()
        header[1] = mode.toByte()
        header[2] = servoCount.toByte()
        header[3] = poseCount.toByte()
        ByteBuffer.wrap(header, 4, 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(maxLoops)

        val durationBytes = ByteArray(poseCount * 4)
        ByteBuffer.wrap(durationBytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            durationsMs.forEach { putInt(it) }
        }

        val ids = servoIds.map { it.toByte() }.toByteArray()

        val values = ByteArray(poseCount * servoCount * 4)
        val valuesBuffer = ByteBuffer.wrap(values).order(ByteOrder.LITTLE_ENDIAN)
        poses.flatten().forEach { value ->
            when (mode) {
                MODE_PWM -> valuesBuffer.putInt(value.toInt())
                MODE_ANGLE -> valuesBuffer.putFloat(value.toFloat())
                else -> error("Unsupported mode: $mode")
            }
        }

        return header + durationBytes + ids + values
    }

    fun encodeCycleStart(index: Int): ByteArray = encodeGroupCommand(ProtocolCommand.Motion.CYCLE_START, index)
    fun encodeCycleRestart(index: Int): ByteArray = encodeGroupCommand(ProtocolCommand.Motion.CYCLE_RESTART, index)
    fun encodeCyclePause(index: Int): ByteArray = encodeGroupCommand(ProtocolCommand.Motion.CYCLE_PAUSE, index)
    fun encodeCycleRelease(index: Int): ByteArray = encodeGroupCommand(ProtocolCommand.Motion.CYCLE_RELEASE, index)
    fun encodeCycleGetStatus(index: Int): ByteArray = encodeGroupCommand(ProtocolCommand.Motion.CYCLE_GET_STATUS, index)

    private fun encodeGroupCommand(cmd: UByte, groupId: Int): ByteArray {
        val data = ByteArray(5)
        data[0] = cmd.toByte()
        ByteBuffer.wrap(data, 1, 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(groupId)
        return data
    }
}
