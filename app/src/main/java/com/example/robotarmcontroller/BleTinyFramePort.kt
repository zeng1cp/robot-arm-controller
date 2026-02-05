package com.example.robotarmcontroller.tinyframe

import android.annotation.SuppressLint
import android.util.Log
import com.example.robotarmcontroller.BleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tinyframe.TFMsg
import tinyframe.TFPeer
import tinyframe.TinyFrame

/**
 * 帧接收回调函数类型 (对应 tf_uart_frame_callback_t)
 */
typealias BleFrameCallback = (frameType: UInt, data: ByteArray, len: Int) -> Unit

/**
 * 类型监听器回调（统一返回 STAY，监听器保持活跃）
 */
typealias FrameTypeListener = (frameType: UInt, data: ByteArray, len: Int) -> Unit

/**
 * BLE TinyFrame 端口
 */
class BleTinyFramePort(
    private val bleManager: BleManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    companion object {
        private const val TAG = "BleTinyFramePort"
    }

    // TinyFrame 实例
    private var tinyFrame: TinyFrame? = null

    // 用户回调函数
    private var userCallback: BleFrameCallback? = null

    // tick 任务
    private var tickJob: Job? = null

    // 数据接收任务（新增）
    private var dataProcessingJob: Job? = null

    // ==================== 公共函数实现 ====================

    @SuppressLint("MissingPermission")
    fun init(callback: BleFrameCallback? = null): Boolean {
        Log.d(TAG, "init BleTinyFramePort")

        userCallback = callback

        try {
            // 初始化 TinyFrame
            tinyFrame = TinyFrame(TFPeer.MASTER) { data ->
                Log.d(TAG, "TF_WriteImpl - 发送帧数据: ${data.size} 字节")
                // 通过 BLE 发送数据
                bleManager.write(data)
            }

            // 启动接收
            tinyFrame?.let {
                if (dataProcessingJob?.isActive != true) {
                    dataProcessingJob = scope.launch {
                        Log.d(TAG, "开启 BLE 接收")
                        bleManager.receivedData.collect { data ->
                            if (data.isNotEmpty()) {
                                Log.d(TAG, "接收到原始BLE数据: ${data.size} 字节")
                                try {
                                    // 将数据传递给TinyFrame解析
                                    tinyFrame?.accept(data)
                                    Log.v(TAG, "已处理数据: ${data.size} 字节")
                                } catch (e: Exception) {
                                    Log.e(TAG, "处理数据时出错: ${e.message}")
                                }
                            }
                        }
                    }
                }
            }

            // 注册 TinyFrame 通用监听器
            tinyFrame?.addGenericListener { tf, msg ->
                Log.d(TAG, "收到帧: type=0x${msg.type.toString(16)}, len=${msg.len}")

                // 调用用户回调（传递帧类型和帧数据）
                val data = msg.data ?: ByteArray(0)
                userCallback?.invoke(msg.type, data, msg.len.toInt())

                // 保持监听器
                tinyframe.TFResult.STAY
            }


            // 启动 tick 任务（每 1ms 调用一次）
            startTickTask()

            Log.d(TAG, "init BleTinyFramePort success")

            return true
        } catch (e: Exception) {
            Log.e(TAG, "init BleTinyFramePort failed: ${e.message}")
            return false
        }
    }

    /**
     * 发送一帧数据
     */
    fun sendFrame(frameType: UInt, data: ByteArray): Boolean {
        // 检查端口是否已初始化
        if (userCallback == null) {
            Log.e(TAG, "error: have not init port")
            return false
        }

        if (tinyFrame == null) {
            Log.e(TAG, "error: TinyFrame not initialized")
            return false
        }

        return try {
            // 构建 TinyFrame 消息
            val msg = TFMsg(
                type = frameType, data = data, len = data.size.toUInt()
            )

            // 发送帧
            val success = tinyFrame!!.send(msg)

            if (success) {
                Log.d(TAG, "send frame: type=0x${frameType.toString(16)}, len=${data.size}")
            } else {
                Log.d(TAG, "send failed")
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "send frame error: ${e.message}")
            false
        }
    }

    /**
     * 添加类型监听器（固定返回 STAY，监听器保持活跃）
     * @param frameType 要监听的帧类型
     * @param listener 监听器回调
     * @return 添加是否成功
     */
    fun addTypeListener(frameType: UInt, listener: FrameTypeListener): Boolean {
        if (tinyFrame == null) {
            Log.e(TAG, "error: TinyFrame not initialized")
            return false
        }

        return try {
            // 包装监听器，固定返回 STAY
            val wrappedListener: (tinyframe.TinyFrame, tinyframe.TFMsg) -> tinyframe.TFResult =
                { _, msg ->
                    listener(msg.type, msg.data ?: ByteArray(0), msg.len.toInt())
                    tinyframe.TFResult.STAY // 固定返回 STAY
                }

            tinyFrame!!.addTypeListener(frameType, wrappedListener)
        } catch (e: Exception) {
            Log.e(TAG, "addTypeListener error: ${e.message}")
            false
        }
    }

    /**
     * 移除类型监听器
     */
    fun removeTypeListener(frameType: UInt): Boolean {
        return tinyFrame?.removeTypeListener(frameType) ?: false
    }

    /**
     * tick(每1ms调用一次)
     */
    fun tick() {
        // 处理 TinyFrame 超时等
        tinyFrame?.tick()
    }

    /**
     * 检查发送是否完成
     */
    fun isTxDone(): Boolean {
        return true
    }

    /**
     * 清理资源
     */
    fun close() {
        tickJob?.cancel()
        dataProcessingJob?.cancel()
        tinyFrame = null
        userCallback = null
        Log.d(TAG, "BleTinyFramePort closed")
    }

    // ==================== 私有方法 ====================

    private fun startTickTask() {
        tickJob = scope.launch {
            while (true) {
                delay(1) // 每 1ms tick 一次
                tick()
            }
        }
    }
}
