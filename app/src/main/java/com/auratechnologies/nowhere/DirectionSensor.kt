package com.auratechnologies.nowhere

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.atan2

class DirectionSensor(context: Context) : SensorEventListener {

    companion object {
        // 카메라 방향의 수직 성분이 이보다 크면(약 53° 이상 눕힘) 평평하게 든 것으로 본다.
        private const val FLAT_THRESHOLD = 0.8f
    }

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val rotationSensor =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    @Volatile
    var azimuth: Float = 0f
        private set

    @Volatile
    var accuracy: Int = SensorManager.SENSOR_STATUS_UNRELIABLE
        private set

    @Volatile
    var hasReading: Boolean = false
        private set

    fun start() {
        rotationSensor?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_UI
            )
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)

        SensorManager.getRotationMatrixFromVector(
            rotationMatrix,
            event.values
        )

        // 회전 행렬 R: 기기 좌표 -> 세계 좌표(동, 북, 위).
        // 후면 카메라는 기기의 -Z 방향을 본다. 그 방향을 수평면에 내려 방위각을 구하면
        // 세로로 들든 가로로 들든 '카메라가 향한 방향'이 그대로 나온다.
        val cameraEast = -rotationMatrix[2]
        val cameraNorth = -rotationMatrix[5]
        val cameraUp = -rotationMatrix[8]

        var degrees = if (abs(cameraUp) < FLAT_THRESHOLD) {
            Math.toDegrees(
                atan2(cameraEast.toDouble(), cameraNorth.toDouble())
            ).toFloat()
        } else {
            // 폰을 거의 눕혀 들었을 때는 카메라가 땅(또는 하늘)을 보므로
            // 기존처럼 기기 위쪽이 가리키는 방향을 쓴다.
            SensorManager.getOrientation(
                rotationMatrix,
                orientation
            )
            Math.toDegrees(
                orientation[0].toDouble()
            ).toFloat()
        }

        if (degrees < 0f) {
            degrees += 360f
        }

        azimuth = degrees
        hasReading = true
    }

    override fun onAccuracyChanged(sensor: Sensor?, newAccuracy: Int) {
        if (sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            accuracy = newAccuracy
        }
    }

    fun getDirectionName(): String {
        return when {
            azimuth >= 337.5f || azimuth < 22.5f -> "북"
            azimuth < 67.5f -> "북동"
            azimuth < 112.5f -> "동"
            azimuth < 157.5f -> "남동"
            azimuth < 202.5f -> "남"
            azimuth < 247.5f -> "남서"
            azimuth < 292.5f -> "서"
            else -> "북서"
        }
    }
}
