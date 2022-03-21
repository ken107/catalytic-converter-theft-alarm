package com.robertohuertas.endless

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.experimental.xor

class KasaClient {

    suspend fun setAlarm(on: Boolean) = withContext(Dispatchers.IO) {
        val payload = encrypt("{\"system\":{\"set_relay_state\":{\"state\":${if (on) 1 else 0}}}}")
        DatagramSocket().use {
            it.broadcast = true
            it.send(DatagramPacket(
                payload,
                payload.size,
                InetAddress.getByName("255.255.255.255"),
                9999
            ))
        }
    }

    private fun encrypt(input: String): ByteArray {
        val buf = input.toByteArray(Charsets.UTF_8)
        var key: Byte = -85
        for (i in buf.indices) {
            buf[i] = buf[i] xor key
            key = buf[i]
        }
        return buf
    }

    private fun decrypt(input: ByteArray): String {
        val buf = ByteArray(input.size)
        var key: Byte = -85
        for (i in buf.indices) {
            buf[i] = input[i] xor key
            key = input[i]
        }
        return buf.toString(Charsets.UTF_8)
    }
}
