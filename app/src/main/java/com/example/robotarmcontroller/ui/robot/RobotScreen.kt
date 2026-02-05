package com.example.robotarmcontroller.ui.robot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RobotScreen(
    state: RobotUiState,
    onPwmChange: (Int, Float) -> Unit,
    onPwmChangeFinished: (Int) -> Unit,
    onAngleChange: (Int, Float) -> Unit,
    onAngleChangeFinished: (Int) -> Unit,
    onToggleControlMode: () -> Unit,
    onSendTestClick: () -> Unit,
    onClearHistoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // 连接状态指示器和模式切换
        ConnectionStatusBar(
            isConnected = state.isConnected,
            connectionStatus = state.connectionStatus,
            controlMode = state.controlMode,
            onToggleControlMode = onToggleControlMode,
            onSendTestClick = onSendTestClick,
            onClearHistoryClick = onClearHistoryClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        if (state.isConnected) {
            // 舵机控制列表
            ServoControlList(
                servoList = state.servoList,
                controlMode = state.controlMode,
                onPwmChange = onPwmChange,
                onPwmChangeFinished = onPwmChangeFinished,
                onAngleChange = onAngleChange,
                onAngleChangeFinished = onAngleChangeFinished,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            )

            // 历史命令显示
            if (state.commandHistory.isNotEmpty()) {
                CommandHistoryPanel(
                    commandHistory = state.commandHistory,
                    onClearHistoryClick = onClearHistoryClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 150.dp)
                        .padding(8.dp)
                )
            }
        } else {
            DisconnectedState(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            )
        }
    }
}

