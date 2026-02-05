package tinyframe
import java.util.concurrent.locks.ReentrantLock

/**
 * TinyFrame 主类
 */
class TinyFrame(
    private val peerBit: TFPeer,
    private val writeImpl: (ByteArray) -> Unit
) {
    // 用户数据
    var userdata: Any? = null
    var usertag: Long = 0

    // 内部状态
    private var nextId: UInt = 0u
    private var state = TFState.SOF
    private var parserTimeoutTicks: TFTicks = 0
    private var id: UInt = 0u
    private var len: UInt = 0u
    private var type: UInt = 0u
    private var cksum: ULong = 0uL
    private var refCksum: ULong = 0uL
    private var discardData = false
    private var rxi: UInt = 0u

    // 接收缓冲区
    private val dataBuffer = ByteArray(TFConfig.TF_MAX_PAYLOAD_RX)

    // 发送状态
    private val sendBuffer = ByteArray(TFConfig.TF_SENDBUF_LEN)
    private var txPos = 0
    private var txLen = 0
    private var txCksum: ULong = 0uL
    private var softLock = false

    // 监听器
    private val idListeners = mutableListOf<IdListener>()
    private val typeListeners = mutableListOf<TypeListener>()
    private val genericListeners = mutableListOf<TFListener>()

    // 互斥锁
    private val lock = ReentrantLock()

    // ------------------------ 初始化方法 ------------------------

    fun resetParser() {
        state = TFState.SOF
        parserTimeoutTicks = 0
    }

    // ------------------------ 数据接收方法 ------------------------

    fun accept(buffer: ByteArray) {
        buffer.forEach { acceptByte(it.toUByte()) }
    }

    fun acceptChar(c: UByte) {
        acceptByte(c)
    }

    private fun acceptByte(c: UByte) {
        // 解析器超时处理
        if (parserTimeoutTicks >= TFConfig.TF_PARSER_TIMEOUT_TICKS) {
            if (state != TFState.SOF) {
                resetParser()
                TFConfig.error("Parser timeout")
            }
        }
        parserTimeoutTicks = 0

        when (state) {
            TFState.SOF -> {
                if (TFConfig.TF_USE_SOF_BYTE && c == TFConfig.TF_SOF_BYTE.toUByte()) {
                    beginFrame()
                } else if (!TFConfig.TF_USE_SOF_BYTE) {
                    beginFrame()
                }
            }

            TFState.ID -> {
                cksum = TFChecksum.add(cksum, c)
                id = (id shl 8) or c.toUInt()
                rxi++
                if (rxi == TFConfig.TF_ID_BYTES.toUInt()) {
                    state = TFState.LEN
                    rxi = 0u
                }
            }

            TFState.LEN -> {
                cksum = TFChecksum.add(cksum, c)
                len = (len shl 8) or c.toUInt()
                rxi++
                if (rxi == TFConfig.TF_LEN_BYTES.toUInt()) {
                    state = TFState.TYPE
                    rxi = 0u
                }
            }

            TFState.TYPE -> {
                cksum = TFChecksum.add(cksum, c)
                type = (type shl 8) or c.toUInt()
                rxi++
                if (rxi == TFConfig.TF_TYPE_BYTES.toUInt()) {
                    if (TFConfig.TF_CKSUM_TYPE == TFConfig.TF_CKSUM_NONE) {
                        state = TFState.DATA
                        rxi = 0u
                    } else {
                        state = TFState.HEAD_CKSUM
                        rxi = 0u
                        refCksum = 0uL
                    }
                }
            }

            TFState.HEAD_CKSUM -> {
                refCksum = (refCksum shl 8) or c.toULong()
                rxi++
                if (rxi == getChecksumSize().toUInt()) {
                    val finalCksum = TFChecksum.end(cksum)
                    if (finalCksum != refCksum) {
                        TFConfig.error("Rx head checksum mismatch")
                        resetParser()
                        return
                    }

                    if (len == 0u) {
                        handleReceivedMessage()
                        resetParser()
                    } else {
                        state = TFState.DATA
                        rxi = 0u
                        cksum = TFChecksum.start()

                        if (len > TFConfig.TF_MAX_PAYLOAD_RX.toUInt()) {
                            TFConfig.error("Rx payload too long: $len")
                            discardData = true
                        }
                    }
                }
            }

            TFState.DATA -> {
                if (!discardData) {
                    cksum = TFChecksum.add(cksum, c)
                    dataBuffer[rxi.toInt()] = c.toByte()
                }
                rxi++

                if (rxi == len) {
                    if (TFConfig.TF_CKSUM_TYPE == TFConfig.TF_CKSUM_NONE) {
                        println("rxi == len, handleReceivedMessage")
                        handleReceivedMessage()
                        resetParser()
                    } else {
                        state = TFState.DATA_CKSUM
                        rxi = 0u
                        refCksum = 0uL
                    }
                }
            }

            TFState.DATA_CKSUM -> {
                refCksum = (refCksum shl 8) or c.toULong()
                rxi++
                if (rxi == getChecksumSize().toUInt()) {
                    val finalCksum = TFChecksum.end(cksum)
                    if (!discardData) {
                        if (finalCksum == refCksum) {
                            handleReceivedMessage()
                        } else {
                            TFConfig.error("Body checksum mismatch")
                        }
                    }
                    resetParser()
                }
            }
        }
    }

    private fun beginFrame() {
        cksum = TFChecksum.start()
        if (TFConfig.TF_USE_SOF_BYTE) {
            cksum = TFChecksum.add(cksum, TFConfig.TF_SOF_BYTE.toUByte())
        }
        discardData = false
        state = TFState.ID
        rxi = 0u
        id = 0u
        len = 0u
        type = 0u
    }

    private fun getChecksumSize(): Int {
        return when (TFConfig.TF_CKSUM_TYPE) {
            TFConfig.TF_CKSUM_NONE -> 0
            TFConfig.TF_CKSUM_XOR,
            TFConfig.TF_CKSUM_CRC8,
            TFConfig.TF_CKSUM_CUSTOM8 -> 1
            TFConfig.TF_CKSUM_CRC16,
            TFConfig.TF_CKSUM_CUSTOM16 -> 2
            TFConfig.TF_CKSUM_CRC32,
            TFConfig.TF_CKSUM_CUSTOM32 -> 4
            else -> 0
        }
    }

    // ------------------------ 消息处理方法 ------------------------

    private fun handleReceivedMessage() {
        val msg = TFMsg(
            frameId = id,
            type = type,
            data = dataBuffer.copyOf(len.toInt()),
            len = len
        )

        // 首先处理ID监听器
        val iterator = idListeners.iterator()
        while (iterator.hasNext()) {
            val listener = iterator.next()
            if (listener.id == msg.frameId) {
                msg.userdata = listener.userdata
                msg.userdata2 = listener.userdata2

                val result = listener.fn(this, msg)
                listener.userdata = msg.userdata
                listener.userdata2 = msg.userdata2

                when (result) {
                    TFResult.NEXT -> {} // 继续处理
                    TFResult.STAY -> return
                    TFResult.RENEW -> {
                        listener.timeout = listener.timeoutMax
                        return
                    }
                    TFResult.CLOSE -> {
                        iterator.remove()
                        return
                    }
                }
            }
        }

        // 清理用户数据，防止泄漏到类型监听器
        msg.userdata = null
        msg.userdata2 = null

        // 处理类型监听器
        val typeIterator = typeListeners.iterator()
        while (typeIterator.hasNext()) {
            val listener = typeIterator.next()
            if (listener.type == msg.type) {
                val result = listener.fn(this, msg)
                when (result) {
                    TFResult.NEXT -> {} // 继续处理
                    TFResult.STAY,
                    TFResult.RENEW -> return
                    TFResult.CLOSE -> {
                        typeIterator.remove()
                        return
                    }
                }
            }
        }

        // 处理通用监听器
        val genericIterator = genericListeners.iterator()
        while (genericIterator.hasNext()) {
            val listener = genericIterator.next()
            val result = listener(this, msg)
            when (result) {
                TFResult.NEXT -> {} // 继续处理
                TFResult.STAY,
                TFResult.RENEW -> return
                TFResult.CLOSE -> {
                    genericIterator.remove()
                    return
                }
            }
        }

        TFConfig.error("Unhandled message, type ${msg.type}")
    }

    // ------------------------ 监听器管理 ------------------------

    fun addIdListener(
        msg: TFMsg,
        cb: TFListener,
        ftimeout: TFListenerTimeout? = null,
        timeout: TFTicks = 0
    ): Boolean {
        if (idListeners.size >= TFConfig.TF_MAX_ID_LST) {
            TFConfig.error("Failed to add ID listener: max limit reached")
            return false
        }

        idListeners.add(IdListener(
            id = msg.frameId,
            fn = cb,
            fnTimeout = ftimeout,
            timeout = timeout,
            timeoutMax = timeout,
            userdata = msg.userdata,
            userdata2 = msg.userdata2
        ))
        return true
    }

    fun addTypeListener(frameType: UInt, cb: TFListener): Boolean {
        if (typeListeners.size >= TFConfig.TF_MAX_TYPE_LST) {
            TFConfig.error("Failed to add type listener: max limit reached")
            return false
        }

        typeListeners.add(TypeListener(type = frameType, fn = cb))
        return true
    }

    fun addGenericListener(cb: TFListener): Boolean {
        if (genericListeners.size >= TFConfig.TF_MAX_GEN_LST) {
            TFConfig.error("Failed to add generic listener: max limit reached")
            return false
        }

        genericListeners.add(cb)
        return true
    }

    fun removeIdListener(frameId: UInt): Boolean {
        return idListeners.removeIf { it.id == frameId }
    }

    fun removeTypeListener(type: UInt): Boolean {
        return typeListeners.removeIf { it.type == type }
    }

    fun removeGenericListener(cb: TFListener): Boolean {
        return genericListeners.removeIf { it == cb }
    }

    fun renewIdListener(id: UInt): Boolean {
        val listener = idListeners.find { it.id == id }
        return if (listener != null) {
            listener.timeout = listener.timeoutMax
            true
        } else {
            false
        }
    }

    // ------------------------ 发送方法 ------------------------

    fun send(msg: TFMsg): Boolean {
        return sendFrame(msg, null, null, 0)
    }

    fun sendSimple(type: UInt, data: ByteArray? = null): Boolean {
        val msg = TFMsg(
            type = type,
            data = data,
            len = data?.size?.toUInt() ?: 0u
        )
        return send(msg)
    }

    fun query(msg: TFMsg, listener: TFListener, ftimeout: TFListenerTimeout? = null, timeout: TFTicks = 0): Boolean {
        return sendFrame(msg, listener, ftimeout, timeout)
    }

    fun querySimple(type: UInt, data: ByteArray?, listener: TFListener, ftimeout: TFListenerTimeout? = null, timeout: TFTicks = 0): Boolean {
        val msg = TFMsg(
            type = type,
            data = data,
            len = data?.size?.toUInt() ?: 0u
        )
        return query(msg, listener, ftimeout, timeout)
    }

    fun respond(msg: TFMsg): Boolean {
        msg.isResponse = true
        return send(msg)
    }

    // ------------------------ 多部分发送方法 ------------------------

    fun sendMultipart(msg: TFMsg): Boolean {
        msg.data = null
        return sendFrameBegin(msg, null, null, 0)
    }

    fun sendSimpleMultipart(type: UInt, len: UInt): Boolean {
        val msg = TFMsg(type = type, len = len)
        return sendMultipart(msg)
    }

    fun queryMultipart(msg: TFMsg, listener: TFListener, ftimeout: TFListenerTimeout? = null, timeout: TFTicks = 0): Boolean {
        msg.data = null
        return sendFrameBegin(msg, listener, ftimeout, timeout)
    }

    fun querySimpleMultipart(type: UInt, len: UInt, listener: TFListener, ftimeout: TFListenerTimeout? = null, timeout: TFTicks = 0): Boolean {
        val msg = TFMsg(type = type, len = len)
        return queryMultipart(msg, listener, ftimeout, timeout)
    }

    fun respondMultipart(msg: TFMsg) {
        msg.data = null
        respond(msg)
    }

    fun multipartPayload(buff: ByteArray) {
        sendFrameChunk(buff)
    }

    fun multipartClose() {
        sendFrameEnd()
    }

    // ------------------------ 内部发送方法 ------------------------

    private fun sendFrame(msg: TFMsg, listener: TFListener?, ftimeout: TFListenerTimeout?, timeout: TFTicks): Boolean {
        return try {
            if (!claimTx()) {
                TFConfig.error("Failed to claim TX")
                return false
            }

            // 先生成头部并获取ID
            composeHead(msg)

            // 添加监听器（需要正确的ID）
            if (listener != null) {
                if (!addIdListener(msg, listener, ftimeout, timeout)) {
                    releaseTx()
                    return false
                }
            }

            // 如果有数据，发送完整帧
            if (msg.len > 0u && msg.data != null) {
                txCksum = TFChecksum.start()
                sendFrameChunk(msg.data!!)
                sendFrameEnd()
            } else {
                // 没有数据，直接发送头部
                if (txPos > 0) {
                    writeImpl(sendBuffer.copyOf(txPos))
                    txPos = 0
                }
                releaseTx()
            }

            true
        } catch (e: Exception) {
            TFConfig.error("Send frame error: ${e.message}")
            releaseTx()
            false
        }
    }

    private fun sendFrameBegin(msg: TFMsg, listener: TFListener?, ftimeout: TFListenerTimeout?, timeout: TFTicks): Boolean {
        return try {
            if (!claimTx()) {
                TFConfig.error("Failed to claim TX")
                return false
            }

            // 生成头部
            composeHead(msg)

            // 添加监听器
            if (listener != null) {
                if (!addIdListener(msg, listener, ftimeout, timeout)) {
                    releaseTx()
                    return false
                }
            }

            // 重置校验和，准备接收数据
            txCksum = TFChecksum.start()
            true
        } catch (e: Exception) {
            TFConfig.error("Send frame begin error: ${e.message}")
            releaseTx()
            false
        }
    }

    private fun sendFrameChunk(buff: ByteArray) {
        lock.lock()
        try {
            var remaining = buff.size
            var offset = 0

            while (remaining > 0) {
                val chunkSize = minOf(TFConfig.TF_SENDBUF_LEN - txPos, remaining)

                // 将数据复制到发送缓冲区并更新校验和
                for (i in 0 until chunkSize) {
                    val byte = buff[offset + i]
                    sendBuffer[txPos++] = byte
                    txCksum = TFChecksum.add(txCksum, byte.toUByte())
                }

                remaining -= chunkSize
                offset += chunkSize

                // 如果缓冲区满了，立即发送
                if (txPos == TFConfig.TF_SENDBUF_LEN) {
                    writeImpl(sendBuffer.copyOf(txPos))
                    txPos = 0
                }
            }
        } finally {
            lock.unlock()
        }
    }

    private fun sendFrameEnd() {
        lock.lock()
        try {
            // 如果有数据，添加校验和
            if (txLen > 0) {
                // 确保校验和有足够空间
                if (TFConfig.TF_SENDBUF_LEN - txPos < getChecksumSize()) {
                    writeImpl(sendBuffer.copyOf(txPos))
                    txPos = 0
                }

                // 添加校验和
                val finalCksum = TFChecksum.end(txCksum)
                writeNumber(finalCksum, getChecksumSize())
            }

            // 发送剩余数据
            if (txPos > 0) {
                writeImpl(sendBuffer.copyOf(txPos))
                txPos = 0
            }
        } finally {
            lock.unlock()
            releaseTx()
        }
    }

    private fun composeHead(msg: TFMsg) {
        txPos = 0
        txLen = msg.len.toInt()

        // 生成ID掩码
        val idMask = ((1u shl (TFConfig.TF_ID_BYTES * 8 - 1)) - 1u)
        val peerBitMask = (1u shl (TFConfig.TF_ID_BYTES * 8 - 1))

        // 生成ID
        val id = if (msg.isResponse) {
            msg.frameId
        } else {
            val newId = nextId++ and idMask
            if (peerBit == TFPeer.MASTER) {
                newId or peerBitMask
            } else {
                newId
            }
        }
        msg.frameId = id

        // 重置校验和
        var cksum = TFChecksum.start()

        // SOF字节
        if (TFConfig.TF_USE_SOF_BYTE) {
            sendBuffer[txPos++] = TFConfig.TF_SOF_BYTE
            cksum = TFChecksum.add(cksum, TFConfig.TF_SOF_BYTE.toUByte())
        }

        // ID
        cksum = writeNumberWithChecksum(id, TFConfig.TF_ID_BYTES, cksum)

        // 长度
        cksum = writeNumberWithChecksum(msg.len, TFConfig.TF_LEN_BYTES, cksum)

        // 类型
        cksum = writeNumberWithChecksum(msg.type, TFConfig.TF_TYPE_BYTES, cksum)

        // 头部校验和
        if (TFConfig.TF_CKSUM_TYPE != TFConfig.TF_CKSUM_NONE) {
            val finalCksum = TFChecksum.end(cksum)
            writeNumber(finalCksum, getChecksumSize())
        }
    }

    private fun writeNumber(value: ULong, bytes: Int) {
        for (i in bytes - 1 downTo 0) {
            val byte = ((value shr (i * 8)) and 0xFFuL).toByte()
            sendBuffer[txPos++] = byte
        }
    }

    private fun writeNumberWithChecksum(value: UInt, bytes: Int, cksum: ULong): ULong {
        var currentCksum = cksum
        for (i in bytes - 1 downTo 0) {
            val byte = ((value.toULong() shr (i * 8)) and 0xFFuL).toByte()
            sendBuffer[txPos++] = byte
            currentCksum = TFChecksum.add(currentCksum, byte.toUByte())
        }
        return currentCksum
    }

    // ------------------------ 互斥锁方法 ------------------------

    private fun claimTx(): Boolean {
        return if (TFConfig.TF_USE_MUTEX) {
            lock.tryLock()
        } else {
            if (softLock) {
                TFConfig.error("TF already locked for TX")
                false
            } else {
                softLock = true
                true
            }
        }
    }

    private fun releaseTx() {
        if (TFConfig.TF_USE_MUTEX) {
            if (lock.isHeldByCurrentThread) {
                lock.unlock()
            }
        } else {
            softLock = false
        }
    }

    // ------------------------ Tick方法 ------------------------

    fun tick() {
        // 解析器超时
        if (parserTimeoutTicks < TFConfig.TF_PARSER_TIMEOUT_TICKS) {
            parserTimeoutTicks++
        }

        // ID监听器超时处理
        val idIterator = idListeners.iterator()
        while (idIterator.hasNext()) {
            val listener = idIterator.next()
            if (listener.timeout > 0) {
                listener.timeout--
                if (listener.timeout == 0L) {
                    TFConfig.error("ID listener ${listener.id} has expired")
                    listener.fnTimeout?.invoke(this)
                    idIterator.remove()
                }
            }
        }
    }
}