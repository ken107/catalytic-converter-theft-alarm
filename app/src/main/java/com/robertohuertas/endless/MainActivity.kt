package com.robertohuertas.endless

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private val editSamplingDurationMs by lazy {
        findViewById<EditText>(R.id.editSamplingDurationMs)
    }

    private val editEngineNoiseThreshold by lazy {
        findViewById<EditText>(R.id.editEngineNoiseThreshold)
    }

    private val editNotificationServerIp by lazy {
        findViewById<EditText>(R.id.editNotificationServerIp)
    }

    private val mLog by lazy {
        findViewById<TextView>(R.id.txtLog)
            .also { it.movementMethod = ScrollingMovementMethod() }
    }

    private val sensorManager by lazy {
        getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    private lateinit var scope: CoroutineScope

    private val broadcastFilter by lazy {
        IntentFilter("com.robertohuertas.endless.LogStatus")
    }

    private val broadcastReceiver by lazy {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                intent.getCharSequenceExtra("message").also {
                    mLog.append(it)
                    mLog.append("\n")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        title = "Endless Service"

        findViewById<Button>(R.id.btnStartService).let {
            it.setOnClickListener {
                log("START THE FOREGROUND SERVICE ON DEMAND")
                if (editSamplingDurationMs.text.isBlank() ||
                    editEngineNoiseThreshold.text.isBlank() ||
                    editNotificationServerIp.text.isBlank()) {
                    mLog.append("Error: Missing params\n")
                }
                else {
                    setServiceConfig(this, ServiceConfig(
                        10,
                        Math.toRadians(5.0).toFloat(),
                        editSamplingDurationMs.text.toString().toInt(),
                        editEngineNoiseThreshold.text.toString().toFloat(),
                        editNotificationServerIp.text.toString()
                    ))
                    actionOnService(Actions.START)
                }
            }
        }

        findViewById<Button>(R.id.btnStopService).let {
            it.setOnClickListener {
                log("STOP THE FOREGROUND SERVICE ON DEMAND")
                actionOnService(Actions.STOP)
            }
        }

        findViewById<Button>(R.id.btnStartNoiseAnalysis).let {
            it.setOnClickListener {
                log("START NOISE ANALYSIS")
                if (editSamplingDurationMs.text.isBlank()) {
                    mLog.append("Error: Missing params\n")
                }
                else {
                    scope.launch {
                        mLog.append("Noise analysis starting\n")
                        val samplingDurationMs = editSamplingDurationMs.text.toString().toInt()
                        val noiseLevel = withContext(Dispatchers.Default) {
                            startNoiseAnalysis(samplingDurationMs)
                        }
                        mLog.append(String.format("Noise analysis ended (noiseLevel=%.3f)\n", noiseLevel))
                    }
                }
            }
        }

        getServiceConfig(this)?.also {
            editSamplingDurationMs.setText(it.samplingDurationMs.toString())
            editEngineNoiseThreshold.setText(it.engineNoiseThreshold.toString())
            editNotificationServerIp.setText(it.notificationServerIp)
        }
    }

    private fun actionOnService(action: Actions) {
        if (getServiceState(this) == ServiceState.STOPPED && action == Actions.STOP) return
        Intent(this, EndlessService::class.java).also {
            it.action = action.name
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                log("Starting the service in >=26 Mode")
                startForegroundService(it)
                return
            }
            log("Starting the service in < 26 Mode")
            startService(it)
        }
    }

    private suspend fun startNoiseAnalysis(samplingDurationMs: Int): Float {
        delay(1000)
        val channel = getAccelerometer(sensorManager)
        try {
            val samples = collectSamples(channel, samplingDurationMs)
            return calcPeakDif(samples) ?: 0f
        }
        finally {
            channel.close()
        }
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(broadcastReceiver, broadcastFilter)
        scope = MainScope()
    }

    override fun onStop() {
        scope.cancel()
        unregisterReceiver(broadcastReceiver)
        super.onStop()
    }
}
