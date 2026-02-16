package com.example.robotarmcontroller.ui.motion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.robotarmcontroller.data.MotionRepository
import com.example.robotarmcontroller.data.ServoRepository
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
class MotionViewModel @Inject constructor(
    private val motionRepository: MotionRepository,
    private val servoRepository: ServoRepository
) : ViewModel() {

    // UI 状态
    private val _uiState = MutableStateFlow(MotionUiState())
    val uiState: StateFlow<MotionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // 合并运动状态（不再包含 Cycle 列表）
            combine(
                motionRepository.motionGroupId,
                motionRepository.motionCompleteGroupId
            ) { motionGroupId, motionCompleteGroupId ->
                MotionUiState(
                    motionGroupId = motionGroupId,
                    motionCompleteGroupId = motionCompleteGroupId,
                    isConnected = true, // TODO: 从蓝牙仓库获取连接状态
                    motionPresets = _uiState.value.motionPresets, // 保持现有预设
                    selectedPreset = _uiState.value.selectedPreset,
                    motionStatus = calculateMotionStatus(motionGroupId, motionCompleteGroupId)
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    // ========== UI 事件处理 ==========

    fun startMotion(mode: Int, ids: List<Int>, values: List<Float>, durationMs: Int) {
        motionRepository.startMotion(mode, ids, values, durationMs)
    }

    fun stopMotion(groupId: Int) {
        motionRepository.stopMotion(groupId)
    }

    fun pauseMotion(groupId: Int) {
        motionRepository.pauseMotion(groupId)
    }

    fun resumeMotion(groupId: Int) {
        motionRepository.resumeMotion(groupId)
    }

    fun requestMotionStatus(groupId: Int) {
        motionRepository.requestMotionStatus(groupId)
    }


    fun previewServoValue(servoId: Int, mode: Int, value: Float) {
        servoRepository.previewServoValue(servoId, mode, value)
    }

    // 预设管理（本地操作）
    fun addMotionPreset(preset: MotionPreset) {
        _uiState.update { current ->
            current.copy(motionPresets = current.motionPresets + preset)
        }
    }

    fun removeMotionPreset(id: Int) {
        _uiState.update { current ->
            current.copy(motionPresets = current.motionPresets.filter { it.id != id })
        }
    }

    fun selectPreset(preset: MotionPreset?) {
        _uiState.update { current ->
            current.copy(selectedPreset = preset)
        }
    }

    fun updatePresetStatus(presetId: Int, status: MotionPresetStatus?, groupId: Int = 0, startedAtMs: Long? = null) {
        _uiState.update { current ->
            val updated = current.motionPresets.map {
                if (it.id == presetId) it.copy(
                    status = status,
                    groupId = groupId,
                    startedAtMs = startedAtMs ?: it.startedAtMs
                ) else it
            }
            current.copy(motionPresets = updated)
        }
    }

    // 辅助函数
    private fun calculateMotionStatus(groupId: Int, completeGroupId: Int): MotionStatus {
        return when {
            groupId > 0 && completeGroupId == 0 -> MotionStatus.RUNNING
            groupId > 0 && completeGroupId == groupId -> MotionStatus.COMPLETED
            else -> MotionStatus.IDLE
        }
    }

    override fun onCleared() {
        motionRepository.close()
        super.onCleared()
    }
}