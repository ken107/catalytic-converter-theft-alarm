package com.robertohuertas.endless

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private val editDetectionIntervalSec by lazy {
        findViewById<EditText>(R.id.editDetectionIntervalSec)
    }

    private val editTiltAngleThreshold by lazy {
        findViewById<EditText>(R.id.editTiltAngleThreshold)
    }

    private val editNotificationServerIp by lazy {
        findViewById<EditText>(R.id.editNotificationServerIp)
    }

    private val mLog by lazy {
        findViewById<TextView>(R.id.txtLog)
            .also { it.movementMethod = ScrollingMovementMethod() }
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
                if (editDetectionIntervalSec.text.isBlank() ||
                    editTiltAngleThreshold.text.isBlank() ||
                    editNotificationServerIp.text.isBlank()) {
                    mLog.append("Error: Missing params\n")
                }
                else {
                    setServiceConfig(this, ServiceConfig(
                        editDetectionIntervalSec.text.toString().toInt(),
                        editTiltAngleThreshold.text.toString().toFloat(),
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

        getServiceConfig(this)?.also {
            editDetectionIntervalSec.setText(it.detectionIntervalSec.toString())
            editTiltAngleThreshold.setText(it.tiltAngleThreshold.toString())
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
