package com.auratechnologies.nowhere


import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import kotlin.math.sqrt

/**
 * 이동 상태(정지·도보·차량) 판별.
 *
 * 정지/도보/차량은 Google Activity Recognition API가 판단한다.
 * (예전에는 가속도·자이로 값과 GPS 속도로 직접 추정해서, 가만히 있어도
 *  GPS 위치가 흔들리면 '도보'로 나오는 문제가 있었다.)
 *
 * 엘리베이터/낙하는 Activity Recognition에 없으므로 기존처럼 기압계와
 * 가속도 센서로 감지하고, 잠시(SPECIAL_MODE_HOLD_MS) 동안만 우선 표시한다.
 */
class MovementDetector(context: Context) : SensorEventListener {

    private val appContext = context.applicationContext

    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    /** 자이로가 없는 기기에서는 엘리베이터 판정이 덜 정확하다(회전량을 알 수 없음). */
    val hasGyroscope: Boolean get() = gyroscope != null

    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val barometer = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

    private val activityClient: ActivityRecognitionClient = ActivityRecognition.getClient(appContext)
    private var activityPendingIntent: PendingIntent? = null
    private var receiverRegistered = false

    // 센서 데이터 저장 (엘리베이터/낙하 감지용)
    private var gyroData = FloatArray(3) // x, y, z 회전
    private var accelData = FloatArray(3) // x, y, z 가속도
    private var currentPressure = 0f
    private var lastPressure = 0f

    // 이동 수단 판별 결과
    enum class TransportMode {
        STATIONARY,    // 정지
        WALKING,       // 도보
        VEHICLE,       // 차량
        FALLING,       // 낙하
        ELEVATOR       // 엘리베이터
    }

    var currentMode = TransportMode.STATIONARY
        private set

    // Activity Recognition이 판단한 기본 상태 (정지/도보/차량)
    private var activityMode = TransportMode.STATIONARY

    // Activity Recognition 결과를 한 번이라도 받았는지.
    // 받은 적이 없을 때만(구글 플레이 서비스 없음, 권한 거부 등) GPS 속도로 대신 판단한다.
    private var hasActivityResult = false

    // 엘리베이터/낙하 감지 시 잠시 우선 표시
    private var specialMode: TransportMode? = null
    private var specialModeUntil = 0L

    private var onModeChanged: ((TransportMode) -> Unit)? = null

