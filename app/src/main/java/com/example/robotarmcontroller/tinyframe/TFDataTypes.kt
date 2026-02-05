package tinyframe

/**
 * 独立定义的类型别名
 */
typealias TFTicks = Long
typealias TFCount = Int

/**
 * 节点类型枚举
 */
enum class TFPeer {
    SLAVE,
    MASTER
}

/**
 * 监听器结果枚举
 */
enum class TFResult {
    NEXT,   // 未处理，让其他监听器处理
    STAY,   // 已处理，保持监听器
    RENEW,  // 已处理，保持并续订（仅对带超时的ID监听器有效）
    CLOSE   // 已处理，移除监听器
}

/**
 * 消息结构体
 */
data class TFMsg(
    var frameId: UInt = 0u,
    var isResponse: Boolean = false,
    var type: UInt = 0u,
    var data: ByteArray? = null,
    var len: UInt = 0u,
    var userdata: Any? = null,
    var userdata2: Any? = null
) {
    companion object {
        fun clear(msg: TFMsg) {
            msg.frameId = 0u
            msg.isResponse = false
            msg.type = 0u
            msg.data = null
            msg.len = 0u
            msg.userdata = null
            msg.userdata2 = null
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TFMsg

        if (frameId != other.frameId) return false
        if (isResponse != other.isResponse) return false
        if (type != other.type) return false
        if (len != other.len) return false
        if (userdata != other.userdata) return false
        if (userdata2 != other.userdata2) return false
        if (data != null) {
            if (other.data == null) return false
            if (!data.contentEquals(other.data)) return false
        } else if (other.data != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = frameId.hashCode()
        result = 31 * result + isResponse.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + (data?.contentHashCode() ?: 0)
        result = 31 * result + len.hashCode()
        result = 31 * result + (userdata?.hashCode() ?: 0)
        result = 31 * result + (userdata2?.hashCode() ?: 0)
        return result
    }
}

/**
 * 监听器回调类型
 */
typealias TFListener = (TinyFrame, TFMsg) -> TFResult
typealias TFListenerTimeout = (TinyFrame) -> Unit