package com.robertohuertas.endless

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    private val editDetectionIntervalSec by lazy {
        findViewById<EditText>(R.id.editDetectionIntervalSec)
    }

    private val editTiltAngleThreshold by lazy {
        findViewById<EditText>(R.id.editTiltAngleThreshold)
    }

    private val mLog by lazy {
        findViewById<TextView>(R.id.txtLog)
            .also { it.movementMethod = ScrollingMovementMethod() }
    }

    private val logReceiver by lazy {
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
                if (isConfigValid()) {
                    saveConfig()
                    mLog.append("Service will run for 10 minutes for testing, after that it will run regularly from midnight to 6:00am\n")
                    startForegroundService(
                        Intent(this, EndlessService::class.java)
                            .setAction(Actions.START.name)
                            .putExtra("workDurationSec", 10*60)
                    )
                } else {
                    mLog.append("Error: Missing args\n")
                }
            }
        }

        findViewById<Button>(R.id.btnStopService).let {
            it.setOnClickListener {
                startForegroundService(
                    Intent(this, EndlessService::class.java)
                        .setAction(Actions.STOP.name)
                )
            }
        }

        getServiceConfig(this)?.also {
            editDetectionIntervalSec.setText(it.detectionIntervalSec.toString())
            editTiltAngleThreshold.setText(it.tiltAngleThreshold.toString())
        }
    }

    private fun isConfigValid() = editDetectionIntervalSec.text.isNotBlank() &&
            editTiltAngleThreshold.text.isNotBlank()

    private fun saveConfig() {
        val config = ServiceConfig(
            0,
            6*3600,
            editDetectionIntervalSec.text.toString().toInt(),
            editTiltAngleThreshold.text.toString().toFloat()
        )
        setServiceConfig(this, config)
        mLog.append("Config saved\n")
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(logReceiver, IntentFilter("com.robertohuertas.endless.LogStatus"))
    }

    override fun onStop() {
        unregisterReceiver(logReceiver)
        super.onStop()
    }
}