    private val activityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!ActivityRecognitionResult.hasResult(intent)) return
            val result = ActivityRecognitionResult.extractResult(intent) ?: return
            handleActivity(result.mostProbableActivity)
        }
    }

    fun startDetection(callback: (TransportMode) -> Unit) {
        onModeChanged = callback

        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        barometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        startActivityRecognition()
    }

    fun stopDetection() {
        sensorManager.unregisterListener(this)
        stopActivityRecognition()
    }

    // ---------------- Google Activity Recognition ----------------

    private fun hasActivityPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Android 9 이하는 설치 시 권한이 주어진다
        }
    }

    @SuppressLint("MissingPermission") // 권한은 hasActivityPermission()으로 확인한다
    private fun startActivityRecognition() {
        if (activityPendingIntent != null) return
        if (!hasActivityPermission()) {
            Log.w(TAG, "활동 인식 권한이 없어 GPS 속도로만 이동 상태를 판단합니다.")
            return
        }

        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                appContext,
                activityReceiver,
                IntentFilter(ACTION_ACTIVITY_UPDATE),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        }

        val intent = Intent(ACTION_ACTIVITY_UPDATE).setPackage(appContext.packageName)
        // 구글 플레이 서비스가 결과를 intent에 채워 넣으므로 MUTABLE 이어야 한다
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(appContext, REQUEST_CODE, intent, flags)

        try {
            activityClient.requestActivityUpdates(UPDATE_INTERVAL_MS, pendingIntent)
                .addOnSuccessListener { Log.d(TAG, "Activity Recognition 시작됨") }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Activity Recognition 시작 실패: ${e.message}")
                    activityPendingIntent = null
                }
            activityPendingIntent = pendingIntent
        } catch (e: SecurityException) {
            Log.w(TAG, "Activity Recognition 권한 오류: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopActivityRecognition() {
        activityPendingIntent?.let { pendingIntent ->
            try {
                activityClient.removeActivityUpdates(pendingIntent)
            } catch (e: SecurityException) {
                Log.w(TAG, "Activity Recognition 중지 오류: ${e.message}")
            }
            pendingIntent.cancel()
        }
        activityPendingIntent = null

        if (receiverRegistered) {
            try {
                appContext.unregisterReceiver(activityReceiver)
            } catch (e: IllegalArgumentException) {
                // 이미 해제됨
            }
            receiverRegistered = false
        }
    }

    private fun handleActivity(activity: DetectedActivity) {
        // 확신이 낮거나 판단할 수 없는 결과(기울임, 알 수 없음)는 무시하고 이전 상태를 유지한다
        if (activity.confidence < MIN_CONFIDENCE) return

        val mode = when (activity.type) {
            DetectedActivity.STILL -> TransportMode.STATIONARY
            DetectedActivity.ON_FOOT,
            DetectedActivity.WALKING,
            DetectedActivity.RUNNING -> TransportMode.WALKING
            DetectedActivity.IN_VEHICLE,
            DetectedActivity.ON_BICYCLE -> TransportMode.VEHICLE
            else -> return
        }

        hasActivityResult = true
        activityMode = mode
        Log.d(TAG, "Activity Recognition: type=${activity.type}, confidence=${activity.confidence} → $mode")
        publishMode()
    }

    // ---------------- 엘리베이터/낙하 (센서) ----------------

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                gyroData = event.values.clone()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                accelData = event.values.clone()
            }
            Sensor.TYPE_PRESSURE -> {
                // 첫 값은 비교 대상이 없으므로 변화량 0으로 시작
                lastPressure = if (currentPressure == 0f) event.values[0] else currentPressure
                currentPressure = event.values[0]
            }
        }

        analyzeSpecialMovement()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun analyzeSpecialMovement() {
        // 자이로스코프 회전 강도 계산
        val gyroMagnitude = sqrt(
            gyroData[0] * gyroData[0] +
                    gyroData[1] * gyroData[1] +
                    gyroData[2] * gyroData[2]
        )

        // 가속도 크기 계산
        val accelMagnitude = sqrt(
            accelData[0] * accelData[0] +
                    accelData[1] * accelData[1] +
                    accelData[2] * accelData[2]
        )

        // 기압 변화량 (고도 변화)
        val pressureChange = currentPressure - lastPressure
        val altitudeChange = calculateAltitudeChange(pressureChange)

        val detected = when {
            // 낙하 감지 (급격한 하강 + 높은 가속도)
            altitudeChange < -3 && accelMagnitude > 15 -> TransportMode.FALLING

            // 엘리베이터 감지 (급격한 고도 변화 + 낮은 자이로 회전)
            kotlin.math.abs(altitudeChange) > 2 && gyroMagnitude < 0.5 -> TransportMode.ELEVATOR

            else -> null
        }

        if (detected != null) {
            specialMode = detected
            specialModeUntil = SystemClock.elapsedRealtime() + SPECIAL_MODE_HOLD_MS
        }
        publishMode()
    }

    // 기압 → 고도 변화 계산 (간략 공식)
    private fun calculateAltitudeChange(pressureChange: Float): Float {
        // 1 hPa ≈ 8.3m 고도 변화
        return -pressureChange * 8.3f
    }

    // ---------------- GPS 속도 (Activity Recognition을 못 쓸 때만) ----------------

    fun updateSpeed(location: Location) {
        // Activity Recognition 결과가 있으면 그 판단을 그대로 쓴다.
        // (GPS 속도는 가만히 있어도 위치가 튀면서 몇 km/h로 나올 수 있다)
        if (hasActivityResult) return

        val speedKmh = location.speed * 3.6f // m/s → km/h
        val reliable = location.hasAccuracy() && location.accuracy <= FALLBACK_MAX_ACCURACY_M

        activityMode = when {
            !location.hasSpeed() || speedKmh < 2f -> TransportMode.STATIONARY
            !reliable -> activityMode // 정확도가 나쁘면 판단을 바꾸지 않는다
            speedKmh <= 8f -> TransportMode.WALKING
            speedKmh > 20f -> TransportMode.VEHICLE
            else -> activityMode
        }
        publishMode()
    }

    // ---------------- 최종 상태 결정 ----------------

    private fun publishMode() {
        val now = SystemClock.elapsedRealtime()
        val special = specialMode
        val mode = if (special != null && now < specialModeUntil) {
            special
        } else {
            specialMode = null
            activityMode
        }

        if (mode != currentMode) {
            currentMode = mode
            onModeChanged?.invoke(currentMode)
        }
    }

    companion object {
        private const val TAG = "NOWHERE"
        private const val ACTION_ACTIVITY_UPDATE =
            "com.auratechnologies.nowhere.ACTION_ACTIVITY_UPDATE"
        private const val REQUEST_CODE = 7301
        private const val UPDATE_INTERVAL_MS = 3000L
        private const val MIN_CONFIDENCE = 60
        private const val SPECIAL_MODE_HOLD_MS = 5000L
        private const val FALLBACK_MAX_ACCURACY_M = 20f
    }
}
