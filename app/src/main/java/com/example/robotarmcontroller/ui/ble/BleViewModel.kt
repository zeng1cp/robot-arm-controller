package com.example.robotarmcontroller.ui.ble

import android.annotation.SuppressLint
import android.util.Log
import androidx.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.robotarmcontroller.BleManager
import com.example.robotarmcontroller.tinyframe.BleFrameCallback
import com.example.robotarmcontroller.tinyframe.BleTinyFramePort
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private const val TAG = "BleViewModel"

class BleViewModel(
    private val bleManager: BleManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(BleUiState())
    val uiState: StateFlow<BleUiState> = _uiState.asStateFlow()

    // 初始化 BLE TinyFrame 端口
    private var bleTinyFramePort: BleTinyFramePort = BleTinyFramePort(bleManager, viewModelScope)

    // 协程任务
    private var scanJob: Job? = null
    private var scanResultCollectJob: Job? = null
    private var connectJob: Job? = null
    private var dataReceiveJob: Job? = null  // 新增：数据接收任务

    // 帧接收共享流（用于向其他ViewModel发送数据）
    private val _frameData = MutableSharedFlow<ByteArray>()
    val frameData: SharedFlow<ByteArray> = _frameData.asSharedFlow()

    init {
        // 监听连接状态
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
                    _uiState.update { currentState ->
                        currentState.copy(foundDevices = scanResults)
                    }
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
        // 停止数据接收
//        stopDataReceiving()

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

    /**
     * 查找已连接的设备
     */
    private fun findConnectedDevice(): androidx.bluetooth.ScanResult? {
        val connectedDevice = bleManager.connectedDevice

        if (connectedDevice != null) {
            return _uiState.value.foundDevices.firstOrNull {
                it.device.name == connectedDevice.name
            }
        }

        return null
    }

    /**
     * 启动数据接收任务
     */
//    private fun startDataReceiving() {
//        // 如果已经有接收任务在运行，先停止
//        stopDataReceiving()
//
//        dataReceiveJob = viewModelScope.launch {
//            Log.d(TAG, "开始接收BLE数据...")
//
//            bleManager.receivedData.collect { data ->
//                if (data.isNotEmpty()) {
//                    Log.d(TAG, "接收到原始BLE数据: ${data.size} 字节")
//
//                    // 1. 将数据转发给TinyFrame端口处理
//                    try {
//                        bleTinyFramePort.acceptData(data)
//                    } catch (e: Exception) {
//                        Log.e(TAG, "TinyFrame处理数据失败: ${e.message}")
//                    }
//
//                    // 2. 同时将数据发送到共享流（供其他ViewModel使用）
//                    _frameData.emit(data)
//
//                    // 3. 可选：直接处理某些特定数据
//                    handleRawBleData(data)
//                }
//            }
//        }
//    }

    /**
     * 停止数据接收任务
     */
//    private fun stopDataReceiving() {
//        dataReceiveJob?.cancel()
//        dataReceiveJob = null
//        Log.d(TAG, "停止接收BLE数据")
//    }

    /**
     * 处理原始BLE数据（可选的直接处理）
     */
    private fun handleRawBleData(data: ByteArray) {
        // 这里可以添加对原始数据的直接处理逻辑
        // 例如：打印调试信息、处理特定格式数据等
        // 简单验证：打印接收到的数据
        Log.d(TAG, "收到BLE数据包: ${data.size} 字节")
        if (data.size > 0) {
            // 示例：将字节转换为十六进制字符串
            val hexString = data.joinToString("") { "%02X".format(it) }
            Log.v(TAG, "原始数据(HEX): $hexString")
        }
    }

    /**
     * 发送帧数据
     */
    fun sendFrame(frameType: UInt, data: ByteArray): Boolean {
        return bleTinyFramePort.sendFrame(frameType, data)
    }

    private fun monitorConnectionState() {
        viewModelScope.launch {
            bleManager.connectionState.collect { connectionState ->
                when (connectionState) {
                    is BleManager.ConnectionState.Ready -> {
                        // 连接成功，初始化 TinyFrame 端口
                        val initSuccess = bleTinyFramePort.init { frameType, data, len ->
                            // TinyFrame解析后的帧数据回调
                            viewModelScope.launch {
                                Log.d(TAG, "TinyFrame解析到帧: type=0x${frameType.toString(16)}, len=$len")
                                // 这里可以进一步处理解析后的帧
                            }
                        }

                        if (initSuccess) {
                            // 获取设备信息
                            val currentDevice = findConnectedDevice()
                            _uiState.update {
                                it.copy(
                                    connectionState = BleConnectionState.Connected,
                                    connectedDevice = currentDevice
                                )
                            }

                            // 重要：连接成功后启动数据接收
//                            startDataReceiving()

                            Log.d(TAG, "连接成功，设备: ${currentDevice?.device?.name ?: "未知"}")
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
                        _uiState.update {
                            it.copy(
                                connectionState = BleConnectionState.Connecting
                            )
                        }
                    }
                    is BleManager.ConnectionState.Error -> {
                        // 发生错误时停止数据接收
//                        stopDataReceiving()

                        _uiState.update {
                            it.copy(
                                connectionState = BleConnectionState.Error(connectionState.message),
                                lastError = connectionState.message
                            )
                        }
                    }
                    is BleManager.ConnectionState.Disconnected -> {
                        // 断开连接时停止数据接收
//                        stopDataReceiving()

                        _uiState.update {
                            it.copy(
                                connectionState = BleConnectionState.Idle,
                                connectedDevice = null
                            )
                        }
                    }
                    else -> {
                        // 其他状态保持原样
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
        connectJob?.cancel()
//        stopDataReceiving()  // 确保停止数据接收
        bleTinyFramePort.close()
        bleManager.close()
    }
}