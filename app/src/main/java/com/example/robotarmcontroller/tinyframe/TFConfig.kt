package tinyframe

/**
 * TinyFrame 配置参数
 * 用户应根据需要修改这些配置
 */
object TFConfig {
    // 字段长度配置
    const val TF_LEN_BYTES = 1
    const val TF_TYPE_BYTES = 1
    const val TF_ID_BYTES = 1

    // 校验和常量
    const val TF_CKSUM_NONE = 0
    const val TF_CKSUM_XOR = 8
    const val TF_CKSUM_CRC8 = 9
    const val TF_CKSUM_CRC16 = 16
    const val TF_CKSUM_CRC32 = 32
    const val TF_CKSUM_CUSTOM8 = 1
    const val TF_CKSUM_CUSTOM16 = 2
    const val TF_CKSUM_CUSTOM32 = 3

    // 校验和类型
    const val TF_CKSUM_TYPE = TF_CKSUM_NONE

    // 缓冲区大小
    const val TF_MAX_PAYLOAD_RX = 1024
    const val TF_SENDBUF_LEN = 128

    // 监听器数量限制
    const val TF_MAX_ID_LST = 8
    const val TF_MAX_TYPE_LST = 8
    const val TF_MAX_GEN_LST = 4

    // 解析器超时（单位：tick）
    const val TF_PARSER_TIMEOUT_TICKS = 100

    // SOF 字节配置
    const val TF_USE_SOF_BYTE = true
    const val TF_SOF_BYTE = 0x01.toByte()

    // 互斥锁配置
    const val TF_USE_MUTEX = false

    // 错误处理回调
    var errorCallback: ((String) -> Unit)? = null

    fun error(message: String) {
        errorCallback?.invoke(message)
        println("[TinyFrame Error] $message")
    }
}