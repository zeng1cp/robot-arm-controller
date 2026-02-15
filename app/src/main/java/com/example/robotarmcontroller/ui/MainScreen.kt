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
import com.example.robotarmcontroller.ui.ble.BleConnectionState
import com.example.robotarmcontroller.ui.ble.BleScreen
import com.example.robotarmcontroller.ui.ble.BleViewModel
import com.example.robotarmcontroller.ui.motion.MotionScreen
import com.example.robotarmcontroller.ui.robot.RobotScreen
import com.example.robotarmcontroller.ui.robot.RobotViewModel

private enum class MainTab(val title: String) {
    SERVO("Servo"),
    MOTION("Motion"),
    ARM("Arm")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    bleViewModel: BleViewModel = hiltViewModel(),
    robotViewModel: RobotViewModel = hiltViewModel()
) {
    val bleState by bleViewModel.uiState.collectAsState()
    val robotState by robotViewModel.uiState.collectAsState()
    val cycleList by robotViewModel.cycleList.collectAsState()

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
                MainTab.SERVO -> RobotScreen(
                    state = robotState,
                    onPwmChange = robotViewModel::onPwmChange,
                    onPwmChangeFinished = robotViewModel::onPwmChangeFinished,
                    onAngleChange = robotViewModel::onAngleChange,
                    onAngleChangeFinished = robotViewModel::onAngleChangeFinished,
                    onToggleControlMode = robotViewModel::toggleControlMode,
                    onClearHistoryClick = robotViewModel::clearHistory,
                    onServoEnableClick = robotViewModel::setServoEnable,
                    onServoDisableClick = robotViewModel::setServoDisable,
                    onAllServoHomeClick = robotViewModel::setAllServoHome,
                    onSyncAllServoStatusClick = robotViewModel::requestAllServoStatus,
                    cycleList = cycleList,
                    onCycleStart = robotViewModel::startMotionCycle,
                    onCyclePause = robotViewModel::pauseMotionCycle,
                    onCycleRestart = robotViewModel::restartMotionCycle,
                    onCycleRelease = robotViewModel::releaseMotionCycle,
                    onRequestCycleStatusClick = robotViewModel::requestMotionCycleStatus,
                    onRequestCycleListClick = robotViewModel::requestCycleList,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                )

                MainTab.MOTION -> MotionScreen(
                    currentGroupId = robotState.motionGroupId,
                    motionCompleteGroupId = robotState.motionCompleteGroupId,
                    servoInfo = robotState.servoList.map {
                        com.example.robotarmcontroller.ui.motion.MotionServoInfo(
                            id = it.id,
                            name = it.name,
                            isMoving = it.isMoving
                        )
                    },
                    onStartMotion = robotViewModel::startMotion,
                    onStopMotion = robotViewModel::stopMotion,
                    onPauseMotion = robotViewModel::pauseMotion,
                    onResumeMotion = robotViewModel::resumeMotion,
                    onGetMotionStatus = robotViewModel::requestMotionStatus,
                    onPreviewServoValue = robotViewModel::previewServoValue,
                    onCreateCycle = robotViewModel::createMotionCycle,
                    onStartCycle = robotViewModel::startMotionCycle,
                    onRestartCycle = robotViewModel::restartMotionCycle,
                    onPauseCycle = robotViewModel::pauseMotionCycle,
                    onReleaseCycle = robotViewModel::releaseMotionCycle,
                    onGetCycleStatus = robotViewModel::requestMotionCycleStatus,
                    onRequestCycleList = robotViewModel::requestCycleList,
                    cycleList = cycleList,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                )

                MainTab.ARM -> PlaceholderPage(title = "Arm 控制（待实现）")
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

@Composable
private fun PlaceholderPage(title: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("后续将按同一协议框架接入命令、状态同步与控制面板。")
    }
}