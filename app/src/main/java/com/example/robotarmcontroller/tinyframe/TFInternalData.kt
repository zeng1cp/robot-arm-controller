package tinyframe

/**
 * 内部使用的数据结构，不对外暴露
 */
internal enum class TFState {
    SOF,
    ID,
    LEN,
    TYPE,
    HEAD_CKSUM,
    DATA,
    DATA_CKSUM
}

internal data class IdListener(
    var id: UInt,
    var fn: TFListener,
    var fnTimeout: TFListenerTimeout? = null,
    var timeout: TFTicks = 0,
    var timeoutMax: TFTicks = 0,
    var userdata: Any? = null,
    var userdata2: Any? = null
)

internal data class TypeListener(
    var type: UInt,
    var fn: TFListener
)