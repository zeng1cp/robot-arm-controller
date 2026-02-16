package com.example.robotarmcontroller.ui.servo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.robotarmcontroller.data.model.CycleInfo
import com.example.robotarmcontroller.data.model.ServoState

@Composable
fun ServoScreen(
    state: ServoUiState,
    onPwmChange: (servoId: Int, pwm: Float) -> Unit,
    onPwmChangeFinished: (servoId: Int) -> Unit,
    onAngleChange: (servoId: Int, angle: Float) -> Unit,
    onAngleChangeFinished: (servoId: Int) -> Unit,
    onToggleControlMode: () -> Unit,
    onClearHistoryClick: () -> Unit,
    onServoEnableClick: () -> Unit,
    onServoDisableClick: () -> Unit,
    onAllServoHomeClick: () -> Unit,
    onSyncAllServoStatusClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedServoId by remember { mutableStateOf(0) }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "舵机控制",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = onToggleControlMode,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.controlMode == ServoControlMode.PWM) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text(text = if (state.controlMode == ServoControlMode.PWM) "PWM模式" else "角度模式")
                }
                Button(onClick = onAllServoHomeClick) {
                    Text("全部归位")
                }
                Button(onClick = onSyncAllServoStatusClick) {
                    Text("同步状态")
                }
            }
        }
        item {
            Divider(modifier = Modifier.padding(horizontal = 16.dp))
        }
        items(state.servoList) { servo ->
            ServoControlCard(
                servo = servo,
                controlMode = state.controlMode,
                onPwmChange = { pwm -> onPwmChange(servo.id, pwm) },
                onPwmChangeFinished = { onPwmChangeFinished(servo.id) },
                onAngleChange = { angle -> onAngleChange(servo.id, angle) },
                onAngleChangeFinished = { onAngleChangeFinished(servo.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        item {
            Divider(modifier = Modifier.padding(horizontal = 16.dp))
        }
        item {
            Text(
                text = "命令历史 (最后5条)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        items(state.commandHistory.takeLast(5).reversed()) { cmd ->
            CommandHistoryItem(cmd = cmd)
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(onClick = onClearHistoryClick) {
                    Text("清空历史")
                }
                OutlinedButton(onClick = onServoEnableClick) {
                    Text("启用舵机")
                }
                OutlinedButton(onClick = onServoDisableClick) {
                    Text("禁用舵机")
                }
            }
        }
    }
}

@Composable
private fun ServoControlCard(
    servo: ServoState,
    controlMode: ServoControlMode,
    onPwmChange: (Float) -> Unit,
    onPwmChangeFinished: () -> Unit,
    onAngleChange: (Float) -> Unit,
    onAngleChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (servo.isMoving) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${servo.id}. ${servo.name}",
                    style = MaterialTheme.typography.titleMedium
                )
                if (servo.isMoving) {
                    Text(
                        text = "移动中",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            if (controlMode == ServoControlMode.PWM) {
                Text(text = "PWM: ${servo.pwm.toInt()} (500-2500)")
                Slider(
                    value = servo.pwm,
                    onValueChange = onPwmChange,
                    onValueChangeFinished = onPwmChangeFinished,
                    valueRange = 500f..2500f,
                    steps = 200,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(text = "角度: ${servo.angle.toInt()}° (0-270)")
                Slider(
                    value = servo.angle,
                    onValueChange = onAngleChange,
                    onValueChangeFinished = onAngleChangeFinished,
                    valueRange = 0f..270f,
                    steps = 270,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (controlMode == ServoControlMode.PWM) "角度: ${(servo.pwm - 500) / 2000f * 270f}°" else "PWM: ${(servo.angle / 270f * 2000f + 500f).toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "状态: ${if (servo.isMoving) "移动" else "静止"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CommandHistoryItem(cmd: com.example.robotarmcontroller.data.model.ServoCommand) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "舵机 ${cmd.servoId}")
                Text(
                    text = "PWM: ${cmd.pwmValue}" + if (cmd.angleValue != null) " 角度: ${cmd.angleValue}°" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = java.text.SimpleDateFormat("HH:mm:ss").format(cmd.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CycleItem(
    cycle: CycleInfo,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onRestart: () -> Unit,
    onRelease: () -> Unit,
    onRequestStatus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stateText = when {
        cycle.running -> "运行中"
        cycle.active -> "激活"
        else -> "空闲"
    }
    val stateColor = when {
        cycle.running -> MaterialTheme.colorScheme.primary
        cycle.active -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "循环 #${cycle.index}", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stateText,
                    color = stateColor
                )
            }
            Text(
                text = "位姿: ${cycle.currentPose}/${cycle.poseCount} 循环: ${cycle.loopCount}/${cycle.maxLoops}",
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(onClick = onStart, modifier = Modifier.weight(1f)) {
                    Text("开始")
                }
                OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f)) {
                    Text("暂停")
                }
                OutlinedButton(onClick = onRestart, modifier = Modifier.weight(1f)) {
                    Text("重启")
                }
                OutlinedButton(onClick = onRelease, modifier = Modifier.weight(1f)) {
                    Text("释放")
                }
            }
        }
    }
}