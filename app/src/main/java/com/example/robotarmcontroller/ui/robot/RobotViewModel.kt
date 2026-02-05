package com.example.robotarmcontroller.ui.robot

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "RobotViewModel"
private const val MAX_COMMAND_HISTORY_SIZE = 100

class RobotViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(RobotUiState())
    val uiState: StateFlow<RobotUiState> = _uiState.asStateFlow()

    // BLE 服务接口
    private var bleService: BleService? = null

    // 帧数据处理回调
//    private var frameCallback: ((FrameData) -> Unit)? = null

    init {
        // 初始化舵机列表
        _uiState.update { currentState ->
            val newList = listOf(
                ServoState(0, "基座", 1500f, 135f),
                ServoState(1, "肩部", 1500f, 135f),
                ServoState(2, "肘部", 1500f, 135f),
                ServoState(3, "腕部", 1500f, 135f),
                ServoState(4, "手腕旋转", 1500f, 135f),
                ServoState(5, "夹爪", 1500f, 135f),
            )
            currentState.copy(servoList = newList)
        }

        // 设置默认的帧数据处理器
//        setFrameHandler { frameData ->
//            handleFrame(frameData)
//        }
    }

    /**
     * 切换控制模式
     */
    fun toggleControlMode() {
        _uiState.update { currentState ->
            val newMode = if (currentState.controlMode == ControlMode.PWM) {
                ControlMode.ANGLE
            } else {
                ControlMode.PWM
            }
            currentState.copy(controlMode = newMode)
        }
        Log.d(TAG, "切换控制模式为: ${_uiState.value.controlMode}")
    }

    /**
     * 设置控制模式
     */
    fun setControlMode(mode: ControlMode) {
        _uiState.update { it.copy(controlMode = mode) }
        Log.d(TAG, "设置控制模式为: $mode")
    }

    /**
     * PWM转角度 (500-2500 -> 0-270)
     */
    private fun pwmToAngle(pwm: Float): Float {
        return ((pwm - 500f) / 2000f) * 270f
    }

    /**
     * 角度转PWM (0-270 -> 500-2500)
     */
    private fun angleToPwm(angle: Float): Float {
        return 500f + (angle / 270f) * 2000f
    }

    private fun updateServo(
        servoId: Int,
        transform: (ServoState) -> ServoState
    ) {
        _uiState.update { currentState ->
            if (servoId !in currentState.servoList.indices) {
                Log.w(TAG, "无效舵机ID: $servoId")
                return@update currentState
            }

            val newList = currentState.servoList.toMutableList()
            newList[servoId] = transform(newList[servoId])
            currentState.copy(servoList = newList)
        }
    }

    private fun appendCommandHistory(
        currentHistory: List<ServoCommand>,
        command: ServoCommand
    ): List<ServoCommand> {
        return (currentHistory + command).takeLast(MAX_COMMAND_HISTORY_SIZE)
    }

    /**
     * 更新PWM值 (同时更新对应的角度值)
     */
    fun updatePwm(servoId: Int, pwm: Float) {
        updateServo(servoId) { oldServo ->
            oldServo.copy(
                pwm = pwm,
                angle = pwmToAngle(pwm),
                isMoving = true
            )
        }
    }

    /**
     * 更新角度值 (同时更新对应的PWM值)
     */
    fun updateAngle(servoId: Int, angle: Float) {
        updateServo(servoId) { oldServo ->
            oldServo.copy(
                pwm = angleToPwm(angle),
                angle = angle,
                isMoving = true
            )
        }
    }

    fun setBleService(service: BleService) {
        this.bleService = service
        _uiState.update { it.copy(isConnected = true, connectionStatus = "已连接") }
    }

    fun disconnect() {
        bleService = null
        _uiState.update { it.copy(isConnected = false, connectionStatus = "未连接") }
    }

