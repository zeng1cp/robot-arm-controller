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
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    onClearHistoryClick: () -> Unit,
    onServoEnableClick: () -> Unit,
    onServoDisableClick: () -> Unit,
    onSyncAllServoStatusClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        RobotActionBar(
            isConnected = state.isConnected,
            controlMode = state.controlMode,
            onToggleControlMode = onToggleControlMode,
            onServoEnableClick = onServoEnableClick,
            onServoDisableClick = onServoDisableClick,
            onSyncAllServoStatusClick = onSyncAllServoStatusClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        if (state.isConnected) {
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
fun RobotScreen(
    state: RobotUiState,
    onPwmChange: (Int, Float) -> Unit,
    onPwmChangeFinished: (Int) -> Unit,
    onAngleChange: (Int, Float) -> Unit,
    onAngleChangeFinished: (Int) -> Unit,
    onToggleControlMode: () -> Unit,
    onSendTestClick: () -> Unit,
    onClearHistoryClick: () -> Unit,
    onServoEnableClick: () -> Unit,
    onServoDisableClick: () -> Unit,
    onRequestServoStatusClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    RobotScreen(
        state = state,
        onPwmChange = onPwmChange,
        onPwmChangeFinished = onPwmChangeFinished,
        onAngleChange = onAngleChange,
        onAngleChangeFinished = onAngleChangeFinished,
        onToggleControlMode = onToggleControlMode,
        onClearHistoryClick = onClearHistoryClick,
        onServoEnableClick = onServoEnableClick,
        onServoDisableClick = onServoDisableClick,
        onSyncAllServoStatusClick = {
            state.servoList.forEach { onRequestServoStatusClick(it.id) }
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RobotActionBar(
    isConnected: Boolean,
    controlMode: ControlMode,
    onToggleControlMode: () -> Unit,
    onServoEnableClick: () -> Unit,
    onServoDisableClick: () -> Unit,
    onSyncAllServoStatusClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onToggleControlMode, enabled = isConnected) {
                Text(if (controlMode == ControlMode.PWM) "PWM" else "角度")
            }
            Button(onClick = onServoEnableClick, enabled = isConnected) { Text("使能") }
            Button(onClick = onServoDisableClick, enabled = isConnected) { Text("失能") }
            OutlinedButton(onClick = onSyncAllServoStatusClick, enabled = isConnected) { Text("同步") }
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
    onAngleChangeFinished: () -> Unit = {}
) {
    Card(
        modifier = modifier.padding(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                val (value, range) = if (controlMode == ControlMode.PWM) {
                    Pair(servo.pwm, 500f..2500f)
                } else {
                    Pair(servo.angle, 0f..270f)
                }

                androidx.compose.material3.Slider(
                    value = value,
                    onValueChange = { newValue ->
                        if (controlMode == ControlMode.PWM) onPwmChange(newValue) else onAngleChange(newValue)
                    },
                    onValueChangeFinished = {
                        if (controlMode == ControlMode.PWM) onPwmChangeFinished() else onAngleChangeFinished()
                    },
                    valueRange = range,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .height(35.dp)
                )

                Text(text = servo.name, color = Color.White)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .padding(horizontal = 20.dp)
            ) {
                val (smallStep, bigStep) = if (controlMode == ControlMode.PWM) Pair(10f, 100f) else Pair(1f, 10f)

                IconButton(onClick = {
                    if (controlMode == ControlMode.PWM) { onPwmChange(servo.pwm - bigStep); onPwmChangeFinished() }
                    else { onAngleChange(servo.angle - bigStep); onAngleChangeFinished() }
                }) { Icon(Icons.Filled.KeyboardDoubleArrowLeft, contentDescription = "减少$bigStep") }

                IconButton(onClick = {
                    if (controlMode == ControlMode.PWM) { onPwmChange(servo.pwm - smallStep); onPwmChangeFinished() }
                    else { onAngleChange(servo.angle - smallStep); onAngleChangeFinished() }
                }) { Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "减少$smallStep") }

                val currentValue = if (controlMode == ControlMode.PWM) servo.pwm else servo.angle
                var text by remember { mutableStateOf(currentValue.toInt().toString()) }
                LaunchedEffect(currentValue) { text = currentValue.toInt().toString() }

                BasicTextField(
                    value = text,
                    singleLine = true,
                    onValueChange = {
                        text = it
                        val v = it.toFloatOrNull() ?: return@BasicTextField
                        if (controlMode == ControlMode.PWM) onPwmChange(v) else onAngleChange(v)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    keyboardActions = KeyboardActions(onDone = {
                        if (controlMode == ControlMode.PWM) onPwmChangeFinished() else onAngleChangeFinished()
                    }),
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                    modifier = Modifier
                        .width(70.dp)
                        .background(Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(vertical = 4.dp)
                )

                IconButton(onClick = {
                    if (controlMode == ControlMode.PWM) { onPwmChange(servo.pwm + smallStep); onPwmChangeFinished() }
                    else { onAngleChange(servo.angle + smallStep); onAngleChangeFinished() }
                }) { Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "增加$smallStep") }

                IconButton(onClick = {
                    if (controlMode == ControlMode.PWM) { onPwmChange(servo.pwm + bigStep); onPwmChangeFinished() }
                    else { onAngleChange(servo.angle + bigStep); onAngleChangeFinished() }
                }) { Icon(Icons.Filled.KeyboardDoubleArrowRight, contentDescription = "增加$bigStep") }
            }

        }
    }
}


@Composable
fun ServoControlCard(
    modifier: Modifier,
    servo: ServoState,
    controlMode: ControlMode,
    onPwmChange: (Float) -> Unit,
    onPwmChangeFinished: () -> Unit,
    onAngleChange: (Float) -> Unit,
    onAngleChangeFinished: () -> Unit,
    onRequestStatus: () -> Unit
) {
    ServoControlCard(
        modifier = modifier,
        servo = servo,
        controlMode = controlMode,
        onPwmChange = onPwmChange,
        onPwmChangeFinished = onPwmChangeFinished,
        onAngleChange = onAngleChange,
        onAngleChangeFinished = onAngleChangeFinished
    )
}

@Composable
fun CommandHistoryPanel(
    commandHistory: List<ServoCommand>,
    onClearHistoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "最近命令 (${commandHistory.size})", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                if (commandHistory.isNotEmpty()) {
                    TextButton(onClick = onClearHistoryClick) {
                        Icon(Icons.Filled.ClearAll, contentDescription = "清空")
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("清空", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(commandHistory.takeLast(5).reversed()) { command ->
                    val modeText = if (command.angleValue != null) {
                        "舵机${command.servoId}: ${command.pwmValue} PWM (${command.angleValue}°)"
                    } else {
                        "舵机${command.servoId}: ${command.pwmValue} PWM"
                    }
                    Text(text = modeText, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
fun DisconnectedState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(
                imageVector = Icons.Filled.BluetoothDisabled,
                contentDescription = "未连接",
                tint = Color.Gray,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "请先连接蓝牙设备", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
        }
    }
}
