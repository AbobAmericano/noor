package com.example.sensors

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class CompassReading(
    val azimuthDegrees: Float = 0f,
    val hasCompassSensor: Boolean = true,
    val accuracyLabel: String = "Calibrated"
)

class QiblaSensorManager(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    fun observeAzimuth(latitude: Double, longitude: Double): Flow<CompassReading> = callbackFlow {
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (rotationSensor == null && (accelSensor == null || magnetSensor == null)) {
            trySend(CompassReading(azimuthDegrees = 0f, hasCompassSensor = false, accuracyLabel = "No Compass Hardware"))
            awaitClose { }
            return@callbackFlow
        }

        val geomagneticField = GeomagneticField(
            latitude.toFloat(),
            longitude.toFloat(),
            0f,
            System.currentTimeMillis()
        )
        val declination = geomagneticField.declination

        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var hasGravity = false
        var hasMagnet = false
        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)

        var smoothedSin = 0.0
        var smoothedCos = 1.0
        var initialized = false
        var currentAccuracy = "Active"

        fun emitHeading(rawMagneticDeg: Float) {
            val trueDeg = ((rawMagneticDeg + declination) % 360f + 360f) % 360f
            val rad = Math.toRadians(trueDeg.toDouble())
            val alpha = 0.16
            if (!initialized) {
                smoothedSin = sin(rad)
                smoothedCos = cos(rad)
                initialized = true
            } else {
                smoothedSin = smoothedSin * (1.0 - alpha) + sin(rad) * alpha
                smoothedCos = smoothedCos * (1.0 - alpha) + cos(rad) * alpha
            }
            val smoothedDeg = ((Math.toDegrees(atan2(smoothedSin, smoothedCos)).toFloat()) % 360f + 360f) % 360f
            trySend(
                CompassReading(
                    azimuthDegrees = smoothedDeg,
                    hasCompassSensor = true,
                    accuracyLabel = currentAccuracy
                )
            )
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, orientation)
                        val deg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                        emitHeading(deg)
                    }

                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, gravity, 0, 3)
                        hasGravity = true
                        if (hasMagnet && SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                            SensorManager.getOrientation(rotationMatrix, orientation)
                            emitHeading(Math.toDegrees(orientation[0].toDouble()).toFloat())
                        }
                    }

                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                        hasMagnet = true
                        if (hasGravity && SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                            SensorManager.getOrientation(rotationMatrix, orientation)
                            emitHeading(Math.toDegrees(orientation[0].toDouble()).toFloat())
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                currentAccuracy = when (accuracy) {
                    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "High Accuracy"
                    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Medium Accuracy"
                    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low • Move in figure-8"
                    else -> "Calibrating"
                }
            }
        }

        if (rotationSensor != null) {
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            sensorManager.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(listener, magnetSensor, SensorManager.SENSOR_DELAY_UI)
        }

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
}
