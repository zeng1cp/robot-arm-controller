package com.example.robotarmcontroller.ui

import androidx.bluetooth.BluetoothDevice
import androidx.bluetooth.ScanResult
import java.util.UUID

data class RobotArmUiState(
    val servoList: List<ServoState> = listOf(),

    val foundBleDeviceList: List<ScanResult> = listOf(),
    val connectedBleDevice: ScanResult? = null,
    val bleState: BleState = BleState.Idle
)

data class ServoState(
    val id: Int = 0,
    val name: String = "",
    val pwm: Float = 1500f,
)


sealed class BleState {
    object Idle : BleState()
    object Scanning : BleState()
    object Connecting : BleState()
    object Connected : BleState()
    data class Error(val message: String) : BleState()
}