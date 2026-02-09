package com.example.robotarmcontroller.ui.robot

import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.robotarmcontroller.protocol.ProtocolCommand
import com.example.robotarmcontroller.protocol.ProtocolFrame
import com.example.robotarmcontroller.protocol.MotionProtocolCodec
import com.example.robotarmcontroller.protocol.ProtocolFrameType
import com.example.robotarmcontroller.protocol.ServoProtocolCodec
import com.example.robotarmcontroller.protocol.ServoSetPwmPayload
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
                ServoState(4, "手腕", 1500f, 135f),
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
        requestAllServoStatus()
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
                                "舵机状态: subcmd=0x${servoState.subcmd.toString(16)}, ID=${servoState.servoId}, PWM=${servoState.currentPwm}, moving=${servoState.moving}, remain=${servoState.remainingMs}"
                            )
                            updateServoFromResponse(servoState.servoId, servoState.currentPwm)
                        } else {
                            Log.w(TAG, "无效舵机状态帧, payload长度=${cmdFrame.payload.size}")
                        }
                    }
                    ProtocolCommand.State.MOTION -> {
                        val payload = cmdFrame.payload
                        if (payload.isEmpty()) {
                            Log.w(TAG, "无效Motion状态帧, payload为空")
                            return
                        }
                        val subcmd = payload[0].toInt() and 0xFF
                        when (subcmd) {
                            ProtocolCommand.Motion.START.toInt(),
                            ProtocolCommand.Motion.GET_STATUS.toInt() -> {
                                if (payload.size >= 5) {
                                    val gid = toIntLe(payload, 1)
                                    _uiState.update { it.copy(motionGroupId = gid) }
                                    Log.i(TAG, "Motion状态: subcmd=0x${subcmd.toString(16)} group=$gid len=${payload.size}")
                                } else {
                                    Log.w(TAG, "无效Motion状态帧, subcmd=0x${subcmd.toString(16)} payload长度=${payload.size}")
                                }
                            }
                            ProtocolCommand.Motion.CYCLE_CREATE.toInt() -> {
                                if (payload.size >= 5) {
                                    val cycleIndex = toIntLe(payload, 1)
                                    Log.i(TAG, "MotionCycle创建: index=$cycleIndex")
                                } else {
                                    Log.w(TAG, "无效MotionCycle创建帧, payload长度=${payload.size}")
                                }
                            }
                            ProtocolCommand.Motion.CYCLE_GET_STATUS.toInt() -> {
                                if (payload.size >= 21) {
                                    val cycleIndex = toIntLe(payload, 1)
                                    val active = payload[5].toInt() and 0xFF
                                    val running = payload[6].toInt() and 0xFF
                                    val currentPose = payload[7].toInt() and 0xFF
                                    val poseCount = payload[8].toInt() and 0xFF
                                    val loopCount = toIntLe(payload, 9)
                                    val maxLoops = toIntLe(payload, 13)
                                    val activeGroupId = toIntLe(payload, 17)
                                    _uiState.update { it.copy(motionGroupId = activeGroupId) }
                                    Log.i(
                                        TAG,
                                        "MotionCycle状态: index=$cycleIndex active=$active running=$running pose=$currentPose/$poseCount loops=$loopCount/$maxLoops group=$activeGroupId"
                                    )
                                } else {
                                    Log.w(TAG, "无效MotionCycle状态帧, payload长度=${payload.size}")
                                }
                            }
                            ProtocolCommand.Motion.STATUS.toInt() -> {
                                if (payload.size >= 6) {
                                    val gid = toIntLe(payload, 1)
                                    val complete = payload[5].toInt() and 0xFF
                                    if (complete != 0) {
                                        _uiState.update { it.copy(motionCompleteGroupId = gid) }
                                    }
                                    Log.i(TAG, "Motion完成: group=$gid complete=$complete")
                                } else {
                                    Log.w(TAG, "无效Motion完成帧, payload长度=${payload.size}")
                                }
                            }
                            ProtocolCommand.Motion.CYCLE_STATUS.toInt() -> {
                                if (payload.size >= 14) {
                                    val cycleIndex = toIntLe(payload, 1)
                                    val loopCount = toIntLe(payload, 5)
                                    val remaining = toIntLe(payload, 9)
                                    val finished = payload[13].toInt() and 0xFF
                                    Log.i(
                                        TAG,
                                        "MotionCycle回调: index=$cycleIndex loop=$loopCount remaining=$remaining finished=$finished"
                                    )
                                } else {
                                    Log.w(TAG, "无效MotionCycle回调帧, payload长度=${payload.size}")
                                }
                            }
                            else -> {
                                Log.d(TAG, "收到Motion状态帧: subcmd=0x${subcmd.toString(16)} len=${payload.size}")
                            }
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

    private fun toIntLe(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
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

    fun startMotion(mode: Int, ids: List<Int>, values: List<Float>, durationMs: Int) {
        if (ids.isEmpty() || values.isEmpty() || ids.size != values.size) {
            Log.w(TAG, "启动Motion失败: ids/values为空或长度不一致 ids=${ids.size} values=${values.size}")
            return
        }
        viewModelScope.launch {
            val data = MotionProtocolCodec.encodeStart(mode, ids, values, durationMs)
            val success = bleService?.sendFrame(ProtocolFrameType.MOTION, data) == true
            Log.d(TAG, "启动Motion: success=$success")
        }
    }

    fun stopMotion(groupId: Int) {
        viewModelScope.launch {
            val data = MotionProtocolCodec.encodeStop(groupId)
            val success = bleService?.sendFrame(ProtocolFrameType.MOTION, data) == true
            Log.d(TAG, "停止Motion: group=$groupId success=$success")
        }
    }

    fun pauseMotion(groupId: Int) {
        viewModelScope.launch {
            val data = MotionProtocolCodec.encodePause(groupId)
            val success = bleService?.sendFrame(ProtocolFrameType.MOTION, data) == true
            Log.d(TAG, "暂停Motion: group=$groupId success=$success")
        }
    }

    fun resumeMotion(groupId: Int) {
        viewModelScope.launch {
            val data = MotionProtocolCodec.encodeResume(groupId)
            val success = bleService?.sendFrame(ProtocolFrameType.MOTION, data) == true
            Log.d(TAG, "继续Motion: group=$groupId success=$success")
        }
    }

    fun requestMotionStatus(groupId: Int) {
        viewModelScope.launch {
            val data = MotionProtocolCodec.encodeGetStatus(groupId)
            val success = bleService?.sendFrame(ProtocolFrameType.MOTION, data) == true
            Log.d(TAG, "查询Motion状态: group=$groupId success=$success")
        }
    }

    fun createMotionCycle(
        mode: Int,
        ids: List<Int>,
        poses: List<List<Float>>,
        durations: List<Int>,
        maxLoops: Int
    ) {
        if (ids.isEmpty()) {
            Log.w(TAG, "创建MotionCycle失败: ids为空")
            return
        }
        if (poses.isEmpty()) {
            Log.w(TAG, "创建MotionCycle失败: poses为空")
            return
        }
        if (poses.size != durations.size) {
            Log.w(TAG, "创建MotionCycle失败: pose数量与duration数量不一致 poses=${poses.size} durations=${durations.size}")
            return
        }
        val invalidPose = poses.firstOrNull { it.size != ids.size }
        if (invalidPose != null) {
            Log.w(TAG, "创建MotionCycle失败: pose维度与ids数量不一致 poseSize=${invalidPose.size} ids=${ids.size}")
            return
        }
        viewModelScope.launch {
            val data = MotionProtocolCodec.encodeCycleCreate(mode, ids, poses, durations, maxLoops)
            val success = bleService?.sendFrame(ProtocolFrameType.MOTION, data) == true
            Log.d(TAG, "创建MotionCycle: success=$success")
        }
    }

    fun startMotionCycle(index: Int) {
        viewModelScope.launch {
            val data = MotionProtocolCodec.encodeCycleStart(index)
            val success = bleService?.sendFrame(ProtocolFrameType.MOTION, data) == true
            Log.d(TAG, "启动MotionCycle: idx=$index success=$success")
        }
    }

    fun restartMotionCycle(index: Int) {
        viewModelScope.launch {
            val data = MotionProtocolCodec.encodeCycleRestart(index)
            val success = bleService?.sendFrame(ProtocolFrameType.MOTION, data) == true
            Log.d(TAG, "重启MotionCycle: idx=$index success=$success")
        }
    }

    fun pauseMotionCycle(index: Int) {
        viewModelScope.launch {
            val data = MotionProtocolCodec.encodeCyclePause(index)
            val success = bleService?.sendFrame(ProtocolFrameType.MOTION, data) == true
            Log.d(TAG, "暂停MotionCycle: idx=$index success=$success")
        }
    }

    fun releaseMotionCycle(index: Int) {
        viewModelScope.launch {
            val data = MotionProtocolCodec.encodeCycleRelease(index)
            val success = bleService?.sendFrame(ProtocolFrameType.MOTION, data) == true
            Log.d(TAG, "释放MotionCycle: idx=$index success=$success")
        }
    }

    fun requestMotionCycleStatus(index: Int) {
        viewModelScope.launch {
            val data = MotionProtocolCodec.encodeCycleGetStatus(index)
            val success = bleService?.sendFrame(ProtocolFrameType.MOTION, data) == true
            Log.d(TAG, "查询MotionCycle状态: idx=$index success=$success")
        }
    }

    fun previewServoValue(servoId: Int, mode: Int, value: Float) {
        if (servoId !in _uiState.value.servoList.indices) {
            Log.w(TAG, "预览舵机失败: 无效舵机ID $servoId")
            return
        }
        val pwmValue = if (mode == MotionProtocolCodec.MODE_PWM) {
            value.toInt().coerceIn(500, 2500)
        } else {
            angleToPwm(value.coerceIn(0f, 270f)).toInt()
        }
        viewModelScope.launch {
            val data = ServoProtocolCodec.encodeSetPwm(
                ServoSetPwmPayload(servoId = servoId, pwmValue = pwmValue, durationMs = 0)
            )
            val success = bleService?.sendFrame(ProtocolFrameType.SERVO, data) == true
            Log.d(TAG, "预览舵机: id=$servoId pwm=$pwmValue success=$success")
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
