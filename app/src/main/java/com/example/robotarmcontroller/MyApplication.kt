package com.example.robotarmcontroller

import android.app.Application
import android.util.Log
import com.example.robotarmcontroller.data.tinyframe.BleTinyFramePort
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MyApplication : Application() {
    @Inject
    lateinit var tinyFramePort: BleTinyFramePort

    override fun onCreate() {
        super.onCreate()
        
        // Hilt 注入后，可手动初始化并检查
        if (!tinyFramePort.initialize()) {
            Log.e("App", "TinyFrame 初始化失败: ${tinyFramePort.getInitError()}")
        }
    }
}
