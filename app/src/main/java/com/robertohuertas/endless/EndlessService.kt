package com.robertohuertas.endless

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.PowerManager
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.sqrt
import kotlin.math.withSign

class EndlessService : Service() {

    private val wakeLock by lazy {
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EndlessService::lock")
    }
    private var serviceExpire: Instant? = null
    private val sensorManager by lazy { getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    private val powerManager by lazy { getSystemService(Context.POWER_SERVICE) as PowerManager }
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    private val wifi by lazy { Wifi(this, this::logStatus) }
    private val kasaClient by lazy { KasaClient() }
    private var gravityRef: FloatArray? = null
    private var alarmOnSince: Long? = null

    private fun isServiceActive() = serviceExpire?.isAfter(Instant.now()) == true

    override fun onBind(intent: Intent) = null

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        when (intent.action) {
            Actions.START.name -> startService(intent.getIntExtra("workDurationSec", 0))
            Actions.STOP.name -> stopService()
            else -> logStatus("Unhandled action '${intent.action}'")
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(1, createNotification())
        logStatus("The service has been created")
    }

    override fun onDestroy() {
        super.onDestroy()
        logStatus("The service has been destroyed")
    }

    private fun startService(intentWorkDurationSec: Int) {
        // schedule next service
        scheduleService(this)

        if (isServiceActive()) return
        val config = getServiceConfig(this) ?: return run {
            logStatus("Error: Can't start service, missing config")
        }
        val workDurationSec = when (intentWorkDurationSec) {
            0 -> config.workDurationSec
            else -> intentWorkDurationSec
        }
        serviceExpire = Instant.now().plus(workDurationSec.toLong(), ChronoUnit.SECONDS)

        logStatus(String.format("Starting service (%s, %d, %d, %.1f)",
            ZonedDateTime.now().withNano(0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            workDurationSec,
            config.detectionIntervalSec,
            config.tiltAngleThreshold
        ))

        // we need this lock so our service gets not affected by Doze Mode
        wakeLock.acquire(workDurationSec * 1000L)

        // we're starting a loop in a coroutine
        GlobalScope.launch {
            while (isServiceActive()) {
                try {
                    detection(config)
                } catch (e: Exception) {
                    logStatus(e.message ?: "Exception!")
                }
                delay(config.detectionIntervalSec*1000L)
            }
            logStatus("End of the loop for the service")

            withContext(Dispatchers.Main) {
                wakeLock.release()
                stopForeground(true)
                stopSelf()
            }
        }
    }

    private fun stopService() {
        logStatus("Stopping service")
        serviceExpire = null
    }

    private suspend fun detection(config: ServiceConfig) {
        if (isEngineOn()) {
            logStatus("Engine running")
            gravityRef = null
            if (alarmOnSince != null) {
                clearAlarm()
                alarmOnSince = null
            }
        }
        else {
            val firstSample = readAccelerometer(sensorManager)
            if (gravityRef == null) {
                logStatus(String.format("u=%s",
                    printVect(firstSample)
                ))
                gravityRef = firstSample
            }
            else {
                val tiltAngle = angleBetweenVectors(gravityRef!!, firstSample)
                logStatus(String.format("u=%s v=%s angle=%.1f",
                    printVect(gravityRef!!),
                    printVect(firstSample),
                    tiltAngle
                ))
                if (tiltAngle >= config.tiltAngleThreshold) {
                    if (alarmOnSince == null) {
                        setAlarm()
                        alarmOnSince = System.currentTimeMillis()
                    }
                    else if (System.currentTimeMillis()-(alarmOnSince ?: 0L) > 2*60*1000L) {
                        clearAlarm()
                        alarmOnSince = null
                        gravityRef = null
                    }
                }
                else {
                    if (alarmOnSince != null) {
                        clearAlarm()
                        alarmOnSince = null
                    }
                }
            }
        }
    }

    private suspend fun setAlarm() {
        logStatus("Alarm ON")
        wifi.turnOn()
        kasaClient.setAlarm(true)
    }

    private suspend fun clearAlarm() {
        logStatus("Alarm OFF")
        kasaClient.setAlarm(false)
        wifi.turnOff()
    }

    private fun isEngineOn(): Boolean {
        val batteryStatus: Intent? = registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return (status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL)
    }

    private fun angleBetweenVectors(u: FloatArray, v: FloatArray): Float {
        val dotProd = u[0]*v[0] + u[1]*v[1] + u[2]*v[2]
        val uMagSq = u[0]*u[0] + u[1]*u[1] + u[2]*u[2]
        val vMagSq = v[0]*v[0] + v[1]*v[1] + v[2]*v[2]
        val cosThetaSq = dotProd*dotProd / (uMagSq*vMagSq)
        val cosTheta = sqrt(cosThetaSq).withSign(dotProd)
        return acos(cosTheta) * 180f / PI.toFloat()
    }

    private fun logStatus(message: String) = log(this, message)

    private fun printVect(v: FloatArray) = String.format("[%.2f,%.2f,%.2f]", v[0], v[1], v[2])

    private fun createNotification(): Notification {
        val notificationChannelId = "ENDLESS SERVICE CHANNEL"
            val channel = NotificationChannel(
                notificationChannelId,
                "Endless Service notifications channel",
                NotificationManager.IMPORTANCE_HIGH
            ).let {
                it.description = "Endless Service channel"
                it.enableLights(true)
                it.lightColor = Color.RED
                it.enableVibration(true)
                it.vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
                it
            }
            notificationManager.createNotificationChannel(channel)

        val pendingIntent: PendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        }

        return Notification.Builder(this, notificationChannelId)
            .setContentTitle("Endless Service")
            .setContentText("This is your favorite endless service working")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setTicker("Ticker text")
            .build()
    }
}
