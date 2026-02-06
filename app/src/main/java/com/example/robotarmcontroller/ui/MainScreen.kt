package com.example.robotarmcontroller.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.robotarmcontroller.protocol.ProtocolCommand
import com.example.robotarmcontroller.protocol.ProtocolFrameType
import com.example.robotarmcontroller.protocol.ServoProtocolCodec
import com.example.robotarmcontroller.protocol.ServoSetPwmPayload
import com.example.robotarmcontroller.protocol.SysProtocolCodec
import com.example.robotarmcontroller.ui.ble.BleConnectionState
import com.example.robotarmcontroller.ui.ble.BleScreen
import com.example.robotarmcontroller.ui.ble.BleViewModel
import com.example.robotarmcontroller.ui.robot.BleService
import com.example.robotarmcontroller.ui.robot.RobotScreen
import com.example.robotarmcontroller.ui.robot.RobotViewModel
import kotlinx.coroutines.launch

private enum class MainTab(val title: String) {
    SERVO("Servo"),
    MOTION("Motion"),
    ARM("Arm"),
    CYCLE("Cycle")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val bleViewModel: BleViewModel = viewModel()
    val robotViewModel: RobotViewModel = viewModel()

    val bleState by bleViewModel.uiState.collectAsState()
    val robotState by robotViewModel.uiState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(bleState.connectionState) {
        when (bleState.connectionState) {
            is BleConnectionState.Connected -> {
                val bleService = object : BleService {
                    override suspend fun sendServoCommand(servoId: Int, pwmValue: Int): Boolean {
                        val data = ServoProtocolCodec.encodeSetPwm(
                            ServoSetPwmPayload(servoId = servoId, pwmValue = pwmValue)
                        )
                        return bleViewModel.sendFrame(ProtocolFrameType.SERVO, data)
                    }

                    override suspend fun setServoEnable(enable: Boolean): Boolean {
                        val data = if (enable) {
                            byteArrayOf(ProtocolCommand.Servo.ENABLE.toByte())
                        } else {
                            byteArrayOf(ProtocolCommand.Servo.DISABLE.toByte())
                        }
                        return bleViewModel.sendFrame(ProtocolFrameType.SERVO, data)
                    }

                    override suspend fun requestServoStatus(servoId: Int): Boolean {
                        val data = ServoProtocolCodec.encodeGetStatus(servoId)
                        return bleViewModel.sendFrame(ProtocolFrameType.SERVO, data)
                    }

                    override suspend fun sendTestMessage(message: String): Boolean {
                        return bleViewModel.sendFrame(
                            ProtocolFrameType.SYS,
                            SysProtocolCodec.encodePing(message)
                        )
                    }

                    override suspend fun sendFrame(frameType: UInt, data: ByteArray): Boolean {
                        return bleViewModel.sendFrame(frameType, data)
                    }
                }
                robotViewModel.setBleService(bleService)

                scope.launch {
                    snackbarHostState.showSnackbar("蓝牙连接成功", duration = SnackbarDuration.Short)
                }
            }

            is BleConnectionState.Error -> {
                robotViewModel.disconnect()
                val errorMsg = (bleState.connectionState as BleConnectionState.Error).message
                scope.launch {
                    snackbarHostState.showSnackbar("连接错误: $errorMsg", duration = SnackbarDuration.Long)
                }
            }

            BleConnectionState.Idle,
            BleConnectionState.Disconnected -> robotViewModel.disconnect()

            else -> Unit
        }
    }

    LaunchedEffect(Unit) {    
        bleViewModel.incomingFrames.collect { frame ->
            robotViewModel.onIncomingProtocolFrame(frame)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 仅保留弹窗和扫描逻辑，不占主要控制区高度
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

            TabRow(selectedTabIndex = selectedTabIndex) {
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
                    onSyncAllServoStatusClick = robotViewModel::requestAllServoStatus,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                )

                MainTab.MOTION -> PlaceholderPage(title = "Motion 控制（待实现）")
                MainTab.ARM -> PlaceholderPage(title = "Arm 控制（待实现）")
                MainTab.CYCLE -> PlaceholderPage(title = "Motion Cycle 控制（待实现）")
            }
        }
    }
}

@Composable
private fun BleConnectionBadge(state: BleConnectionState) {
    val (icon, text, tint) = when (state) {
        BleConnectionState.Connected -> Triple(Icons.Default.BluetoothConnected, "已连接", Color(0xFF2E7D32))
        BleConnectionState.Connecting -> Triple(Icons.AutoMirrored.Filled.BluetoothSearching, "连接中", Color(0xFF1565C0))
        BleConnectionState.Scanning -> Triple(Icons.AutoMirrored.Filled.BluetoothSearching, "扫描中", Color(0xFF1565C0))
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
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("后续将按同一协议框架接入命令、状态同步与控制面板。")
    }
}
