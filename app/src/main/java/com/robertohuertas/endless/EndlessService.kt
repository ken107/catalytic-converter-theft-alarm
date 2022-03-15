package com.robertohuertas.endless

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.widget.Toast
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.jsonBody
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.*
import kotlin.math.acos
import kotlin.math.sqrt
import kotlin.math.withSign


class EndlessService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceStarted = false
    private val detectionIntervalSec = 10
    private val sensorManager by lazy { getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    private val sensor by lazy { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    private val samplingFrequencyHz = 50
    private val samplingDurationSec = 1
    private var gravityRef: FloatArray? = null
    private val tiltAngleThreshold = Math.toRadians(5.0).toFloat()
    private val vibrationNoiseThreshold = .1f

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
        log("Starting the foreground service task")
        Toast.makeText(this, "Service starting its task", Toast.LENGTH_SHORT).show()
        isServiceStarted = true
        setServiceState(this, ServiceState.STARTED)

        // we need this lock so our service gets not affected by Doze Mode
        wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EndlessService::lock").apply {
                    acquire()
                }
            }

        // we're starting a loop in a coroutine
        GlobalScope.launch(Dispatchers.IO) {
            while (isServiceStarted) {
                detection()
                delay(detectionIntervalSec*1000L)
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
    }

    private suspend fun detection() {
        val channel = Channel<FloatArray>(0)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                channel.offer(event.values)
            }
            override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
            }
        }
        sensorManager.registerListener(
            listener,
            sensor,
            1000*1000/samplingFrequencyHz,
            100*1000
        )
        val firstSample = channel.receive()
        if (gravityRef == null) {
            if (!isEngineOn(channel)) gravityRef = firstSample
        }
        else {
            val tiltAngle = angleBetweenVectors(gravityRef!!, firstSample)
            logStatus(StringBuilder()
                .append(printVect(gravityRef!!))
                .append(" ")
                .append(printVect(firstSample))
                .append(" ")
                .append(String.format("%.3f", Math.toDegrees(tiltAngle.toDouble())))
                .toString())
            if (tiltAngle > tiltAngleThreshold) {
                if (!isEngineOn(channel)) raiseAlarm()
                gravityRef = null
            }
        }
        sensorManager.unregisterListener(listener)
    }

    private suspend fun isEngineOn(channel: Channel<FloatArray>): Boolean {
        val samples = LinkedList<FloatArray>().also {
            while (it.size < samplingDurationSec*samplingFrequencyHz)
                it.add(channel.receive())
        }
        return false
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
        logStatus("THEFT DETECTED")
    }

    private fun logStatus(message: String) {
        Intent("com.robertohuertas.endless.LogStatus").let {
            it.putExtra("message", message)
            sendBroadcast(it)
        }
    }

    private fun printVect(v: FloatArray) = StringBuilder()
        .append("[")
        .append(String.format("%.2f", v[0]))
        .append(",")
        .append(String.format("%.2f", v[1]))
        .append(",")
        .append(String.format("%.2f", v[2]))
        .append("]")

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
