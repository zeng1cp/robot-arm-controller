package com.example.robotarmcontroller.ui


import android.Manifest
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.bluetooth.BluetoothDevice
import androidx.bluetooth.ScanResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.robotarmcontroller.BleManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RobotArmViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RobotArmViewModel::class.java)) {
            val bleManager = BleManager(context)
            @Suppress("UNCHECKED_CAST")
            return RobotArmViewModel(bleManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

private const val TAG = "RobotArmViewModel"

class RobotArmViewModel(
    private val bleManager: BleManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<RobotArmUiState>(RobotArmUiState())
    val uiState: StateFlow<RobotArmUiState> = _uiState.asStateFlow()

    private val scanResults: StateFlow<List<ScanResult>> = bleManager.scanResults
    private var scanJob: Job? = null
    private var scanResultCollectJob: Job? = null
    private var connectJob: Job? = null
    private var connectionCollectJob: Job? = null

    init {
        _uiState.update { currentState ->
            val newList = listOf(
                ServoState(0, "servo_0"),
                ServoState(1, "servo_1"),
                ServoState(2, "servo_2"),
                ServoState(3, "servo_3"),
                ServoState(4, "servo_4"),
                ServoState(5, "servo_5"),
            )
            currentState.copy(servoList = newList)
        }
        // 收集 BLE 连接状态
        //collectBleConnectionState()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun bleScan() {
        if (scanJob?.isActive != true) {
            scanJob = viewModelScope.launch {
                bleManager.scan()
            }
        }

        if (scanResultCollectJob?.isActive != true) {
            scanResultCollectJob = viewModelScope.launch {
                bleManager.scanResults.collect { scanResults ->
                    _uiState.update { currentState ->
                        currentState.copy(foundBleDeviceList = scanResults)
                    }
                }
            }
        }

    }

    fun bleScanStop() {
        scanJob?.cancel()
        scanJob = null
        scanResultCollectJob?.cancel()
        scanResultCollectJob = null
        Log.i(TAG, "bleScan stopped")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun bleConnect(device: BluetoothDevice) {
        bleScanStop()
        connectJob = viewModelScope.launch {
            bleManager.connect(device = device)
        }
    }

    // 添加断开连接方法
    fun bleDisconnect() {
        connectJob?.cancel()
        connectJob = null
        // 这里需要 BleManager 添加 disconnect 方法
    }

    private fun collectBleConnectionState() {
        if (connectionCollectJob?.isActive != true) {
            connectionCollectJob = viewModelScope.launch {
                bleManager.connectionState.collect { connectionState ->
                    _uiState.update { currentState ->
                        when (connectionState) {
                            is BleManager.ConnectionState.Ready -> {
                                currentState.copy(
                                    bleState = BleState.Connected
                                )
                            }

                            is BleManager.ConnectionState.Connecting -> {
                                currentState.copy(
                                    bleState = BleState.Connecting
                                )
                            }

                            is BleManager.ConnectionState.Error -> {
                                currentState.copy(
                                    bleState = BleState.Error(connectionState.message)
                                )
                            }

                            is BleManager.ConnectionState.Disconnected -> {
                                currentState.copy(
                                    bleState = BleState.Idle,
                                    connectedBleDevice = null
                                )
                            }

                            else -> currentState
                        }
                    }
                }
            }
        }
    }

    fun bleWrite(data: ByteArray) {
        bleManager.write(data)
    }

    fun onPwmChange(index: Int, pwm: Float) {
        _uiState.update { currentState ->
            val newList = currentState.servoList.toMutableList()
            val oldServo = newList[index]
            newList[index] = oldServo.copy(pwm = pwm)

            currentState.copy(servoList = newList)
        }
    }

    fun onPwmChangeFinished(index: Int) {
        val newList = _uiState.value.servoList.toMutableList()
        val oldServo = newList[index]
        var pwm = oldServo.pwm
        pwm = pwm.coerceIn(500f, 2500f)
        _uiState.update { currentState ->
            newList[index] = oldServo.copy(pwm = pwm)
            currentState.copy(servoList = newList)
        }
        // send massage

    }


}