//    fun setFrameHandler(handler: (FrameData) -> Unit) {
//        this.frameCallback = handler
//    }
//
//    fun receiveFrame(frameData: FrameData) {
//        frameCallback?.invoke(frameData)
//    }
//
//    private fun handleFrame(frameData: FrameData) {
//        Log.d(TAG, "处理帧: type=0x${frameData.frameType.toString(16)}, len=${frameData.len}")
//
//        when (frameData.frameType) {
//            0x22u -> {
//                val message = String(frameData.data, 0, frameData.len)
//                Log.i(TAG, "收到测试消息: $message")
//            }
//            0x23u -> {
//                if (frameData.len >= 3) {
//                    val servoId = frameData.data[0].toInt() and 0xFF
//                    val pwmValue = ((frameData.data[1].toInt() and 0xFF) shl 8) or
//                            (frameData.data[2].toInt() and 0xFF)
//                    Log.i(TAG, "舵机响应: ID=$servoId, PWM=$pwmValue")
//                    updateServoFromResponse(servoId, pwmValue)
//                }
//            }
//            else -> {
//                Log.d(TAG, "收到未知帧类型: 0x${frameData.frameType.toString(16)}")
//            }
//        }
//    }

    private fun updateServoFromResponse(servoId: Int, pwmValue: Int) {
        _uiState.update { currentState ->
            val newList = currentState.servoList.toMutableList()
            if (servoId < newList.size) {
                val oldServo = newList[servoId]
                val newAngle = pwmToAngle(pwmValue.toFloat())
                newList[servoId] = oldServo.copy(
                    pwm = pwmValue.toFloat(),
                    angle = newAngle,
                    isMoving = false
                )
                currentState.copy(servoList = newList)
            } else {
                Log.w(TAG, "收到未知舵机ID: $servoId")
                currentState
            }
        }
    }

    fun onPwmChange(servoId: Int, pwm: Float) {
        updatePwm(servoId, pwm)
    }

    fun onPwmChangeFinished(servoId: Int) {
        if (servoId !in _uiState.value.servoList.indices) {
            Log.w(TAG, "无效舵机ID: $servoId")
            return
        }

        val servo = _uiState.value.servoList[servoId]
        val pwm = servo.pwm.coerceIn(500f, 2500f)

        _uiState.update { currentState ->
            val newList = currentState.servoList.toMutableList()
            newList[servoId] = servo.copy(pwm = pwm, isMoving = false)

            val command = ServoCommand(servoId, pwm.toInt(), servo.angle.toInt())
            currentState.copy(
                servoList = newList,
                lastCommandSent = command,
                commandHistory = appendCommandHistory(currentState.commandHistory, command)
            )
        }

        sendServoCommand(servoId, pwm.toInt())
    }

    fun onAngleChange(servoId: Int, angle: Float) {
        updateAngle(servoId, angle)
    }

    fun onAngleChangeFinished(servoId: Int) {
        if (servoId !in _uiState.value.servoList.indices) {
            Log.w(TAG, "无效舵机ID: $servoId")
            return
        }

        val servo = _uiState.value.servoList[servoId]
        val angle = servo.angle.coerceIn(0f, 270f)
        val newPwm = angleToPwm(angle)
        _uiState.update { currentState ->
            val newList = currentState.servoList.toMutableList()
            newList[servoId] = servo.copy(
                pwm = newPwm,
                angle = angle,
                isMoving = false
            )

            val command = ServoCommand(servoId, newPwm.toInt(), angle.toInt())
            currentState.copy(
                servoList = newList,
                lastCommandSent = command,
                commandHistory = appendCommandHistory(currentState.commandHistory, command)
            )
        }

        sendServoCommand(servoId, newPwm.toInt())
    }

    private fun sendServoCommand(servoId: Int, pwmValue: Int) {
        viewModelScope.launch {
            bleService?.sendServoCommand(servoId, pwmValue)?.let { success ->
                if (success) {
                    Log.d(TAG, "舵机命令发送成功: ID=$servoId, PWM=$pwmValue")
                } else {
                    Log.e(TAG, "舵机命令发送失败")
                }
            }
        }
    }

    fun sendTestMessage(message: String = "Hello from Android!") {
        viewModelScope.launch {
            bleService?.sendTestMessage(message)?.let { success ->
                if (success) {
                    Log.d(TAG, "测试消息发送成功: $message")
                } else {
                    Log.e(TAG, "测试消息发送失败")
                }
            }
        }
    }

    fun clearHistory() {
        _uiState.update { it.copy(commandHistory = emptyList()) }
    }
}

// BLE 服务接口
interface BleService {
    suspend fun sendServoCommand(servoId: Int, pwmValue: Int): Boolean
    suspend fun sendTestMessage(message: String): Boolean
    suspend fun sendFrame(frameType: UInt, data: ByteArray): Boolean
}
