package com.example.robotarmcontroller.ui.cycle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.robotarmcontroller.data.model.CycleInfo
import com.example.robotarmcontroller.data.CycleRepository
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
class CycleViewModel @Inject constructor(
    private val cycleRepository: CycleRepository,
    private val servoRepository: ServoRepository
) : ViewModel() {

    // UI 状态
    private val _uiState = MutableStateFlow(CycleUiState())
    val uiState: StateFlow<CycleUiState> = _uiState.asStateFlow()

    // 舵机状态列表（用于创建 Cycle 时选择舵机）
    val servoStates: StateFlow<List<ServoState>> = servoRepository.servoStates

    // Cycle 列表（直接暴露，方便其他屏幕使用）
    val cycleList: StateFlow<List<CycleInfo>> = cycleRepository.cycleList

    init {
        viewModelScope.launch {
            // 合并 Cycle 列表和舵机状态
            combine(
                cycleRepository.cycleList,
                servoRepository.servoStates
            ) { cycleList, servoStates ->
                CycleUiState(
                    cycleList = cycleList,
                    errorMessage = null,
                    creationState = _uiState.value.creationState.copy() // 保持现有创建状态
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    // ========== Cycle 操作 ==========

    fun createCycle(
        mode: Int,
        ids: List<Int>,
        poses: List<List<Float>>,
        durations: List<Int>,
        maxLoops: Int
    ) {
        cycleRepository.createCycle(mode, ids, poses, durations, maxLoops)
    }

    fun startCycle(index: Int) {
        cycleRepository.startCycle(index)
    }

    fun restartCycle(index: Int) {
        cycleRepository.restartCycle(index)
    }

    fun pauseCycle(index: Int) {
        cycleRepository.pauseCycle(index)
    }

    fun releaseCycle(index: Int) {
        cycleRepository.releaseCycle(index)
    }

    fun requestCycleStatus(index: Int) {
        cycleRepository.requestCycleStatus(index)
    }

    fun requestCycleList() {
        cycleRepository.requestCycleList()
    }

    // ========== 创建状态管理 ==========

    fun updateCreationMode(mode: Int) {
        _uiState.update { current ->
            current.copy(creationState = current.creationState.copy(mode = mode))
        }
    }

    fun updateSelectedServoIds(ids: List<Int>) {
        _uiState.update { current ->
            current.copy(creationState = current.creationState.copy(selectedServoIds = ids))
        }
    }

    fun addPose(values: Map<Int, Float>, durationMs: Int) {
        _uiState.update { current ->
            val newPoses = current.creationState.poses.toMutableList()
            val sortedValues = current.creationState.selectedServoIds.map { servoId ->
                values[servoId] ?: 0f
            }
            newPoses.add(sortedValues)
            val newDurations = current.creationState.durations + durationMs
            current.copy(creationState = current.creationState.copy(
                poses = newPoses,
                durations = newDurations,
                editingPoseIndex = -1,
                currentPoseValues = emptyMap()
            ))
        }
    }

    fun updatePose(index: Int, values: Map<Int, Float>, durationMs: Int) {
        _uiState.update { current ->
            val newPoses = current.creationState.poses.toMutableList()
            val sortedValues = current.creationState.selectedServoIds.map { servoId ->
                values[servoId] ?: 0f
            }
            if (index < newPoses.size) {
                newPoses[index] = sortedValues
            }
            val newDurations = current.creationState.durations.toMutableList()
            if (index < newDurations.size) {
                newDurations[index] = durationMs
            }
            current.copy(creationState = current.creationState.copy(
                poses = newPoses,
                durations = newDurations,
                editingPoseIndex = -1,
                currentPoseValues = emptyMap()
            ))
        }
    }

    fun removePose(index: Int) {
        _uiState.update { current ->
            val newPoses = current.creationState.poses.toMutableList()
            val newDurations = current.creationState.durations.toMutableList()
            if (index < newPoses.size) {
                newPoses.removeAt(index)
                newDurations.removeAt(index)
            }
            current.copy(creationState = current.creationState.copy(
                poses = newPoses,
                durations = newDurations
            ))
        }
    }

    fun setEditingPose(index: Int) {
        _uiState.update { current ->
            val poseValues = if (index >= 0 && index < current.creationState.poses.size) {
                val pose = current.creationState.poses[index]
                current.creationState.selectedServoIds.mapIndexed { idx, servoId ->
                    servoId to pose.getOrElse(idx) { 0f }
                }.toMap()
            } else {
                emptyMap()
            }
            current.copy(creationState = current.creationState.copy(
                editingPoseIndex = index,
                currentPoseValues = poseValues
            ))
        }
    }

    fun updateCurrentPoseValue(servoId: Int, value: Float) {
        _uiState.update { current ->
            val newValues = current.creationState.currentPoseValues.toMutableMap()
            newValues[servoId] = value
            current.copy(creationState = current.creationState.copy(
                currentPoseValues = newValues
            ))
        }
    }

    fun clearCreationState() {
        _uiState.update { current ->
            current.copy(creationState = CycleCreationState())
        }
    }

    // ========== 删除与编辑 ==========

    fun deleteCycle(index: Int) {
        // 发送释放命令
        cycleRepository.releaseCycle(index)
        // 乐观更新：从本地列表中移除
        _uiState.update { current ->
            val newList = current.cycleList.filter { it.index != index }
            current.copy(cycleList = newList, deletingCycleIndex = null)
        }
    }

    fun confirmDeleteCycle(index: Int?) {
        _uiState.update { current ->
            current.copy(deletingCycleIndex = index)
        }
    }

    fun startEditingCycle(index: Int) {
        val cycle = _uiState.value.cycleList.find { it.index == index }
        if (cycle != null) {
            // 由于 CycleInfo 不包含完整的创建参数，我们无法加载完整状态
            // 这里只设置编辑索引，UI 可以显示提示
            _uiState.update { current ->
                current.copy(editingCycleIndex = index, editingCycleState = null)
            }
        }
    }

    fun cancelEditingCycle() {
        _uiState.update { current ->
            current.copy(editingCycleIndex = -1, editingCycleState = null)
        }
    }

    override fun onCleared() {
        cycleRepository.close()
        super.onCleared()
    }
}