package com.example.robotarmcontroller.data

import android.util.Log
import com.example.robotarmcontroller.protocol.*
import com.example.robotarmcontroller.data.tinyframe.BleTinyFramePort
import com.example.robotarmcontroller.data.model.ServoCommand
import com.example.robotarmcontroller.data.model.ServoState
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

private const val TAG = "ServoRepository"

/**
 * 舵机业务仓库，负责舵机协议解析、状态维护和指令发送。
 */
@Singleton
class ServoRepository @Inject constructor(
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

    // 命令历史
    private val _commandHistory = MutableStateFlow<List<ServoCommand>>(emptyList())
    val commandHistory: StateFlow<List<ServoCommand>> = _commandHistory.asStateFlow()

    init {
        scope.launch {
            tinyFramePort.incomingFrames.collect { msg ->
                val frame = ProtocolFrame(type = msg.type, payload = msg.data ?: ByteArray(0))
                when (frame.type) {
                    ProtocolFrameType.STATE -> handleStateFrame(frame)
                    else -> Unit // 忽略其他帧类型
                }
            }
        }
    }

    // ========== 帧处理 ==========

    private fun handleStateFrame(frame: ProtocolFrame) {
        val cmdFrame = frame.parseCommandFrame() ?: return
        when (cmdFrame.cmd) {
            ProtocolCommand.State.SERVO -> handleServoState(cmdFrame.payload)
            else -> Unit // 忽略其他STATE命令
        }
    }

    private fun handleServoState(payload: ByteArray) {
        val servoState = ServoProtocolCodec.decodeStatePayload(payload)
        servoState?.let {
            updateServoState(it.servoId, it.currentPwm, it.moving)
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

    private fun angleToPwm(angle: Float): Float = 500f + (angle / 270f) * 2000f

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