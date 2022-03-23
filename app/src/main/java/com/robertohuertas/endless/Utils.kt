package com.robertohuertas.endless

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.channels.Channel

fun log(context: Context, msg: String) {
    Log.d("ENDLESS-SERVICE", msg)
    context.sendBroadcast(
        Intent("com.robertohuertas.endless.LogStatus")
            .putExtra("message", msg)
    )
}

suspend fun readAccelerometer(sensorManager: SensorManager): FloatArray {
    val channel = Channel<FloatArray>()
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
        SensorManager.SENSOR_DELAY_NORMAL
    )
    try {
        return channel.receive()
    } finally {
        sensorManager.unregisterListener(listener)
    }
}
