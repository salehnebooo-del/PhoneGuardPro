package com.phoneguard.pro.data.diagnostics

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.phoneguard.pro.data.model.SensorItem
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class SensorInfoProvider(context: Context) {
    private val sm: SensorManager? = context.getSystemService(SensorManager::class.java)
    private val sensors: List<Sensor> by lazy { sm?.getSensorList(Sensor.TYPE_ALL) ?: emptyList() }

    fun list(): List<SensorItem> = sensors.mapIndexed { i, s ->
        SensorItem(
            index = i,
            name = s.name,
            vendor = s.vendor,
            typeName = prettyType(s),
            type = s.type,
            powerMa = s.power,
            maxRange = s.maximumRange,
            resolution = s.resolution,
            isWakeUp = s.isWakeUpSensor
        )
    }

    /** Live readings from a real hardware sensor. The listener is unregistered when collection stops. */
    fun readings(index: Int): Flow<FloatArray> = callbackFlow {
        val manager = sm
        val sensor = sensors.getOrNull(index)
        if (manager == null || sensor == null) {
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(event.values.clone())
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val ok = manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        if (!ok) {
            close()
            return@callbackFlow
        }
        awaitClose { manager.unregisterListener(listener) }
    }

    private fun prettyType(s: Sensor): String {
        val raw = s.stringType ?: return "Type ${s.type}"
        return raw.substringAfterLast('.')
            .replace('_', ' ')
            .replaceFirstChar { it.uppercase() }
    }
}
