// ui/robot/RobotViewModel.kt
package com.example.robotarmcontroller.ui.robot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.robotarmcontroller.data.CycleInfo
import com.example.robotarmcontroller.data.RobotRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RobotViewModel @Inject constructor(
    private val repository: RobotRepository
) : ViewModel() {

    // UI 状态聚合
    private val _uiState = MutableStateFlow(RobotUiState())
    val uiState: StateFlow<RobotUiState> = _uiState.asStateFlow()

    // 单独暴露 cycleList 供其他组件使用（如果需要）
    val cycleList: StateFlow<List<CycleInfo>> = repository.cycleList

    init {
        viewModelScope.launch {
            // 合并多个 Flow 更新 UI 状态
            combine(
                repository.servoStates,
                repository.motionGroupId,
                repository.motionCompleteGroupId,
                repository.commandHistory
            ) { servoList, motionGroupId, motionCompleteGroupId, history ->
                RobotUiState(
                    servoList = servoList,
                    isConnected = true, // 连接状态可从别处获取，或从 repository 添加
                    commandHistory = history,
                    controlMode = _uiState.value.controlMode, // 保持用户选择的模式
                    motionGroupId = motionGroupId,
                    motionCompleteGroupId = motionCompleteGroupId
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    // ========== UI 事件处理 ==========

    fun toggleControlMode() {
        _uiState.update { current ->
            val newMode = if (current.controlMode == ControlMode.PWM) ControlMode.ANGLE else ControlMode.PWM
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
        repository.sendPwmCommand(servoId, pwm)
        repository.addCommandToHistory(servoId, pwm, servo.angle.toInt())
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
        repository.sendPwmCommand(servoId, pwm)
        repository.addCommandToHistory(servoId, pwm, angle.toInt())
    }

    fun setServoEnable() {
        repository.setServoEnable(true)
    }

    fun setServoDisable() {
        repository.setServoEnable(false)
    }

    fun setAllServoHome() {
        repository.sendServosHome()
    }

    fun requestAllServoStatus() {
        repository.requestAllServoStatus()
    }

    fun requestServoStatus(servoId: Int) {
        repository.requestServoStatus(servoId)
    }

    // Motion 相关
    fun startMotion(mode: Int, ids: List<Int>, values: List<Float>, durationMs: Int) {
        repository.startMotion(mode, ids, values, durationMs)
    }

    fun stopMotion(groupId: Int) {
        repository.stopMotion(groupId)
    }

    fun pauseMotion(groupId: Int) {
        repository.pauseMotion(groupId)
    }

    fun resumeMotion(groupId: Int) {
        repository.resumeMotion(groupId)
    }

    fun requestMotionStatus(groupId: Int) {
        repository.requestMotionStatus(groupId)
    }

    fun previewServoValue(servoId: Int, mode: Int, value: Float) {
        repository.previewServoValue(servoId, mode, value)
    }

    fun createMotionCycle(mode: Int, ids: List<Int>, poses: List<List<Float>>, durations: List<Int>, maxLoops: Int) {
        repository.createMotionCycle(mode, ids, poses, durations, maxLoops)
    }

    fun startMotionCycle(index: Int) {
        repository.startMotionCycle(index)
    }

    fun restartMotionCycle(index: Int) {
        repository.restartMotionCycle(index)
    }

    fun pauseMotionCycle(index: Int) {
        repository.pauseMotionCycle(index)
    }

    fun releaseMotionCycle(index: Int) {
        repository.releaseMotionCycle(index)
    }

    fun requestMotionCycleStatus(index: Int) {
        repository.requestMotionCycleStatus(index)
    }

    fun requestCycleList() {
        repository.requestCycleList()
    }

    fun clearHistory() {
        repository.clearHistory()
    }

    // 辅助转换函数（用于预览）
    private fun pwmToAngle(pwm: Int): Float = ((pwm - 500) / 2000f) * 270f
    private fun angleToPwm(angle: Float): Float = 500f + (angle / 270f) * 2000f

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }
}