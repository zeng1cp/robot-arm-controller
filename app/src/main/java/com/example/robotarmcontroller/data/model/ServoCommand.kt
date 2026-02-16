package com.example.robotarmcontroller.data.model

/**
 * 舵机命令历史数据模型
 * @param servoId 舵机ID
 * @param pwmValue PWM值 (500-2500)
 * @param angleValue 角度值 (0-270)，可选
 * @param timestamp 时间戳（毫秒）
 */
data class ServoCommand(
    val servoId: Int,
    val pwmValue: Int,
    val angleValue: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
)