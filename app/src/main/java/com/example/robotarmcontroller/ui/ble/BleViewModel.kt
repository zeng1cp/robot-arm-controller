package com.example.robotarmcontroller.ui.ble

import android.annotation.SuppressLint
import android.util.Log
import androidx.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.robotarmcontroller.BleManager
import com.example.robotarmcontroller.protocol.ProtocolFrame
import com.example.robotarmcontroller.tinyframe.BleTinyFramePort
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "BleViewModel"

class BleViewModel(
    private val bleManager: BleManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(BleUiState())
    val uiState: StateFlow<BleUiState> = _uiState.asStateFlow()

    private var bleTinyFramePort: BleTinyFramePort = BleTinyFramePort(bleManager, viewModelScope)

    private var scanJob: Job? = null
    private var scanResultCollectJob: Job? = null
    private var connectJob: Job? = null

    private val _incomingFrames = MutableSharedFlow<ProtocolFrame>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val incomingFrames: SharedFlow<ProtocolFrame> = _incomingFrames.asSharedFlow()

    init {
        monitorConnectionState()
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
                    bleManager.scan()
                } catch (e: Exception) {
                    Log.e(TAG, "扫描失败: ${e.message}")
                    _uiState.update {
                        it.copy(
                            connectionState = BleConnectionState.Error("扫描失败: ${e.message}"),
                            lastError = "扫描失败: ${e.message}"
                        )
                    }
                }
            }
        }

        if (scanResultCollectJob?.isActive != true) {
            scanResultCollectJob = viewModelScope.launch {
                bleManager.scanResults.collect { scanResults ->
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
        bleManager.stopScan()

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
                bleManager.connect(device = device)
            } catch (e: Exception) {
                Log.e(TAG, "连接失败: ${e.message}")
                _uiState.update {
                    it.copy(
                        connectionState = BleConnectionState.Error("连接失败: ${e.message}"),
                        lastError = "连接失败: ${e.message}"
                    )
                }
            }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        bleManager.disconnect()
        bleTinyFramePort.close()
        _uiState.update {
            it.copy(
                connectionState = BleConnectionState.Idle,
                connectedDevice = null
            )
        }
    }

    fun sendFrame(frameType: UInt, data: ByteArray): Boolean {
        return bleTinyFramePort.sendFrame(frameType, data)
    }

    private fun findConnectedDevice(): androidx.bluetooth.ScanResult? {
        val connectedDevice = bleManager.connectedDevice ?: return null
        return _uiState.value.foundDevices.firstOrNull {
            it.device.name == connectedDevice.name
        }
    }

    private fun monitorConnectionState() {
        viewModelScope.launch {
            bleManager.connectionState.collect { connectionState ->
                when (connectionState) {
                    is BleManager.ConnectionState.Ready -> {
                        val initSuccess = bleTinyFramePort.init { frameType, data, len ->
                            viewModelScope.launch {
                                Log.d(TAG, "TinyFrame解析到帧: type=0x${frameType.toString(16)}, len=$len")
                                _incomingFrames.emit(
                                    ProtocolFrame(
                                        type = frameType,
                                        payload = data.copyOf(len.coerceAtMost(data.size))
                                    )
                                )
                            }
                        }

                        if (initSuccess) {
                            val currentDevice = findConnectedDevice()
                            _uiState.update {
                                it.copy(
                                    connectionState = BleConnectionState.Connected,
                                    connectedDevice = currentDevice
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    connectionState = BleConnectionState.Error("端口初始化失败"),
                                    lastError = "端口初始化失败"
                                )
                            }
                        }
                    }

                    is BleManager.ConnectionState.DiscoveringServices -> {
                        _uiState.update { it.copy(connectionState = BleConnectionState.Connecting) }
                    }

                    is BleManager.ConnectionState.Error -> {
                        _uiState.update {
                            it.copy(
                                connectionState = BleConnectionState.Error(connectionState.message),
                                lastError = connectionState.message
                            )
                        }
                    }

                    is BleManager.ConnectionState.Disconnected -> {
                        _uiState.update {
                            it.copy(
                                connectionState = BleConnectionState.Idle,
                                connectedDevice = null
                            )
                        }
                    }

                    else -> Unit
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
        connectJob?.cancel()
        bleTinyFramePort.close()
        bleManager.close()
    }
}
