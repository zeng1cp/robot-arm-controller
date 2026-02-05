package tinyframe

/**
 * TinyFrame 工具类
 * 用于调试和测试的工具函数
 */
object TFUtils {

    // ANSI颜色代码
    private const val ANSI_RESET = "\u001B[0m"
    private const val ANSI_BLUE = "\u001B[94m"
    private const val ANSI_RED = "\u001B[31m"
    private const val ANSI_YELLOW = "\u001B[33m"

    /**
     * 将二进制帧转储为十六进制、十进制和ASCII格式
     * 类似C版本的dumpFrame函数
     *
     * @param buff 要转储的字节数组
     * @param len 要转储的长度（如果为-1则转储整个数组）
     */
    fun dumpFrame(buff: ByteArray, len: Int = -1) {
        val actualLen = if (len == -1) buff.size else minOf(len, buff.size)

        for (i in 0 until actualLen) {
            val byte = buff[i].toInt() and 0xFF

            // 十进制值
            print("%3d ".format(byte))

            // 十六进制值（蓝色）
            print(ANSI_BLUE)
            print("%02X".format(byte))
            print(ANSI_RESET)

            // ASCII字符或点号（红色）
            print(" ")
            if (byte >= 0x20 && byte < 127) {
                print(byte.toChar())
            } else {
                print(ANSI_RED)
                print(".")
                print(ANSI_RESET)
            }

            println()
        }
        println("--- end of frame ---\n")
    }

    /**
     * 转储消息元数据（不包括内容）
     * 类似C版本的dumpFrameInfo函数
     *
     * @param msg 要转储的消息
     */
    fun dumpFrameInfo(msg: TFMsg) {
        println(
            ANSI_YELLOW +
                    "Frame info\n" +
                    "  type: %02Xh\n".format(msg.type.toInt() and 0xFF) +
                    "   len: %d\n".format(msg.len.toInt()) +
                    "    id: %Xh".format(msg.frameId.toInt() and 0xFF)
        )

        if (msg.data != null && msg.len > 0u) {
            val dataStr = if (msg.data != null) {
                // 尝试将数据转换为字符串，如果不是可打印字符则显示十六进制
                try {
                    val text = String(msg.data!!, 0, msg.len.toInt())
                    if (text.all { it.code in 32..126 }) {
                        "\"$text\""
                    } else {
                        msg.data!!.joinToString("") { "%02X".format(it) }
                    }
                } catch (e: Exception) {
                    msg.data!!.joinToString("") { "%02X".format(it) }
                }
            } else {
                "(null)"
            }

            println("  data: $dataStr")
        }

        println(ANSI_RESET)
    }

    /**
     * 将字节数组转换为十六进制字符串
     *
     * @param bytes 字节数组
     * @param separator 分隔符（可选）
     * @return 十六进制字符串
     */
    fun bytesToHex(bytes: ByteArray, separator: String = " "): String {
        return bytes.joinToString(separator) { "%02X".format(it) }
    }

    /**
     * 从十六进制字符串解析字节数组
     *
     * @param hex 十六进制字符串（可包含空格）
     * @return 字节数组
     */
    fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.replace(" ", "").replace("\n", "").replace("\t", "")
        require(cleanHex.length % 2 == 0) { "Invalid hex string length" }

        return ByteArray(cleanHex.length / 2) { index ->
            val byteStr = cleanHex.substring(index * 2, index * 2 + 2)
            byteStr.toInt(16).toByte()
        }
    }

    /**
     * 创建测试帧数据
     *
     * @param type 消息类型
     * @param data 数据内容
     * @param id 帧ID（可选，自动生成）
     * @return 测试消息
     */
    fun createTestMessage(type: UInt, data: String, id: UInt = 0u): TFMsg {
        return TFMsg(
            frameId = id,
            type = type,
            data = data.toByteArray(),
            len = data.length.toUInt()
        )
    }

    /**
     * 模拟接收帧数据（用于测试）
     *
     * @param tf TinyFrame实例
     * @param type 消息类型
     * @param data 数据
     * @param id 帧ID
     */
    fun simulateReceiveFrame(tf: TinyFrame, type: UInt, data: String, id: UInt = 0u) {
        println("Simulating receive frame: type=0x${type.toString(16)}, data=\"$data\"")

        val testFrame = TinyFrame(TFPeer.SLAVE) { }
        val output = java.io.ByteArrayOutputStream()

        val frameBuilder = TinyFrame(TFPeer.SLAVE) { buffer ->
            output.write(buffer)
        }

        val msg = createTestMessage(type, data, id)
        frameBuilder.send(msg)

        val frameData = output.toByteArray()
        println("Generated frame data (${frameData.size} bytes):")
        dumpFrame(frameData)

        tf.accept(frameData)
    }
}