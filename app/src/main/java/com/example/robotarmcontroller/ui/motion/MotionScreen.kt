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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.robotarmcontroller.protocol.MotionProtocolCodec
import com.example.robotarmcontroller.ui.robot.CycleInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import org.json.JSONArray
import org.json.JSONObject

data class MotionServoInfo(
    val id: Int,
    val name: String,
    val isMoving: Boolean
)

private enum class MotionPresetStatus {
    PENDING,
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

private data class CyclePreset(
    val id: Int,
    val name: String,
    val mode: Int,
    val servoIds: List<Int>,
    val poses: List<List<Float>>,
    val durationsMs: List<Int>,
    val maxLoops: Int,
    val cycleIndex: Int
)

private const val PRESET_PREFS_NAME = "motion_presets"
private const val PRESET_KEY = "preset_list"
private const val MAX_PRESET_COUNT = 30
private const val CYCLE_PRESET_PREFS_NAME = "motion_cycle_presets"
private const val CYCLE_PRESET_KEY = "cycle_preset_list"
private const val MAX_CYCLE_PRESET_COUNT = 20

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
    onGetCycleStatus: (index: Int) -> Unit,
    onRequestCycleList: () -> Unit = {},
    cycleList: List<CycleInfo> = emptyList()
) {
    val context = LocalContext.current
    val presets = remember { mutableStateListOf<MotionPreset>() }
    val cyclePresets = remember { mutableStateListOf<CyclePreset>() }
    var showPresets by remember { mutableStateOf(true) }
    var showCyclePresets by remember { mutableStateOf(true) }
    var nextPresetId by remember { mutableIntStateOf(1) }
    var nextCyclePresetId by remember { mutableIntStateOf(1) }
    var pendingGroupPresetId by remember { mutableStateOf<Int?>(null) }
    var lastHandledCompleteGroupId by remember { mutableIntStateOf(0) }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }

    var showPresetDialog by remember { mutableStateOf(false) }
    var showCyclePresetDialog by remember { mutableStateOf(false) }
    var showConflictDialog by remember { mutableStateOf(false) }
    var conflictMessage by remember { mutableStateOf("") }
    var editingPresetId by remember { mutableStateOf<Int?>(null) }
    var editingCyclePresetId by remember { mutableStateOf<Int?>(null) }
    var pendingStartAtMs by remember { mutableStateOf<Long?>(null) }
    var showStartFailureDialog by remember { mutableStateOf(false) }
    var startFailureMessage by remember { mutableStateOf("") }
    var pendingDeletePresetId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        val loaded = loadPresets(context)
        if (loaded.isNotEmpty()) {
            presets.addAll(loaded)
            nextPresetId = (loaded.maxOfOrNull { it.id } ?: 0) + 1
        }
        val loadedCycles = loadCyclePresets(context)
        if (loadedCycles.isNotEmpty()) {
            cyclePresets.addAll(loadedCycles)
            nextCyclePresetId = (loadedCycles.maxOfOrNull { it.id } ?: 0) + 1
        }
    }

    LaunchedEffect(presets) {
        snapshotFlow { presets.toList() }
            .distinctUntilChanged()
            .collectLatest { savePresets(context, it) }
    }

    LaunchedEffect(cyclePresets) {
        snapshotFlow { cyclePresets.toList() }
            .distinctUntilChanged()
            .collectLatest { saveCyclePresets(context, it) }
    }

    LaunchedEffect(Unit) {
        onRequestCycleList()
    }

    LaunchedEffect(currentGroupId) {
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
            pendingStartAtMs = null
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

    LaunchedEffect(pendingGroupPresetId, pendingStartAtMs) {
        val pendingId = pendingGroupPresetId ?: return@LaunchedEffect
        val startedAt = pendingStartAtMs ?: return@LaunchedEffect
        delay(1200)
        if (pendingGroupPresetId == pendingId && currentGroupId <= 0 && pendingStartAtMs == startedAt) {
            updatePresetStatus(presets, pendingId, null, clearGroup = true)
            pendingGroupPresetId = null
            pendingStartAtMs = null
            startFailureMessage = "启动 Motion 失败：未收到有效的 groupId。"
            showStartFailureDialog = true
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
                            MotionPresetStatus.PENDING -> Color(0xFF5C6BC0)
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
                                    MotionPresetStatus.PENDING -> Unit
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("预设动作列表", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Switch(checked = showPresets, onCheckedChange = { showPresets = it })
                }
                if (showPresets) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            editingPresetId = null
                            showPresetDialog = true
                        }) { Text("添加预设 Motion") }
                    }
                    if (presets.isEmpty()) {
                        Text("暂无预设动作，请点击“添加预设 Motion”。", color = Color.Gray)
                    } else {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 360.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
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
                                        pendingStartAtMs = System.currentTimeMillis()
                                        onStartMotion(preset.mode, preset.servoIds, preset.values, preset.durationMs)
                                        updatePresetStatus(presets, preset.id, MotionPresetStatus.PENDING)
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
                                    },
                                    onEdit = {
                                        editingPresetId = preset.id
                                        showPresetDialog = true
                                    },
                                    onDelete = {
                                        pendingDeletePresetId = preset.id
                                    }
                                )
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Cycle 预设列表", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Switch(checked = showCyclePresets, onCheckedChange = { showCyclePresets = it })
                }
                if (showCyclePresets) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            editingCyclePresetId = null
                            showCyclePresetDialog = true
                        }) { Text("添加 Cycle 预设") }
                        OutlinedButton(onClick = onRequestCycleList) { Text("同步 Cycle") }
                    }
                    if (cyclePresets.isEmpty()) {
                        Text("暂无 Cycle 预设。", color = Color.Gray)
                    } else {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            cyclePresets.forEach { preset ->
                                val status = cycleList.firstOrNull { it.index == preset.cycleIndex }
                                CyclePresetCard(
                                    preset = preset,
                                    status = status,
                                    onCreate = {
                                        onCreateCycle(
                                            preset.mode,
                                            preset.servoIds,
                                            preset.poses,
                                            preset.durationsMs,
                                            preset.maxLoops
                                        )
                                        onRequestCycleList()
                                    },
                                    onStart = { onStartCycle(preset.cycleIndex) },
                                    onPauseOrRestart = {
                                        if (status?.running == true) onPauseCycle(preset.cycleIndex)
                                        else onRestartCycle(preset.cycleIndex)
                                    },
                                    onRelease = { onReleaseCycle(preset.cycleIndex) },
                                    onGetStatus = { onGetCycleStatus(preset.cycleIndex) },
                                    onEdit = {
                                        editingCyclePresetId = preset.id
                                        showCyclePresetDialog = true
                                    },
                                    onDelete = {
                                        val idx = cyclePresets.indexOfFirst { it.id == preset.id }
                                        if (idx >= 0) cyclePresets.removeAt(idx)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

    }

    if (showPresetDialog) {
        val editingPreset = editingPresetId?.let { id -> presets.firstOrNull { it.id == id } }
        MotionPresetDialog(
            servoInfo = servoInfo,
            onDismiss = { showPresetDialog = false },
            preset = editingPreset,
            onConfirm = { preset ->
                if (preset.servoIds.isEmpty()) {
                    conflictMessage = "请至少选择一个舵机。"
                    showConflictDialog = true
                    return@MotionPresetDialog
                }
                if (editingPreset != null) {
                    val index = presets.indexOfFirst { it.id == editingPreset.id }
                    if (index >= 0) {
                        presets[index] = editingPreset.copy(
                            name = preset.name,
                            mode = preset.mode,
                            durationMs = preset.durationMs,
                            servoIds = preset.servoIds,
                            values = preset.values,
                            realtime = preset.realtime
                        )
                    }
                } else {
                    presets.add(preset.copy(id = nextPresetId++))
                    if (presets.size > MAX_PRESET_COUNT) {
                        repeat(presets.size - MAX_PRESET_COUNT) { presets.removeAt(0) }
                    }
                }
                showPresetDialog = false
                editingPresetId = null
            },
            onPreviewServoValue = onPreviewServoValue
        )
    }

    if (showCyclePresetDialog) {
        val editingCycle = editingCyclePresetId?.let { id -> cyclePresets.firstOrNull { it.id == id } }
        CyclePresetDialog(
            motionPresets = presets,
            preset = editingCycle,
            onDismiss = { showCyclePresetDialog = false },
            onConfirm = { result ->
                if (editingCycle != null) {
                    val idx = cyclePresets.indexOfFirst { it.id == editingCycle.id }
                    if (idx >= 0) cyclePresets[idx] = editingCycle.copy(
                        name = result.name,
                        mode = result.mode,
                        servoIds = result.servoIds,
                        poses = result.poses,
                        durationsMs = result.durationsMs,
                        maxLoops = result.maxLoops,
                        cycleIndex = result.cycleIndex
                    )
                } else {
                    cyclePresets.add(result.copy(id = nextCyclePresetId++))
                    if (cyclePresets.size > MAX_CYCLE_PRESET_COUNT) {
                        repeat(cyclePresets.size - MAX_CYCLE_PRESET_COUNT) { cyclePresets.removeAt(0) }
                    }
                }
                showCyclePresetDialog = false
                editingCyclePresetId = null
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

    if (showStartFailureDialog) {
        AlertDialog(
            onDismissRequest = { showStartFailureDialog = false },
            title = { Text("启动失败") },
            text = { Text(startFailureMessage) },
            confirmButton = {
                TextButton(onClick = { showStartFailureDialog = false }) { Text("知道了") }
            }
        )
    }

    if (pendingDeletePresetId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeletePresetId = null },
            title = { Text("删除预设") },
            text = { Text("确定要删除这个预设动作吗？") },
            confirmButton = {
                TextButton(onClick = {
                    val id = pendingDeletePresetId
                    if (id != null) {
                        val index = presets.indexOfFirst { it.id == id }
                        if (index >= 0) {
                            presets.removeAt(index)
                        }
                    }
                    pendingDeletePresetId = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletePresetId = null }) { Text("取消") }
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
            status == MotionPresetStatus.PENDING -> 0
            status == null -> 0
            else -> preset.elapsedMs
        }
        val startedAt = when {
            status == MotionPresetStatus.RUNNING && preset.groupId > 0 && preset.startedAtMs == null -> now
            status == MotionPresetStatus.PENDING -> null
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
    val progressColor = if (progress > 0f) Color(0xFF2E7D32) else borderColor
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
        val rect = androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height)
        val outlinePath = Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(rect, cornerRadius))
        }
        drawRoundRect(
            color = borderColor,
            size = size,
            cornerRadius = cornerRadius,
            style = Stroke(width = strokePx)
        )
        if (progress > 0f) {
            val measure = PathMeasure()
            measure.setPath(outlinePath, false)
            val segment = Path()
            val total = measure.length
            val normalized = progress.coerceIn(0f, 1f)
            val minSegment = strokePx * 4f
            val target = (total * normalized).coerceAtLeast(minSegment)
            if (target > 0f) {
                measure.getSegment(0f, target, segment, true)
                drawPath(
                    path = segment,
                    color = progressColor,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
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
    onResume: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val statusText = when (preset.status) {
        MotionPresetStatus.PENDING -> "等待中"
        MotionPresetStatus.RUNNING -> "运行中"
        MotionPresetStatus.PAUSED -> "已暂停"
        null -> "未运行"
    }
    val statusColor = when (preset.status) {
        MotionPresetStatus.PENDING -> Color(0xFF5C6BC0)
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
                OutlinedButton(onClick = onEdit) { Text("编辑") }
                OutlinedButton(onClick = onDelete) { Text("删除") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MotionPresetDialog(
    servoInfo: List<MotionServoInfo>,
    onDismiss: () -> Unit,
    preset: MotionPreset?,
    onConfirm: (MotionPreset) -> Unit,
    onPreviewServoValue: (servoId: Int, mode: Int, value: Float) -> Unit
) {
    var name by remember(preset) { mutableStateOf(preset?.name ?: "预设动作") }
    var mode by remember(preset) { mutableIntStateOf(preset?.mode ?: MotionProtocolCodec.MODE_PWM) }
    var durationMs by remember(preset) { mutableStateOf(preset?.durationMs?.toString() ?: "500") }
    var realtime by remember(preset) { mutableStateOf(preset?.realtime ?: false) }
    val selectedIds = remember(preset) { mutableStateListOf<Int>().apply { preset?.servoIds?.let { addAll(it) } } }
    val valueMap = remember(preset) {
        mutableStateMapOf<Int, Float>().apply {
            preset?.servoIds?.forEachIndexed { index, id ->
                this[id] = preset.values.getOrNull(index) ?: 0f
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (preset == null) "添加预设 Motion" else "编辑预设 Motion") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
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
                FlowRow(
                    maxItemsInEachRow = 3,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    servoInfo.take(6).forEach { servo ->
                        val selected = selectedIds.contains(servo.id)
                        val buttonColors = if (selected) {
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        } else {
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        }
                        Button(
                            colors = buttonColors,
                            onClick = {
                                if (selected) {
                                    selectedIds.remove(servo.id)
                                    valueMap.remove(servo.id)
                                } else {
                                    selectedIds.add(servo.id)
                                    if (!valueMap.containsKey(servo.id)) {
                                        valueMap[servo.id] = if (mode == MotionProtocolCodec.MODE_PWM) 1500f else 135f
                                    }
                                }
                            }
                        ) {
                            Text(servo.name, color = if (selected) Color.White else Color.Black)
                        }
                    }
                }

                selectedIds.sorted().forEach { servoId ->
                    val currentValue = valueMap[servoId] ?: if (mode == MotionProtocolCodec.MODE_PWM) 1500f else 135f
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("舵机 $servoId 值：${currentValue.toInt()}")
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
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
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
                Text(if (preset == null) "添加" else "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun CyclePresetCard(
    preset: CyclePreset,
    status: CycleInfo?,
    onCreate: () -> Unit,
    onStart: () -> Unit,
    onPauseOrRestart: () -> Unit,
    onRelease: () -> Unit,
    onGetStatus: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val statusText = when {
        status == null -> "未同步"
        status.running -> "运行中"
        status.active -> "已创建"
        else -> "未激活"
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(preset.name, style = MaterialTheme.typography.titleSmall)
                    Text("Cycle #${preset.cycleIndex} · $statusText", style = MaterialTheme.typography.bodySmall)
                    Text("Pose: ${preset.poses.size}  MaxLoops: ${preset.maxLoops}", style = MaterialTheme.typography.bodySmall)
                }
                Text(if (preset.mode == MotionProtocolCodec.MODE_PWM) "PWM" else "角度", style = MaterialTheme.typography.bodySmall)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCreate) { Text("创建") }
                OutlinedButton(onClick = onStart) { Text("启动") }
                OutlinedButton(onClick = onPauseOrRestart) { Text(if (status?.running == true) "暂停" else "重启") }
                OutlinedButton(onClick = onRelease) { Text("释放") }
                TextButton(onClick = onGetStatus) { Text("状态") }
                OutlinedButton(onClick = onEdit) { Text("编辑") }
                OutlinedButton(onClick = onDelete) { Text("删除") }
            }
        }
    }
}

@Composable
private fun CyclePresetDialog(
    motionPresets: List<MotionPreset>,
    preset: CyclePreset?,
    onDismiss: () -> Unit,
    onConfirm: (CyclePreset) -> Unit
) {
    var name by remember(preset) { mutableStateOf(preset?.name ?: "Cycle 预设") }
    var cycleIndex by remember(preset) { mutableStateOf(preset?.cycleIndex?.toString() ?: "0") }
    var maxLoops by remember(preset) { mutableStateOf(preset?.maxLoops?.toString() ?: "0") }
    val selectedMotionIds = remember(preset) { mutableStateListOf<Int>().apply {
        if (preset != null) {
            motionPresets.forEach { mp ->
                if (preset.poses.contains(mp.values) && preset.durationsMs.contains(mp.durationMs)) add(mp.id)
            }
        }
    } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (preset == null) "添加 Cycle 预设" else "编辑 Cycle 预设") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                TextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth())
                TextField(
                    value = cycleIndex,
                    onValueChange = { cycleIndex = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Cycle Index") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = maxLoops,
                    onValueChange = { maxLoops = it.filter { ch -> ch.isDigit() } },
                    label = { Text("最大循环次数(0=无限)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("选择构成 Cycle 的 Motion 预设")
                motionPresets.forEach { mp ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = selectedMotionIds.contains(mp.id),
                            onCheckedChange = { checked ->
                                if (checked) selectedMotionIds.add(mp.id) else selectedMotionIds.remove(mp.id)
                            }
                        )
                        Text("${mp.name} (${mp.durationMs}ms)")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val selected = motionPresets.filter { selectedMotionIds.contains(it.id) }
                if (selected.isEmpty()) return@TextButton
                val first = selected.first()
                if (!selected.all { it.mode == first.mode && it.servoIds == first.servoIds }) return@TextButton
                onConfirm(
                    CyclePreset(
                        id = 0,
                        name = name,
                        mode = first.mode,
                        servoIds = first.servoIds,
                        poses = selected.map { it.values },
                        durationsMs = selected.map { it.durationMs },
                        maxLoops = maxLoops.toIntOrNull() ?: 0,
                        cycleIndex = cycleIndex.toIntOrNull() ?: 0
                    )
                )
            }) { Text(if (preset == null) "添加" else "保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
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


private fun loadCyclePresets(context: android.content.Context): List<CyclePreset> {
    val prefs = context.getSharedPreferences(CYCLE_PRESET_PREFS_NAME, android.content.Context.MODE_PRIVATE)
    val raw = prefs.getString(CYCLE_PRESET_KEY, null) ?: return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val posesJson = obj.getJSONArray("poses")
                val poses = mutableListOf<List<Float>>()
                for (pi in 0 until posesJson.length()) {
                    poses.add(posesJson.getJSONArray(pi).toFloatList())
                }
                add(
                    CyclePreset(
                        id = obj.optInt("id", i + 1),
                        name = obj.optString("name", "Cycle 预设"),
                        mode = obj.optInt("mode", MotionProtocolCodec.MODE_PWM),
                        servoIds = obj.getJSONArray("servoIds").toIntList(),
                        poses = poses,
                        durationsMs = obj.getJSONArray("durationsMs").toIntList(),
                        maxLoops = obj.optInt("maxLoops", 0),
                        cycleIndex = obj.optInt("cycleIndex", 0)
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

private fun saveCyclePresets(context: android.content.Context, presets: List<CyclePreset>) {
    val array = JSONArray()
    presets.forEach { preset ->
        val obj = JSONObject()
        obj.put("id", preset.id)
        obj.put("name", preset.name)
        obj.put("mode", preset.mode)
        obj.put("servoIds", JSONArray(preset.servoIds))
        val posesArray = JSONArray()
        preset.poses.forEach { pose -> posesArray.put(JSONArray(pose)) }
        obj.put("poses", posesArray)
        obj.put("durationsMs", JSONArray(preset.durationsMs))
        obj.put("maxLoops", preset.maxLoops)
        obj.put("cycleIndex", preset.cycleIndex)
        array.put(obj)
    }
    context.getSharedPreferences(CYCLE_PRESET_PREFS_NAME, android.content.Context.MODE_PRIVATE)
        .edit()
        .putString(CYCLE_PRESET_KEY, array.toString())
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
