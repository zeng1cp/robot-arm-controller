package com.example.robotarmcontroller.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

object CycleProtocolCodec {
    const val MODE_PWM = 0
    const val MODE_ANGLE = 1

    fun encodeCreate(
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

        val header = ByteArray(8)
        header[0] = ProtocolCommand.Cycle.CREATE.toByte()
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

    fun encodeStart(index: Int): ByteArray = encodeIndexCommand(ProtocolCommand.Cycle.START, index)
    fun encodeRestart(index: Int): ByteArray = encodeIndexCommand(ProtocolCommand.Cycle.RESTART, index)
    fun encodePause(index: Int): ByteArray = encodeIndexCommand(ProtocolCommand.Cycle.PAUSE, index)
    fun encodeRelease(index: Int): ByteArray = encodeIndexCommand(ProtocolCommand.Cycle.RELEASE, index)
    fun encodeGetStatus(index: Int): ByteArray = encodeIndexCommand(ProtocolCommand.Cycle.GET_STATUS, index)
    fun encodeList(): ByteArray = byteArrayOf(ProtocolCommand.Cycle.LIST.toByte())

    private fun encodeIndexCommand(cmd: UByte, index: Int): ByteArray {
        val data = ByteArray(5)
        data[0] = cmd.toByte()
        ByteBuffer.wrap(data, 1, 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(index)
        return data
    }
}