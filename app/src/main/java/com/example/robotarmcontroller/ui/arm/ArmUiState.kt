package com.example.robotarmcontroller.ui.arm

import com.example.robotarmcontroller.data.model.ServoState

/**
 * 机械臂整体控制模块 UI 状态
 * 此模块用于协调多个舵机与运动控制，提供高级操作（如预设动作、协同运动）
 */
data class ArmUiState(
    // 所有舵机状态（来自 ServoRepository）
    val servoStates: List<ServoState> = emptyList(),

    // 当前选中的预设动作
    val selectedPreset: ArmPreset? = null,

    // 协同运动是否激活
    val cooperativeMode: Boolean = false,

    // 错误信息
    val errorMessage: String? = null,

    // 连接状态
    val isConnected: Boolean = false
)

/**
 * 机械臂预设动作
 */
data class ArmPreset(
    val id: Int,
    val name: String,
    val description: String? = null,
    val servoTargets: Map<Int, Float>, // 舵机ID -> 目标值（PWM或角度）
    val durationMs: Int = 1000,
    val mode: ArmPresetMode = ArmPresetMode.PWM
)

/**
 * 预设模式
 */
enum class ArmPresetMode {
    PWM,   // PWM模式
    ANGLE  // 角度模式
}