@Composable
fun ConnectionStatusBar(
    isConnected: Boolean,
    connectionStatus: String,
    controlMode: ControlMode,
    onToggleControlMode: () -> Unit,
    onSendTestClick: () -> Unit,
    onClearHistoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // 第一行：连接状态和模式切换
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isConnected) Icons.Filled.CheckCircle else Icons.Filled.Error,
                        contentDescription = "连接状态",
                        tint = if (isConnected) Color.Green else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = connectionStatus,
                        color = if (isConnected) Color.Green else Color.Gray,
                        fontSize = 16.sp
                    )
                }

                // 模式切换按钮
                if (isConnected) {
                    Button(
                        onClick = onToggleControlMode,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (controlMode == ControlMode.PWM)
                                Color.Blue.copy(alpha = 0.1f)
                            else
                                Color.Green.copy(alpha = 0.1f),
                            contentColor = if (controlMode == ControlMode.PWM)
                                Color.Blue
                            else
                                Color.Green
                        )
                    ) {
                        Text(
                            text = if (controlMode == ControlMode.PWM) "PWM模式" else "角度模式",
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // 第二行：操作按钮
            if (isConnected) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSendTestClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Send, "发送测试", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("发送测试")
                    }

                    if (false) { // 暂时隐藏清空历史按钮
                        Button(
                            onClick = onClearHistoryClick,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Gray.copy(alpha = 0.1f),
                                contentColor = Color.Gray
                            )
                        ) {
                            Icon(Icons.Filled.ClearAll, "清空历史", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ServoControlList(
    servoList: List<ServoState>,
    controlMode: ControlMode,
    onPwmChange: (Int, Float) -> Unit,
    onPwmChangeFinished: (Int) -> Unit,
    onAngleChange: (Int, Float) -> Unit,
    onAngleChangeFinished: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        items(servoList) { servo ->
            ServoControlCard(
                servo = servo,
                controlMode = controlMode,
                onPwmChange = { onPwmChange(servo.id, it) },
                onPwmChangeFinished = { onPwmChangeFinished(servo.id) },
                onAngleChange = { onAngleChange(servo.id, it) },
                onAngleChangeFinished = { onAngleChangeFinished(servo.id) },
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

@Composable
fun ServoControlCard(
    modifier: Modifier = Modifier,
    servo: ServoState,
    controlMode: ControlMode,
    onPwmChange: (Float) -> Unit = {},
    onPwmChangeFinished: () -> Unit = {},
    onAngleChange: (Float) -> Unit = {},
    onAngleChangeFinished: () -> Unit = {},
) {
    Card(modifier = modifier.padding(2.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp)
        ) {
            // -------------------------------------------
            // ⭐ Slider + 中间文字 + 当前值
            // -------------------------------------------
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 根据控制模式选择滑块参数
                val (value, range, step) = if (controlMode == ControlMode.PWM) {
                    Triple(servo.pwm, 500f..2500f, 100f)
                } else {
                    Triple(servo.angle, 0f..270f, 10f)
                }

                Slider(
                    value = value,
                    onValueChange = { newValue ->
                        if (controlMode == ControlMode.PWM) {
                            onPwmChange(newValue)
                        } else {
                            onAngleChange(newValue)
                        }
                    },
                    onValueChangeFinished = {
                        if (controlMode == ControlMode.PWM) {
                            onPwmChangeFinished()
                        } else {
                            onAngleChangeFinished()
                        }
                    },
                    valueRange = range,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .height(35.dp)
                )

                // 舵机名称和当前值
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = servo.name,
                        color = Color.White
                    )
                }
            }

            // -------------------------------------------
            // ⭐ 图标按钮 + 输入框 + 图标按钮
            // -------------------------------------------
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .padding(horizontal = 20.dp)
            ) {
                // 根据控制模式选择步长
                val (smallStep, bigStep) = if (controlMode == ControlMode.PWM) {
                    Pair(10f, 100f)
                } else {
                    Pair(1f, 10f)
                }

                // 按钮：-bigStep
                IconButton(onClick = {
                    if (controlMode == ControlMode.PWM) {
                        onPwmChange(servo.pwm - bigStep)
                        onPwmChangeFinished()
                    } else {
                        onAngleChange(servo.angle - bigStep)
                        onAngleChangeFinished()
                    }
                }) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardDoubleArrowLeft,
                        contentDescription = "减少$bigStep"
                    )
                }

                // 按钮：-smallStep
                IconButton(onClick = {
                    if (controlMode == ControlMode.PWM) {
                        onPwmChange(servo.pwm - smallStep)
                        onPwmChangeFinished()
                    } else {
                        onAngleChange(servo.angle - smallStep)
                        onAngleChangeFinished()
                    }
                }) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowLeft,
                        contentDescription = "减少$smallStep"
                    )
                }

                // 输入框
                val currentValue = if (controlMode == ControlMode.PWM) servo.pwm else servo.angle
                var text by remember { mutableStateOf(currentValue.toInt().toString()) }

                LaunchedEffect(currentValue) {
                    text = currentValue.toInt().toString()
                }

                BasicTextField(
                    value = text,
                    singleLine = true,
                    onValueChange = {
                        text = it
                        val v = it.toFloatOrNull()
                        if (v != null) {
                            if (controlMode == ControlMode.PWM) {
                                onPwmChange(v)
                            } else {
                                onAngleChange(v)
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        if (controlMode == ControlMode.PWM) {
                            onPwmChangeFinished()
                        } else {
                            onAngleChangeFinished()
                        }
                    }),
                    textStyle = LocalTextStyle.current.copy(
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier
                        .width(70.dp)
                        .background(Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(vertical = 4.dp)
                )

                // 按钮：+smallStep
                IconButton(onClick = {
                    if (controlMode == ControlMode.PWM) {
                        onPwmChange(servo.pwm + smallStep)
                        onPwmChangeFinished()
                    } else {
                        onAngleChange(servo.angle + smallStep)
                        onAngleChangeFinished()
                    }
                }) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowRight,
                        contentDescription = "增加$smallStep"
                    )
                }

                // 按钮：+bigStep
                IconButton(onClick = {
                    if (controlMode == ControlMode.PWM) {
                        onPwmChange(servo.pwm + bigStep)
                        onPwmChangeFinished()
                    } else {
                        onAngleChange(servo.angle + bigStep)
                        onAngleChangeFinished()
                    }
                }) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardDoubleArrowRight,
                        contentDescription = "增加$bigStep"
                    )
                }
            }

            // 模式信息提示
            Text(
                text = if (controlMode == ControlMode.PWM) {
                    "PWM范围: 500-2500"
                } else {
                    "角度范围: 0-270°"
                },
                fontSize = 10.sp,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
fun CommandHistoryPanel(
    commandHistory: List<ServoCommand>,
    onClearHistoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "最近命令 (${commandHistory.size})",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )

                if (commandHistory.isNotEmpty()) {
                    TextButton(onClick = onClearHistoryClick) {
                        Text("清空", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(commandHistory.takeLast(5).reversed()) { command ->
                    val modeText = if (command.angleValue != null) {
                        "舵机${command.servoId}: ${command.pwmValue} PWM (${command.angleValue}°)"
                    } else {
                        "舵机${command.servoId}: ${command.pwmValue} PWM"
                    }
                    Text(
                        text = modeText,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DisconnectedState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.BluetoothDisabled,
                contentDescription = "未连接",
                tint = Color.Gray,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "请先连接蓝牙设备",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray
            )
        }
    }
}