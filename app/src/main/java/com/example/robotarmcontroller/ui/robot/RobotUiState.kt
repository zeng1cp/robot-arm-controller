package com.example.robotarmcontroller.ui.robot

// 控制模式枚举
enum class ControlMode {
    PWM,  // PWM模式 (500-2500)
    ANGLE // 角度模式 (0-270度)
}

data class RobotUiState(
    val servoList: List<ServoState> = emptyList(),
    val isConnected: Boolean = false,
    val lastCommandSent: ServoCommand? = null,
    val commandHistory: List<ServoCommand> = emptyList(),
    val connectionStatus: String = "未连接",
    val controlMode: ControlMode = ControlMode.PWM, // 新增：控制模式
    val motionGroupId: Int = 0,
    val motionCompleteGroupId: Int = 0
)

data class ServoState(
    val id: Int = 0,
    val name: String = "",
    val pwm: Float = 1500f,      // PWM值 (500-2500)
    val angle: Float = 135f,     // 角度值 (0-270)
    val isMoving: Boolean = false
)

data class ServoCommand(
    val servoId: Int,
    val pwmValue: Int,
    val angleValue: Int? = null, // 可选的角度值
    val timestamp: Long = System.currentTimeMillis()
)
