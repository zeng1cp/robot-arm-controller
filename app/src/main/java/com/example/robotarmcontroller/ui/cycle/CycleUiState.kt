package com.example.robotarmcontroller.ui.cycle

import com.example.robotarmcontroller.data.model.CycleInfo

/**
 * Cycle 模块 UI 状态
 */
data class CycleUiState(
    // Cycle 列表
    val cycleList: List<CycleInfo> = emptyList(),

    // 选中的 Cycle
    val selectedCycle: CycleInfo? = null,

    // Cycle 运行状态
    val cycleRunning: Boolean = false,

    // 当前循环计数
    val currentLoop: Int = 0,

    // 错误信息
    val errorMessage: String? = null,

    // 创建新 Cycle 的状态
    val creationState: CycleCreationState = CycleCreationState(),

    // 正在删除的 Cycle 索引（用于确认对话框）
    val deletingCycleIndex: Int? = null,

    // 正在编辑的 Cycle 索引（-1 表示未编辑）
    val editingCycleIndex: Int = -1,

    // 编辑中的 Cycle 参数（从现有 Cycle 加载）
    val editingCycleState: CycleCreationState? = null
)

/**
 * Cycle 创建状态
 */
data class CycleCreationState(
    // 创建模式 (0=PWM, 1=角度)
    val mode: Int = 0,

    // 选中的舵机 ID 列表
    val selectedServoIds: List<Int> = emptyList(),

    // 姿态列表 (每个姿态是舵机值的列表)
    val poses: List<List<Float>> = emptyList(),

    // 每个姿态的持续时间 (ms)
    val durations: List<Int> = emptyList(),

    // 最大循环次数
    val maxLoops: Int = 1,

    // 当前编辑的姿态索引 (-1 表示未编辑)
    val editingPoseIndex: Int = -1,

    // 当前姿态的舵机值映射
    val currentPoseValues: Map<Int, Float> = emptyMap()
)

/**
 * Cycle 控制模式
 */
enum class CycleControlMode {
    PWM,    // PWM 模式
    ANGLE   // 角度模式
}

/**
 * Cycle 运行状态
 */
enum class CycleStatus {
    IDLE,       // 空闲
    RUNNING,    // 运行中
    PAUSED,     // 已暂停
    COMPLETED,  // 已完成
    ERROR       // 错误
}