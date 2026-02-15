package com.example.robotarmcontroller.common

import android.content.res.Resources
import androidx.annotation.StringRes

/**
 * 统一的应用程序错误类型。
 * 封装所有可能的错误源，并提供用户友好的消息。
 */
sealed class AppError(
    val message: String,
    val cause: Throwable? = null,
    val userMessage: String? = null
) {
    /** 蓝牙相关错误 */
    sealed class BluetoothError(message: String, cause: Throwable? = null, userMessage: String? = null) :
        AppError(message, cause, userMessage) {
        
        /** 蓝牙未启用 */
        object BluetoothDisabled : BluetoothError(
            message = "Bluetooth is disabled",
            userMessage = "蓝牙未启用，请打开蓝牙后重试"
        )
        
        /** 缺少权限 */
        data class MissingPermissions(val permissions: List<String>) : BluetoothError(
            message = "Missing permissions: ${permissions.joinToString()}",
            userMessage = "缺少必要的蓝牙权限，请授予权限后重试"
        )
        
        /** 扫描失败 */
        data class ScanFailed(val errorCause: Throwable?) : BluetoothError(
            message = "Bluetooth scan failed",
            cause = errorCause,
            userMessage = "设备扫描失败，请稍后重试"
        )
        
        /** 连接失败 */
        data class ConnectionFailed(val errorCause: Throwable?) : BluetoothError(
            message = "Bluetooth connection failed",
            cause = errorCause,
            userMessage = "设备连接失败，请检查设备是否可连接"
        )
        
        /** 连接断开 */
        object ConnectionLost : BluetoothError(
            message = "Bluetooth connection lost",
            userMessage = "设备连接已断开"
        )
        
        /** 服务发现失败 */
        data class ServiceDiscoveryFailed(val errorCause: Throwable?) : BluetoothError(
            message = "Service discovery failed",
            cause = errorCause,
            userMessage = "设备服务发现失败"
        )
        
        /** 特征值未找到 */
        data class CharacteristicNotFound(val type: String) : BluetoothError(
            message = "Characteristic not found: $type",
            userMessage = "未找到必要的设备通信特征值"
        )
        
        /** 写入失败 */
        data class WriteFailed(val errorCause: Throwable?) : BluetoothError(
            message = "Write operation failed",
            cause = errorCause,
            userMessage = "数据发送失败"
        )
        
        /** 读取失败 */
        data class ReadFailed(val errorCause: Throwable?) : BluetoothError(
            message = "Read operation failed",
            cause = errorCause,
            userMessage = "数据接收失败"
        )
    }
    
    /** 协议相关错误 */
    sealed class ProtocolError(message: String, cause: Throwable? = null, userMessage: String? = null) :
        AppError(message, cause, userMessage) {
        
        /** 帧解析失败 */
        data class FrameParseFailed(val frameType: String) : ProtocolError(
            message = "Failed to parse frame of type: $frameType",
            userMessage = "数据帧解析失败"
        )
        
        /** 无效的帧类型 */
        data class InvalidFrameType(val type: Int) : ProtocolError(
            message = "Invalid frame type: 0x${type.toString(16)}",
            userMessage = "无效的数据帧类型"
        )
        
        /** 校验和错误 */
        object ChecksumError : ProtocolError(
            message = "Frame checksum error",
            userMessage = "数据校验错误"
        )
        
        /** 协议版本不兼容 */
        data class VersionMismatch(val expected: Int, val actual: Int) : ProtocolError(
            message = "Protocol version mismatch: expected $expected, got $actual",
            userMessage = "协议版本不兼容"
        )
    }
    
    /** 网络/通信错误 */
    sealed class NetworkError(message: String, cause: Throwable? = null, userMessage: String? = null) :
        AppError(message, cause, userMessage) {
        
        /** 超时 */
        data class Timeout(val operation: String) : NetworkError(
            message = "Operation timed out: $operation",
            userMessage = "操作超时，请检查网络连接"
        )
        
        /** 无网络连接 */
        object NoConnection : NetworkError(
            message = "No network connection",
            userMessage = "无网络连接"
        )
    }
    
    /** 数据/存储错误 */
    sealed class DataError(message: String, cause: Throwable? = null, userMessage: String? = null) :
        AppError(message, cause, userMessage) {
        
        /** 数据验证失败 */
        data class ValidationFailed(val field: String) : DataError(
            message = "Data validation failed for field: $field",
            userMessage = "数据验证失败"
        )
        
        /** 存储失败 */
        data class StorageFailed(val errorCause: Throwable?) : DataError(
            message = "Storage operation failed",
            cause = errorCause,
            userMessage = "数据存储失败"
        )
        
        /** 读取失败 */
        data class LoadFailed(val errorCause: Throwable?) : DataError(
            message = "Load operation failed",
            cause = errorCause,
            userMessage = "数据加载失败"
        )
    }
    
    /** 未知/未分类错误 */
    data class UnknownError(val errorCause: Throwable?) : AppError(
        message = "Unknown error occurred",
        cause = errorCause,
        userMessage = "发生未知错误"
    )
    
    /** 从异常创建AppError的工厂方法 */
    companion object {
        fun fromException(exception: Throwable): AppError {
            return when (exception) {
                is java.net.SocketTimeoutException -> NetworkError.Timeout("Socket operation")
                is java.io.IOException -> NetworkError.NoConnection
                is java.lang.SecurityException -> BluetoothError.MissingPermissions(emptyList())
                else -> UnknownError(exception)
            }
        }
    }
}

/** 获取用户友好的错误消息 */
fun AppError.getUserMessage(resources: Resources, @StringRes defaultRes: Int = -1): String {
    return userMessage ?: if (defaultRes != -1) {
        resources.getString(defaultRes)
    } else {
        "发生错误：$message"
    }
}

/** 将AppError转换为字符串用于日志记录 */
fun AppError.toLogString(): String {
    return "${this::class.simpleName}: $message" + (cause?.let { " (cause: ${it.message})" } ?: "")
}