package com.example.robotarmcontroller.ui.cycle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.robotarmcontroller.data.model.CycleInfo
import com.example.robotarmcontroller.data.model.ServoState
import com.example.robotarmcontroller.protocol.MotionProtocolCodec

@Composable
fun CycleScreen(
    state: CycleUiState,
    servoStates: List<ServoState>,
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
    onRequestCycleStatus: (index: Int) -> Unit,
    onRequestCycleList: () -> Unit,
    onUpdateCreationMode: (mode: Int) -> Unit,
    onUpdateSelectedServoIds: (ids: List<Int>) -> Unit,
    onAddPose: (values: Map<Int, Float>, durationMs: Int) -> Unit,
    onUpdatePose: (index: Int, values: Map<Int, Float>, durationMs: Int) -> Unit,
    onRemovePose: (index: Int) -> Unit,
    onSetEditingPose: (index: Int) -> Unit,
    onUpdateCurrentPoseValue: (servoId: Int, value: Float) -> Unit,
    onClearCreationState: () -> Unit,
    onDeleteCycle: (index: Int) -> Unit,
    onConfirmDelete: (index: Int?) -> Unit,
    onStartEditingCycle: (index: Int) -> Unit,
    onCancelEditingCycle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val deletingIndex = state.deletingCycleIndex
    if (deletingIndex != null) {
        AlertDialog(
            onDismissRequest = { onConfirmDelete(null) },
            title = { Text("删除 Cycle") },
            text = { Text("确定要删除 Cycle $deletingIndex 吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCycle(deletingIndex)
                        onConfirmDelete(null)
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { onConfirmDelete(null) }) {
                    Text("取消")
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        onRequestCycleList()
    }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "Cycle 循环控制",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        item {
            Text(
                text = "Cycle 列表 (${state.cycleList.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        items(state.cycleList) { cycle ->
            CycleCard(
                cycle = cycle,
                onStart = { onStartCycle(cycle.index) },
                onRestart = { onRestartCycle(cycle.index) },
                onPause = { onPauseCycle(cycle.index) },
                onRelease = { onReleaseCycle(cycle.index) },
                onRequestStatus = { onRequestCycleStatus(cycle.index) },
                onDelete = { onConfirmDelete(cycle.index) },
                onEdit = { onStartEditingCycle(cycle.index) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        item {
            Divider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }

        item {
            Text(
                text = "创建新 Cycle",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        item {
            CreationPanel(
                creationState = state.creationState,
                servoStates = servoStates,
                onUpdateMode = onUpdateCreationMode,
                onUpdateSelectedServoIds = onUpdateSelectedServoIds,
                onAddPose = onAddPose,
                onUpdatePose = onUpdatePose,
                onRemovePose = onRemovePose,
                onSetEditingPose = onSetEditingPose,
                onUpdateCurrentPoseValue = onUpdateCurrentPoseValue,
                onCreateCycle = { mode, ids, poses, durations, maxLoops ->
                    onCreateCycle(mode, ids, poses, durations, maxLoops)
                    onClearCreationState()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun CycleCard(
    cycle: CycleInfo,
    onStart: () -> Unit,
    onRestart: () -> Unit,
    onPause: () -> Unit,
    onRelease: () -> Unit,
    onRequestStatus: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (cycle.active) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cycle ${cycle.index}",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (cycle.active) "激活" else "未激活",
                        color = if (cycle.active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    )
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "编辑",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "姿态: ${cycle.currentPose}/${cycle.poseCount} | 循环: ${cycle.loopCount}/${cycle.maxLoops}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = onStart,
                    enabled = cycle.active && !cycle.running && cycle.activeGroupId == 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("开始")
                }
                Button(
                    onClick = onPause,
                    enabled = cycle.active && cycle.running,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("暂停")
                }
                Button(
                    onClick = onRestart,
                    enabled = cycle.active && !cycle.running && cycle.activeGroupId != 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("重启")
                }
                Button(
                    onClick = onRelease,
                    enabled = cycle.active,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("释放")
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onRequestStatus,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("查询状态")
            }
        }
    }
}

@Composable
private fun CreationPanel(
    creationState: CycleCreationState,
    servoStates: List<ServoState>,
    onUpdateMode: (mode: Int) -> Unit,
    onUpdateSelectedServoIds: (ids: List<Int>) -> Unit,
    onAddPose: (values: Map<Int, Float>, durationMs: Int) -> Unit,
    onUpdatePose: (index: Int, values: Map<Int, Float>, durationMs: Int) -> Unit,
    onRemovePose: (index: Int) -> Unit,
    onSetEditingPose: (index: Int) -> Unit,
    onUpdateCurrentPoseValue: (servoId: Int, value: Float) -> Unit,
    onCreateCycle: (mode: Int, ids: List<Int>, poses: List<List<Float>>, durations: List<Int>, maxLoops: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val mode = creationState.mode
    val selectedServoIds = creationState.selectedServoIds
    val poses = creationState.poses
    val durations = creationState.durations
    val maxLoops = creationState.maxLoops
    val editingPoseIndex = creationState.editingPoseIndex
    val currentPoseValues = creationState.currentPoseValues

    val defaultPoseDuration = "1000";

    // 本地状态用于当前编辑的持续时间（临时）
    var poseDurationText by rememberSaveable { mutableStateOf("") }
    // 当开始编辑某个姿态时，加载其持续时间
    LaunchedEffect(editingPoseIndex) {
        if (editingPoseIndex in durations.indices) {
            poseDurationText = durations[editingPoseIndex].toString()
        } else {
            poseDurationText = defaultPoseDuration
        }
    }

    // 处理舵机选择变化：当 selectedServoIds 改变时，确保 currentPoseValues 包含所有选中的舵机，并移除未选中的
    LaunchedEffect(selectedServoIds) {
        val currentMap = currentPoseValues.toMutableMap()
        // 添加缺失的舵机默认值
        selectedServoIds.forEach { id ->
            if (!currentMap.containsKey(id)) {
                currentMap[id] = if (mode == MotionProtocolCodec.MODE_PWM) 1500f else 135f
            }
        }
        // 移除未选中的舵机
        currentMap.keys.retainAll(selectedServoIds.toSet())
        // 逐个更新，避免一次性全量替换（因为 onUpdateCurrentPoseValue 是单值更新，我们只能逐个调用）
        currentMap.forEach { (id, value) ->
            if (currentPoseValues[id] != value) {
                onUpdateCurrentPoseValue(id, value)
            }
        }
        // 对于被移除的舵机，我们不需要显式删除，因为它们已从 map 中移除，但 ViewModel 中 map 仍然可能包含？我们依靠 ViewModel 的更新逻辑，但这里无法直接删除键。
        // 更好的做法是通过一个函数同步整个 map，但 ViewModel 只提供了单值更新。我们暂且假设选中舵机变化时，UI 会重新输入，旧的键值不会被用到。
        // 实际上 ViewModel 中的 currentPoseValues 可能包含已移除舵机，但在点击添加/更新姿态时，会使用 selectedServoIds 进行过滤，所以没问题。
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 模式选择
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("控制模式：", modifier = Modifier.padding(end = 8.dp))
            Row {
                RadioButton(
                    selected = mode == MotionProtocolCodec.MODE_PWM,
                    onClick = { onUpdateMode(MotionProtocolCodec.MODE_PWM) }
                )
                Text("PWM", modifier = Modifier.padding(end = 16.dp))
                RadioButton(
                    selected = mode == MotionProtocolCodec.MODE_ANGLE,
                    onClick = { onUpdateMode(MotionProtocolCodec.MODE_ANGLE) }
                )
                Text("角度")
            }
        }

        // 舵机选择
        Text("选择舵机：")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            servoStates.forEach { servo ->
                val isSelected = selectedServoIds.contains(servo.id)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        val newIds = if (isSelected) {
                            selectedServoIds - servo.id
                        } else {
                            selectedServoIds + servo.id
                        }
                        onUpdateSelectedServoIds(newIds)
                    },
                    label = { Text("${servo.name} (${servo.id})") }
                )
            }
        }

        // 如果未选择舵机，提示
        if (selectedServoIds.isEmpty()) {
            Text("请至少选择一个舵机", color = MaterialTheme.colorScheme.error)
        }

        // 当前姿态编辑区域
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (editingPoseIndex == -1) "添加新姿态" else "编辑姿态 #${editingPoseIndex + 1}",
                    style = MaterialTheme.typography.titleSmall
                )

                // 为每个选中的舵机显示滑块和输入框
                selectedServoIds.sorted().forEach { servoId ->
                    val value = currentPoseValues[servoId]
                        ?: if (mode == MotionProtocolCodec.MODE_PWM) 1500f else 135f
                    val range = if (mode == MotionProtocolCodec.MODE_PWM) 500f..2500f else 0f..270f
                    Column {
                        Text("舵机 $servoId: ${value.toInt()}")
                        Slider(
                            value = value,
                            onValueChange = { onUpdateCurrentPoseValue(servoId, it) },
                            valueRange = range,
                            steps = if (mode == MotionProtocolCodec.MODE_PWM) 200 else 27 // 粗略步进
                        )
                        OutlinedTextField(
                            value = value.toInt().toString(),
                            onValueChange = {
                                it.toIntOrNull()?.let { intValue ->
                                    val clamped = intValue.coerceIn(
                                        range.start.toInt(),
                                        range.endInclusive.toInt()
                                    )
                                    onUpdateCurrentPoseValue(servoId, clamped.toFloat())
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // 持续时间输入
                OutlinedTextField(
                    value = poseDurationText,
                    onValueChange = { poseDurationText = it.filter { char -> char.isDigit() } },
                    label = { Text("持续时间 (ms)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 添加/更新按钮
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val durationMs = poseDurationText.toIntOrNull()
                    Button(
                        onClick = {
                            if (durationMs != null && selectedServoIds.isNotEmpty()) {
                                if (editingPoseIndex == -1) {
                                    onAddPose(currentPoseValues, durationMs)
                                } else {
                                    onUpdatePose(editingPoseIndex, currentPoseValues, durationMs)
                                    onSetEditingPose(-1) // 退出编辑模式
                                }
                                poseDurationText = defaultPoseDuration
                            }
                        },
                        enabled = durationMs != null && selectedServoIds.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (editingPoseIndex == -1) "添加姿态" else "更新姿态")
                    }
                    if (editingPoseIndex != -1) {
                        OutlinedButton(
                            onClick = { onSetEditingPose(-1) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("取消")
                        }
                    }
                }
            }
        }

        // 已添加姿态列表
        if (poses.isNotEmpty()) {
            Text("已添加姿态 (${poses.size})", style = MaterialTheme.typography.titleSmall)
            poses.forEachIndexed { index, poseValues ->
                val duration = durations.getOrElse(index) { 0 }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("姿态 #${index + 1}", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "持续时间: ${duration}ms",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "舵机值: ${
                                    poseValues.joinToString(
                                        prefix = "[",
                                        postfix = "]",
                                        limit = 3
                                    )
                                }", style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row {
                            IconButton(onClick = { onSetEditingPose(index) }) {
                                Icon(Icons.Default.Edit, contentDescription = "编辑")
                            }
                            IconButton(onClick = { onRemovePose(index) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        // 最大循环次数和创建按钮
        var maxLoopsText by rememberSaveable { mutableStateOf(maxLoops.toString()) }
        OutlinedTextField(
            value = maxLoopsText,
            onValueChange = { maxLoopsText = it.filter { char -> char.isDigit() } },
            label = { Text("最大循环次数 (0 表示无限)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                val maxLoopsInt = maxLoopsText.toIntOrNull() ?: 0
                if (selectedServoIds.isNotEmpty() && poses.isNotEmpty() && poses.size == durations.size) {
                    onCreateCycle(mode, selectedServoIds, poses, durations, maxLoopsInt)
                }
            },
            enabled = selectedServoIds.isNotEmpty() && poses.isNotEmpty() && poses.size == durations.size,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("创建 Cycle")
        }
    }
}