package com.example.robotarmcontroller.ui.arm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.robotarmcontroller.data.ServoRepository
import com.example.robotarmcontroller.data.MotionRepository
import com.example.robotarmcontroller.protocol.MotionProtocolCodec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArmViewModel @Inject constructor(
    private val servoRepository: ServoRepository,
    private val motionRepository: MotionRepository
) : ViewModel() {

    // UI 状态
    private val _uiState = MutableStateFlow(ArmUiState())
    val uiState: StateFlow<ArmUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // 合并舵机状态与运动组ID
            combine(
                servoRepository.servoStates,
                motionRepository.motionGroupId
            ) { servoStates, motionGroupId ->
                ArmUiState(
                    servoStates = servoStates,
                    isConnected = true, // TODO: 从蓝牙仓库获取连接状态
                    cooperativeMode = motionGroupId > 0
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    // 执行预设动作
    fun executePreset(preset: ArmPreset) {
        // 将预设转换为运动指令
        val mode = if (preset.mode == ArmPresetMode.PWM) MotionProtocolCodec.MODE_PWM else MotionProtocolCodec.MODE_ANGLE
        val ids = preset.servoTargets.keys.toList()
        val values = preset.servoTargets.values.toList()
        motionRepository.startMotion(mode, ids, values, preset.durationMs)
    }

    // 停止所有运动
    fun stopAllMotion() {
        motionRepository.stopMotion(_uiState.value.servoStates.firstOrNull()?.id ?: 0)
    }

    // 选择预设
    fun selectPreset(preset: ArmPreset?) {
        _uiState.update { current ->
            current.copy(selectedPreset = preset)
        }
    }

    // 切换协同模式
    fun toggleCooperativeMode() {
        _uiState.update { current ->
            current.copy(cooperativeMode = !current.cooperativeMode)
        }
    }

    override fun onCleared() {
        // 可选的清理操作
        super.onCleared()
    }
}