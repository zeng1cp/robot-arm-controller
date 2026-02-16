package com.example.robotarmcontroller.data.model

/**
 * 舵机状态数据模型（纯数据类）
 * @param id 舵机ID (0-5)
 * @param name 舵机名称
 * @param pwm PWM值 (500-2500)
 * @param angle 角度值 (0-270)
 * @param isMoving 是否正在移动
 */
data class ServoState(
    val id: Int = 0,
    val name: String = "",
    val pwm: Float = 1500f,
    val angle: Float = 135f,
    val isMoving: Boolean = false
)