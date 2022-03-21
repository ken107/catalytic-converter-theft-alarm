package com.robertohuertas.endless

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay


class Wifi(
    private val context: Context,
    private val logStatus: (String) -> Unit
) {
    private val wifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    suspend fun turnOn() {
        if (!wifiManager.isWifiEnabled) {
            logStatus("Wifi ON")
            wifiManager.isWifiEnabled = true
            waitUntilReady()
        } else {
            logStatus("Wifi already ON")
        }
    }

    private suspend fun waitUntilReady() {
        val channel = Channel<Unit>(0)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == WifiManager.NETWORK_STATE_CHANGED_ACTION) {
                    val info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO) as NetworkInfo
                    if (info.isConnected) channel.offer(Unit)
                }
            }
        }
        context.registerReceiver(
            receiver,
            IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        )
        try {
            channel.receive()
        } finally {
            context.unregisterReceiver(receiver)
        }
        delay(1000)
    }

    suspend fun turnOff() {
        delay(500)
        if (wifiManager.isWifiEnabled) {
            logStatus("Wifi OFF")
            wifiManager.isWifiEnabled = false
        } else {
            logStatus("Wifi already OFF")
        }
    }
}
