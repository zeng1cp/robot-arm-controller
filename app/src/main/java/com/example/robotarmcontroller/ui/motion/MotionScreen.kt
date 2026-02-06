package com.example.robotarmcontroller.ui.motion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun MotionScreen(
    modifier: Modifier = Modifier,
    onStartMotion: (mode: Int, ids: List<Int>, values: List<Float>, durationMs: Int) -> Unit,
    onStopMotion: (groupId: Int) -> Unit,
    onPauseMotion: (groupId: Int) -> Unit,
    onResumeMotion: (groupId: Int) -> Unit,
    onGetMotionStatus: (groupId: Int) -> Unit,
    onCreateCycle: (
        mode: Int,
        ids: List<Int>,
        poses: List<List<Float>>,
        durations: List<Int>,
        maxLoops: Int
    ) -> Unit,
    onStartCycle: (index: Int) -> Unit,
    onRestartCycle: (index: Int) -> Unit,
    onPauseCycle: (index: Int) -> Unit,
    onReleaseCycle: (index: Int) -> Unit,
    onGetCycleStatus: (index: Int) -> Unit
) {
    var modeText by remember { mutableStateOf("0") }
    var idsText by remember { mutableStateOf("0,1,2") }
    var valuesText by remember { mutableStateOf("1500,1500,1500") }
    var durationText by remember { mutableStateOf("500") }
    var groupIdText by remember { mutableStateOf("0") }

    var poseText by remember { mutableStateOf("1500,1500,1500|1600,1600,1600") }
    var durationsText by remember { mutableStateOf("500,500") }
    var maxLoopsText by remember { mutableStateOf("0") }
    var cycleIndexText by remember { mutableStateOf("0") }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Motion 控制", style = MaterialTheme.typography.titleMedium)

                TextField(
                    value = modeText,
                    onValueChange = { modeText = it },
                    label = { Text("模式 (0 PWM / 1 角度)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = idsText,
                    onValueChange = { idsText = it },
                    label = { Text("舵机ID (逗号分隔)") },
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = valuesText,
                    onValueChange = { valuesText = it },
                    label = { Text("值列表 (PWM或角度)") },
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = durationText,
                    onValueChange = { durationText = it },
                    label = { Text("持续时间(ms)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val mode = modeText.toIntOrNull() ?: 0
                        val ids = parseInts(idsText)
                        val values = parseFloats(valuesText)
                        val duration = durationText.toIntOrNull() ?: 0
                        onStartMotion(mode, ids, values, duration)
                    }) { Text("启动") }
                    OutlinedButton(onClick = { onStopMotion(groupIdText.toIntOrNull() ?: 0) }) { Text("停止") }
                    OutlinedButton(onClick = { onPauseMotion(groupIdText.toIntOrNull() ?: 0) }) { Text("暂停") }
                    OutlinedButton(onClick = { onResumeMotion(groupIdText.toIntOrNull() ?: 0) }) { Text("继续") }
                    OutlinedButton(onClick = { onGetMotionStatus(groupIdText.toIntOrNull() ?: 0) }) { Text("状态") }
                }

                TextField(
                    value = groupIdText,
                    onValueChange = { groupIdText = it },
                    label = { Text("Group ID") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Motion Cycle", style = MaterialTheme.typography.titleMedium)
                Text("Pose格式: 用 | 分隔每个pose, pose内用逗号")

                TextField(
                    value = poseText,
                    onValueChange = { poseText = it },
                    label = { Text("Pose values") },
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = durationsText,
                    onValueChange = { durationsText = it },
                    label = { Text("Pose durations(ms)") },
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = maxLoopsText,
                    onValueChange = { maxLoopsText = it },
                    label = { Text("Max loops (0=无限)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = cycleIndexText,
                    onValueChange = { cycleIndexText = it },
                    label = { Text("Cycle index") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val mode = modeText.toIntOrNull() ?: 0
                        val ids = parseInts(idsText)
                        val poses = poseText.split('|').filter { it.isNotBlank() }.map { parseFloats(it) }
                        val durations = parseInts(durationsText)
                        val maxLoops = maxLoopsText.toIntOrNull() ?: 0
                        onCreateCycle(mode, ids, poses, durations, maxLoops)
                    }) { Text("创建") }
                    OutlinedButton(onClick = { onStartCycle(cycleIndexText.toIntOrNull() ?: 0) }) { Text("启动") }
                    OutlinedButton(onClick = { onRestartCycle(cycleIndexText.toIntOrNull() ?: 0) }) { Text("重启") }
                    OutlinedButton(onClick = { onPauseCycle(cycleIndexText.toIntOrNull() ?: 0) }) { Text("暂停") }
                    OutlinedButton(onClick = { onReleaseCycle(cycleIndexText.toIntOrNull() ?: 0) }) { Text("释放") }
                    OutlinedButton(onClick = { onGetCycleStatus(cycleIndexText.toIntOrNull() ?: 0) }) { Text("状态") }
                }

                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

private fun parseInts(value: String): List<Int> {
    return value.split(',').mapNotNull { it.trim().takeIf { it.isNotEmpty() }?.toIntOrNull() }
}

private fun parseFloats(value: String): List<Float> {
    return value.split(',').mapNotNull { it.trim().takeIf { it.isNotEmpty() }?.toFloatOrNull() }
}
