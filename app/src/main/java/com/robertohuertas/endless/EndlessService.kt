package com.robertohuertas.endless

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.widget.Toast
import kotlinx.coroutines.*
import java.util.*
import kotlin.math.acos
import kotlin.math.sqrt
import kotlin.math.withSign


class EndlessService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceStarted = false
    private val sensorManager by lazy { getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    private var gravityRef: FloatArray? = null

    override fun onBind(intent: Intent): IBinder? {
        log("Some component want to bind with the service")
        // We don't provide binding, so return null
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("onStartCommand executed with startId: $startId")
        if (intent != null) {
            val action = intent.action
            log("using an intent with action $action")
            when (action) {
                Actions.START.name -> startService()
                Actions.STOP.name -> stopService()
                else -> log("This should never happen. No action in the received intent")
            }
        } else {
            log(
                "with a null intent. It has been probably restarted by the system."
            )
        }
        // by returning this we make sure the service is restarted if the system kills the service
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        log("The service has been created".toUpperCase(Locale.ROOT))
        val notification = createNotification()
        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        log("The service has been destroyed".toUpperCase(Locale.ROOT))
        Toast.makeText(this, "Service destroyed", Toast.LENGTH_SHORT).show()
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        val restartServiceIntent = Intent(applicationContext, EndlessService::class.java).also {
            it.setPackage(packageName)
        }
        val restartServicePendingIntent: PendingIntent = PendingIntent.getService(this, 1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
        applicationContext.getSystemService(Context.ALARM_SERVICE)
        val alarmService: AlarmManager = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmService.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000, restartServicePendingIntent)
    }
    
    private fun startService() {
        if (isServiceStarted) return
        val config = getServiceConfig(this)
        if (config == null) {
            logStatus("Error: Missing service config")
            return
        }
        log("Starting the foreground service task")
        Toast.makeText(this, "Service starting its task", Toast.LENGTH_SHORT).show()
        isServiceStarted = true
        setServiceState(this, ServiceState.STARTED)
        logStatus(String.format("Service started (%d, %.3f, %d, %.3f, %s)",
            config.detectionIntervalSec,
            config.tiltAngleThreshold,
            config.samplingDurationMs,
            config.engineNoiseThreshold,
            config.notificationServerIp
        ))

        // we need this lock so our service gets not affected by Doze Mode
        wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EndlessService::lock").apply {
                    acquire()
                }
            }

        // we're starting a loop in a coroutine
        GlobalScope.launch {
            while (isServiceStarted) {
                detection(config)
                delay(config.detectionIntervalSec*1000L)
            }
            log("End of the loop for the service")
        }
    }

    private fun stopService() {
        log("Stopping the foreground service")
        Toast.makeText(this, "Service stopping", Toast.LENGTH_SHORT).show()
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            stopForeground(true)
            stopSelf()
        } catch (e: Exception) {
            log("Service stopped without being started: ${e.message}")
        }
        isServiceStarted = false
        setServiceState(this, ServiceState.STOPPED)
        logStatus("Service stopped")
    }

    private suspend fun detection(config: ServiceConfig) {
        val channel = getAccelerometer(sensorManager)
        try {
            val firstSample = channel.receive()
            if (gravityRef == null) {
                logStatus(String.format("ref=%s",
                    printVect(firstSample)
                ))
                val samples = collectSamples(channel, config.samplingDurationMs)
                if (!isEngineOn(samples, config)) gravityRef = firstSample
            }
            else {
                val tiltAngle = angleBetweenVectors(gravityRef!!, firstSample)
                logStatus(String.format("u=%s v=%s angle=%.3f",
                    printVect(gravityRef!!),
                    printVect(firstSample),
                    Math.toDegrees(tiltAngle.toDouble())
                ))
                if (tiltAngle >= config.tiltAngleThreshold) {
                    val samples = collectSamples(channel, config.samplingDurationMs)
                    if (!isEngineOn(samples, config)) raiseAlarm()
                    gravityRef = null
                }
            }
        }
        finally {
            channel.close()
        }
    }

    private fun isEngineOn(samples: LinkedList<FloatArray>, config: ServiceConfig): Boolean {
        val peakDif = calcPeakDif(samples) ?: 0f
        logStatus(String.format("noiseLevel=%.3f", peakDif))
        return peakDif >= config.engineNoiseThreshold
    }

    private fun angleBetweenVectors(u: FloatArray, v: FloatArray): Float {
        val dotProd = u[0]*v[0] + u[1]*v[1] + u[2]*v[2]
        val uMagSq = u[0]*u[0] + u[1]*u[1] + u[2]*u[2]
        val vMagSq = v[0]*v[0] + v[1]*v[1] + v[2]*v[2]
        val cosThetaSq = dotProd*dotProd / (uMagSq*vMagSq)
        val cosTheta = sqrt(cosThetaSq).withSign(dotProd)
        return acos(cosTheta)
    }

    private fun raiseAlarm() {
        Toast.makeText(this, "THEFT DETECTED", Toast.LENGTH_SHORT).show()
    }

    private fun logStatus(message: String) {
        Intent("com.robertohuertas.endless.LogStatus").let {
            it.putExtra("message", message)
            sendBroadcast(it)
        }
    }

    private fun printVect(v: FloatArray) = String.format("[%.2f,%.2f,%.2f]", v[0], v[1], v[2])

    private fun createNotification(): Notification {
        val notificationChannelId = "ENDLESS SERVICE CHANNEL"

        // depending on the Android API that we're dealing with we will have
        // to use a specific method to create the notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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
        }

        val pendingIntent: PendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        }

        val builder: Notification.Builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(
            this,
            notificationChannelId
        ) else Notification.Builder(this)

        return builder
            .setContentTitle("Endless Service")
            .setContentText("This is your favorite endless service working")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setTicker("Ticker text")
            .setPriority(Notification.PRIORITY_HIGH) // for under android 26 compatibility
            .build()
    }
}
