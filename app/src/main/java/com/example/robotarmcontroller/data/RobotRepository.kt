package com.example.robotarmcontroller.data

import android.util.Log
import com.example.robotarmcontroller.protocol.*
import com.example.robotarmcontroller.data.tinyframe.BleTinyFramePort
import com.example.robotarmcontroller.ui.robot.ServoCommand
import com.example.robotarmcontroller.ui.robot.ServoState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "RobotRepository"

/**
 * 机械臂业务仓库，负责所有协议解析、状态维护和指令发送。
 */
@Singleton
class RobotRepository @Inject constructor(
    private val tinyFramePort: BleTinyFramePort
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // 舵机状态
    private val _servoStates = MutableStateFlow(
        listOf(
            ServoState(0, "基座", 1500f, 135f),
            ServoState(1, "肩部", 1500f, 135f),
            ServoState(2, "肘部", 1500f, 135f),
            ServoState(3, "腕部", 1500f, 135f),
            ServoState(4, "手腕", 1500f, 135f),
            ServoState(5, "夹爪", 1500f, 135f)
        )
    )
    val servoStates: StateFlow<List<ServoState>> = _servoStates.asStateFlow()

    // 运动相关状态
    private val _motionGroupId = MutableStateFlow(0)
    val motionGroupId: StateFlow<Int> = _motionGroupId.asStateFlow()

    private val _motionCompleteGroupId = MutableStateFlow(0)
    val motionCompleteGroupId: StateFlow<Int> = _motionCompleteGroupId.asStateFlow()

    // Cycle 列表
    private val _cycleList = MutableStateFlow<List<CycleInfo>>(emptyList())
    val cycleList: StateFlow<List<CycleInfo>> = _cycleList.asStateFlow()

    // 命令历史
    private val _commandHistory = MutableStateFlow<List<ServoCommand>>(emptyList())
    val commandHistory: StateFlow<List<ServoCommand>> = _commandHistory.asStateFlow()

    init {
        scope.launch {
            tinyFramePort.incomingFrames.collect { msg ->
                val frame = ProtocolFrame(type = msg.type, payload = msg.data ?: ByteArray(0))
                when (frame.type) {
                    ProtocolFrameType.SYS -> handleSysFrame(frame)
                    ProtocolFrameType.STATE -> handleStateFrame(frame)
                    else -> Log.d(TAG, "忽略帧类型: 0x${frame.type.toString(16)}")
                }
            }
        }
    }

    // ========== 帧处理 ==========

    private fun handleSysFrame(frame: ProtocolFrame) {
        val cmdFrame = frame.parseCommandFrame() ?: return
        when (cmdFrame.cmd) {
            ProtocolCommand.Sys.PONG,
            ProtocolCommand.Sys.INFO,
            ProtocolCommand.Sys.HEARTBEAT -> {
                Log.i(TAG, "收到SYS消息: cmd=0x${cmdFrame.cmd.toString(16)}")
            }
            else -> Log.d(TAG, "未知SYS cmd: 0x${cmdFrame.cmd.toString(16)}")
        }
    }

    private fun handleStateFrame(frame: ProtocolFrame) {
        val cmdFrame = frame.parseCommandFrame() ?: return
        when (cmdFrame.cmd) {
            ProtocolCommand.State.SERVO -> handleServoState(cmdFrame.payload)
            ProtocolCommand.State.MOTION -> handleMotionState(cmdFrame.payload)
            else -> Log.d(TAG, "未知STATE cmd: 0x${cmdFrame.cmd.toString(16)}")
        }
    }

    private fun handleServoState(payload: ByteArray) {
        val servoState = ServoProtocolCodec.decodeStatePayload(payload)
        servoState?.let {
            updateServoState(it.servoId, it.currentPwm, it.moving)
        }
    }

    private fun handleMotionState(payload: ByteArray) {
        if (payload.isEmpty()) return
        val subcmd = payload[0].toInt() and 0xFF
        when (subcmd) {
            ProtocolCommand.Motion.START.toInt(),
            ProtocolCommand.Motion.GET_STATUS.toInt() -> {
                if (payload.size >= 5) {
                    val gid = toIntLe(payload, 1)
                    _motionGroupId.value = gid
                    Log.i(TAG, "Motion状态: subcmd=0x${subcmd.toString(16)} group=$gid")
                }
            }
            ProtocolCommand.Motion.STATUS.toInt() -> {
                if (payload.size >= 6) {
                    val gid = toIntLe(payload, 1)
                    val complete = payload[5].toInt() and 0xFF
                    if (complete != 0) {
                        _motionCompleteGroupId.value = gid
                    }
                    Log.i(TAG, "Motion完成: group=$gid complete=$complete")
                }
            }
            ProtocolCommand.Motion.CYCLE_LIST.toInt() -> parseCycleList(payload)
            ProtocolCommand.Motion.CYCLE_CREATE.toInt() -> {
                if (payload.size >= 5) {
                    val cycleIndex = toIntLe(payload, 1)
                    Log.i(TAG, "Cycle创建: index=$cycleIndex")
                }
            }
            ProtocolCommand.Motion.CYCLE_GET_STATUS.toInt() -> parseCycleStatus(payload)
            ProtocolCommand.Motion.CYCLE_STATUS.toInt() -> parseCycleCallback(payload)
            else -> Log.d(TAG, "未知Motion子命令: 0x${subcmd.toString(16)}")
        }
    }

    // ========== 工具函数 ==========

    private fun toIntLe(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun updateServoState(servoId: Int, pwm: Int, moving: Boolean = false) {
        _servoStates.update { list ->
            list.map {
                if (it.id == servoId) {
                    it.copy(
                        pwm = pwm.toFloat(),
                        angle = pwmToAngle(pwm),
                        isMoving = moving
                    )
                } else it
            }
        }
        Log.d(TAG, "更新舵机: id=$servoId, pwm=$pwm")
    }

    private fun pwmToAngle(pwm: Int): Float = ((pwm - 500) / 2000f) * 270f

    // ========== Cycle 解析 ==========

    private fun parseCycleList(payload: ByteArray) {
        // payload[0] 是 subcmd
        if (payload.size < 2) return
        val data = payload.copyOfRange(1, payload.size)
        if (data.isEmpty()) return
        val count = data[0].toInt() and 0xFF

        val compactEntryLen = 17
        val expandedEntryLen = 21
        val list = mutableListOf<CycleInfo>()

        when {
            data.size == 1 + count * compactEntryLen -> {
                var off = 1
                repeat(count) {
                    val index = data[off].toInt() and 0xFF
                    val active = data[off + 1].toInt() and 0xFF
                    val running = data[off + 2].toInt() and 0xFF
                    val currentPose = data[off + 3].toInt() and 0xFF
                    val poseCount = data[off + 4].toInt() and 0xFF
                    val loopCount = toIntLe(data, off + 5)
                    val maxLoops = toIntLe(data, off + 9)
                    val activeGroupId = toIntLe(data, off + 13)
                    list.add(CycleInfo(index, active != 0, running != 0, currentPose, poseCount, loopCount, maxLoops, activeGroupId))
                    off += compactEntryLen
                }
            }
            data.size == 1 + count * expandedEntryLen -> {
                var off = 1
                repeat(count) {
                    val index = toIntLe(data, off)
                    val active = data[off + 4].toInt() and 0xFF
                    val running = data[off + 5].toInt() and 0xFF
                    val currentPose = data[off + 6].toInt() and 0xFF
                    val poseCount = data[off + 7].toInt() and 0xFF
                    val loopCount = toIntLe(data, off + 8)
                    val maxLoops = toIntLe(data, off + 12)
                    val activeGroupId = toIntLe(data, off + 16)
                    list.add(CycleInfo(index, active != 0, running != 0, currentPose, poseCount, loopCount, maxLoops, activeGroupId))
                    off += expandedEntryLen
                }
            }
            else -> {
                // 启发式回退
                if ((data.size - 1) % compactEntryLen == 0) {
                    var off = 1
                    while (off + compactEntryLen <= data.size) {
                        val index = data[off].toInt() and 0xFF
                        val active = data[off + 1].toInt() and 0xFF
                        val running = data[off + 2].toInt() and 0xFF
                        val currentPose = data[off + 3].toInt() and 0xFF
                        val poseCount = data[off + 4].toInt() and 0xFF
                        val loopCount = toIntLe(data, off + 5)
                        val maxLoops = toIntLe(data, off + 9)
                        val activeGroupId = toIntLe(data, off + 13)
                        list.add(CycleInfo(index, active != 0, running != 0, currentPose, poseCount, loopCount, maxLoops, activeGroupId))
                        off += compactEntryLen
                    }
                }
            }
        }
        if (list.isNotEmpty()) {
            _cycleList.value = list.sortedBy { it.index }
            Log.i(TAG, "Cycle列表更新: ${list.size} 个")
        }
    }

    private fun parseCycleStatus(payload: ByteArray) {
        if (payload.size >= 21) {
            val cycleIndex = toIntLe(payload, 1)
            val active = payload[5].toInt() and 0xFF
            val running = payload[6].toInt() and 0xFF
            val currentPose = payload[7].toInt() and 0xFF
            val poseCount = payload[8].toInt() and 0xFF
            val loopCount = toIntLe(payload, 9)
            val maxLoops = toIntLe(payload, 13)
            val activeGroupId = toIntLe(payload, 17)
            Log.i(TAG, "Cycle状态: idx=$cycleIndex active=$active running=$running pose=$currentPose/$poseCount loops=$loopCount/$maxLoops group=$activeGroupId")
        }
    }

    private fun parseCycleCallback(payload: ByteArray) {
        if (payload.size >= 14) {
            val cycleIndex = toIntLe(payload, 1)
            val loopCount = toIntLe(payload, 5)
            val remaining = toIntLe(payload, 9)
            val finished = payload[13].toInt() and 0xFF
            Log.i(TAG, "Cycle回调: idx=$cycleIndex loop=$loopCount remaining=$remaining finished=$finished")
        }
    }

    // ========== 对外指令发送 ==========

    fun sendServosHome() {
        val data = byteArrayOf(ProtocolCommand.Servo.HOME.toByte())
        tinyFramePort.sendFrame(ProtocolFrameType.SERVO, data)
    }

    fun sendPwmCommand(servoId: Int, pwm: Int) {
        val payload = ServoSetPwmPayload(servoId = servoId, pwmValue = pwm)
        val data = ServoProtocolCodec.encodeSetPwm(payload)
        tinyFramePort.sendFrame(ProtocolFrameType.SERVO, data)
    }

    fun setServoEnable(enable: Boolean) {
        val cmd = if (enable) ProtocolCommand.Servo.ENABLE else ProtocolCommand.Servo.DISABLE
        tinyFramePort.sendFrame(ProtocolFrameType.SERVO, byteArrayOf(cmd.toByte()))
    }

    fun requestServoStatus(servoId: Int) {
        val data = ServoProtocolCodec.encodeGetStatus(servoId)
        tinyFramePort.sendFrame(ProtocolFrameType.SERVO, data)
    }

    fun requestAllServoStatus() {
        _servoStates.value.forEach { servo ->
            requestServoStatus(servo.id)
        }
    }

    // Motion 指令
    fun startMotion(mode: Int, ids: List<Int>, values: List<Number>, durationMs: Int) {
        if (ids.isEmpty() || values.isEmpty() || ids.size != values.size) return
        val data = MotionProtocolCodec.encodeStart(mode, ids, values, durationMs)
        tinyFramePort.sendFrame(ProtocolFrameType.MOTION, data)
    }

    fun stopMotion(groupId: Int) {
        val data = MotionProtocolCodec.encodeStop(groupId)
        tinyFramePort.sendFrame(ProtocolFrameType.MOTION, data)
    }

    fun pauseMotion(groupId: Int) {
        val data = MotionProtocolCodec.encodePause(groupId)
        tinyFramePort.sendFrame(ProtocolFrameType.MOTION, data)
    }

    fun resumeMotion(groupId: Int) {
        val data = MotionProtocolCodec.encodeResume(groupId)
        tinyFramePort.sendFrame(ProtocolFrameType.MOTION, data)
    }

    fun requestMotionStatus(groupId: Int) {
        val data = MotionProtocolCodec.encodeGetStatus(groupId)
        tinyFramePort.sendFrame(ProtocolFrameType.MOTION, data)
    }

    fun createMotionCycle(
        mode: Int,
        ids: List<Int>,
        poses: List<List<Number>>,
        durations: List<Int>,
        maxLoops: Int
    ) {
        if (ids.isEmpty() || poses.isEmpty() || poses.size != durations.size) return
        val data = MotionProtocolCodec.encodeCycleCreate(mode, ids, poses, durations, maxLoops)
        tinyFramePort.sendFrame(ProtocolFrameType.MOTION, data)
    }

    fun startMotionCycle(index: Int) {
        val data = MotionProtocolCodec.encodeCycleStart(index)
        tinyFramePort.sendFrame(ProtocolFrameType.MOTION, data)
    }

    fun restartMotionCycle(index: Int) {
        val data = MotionProtocolCodec.encodeCycleRestart(index)
        tinyFramePort.sendFrame(ProtocolFrameType.MOTION, data)
    }

    fun pauseMotionCycle(index: Int) {
        val data = MotionProtocolCodec.encodeCyclePause(index)
        tinyFramePort.sendFrame(ProtocolFrameType.MOTION, data)
    }

    fun releaseMotionCycle(index: Int) {
        val data = MotionProtocolCodec.encodeCycleRelease(index)
        tinyFramePort.sendFrame(ProtocolFrameType.MOTION, data)
    }

    fun requestMotionCycleStatus(index: Int) {
        val data = MotionProtocolCodec.encodeCycleGetStatus(index)
        tinyFramePort.sendFrame(ProtocolFrameType.MOTION, data)
    }

    fun requestCycleList() {
        tinyFramePort.sendFrame(ProtocolFrameType.MOTION, byteArrayOf(ProtocolCommand.Motion.CYCLE_LIST.toByte()))
    }

    fun previewServoValue(servoId: Int, mode: Int, value: Float) {
        val pwm = if (mode == MotionProtocolCodec.MODE_PWM) {
            value.toInt().coerceIn(500, 2500)
        } else {
            angleToPwm(value).toInt()
        }
        val payload = ServoSetPwmPayload(servoId = servoId, pwmValue = pwm, durationMs = 0)
        val data = ServoProtocolCodec.encodeSetPwm(payload)
        tinyFramePort.sendFrame(ProtocolFrameType.SERVO, data)
    }

    private fun angleToPwm(angle: Float): Float = 500f + (angle / 270f) * 2000f

    fun addCommandToHistory(servoId: Int, pwm: Int, angle: Int?) {
        val command = ServoCommand(servoId, pwm, angle)
        _commandHistory.update { (it + command).takeLast(100) }
    }

    fun clearHistory() {
        _commandHistory.value = emptyList()
    }

    fun close() {
        scope.cancel()
    }
}
