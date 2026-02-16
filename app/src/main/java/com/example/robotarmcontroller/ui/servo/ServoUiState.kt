package com.example.robotarmcontroller.ui.servo

import com.example.robotarmcontroller.data.model.ServoState
import com.example.robotarmcontroller.data.model.ServoCommand

// 舵机控制模式枚举
enum class ServoControlMode {
    PWM,  // PWM模式 (500-2500)
    ANGLE // 角度模式 (0-270度)
}

data class ServoUiState(
    // 舵机状态列表
    val servoList: List<ServoState> = emptyList(),
    
    // 连接状态
    val isConnected: Boolean = false,
    
    // 最后发送的命令
    val lastCommandSent: ServoCommand? = null,
    
    // 命令历史
    val commandHistory: List<ServoCommand> = emptyList(),
    
    // 连接状态文本
    val connectionStatus: String = "未连接",
    
    // 控制模式
    val controlMode: ServoControlMode = ServoControlMode.PWM
)