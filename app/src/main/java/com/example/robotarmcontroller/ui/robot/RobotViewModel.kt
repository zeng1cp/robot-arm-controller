package com.example.robotarmcontroller.ui.robot

import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.robotarmcontroller.protocol.ProtocolCommand
import com.example.robotarmcontroller.protocol.ProtocolFrame
import com.example.robotarmcontroller.protocol.ProtocolFrameType
import com.example.robotarmcontroller.protocol.ServoProtocolCodec
import com.example.robotarmcontroller.protocol.parseCommandFrame
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

    fun onIncomingProtocolFrame(frame: ProtocolFrame) {
        when (frame.type) {
            ProtocolFrameType.SYS -> {
                val cmdFrame = frame.parseCommandFrame() ?: return
                when (cmdFrame.cmd) {
                    ProtocolCommand.Sys.PONG,
                    ProtocolCommand.Sys.INFO,
                    ProtocolCommand.Sys.HEARTBEAT -> {
                        Log.i(TAG, "收到SYS消息: cmd=0x${(cmdFrame.cmd.toInt() and 0xFF).toString(16)}, len=${cmdFrame.payload.size}")
                    }
                    else -> {
                        Log.d(TAG, "收到未知SYS cmd: 0x${(cmdFrame.cmd.toInt() and 0xFF).toString(16)}")
                    }
                }
            }

            ProtocolFrameType.STATE -> {
                val cmdFrame = frame.parseCommandFrame() ?: return
                when (cmdFrame.cmd) {
                    ProtocolCommand.State.SERVO -> {
                        val servoState = ServoProtocolCodec.decodeStatePayload(cmdFrame.payload)
                        if (servoState != null) {
                            Log.i(
                                TAG,
                                "舵机状态: ID=${servoState.servoId}, PWM=${servoState.currentPwm}, moving=${servoState.moving}, remain=${servoState.remainingMs}"
                            )
                            updateServoFromResponse(servoState.servoId, servoState.currentPwm)
                        } else {
                            Log.w(TAG, "无效舵机状态帧, payload长度=${cmdFrame.payload.size}")
                        }
                    }
                    else -> {
                        Log.d(TAG, "收到STATE cmd: 0x${(cmdFrame.cmd.toInt() and 0xFF).toString(16)}, len=${cmdFrame.payload.size}")
                    }
                }
            }

            else -> {
                Log.d(TAG, "收到未处理帧类型: 0x${frame.type.toString(16)}")
            }
        }
    }

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


    fun setServoEnable() {
        viewModelScope.launch {
            val success = bleService?.setServoEnable(true) == true
            Log.d(TAG, "舵机使能命令发送: $success")
        }
    }

    fun setServoDisable() {
        viewModelScope.launch {
            val success = bleService?.setServoEnable(false) == true
            Log.d(TAG, "舵机失能命令发送: $success")
        }
    }

    fun requestAllServoStatus() {
        val ids = _uiState.value.servoList.map { it.id }
        viewModelScope.launch {
            ids.forEach { servoId ->
                val success = bleService?.requestServoStatus(servoId) == true
                Log.d(TAG, "请求舵机状态: id=$servoId success=$success")
            }
        }
    }

    fun requestServoStatus(servoId: Int) {
        if (servoId !in _uiState.value.servoList.indices) {
            Log.w(TAG, "无效舵机ID: $servoId")
            return
        }
        viewModelScope.launch {
            val success = bleService?.requestServoStatus(servoId) == true
            Log.d(TAG, "请求舵机状态: id=$servoId success=$success")
        }
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
    suspend fun setServoEnable(enable: Boolean): Boolean
    suspend fun requestServoStatus(servoId: Int): Boolean
    suspend fun sendTestMessage(message: String): Boolean
    suspend fun sendFrame(frameType: UInt, data: ByteArray): Boolean
}
