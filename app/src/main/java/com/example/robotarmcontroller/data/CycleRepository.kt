package com.example.robotarmcontroller.data

import android.util.Log
import com.example.robotarmcontroller.data.model.CycleInfo
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

private const val TAG = "CycleRepository"

/**
 * Cycle（循环运动）业务仓库，负责Cycle协议解析、状态维护和指令发送。
 */
@Singleton
class CycleRepository @Inject constructor(
    private val tinyFramePort: BleTinyFramePort
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Cycle列表
    private val _cycleList = MutableStateFlow<List<CycleInfo>>(emptyList())
    val cycleList: StateFlow<List<CycleInfo>> = _cycleList.asStateFlow()

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
            ProtocolCommand.State.CYCLE -> handleCycleState(cmdFrame.payload)
            else -> Unit // 忽略其他STATE命令
        }
    }

    private fun handleCycleState(payload: ByteArray) {
        if (payload.isEmpty()) return
        val subcmd = payload[0].toInt() and 0xFF
        when (subcmd) {
            ProtocolCommand.Cycle.LIST.toInt() -> parseCycleList(payload)
            ProtocolCommand.Cycle.CREATE.toInt() -> {
                if (payload.size >= 5) {
                    val cycleIndex = toIntLe(payload, 1)
                    Log.i(TAG, "Cycle创建: index=$cycleIndex")
                }
            }

            ProtocolCommand.Cycle.GET_STATUS.toInt() -> parseCycleStatus(payload)
            ProtocolCommand.Cycle.STATUS.toInt() -> parseCycleCallback(payload)
            else -> Unit // 忽略其他Cycle子命令
        }
    }

    // ========== Cycle 解析 ==========

    private fun parseCycleList(payload: ByteArray) {
        // payload[0] 是 subcmd
        if (payload.size < 2) return
        val data = payload.copyOfRange(1, payload.size)
        if (data.isEmpty()) return
        val count = data[0].toInt() and 0xFF

        val compactEntryLen = 17
        val expandedEntryLen = 21
        val list = mutableListOf<CycleInfo>()

        when {
            data.size == 1 + count * compactEntryLen -> {
                var off = 1
                repeat(count) {
                    val index = data[off].toInt() and 0xFF
                    val active = data[off + 1].toInt() and 0xFF
                    val running = data[off + 2].toInt() and 0xFF
                    val currentPose = data[off + 3].toInt() and 0xFF
                    val poseCount = data[off + 4].toInt() and 0xFF
                    val loopCount = toIntLe(data, off + 5)
                    val maxLoops = toIntLe(data, off + 9)
                    val activeGroupId = toIntLe(data, off + 13)
                    list.add(
                        CycleInfo(
                            index,
                            active != 0,
                            running != 0,
                            currentPose,
                            poseCount,
                            loopCount,
                            maxLoops,
                            activeGroupId
                        )
                    )
                    off += compactEntryLen
                }
            }

            data.size == 1 + count * expandedEntryLen -> {
                var off = 1
                repeat(count) {
                    val index = toIntLe(data, off)
                    val active = data[off + 4].toInt() and 0xFF
                    val running = data[off + 5].toInt() and 0xFF
                    val currentPose = data[off + 6].toInt() and 0xFF
                    val poseCount = data[off + 7].toInt() and 0xFF
                    val loopCount = toIntLe(data, off + 8)
                    val maxLoops = toIntLe(data, off + 12)
                    val activeGroupId = toIntLe(data, off + 16)
                    list.add(
                        CycleInfo(
                            index,
                            active != 0,
                            running != 0,
                            currentPose,
                            poseCount,
                            loopCount,
                            maxLoops,
                            activeGroupId
                        )
                    )
                    off += expandedEntryLen
                }
            }

            else -> {
                // 启发式回退
                if ((data.size - 1) % compactEntryLen == 0) {
                    var off = 1
                    while (off + compactEntryLen <= data.size) {
                        val index = data[off].toInt() and 0xFF
                        val active = data[off + 1].toInt() and 0xFF
                        val running = data[off + 2].toInt() and 0xFF
                        val currentPose = data[off + 3].toInt() and 0xFF
                        val poseCount = data[off + 4].toInt() and 0xFF
                        val loopCount = toIntLe(data, off + 5)
                        val maxLoops = toIntLe(data, off + 9)
                        val activeGroupId = toIntLe(data, off + 13)
                        list.add(
                            CycleInfo(
                                index,
                                active != 0,
                                running != 0,
                                currentPose,
                                poseCount,
                                loopCount,
                                maxLoops,
                                activeGroupId
                            )
                        )
                        off += compactEntryLen
                    }
                }
            }
        }

        _cycleList.value = list.sortedBy { it.index }
        Log.i(TAG, "Cycle列表更新: ${list.size} 个")

    }

    private fun parseCycleStatus(payload: ByteArray) {
        if (payload.size >= 21) {
            val cycleIndex = toIntLe(payload, 1)
            val active = payload[5].toInt() and 0xFF
            val running = payload[6].toInt() and 0xFF
            val currentPose = payload[7].toInt() and 0xFF
            val poseCount = payload[8].toInt() and 0xFF
            val loopCount = toIntLe(payload, 9)
            val maxLoops = toIntLe(payload, 13)
            val activeGroupId = toIntLe(payload, 17)
            Log.i(
                TAG,
                "Cycle状态: idx=$cycleIndex active=$active running=$running pose=$currentPose/$poseCount loops=$loopCount/$maxLoops group=$activeGroupId"
            )

            // 更新 Cycle 列表中的对应条目
            _cycleList.value = _cycleList.value.map { cycle ->
                if (cycle.index == cycleIndex) {
                    CycleInfo(
                        index = cycleIndex,
                        active = active != 0,
                        running = running != 0,
                        currentPose = currentPose,
                        poseCount = poseCount,
                        loopCount = loopCount,
                        maxLoops = maxLoops,
                        activeGroupId = activeGroupId
                    )
                } else {
                    cycle
                }
            }
        }
    }

    private fun parseCycleCallback(payload: ByteArray) {
        if (payload.size >= 14) {
            val cycleIndex = toIntLe(payload, 1)
            val loopCount = toIntLe(payload, 5)
            val remaining = toIntLe(payload, 9)
            val finished = payload[13].toInt() and 0xFF
            Log.i(
                TAG,
                "Cycle回调: idx=$cycleIndex loop=$loopCount remaining=$remaining finished=$finished"
            )

            // 更新对应 Cycle 的循环计数和运行状态
            _cycleList.value = _cycleList.value.map { cycle ->
                if (cycle.index == cycleIndex) {
                    CycleInfo(
                        index = cycleIndex,
                        active = cycle.active,
                        running = if (finished != 0) false else cycle.running,
                        currentPose = cycle.currentPose,
                        poseCount = cycle.poseCount,
                        loopCount = loopCount,
                        maxLoops = cycle.maxLoops,
                        activeGroupId = cycle.activeGroupId
                    )
                } else {
                    cycle
                }
            }
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

    fun createCycle(
        mode: Int,
        ids: List<Int>,
        poses: List<List<Number>>,
        durations: List<Int>,
        maxLoops: Int
    ) {
        if (ids.isEmpty() || poses.isEmpty() || poses.size != durations.size) return
        val data = CycleProtocolCodec.encodeCreate(mode, ids, poses, durations, maxLoops)
        tinyFramePort.sendFrame(ProtocolFrameType.CYCLE, data)
    }

    fun startCycle(index: Int) {
        val data = CycleProtocolCodec.encodeStart(index)
        tinyFramePort.sendFrame(ProtocolFrameType.CYCLE, data)
    }

    fun restartCycle(index: Int) {
        val data = CycleProtocolCodec.encodeRestart(index)
        tinyFramePort.sendFrame(ProtocolFrameType.CYCLE, data)
    }

    fun pauseCycle(index: Int) {
        val data = CycleProtocolCodec.encodePause(index)
        tinyFramePort.sendFrame(ProtocolFrameType.CYCLE, data)
    }

    fun releaseCycle(index: Int) {
        val data = CycleProtocolCodec.encodeRelease(index)
        tinyFramePort.sendFrame(ProtocolFrameType.CYCLE, data)
    }

    fun requestCycleStatus(index: Int) {
        val data = CycleProtocolCodec.encodeGetStatus(index)
        tinyFramePort.sendFrame(ProtocolFrameType.CYCLE, data)
    }

    fun requestCycleList() {
        val data = CycleProtocolCodec.encodeList()
        tinyFramePort.sendFrame(ProtocolFrameType.CYCLE, data)
    }

    fun close() {
        scope.cancel()
    }
}