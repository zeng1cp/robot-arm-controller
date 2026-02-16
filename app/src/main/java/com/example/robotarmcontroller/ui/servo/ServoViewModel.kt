package com.example.robotarmcontroller.ui.servo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.robotarmcontroller.data.ServoRepository
import com.example.robotarmcontroller.data.model.ServoCommand
import com.example.robotarmcontroller.data.model.ServoState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServoViewModel @Inject constructor(
    private val servoRepository: ServoRepository
) : ViewModel() {

    // UI 状态
    private val _uiState = MutableStateFlow(ServoUiState())
    val uiState: StateFlow<ServoUiState> = _uiState.asStateFlow()

    // 命令历史（直接暴露）
    val commandHistory: StateFlow<List<ServoCommand>> = servoRepository.commandHistory

    init {
        viewModelScope.launch {
            // 合并舵机状态与命令历史
            combine(
                servoRepository.servoStates,
                servoRepository.commandHistory
            ) { servoList, history ->
                ServoUiState(
                    servoList = servoList,
                    isConnected = true, // TODO: 从蓝牙仓库获取连接状态
                    commandHistory = history,
                    controlMode = _uiState.value.controlMode,
                    lastCommandSent = history.lastOrNull()
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    // ========== UI 事件处理 ==========

    fun toggleControlMode() {
        _uiState.update { current ->
            val newMode = if (current.controlMode == ServoControlMode.PWM) ServoControlMode.ANGLE else ServoControlMode.PWM
            current.copy(controlMode = newMode)
        }
    }

    fun onPwmChange(servoId: Int, pwm: Float) {
        // 仅更新本地预览值，不发送
        _uiState.update { current ->
            val newList = current.servoList.map {
                if (it.id == servoId) it.copy(pwm = pwm, angle = pwmToAngle(pwm.toInt())) else it
            }
            current.copy(servoList = newList)
        }
    }

    fun onPwmChangeFinished(servoId: Int) {
        val servo = _uiState.value.servoList.firstOrNull { it.id == servoId } ?: return
        val pwm = servo.pwm.toInt().coerceIn(500, 2500)
        servoRepository.sendPwmCommand(servoId, pwm)
        servoRepository.addCommandToHistory(servoId, pwm, servo.angle.toInt())
    }

    fun onAngleChange(servoId: Int, angle: Float) {
        _uiState.update { current ->
            val newList = current.servoList.map {
                if (it.id == servoId) it.copy(angle = angle, pwm = angleToPwm(angle)) else it
            }
            current.copy(servoList = newList)
        }
    }

    fun onAngleChangeFinished(servoId: Int) {
        val servo = _uiState.value.servoList.firstOrNull { it.id == servoId } ?: return
        val angle = servo.angle.coerceIn(0f, 270f)
        val pwm = angleToPwm(angle).toInt()
        servoRepository.sendPwmCommand(servoId, pwm)
        servoRepository.addCommandToHistory(servoId, pwm, angle.toInt())
    }

    fun setServoEnable() {
        servoRepository.setServoEnable(true)
    }

    fun setServoDisable() {
        servoRepository.setServoEnable(false)
    }

    fun sendServosHome() {
        servoRepository.sendServosHome()
    }

    fun requestAllServoStatus() {
        servoRepository.requestAllServoStatus()
    }

    fun requestServoStatus(servoId: Int) {
        servoRepository.requestServoStatus(servoId)
    }

    fun clearHistory() {
        servoRepository.clearHistory()
    }

    // 辅助转换函数（用于预览）
    private fun pwmToAngle(pwm: Int): Float = ((pwm - 500) / 2000f) * 270f
    private fun angleToPwm(angle: Float): Float = 500f + (angle / 270f) * 2000f

    override fun onCleared() {
        servoRepository.close()
        super.onCleared()
    }
}