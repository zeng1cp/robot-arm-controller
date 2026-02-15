package com.example.robotarmcontroller.ui.ble

import android.annotation.SuppressLint
import android.util.Log
import androidx.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.robotarmcontroller.common.AppError
import com.example.robotarmcontroller.data.BleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "BleViewModel"
@HiltViewModel
class BleViewModel @Inject constructor(
    private val bleRepository: BleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BleUiState())
    val uiState: StateFlow<BleUiState> = _uiState.asStateFlow()
    private var scanJob: Job? = null
    private var scanResultCollectJob: Job? = null
    private var connectJob: Job? = null

    init {
        monitorConnectionState()
    }
    private fun monitorConnectionState() {
        viewModelScope.launch {
            bleRepository.connectionState.collect { state ->
                _uiState.update { current ->
                    val connectionState = when (state) {
                        is BleRepository.ConnectionState.Idle -> BleConnectionState.Idle
                        is BleRepository.ConnectionState.Connecting -> BleConnectionState.Connecting
                        is BleRepository.ConnectionState.DiscoveringServices -> BleConnectionState.Connecting
                        is BleRepository.ConnectionState.Ready -> BleConnectionState.Connected
                        is BleRepository.ConnectionState.Error -> BleConnectionState.Error(state.error)
                        is BleRepository.ConnectionState.Disconnected -> BleConnectionState.Disconnected
                    }
                    val connectedDevice = if (state is BleRepository.ConnectionState.Ready) {
                        findConnectedDevice()
                    } else null
                    current.copy(
                        connectionState = connectionState,
                        connectedDevice = connectedDevice
                    )
                }
            }
        }
    }
    fun showScanDialog() {
        _uiState.update { it.copy(isScanDialogVisible = true) }
        startScan()
    }

    fun hideScanDialog() {
        _uiState.update { it.copy(isScanDialogVisible = false) }
        stopScan()
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        if (scanJob?.isActive != true) {
            _uiState.update { it.copy(connectionState = BleConnectionState.Scanning) }
            scanJob = viewModelScope.launch {
                try {
                    bleRepository.startScan()
                } catch (e: Exception) {
                    Log.e(TAG, "扫描失败: ${e.message}")
                    _uiState.update {
                        it.copy(
                            connectionState = BleConnectionState.Error(AppError.BluetoothError.ScanFailed(e)),
                            lastError = "扫描失败: ${e.message}"
                        )
                    }
                }
            }
        }

        if (scanResultCollectJob?.isActive != true) {
            scanResultCollectJob = viewModelScope.launch {
                bleRepository.scanResults.collect { scanResults ->
                    _uiState.update { currentState -> currentState.copy(foundDevices = scanResults) }
                }
            }
        }
    }

    private fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        scanResultCollectJob?.cancel()
        scanResultCollectJob = null
        bleRepository.stopScan()

        if (_uiState.value.connectionState !is BleConnectionState.Connected) {
            _uiState.update { it.copy(connectionState = BleConnectionState.Idle) }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        hideScanDialog()
        connectJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(connectionState = BleConnectionState.Connecting) }
                bleRepository.connect(device = device)
            } catch (e: Exception) {
                Log.e(TAG, "连接失败: ${e.message}")
                _uiState.update {
                    it.copy(
                        connectionState = BleConnectionState.Error(AppError.BluetoothError.ConnectionFailed(e)),
                        lastError = "连接失败: ${e.message}"
                    )
                }
            }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        bleRepository.disconnect()
        _uiState.update {
            it.copy(
                connectionState = BleConnectionState.Idle,
                connectedDevice = null
            )
        }
    }

    private fun findConnectedDevice(): androidx.bluetooth.ScanResult? {
        val connectedDevice = bleRepository.connectedDevice ?: return null
        return _uiState.value.foundDevices.firstOrNull {
            it.device.name == connectedDevice.name
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
        connectJob?.cancel()
        bleRepository.close()
    }
}
