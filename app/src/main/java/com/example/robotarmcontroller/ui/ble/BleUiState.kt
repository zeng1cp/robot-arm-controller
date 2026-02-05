package com.example.robotarmcontroller.ui.ble

import androidx.bluetooth.ScanResult

sealed class BleConnectionState {
    object Idle : BleConnectionState()
    object Scanning : BleConnectionState()
    object Connecting : BleConnectionState()
    object Connected : BleConnectionState()
    object Disconnected : BleConnectionState()
    data class Error(val message: String) : BleConnectionState()
}

data class BleUiState(
    val connectionState: BleConnectionState = BleConnectionState.Idle,
    val foundDevices: List<ScanResult> = emptyList(),
    val connectedDevice: ScanResult? = null,
    val isScanDialogVisible: Boolean = false,
    val lastError: String? = null
)