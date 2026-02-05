package com.example.robotarmcontroller.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.robotarmcontroller.BleManager
import com.example.robotarmcontroller.ui.ble.BleViewModel
import com.example.robotarmcontroller.ui.robot.RobotViewModel

class MainViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(BleViewModel::class.java) -> {
                val bleManager = BleManager(context)
                BleViewModel(bleManager) as T
            }
            modelClass.isAssignableFrom(RobotViewModel::class.java) -> {
                RobotViewModel() as T
            }
            else -> {
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
}