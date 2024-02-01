package com.example.gyro_wear.presentation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.PowerManager
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.gyro_wear.presentation.theme.Gyro_wearTheme
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

class MainActivity : ComponentActivity(), SensorEventListener {
    private var dataSendJob: Job? = null
    private lateinit var sensorManager: SensorManager
    private lateinit var gyroSensor: Sensor
    private lateinit var dataClient: DataClient // 데이터 전송을 위한 DataClient

    private lateinit var powerManager: PowerManager
    private lateinit var wakeLock: PowerManager.WakeLock

    // 추가된 변수들
    private var gyroXValue by mutableStateOf(0f)
    private var gyroYValue by mutableStateOf(0f)
    private var gyroZValue by mutableStateOf(0f)

    // 데이터 전송 주기를 관리하기 위한 변수들
    private val sensorDataHandler = Handler()
    private var dataSendInterval = 100L // 기본 데이터 전송 주기: 100ms (10Hz)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        dataClient = Wearable.getDataClient(this) // DataClient 초기화

        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "gyrowear:gyroWakeLock")

        // 앱의 컨텐츠로 WearApp을 설정
        setContent {
            WearApp()
        }
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_NORMAL)
        wakeLock.acquire() // WakeLock을 활성화하여 절전 모드에서도 CPU를 활성 상태로 유지
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        wakeLock.release() // onPause에서 WakeLock 해제
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 자이로센서 정확도 변경 처리
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_GYROSCOPE) {
            val gyroX = event.values[0]
            val gyroY = event.values[1]
            val gyroZ = event.values[2]

            // UI 업데이트를 위해 변수에 값 설정
            gyroXValue = gyroX
            gyroYValue = gyroY
            gyroZValue = gyroZ
        }
    }

    private fun sendDataToAndroidApp() {
        // 데이터 맵에 자이로센서 데이터 추가
        val dataMap = DataMap()
        dataMap.putFloat("gyro_x", gyroXValue)
        dataMap.putFloat("gyro_y", gyroYValue)
        dataMap.putFloat("gyro_z", gyroZValue)

        // 안드로이드 앱으로 데이터 전송
        val request = PutDataMapRequest.create("/gyro_data")
        request.dataMap.putAll(dataMap)

        // 데이터 아이템을 업데이트하고 보냅니다.
        dataClient.putDataItem(request.asPutDataRequest())
    }

    private fun startDataSending() {
        // 이전에 시작된 작업이 있다면 취소
        stopDataSending()

        // 주기적으로 데이터를 전송하기 위한 Runnable 정의
        val dataSendingRunnable = object : Runnable {
            override fun run() {
                Log.d("AndroidWear", "$dataSendInterval")
                sendDataToAndroidApp()

                // 주기적으로 데이터 전송을 반복합니다.
                sensorDataHandler.postDelayed(this, dataSendInterval)
            }
        }

        // Runnable을 처음 실행합니다.
        sensorDataHandler.post(dataSendingRunnable)
    }

    private fun stopDataSending() {
        // 작업이 실행 중인 경우, Runnable을 제거하여 데이터 전송을 중지합니다.
        sensorDataHandler.removeCallbacksAndMessages(null)
    }

    // 주기 설정 함수
    private fun setSensorDataInterval(interval: Long) {
        dataSendInterval = interval
        // 데이터 전송 주기를 설정하고 데이터 전송을 시작합니다.
        startDataSending()
    }

    @Composable
    fun WearApp() {
        var selectedInterval by remember { mutableStateOf("Normal") }

        Gyro_wearTheme {
            // Use State to hold the sensor values
            var gyroXValue by remember { mutableStateOf(0f) }
            var gyroYValue by remember { mutableStateOf(0f) }
            var gyroZValue by remember { mutableStateOf(0f) }

            // Listen for sensor changes
            DisposableEffect(Unit) {
                // Register sensor listener and update gyro values
                val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
                val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent?) {
                        if (event?.sensor?.type == Sensor.TYPE_GYROSCOPE) {
                            val gyroX = event.values[0]
                            val gyroY = event.values[1]
                            val gyroZ = event.values[2]

                            // Update gyro values using mutableStateOf
                            gyroXValue = gyroX
                            gyroYValue = gyroY
                            gyroZValue = gyroZ
                        }
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                        // Handle accuracy changes if needed
                    }
                }

                sensorManager.registerListener(
                    listener,
                    gyroSensor,
                    SensorManager.SENSOR_DELAY_NORMAL
                )

                // Unregister sensor listener when the Composable is no longer active
                onDispose {
                    sensorManager.unregisterListener(listener)
                }
            }

            // UI
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary,
                        text = "Gyro X: $gyroXValue"
                    )
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary,
                        text = "Gyro Y: $gyroYValue"
                    )
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary,
                        text = "Gyro Z: $gyroZValue"
                    )

                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary,
                        text = "설정된 주기: $selectedInterval"
                    )

                    // "normal" 버튼을 누를 때 실시간으로 데이터를 전송
                    Button(
                        onClick = {
                            selectedInterval = "Normal"
                            setSensorDataInterval(1L)
                        },
                        enabled = true,
                        modifier = Modifier.padding(2.dp)
                    ) {
                        Text(text = "normal")
                    }

                    // "100Hz" 버튼
                    Button(
                        onClick = {
                            selectedInterval = "100Hz"
                            setSensorDataInterval(10L) // 10ms 주기로 설정
                        },
                        enabled = true,
                        modifier = Modifier.padding(2.dp)
                    ) {
                        Text(text = "100Hz")
                    }

                    // "16Hz" 버튼
                    Button(
                        onClick = {
                            selectedInterval = "16Hz"
                            setSensorDataInterval(62L) // 62ms 주기로 설정
                        },
                        enabled = true,
                        modifier = Modifier.padding(2.dp)
                    ) {
                        Text(text = "16Hz")
                    }
                }
            }
        }
    }

    @Preview(device = Devices.WEAR_OS_SMALL_ROUND, showSystemUi = true)
    @Composable
    fun DefaultPreview() {
        WearApp()
    }
}
