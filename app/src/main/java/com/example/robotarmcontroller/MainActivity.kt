package com.example.robotarmcontroller

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.bluetooth.BluetoothLe
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.example.robotarmcontroller.ui.RobotArmViewModel
import com.example.robotarmcontroller.ui.theme.RobotArmControllerTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.robotarmcontroller.ui.MainScreen
import com.example.robotarmcontroller.ui.MainViewModelFactory
import com.example.robotarmcontroller.ui.RobotArmViewModelFactory
import com.example.robotarmcontroller.ui.ServoState


class MainActivity : ComponentActivity() {
    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
        private const val PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 检查权限
        if (!hasRequiredPermissions()) {
            requestPermissions()
        }
        setContent {
            RobotArmControllerTheme {
//                Column(
//                    Modifier
//                        .fillMaxSize()
//                        .windowInsetsPadding(WindowInsets.systemBars)
//                ) {
//                    RobotArmControllerApp()
//                }

                // 创建自定义的 ViewModel Factory
                val viewModelFactory = MainViewModelFactory(this)

                // 设置 ViewModel 工厂
                CompositionLocalProvider(
                    LocalViewModelStoreOwner provides this
                ) {
                    // 使用自定义工厂创建 ViewModel
                    viewModel<com.example.robotarmcontroller.ui.ble.BleViewModel>(
                        factory = viewModelFactory
                    )
                    viewModel<com.example.robotarmcontroller.ui.robot.RobotViewModel>(
                        factory = viewModelFactory
                    )

                    MainScreen()
                }
            }
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            REQUIRED_PERMISSIONS,
            PERMISSION_REQUEST_CODE
        )
    }
}


@SuppressLint("MissingPermission")
@Composable
fun RobotArmControllerApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val viewModel: RobotArmViewModel = viewModel(
        factory = RobotArmViewModelFactory(context)
    )
    val uiState by viewModel.uiState.collectAsState()



    PWMSliderList(
        servoList = uiState.servoList,
        onPwmChange = viewModel::onPwmChange,
        onPwmChangeFinished = viewModel::onPwmChangeFinished
    )


    Button(onClick = { viewModel.bleWrite("aaa".toByteArray()) }) { Text("Send") }

    viewModel.bleScan()

    LazyColumn(modifier = modifier) {
        items(uiState.foundBleDeviceList) { device ->
            Card(onClick = {
                viewModel.bleConnect(device.device)
            }) {
                Text("${device.device.name} - ${device.deviceAddress.address} - ${device.rssi} - ${device.device.bondState}")
            }
        }
    }

}

@Composable
fun PWMSliderList(
    servoList: List<ServoState>,
    modifier: Modifier = Modifier,
    onPwmChange: (Int, Float) -> Unit,
    onPwmChangeFinished: (Int) -> Unit,
) {
    LazyColumn(modifier = modifier) {
        items(servoList) { servo ->
            PWMSlider(
                servo = servo,
                onPwmChange = { onPwmChange(servo.id, it) },
                onPwmChangeFinished = { onPwmChangeFinished(servo.id) },
            )
        }
    }
}

@Composable
fun PWMSlider(
    modifier: Modifier = Modifier,
    servo: ServoState,
    onPwmChange: (Float) -> Unit = {},
    onPwmChangeFinished: () -> Unit = {},
) {
    Card(modifier = modifier.padding(2.dp)) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp)
        ) {

            // -------------------------------------------
            // ⭐ Slider + 中间文字
            // -------------------------------------------
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Slider(
                    value = servo.pwm,
                    onValueChange = { onPwmChange(it) },
                    onValueChangeFinished = { onPwmChangeFinished() },
                    valueRange = 500f..2500f,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .height(35.dp)
                )
                Text(text = servo.name)
            }

            // -------------------------------------------
            // ⭐ 图标按钮 + 输入框 + 图标按钮
            // -------------------------------------------
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .padding(horizontal = 20.dp)
            ) {

                // 按钮：-100
                IconButton(onClick = {
                    onPwmChange(servo.pwm - 100)
                    onPwmChangeFinished()
                }) {
                    Icon(
                        painter = painterResource(R.drawable.keyboard_double_arrow_left_24px),
                        contentDescription = null
                    )
                }

                // 按钮：-10
                IconButton(onClick = {
                    onPwmChange(servo.pwm - 10)
                    onPwmChangeFinished()
                }) {
                    Icon(
                        painter = painterResource(R.drawable.keyboard_arrow_left_24px),
                        contentDescription = null
                    )
                }

                var text by remember { mutableStateOf(servo.pwm.toInt().toString()) }

                LaunchedEffect(servo.pwm) { // slider 或按钮变化 -> 更新输入框
                    text = servo.pwm.toInt().toString()
                }
                BasicTextField(
                    value = text,
                    singleLine = true,
                    onValueChange = {
                        text = it
                        val v = it.toFloatOrNull()
                        if (v != null) {
                            onPwmChange(v)
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        onPwmChangeFinished()
                    }),
                    textStyle = LocalTextStyle.current.copy(
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier
                        .width(70.dp)
                        .background(Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                )

                // 按钮：+10
                IconButton(onClick = {
                    onPwmChange(servo.pwm + 10)
                    onPwmChangeFinished()
                }) {
                    Icon(
                        painter = painterResource(R.drawable.keyboard_arrow_right_24px),
                        contentDescription = null
                    )
                }

                // 按钮：+100
                IconButton(onClick = {
                    onPwmChange(servo.pwm + 100)
                    onPwmChangeFinished()
                }) {
                    Icon(
                        painter = painterResource(R.drawable.keyboard_double_arrow_right_24px),
                        contentDescription = null
                    )
                }
            }
        }
    }
}



@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    RobotArmControllerTheme {
        RobotArmControllerApp()
    }
}