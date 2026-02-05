package com.example.robotarmcontroller.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.robotarmcontroller.ui.ble.*
import com.example.robotarmcontroller.ui.robot.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier
) {
    // 获取两个 ViewModel
    val bleViewModel: BleViewModel = viewModel()
    val robotViewModel: RobotViewModel = viewModel()

    val bleState by bleViewModel.uiState.collectAsState()
    val robotState by robotViewModel.uiState.collectAsState()

    // 用于显示 Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 当蓝牙连接状态变化时，更新机械臂的连接状态
    LaunchedEffect(bleState.connectionState) {
        when (bleState.connectionState) {
            is BleConnectionState.Connected -> {
                // 创建 BLE 服务并传递给机械臂 ViewModel
                val bleService = object : BleService {
                    override suspend fun sendServoCommand(servoId: Int, pwmValue: Int): Boolean {
                        val data = ByteArray(3)
                        data[0] = servoId.toByte()
                        data[1] = ((pwmValue shr 8) and 0xFF).toByte()
                        data[2] = (pwmValue and 0xFF).toByte()
                        return bleViewModel.sendFrame(0x23u, data)
                    }

                    override suspend fun sendTestMessage(message: String): Boolean {
                        return bleViewModel.sendFrame(0x22u, message.toByteArray())
                    }

                    override suspend fun sendFrame(frameType: UInt, data: ByteArray): Boolean {
                        return bleViewModel.sendFrame(frameType, data)
                    }
                }
                robotViewModel.setBleService(bleService)

                // 显示连接成功的消息
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = "蓝牙连接成功",
                        duration = SnackbarDuration.Short
                    )
                }
            }
            is BleConnectionState.Error -> {
                robotViewModel.disconnect()

                // 显示错误消息
                val errorMsg = (bleState.connectionState as BleConnectionState.Error).message
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = "连接错误: $errorMsg",
                        duration = SnackbarDuration.Long
                    )
                }
            }
            BleConnectionState.Idle, BleConnectionState.Disconnected -> {
                robotViewModel.disconnect()
            }
            else -> {
                // 其他状态不需要特殊处理
            }
        }
    }

    // 监听接收到的帧数据，并传递给 RobotViewModel
    LaunchedEffect(Unit) {
        bleViewModel.frameData.collect { frameData ->
//            robotViewModel.handleFrame(frameData)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("机械臂控制器") },
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
            // 蓝牙控制区域
            BleScreen(
                state = bleState,
                onScanClick = { bleViewModel.showScanDialog() },
                onDisconnectClick = { bleViewModel.disconnect() },
                onDeviceClick = { device -> bleViewModel.connectToDevice(device) },
                onRefreshClick = {
                    // 重新扫描
                    bleViewModel.showScanDialog()
                },
                onDismissScanDialog = { bleViewModel.hideScanDialog() },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 机械臂控制区域
            RobotScreen(
                state = robotState,
                onPwmChange = robotViewModel::onPwmChange,
                onPwmChangeFinished = robotViewModel::onPwmChangeFinished,
                onAngleChange = robotViewModel::onAngleChange,
                onAngleChangeFinished = robotViewModel::onAngleChangeFinished,
                onToggleControlMode = { robotViewModel.toggleControlMode() },
                onSendTestClick = { robotViewModel.sendTestMessage() },
                onClearHistoryClick = { robotViewModel.clearHistory() },
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            )
        }
    }
}