package com.auratechnologies.nowhere

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class DirectionSensor(context: Context) : SensorEventListener {

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

        SensorManager.getOrientation(
            rotationMatrix,
            orientation
        )

        var degrees = Math.toDegrees(
            orientation[0].toDouble()
        ).toFloat()

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
