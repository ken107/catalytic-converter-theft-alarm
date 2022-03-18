package com.robertohuertas.endless

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.channels.Channel
import java.util.*

fun log(msg: String) {
    Log.d("ENDLESS-SERVICE", msg)
}

fun getAccelerometer(sensorManager: SensorManager) = Channel<FloatArray>(0).also { channel ->
    val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            channel.offer(event.values.copyOf())
        }
        override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        }
    }
    sensorManager.registerListener(
        listener,
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
        SensorManager.SENSOR_DELAY_GAME
    )
    channel.invokeOnClose {
        sensorManager.unregisterListener(listener)
    }
}

suspend fun <E> collectSamples(channel: Channel<E>, durationMs: Int) = LinkedList<E>().also {
    val since = System.currentTimeMillis()
    while (System.currentTimeMillis()-since < durationMs) {
        it.add(channel.receive())
    }
}

fun calcPeakDif(samples: LinkedList<FloatArray>) = intArrayOf(0, 1, 2)
    .map { index ->
        val max = samples.map { it[index] }.max() ?: 0f
        val min = samples.map { it[index] }.min() ?: 0f
        max - min
    }
    .max()
