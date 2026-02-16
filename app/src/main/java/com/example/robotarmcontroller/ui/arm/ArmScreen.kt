package com.example.robotarmcontroller.ui.arm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.robotarmcontroller.data.model.ServoState

@Composable
fun ArmScreen(
    state: ArmUiState,
    onExecutePreset: (ArmPreset) -> Unit,
    onStopAllMotion: () -> Unit,
    onSelectPreset: (ArmPreset?) -> Unit,
    onToggleCooperativeMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp)
    ) {
        // 状态概览
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("机械臂协同控制", style = MaterialTheme.typography.titleLarge)
                    OutlinedButton(
                        onClick = onToggleCooperativeMode,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            if (state.cooperativeMode) Icons.Filled.ToggleOn else Icons.Filled.ToggleOff,
                            contentDescription = "协同模式"
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (state.cooperativeMode) "协同模式开启" else "协同模式关闭")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "舵机数量: ${state.servoStates.size}",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Text(
                    "连接状态: ${if (state.isConnected) "已连接" else "未连接"}",
                    fontSize = 14.sp,
                    color = if (state.isConnected) Color(0xFF2E7D32) else Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 预设列表
        Text("预设动作", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(samplePresets) { preset ->
                ArmPresetCard(
                    preset = preset,
                    isSelected = state.selectedPreset?.id == preset.id,
                    onSelect = { onSelectPreset(preset) },
                    onExecute = { onExecutePreset(preset) }
                )
            }
        }

        // 控制按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    state.selectedPreset?.let { onExecutePreset(it) }
                },
                enabled = state.selectedPreset != null
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "执行")
                Spacer(modifier = Modifier.width(4.dp))
                Text("执行选中预设")
            }
            OutlinedButton(
                onClick = onStopAllMotion
            ) {
                Icon(Icons.Filled.Stop, contentDescription = "停止")
                Spacer(modifier = Modifier.width(4.dp))
                Text("停止所有运动")
            }
        }
    }
}

@Composable
private fun ArmPresetCard(
    preset: ArmPreset,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onExecute: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(preset.name, fontWeight = FontWeight.Medium)
                    preset.description?.let {
                        Text(it, fontSize = 12.sp, color = Color.Gray)
                    }
                }
                Button(onClick = onExecute, shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "执行")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("执行")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "舵机: ${preset.servoTargets.keys.sorted().joinToString()}",
                fontSize = 12.sp,
                color = Color.Gray
            )
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "模式: ${preset.mode.name}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Text(
                    "时长: ${preset.durationMs}ms",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

// 示例预设
private val samplePresets = listOf(
    ArmPreset(
        id = 1,
        name = "归位姿势",
        description = "所有舵机回到中间位置",
        servoTargets = mapOf(
            0 to 1500f,
            1 to 1500f,
            2 to 1500f,
            3 to 1500f,
            4 to 1500f,
            5 to 1500f
        ),
        durationMs = 2000,
        mode = ArmPresetMode.PWM
    ),
    ArmPreset(
        id = 2,
        name = "抓取姿势",
        description = "夹爪闭合，肘部弯曲",
        servoTargets = mapOf(
            0 to 1500f,
            1 to 1200f,
            2 to 1800f,
            3 to 1500f,
            4 to 1500f,
            5 to 2000f
        ),
        durationMs = 1500,
        mode = ArmPresetMode.PWM
    ),
    ArmPreset(
        id = 3,
        name = "伸展姿势",
        description = "机械臂完全伸展",
        servoTargets = mapOf(
            0 to 1500f,
            1 to 2000f,
            2 to 1000f,
            3 to 1500f,
            4 to 1500f,
            5 to 1500f
        ),
        durationMs = 2500,
        mode = ArmPresetMode.PWM
    )
)