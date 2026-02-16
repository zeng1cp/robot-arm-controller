package com.example.robotarmcontroller.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.robotarmcontroller.data.model.CycleInfo
import com.example.robotarmcontroller.ui.arm.ArmScreen
import com.example.robotarmcontroller.ui.arm.ArmViewModel
import com.example.robotarmcontroller.ui.ble.BleConnectionState
import com.example.robotarmcontroller.ui.ble.BleScreen
import com.example.robotarmcontroller.ui.ble.BleViewModel
import com.example.robotarmcontroller.ui.motion.MotionScreen
import com.example.robotarmcontroller.ui.motion.MotionViewModel
import com.example.robotarmcontroller.ui.servo.ServoScreen
import com.example.robotarmcontroller.ui.servo.ServoViewModel
import com.example.robotarmcontroller.ui.cycle.CycleScreen
import com.example.robotarmcontroller.ui.cycle.CycleViewModel

private enum class MainTab(val title: String) {
    SERVO("Servo"),
    MOTION("Motion"),
    CYCLE("Cycle"),
    ARM("Arm")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    bleViewModel: BleViewModel = hiltViewModel(),
    servoViewModel: ServoViewModel = hiltViewModel(),
    motionViewModel: MotionViewModel = hiltViewModel(),
    armViewModel: ArmViewModel = hiltViewModel(),
    cycleViewModel: CycleViewModel = hiltViewModel()
) {
    val bleState by bleViewModel.uiState.collectAsState()
    val servoState by servoViewModel.uiState.collectAsState()
    val motionState by motionViewModel.uiState.collectAsState()
    val armState by armViewModel.uiState.collectAsState()
    val cycleList by cycleViewModel.cycleList.collectAsState<List<CycleInfo>>()
    val cycleState by cycleViewModel.uiState.collectAsState()
    val servoStates by cycleViewModel.servoStates.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row {
                        Text("机械臂控制器")
                        Spacer(modifier = Modifier.width(8.dp))
                        BleConnectionBadge(state = bleState.connectionState)
                    }
                },
                actions = {
                    if (bleState.connectionState == BleConnectionState.Connected) {
                        TextButton(onClick = { bleViewModel.disconnect() }) { Text("断开") }
                    } else {
                        TextButton(onClick = { bleViewModel.showScanDialog() }) { Text("连接") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 蓝牙扫描弹窗和状态卡片（仅用于弹窗，不占主要高度）
            BleScreen(
                state = bleState,
                onScanClick = { bleViewModel.showScanDialog() },
                onDisconnectClick = { bleViewModel.disconnect() },
                onDeviceClick = { device -> bleViewModel.connectToDevice(device) },
                onRefreshClick = { bleViewModel.showScanDialog() },
                onDismissScanDialog = { bleViewModel.hideScanDialog() },
                showStatusCard = false,
                modifier = Modifier.fillMaxWidth()
            )

            PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
                MainTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(tab.title) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (MainTab.entries[selectedTabIndex]) {
                MainTab.SERVO -> ServoScreen(
                    state = servoState,
                    onPwmChange = servoViewModel::onPwmChange,
                    onPwmChangeFinished = servoViewModel::onPwmChangeFinished,
                    onAngleChange = servoViewModel::onAngleChange,
                    onAngleChangeFinished = servoViewModel::onAngleChangeFinished,
                    onToggleControlMode = servoViewModel::toggleControlMode,
                    onClearHistoryClick = servoViewModel::clearHistory,
                    onServoEnableClick = servoViewModel::setServoEnable,
                    onServoDisableClick = servoViewModel::setServoDisable,
                    onAllServoHomeClick = servoViewModel::sendServosHome,
                    onSyncAllServoStatusClick = servoViewModel::requestAllServoStatus,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                )

                MainTab.MOTION -> MotionScreen(
                    currentGroupId = motionState.motionGroupId,
                    motionCompleteGroupId = motionState.motionCompleteGroupId,
                    servoInfo = servoState.servoList.map {
                        com.example.robotarmcontroller.ui.motion.MotionServoInfo(
                            id = it.id,
                            name = it.name,
                            isMoving = it.isMoving
                        )
                    },
                    onStartMotion = motionViewModel::startMotion,
                    onStopMotion = motionViewModel::stopMotion,
                    onPauseMotion = motionViewModel::pauseMotion,
                    onResumeMotion = motionViewModel::resumeMotion,
                    onGetMotionStatus = motionViewModel::requestMotionStatus,
                    onPreviewServoValue = motionViewModel::previewServoValue,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                )

                MainTab.CYCLE -> CycleScreen(
                    state = cycleState,
                    servoStates = servoStates,
                    onCreateCycle = cycleViewModel::createCycle,
                    onStartCycle = cycleViewModel::startCycle,
                    onRestartCycle = cycleViewModel::restartCycle,
                    onPauseCycle = cycleViewModel::pauseCycle,
                    onReleaseCycle = cycleViewModel::releaseCycle,
                    onRequestCycleStatus = cycleViewModel::requestCycleStatus,
                    onRequestCycleList = cycleViewModel::requestCycleList,
                    onUpdateCreationMode = cycleViewModel::updateCreationMode,
                    onUpdateSelectedServoIds = cycleViewModel::updateSelectedServoIds,
                    onAddPose = cycleViewModel::addPose,
                    onUpdatePose = cycleViewModel::updatePose,
                    onRemovePose = cycleViewModel::removePose,
                    onSetEditingPose = cycleViewModel::setEditingPose,
                    onUpdateCurrentPoseValue = cycleViewModel::updateCurrentPoseValue,
                    onClearCreationState = cycleViewModel::clearCreationState,
                    onDeleteCycle = cycleViewModel::deleteCycle,
                    onConfirmDelete = cycleViewModel::confirmDeleteCycle,
                    onStartEditingCycle = cycleViewModel::startEditingCycle,
                    onCancelEditingCycle = cycleViewModel::cancelEditingCycle,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                )

                MainTab.ARM -> ArmScreen(
                    state = armState,
                    onExecutePreset = armViewModel::executePreset,
                    onStopAllMotion = armViewModel::stopAllMotion,
                    onSelectPreset = armViewModel::selectPreset,
                    onToggleCooperativeMode = armViewModel::toggleCooperativeMode,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BleConnectionBadge(state: BleConnectionState) {
    val (icon, text, tint) = when (state) {
        BleConnectionState.Connected -> Triple(
            Icons.Default.BluetoothConnected,
            "已连接",
            Color(0xFF2E7D32)
        )
        BleConnectionState.Connecting -> Triple(
            Icons.AutoMirrored.Filled.BluetoothSearching,
            "连接中",
            Color(0xFF1565C0)
        )
        BleConnectionState.Scanning -> Triple(
            Icons.AutoMirrored.Filled.BluetoothSearching,
            "扫描中",
            Color(0xFF1565C0)
        )
        is BleConnectionState.Error -> Triple(Icons.Default.Warning, "错误", Color(0xFFC62828))
        else -> Triple(Icons.Default.Bluetooth, "未连接", Color.Gray)
    }
    Row {
        Icon(icon, contentDescription = text, tint = tint)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, color = tint)
    }
}