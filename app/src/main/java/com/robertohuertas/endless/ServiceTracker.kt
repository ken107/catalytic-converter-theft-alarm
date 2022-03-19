package com.robertohuertas.endless

import android.content.Context
import android.content.SharedPreferences

enum class ServiceState {
    STARTED,
    STOPPED,
}

data class ServiceConfig (
    val detectionIntervalSec: Int,
    val tiltAngleThreshold: Float,
    val notificationServerIp: String
)

private const val name = "SPYSERVICE_KEY"
private const val key = "SPYSERVICE_STATE"

fun setServiceState(context: Context, state: ServiceState) {
    val sharedPrefs = getPreferences(context)
    sharedPrefs.edit().let {
        it.putString(key, state.name)
        it.apply()
    }
}

fun getServiceState(context: Context): ServiceState {
    val sharedPrefs = getPreferences(context)
    val value = sharedPrefs.getString(key, ServiceState.STOPPED.name)
    return ServiceState.valueOf(value)
}

fun setServiceConfig(context: Context, config: ServiceConfig) {
    val sharedPrefs = getPreferences(context)
    sharedPrefs.edit().let {
        it.putInt("detectionIntervalSec", config.detectionIntervalSec)
        it.putFloat("tiltAngleThreshold", config.tiltAngleThreshold)
        it.putString("notificationServerIp", config.notificationServerIp)
        it.apply()
    }
}

fun getServiceConfig(context: Context): ServiceConfig? {
    val sharedPrefs = getPreferences(context)
    return sharedPrefs.let {
        if (it.contains("detectionIntervalSec") &&
            it.contains("tiltAngleThreshold") &&
            it.contains("notificationServerIp")) {
            ServiceConfig(
                it.getInt("detectionIntervalSec", 0),
                it.getFloat("tiltAngleThreshold", 0f),
                it.getString("notificationServerIp", "")
            )
        } else {
            null
        }
    }
}

private fun getPreferences(context: Context): SharedPreferences {
    return context.getSharedPreferences(name, 0)
}
