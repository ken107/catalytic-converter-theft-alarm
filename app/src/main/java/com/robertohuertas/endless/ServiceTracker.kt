package com.robertohuertas.endless

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class ServiceConfig (
    val workStartHour: Int,
    val workDurationSec: Int,
    val detectionIntervalSec: Int,
    val tiltAngleThreshold: Float
)

private const val name = "SPYSERVICE_KEY"

fun setServiceConfig(context: Context, config: ServiceConfig) {
    val sharedPrefs = getPreferences(context)
    sharedPrefs.edit().let {
        it.putInt("workStartHour", config.workStartHour)
        it.putInt("workDurationSec", config.workDurationSec)
        it.putInt("detectionIntervalSec", config.detectionIntervalSec)
        it.putFloat("tiltAngleThreshold", config.tiltAngleThreshold)
        it.apply()
    }
}

fun getServiceConfig(context: Context): ServiceConfig? {
    val sharedPrefs = getPreferences(context)
    return sharedPrefs.let {
        if (it.contains("workStartHour") &&
            it.contains("workDurationSec") &&
            it.contains("detectionIntervalSec") &&
            it.contains("tiltAngleThreshold")) {
            ServiceConfig(
                it.getInt("workStartHour", 0),
                it.getInt("workDurationSec", 0),
                it.getInt("detectionIntervalSec", 0),
                it.getFloat("tiltAngleThreshold", 0f)
            )
        } else {
            null
        }
    }
}

private fun getPreferences(context: Context): SharedPreferences {
    return context.getSharedPreferences(name, 0)
}

fun scheduleService(context: Context) {
    val config = getServiceConfig(context) ?: return run {
        log(context, "Error: Can't schedule service, missing config")
    }
    val startTime = ZonedDateTime.now()
        .plusDays(1)
        .withHour(config.workStartHour)
        .withMinute(0)
        .withSecond(0)
        .withNano(0)
    val pendingIntent = PendingIntent.getForegroundService(
        context,
        1,
        Intent(context, EndlessService::class.java)
            .setAction(Actions.START.name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
    )
    (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        startTime.toInstant().toEpochMilli(),
        pendingIntent
    )
    log(context, "Next service scheduled for " + startTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
}
