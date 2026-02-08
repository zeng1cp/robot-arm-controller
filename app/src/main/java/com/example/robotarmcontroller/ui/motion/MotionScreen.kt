package com.example.robotarmcontroller.ui.motion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.robotarmcontroller.protocol.MotionProtocolCodec
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.snapshotFlow
import org.json.JSONArray
import org.json.JSONObject

data class MotionServoInfo(
    val id: Int,
    val name: String,
    val isMoving: Boolean
)

private enum class MotionPresetStatus {
    RUNNING,
    PAUSED
}

private data class MotionPreset(
    val id: Int,
    val name: String,
    val mode: Int,
    val durationMs: Int,
    val servoIds: List<Int>,
    val values: List<Float>,
    val realtime: Boolean,
    val status: MotionPresetStatus? = null,
    val groupId: Int = 0,
    val startedAtMs: Long? = null,
    val elapsedMs: Long = 0
)

private const val PRESET_PREFS_NAME = "motion_presets"
private const val PRESET_KEY = "preset_list"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MotionScreen(
    modifier: Modifier = Modifier,
    currentGroupId: Int = 0,
    motionCompleteGroupId: Int = 0,
    servoInfo: List<MotionServoInfo> = emptyList(),
    onStartMotion: (mode: Int, ids: List<Int>, values: List<Float>, durationMs: Int) -> Unit,
    onStopMotion: (groupId: Int) -> Unit,
    onPauseMotion: (groupId: Int) -> Unit,
    onResumeMotion: (groupId: Int) -> Unit,
    onGetMotionStatus: (groupId: Int) -> Unit,
    onPreviewServoValue: (servoId: Int, mode: Int, value: Float) -> Unit,
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
    val context = LocalContext.current
    val presets = remember { mutableStateListOf<MotionPreset>() }
    var showPresets by remember { mutableStateOf(true) }
    var maxPresetCount by remember { mutableStateOf("10") }
    var nextPresetId by remember { mutableIntStateOf(1) }
    var pendingGroupPresetId by remember { mutableStateOf<Int?>(null) }
    var lastHandledCompleteGroupId by remember { mutableIntStateOf(0) }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }

    var showPresetDialog by remember { mutableStateOf(false) }
    var showCycleDialog by remember { mutableStateOf(false) }
    var showConflictDialog by remember { mutableStateOf(false) }
    var conflictMessage by remember { mutableStateOf("") }
    var showPresetLimitDialog by remember { mutableStateOf(false) }
    var cycleIndexText by remember { mutableStateOf("0") }

    var groupIdText by remember { mutableStateOf(currentGroupId.toString()) }

    LaunchedEffect(Unit) {
        val loaded = loadPresets(context)
        if (loaded.isNotEmpty()) {
            presets.addAll(loaded)
            nextPresetId = (loaded.maxOfOrNull { it.id } ?: 0) + 1
        }
    }

    LaunchedEffect(presets) {
        snapshotFlow { presets.toList() }
            .distinctUntilChanged()
            .collectLatest { savePresets(context, it) }
    }

    LaunchedEffect(currentGroupId) {
        groupIdText = currentGroupId.toString()
        val pendingId = pendingGroupPresetId
        if (pendingId != null && currentGroupId > 0) {
            val index = presets.indexOfFirst { it.id == pendingId }
            if (index >= 0) {
                val preset = presets[index]
                presets[index] = preset.copy(
                    groupId = currentGroupId,
                    status = MotionPresetStatus.RUNNING,
                    startedAtMs = System.currentTimeMillis(),
                    elapsedMs = 0
                )
            }
            pendingGroupPresetId = null
        }
    }

    LaunchedEffect(motionCompleteGroupId) {
        if (motionCompleteGroupId > 0 && motionCompleteGroupId != lastHandledCompleteGroupId) {
            val index = presets.indexOfFirst { it.groupId == motionCompleteGroupId }
            if (index >= 0) {
                updatePresetStatus(presets, presets[index].id, null, clearGroup = true)
            }
            lastHandledCompleteGroupId = motionCompleteGroupId
        }
    }

    val servoNameMap = remember(servoInfo) { servoInfo.associate { it.id to it.name } }

    LaunchedEffect(maxPresetCount) {
        val maxCount = maxPresetCount.toIntOrNull() ?: return@LaunchedEffect
        if (maxCount > 0 && presets.size > maxCount) {
            val removeCount = presets.size - maxCount
            repeat(removeCount) { presets.removeAt(0) }
        }
    }

    val activePresets = presets.filter { it.status != null }
    val occupancyMap = activePresets
        .flatMap { preset -> preset.servoIds.map { it to preset.name } }
        .toMap()
    val activePresetByServo = activePresets
        .flatMap { preset -> preset.servoIds.map { it to preset } }
        .toMap()

    LaunchedEffect(activePresets.size) {
        while (true) {
            if (activePresets.isNotEmpty()) {
                nowMs = System.currentTimeMillis()
            }
            delay(120)
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (activePresets.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("正在活跃的 Motion", style = MaterialTheme.typography.titleMedium)
                    activePresets.forEach { preset ->
                        val statusColor = when (preset.status) {
                            MotionPresetStatus.RUNNING -> Color(0xFF2E7D32)
                            MotionPresetStatus.PAUSED -> Color(0xFF757575)
                            null -> MaterialTheme.colorScheme.outline
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(statusColor.copy(alpha = 0.12f))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(preset.name, color = statusColor)
                                Text(
                                    text = "Group ID: ${preset.groupId.takeIf { it > 0 } ?: "等待中"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = statusColor
                                )
                            }
                            TextButton(onClick = {
                                when (preset.status) {
                                    MotionPresetStatus.RUNNING -> {
                                        if (preset.groupId > 0) {
                                            onPauseMotion(preset.groupId)
                                            updatePresetStatus(presets, preset.id, MotionPresetStatus.PAUSED)
                                        }
                                    }
                                    MotionPresetStatus.PAUSED -> {
                                        if (preset.groupId > 0) {
                                            onResumeMotion(preset.groupId)
                                            updatePresetStatus(presets, preset.id, MotionPresetStatus.RUNNING)
                                        }
                                    }
                                    null -> Unit
                                }
                            }) {
                                Text(if (preset.status == MotionPresetStatus.PAUSED) "继续" else "暂停")
                            }
                        }
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("舵机运行状态", style = MaterialTheme.typography.titleMedium)
                val rows = servoInfo.take(6).chunked(3)
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { servo ->
                            val occupiedBy = occupancyMap[servo.id]
                            val statusText = if (servo.isMoving) "运行中" else "空闲"
                            val preset = activePresetByServo[servo.id]
                            val progress = calculateServoProgress(preset, nowMs)
                            ServoStatusCard(
                                name = servo.name,
                                id = servo.id,
                                occupiedBy = occupiedBy,
                                statusText = statusText,
                                isMoving = servo.isMoving,
                                progress = progress,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size < 3) {
                            repeat(3 - row.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Motion 控制", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Group ID")
                    Spacer(modifier = Modifier.width(8.dp))
                    TextField(
                        value = groupIdText,
                        onValueChange = { groupIdText = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showPresetDialog = true }) { Text("添加预设 Motion") }
                    OutlinedButton(onClick = { showCycleDialog = true }) { Text("创建 Cycle") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onStopMotion(groupIdText.toIntOrNull() ?: 0) }) { Text("停止") }
                    OutlinedButton(onClick = { onPauseMotion(groupIdText.toIntOrNull() ?: 0) }) { Text("暂停") }
                    OutlinedButton(onClick = { onResumeMotion(groupIdText.toIntOrNull() ?: 0) }) { Text("继续") }
                    OutlinedButton(onClick = { onGetMotionStatus(groupIdText.toIntOrNull() ?: 0) }) { Text("状态") }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("预设动作列表", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Switch(checked = showPresets, onCheckedChange = { showPresets = it })
                }
                if (showPresets) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("最大长度")
                        Spacer(modifier = Modifier.width(8.dp))
                        TextField(
                            value = maxPresetCount,
                            onValueChange = { maxPresetCount = it.filter { ch -> ch.isDigit() } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(100.dp)
                        )
                    }
                    if (presets.isEmpty()) {
                        Text("暂无预设动作，请点击“添加预设 Motion”。", color = Color.Gray)
                    } else {
                        presets.forEach { preset ->
                            MotionPresetCard(
                                preset = preset,
                                onStart = {
                                    val conflicts = detectConflicts(preset, presets, servoNameMap)
                                    if (conflicts.isNotEmpty()) {
                                        conflictMessage = conflicts
                                        showConflictDialog = true
                                        return@MotionPresetCard
                                    }
                                    pendingGroupPresetId = preset.id
                                    onStartMotion(preset.mode, preset.servoIds, preset.values, preset.durationMs)
                                    updatePresetStatus(presets, preset.id, MotionPresetStatus.RUNNING)
                                },
                                onStop = {
                                    if (preset.groupId > 0) {
                                        onStopMotion(preset.groupId)
                                    }
                                    updatePresetStatus(presets, preset.id, null, clearGroup = true)
                                },
                                onPause = {
                                    if (preset.groupId > 0) {
                                        onPauseMotion(preset.groupId)
                                        updatePresetStatus(presets, preset.id, MotionPresetStatus.PAUSED)
                                    }
                                },
                                onResume = {
                                    if (preset.groupId > 0) {
                                        onResumeMotion(preset.groupId)
                                        updatePresetStatus(presets, preset.id, MotionPresetStatus.RUNNING)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Cycle 控制", style = MaterialTheme.typography.titleMedium)
                TextField(
                    value = cycleIndexText,
                    onValueChange = { cycleIndexText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Cycle index") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onStartCycle(cycleIndexText.toIntOrNull() ?: 0) }) { Text("启动") }
                    OutlinedButton(onClick = { onRestartCycle(cycleIndexText.toIntOrNull() ?: 0) }) { Text("重启") }
                    OutlinedButton(onClick = { onPauseCycle(cycleIndexText.toIntOrNull() ?: 0) }) { Text("暂停") }
                    OutlinedButton(onClick = { onReleaseCycle(cycleIndexText.toIntOrNull() ?: 0) }) { Text("释放") }
                    OutlinedButton(onClick = { onGetCycleStatus(cycleIndexText.toIntOrNull() ?: 0) }) { Text("状态") }
                }
            }
        }
    }

    if (showPresetDialog) {
        MotionPresetDialog(
            servoInfo = servoInfo,
            onDismiss = { showPresetDialog = false },
            onConfirm = { preset ->
                if (preset.servoIds.isEmpty()) {
                    conflictMessage = "请至少选择一个舵机。"
                    showConflictDialog = true
                    return@MotionPresetDialog
                }
                val maxCount = maxPresetCount.toIntOrNull() ?: 10
                if (presets.size >= maxCount) {
                    showPresetLimitDialog = true
                    return@MotionPresetDialog
                }
                presets.add(preset.copy(id = nextPresetId++))
                showPresetDialog = false
            },
            onPreviewServoValue = onPreviewServoValue
        )
    }

    if (showCycleDialog) {
        MotionCycleDialog(
            presets = presets,
            onDismiss = { showCycleDialog = false },
            onConfirm = { selectedPresets, maxLoops ->
                val first = selectedPresets.firstOrNull() ?: return@MotionCycleDialog
                val sameMode = selectedPresets.all { it.mode == first.mode }
                val sameServos = selectedPresets.all { it.servoIds == first.servoIds }
                if (!sameMode || !sameServos) {
                    conflictMessage = "所选预设的模式或舵机列表不一致，无法创建 Cycle。"
                    showConflictDialog = true
                    return@MotionCycleDialog
                }
                val poses = selectedPresets.map { it.values }
                val durations = selectedPresets.map { it.durationMs }
                onCreateCycle(first.mode, first.servoIds, poses, durations, maxLoops)
                showCycleDialog = false
            }
        )
    }

    if (showConflictDialog) {
        AlertDialog(
            onDismissRequest = { showConflictDialog = false },
            title = { Text("舵机冲突") },
            text = { Text(conflictMessage) },
            confirmButton = {
                TextButton(onClick = { showConflictDialog = false }) { Text("知道了") }
            }
        )
    }

    if (showPresetLimitDialog) {
        AlertDialog(
            onDismissRequest = { showPresetLimitDialog = false },
            title = { Text("超过最大长度") },
            text = { Text("预设动作数量已达到上限，请调整最大长度或删除部分预设。") },
            confirmButton = {
                TextButton(onClick = { showPresetLimitDialog = false }) { Text("好的") }
            }
        )
    }
}

private fun updatePresetStatus(
    presets: MutableList<MotionPreset>,
    presetId: Int,
    status: MotionPresetStatus?,
    clearGroup: Boolean = false
) {
    val index = presets.indexOfFirst { it.id == presetId }
    if (index >= 0) {
        val preset = presets[index]
        val now = System.currentTimeMillis()
        val elapsed = when {
            status == MotionPresetStatus.PAUSED && preset.startedAtMs != null ->
                preset.elapsedMs + (now - preset.startedAtMs)
            status == MotionPresetStatus.RUNNING && preset.status == MotionPresetStatus.PAUSED ->
                preset.elapsedMs
            status == null -> 0
            else -> preset.elapsedMs
        }
        val startedAt = when {
            status == MotionPresetStatus.RUNNING && preset.groupId > 0 && preset.startedAtMs == null -> now
            status == MotionPresetStatus.PAUSED -> null
            status == null -> null
            else -> preset.startedAtMs
        }
        presets[index] = preset.copy(
            status = status,
            groupId = if (clearGroup) 0 else preset.groupId,
            startedAtMs = if (clearGroup) null else startedAt,
            elapsedMs = elapsed
        )
    }
}

private fun detectConflicts(
    preset: MotionPreset,
    presets: List<MotionPreset>,
    servoNameMap: Map<Int, String>
): String {
    val activePresets = presets.filter { it.status != null && it.id != preset.id }
    if (activePresets.isEmpty()) return ""
    val conflicts = preset.servoIds.flatMap { servoId ->
        activePresets.filter { it.servoIds.contains(servoId) }
            .map { conflictPreset ->
                val servoName = servoNameMap[servoId] ?: "ID $servoId"
                "$servoName 被 ${conflictPreset.name} 占用"
            }
    }
    return conflicts.distinct().joinToString("\n")
}

private fun calculateServoProgress(preset: MotionPreset?, nowMs: Long): Float {
    if (preset == null || preset.status != MotionPresetStatus.RUNNING) return 0f
    val duration = preset.durationMs.takeIf { it > 0 } ?: return 0f
    val startedAt = preset.startedAtMs ?: return 0f
    val elapsed = (preset.elapsedMs + (nowMs - startedAt)).coerceAtLeast(0)
    return (elapsed.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
}

@Composable
private fun ServoStatusCard(
    name: String,
    id: Int,
    occupiedBy: String?,
    statusText: String,
    isMoving: Boolean,
    progress: Float,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    strokeWidth: Dp = 3.dp
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val progressColor = if (isMoving) Color(0xFF2E7D32) else borderColor
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier = modifier
            .height(84.dp)
            .background(MaterialTheme.colorScheme.surface, shape)
            .drawProgressBorder(
                shape = shape,
                borderColor = borderColor,
                progressColor = progressColor,
                progress = progress,
                strokeWidth = strokeWidth
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("$name #$id", style = MaterialTheme.typography.bodySmall)
        Text(
            text = if (occupiedBy != null) "占用: $occupiedBy" else statusText,
            style = MaterialTheme.typography.bodySmall,
            color = if (occupiedBy != null) Color(0xFF1565C0) else Color.Gray
        )
    }
}

private fun Modifier.drawProgressBorder(
    shape: RoundedCornerShape,
    borderColor: Color,
    progressColor: Color,
    progress: Float,
    strokeWidth: Dp
) = drawBehind {
    val strokePx = strokeWidth.toPx()
    val inset = strokePx / 2f
    val size = size.copy(width = size.width - strokePx, height = size.height - strokePx)
    val corner = shape.topStart.toPx(size, this)
    val cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner)

    translate(inset, inset) {
        drawRoundRect(
            color = borderColor,
            size = size,
            cornerRadius = cornerRadius,
            style = Stroke(width = strokePx)
        )
        if (progress > 0f) {
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                size = size,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MotionPresetCard(
    preset: MotionPreset,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    val statusText = when (preset.status) {
        MotionPresetStatus.RUNNING -> "运行中"
        MotionPresetStatus.PAUSED -> "已暂停"
        null -> "未运行"
    }
    val statusColor = when (preset.status) {
        MotionPresetStatus.RUNNING -> Color(0xFF2E7D32)
        MotionPresetStatus.PAUSED -> Color(0xFF757575)
        null -> Color.Gray
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(preset.name, style = MaterialTheme.typography.titleSmall)
                    Text("状态: $statusText", color = statusColor, style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "Group ID: ${preset.groupId.takeIf { it > 0 } ?: "未分配"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("时长: ${preset.durationMs}ms", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = if (preset.mode == MotionProtocolCodec.MODE_PWM) "模式: PWM" else "模式: 角度",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStart) { Text("开始") }
                OutlinedButton(onClick = onStop) { Text("停止") }
                OutlinedButton(onClick = onPause, enabled = preset.groupId > 0) { Text("暂停") }
                OutlinedButton(onClick = onResume, enabled = preset.groupId > 0) { Text("继续") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MotionPresetDialog(
    servoInfo: List<MotionServoInfo>,
    onDismiss: () -> Unit,
    onConfirm: (MotionPreset) -> Unit,
    onPreviewServoValue: (servoId: Int, mode: Int, value: Float) -> Unit
) {
    var name by remember { mutableStateOf("预设动作") }
    var mode by remember { mutableIntStateOf(MotionProtocolCodec.MODE_PWM) }
    var durationMs by remember { mutableStateOf("500") }
    var realtime by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Int>() }
    val valueMap = remember { mutableStateMapOf<Int, Float>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加预设 Motion") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("模式")
                    RadioButton(
                        selected = mode == MotionProtocolCodec.MODE_PWM,
                        onClick = { mode = MotionProtocolCodec.MODE_PWM }
                    )
                    Text("PWM")
                    RadioButton(
                        selected = mode == MotionProtocolCodec.MODE_ANGLE,
                        onClick = { mode = MotionProtocolCodec.MODE_ANGLE }
                    )
                    Text("角度")
                }
                TextField(
                    value = durationMs,
                    onValueChange = { durationMs = it.filter { ch -> ch.isDigit() } },
                    label = { Text("持续时间(ms)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("舵机实时响应")
                    RadioButton(selected = realtime, onClick = { realtime = true })
                    Text("开启")
                    RadioButton(selected = !realtime, onClick = { realtime = false })
                    Text("关闭")
                }

                Text("选择舵机")
                servoInfo.forEach { servo ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = selectedIds.contains(servo.id),
                            onCheckedChange = { checked ->
                                if (checked) {
                                    selectedIds.add(servo.id)
                                    if (!valueMap.containsKey(servo.id)) {
                                        valueMap[servo.id] = if (mode == MotionProtocolCodec.MODE_PWM) 1500f else 135f
                                    }
                                } else {
                                    selectedIds.remove(servo.id)
                                    valueMap.remove(servo.id)
                                }
                            }
                        )
                        Text("${servo.name} (#${servo.id})")
                    }
                }

                selectedIds.sorted().forEach { servoId ->
                    val currentValue = valueMap[servoId] ?: if (mode == MotionProtocolCodec.MODE_PWM) 1500f else 135f
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("舵机 $servoId 值")
                        Slider(
                            value = currentValue,
                            onValueChange = {
                                valueMap[servoId] = it
                                if (realtime) {
                                    onPreviewServoValue(servoId, mode, it)
                                }
                            },
                            valueRange = if (mode == MotionProtocolCodec.MODE_PWM) 500f..2500f else 0f..270f
                        )
                        TextField(
                            value = currentValue.toInt().toString(),
                            onValueChange = {
                                val parsed = it.toFloatOrNull()
                                if (parsed != null) {
                                    val clamped = if (mode == MotionProtocolCodec.MODE_PWM) {
                                        parsed.coerceIn(500f, 2500f)
                                    } else {
                                        parsed.coerceIn(0f, 270f)
                                    }
                                    valueMap[servoId] = clamped
                                    if (realtime) {
                                        onPreviewServoValue(servoId, mode, clamped)
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(120.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val ids = selectedIds.sorted()
                val values = ids.map { valueMap[it] ?: 0f }
                onConfirm(
                    MotionPreset(
                        id = 0,
                        name = name,
                        mode = mode,
                        durationMs = durationMs.toIntOrNull() ?: 0,
                        servoIds = ids,
                        values = values,
                        realtime = realtime
                    )
                )
            }) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun MotionCycleDialog(
    presets: List<MotionPreset>,
    onDismiss: () -> Unit,
    onConfirm: (selected: List<MotionPreset>, maxLoops: Int) -> Unit
) {
    val selectedIds = remember { mutableStateListOf<Int>() }
    var maxLoops by remember { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("通过预设创建 Cycle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("选择预设动作")
                presets.forEach { preset ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = selectedIds.contains(preset.id),
                            onCheckedChange = { checked ->
                                if (checked) {
                                    selectedIds.add(preset.id)
                                } else {
                                    selectedIds.remove(preset.id)
                                }
                            }
                        )
                        Text(preset.name)
                    }
                }
                TextField(
                    value = maxLoops,
                    onValueChange = { maxLoops = it.filter { ch -> ch.isDigit() } },
                    label = { Text("最大循环次数 (0=无限)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val selected = presets.filter { selectedIds.contains(it.id) }
                onConfirm(selected, maxLoops.toIntOrNull() ?: 0)
            }) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun loadPresets(context: android.content.Context): List<MotionPreset> {
    val prefs = context.getSharedPreferences(PRESET_PREFS_NAME, android.content.Context.MODE_PRIVATE)
    val raw = prefs.getString(PRESET_KEY, null) ?: return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val ids = obj.getJSONArray("servoIds").toIntList()
                val values = obj.getJSONArray("values").toFloatList()
                add(
                    MotionPreset(
                        id = obj.optInt("id", i + 1),
                        name = obj.optString("name", "预设动作"),
                        mode = obj.optInt("mode", MotionProtocolCodec.MODE_PWM),
                        durationMs = obj.optInt("durationMs", 0),
                        servoIds = ids,
                        values = values,
                        realtime = obj.optBoolean("realtime", false),
                        status = null,
                        groupId = 0
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

private fun savePresets(context: android.content.Context, presets: List<MotionPreset>) {
    val array = JSONArray()
    presets.forEach { preset ->
        val obj = JSONObject()
        obj.put("id", preset.id)
        obj.put("name", preset.name)
        obj.put("mode", preset.mode)
        obj.put("durationMs", preset.durationMs)
        obj.put("realtime", preset.realtime)
        obj.put("servoIds", JSONArray(preset.servoIds))
        obj.put("values", JSONArray(preset.values))
        array.put(obj)
    }
    context.getSharedPreferences(PRESET_PREFS_NAME, android.content.Context.MODE_PRIVATE)
        .edit()
        .putString(PRESET_KEY, array.toString())
        .apply()
}

private fun JSONArray.toIntList(): List<Int> {
    val list = mutableListOf<Int>()
    for (i in 0 until length()) {
        list.add(optInt(i))
    }
    return list
}

private fun JSONArray.toFloatList(): List<Float> {
    val list = mutableListOf<Float>()
    for (i in 0 until length()) {
        list.add(optDouble(i).toFloat())
    }
    return list
}
