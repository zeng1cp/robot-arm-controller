package com.example.robotarmcontroller.data.tinyframe

import android.annotation.SuppressLint
import android.util.Log
import com.example.robotarmcontroller.data.BleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tinyframe.TFMsg
import tinyframe.TFPeer
import tinyframe.TinyFrame
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BleTinyFramePort"

/**
 * TinyFrame 与 BLE 的桥接类，负责帧的封装、解析和收发。
 *
 * @param bleRepository 底层 BLE 仓库，用于发送原始数据和接收原始数据。
 */
@Singleton
class BleTinyFramePort @Inject constructor(
    private val bleRepository: BleRepository
) {
    private val scope = CoroutineScope(Dispatchers.Default)

    private var tinyFrame: TinyFrame? = null
    private var tickJob: Job? = null
    private var receiveJob: Job? = null
    private var connectionMonitorJob: Job? = null

    // 初始化状态标志
    private var isInitialized = false
    private var initError: String? = null

    // 用于向上层发送解析后的帧
    private val _incomingFrames = MutableSharedFlow<TFMsg>(extraBufferCapacity = 64)
    val incomingFrames: SharedFlow<TFMsg> = _incomingFrames.asSharedFlow()

    init {
        // 自动初始化，但失败时不影响实例创建
        initialize()
        // 启动连接状态监控
        startConnectionMonitoring()
    }

    /**
     * 显式初始化 TinyFrame。通常在应用启动时调用一次，可检查返回值。
     * 如果已经初始化成功，直接返回 true。
     */
    @SuppressLint("MissingPermission")
    fun initialize(): Boolean {
        // 如果已经初始化，先关闭旧资源
        if (isInitialized) {
            internalClose()
        }

        try {
            // 创建 TinyFrame 实例，指定发送函数
            tinyFrame = TinyFrame(TFPeer.MASTER) { data ->
                Log.d(TAG, "发送原始数据: ${data.size} 字节")
                bleRepository.write(data)
            }

            // 注册通用监听器，将所有收到的帧转发到 Flow
            tinyFrame?.addGenericListener { _, msg ->
                Log.d(TAG, "收到帧: type=0x${msg.type.toString(16)}, len=${msg.len}")
                scope.launch { _incomingFrames.emit(msg) }
                tinyframe.TFResult.STAY
            }

            // 启动接收协程
            startReceiving()
            // 启动 tick 任务
            startTickTask()

            isInitialized = true
            initError = null
            Log.d(TAG, "BleTinyFramePort 初始化成功")
            return true
        } catch (e: Exception) {
            initError = e.message
            Log.e(TAG, "初始化失败: ${e.message}")
            return false
        }
    }

    /**
     * 启动连接状态监控，在连接断开时关闭端口，连接成功时重新初始化
     */
    private fun startConnectionMonitoring() {
        connectionMonitorJob = scope.launch {
            bleRepository.connectionState.collectLatest { connectionState ->
                when (connectionState) {
                    is BleRepository.ConnectionState.Ready -> {
                        // 连接成功，确保端口已初始化
                        if (!isInitialized) {
                            Log.d(TAG, "检测到连接就绪，重新初始化端口")
                            initialize()
                        }
                    }
                    is BleRepository.ConnectionState.Disconnected,
                    is BleRepository.ConnectionState.Error,
                    is BleRepository.ConnectionState.Idle -> {
                        // 连接断开，关闭端口资源但保持实例存活
                        if (isInitialized) {
                            Log.d(TAG, "检测到连接断开，关闭端口资源")
                            internalClose()
                        }
                    }
                    else -> {
                        // 连接中、发现服务等状态，不做处理
                    }
                }
            }
        }
    }

    private fun startReceiving() {
        receiveJob = scope.launch {
            bleRepository.receivedData.collect { rawData ->
                if (rawData.isNotEmpty()) {
                    Log.v(TAG, "接收原始数据: ${rawData.size} 字节")
                    tinyFrame?.accept(rawData)
                }
            }
        }
    }

    private fun startTickTask() {
        tickJob = scope.launch {
            while (true) {
                kotlinx.coroutines.delay(1) // 每 1ms tick 一次
                tinyFrame?.tick()
            }
        }
    }

    /**
     * 发送一帧数据
     * @return true 发送成功，false 发送失败（未初始化或发送错误）
     */
    fun sendFrame(frameType: UInt, data: ByteArray): Boolean {
        if (!isInitialized) {
            Log.e(TAG, "未初始化，无法发送")
            return false
        }
        
        // 检查BLE连接状态，确保连接就绪
        val connectionState = bleRepository.connectionState.value
        if (connectionState !is BleRepository.ConnectionState.Ready) {
            Log.e(TAG, "BLE连接未就绪，当前状态: $connectionState")
            return false
        }
        
        val msg = TFMsg(type = frameType, data = data, len = data.size.toUInt())
        val success = tinyFrame?.send(msg) ?: false
        if (success) {
            Log.d(TAG, "发送帧: type=0x${frameType.toString(16)}, len=${data.size}")
        } else {
            Log.e(TAG, "发送帧失败，TinyFrame内部错误")
        }
        return success
    }

    /**
     * 当前初始化状态
     */
    fun isInitialized(): Boolean = isInitialized

    /**
     * 获取初始化错误信息（如果有）
     */
    fun getInitError(): String? = initError

    /**
     * 内部关闭方法，只关闭资源但不取消整个scope
     */
    private fun internalClose() {
        tickJob?.cancel()
        receiveJob?.cancel()
        tinyFrame = null
        isInitialized = false
        // 注意：这里不取消scope，因为connectionMonitorJob还在运行
        Log.d(TAG, "BleTinyFramePort 资源已关闭")
    }

    /**
     * 完全关闭资源，释放协程（包括连接监控）
     */
    fun close() {
        connectionMonitorJob?.cancel()
        internalClose()
        scope.cancel()
        Log.d(TAG, "BleTinyFramePort 已完全关闭")
    }
}