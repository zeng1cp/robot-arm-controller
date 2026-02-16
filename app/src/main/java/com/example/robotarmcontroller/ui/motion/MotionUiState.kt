package com.example.robotarmcontroller.ui.motion

// 运动控制模式枚举
enum class MotionControlMode {
    PWM,    // PWM模式
    ANGLE   // 角度模式
}

data class MotionUiState(
    // 当前运动组ID
    val motionGroupId: Int = 0,
    
    // 已完成的运动组ID
    val motionCompleteGroupId: Int = 0,
    
    // 连接状态
    val isConnected: Boolean = false,
    
    // 运动预设列表
    val motionPresets: List<MotionPreset> = emptyList(),
    
    // 选中的运动预设
    val selectedPreset: MotionPreset? = null,
    
    // 运动状态
    val motionStatus: MotionStatus = MotionStatus.IDLE,
    
    // 错误信息
    val errorMessage: String? = null
)

// 运动状态
enum class MotionStatus {
    IDLE,           // 空闲
    PREPARING,      // 准备中
    RUNNING,        // 运行中
    PAUSED,         // 已暂停
    COMPLETED,      // 已完成
    ERROR           // 错误
}

// 运动预设
data class MotionPreset(
    val id: Int,
    val name: String,
    val mode: Int,
    val durationMs: Int,
    val servoIds: List<Int>,
    val values: List<Float>,
    val realtime: Boolean = false,
    val status: MotionPresetStatus? = null,
    val groupId: Int = 0,
    val startedAtMs: Long? = null,
    val elapsedMs: Long = 0
)

// 运动预设状态
enum class MotionPresetStatus {
    PENDING,    // 待执行
    RUNNING,    // 执行中
    PAUSED      // 已暂停
}