package com.example.robotarmcontroller.ui.ble

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun BleScreen(
    state: BleUiState,
    onScanClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onDeviceClick: (androidx.bluetooth.BluetoothDevice) -> Unit,
    onRefreshClick: () -> Unit,
    onDismissScanDialog: () -> Unit,
    showStatusCard: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (showStatusCard) {
        Column(modifier = modifier) {
            BleStatusCard(
                connectionState = state.connectionState,
                connectedDevice = state.connectedDevice,
                onScanClick = onScanClick,
                onDisconnectClick = onDisconnectClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }

    // 扫描设备弹窗
    if (state.isScanDialogVisible) {
        BleScanDialog(
            devices = state.foundDevices,
            isScanning = state.connectionState == BleConnectionState.Scanning,
            onDeviceClick = { device ->
                onDeviceClick(device.device)
            },
            onRefreshClick = onRefreshClick,
            onDismiss = onDismissScanDialog
        )
    }
}

@Composable
fun BleStatusCard(
    connectionState: BleConnectionState,
    connectedDevice: androidx.bluetooth.ScanResult?,
    onScanClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    // 状态和图标
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when (connectionState) {
                                BleConnectionState.Connected -> Icons.Default.BluetoothConnected
                                BleConnectionState.Scanning -> Icons.AutoMirrored.Filled.BluetoothSearching
                                is BleConnectionState.Error -> Icons.Default.Warning
                                else -> Icons.Default.Bluetooth
                            },
                            contentDescription = "蓝牙状态",
                            tint = when (connectionState) {
                                BleConnectionState.Connected -> Color.Green
                                is BleConnectionState.Error -> Color.Red
                                BleConnectionState.Scanning -> Color.Blue
                                else -> Color.Gray
                            },
                            modifier = Modifier.size(24.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = when (connectionState) {
                                BleConnectionState.Connected -> "已连接"
                                BleConnectionState.Connecting -> "连接中..."
                                BleConnectionState.Scanning -> "扫描中..."
                                is BleConnectionState.Error -> "错误"
                                else -> "未连接"
                            },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (connectionState) {
                                BleConnectionState.Connected -> Color.Green
                                BleConnectionState.Connecting -> Color.Blue
                                BleConnectionState.Scanning -> Color.Blue
                                is BleConnectionState.Error -> Color.Red
                                else -> Color.Gray
                            }
                        )
                    }

                    // 设备信息
                    if (connectedDevice != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = connectedDevice.device.name ?: "未知设备",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = connectedDevice.deviceAddress.address,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }

                // 操作按钮
                when (connectionState) {
                    BleConnectionState.Connected -> {
                        Button(
                            onClick = onDisconnectClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Red.copy(alpha = 0.1f),
                                contentColor = Color.Red
                            )
                        ) {
                            Text("断开")
                        }
                    }
                    else -> {
                        Button(
                            onClick = onScanClick,
                            enabled = connectionState != BleConnectionState.Scanning
                        ) {
                            Text("扫描设备")
                        }
                    }
                }
            }

            // 错误信息显示
            if (connectionState is BleConnectionState.Error) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "错误: ${connectionState.message}",
                    color = Color.Red,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun BleScanDialog(
    devices: List<androidx.bluetooth.ScanResult>,
    isScanning: Boolean,
    onDeviceClick: (androidx.bluetooth.ScanResult) -> Unit,
    onRefreshClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp, max = 500.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // 标题栏
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "选择蓝牙设备",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Text("扫描中...", fontSize = 12.sp, color = Color.Blue)
                        }

                        IconButton(
                            onClick = onRefreshClick,
                            enabled = !isScanning
                        ) {
                            Icon(Icons.Default.Refresh, "刷新")
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "关闭")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 设备列表
                if (devices.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isScanning) {
                            Text("正在搜索设备...")
                        } else {
                            Text("未找到设备")
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = onRefreshClick) {
                                Text("重新扫描")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f)
                    ) {
                        items(devices) { device ->
                            BleDeviceItem(
                                device = device,
                                onClick = { onDeviceClick(device) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 底部信息
                Text(
                    text = "找到 ${devices.size} 个设备",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun BleDeviceItem(
    device: androidx.bluetooth.ScanResult,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = device.device.name ?: "未知设备",
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    )
                    Text(
                        text = device.deviceAddress.address,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "${device.rssi} dBm",
                        fontSize = 12.sp,
                        color = when {
                            device.rssi > -60 -> Color.Green
                            device.rssi > -70 -> Color(0xFF4CAF50)
                            device.rssi > -80 -> Color(0xFFFF9800)
                            else -> Color.Red
                        }
                    )
                    Text(
                        text = when (device.device.bondState) {
                            10 -> "未配对" // BOND_NONE
                            11 -> "配对中" // BOND_BONDING
                            12 -> "已配对" // BOND_BONDED
                            else -> "未知"
                        },
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}