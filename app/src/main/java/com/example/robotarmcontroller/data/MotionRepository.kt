package com.example.robotarmcontroller.data

import android.util.Log
import com.example.robotarmcontroller.protocol.*
import com.example.robotarmcontroller.data.tinyframe.BleTinyFramePort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MotionRepository"

/**
 * 运动控制业务仓库，负责运动协议解析、状态维护和指令发送。
 */
@Singleton
class MotionRepository @Inject constructor(
    private val tinyFramePort: BleTinyFramePort
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // 运动组ID
    private val _motionGroupId = MutableStateFlow(0)
    val motionGroupId: StateFlow<Int> = _motionGroupId.asStateFlow()

    // 已完成的运动组ID
    private val _motionCompleteGroupId = MutableStateFlow(0)
    val motionCompleteGroupId: StateFlow<Int> = _motionCompleteGroupId.asStateFlow()

    init {
        scope.launch {
            tinyFramePort.incomingFrames.collect { msg ->
                val frame = ProtocolFrame(type = msg.type, payload = msg.data ?: ByteArray(0))
                when (frame.type) {
                    ProtocolFrameType.STATE -> handleStateFrame(frame)
                    else -> Unit // 忽略其他帧类型
                }
            }
        }
    }

    // ========== 帧处理 ==========

    private fun handleStateFrame(frame: ProtocolFrame) {
        val cmdFrame = frame.parseCommandFrame() ?: return
        when (cmdFrame.cmd) {
            ProtocolCommand.State.MOTION -> handleMotionState(cmdFrame.payload)
            else -> Unit // 忽略其他STATE命令
        }
    }

    private fun handleMotionState(payload: ByteArray) {
        if (payload.isEmpty()) return
        val subcmd = payload[0].toInt() and 0xFF
        when (subcmd) {
            ProtocolCommand.Motion.START.toInt(),
            ProtocolCommand.Motion.GET_STATUS.toInt() -> {
                if (payload.size >= 5) {
                    val gid = toIntLe(payload, 1)
                    _motionGroupId.value = gid
                    Log.i(TAG, "Motion状态: subcmd=0x${subcmd.toString(16)} group=$gid")
                }
            }
            ProtocolCommand.Motion.STATUS.toInt() -> {
                if (payload.size >= 6) {
                    val gid = toIntLe(payload, 1)
                    val complete = payload[5].toInt() and 0xFF
                    if (complete != 0) {
                        _motionCompleteGroupId.value = gid
                    }
                    Log.i(TAG, "Motion完成: group=$gid complete=$complete")
                }
            }
            else -> Log.d(TAG, "未知Motion子命令: 0x${subcmd.toString(16)}")
        }
    }


    // ========== 工具函数 ==========

    private fun toIntLe(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    // ========== 对外指令发送 ==========

    fun startMotion(mode: Int, ids: List<Int>, values: List<Number>, durationMs: Int) {
        if (ids.isEmpty() || values.isEmpty() || ids.size != values.size) return
        val data = MotionProtocolCodec.encodeStart(mode, ids, values, durationMs)
        tinyFramePort.sendFrame(ProtocolFrameType.MOTION, data)
    }

    fun stopMotion(groupId: Int) {
        val data = MotionProtocolCodec.encodeStop(groupId)
        tinyFramePort.sendFrame(ProtocolFrameType.MOTION, data)
    }

    fun pauseMotion(groupId: Int) {
        val data = MotionProtocolCodec.encodePause(groupId)
        tinyFramePort.sendFrame(ProtocolFrameType.MOTION, data)
    }

    fun resumeMotion(groupId: Int) {
        val data = MotionProtocolCodec.encodeResume(groupId)
        tinyFramePort.sendFrame(ProtocolFrameType.MOTION, data)
    }

    fun requestMotionStatus(groupId: Int) {
        val data = MotionProtocolCodec.encodeGetStatus(groupId)
        tinyFramePort.sendFrame(ProtocolFrameType.MOTION, data)
    }


    fun close() {
        scope.cancel()
    }
}