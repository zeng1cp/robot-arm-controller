package com.example.robotarmcontroller.data

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.bluetooth.BluetoothDevice
import androidx.bluetooth.BluetoothLe
import androidx.bluetooth.GattCharacteristic
import androidx.bluetooth.GattService
import androidx.bluetooth.ScanResult
import com.example.robotarmcontroller.common.AppError
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "BleRepository"

@Singleton
class BleRepository @Inject constructor(
    @field:ApplicationContext private val context: Context
) {
    private val bluetoothLe = BluetoothLe(context)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var scanJob: Job? = null // 用于管理扫描的 Job
    private var connectionJob: Job? = null // 用于管理连接的 Job
    private var connectionCompletionDeferred: CompletableDeferred<Unit>? = null // 用于控制连接的生命周期

    // 状态流
    sealed class ConnectionState {
        object Idle : ConnectionState()
        object Connecting : ConnectionState()
        object DiscoveringServices : ConnectionState()
        data class Ready(
            val txCharacteristic: GattCharacteristic?,
            val rxCharacteristic: GattCharacteristic?
        ) : ConnectionState()

        data class Error(val error: AppError) : ConnectionState()
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
    private var writeChannel: Channel<ByteArray> = Channel(Channel.UNLIMITED)

    /**
     * 重新创建写入通道，在连接成功或通道被取消后调用
     */
    private fun recreateWriteChannel() {
        // 如果通道已经关闭或取消，创建新的通道
        if (writeChannel.isClosedForSend || writeChannel.isClosedForReceive) {
            writeChannel = Channel(Channel.UNLIMITED)
            Log.d(TAG, "写入通道已重新创建")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
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
    fun connect(device: BluetoothDevice) {
        if (connectionJob?.isActive == true) {
            Log.d(TAG, "连接已在进行中或已连接。")
            return
        }

        connectionJob = scope.launch {
            connectionCompletionDeferred = CompletableDeferred() // 初始化 CompletableDeferred
            try {
                _connectedDevice = null
                _connectionState.value = ConnectionState.Connecting

                bluetoothLe.connectGatt(device) {
                    _connectionState.value = ConnectionState.DiscoveringServices

                    servicesFlow
                        .filter { it.isNotEmpty() }
                        .first()

                    _connectedDevice = device

                    findUartCharacteristics(this.services)
                    stopScan() // 连接成功后停止扫描
                    
                    // 重新创建写入通道，确保发送功能正常
                    recreateWriteChannel()

                    launch {
                        rxCharacteristic?.let {
                            subscribeToCharacteristic(it).collect { bytes ->
                                _receivedData.emit(bytes)
                                Log.d(TAG, "received: ${bytes.contentToString()}")
                            }
                        }
                    }
                    launch {
                        txCharacteristic?.let {
                            for (data in writeChannel) {
                                writeCharacteristic(it, data)
                                Log.d(TAG, "send:  ${data.contentToString()}")
                            }
                        }
                    }
                    
                    connectionCompletionDeferred?.await() // 挂起 lambda，直到 deferred 完成
                }
            } catch (e: CancellationException) {
                Log.e(TAG, "BLE Connect Cancelled: ${e.message}")
                _connectionState.value = ConnectionState.Error(AppError.BluetoothError.ConnectionLost)
                _connectedDevice = null
            } catch (e: Exception) {
                Log.e(TAG, "BLE Connect Failed: ${e.message}")
                _connectionState.value = ConnectionState.Error(AppError.BluetoothError.ConnectionFailed(e))
                _connectedDevice = null
            } finally {
                // 确保 deferred 在连接结束时完成，以解除 await()
                connectionCompletionDeferred?.complete(Unit)

                if (_connectionState.value !is ConnectionState.Error) {
                    _connectionState.value = ConnectionState.Disconnected
                }
                _connectedDevice = null
                rxCharacteristic = null
                txCharacteristic = null
                writeChannel.cancel() // 在连接 Job 结束时取消 writeChannel
                Log.d(TAG, "连接Job结束，写入通道已清空")
            }
        }
    }
    
    fun write(data: ByteArray): Boolean {
        // 检查连接状态，只有在Ready状态才能发送
        val currentState = _connectionState.value
        if (currentState !is ConnectionState.Ready) {
            Log.e(TAG, "连接未就绪，当前状态: $currentState")
            return false
        }

        // 检查通道是否可发送，如果关闭但连接正常，尝试恢复
        if (writeChannel.isClosedForSend) {
            Log.w(TAG, "写入通道已关闭，但连接状态正常，尝试恢复")
            recreateWriteChannel()
            
            // 再次检查
            if (writeChannel.isClosedForSend) {
                Log.e(TAG, "恢复写入通道失败")
                return false
            }
        }

        val result = writeChannel.trySend(data).isSuccess
        if (!result) {
            Log.e(TAG, "发送数据失败，通道可能已满或已关闭")
        }
        return result
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

                Log.d(
                    TAG,
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
                _connectionState.value = ConnectionState.Error(AppError.BluetoothError.CharacteristicNotFound("UART"))
                Log.e(TAG, "未找到任何透传特征值")
            }

            txCharacteristic == null -> {
                _connectionState.value = ConnectionState.Error(AppError.BluetoothError.CharacteristicNotFound("TX"))
                Log.e(TAG, "未找到 TX 特征值，但找到了 RX")
            }

            rxCharacteristic == null -> {
                _connectionState.value = ConnectionState.Error(AppError.BluetoothError.CharacteristicNotFound("RX"))
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
        connectionCompletionDeferred?.complete(Unit) // 完成 deferred，解除 connectGatt lambda 的挂起
        connectionJob?.cancel() // 取消正在进行的连接 Job
        connectionJob = null
        _connectedDevice = null
        _connectionState.value = ConnectionState.Disconnected
        writeChannel.cancel() // 清空并关闭写入通道
        Log.d(TAG, "BLE 已断开连接，连接 Job 已取消")
    }


    /**
     * 清理资源
     */
    fun close() {
        stopScan()
        disconnect()
        scope.cancel() // 取消 BleManager 自身的 CoroutineScope
        Log.d(TAG, "BLE 管理器已关闭")
    }

}