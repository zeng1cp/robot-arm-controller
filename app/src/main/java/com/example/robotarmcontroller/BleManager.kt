package com.example.robotarmcontroller

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.bluetooth.BluetoothDevice
import androidx.bluetooth.BluetoothLe
import androidx.bluetooth.GattCharacteristic
import androidx.bluetooth.GattService
import androidx.bluetooth.ScanResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.collections.forEach
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "BleManager"


class BleManager(context: Context) {
    private val bluetoothLe = BluetoothLe(context)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var scanJob: Job? = null // 用于管理扫描的 Job

    // 状态流
    sealed class ConnectionState {
        object Idle : ConnectionState()
        object Connecting : ConnectionState()
        object DiscoveringServices : ConnectionState()
        data class Ready(
            val txCharacteristic: GattCharacteristic?,
            val rxCharacteristic: GattCharacteristic?
        ) : ConnectionState()

        data class Error(val message: String) : ConnectionState()
        object Disconnected : ConnectionState()
    }

    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults: StateFlow<List<ScanResult>> = _scanResults.asStateFlow()
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // 已连接的设备
    private var _connectedDevice: BluetoothDevice? = null
    val connectedDevice: BluetoothDevice? get() = _connectedDevice

    // 接收数据流
    private val _receivedData = MutableSharedFlow<ByteArray>()
    val receivedData: SharedFlow<ByteArray> = _receivedData.asSharedFlow()

    private var rxCharacteristic: GattCharacteristic? = null
    private var txCharacteristic: GattCharacteristic? = null
    private val writeChannel = Channel<ByteArray>(Channel.UNLIMITED)


    @RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() {
        if (scanJob?.isActive == true) {
            Log.d(TAG, "Scan is already active.")
            return
        }
        scanJob = scope.launch {
            bluetoothLe.scan().collect { scanResult ->
                Log.d(
                    TAG,
                    "发现BLE设备: 名称 - ${scanResult.device.name}, 地址 - ${scanResult.deviceAddress.address}, 信号强度 - ${scanResult.rssi}, uuids - ${scanResult.serviceUuids}"
                )
                _scanResults.update { oldList ->
                    val newList = oldList.toMutableList()
                    val existingIndex =
                        oldList.indexOfFirst { it.deviceAddress.address == scanResult.deviceAddress.address }
                    if (existingIndex != -1) {
                        newList[existingIndex] = scanResult // 更新信号强度等信息
                    } else {
                        newList.add(scanResult)
                    }
                    newList
                }
            }
        }
    }

    fun stopScan() {
        Log.d(TAG, "stopScan 被调用")
        scanJob?.cancel() // 取消扫描 Job
        scanJob = null
        _scanResults.value = emptyList() // 清空扫描结果
        Log.d(TAG, "BLE 扫描已停止")
    }
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun connect(device: BluetoothDevice) {
        try {
            // 清除之前的连接
            _connectedDevice = null

            _connectionState.value = ConnectionState.Connecting

            bluetoothLe.connectGatt(device) {
                _connectionState.value = ConnectionState.DiscoveringServices

                servicesFlow
                    .filter { it.isNotEmpty() }
                    .first()

                // 保存连接的设备
                _connectedDevice = device

                findUartCharacteristics(this.services)
                stopScan() // 连接成功后停止扫描

                coroutineScope {
                    // 接收协程
                    launch {
                        rxCharacteristic?.let {
                            subscribeToCharacteristic(it).collect { bytes ->
                                _receivedData.emit(bytes)
                                Log.d(TAG, "received: ${bytes.contentToString()}")
                            }
                        }
                    }
                    // 发送协程
                    launch {
                        txCharacteristic?.let {
                            for (data in writeChannel) {
                                writeCharacteristic(it, data)
                                Log.d(TAG,"send:  ${data.contentToString()}")
                            }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            Log.e(TAG, "BLE Connect Failed: ${e.message}")
            _connectionState.value = ConnectionState.Error(e.message ?: "连接失败")
            _connectedDevice = null
        } catch (e: Exception) {
            Log.e(TAG, "BLE Connect Failed: ${e.message}")
            _connectionState.value = ConnectionState.Error(e.message ?: "连接失败")
            _connectedDevice = null
        }
    }


    fun write(data: ByteArray) {
        writeChannel.trySend(data)
    }


    /**
     * 智能查找 UART 特征值的核心逻辑
     * 策略：基于属性识别，不依赖固定 UUID
     */
    private fun findUartCharacteristics(services: List<GattService>) {
        txCharacteristic = null
        rxCharacteristic = null

        services.forEach { service ->
            service.characteristics.forEach { characteristic ->
                val properties = characteristic.properties

                // 调试日志
                Log.d(
                    TAG,  // 改为统一的 TAG
                    "特征值: ${characteristic.uuid}, 属性: $properties (${properties.toInt()})"
                )

                // 1. 查找 TX 特征值（发送）
                if (txCharacteristic == null) {
                    val hasWrite = properties and GattCharacteristic.PROPERTY_WRITE != 0
                    val hasWriteNoResponse =
                        properties and GattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0

                    if ((hasWrite && hasWriteNoResponse) || hasWriteNoResponse) {
                        txCharacteristic = characteristic
                        Log.i(
                            TAG,
                            "找到 TX 特征值: ${characteristic.uuid}, 属性值: ${properties.toInt()}"
                        )
                    }
                }

                // 2. 查找 RX 特征值（接收）
                if (rxCharacteristic == null) {
                    val hasNotify = properties and GattCharacteristic.PROPERTY_NOTIFY != 0
                    val hasIndicate = properties and GattCharacteristic.PROPERTY_INDICATE != 0

                    // 检查通知或指示属性
                    if (hasNotify) {
                        rxCharacteristic = characteristic
                        Log.i(
                            TAG,
                            "找到 RX 特征值: ${characteristic.uuid}, 属性值: ${properties.toInt()}"
                        )
                    }
                }
            }
        }

        // 更新连接状态
        when {
            txCharacteristic == null && rxCharacteristic == null -> {
                _connectionState.value = ConnectionState.Error("未找到透传特征值")
                Log.e(TAG, "未找到任何透传特征值")
            }

            txCharacteristic == null -> {
                _connectionState.value = ConnectionState.Error("未找到 TX(发送)特征值")
                Log.e(TAG, "未找到 TX 特征值，但找到了 RX")
            }

            rxCharacteristic == null -> {
                _connectionState.value = ConnectionState.Error("未找到 RX(接收)特征值")
                Log.e(TAG, "未找到 RX 特征值，但找到了 TX")
            }

            else -> {
                _connectionState.value = ConnectionState.Ready(txCharacteristic, rxCharacteristic)
                Log.i(
                    TAG,
                    "UART 特征值准备就绪。TX: ${txCharacteristic?.uuid}, RX: ${rxCharacteristic?.uuid}"
                )
            }
        }
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        _connectedDevice = null
        _connectionState.value = ConnectionState.Disconnected
        // TODO: 如果有正在进行的写入操作，可能需要取消或清空 writeChannel
        Log.d(TAG, "BLE 已断开连接")
    }

    /**
     * 清理资源
     */
    fun close() {
        stopScan()
        disconnect()
        scope.cancel()
        Log.d(TAG, "BLE 管理器已关闭")
    }

}